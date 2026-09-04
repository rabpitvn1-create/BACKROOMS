#!/usr/bin/env python3
"""Expand a compact Gemini V2 label pack against the deterministic V2 context universe.

The committed label pack stores only sampleId/proposal/confidence/reasonCode. featureTextV2 and the
Core legal proposal set are reconstructed from the deterministic simulator output by exact sampleId,
which keeps the repository artifact small without weakening contract validation.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

PROPOSALS = {"NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY"}


def stable_id(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:24]


def load_contexts(path: Path) -> dict[str, dict]:
    result: dict[str, dict] = {}
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            sample_id = str(row.get("sampleIdV2") or "").strip()
            text = str(row.get("featureTextV2") or "").strip()
            legal = [str(value).upper() for value in ((row.get("state") or {}).get("legalProposals") or [])]
            if not sample_id or not text or sample_id != stable_id(text):
                continue
            if not legal or any(value not in PROPOSALS for value in legal):
                raise SystemExit(f"invalid legal V2 context for {sample_id}")
            compact = {"featureTextV2": text, "legalProposals": legal}
            previous = result.get(sample_id)
            if previous is not None and previous != compact:
                raise SystemExit(f"conflicting deterministic V2 context for {sample_id}")
            result[sample_id] = compact
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contexts", required=True)
    parser.add_argument("--labels", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()

    contexts = load_contexts(Path(args.contexts))
    rows = []
    seen = set()
    proposal_counts = Counter()
    with Path(args.labels).open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"sampleId", "proposal", "confidence", "reasonCode"}
        if not required.issubset(reader.fieldnames or []):
            raise SystemExit(f"compact Gemini V2 label pack missing columns: {sorted(required)}")
        for source in reader:
            sample_id = str(source.get("sampleId") or "").strip()
            if not sample_id or sample_id in seen:
                raise SystemExit(f"missing/duplicate Gemini V2 sample id: {sample_id}")
            seen.add(sample_id)
            context = contexts.get(sample_id)
            if context is None:
                raise SystemExit(f"Gemini V2 label not reproducible from context universe: {sample_id}")
            proposal = str(source.get("proposal") or "").upper()
            try:
                confidence = float(source.get("confidence"))
            except (TypeError, ValueError):
                raise SystemExit(f"invalid Gemini confidence for {sample_id}")
            if proposal not in context["legalProposals"] or proposal not in PROPOSALS:
                raise SystemExit(f"illegal Gemini V2 proposal for {sample_id}: {proposal}")
            if not 0.0 <= confidence <= 1.0:
                raise SystemExit(f"out-of-range Gemini confidence for {sample_id}")
            reason = str(source.get("reasonCode") or "OTHER").upper()
            rows.append({
                "sampleId": sample_id,
                "teacher": "GEMINI",
                "model": "gemini-3.5-flash-lite",
                "featureTextV2": context["featureTextV2"],
                "legalProposals": context["legalProposals"],
                "label": {
                    "proposal": proposal,
                    "confidence": confidence,
                    "reasonCode": reason,
                },
            })
            proposal_counts[proposal] += 1

    rows.sort(key=lambda row: row["sampleId"])
    with Path(args.output).open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    report = {
        "contract": "WORLD_DIRECTOR_PRESSURE_V2_GEMINI_EXPANDED",
        "contextUniverse": len(contexts),
        "labels": len(rows),
        "proposalCounts": dict(sorted(proposal_counts.items())),
        "allIdsReproduced": len(rows) == len(seen),
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0 if rows else 2


if __name__ == "__main__":
    raise SystemExit(main())
