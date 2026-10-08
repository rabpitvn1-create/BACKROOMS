#!/usr/bin/env python3
"""P1.4 read-only lint of the shipping knowledge registry; no policy cutover."""
from __future__ import annotations

from collections import defaultdict
from hashlib import sha256
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INPUT = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
OUTPUT = ROOT / "app/build/reports/canon-p1/schema-lint.json"
ID_FORMAT = re.compile(r"^[A-Z][A-Z0-9_]*(?:\.[A-Z0-9_]+)+$")
AFFORDANCE_FORMAT = re.compile(r"^[a-z][a-z0-9_]*$")
REQUIRED_STRINGS = ("id", "domain", "kind", "text", "authority", "mutability")


def lint(raw: str) -> dict:
    findings = []

    def flag(severity: str, code: str, pointer: str, detail: str) -> None:
        findings.append({
            "severity": severity,
            "code": code,
            "pointer": pointer,
            "detail": detail,
        })

    report = {
        "schemaVersion": 1,
        "mode": "shadow_only",
        "source": "app/src/main/assets/knowledge/knowledge_db.json",
        "inputSha256": sha256(raw.encode("utf-8")).hexdigest(),
        "priorityPolicy": "observed_registry_band_warning_only",
        "observedPriorityRange": None,
        "recordCount": 0,
        "counts": {"ERROR": 0, "WARNING": 0},
        "findings": findings,
    }

    try:
        root = json.loads(raw)
    except json.JSONDecodeError as exc:
        flag("ERROR", "INVALID_JSON", "$", str(exc))
        return finish(report)
    if not isinstance(root, dict):
        flag("ERROR", "ROOT_NOT_OBJECT", "$", "JSONObject parser requires an object")
        return finish(report)
    records = root.get("records")
    if not isinstance(records, list):
        flag("ERROR", "RECORDS_NOT_ARRAY", "$.records", "getJSONArray(records) requires an array")
        return finish(report)
    report["recordCount"] = len(records)

    seen_ids = {}
    references = []
    tags_by_normalized_value = defaultdict(set)
    observed_priorities = [
        item["priority"] for item in records
        if isinstance(item, dict)
        and type(item.get("priority")) is int
    ]
    if observed_priorities:
        report["observedPriorityRange"] = {
            "min": min(observed_priorities),
            "max": max(observed_priorities),
        }
    observed_min = min(observed_priorities, default=None)
    observed_max = max(observed_priorities, default=None)

    for index, record in enumerate(records):
        pointer = f"$.records[{index}]"
        if not isinstance(record, dict):
            flag("ERROR", "RECORD_NOT_OBJECT", pointer, "getJSONObject requires an object")
            continue
        for name in REQUIRED_STRINGS:
            value = record.get(name)
            if value is None:
                flag("ERROR", "MISSING_RUNTIME_FIELD", f"{pointer}.{name}", "getString requires this field")
            elif not isinstance(value, str):
                flag("WARNING", "COERCED_RUNTIME_STRING", f"{pointer}.{name}", "Runtime getString may coerce a non-string")
            elif not value.strip():
                flag("WARNING", "EMPTY_RUNTIME_STRING", f"{pointer}.{name}", "Empty runtime field is not rejected by the parser")
        record_id = record.get("id")
        if isinstance(record_id, str):
            if record_id in seen_ids:
                flag("ERROR", "DUPLICATE_ID", f"{pointer}.id",
                     f"Duplicate id of $.records[{seen_ids[record_id]}] causes runtime require failure")
            else:
                seen_ids[record_id] = index
            if record_id and not ID_FORMAT.fullmatch(record_id):
                flag("WARNING", "UNCONTRACTED_ID_FORMAT", f"{pointer}.id",
                     "Observed naming convention only; no official namespace contract enforced")
        source = record.get("source")
        if not isinstance(source, dict):
            flag("ERROR", "INVALID_SOURCE", f"{pointer}.source",
                 "Runtime getJSONObject(source) requires an object")
        else:
            document = source.get("document")
            anchor = source.get("anchor")
            if document is None:
                flag("ERROR", "MISSING_SOURCE_DOCUMENT", f"{pointer}.source.document",
                     "Runtime getString(document) requires a value")
            elif not isinstance(document, str) or not document.strip():
                flag("WARNING", "INVALID_SOURCE_DOCUMENT", f"{pointer}.source.document",
                     "Source document should be a nonempty string")
            elif document.startswith("/") or ".." in Path(document).parts:
                flag("WARNING", "UNUSUAL_SOURCE_DOCUMENT", f"{pointer}.source.document",
                     "Absolute or traversing source paths should be reviewed")
            if not isinstance(anchor, str) or not anchor.strip():
                flag("WARNING", "INVALID_SOURCE_ANCHOR", f"{pointer}.source.anchor",
                     "Runtime optString accepts missing/empty anchor but provenance is unusable")
        priority = record.get("priority")
        if type(priority) is not int:
            flag("WARNING", "NON_INTEGER_PRIORITY", f"{pointer}.priority",
                 "Runtime optInt may default/coerce to 80; no authoring tier allowed")
        elif observed_min is not None and observed_max is not None and not observed_min <= priority <= observed_max:
            flag("WARNING", "OUTSIDE_OBSERVED_PRIORITY_RANGE", f"{pointer}.priority",
                 "Outside empirical registry range; NOT an enforced runtime limit")
        for authored_tier in ("tier", "budgetTier", "derivedBudgetTier"):
            if authored_tier in record:
                flag("WARNING", "AUTHORED_TIER_FORBIDDEN", f"{pointer}.{authored_tier}",
                     "P1 tier is derived from live budgeting, not authored metadata")

        domain = record.get("domain")
        tags = record.get("tags", [])
        if not isinstance(tags, list):
            flag("WARNING", "TAGS_NOT_ARRAY", f"{pointer}.tags", "Runtime optJSONArray treats this as absent")
            tags = []
        usable_tags = set()
        local_tags = set()
        for tag_index, tag in enumerate(tags):
            tag_ptr = f"{pointer}.tags[{tag_index}]"
            if not isinstance(tag, str):
                flag("WARNING", "TAG_NOT_STRING", tag_ptr, "Runtime optString coerces tag entries")
                continue
            normalized = tag.strip().lower()
            if not normalized:
                flag("WARNING", "EMPTY_TAG", tag_ptr, "Ignored by runtime strings()")
                continue
            if normalized in local_tags:
                flag("WARNING", "DUPLICATE_NORMALIZED_TAG", tag_ptr,
                     "The runtime Set collapses repeated normalized tags")
            local_tags.add(normalized)
            if len(normalized) >= 3:
                usable_tags.add(normalized)
            if isinstance(record_id, str) and record_id:
                tags_by_normalized_value[normalized].add(record_id)
        if domain in ("ENTITY", "ITEM") and not usable_tags:
            flag("WARNING", "ENTITY_ITEM_WITHOUT_USABLE_TAGS", f"{pointer}.tags",
                 "Direct Entity/Item lookup requires a normalized tag of at least 3 characters")

        for field in ("references", "affordances"):
            values = record.get(field, [])
            if not isinstance(values, list):
                flag("WARNING", "NON_ARRAY_METADATA", f"{pointer}.{field}",
                     "Runtime optJSONArray treats this as absent")
                continue
            for value_index, value in enumerate(values):
                element_ptr = f"{pointer}.{field}[{value_index}]"
                if not isinstance(value, str):
                    flag("WARNING", "NON_STRING_METADATA", element_ptr,
                         "Runtime optString coerces metadata entries")
                    continue
                if not value.strip():
                    flag("WARNING", "EMPTY_METADATA_VALUE", element_ptr,
                         "Runtime ignores empty values")
                if field == "references" and value.strip():
                    references.append((value.strip(), element_ptr))
                if field == "affordances" and value.strip() and not AFFORDANCE_FORMAT.fullmatch(value.strip()):
                    flag("WARNING", "UNUSUAL_AFFORDANCE", element_ptr,
                         "Outside observed snake_case affordance convention")

    for target_id, pointer in references:
        if target_id not in seen_ids:
            flag("WARNING", "DANGLING_REFERENCE", pointer,
                 "Runtime reference traversal skips the missing target")
    for tag, record_ids in sorted(tags_by_normalized_value.items()):
        if len(record_ids) > 1:
            flag("WARNING", "SHARED_NORMALIZED_TAG", "$.records[*].tags",
                 f"Tag {tag!r} maps to multiple records: {', '.join(sorted(record_ids))}")

    return finish(report)


def finish(report: dict) -> dict:
    for item in report["findings"]:
        report["counts"][item["severity"]] += 1
    return report


def main() -> None:
    report = lint(INPUT.read_text(encoding="utf-8"))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Canon P1.4 lint: records={report['recordCount']} "
          f"errors={report['counts']['ERROR']} warnings={report['counts']['WARNING']}")
    # The CI verification step owns ERROR-only gating. Keep this report available on RED.


if __name__ == "__main__":
    main()
