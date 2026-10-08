#!/usr/bin/env python3
"""P1.5 authoring -> candidate DB compilation, shadow only.

This intentionally starts from the final production-patched DB as a schema baseline.
Only files under build/reports are written. Shipping assets are never rewritten.
"""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
OUT = ROOT / "app/build/reports/canon-p1"
RUNTIME_FIELDS = (
    "id", "domain", "kind", "text", "source", "authority", "mutability",
    "priority", "tags", "references", "affordances",
)


def write(name: str, value: dict) -> None:
    (OUT / name).write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def main() -> None:
    # Use the actual DB AFTER production patch chain, not a stale source snapshot.
    raw = SOURCE.read_text(encoding="utf-8")
    baseline = json.loads(raw)
    assert isinstance(baseline, dict) and isinstance(baseline.get("records"), list)
    OUT.mkdir(parents=True, exist_ok=True)
    baseline_records = baseline["records"]
    # Authoring-only metadata deliberately lives outside runtime record payloads.
    authoring = {
        "schemaVersion": baseline.get("schemaVersion"),
        "representation": "canon_p1_shadow_authoring_v1",
        "records": [
            {"runtime": copy.deepcopy(record), "authoring": {"rev": 1, "status": "active"}}
            for record in baseline_records
        ],
    }
    write("compiler-authoring-shadow.json", authoring)

    # Candidate compiler: preserve the exact runtime schema, arrays and source fields.
    compiled = {
        "schemaVersion": authoring["schemaVersion"],
        "records": [copy.deepcopy(entry["runtime"]) for entry in authoring["records"]],
    }
    write("compiler-candidate.json", compiled)

    failures = []
    before_ids = [record.get("id") for record in baseline_records]
    after_ids = [record.get("id") for record in compiled["records"]]
    if before_ids != after_ids:
        failures.append({"field": "ordered_record_ids", "reason": "ordered_ids_differ"})
    field_counts = {field: 0 for field in RUNTIME_FIELDS}
    for index, (before, after) in enumerate(zip(baseline_records, compiled["records"])):
        for field in RUNTIME_FIELDS:
            if field not in before or field not in after or before[field] != after[field]:
                failures.append({
                    "index": index,
                    "id": before.get("id"),
                    "field": field,
                    "reason": "missing_or_nonidentical",
                })
            else:
                field_counts[field] += 1
    report = {
        "schemaVersion": 1,
        "mode": "shadow_only",
        "source": "final_production_patched_knowledge_db",
        "shippingAssetUnchanged": SOURCE.read_text(encoding="utf-8") == raw,
        "baselineRecordCount": len(baseline_records),
        "candidateRecordCount": len(compiled["records"]),
        "sameOrderedRecordIds": before_ids == after_ids,
        "sameRecordCount": len(baseline_records) == len(compiled["records"]),
        "fieldMatchCounts": field_counts,
        "runtimeFields": list(RUNTIME_FIELDS),
        "baselineSha256": hashlib.sha256(raw.encode("utf-8")).hexdigest(),
        "candidateSha256": hashlib.sha256(
            (OUT / "compiler-candidate.json").read_bytes()
        ).hexdigest(),
        "failures": failures,
        "passed": not failures and len(baseline_records) == len(compiled["records"]),
    }
    write("compiler-data-parity.json", report)
    print("Canon P1.5 compiler data parity: records=", len(baseline_records),
          "passed=", report["passed"], "failures=", len(failures))
    if not report["passed"]:
        raise SystemExit("Compiler candidate data parity failed")


if __name__ == "__main__":
    main()
