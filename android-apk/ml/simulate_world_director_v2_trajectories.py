#!/usr/bin/env python3
"""Generate privacy-safe WorldDirector V2 trajectories from the existing registered-Level simulator.

V2 deliberately expands only the observable pacing contract. It adds recent action/pressure history,
streaks, density/entropy buckets, and time-since-pressure buckets to V1 local features. It never adds
Level/zone/evidence identifiers, escape/puzzle information, item/Entity identity, inventory, player
text, provider data, or character-private canon.

The output is still offline training data. Production runtime/model assets are not modified here.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

import simulate_world_director_trajectories as v1

PRESSURES = ("NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY")


def bucket_since(value: int) -> str:
    if value <= 1:
        return "0_1"
    if value <= 4:
        return "2_4"
    if value <= 9:
        return "5_9"
    if value <= 19:
        return "10_19"
    return "20_plus"


def bucket_streak(value: int) -> str:
    if value <= 0:
        return "0"
    if value == 1:
        return "1"
    if value <= 3:
        return "2_3"
    return "4_plus"


def bucket_density(value: float) -> str:
    if value <= 0.0:
        return "zero"
    if value <= 0.25:
        return "low"
    if value <= 0.50:
        return "mid"
    return "high"


def bucket_entropy(value: float) -> str:
    if value <= 0.0:
        return "zero"
    if value <= 0.75:
        return "low"
    if value <= 1.25:
        return "mid"
    return "high"


def bucket_turn(value: int) -> str:
    if value <= 7:
        return "opening"
    if value <= 20:
        return "early"
    if value <= 50:
        return "mid"
    return "long_run"


def turns_since_pressure(history: list[dict], pressure: str, cap: int = 20) -> int:
    for distance, row in enumerate(reversed(history), start=1):
        if row.get("pressure") == pressure:
            return min(distance, cap)
    return cap


def sanitize(value: object) -> str:
    text = str(value or "").lower()
    return "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in text).strip("_")


def feature_text_v2(snapshot: dict, history_positions: int = 4) -> str:
    state = snapshot.get("state") or {}
    history = list(snapshot.get("history") or [])
    parts = [str(snapshot.get("featureTextV1") or "").strip()]
    parts += [
        "contract_world_director_pressure_v2",
        f"turn_{bucket_turn(int(snapshot.get('turnIndex') or 0))}",
        f"since_entity_{bucket_since(int(state.get('turnsSinceEntityPressure') or 20))}",
        f"since_item_{bucket_since(int(state.get('turnsSinceItemOpportunity') or 20))}",
        f"since_maze_{bucket_since(turns_since_pressure(history, 'MAZE_PRESSURE'))}",
        f"search_streak_{bucket_streak(int(state.get('searchStreak') or 0))}",
        f"explore_streak_{bucket_streak(int(state.get('exploreStreak') or 0))}",
        f"entity_density8_{bucket_density(float(state.get('entityPressureDensity8') or 0.0))}",
        f"item_density8_{bucket_density(float(state.get('itemOpportunityDensity8') or 0.0))}",
        f"maze_density8_{bucket_density(float(state.get('mazePressureDensity8') or 0.0))}",
        f"pressure_entropy8_{bucket_entropy(float(state.get('pressureEntropy8') or 0.0))}",
    ]

    legal = [str(value).upper() for value in state.get("legalProposals") or []]
    if legal:
        parts.append("legalset_" + "_".join(name.lower() for name in PRESSURES if name in legal))

    recent = list(reversed(history[-history_positions:]))
    for position, row in enumerate(recent, start=1):
        action = sanitize(row.get("actionKind")) or "unknown"
        pressure = sanitize(row.get("pressure")) or "none"
        parts.append(f"h{position}_action_{action}")
        parts.append(f"h{position}_pressure_{pressure}")

    previous_pressure = sanitize(recent[0].get("pressure")) if recent else "none"
    previous_action = sanitize(recent[0].get("actionKind")) if recent else "none"
    parts.append(f"previous_pressure_{previous_pressure or 'none'}")
    parts.append(f"previous_action_{previous_action or 'none'}")

    # Explicit interaction tokens give a small linear LiteRT classifier useful nonlinear context
    # without inflating the model into a large network.
    action = sanitize(state.get("actionKind")) or "unknown"
    visit = sanitize(state.get("visitBucket")) or "unknown"
    parts += [
        f"cross_action_visit_{action}_{visit}",
        f"cross_action_prevpressure_{action}_{previous_pressure or 'none'}",
        f"cross_action_entitydensity_{action}_{bucket_density(float(state.get('entityPressureDensity8') or 0.0))}",
        f"cross_action_itemdensity_{action}_{bucket_density(float(state.get('itemOpportunityDensity8') or 0.0))}",
        f"cross_action_mazedensity_{action}_{bucket_density(float(state.get('mazePressureDensity8') or 0.0))}",
    ]
    return " ".join(part for part in parts if part).strip()


def sample_id_v2(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:24]


def enrich(row: dict, history_positions: int) -> dict:
    text = feature_text_v2(row, history_positions)
    result = dict(row)
    result["schemaVersion"] = 3
    result["featureTextV2"] = text
    result["sampleIdV2"] = sample_id_v2(text)
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--levels", default="../app/src/main/assets/levels")
    parser.add_argument("--runs", type=int, default=960)
    parser.add_argument("--turns", type=int, default=120)
    parser.add_argument("--history", type=int, default=16)
    parser.add_argument("--history-positions", type=int, default=4)
    parser.add_argument("--seed", type=int, default=2299)
    parser.add_argument("--output", default="world_director_v2_teacher_snapshots.jsonl")
    parser.add_argument("--report", default="world_director_v2_teacher_snapshot_report.json")
    args = parser.parse_args()

    if args.runs <= 0 or args.turns <= 0:
        raise SystemExit("runs and turns must be positive")
    if not 4 <= args.history <= 32:
        raise SystemExit("history must be in 4..32 for the V2 pacing contract")
    if not 1 <= args.history_positions <= min(8, args.history):
        raise SystemExit("history-positions must be in 1..8 and <= history")

    level_paths = sorted(Path(args.levels).glob("**/*.json"))
    levels = [v1.LevelData.load(path) for path in level_paths]
    levels = [level for level in levels if level.initial_zone and level.zones]
    if not levels:
        raise SystemExit("no registered Level JSON assets found")

    base_rows = v1.simulate(levels, args.runs, args.turns, args.history, args.seed)
    rows = [enrich(row, args.history_positions) for row in base_rows]

    with Path(args.output).open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    unique_v1 = {row.get("featureTextV1") for row in rows}
    unique_v2 = {row.get("featureTextV2") for row in rows}
    legal_counts = Counter(",".join((row.get("state") or {}).get("legalProposals") or []) for row in rows)
    report = {
        "schemaVersion": 2,
        "contract": "WORLD_DIRECTOR_PRESSURE_V2",
        "seed": args.seed,
        "runs": args.runs,
        "turnsPerRun": args.turns,
        "rows": len(rows),
        "sessions": len({row.get("sessionId") for row in rows}),
        "historyLimit": args.history,
        "historyPositions": args.history_positions,
        "uniqueV1Contexts": len(unique_v1),
        "uniqueV2Contexts": len(unique_v2),
        "expansionRatioVsV1": round(len(unique_v2) / max(1, len(unique_v1)), 4),
        "legalSetCounts": dict(sorted(legal_counts.items())),
        "hiddenAuthorityIncluded": False,
        "productionRuntimeModified": False,
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
