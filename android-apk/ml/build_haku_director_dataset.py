#!/usr/bin/env python3
"""Build a grouped Haku-teacher dataset for the existing WorldDirector V1 input contract."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

LABELS = ("NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY")


def stable(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--min-confidence", type=float, default=0.60)
    args = parser.parse_args()

    raw = []
    with Path(args.input).open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            text = str(row.get("featureTextV1") or "").strip()
            label = str(row.get("proposal") or "").upper()
            confidence = float(row.get("confidence") or 0.0)
            if text and label in LABELS and confidence >= args.min_confidence:
                raw.append({
                    "text": text,
                    "intent": label,
                    "confidence": confidence,
                    "reason": str(row.get("reasonCode") or ""),
                })

    dedup = {}
    for row in raw:
        key = row["text"]
        previous = dedup.get(key)
        if previous is not None and previous["intent"] != row["intent"]:
            raise SystemExit("conflicting Haku labels for identical V1 feature text")
        if previous is None or row["confidence"] > previous["confidence"]:
            dedup[key] = row
    rows = list(dedup.values())
    if len(rows) < 20:
        raise SystemExit(f"too few Haku V1 labels: {len(rows)}")

    by_label = defaultdict(list)
    for row in rows:
        by_label[row["intent"]].append(row)
    missing = [label for label in LABELS if not by_label[label]]
    if missing:
        raise SystemExit(f"Haku dataset missing labels: {missing}")

    # Group split by exact runtime feature text. Each class contributes roughly 20% held-out rows,
    # so test accuracy is not inflated by duplicate V1 contexts.
    for label, group in by_label.items():
        group.sort(key=lambda row: stable(row["text"]))
        test_count = max(1, round(len(group) * 0.20))
        test_keys = {stable(row["text"]) for row in group[:test_count]}
        for row in group:
            row["split"] = "test" if stable(row["text"]) in test_keys else "train"

    rows.sort(key=lambda row: stable(row["text"]))
    with Path(args.output).open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["text", "intent", "split", "teacher_confidence", "teacher_reason"])
        writer.writeheader()
        for row in rows:
            writer.writerow({
                "text": row["text"],
                "intent": row["intent"],
                "split": row["split"],
                "teacher_confidence": f"{row['confidence']:.4f}",
                "teacher_reason": row["reason"],
            })

    split_counts = Counter((row["split"], row["intent"]) for row in rows)
    report = {
        "contract": "WORLD_DIRECTOR_PRESSURE_V1_HAKU_DISTILLATION",
        "sourceRows": len(raw),
        "uniqueRows": len(rows),
        "minConfidence": args.min_confidence,
        "labelCounts": dict(Counter(row["intent"] for row in rows)),
        "trainRows": sum(row["split"] == "train" for row in rows),
        "testRows": sum(row["split"] == "test" for row in rows),
        "splitLabelCounts": {f"{split}:{label}": count for (split, label), count in sorted(split_counts.items())},
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
