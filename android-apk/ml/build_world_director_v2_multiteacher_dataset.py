#!/usr/bin/env python3
"""Merge Gemini + Haku labels into a conservative WorldDirector V2 distillation dataset.

Exact cross-teacher disagreements are never silently resolved. Agreement receives the highest sample
weight, Haku-only and high-confidence Gemini-only coverage are retained at lower weight, and the
report preserves disagreement statistics for evaluation.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

LABELS = ("NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY")


def stable_id(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:24]


def read_teacher(path: Path, teacher: str) -> dict[str, dict]:
    result: dict[str, dict] = {}
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            sample_id = str(row.get("sampleId") or "").strip()
            text = str(row.get("featureTextV2") or "").strip()
            legal = tuple(str(value).upper() for value in row.get("legalProposals") or [])
            label = row.get("label") or {}
            proposal = str(label.get("proposal") or "").upper()
            try:
                confidence = float(label.get("confidence"))
            except (TypeError, ValueError):
                continue
            if not sample_id or not text or sample_id != stable_id(text):
                continue
            if proposal not in LABELS or proposal not in legal or not 0.0 <= confidence <= 1.0:
                continue
            normalized = {
                "sampleId": sample_id,
                "text": text,
                "legal": legal,
                "proposal": proposal,
                "confidence": confidence,
                "teacher": teacher,
            }
            previous = result.get(sample_id)
            if previous is not None and previous != normalized:
                raise SystemExit(f"conflicting {teacher} rows for {sample_id}")
            result[sample_id] = normalized
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gemini", required=True)
    parser.add_argument("--haku", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--gemini-min-confidence", type=float, default=0.75)
    parser.add_argument("--haku-min-confidence", type=float, default=0.65)
    parser.add_argument("--agreement-min-confidence", type=float, default=0.55)
    args = parser.parse_args()

    gemini = read_teacher(Path(args.gemini), "GEMINI")
    haku = read_teacher(Path(args.haku), "HAKU")
    all_ids = sorted(set(gemini) | set(haku))
    overlap_ids = sorted(set(gemini) & set(haku))
    agreements = 0
    disagreements = []
    accepted = []
    dropped_low_confidence = 0

    for sample_id in all_ids:
        g = gemini.get(sample_id)
        h = haku.get(sample_id)
        if g and h:
            if g["text"] != h["text"] or g["legal"] != h["legal"]:
                raise SystemExit(f"teacher input contract mismatch for {sample_id}")
            if g["proposal"] != h["proposal"]:
                disagreements.append({
                    "sampleId": sample_id,
                    "gemini": g["proposal"],
                    "geminiConfidence": g["confidence"],
                    "haku": h["proposal"],
                    "hakuConfidence": h["confidence"],
                })
                continue
            if min(g["confidence"], h["confidence"]) < args.agreement_min_confidence:
                dropped_low_confidence += 1
                continue
            agreements += 1
            confidence = (g["confidence"] + h["confidence"]) / 2.0
            accepted.append({
                "sampleId": sample_id,
                "text": g["text"],
                "intent": g["proposal"],
                "sample_weight": 2.5 + confidence,
                "teacher_source": "GEMINI_HAKU_AGREE",
                "teacher_confidence": confidence,
            })
            continue

        if h:
            if h["confidence"] < args.haku_min_confidence:
                dropped_low_confidence += 1
                continue
            accepted.append({
                "sampleId": sample_id,
                "text": h["text"],
                "intent": h["proposal"],
                "sample_weight": 1.5 * h["confidence"],
                "teacher_source": "HAKU_ONLY",
                "teacher_confidence": h["confidence"],
            })
            continue

        if g:
            if g["confidence"] < args.gemini_min_confidence:
                dropped_low_confidence += 1
                continue
            accepted.append({
                "sampleId": sample_id,
                "text": g["text"],
                "intent": g["proposal"],
                "sample_weight": g["confidence"],
                "teacher_source": "GEMINI_ONLY",
                "teacher_confidence": g["confidence"],
            })

    if len(accepted) < 100:
        raise SystemExit(f"too few accepted V2 teacher rows: {len(accepted)}")

    by_label = defaultdict(list)
    for row in accepted:
        by_label[row["intent"]].append(row)
    missing = [label for label in LABELS if len(by_label[label]) < 5]
    if missing:
        raise SystemExit(f"V2 dataset lacks enough rows for labels: {missing}")

    # Deterministic stratified 80/20 split by exact model input id.
    for label, group in by_label.items():
        group.sort(key=lambda row: hashlib.sha256(row["sampleId"].encode()).hexdigest())
        test_count = max(1, round(len(group) * 0.20))
        test_ids = {row["sampleId"] for row in group[:test_count]}
        for row in group:
            row["split"] = "test" if row["sampleId"] in test_ids else "train"

    accepted.sort(key=lambda row: row["sampleId"])
    with Path(args.output).open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=[
            "sampleId", "text", "intent", "split", "sample_weight", "teacher_source", "teacher_confidence"
        ])
        writer.writeheader()
        for row in accepted:
            writer.writerow({
                "sampleId": row["sampleId"],
                "text": row["text"],
                "intent": row["intent"],
                "split": row["split"],
                "sample_weight": f"{row['sample_weight']:.6f}",
                "teacher_source": row["teacher_source"],
                "teacher_confidence": f"{row['teacher_confidence']:.6f}",
            })

    source_counts = Counter(row["teacher_source"] for row in accepted)
    label_counts = Counter(row["intent"] for row in accepted)
    split_counts = Counter(row["split"] for row in accepted)
    report = {
        "contract": "WORLD_DIRECTOR_PRESSURE_V2_MULTITEACHER",
        "geminiRows": len(gemini),
        "hakuRows": len(haku),
        "teacherOverlap": len(overlap_ids),
        "teacherAgreements": agreements,
        "teacherDisagreements": len(disagreements),
        "teacherAgreementRate": round(agreements / max(1, len(overlap_ids)), 6),
        "droppedLowConfidence": dropped_low_confidence,
        "acceptedRows": len(accepted),
        "sourceCounts": dict(sorted(source_counts.items())),
        "labelCounts": dict(sorted(label_counts.items())),
        "splitCounts": dict(sorted(split_counts.items())),
        "disagreementExamples": disagreements[:50],
        "disagreementsUsedForTraining": False,
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
