#!/usr/bin/env python3
"""Convert an exported BackroomsDirector JSONL telemetry trace into a safe offline dataset.

Derives conservative target labels (NONE, MAZE_PRESSURE, ENTITY_PRESSURE, ITEM_OPPORTUNITY)
from observable outcome signals and assigns deterministic session-safe splits without treating
old model preferences alone as ground truth.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
from typing import Dict, List, Tuple, Any

SCHEMA_VERSION = 1
FORBIDDEN_KEYS = {
    "levelId", "zoneId", "evidenceId", "evidenceIds", "requiredFacts", "requiredActions",
    "solutionId", "escapeBlueprint", "playerText", "input", "apiKey", "secret",
}
FIELDS = [
    "sessionId", "actionKind", "features", "candidateSourceCounts", "modelPreferredSource",
    "modelAccepted", "fallbackUsed", "selectedSource", "surfacedCount",
    "discoveredEvidenceBefore", "discoveredEvidenceAfter", "discoveredFactBefore",
    "discoveredFactAfter", "unlockedFact", "worldRevisionBefore", "worldRevisionAfter",
]

# Canonical labels matching backrooms_director_labels.txt
VALID_LABELS = {"NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY"}


def session_split(session_id: str) -> str:
    """Deterministic session split based on sha256 digest: 70% train, 10% val, 20% test."""
    digest = hashlib.sha256(session_id.encode("utf-8")).hexdigest()
    val = int(digest[:8], 16) % 100
    if val < 70:
        return "train"
    elif val < 80:
        return "val"
    else:
        return "test"


def derive_label_and_validate(row: Dict[str, Any]) -> Tuple[str, str]:
    """
    Derive target label using observable outcome signals and context features.

    Target Label Taxonomy:
      - NONE: Safe abstention / execution with no world pressure change.
      - MAZE_PRESSURE: Exploration leading to maze/topology progress or environmental observation.
      - ENTITY_PRESSURE: Exploration leading to survivor/entity encounter discovery.
      - ITEM_OPPORTUNITY: Search or exploration leading to item discovery opportunity.

    Explicit Derivation Logic (No hidden heuristic magic):
    1. Candidate legality gate: the selected source must have been legal (candidateSourceCounts > 0).
    2. Observable outcome signal gate:
       At least one of the following signals must be positive:
         - surfacedCount > 0
         - discoveredEvidenceAfter > discoveredEvidenceBefore
         - discoveredFactAfter > discoveredFactBefore
         - unlockedFact is True
         - worldRevisionAfter > worldRevisionBefore
    3. If positive outcome signal is absent, row is marked NONE (abstention) if legal, or rejected if ambiguous.
    4. If positive outcome signal is present:
         - actionKind == SEARCH -> ITEM_OPPORTUNITY
         - actionKind == EXPLORE:
             * selectedSource == "SURVIVOR" or "candidate_entity_pressure" in features -> ENTITY_PRESSURE
             * selectedSource in ("ANOMALY", "ENVIRONMENT") or "candidate_maze_pressure" in features -> MAZE_PRESSURE
             * "candidate_item_opportunity" in features -> ITEM_OPPORTUNITY
             * Else -> NONE
         - actionKind == EXECUTE -> NONE
    """
    selected_source = row.get("selectedSource", "")
    counts = row.get("candidateSourceCounts")
    if isinstance(counts, str):
        try:
            counts = json.loads(counts)
        except Exception:
            return "", "malformed_candidateSourceCounts_json"

    if not isinstance(counts, dict):
        return "", "candidateSourceCounts_not_dict"

    # 1. Candidate legality gate: selected source must have candidate count > 0 if selected
    if selected_source and counts.get(selected_source, 0) <= 0:
        return "", f"illegal_selected_source:{selected_source}_count_{counts.get(selected_source, 0)}"

    # 2. Observable outcome signal gate
    surfaced = row.get("surfacedCount", 0)
    ev_before = row.get("discoveredEvidenceBefore", 0)
    ev_after = row.get("discoveredEvidenceAfter", 0)
    fact_before = row.get("discoveredFactBefore", 0)
    fact_after = row.get("discoveredFactAfter", 0)
    unlocked = row.get("unlockedFact", False)
    rev_before = row.get("worldRevisionBefore", 0)
    rev_after = row.get("worldRevisionAfter", 0)

    has_positive_outcome = (
        surfaced > 0 or
        ev_after > ev_before or
        fact_after > fact_before or
        bool(unlocked) or
        rev_after > rev_before
    )

    action_kind = str(row.get("actionKind", "")).upper()
    features = str(row.get("features", ""))

    if not has_positive_outcome:
        # Without positive outcome, repeated actions without world-state change yield NONE (abstention)
        return "NONE", ""

    # Derive intent class based on action kind, selected source, and features
    if action_kind == "SEARCH":
        return "ITEM_OPPORTUNITY", ""
    elif action_kind == "EXPLORE":
        if selected_source == "SURVIVOR" or "candidate_entity_pressure" in features:
            return "ENTITY_PRESSURE", ""
        elif selected_source in ("ANOMALY", "ENVIRONMENT") or "candidate_maze_pressure" in features:
            return "MAZE_PRESSURE", ""
        elif "candidate_item_opportunity" in features:
            return "ITEM_OPPORTUNITY", ""
        else:
            return "NONE", ""
    elif action_kind == "EXECUTE":
        return "NONE", ""
    else:
        return "NONE", ""


def process_telemetry_source(input_path: Path) -> Tuple[List[Dict[str, Any]], Dict[str, int], Dict[str, int]]:
    """Reads JSONL telemetry file/directory, validates schema, rejects malformed/forbidden rows, and derives dataset rows."""
    raw_lines: List[str] = []
    if input_path.is_file():
        raw_lines = input_path.read_text(encoding="utf-8").splitlines()
    elif input_path.is_dir():
        for jsonl_file in sorted(input_path.glob("*.jsonl")):
            raw_lines.extend(jsonl_file.read_text(encoding="utf-8").splitlines())
    else:
        return [], {"error_file_not_found": 1}, {}

    dataset_rows: List[Dict[str, Any]] = []
    rejection_counts: Dict[str, int] = {}
    session_row_counts: Dict[str, int] = {}

    for line_no, raw in enumerate(raw_lines, start=1):
        if not raw.strip():
            continue
        try:
            value = json.loads(raw)
        except Exception:
            rejection_counts["malformed_json"] = rejection_counts.get("malformed_json", 0) + 1
            continue

        if not isinstance(value, dict):
            rejection_counts["row_not_object"] = rejection_counts.get("row_not_object", 0) + 1
            continue

        forbidden = FORBIDDEN_KEYS.intersection(value)
        if forbidden:
            key = f"forbidden_keys:{','.join(sorted(forbidden))}"
            rejection_counts[key] = rejection_counts.get(key, 0) + 1
            continue

        if value.get("schemaVersion") != SCHEMA_VERSION:
            key = f"unsupported_schemaVersion:{value.get('schemaVersion')}"
            rejection_counts[key] = rejection_counts.get(key, 0) + 1
            continue

        missing = [field for field in FIELDS if field not in value]
        if missing:
            key = f"missing_fields:{','.join(missing)}"
            rejection_counts[key] = rejection_counts.get(key, 0) + 1
            continue

        session_id = str(value["sessionId"])
        label, rejection_reason = derive_label_and_validate(value)
        if rejection_reason:
            rejection_counts[rejection_reason] = rejection_counts.get(rejection_reason, 0) + 1
            continue

        split = session_split(session_id)
        feature_text = str(value["features"])

        dataset_rows.append({
            "text": feature_text,
            "intent": label,
            "split": split,
            "sessionId": session_id,
        })
        session_row_counts[session_id] = session_row_counts.get(session_id, 0) + 1

    return dataset_rows, rejection_counts, session_row_counts


def main():
    parser = argparse.ArgumentParser(description="Convert BackroomsDirector telemetry into safe LiteRT dataset.")
    parser.add_argument("--input", type=Path, help="JSONL file or directory exported by Android exportDirectorTelemetry()")
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("director_telemetry_dataset.csv"))
    parser.add_argument("--report", type=Path, default=Path(__file__).with_name("director_telemetry_stats.json"))
    args = parser.parse_args()

    if args.input is None or not args.input.exists():
        report = {
            "status": "DATA_BLOCKED",
            "message": "No real telemetry data input found.",
            "total_rows_read": 0,
            "accepted_rows": 0,
            "rejections": {"input_missing": 1},
            "sessions": 0,
            "splits": {"train": 0, "val": 0, "test": 0},
        }
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
        return

    rows, rejections, session_counts = process_telemetry_source(args.input)
    splits = {
        "train": sum(1 for r in rows if r["split"] == "train"),
        "val": sum(1 for r in rows if r["split"] == "val"),
        "test": sum(1 for r in rows if r["split"] == "test"),
    }

    report = {
        "status": "OK" if len(session_counts) >= 5 else "DATA_BLOCKED_TOO_FEW_SESSIONS",
        "total_rows_accepted": len(rows),
        "rejections": rejections,
        "unique_sessions": len(session_counts),
        "splits_row_counts": splits,
        "session_row_counts": session_counts,
    }

    if rows:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=["text", "intent", "split", "sessionId"])
            writer.writeheader()
            writer.writerows(rows)

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
