#!/usr/bin/env python3
"""Build the first seed dataset for the on-device BackroomsDirector policy.

This dataset does not encode Level IDs, puzzle answers, or evidence IDs. It teaches only a small
source-selection policy from observable/derived behavior features. The engine still owns legality.
"""
import csv
from pathlib import Path

OUT = Path(__file__).with_name("director_dataset.csv")

ZONES = [
    "zone_loop",
    "zone_memory_room",
    "zone_utility",
    "zone_transition_candidate",
    "zone_fluorescent",
    "zone_dark",
]
EVIDENCE = ["evidence_none", "evidence_some", "evidence_many"]
REVISIONS = ["revision_early", "revision_changed"]


def add(rows, label, action, visit, candidates, *, recent="recent_move", seen=(), unseen=(), repeats=48):
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
        tokens.extend(f"seen_{name}" for name in seen)
        tokens.extend(f"unseen_{name}" for name in unseen)
        # Include harmless context variation so the model cannot memorize one exact sentence.
        tokens.append(f"context_bucket_{index % 7}")
        split = "test" if index % 5 == 0 else "train"
        rows.append({"text": " ".join(tokens), "intent": label, "split": split})


def main():
    rows = []

    # SEARCH is always incremental investigation. Other source tokens may exist in the Level, but
    # a SEARCH action only ranks evidence already tagged SEARCH by the deterministic runtime.
    add(rows, "SEARCH", "action_search", "visit_first", ["search"], unseen=["search"])
    add(rows, "SEARCH", "action_search", "visit_repeat", ["search"], seen=["environment"], unseen=["search"])
    add(rows, "SEARCH", "action_search", "visit_deep", ["search"], seen=["anomaly", "survivor"], unseen=["search"])

    # Repeated traversal favors environmental pattern evidence: loops, relocated marks, geometry
    # drift, light changes, and other observations that only become meaningful after repetition.
    add(rows, "ENVIRONMENT", "action_explore", "visit_repeat", ["environment", "anomaly"], unseen=["environment"])
    add(rows, "ENVIRONMENT", "action_explore", "visit_deep", ["environment", "survivor"], seen=["survivor"], unseen=["environment"])
    add(rows, "ENVIRONMENT", "action_explore", "visit_repeat", ["environment"], seen=["anomaly"], unseen=["environment"])

    # A committed world-changing EXECUTE can make an anomaly newly observable. First-entry explore
    # with no survivor evidence also prefers anomaly over inventing a source that is not present.
    add(rows, "ANOMALY", "action_execute", "visit_first", ["anomaly", "environment"], recent="recent_execute", unseen=["anomaly"])
    add(rows, "ANOMALY", "action_execute", "visit_repeat", ["anomaly", "survivor"], recent="recent_execute", unseen=["anomaly"])
    add(rows, "ANOMALY", "action_explore", "visit_first", ["anomaly"], unseen=["anomaly"])
    add(rows, "ANOMALY", "action_explore", "visit_first", ["anomaly", "environment"], unseen=["anomaly", "environment"])

    # On first contact, legitimate survivor-origin evidence may surface before less personal clues.
    # The runtime has already checked that the evidence exists and its discoverConditions are met.
    add(rows, "SURVIVOR", "action_explore", "visit_first", ["survivor", "anomaly"], unseen=["survivor"])
    add(rows, "SURVIVOR", "action_explore", "visit_first", ["survivor", "environment"], seen=["environment"], unseen=["survivor"])
    add(rows, "SURVIVOR", "action_explore", "visit_first", ["survivor"], unseen=["survivor"])

    OUT.write_text("", encoding="utf-8")
    with OUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["text", "intent", "split"])
        writer.writeheader()
        writer.writerows(rows)

    counts = {label: sum(1 for row in rows if row["intent"] == label) for label in ("SEARCH", "ENVIRONMENT", "ANOMALY", "SURVIVOR")}
    print({"rows": len(rows), "counts": counts, "output": str(OUT)})


if __name__ == "__main__":
    main()
