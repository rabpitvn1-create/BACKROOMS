#!/usr/bin/env python3
"""Build the synthetic seed dataset for the on-device WorldDirector proposal policy.

This dataset belongs only to WORLD_DIRECTOR_PRESSURE_V1. The model never sees Level IDs, zone IDs,
escape/transition tags, puzzle answers, evidence IDs, required facts/actions, inventory contents, or
Entity identities. Core computes the legal proposal set first; LiteRT only ranks broad pressure
classes inside that set.
"""
import csv
from pathlib import Path

OUT = Path(__file__).with_name("director_dataset.csv")
LABELS_PATH = Path(__file__).parents[1] / "app/src/main/assets/models/backrooms_director_labels.txt"
WORLD_DIRECTOR_LABELS = ("NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY")

ZONES = [
    "zone_loop",
    "zone_memory_room",
    "zone_utility",
    "zone_fluorescent",
    "zone_dark",
    "zone_wet",
]
EVIDENCE = ["evidence_none", "evidence_some", "evidence_many"]
REVISIONS = ["revision_early", "revision_changed"]


def add(rows, label, action, visit, candidates, *, recent="recent_move", repeats=64):
    for index in range(repeats):
        tokens = [
            action,
            visit,
            REVISIONS[index % len(REVISIONS)],
            recent,
            ZONES[index % len(ZONES)],
            EVIDENCE[(index // 2) % len(EVIDENCE)],
        ]
        tokens.extend(f"candidate_{name}" for name in candidates)
        tokens.append(f"context_bucket_{index % 11}")
        split = "test" if index % 5 == 0 else "train"
        rows.append({"text": " ".join(tokens), "intent": label, "split": split})


def validate_label_contract():
    labels = tuple(line.strip() for line in LABELS_PATH.read_text(encoding="utf-8").splitlines() if line.strip())
    if labels != WORLD_DIRECTOR_LABELS:
        raise SystemExit(
            "WorldDirector label contract mismatch: "
            f"expected={WORLD_DIRECTOR_LABELS!r} actual={labels!r}. "
            "Evidence-source labels/telemetry must use a separate model contract."
        )


def main():
    validate_label_contract()
    rows = []

    # NONE is the safe abstention class. EXECUTE never asks the director to create world pressure,
    # and any action with no legal pressure candidate remains NONE.
    add(rows, "NONE", "action_execute", "visit_first", ["none"], recent="recent_execute")
    add(rows, "NONE", "action_execute", "visit_repeat", ["none"], recent="recent_execute")
    add(rows, "NONE", "action_search", "visit_first", ["none"])

    # Repeated/deep traversal is where local maze pressure is useful. Core only advertises this
    # candidate when proceduralTopology is enabled and the local graph passes the liveness gate.
    add(rows, "MAZE_PRESSURE", "action_explore", "visit_repeat", ["none", "maze_pressure", "entity_pressure"])
    add(rows, "MAZE_PRESSURE", "action_explore", "visit_deep", ["none", "maze_pressure", "item_opportunity"])
    add(rows, "MAZE_PRESSURE", "action_explore", "visit_deep", ["none", "maze_pressure"])

    # First/early exploration can request combat pressure, but Core still owns allowEntities,
    # existing-combat checks, encounter probability, Entity selection, and actual combat start.
    add(rows, "ENTITY_PRESSURE", "action_explore", "visit_first", ["none", "entity_pressure", "item_opportunity"])
    add(rows, "ENTITY_PRESSURE", "action_explore", "visit_first", ["none", "entity_pressure", "maze_pressure"])
    add(rows, "ENTITY_PRESSURE", "action_explore", "visit_repeat", ["none", "entity_pressure"])

    # Search/resource pacing may request an item opportunity. This never grants or names an item;
    # inventory authority and acquisition remain entirely in Core.
    add(rows, "ITEM_OPPORTUNITY", "action_search", "visit_first", ["none", "item_opportunity"])
    add(rows, "ITEM_OPPORTUNITY", "action_search", "visit_repeat", ["none", "item_opportunity"])
    add(rows, "ITEM_OPPORTUNITY", "action_explore", "visit_deep", ["none", "item_opportunity", "entity_pressure"])

    OUT.write_text("", encoding="utf-8")
    with OUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["text", "intent", "split"])
        writer.writeheader()
        writer.writerows(rows)

    counts = {label: sum(1 for row in rows if row["intent"] == label) for label in WORLD_DIRECTOR_LABELS}
    print({"contract": "WORLD_DIRECTOR_PRESSURE_V1", "rows": len(rows), "counts": counts, "output": str(OUT)})


if __name__ == "__main__":
    main()
