#!/usr/bin/env python3
"""Validate/export BackroomsDirector evidence-selection telemetry as a safe offline CSV.

IMPORTANT CONTRACT BOUNDARY:
This telemetry belongs to deterministic registered-Level evidence selection (SEARCH / ENVIRONMENT /
ANOMALY / SURVIVOR). The production LiteRT asset was retargeted by PR #174 to WorldDirector
pressure proposals (NONE / MAZE_PRESSURE / ENTITY_PRESSURE / ITEM_OPPORTUNITY). Therefore this
script intentionally does NOT derive WorldDirector labels, rewards, or a production training dataset.
A future evidence-selector experiment may consume this CSV under a separate model/label contract.
"""
import argparse
import csv
import json
from pathlib import Path

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


def load_rows(path: Path):
    rows = []
    for line_no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError(f"line {line_no}: telemetry row must be an object")
        forbidden = FORBIDDEN_KEYS.intersection(value)
        if forbidden:
            raise ValueError(f"line {line_no}: forbidden telemetry keys: {sorted(forbidden)}")
        if value.get("schemaVersion") != SCHEMA_VERSION:
            raise ValueError(f"line {line_no}: unsupported schemaVersion {value.get('schemaVersion')!r}")
        missing = [field for field in FIELDS if field not in value]
        if missing:
            raise ValueError(f"line {line_no}: missing fields: {missing}")
        row = {field: value[field] for field in FIELDS}
        row["candidateSourceCounts"] = json.dumps(row["candidateSourceCounts"], sort_keys=True, separators=(",", ":"))
        rows.append(row)
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path, help="JSONL exported by Android.exportDirectorTelemetry()")
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("director_telemetry_dataset.csv"))
    args = parser.parse_args()

    rows = load_rows(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print({"contract": "BACKROOMS_EVIDENCE_TELEMETRY_V1", "rows": len(rows), "output": str(args.output)})


if __name__ == "__main__":
    main()
