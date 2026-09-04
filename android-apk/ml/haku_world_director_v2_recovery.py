#!/usr/bin/env python3
"""Recover a bounded Haku V2 teacher batch with durable per-request checkpoints.

This wrapper exists because the first long-running V2 teacher job reached the GitHub Actions timeout
before the original teacher could flush its final report. It deliberately uses only the remaining
safety reserve from the previously authorized budget. Every successful request is checkpointed so a
runner cancellation cannot discard paid labels again.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
from collections import Counter
from pathlib import Path

import haku_world_director_v2_teacher as base


def write_checkpoint(
    output: Path,
    report_path: Path,
    labels: list[dict],
    contexts_count: int,
    priority_ids: set[str],
    requests: int,
    estimated_spend: float,
    prompt_tokens: int,
    completion_tokens: int,
    failures: list[dict],
    stopped: str,
    args: argparse.Namespace,
) -> None:
    by_id = {row["sampleId"]: row for row in labels}
    stable_labels = [by_id[key] for key in sorted(by_id)]
    with output.open("w", encoding="utf-8") as handle:
        for row in stable_labels:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    proposal_counts = Counter(row["label"]["proposal"] for row in stable_labels)
    overlap = sum(row["sampleId"] in priority_ids for row in stable_labels)
    report = {
        "schemaVersion": 2,
        "contract": "WORLD_DIRECTOR_PRESSURE_V2_RECOVERY",
        "teacher": "HAKU",
        "model": args.model,
        "uniqueContextsConsidered": contexts_count,
        "geminiPriorityIds": len(priority_ids),
        "geminiOverlapLabeled": overlap,
        "labels": len(stable_labels),
        "requests": requests,
        "estimatedSpendVnd": round(estimated_spend, 4),
        "userBudgetVnd": args.budget_vnd,
        "safetyReserveVnd": args.safety_reserve_vnd,
        "effectiveCapVnd": max(0.0, args.budget_vnd - args.safety_reserve_vnd),
        "promptTokens": prompt_tokens,
        "completionTokens": completion_tokens,
        "proposalCounts": dict(sorted(proposal_counts.items())),
        "failureCount": len(failures),
        "failures": failures,
        "stopped": stopped,
        "checkpointedPerRequest": True,
        "secretPersisted": False,
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--gemini-labels", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--model", default=base.DEFAULT_MODEL)
    parser.add_argument("--batch-size", type=int, default=48)
    parser.add_argument("--max-unique", type=int, default=768)
    parser.add_argument("--max-output-tokens", type=int, default=1900)
    parser.add_argument("--budget-vnd", type=float, default=240.0)
    parser.add_argument("--safety-reserve-vnd", type=float, default=10.0)
    parser.add_argument("--max-requests", type=int, default=12)
    args = parser.parse_args()

    api_key = os.environ.get("HAKU_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("missing HAKU_API_KEY")
    if args.batch_size < 1 or args.max_requests < 1:
        raise SystemExit("invalid batch/request limit")

    effective_cap = max(0.0, args.budget_vnd - args.safety_reserve_vnd)
    if effective_cap < base.MIN_REQUEST_VND:
        raise SystemExit("effective Haku recovery budget is below one minimum request")

    output = Path(args.output)
    report_path = Path(args.report)
    priority_ids = base.load_priority_ids(Path(args.gemini_labels))
    contexts = base.load_unique_contexts(Path(args.input), args.max_unique, priority_ids)

    labels: list[dict] = []
    failures: list[dict] = []
    estimated_spend = 0.0
    prompt_tokens = 0
    completion_tokens = 0
    requests = 0
    stopped = "completed"

    write_checkpoint(
        output, report_path, labels, len(contexts), priority_ids, requests, estimated_spend,
        prompt_tokens, completion_tokens, failures, "initialized", args,
    )

    for start in range(0, len(contexts), args.batch_size):
        if requests >= args.max_requests:
            stopped = "max_requests"
            break
        batch = contexts[start:start + args.batch_size]
        prompt = base.compact_prompt(batch)
        reserve = base.conservative_reserve_vnd(prompt, args.max_output_tokens)
        if estimated_spend + reserve > effective_cap:
            stopped = "budget_guard"
            break

        requests += 1
        try:
            parsed, usage = base.request_batch(api_key, args.model, batch, args.max_output_tokens)
            batch_labels = base.validate_labels(batch, parsed)
            pt = int(usage.get("prompt_tokens") or 0)
            ct = int(usage.get("completion_tokens") or 0)
            prompt_tokens += pt
            completion_tokens += ct
            estimated_spend += base.cost_vnd(pt, ct) if pt or ct else reserve
            labels.extend(batch_labels)
            if len(batch_labels) != len(batch):
                failures.append({
                    "request": requests,
                    "status": "partial_labels",
                    "expected": len(batch),
                    "accepted": len(batch_labels),
                })
        except urllib.error.HTTPError as error:
            # Keep the accounting conservative even though provider documentation says failed requests
            # are not charged.
            estimated_spend += base.MIN_REQUEST_VND
            failures.append({"request": requests, "status": f"http_{int(error.code)}"})
            stopped = "provider_limit" if error.code in (402, 429) else "provider_error"
        except Exception as error:
            estimated_spend += base.MIN_REQUEST_VND
            failures.append({"request": requests, "status": error.__class__.__name__})
            stopped = "invalid_or_transport_error"

        write_checkpoint(
            output, report_path, labels, len(contexts), priority_ids, requests, estimated_spend,
            prompt_tokens, completion_tokens, failures, stopped if stopped != "completed" else "running", args,
        )
        if stopped != "completed":
            break

    write_checkpoint(
        output, report_path, labels, len(contexts), priority_ids, requests, estimated_spend,
        prompt_tokens, completion_tokens, failures, stopped, args,
    )
    print(report_path.read_text(encoding="utf-8").strip())
    return 0 if labels else 2


if __name__ == "__main__":
    sys.exit(main())
