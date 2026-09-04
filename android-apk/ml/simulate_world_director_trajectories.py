#!/usr/bin/env python3
"""Generate privacy-safe WorldDirector training trajectories from registered Level data.

This is an offline data generator. It does not call any provider and does not alter production
runtime behavior. Level topology, safe zone tags, evidence availability and generation constraints
shape trajectories, but hidden identifiers/solutions are never written to output.

The behavior policy is deliberately stochastic and is NOT a target label. Snapshots are captured
before the behavior proposal is sampled so a teacher can label them independently.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
from collections import Counter, deque
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

PROPOSALS = ("NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY")
ACTIONS = ("SEARCH", "EXPLORE", "EXECUTE")
HIDDEN_TAG_FRAGMENTS = ("escape", "exit", "transition", "solution", "required", "hidden", "secret", "blueprint")
FORBIDDEN_OUTPUT_KEYS = {
    "levelId", "zoneId", "evidenceId", "evidenceIds", "requiredFacts", "requiredActions",
    "solutionId", "escapeBlueprint", "playerText", "input", "apiKey", "secret", "entityId",
    "itemId", "inventory", "characterCanon",
}

PERSONAS = {
    "balanced": {"SEARCH": 0.30, "EXPLORE": 0.55, "EXECUTE": 0.15},
    "explorer": {"SEARCH": 0.15, "EXPLORE": 0.75, "EXECUTE": 0.10},
    "scavenger": {"SEARCH": 0.62, "EXPLORE": 0.30, "EXECUTE": 0.08},
    "repeater": {"SEARCH": 0.42, "EXPLORE": 0.52, "EXECUTE": 0.06},
    "speedrunner": {"SEARCH": 0.18, "EXPLORE": 0.47, "EXECUTE": 0.35},
    "cautious": {"SEARCH": 0.46, "EXPLORE": 0.43, "EXECUTE": 0.11},
}


def safe_tags(tags: Iterable[str]) -> list[str]:
    result = []
    for tag in tags:
        normalized = str(tag).lower()
        if any(fragment in normalized for fragment in HIDDEN_TAG_FRAGMENTS):
            continue
        clean = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in normalized).strip("_")
        if clean:
            result.append(clean)
    return sorted(set(result))


def bucket_visit(count: int) -> str:
    return "first" if count <= 1 else "repeat" if count == 2 else "deep"


def bucket_evidence(count: int) -> str:
    return "none" if count == 0 else "some" if count <= 3 else "many"


def feature_text(action: str, visit_count: int, revision: int, recent_mutation: str | None,
                 tags: list[str], discovered_evidence: int, legal: list[str]) -> str:
    parts = [f"action_{action.lower()}", f"visit_{bucket_visit(visit_count)}"]
    parts.append("revision_early" if revision <= 2 else "revision_changed")
    if recent_mutation:
        clean = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in recent_mutation.lower()).strip("_")
        if clean:
            parts.append(f"recent_{clean}")
    parts.extend(f"zone_{tag}" for tag in tags)
    parts.append(f"evidence_{bucket_evidence(discovered_evidence)}")
    parts.extend(f"candidate_{proposal.lower()}" for proposal in PROPOSALS if proposal in legal)
    return " ".join(parts)


@dataclass
class LevelData:
    source: Path
    zones: list[dict]
    initial_zone: str
    explore_route: list[str]
    evidence: list[dict]
    allow_entities: bool
    procedural_topology: bool

    @classmethod
    def load(cls, path: Path) -> "LevelData":
        raw = json.loads(path.read_text(encoding="utf-8"))
        constraints = raw.get("generationConstraints") or {}
        return cls(
            source=path,
            zones=list(raw.get("zones") or []),
            initial_zone=str(raw.get("initialZoneId") or ""),
            explore_route=list(raw.get("exploreRoute") or []),
            evidence=list(raw.get("evidence") or []),
            allow_entities=bool(constraints.get("allowEntities", False)),
            procedural_topology=bool(constraints.get("proceduralTopology", False)),
        )

    @property
    def zone_map(self) -> dict[str, dict]:
        return {str(zone.get("id")): zone for zone in self.zones}


@dataclass
class SimState:
    current_zone: str
    visits: Counter = field(default_factory=Counter)
    revision: int = 0
    discovered: set[int] = field(default_factory=set)
    environment: dict[str, str] = field(default_factory=dict)
    recent_mutation: str | None = None
    turn: int = 0
    last_combat: int | None = None
    last_entity: int | None = None
    last_item: int | None = None
    search_streak: int = 0
    explore_streak: int = 0
    history: deque = field(default_factory=lambda: deque(maxlen=32))


def graph_reachable(level: LevelData, start: str) -> bool:
    zones = level.zone_map
    current = zones.get(start)
    if not current or len(current.get("connections") or []) < 2:
        return False
    seen: set[str] = set()
    queue = deque([start])
    while queue:
        zone_id = queue.popleft()
        if zone_id in seen:
            continue
        seen.add(zone_id)
        for target in zones.get(zone_id, {}).get("connections") or []:
            target = str(target)
            if target in zones and target not in seen:
                queue.append(target)
    return seen == set(zones)


def legal_proposals(level: LevelData, state: SimState, action: str, combat_clear: bool = True) -> list[str]:
    legal = ["NONE"]
    if action == "EXPLORE" and level.procedural_topology and graph_reachable(level, state.current_zone):
        legal.append("MAZE_PRESSURE")
    if action == "EXPLORE" and combat_clear and level.allow_entities:
        legal.append("ENTITY_PRESSURE")
    if action in {"SEARCH", "EXPLORE"} and combat_clear:
        legal.append("ITEM_OPPORTUNITY")
    return [proposal for proposal in PROPOSALS if proposal in legal]


def choose_action(rng: random.Random, persona: str, previous: str | None) -> str:
    weights = dict(PERSONAS[persona])
    if persona == "repeater" and previous in weights:
        weights[previous] *= 2.2
    names = list(weights)
    return rng.choices(names, weights=[weights[name] for name in names], k=1)[0]


def conditions_met(conditions: list[str], state: SimState) -> bool:
    for condition in conditions:
        if condition.startswith("visit:"):
            try:
                _, zone_id, count = condition.split(":", 2)
                if state.visits[zone_id] < int(count):
                    return False
            except ValueError:
                return False
        elif condition.startswith("env:"):
            try:
                payload = condition[4:]
                key, value = payload.split("=", 1)
                if state.environment.get(key) != value:
                    return False
            except ValueError:
                return False
    return True


def maybe_discover(level: LevelData, state: SimState, action: str, rng: random.Random) -> bool:
    candidates = []
    for index, evidence in enumerate(level.evidence):
        if index in state.discovered or str(evidence.get("zoneId")) != state.current_zone:
            continue
        sources = {str(source).upper() for source in evidence.get("sources") or []}
        if action == "SEARCH" and "SEARCH" not in sources:
            continue
        if action == "EXPLORE" and not (sources & {"ENVIRONMENT", "ANOMALY", "SURVIVOR"}):
            continue
        if action == "EXECUTE":
            continue
        if conditions_met(list(evidence.get("discoverConditions") or []), state):
            candidates.append(index)
    if candidates and rng.random() < (0.72 if action == "SEARCH" else 0.42):
        state.discovered.add(rng.choice(candidates))
        return True
    return False


def move_explore(level: LevelData, state: SimState, rng: random.Random) -> bool:
    zones = level.zone_map
    connections = [str(value) for value in zones.get(state.current_zone, {}).get("connections") or [] if str(value) in zones]
    if not connections:
        return False
    preferred = next((zone for zone in level.explore_route if zone in connections and state.visits[zone] == 0), None)
    target = preferred if preferred and rng.random() < 0.72 else rng.choice(connections)
    if target == state.current_zone:
        return False
    state.current_zone = target
    state.visits[target] += 1
    state.revision += 1
    state.recent_mutation = "move"
    return True


def maybe_execute_environment(level: LevelData, state: SimState, rng: random.Random) -> bool:
    # Use the real Level 1 environment key when present without exposing action IDs or puzzle facts.
    if "power" in state.environment and state.environment.get("power") != "off" and rng.random() < 0.28:
        state.environment["power"] = "off"
        state.revision += 1
        state.recent_mutation = "environment"
        return True
    return False


def density(history: deque, key: str, value: object, window: int = 8) -> float:
    rows = list(history)[-window:]
    if not rows:
        return 0.0
    return sum(row.get(key) == value for row in rows) / len(rows)


def since(turn: int, last: int | None) -> int:
    return 99 if last is None else max(0, turn - last)


def entropy_recent_pressures(history: deque, window: int = 8) -> float:
    values = [row["pressure"] for row in list(history)[-window:] if row.get("pressure")]
    if not values:
        return 0.0
    counts = Counter(values)
    total = len(values)
    return round(-sum((count / total) * math.log2(count / total) for count in counts.values()), 4)


def snapshot(level: LevelData, state: SimState, action: str, legal: list[str], session_id: str, history_limit: int) -> dict:
    zone = level.zone_map.get(state.current_zone, {})
    tags = safe_tags(zone.get("tags") or [])
    visit_count = state.visits[state.current_zone]
    history = list(state.history)[-history_limit:]
    safe = {
        "schemaVersion": 2,
        "sessionId": session_id,
        "turnIndex": state.turn,
        "featureTextV1": feature_text(
            action, visit_count, state.revision, state.recent_mutation, tags, len(state.discovered), legal
        ),
        "state": {
            "actionKind": action,
            "visitBucket": bucket_visit(visit_count),
            "revisionBucket": "early" if state.revision <= 2 else "changed",
            "safeZoneTags": tags,
            "evidenceBucket": bucket_evidence(len(state.discovered)),
            "legalProposals": legal,
            "turnsSinceCombat": since(state.turn, state.last_combat),
            "turnsSinceEntityPressure": since(state.turn, state.last_entity),
            "turnsSinceItemOpportunity": since(state.turn, state.last_item),
            "searchStreak": state.search_streak,
            "exploreStreak": state.explore_streak,
            "combatDensity8": round(density(state.history, "outcome", "combat"), 4),
            "entityPressureDensity8": round(density(state.history, "pressure", "ENTITY_PRESSURE"), 4),
            "itemOpportunityDensity8": round(density(state.history, "pressure", "ITEM_OPPORTUNITY"), 4),
            "mazePressureDensity8": round(density(state.history, "pressure", "MAZE_PRESSURE"), 4),
            "pressureEntropy8": entropy_recent_pressures(state.history),
        },
        "history": history,
    }
    serialized = json.dumps(safe, ensure_ascii=False)
    lowered = serialized.lower()
    for key in FORBIDDEN_OUTPUT_KEYS:
        if f'"{key.lower()}"' in lowered:
            raise AssertionError(f"forbidden output key leaked: {key}")
    return safe


def behavior_pressure(rng: random.Random, legal: list[str]) -> str:
    # Broad coverage without treating this stochastic choice as ground truth.
    weights = {"NONE": 2.0, "MAZE_PRESSURE": 1.0, "ENTITY_PRESSURE": 1.0, "ITEM_OPPORTUNITY": 1.0}
    return rng.choices(legal, weights=[weights[name] for name in legal], k=1)[0]


def advance(level: LevelData, state: SimState, action: str, pressure: str, rng: random.Random) -> None:
    state.recent_mutation = None
    discovered = maybe_discover(level, state, action, rng)
    moved = action == "EXPLORE" and rng.random() < 0.82 and move_explore(level, state, rng)
    env_changed = action == "EXECUTE" and maybe_execute_environment(level, state, rng)

    outcome = "none"
    if pressure == "ENTITY_PRESSURE":
        outcome = "combat"
        state.last_combat = state.turn
        state.last_entity = state.turn
    elif pressure == "ITEM_OPPORTUNITY":
        outcome = "reward"
        state.last_item = state.turn
    elif pressure == "MAZE_PRESSURE":
        outcome = "maze"

    if discovered and not state.recent_mutation:
        state.revision += 1
        state.recent_mutation = "evidence"
    elif env_changed:
        state.recent_mutation = "environment"
    elif moved and not state.recent_mutation:
        state.recent_mutation = "move"

    state.history.append({
        "actionKind": action,
        "pressure": pressure,
        "outcome": outcome,
        "visitBucket": bucket_visit(state.visits[state.current_zone]),
        "evidenceBucket": bucket_evidence(len(state.discovered)),
    })
    state.search_streak = state.search_streak + 1 if action == "SEARCH" else 0
    state.explore_streak = state.explore_streak + 1 if action == "EXPLORE" else 0
    state.turn += 1


def simulate(levels: list[LevelData], runs: int, turns: int, history_limit: int, seed: int) -> list[dict]:
    rows: list[dict] = []
    for run_index in range(runs):
        run_seed = seed + run_index * 104729
        rng = random.Random(run_seed)
        level = levels[run_index % len(levels)]
        persona = list(PERSONAS)[run_index % len(PERSONAS)]
        session_id = "sim-" + hashlib.sha256(f"{seed}|{run_index}|{level.source.name}".encode()).hexdigest()[:16]
        initial_environment = json.loads(level.source.read_text(encoding="utf-8")).get("environment") or {}
        state = SimState(current_zone=level.initial_zone, environment={str(k): str(v) for k, v in initial_environment.items()})
        state.visits[state.current_zone] = 1
        previous_action = None
        for _ in range(turns):
            action = choose_action(rng, persona, previous_action)
            legal = legal_proposals(level, state, action, combat_clear=True)
            rows.append(snapshot(level, state, action, legal, session_id, history_limit))
            pressure = behavior_pressure(rng, legal)
            advance(level, state, action, pressure, rng)
            previous_action = action
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--levels", default="../app/src/main/assets/levels")
    parser.add_argument("--runs", type=int, default=240)
    parser.add_argument("--turns", type=int, default=80)
    parser.add_argument("--history", type=int, default=16)
    parser.add_argument("--seed", type=int, default=2299)
    parser.add_argument("--output", default="world_director_teacher_snapshots.jsonl")
    parser.add_argument("--report", default="world_director_teacher_snapshot_report.json")
    args = parser.parse_args()

    level_paths = sorted(Path(args.levels).glob("**/*.json"))
    levels = [LevelData.load(path) for path in level_paths]
    levels = [level for level in levels if level.initial_zone and level.zones]
    if not levels:
        raise SystemExit("no registered Level JSON assets found")
    if args.runs <= 0 or args.turns <= 0 or not 1 <= args.history <= 32:
        raise SystemExit("runs/turns must be positive and history must be in 1..32")

    rows = simulate(levels, args.runs, args.turns, args.history, args.seed)
    output = Path(args.output)
    with output.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    action_counts = Counter(row["state"]["actionKind"] for row in rows)
    legal_counts = Counter(",".join(row["state"]["legalProposals"]) for row in rows)
    report = {
        "schemaVersion": 1,
        "generator": "registered-level-safe-trajectory-v1",
        "seed": args.seed,
        "runs": args.runs,
        "turnsPerRun": args.turns,
        "rows": len(rows),
        "sessions": len({row["sessionId"] for row in rows}),
        "registeredLevelAssets": len(levels),
        "historyLimit": args.history,
        "actionCounts": dict(sorted(action_counts.items())),
        "legalSetCounts": dict(sorted(legal_counts.items())),
        "forbiddenOutputKeys": sorted(FORBIDDEN_OUTPUT_KEYS),
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
