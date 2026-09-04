#!/usr/bin/env python3
"""Budget-capped Haku teacher for the deployable WorldDirector V1 feature contract.

Only featureTextV1 plus Core's legal proposal set is sent to Haku. Rich simulator history,
Level/zone/evidence identifiers, puzzle data, inventory, player text and provider secrets never
enter the request. The script never rotates providers and stops before the configured VND cap.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

ENDPOINT = "https://api.vilao.ai/v1/chat/completions"
DEFAULT_MODEL = "claude-haiku-4-5-20251001"
PROPOSALS = ("NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY")
REASON_CODES = (
    "SAFE_ABSTAIN",
    "EXPLORATION_PRESSURE",
    "RESOURCE_PACING",
    "VARIETY",
    "LOCAL_CONTEXT",
)
INPUT_VND_PER_M = 1050.0
OUTPUT_VND_PER_M = 5250.0
MIN_REQUEST_VND = 5.0

SYSTEM = """You are a teacher for a tiny on-device WorldDirector classifier.
You see ONLY the exact V1 feature string that the on-device LiteRT policy can see, plus the legal
proposal set already computed by deterministic Core. Choose one legal proposal. Never infer hidden
Level identity, exit/escape solution, puzzle facts, undiscovered evidence, Entity/item identity,
inventory, character canon, player text, or any information not encoded in featureTextV1.

Policy intent:
- NONE is a safe abstention and is required when it is the only legal proposal.
- SEARCH may prefer ITEM_OPPORTUNITY when legal, especially with little evidence, but can abstain.
- EXPLORE can use ENTITY_PRESSURE for early/ordinary traversal, MAZE_PRESSURE for repeated/deep
  traversal when legal, ITEM_OPPORTUNITY for resource pacing, or NONE when local context suggests
  restraint.
- revision/evidence/visit/recent/zone tokens are soft local pacing clues, never hidden-solution clues.
Return compact JSON only:
{"labels":[{"id":"...","proposal":"...","confidence":0.0,"reasonCode":"..."}]}
reasonCode must be one of SAFE_ABSTAIN, EXPLORATION_PRESSURE, RESOURCE_PACING, VARIETY, LOCAL_CONTEXT.
Do not include explanations or extra keys.
"""


def stable_id(feature_text: str) -> str:
    return hashlib.sha256(feature_text.encode("utf-8")).hexdigest()[:20]


def load_unique_contexts(path: Path, max_unique: int) -> list[dict]:
    by_text: dict[str, tuple[str, ...]] = {}
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            feature_text = str(row.get("featureTextV1") or "").strip()
            legal = tuple(str(x) for x in ((row.get("state") or {}).get("legalProposals") or []))
            if not feature_text or not legal:
                continue
            if any(item not in PROPOSALS for item in legal):
                raise SystemExit(f"invalid legal proposal set for {stable_id(feature_text)}")
            previous = by_text.get(feature_text)
            if previous is not None and previous != legal:
                raise SystemExit(f"inconsistent legal proposal set for {stable_id(feature_text)}")
            by_text[feature_text] = legal

    contexts = [
        {"id": stable_id(text), "featureTextV1": text, "legalProposals": list(legal)}
        for text, legal in by_text.items()
    ]
    contexts.sort(key=lambda row: row["id"])
    if max_unique > 0:
        contexts = contexts[:max_unique]
    return contexts


def compact_prompt(batch: list[dict]) -> str:
    payload = [
        {"id": row["id"], "featureTextV1": row["featureTextV1"], "legalProposals": row["legalProposals"]}
        for row in batch
    ]
    return SYSTEM + "\nBATCH=" + json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def strip_json_fence(text: str) -> str:
    value = text.strip()
    if value.startswith("```"):
        first = value.find("\n")
        if first >= 0:
            value = value[first + 1 :]
        fence = value.rfind("```")
        if fence >= 0:
            value = value[:fence]
    value = value.strip()
    start_obj = value.find("{")
    end_obj = value.rfind("}")
    if start_obj >= 0 and end_obj > start_obj:
        return value[start_obj : end_obj + 1]
    return value


def cost_vnd(prompt_tokens: int, completion_tokens: int) -> float:
    metered = prompt_tokens * INPUT_VND_PER_M / 1_000_000.0
    metered += completion_tokens * OUTPUT_VND_PER_M / 1_000_000.0
    return max(MIN_REQUEST_VND, metered)


def conservative_reserve_vnd(prompt: str, max_output_tokens: int) -> float:
    input_upper = max(1, len(prompt) * 2)
    return cost_vnd(input_upper, max_output_tokens)


def request_batch(api_key: str, model: str, batch: list[dict], max_output_tokens: int) -> tuple[dict, dict]:
    prompt = compact_prompt(batch)
    body = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0,
        "max_tokens": max_output_tokens,
        "stream": False,
    }
    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        raw = json.loads(response.read().decode("utf-8"))

    choices = raw.get("choices") or []
    message = (choices[0].get("message") if choices else None) or {}
    content = str(message.get("content") or "").strip()
    if not content:
        raise ValueError("empty_haku_response")
    parsed = json.loads(strip_json_fence(content))
    usage = raw.get("usage") or {}
    return parsed, usage


def validate_labels(batch: list[dict], parsed: dict) -> list[dict]:
    requested = {row["id"]: row for row in batch}
    seen: set[str] = set()
    result = []
    for item in parsed.get("labels") or []:
        sample_id = str(item.get("id") or "")
        if sample_id not in requested or sample_id in seen:
            continue
        proposal = str(item.get("proposal") or "").upper()
        try:
            confidence = float(item.get("confidence"))
        except (TypeError, ValueError):
            continue
        reason = str(item.get("reasonCode") or "LOCAL_CONTEXT").upper()
        if reason not in REASON_CODES:
            reason = "LOCAL_CONTEXT"
        if proposal not in requested[sample_id]["legalProposals"]:
            continue
        if not 0.0 <= confidence <= 1.0:
            continue
        seen.add(sample_id)
        result.append({
            "sampleId": sample_id,
            "featureTextV1": requested[sample_id]["featureTextV1"],
            "legalProposals": requested[sample_id]["legalProposals"],
            "proposal": proposal,
            "confidence": confidence,
            "reasonCode": reason,
        })
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--batch-size", type=int, default=24)
    parser.add_argument("--max-unique", type=int, default=4096)
    parser.add_argument("--max-output-tokens", type=int, default=1400)
    parser.add_argument("--budget-vnd", type=float, default=4103.0)
    parser.add_argument("--safety-reserve-vnd", type=float, default=250.0)
    parser.add_argument("--max-requests", type=int, default=400)
    args = parser.parse_args()

    api_key = os.environ.get("HAKU_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("missing HAKU_API_KEY")
    if args.batch_size < 1 or args.max_requests < 1:
        raise SystemExit("invalid batch/request limit")
    effective_cap = max(0.0, args.budget_vnd - args.safety_reserve_vnd)
    if effective_cap < MIN_REQUEST_VND:
        raise SystemExit("effective Haku budget is below one minimum request")

    contexts = load_unique_contexts(Path(args.input), args.max_unique)
    labels: list[dict] = []
    failures = []
    estimated_spend = 0.0
    prompt_tokens = 0
    completion_tokens = 0
    requests = 0
    stopped = "completed"

    for start in range(0, len(contexts), args.batch_size):
        if requests >= args.max_requests:
            stopped = "max_requests"
            break
        batch = contexts[start : start + args.batch_size]
        prompt = compact_prompt(batch)
        reserve = conservative_reserve_vnd(prompt, args.max_output_tokens)
        if estimated_spend + reserve > effective_cap:
            stopped = "budget_guard"
            break

        requests += 1
        try:
            parsed, usage = request_batch(api_key, args.model, batch, args.max_output_tokens)
            batch_labels = validate_labels(batch, parsed)
            pt = int(usage.get("prompt_tokens") or 0)
            ct = int(usage.get("completion_tokens") or 0)
            prompt_tokens += pt
            completion_tokens += ct
            billed = cost_vnd(pt, ct) if pt or ct else reserve
            estimated_spend += billed
            labels.extend(batch_labels)
            if len(batch_labels) != len(batch):
                failures.append({
                    "request": requests,
                    "status": "partial_labels",
                    "expected": len(batch),
                    "accepted": len(batch_labels),
                })
        except urllib.error.HTTPError as error:
            estimated_spend += MIN_REQUEST_VND
            failures.append({"request": requests, "status": f"http_{int(error.code)}"})
            stopped = "provider_limit" if error.code in (402, 429) else "provider_error"
            break
        except Exception as error:
            estimated_spend += MIN_REQUEST_VND
            failures.append({"request": requests, "status": error.__class__.__name__})
            stopped = "invalid_or_transport_error"
            break

    by_id = {row["sampleId"]: row for row in labels}
    labels = [by_id[key] for key in sorted(by_id)]
    with Path(args.output).open("w", encoding="utf-8") as handle:
        for row in labels:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    proposal_counts = Counter(row["proposal"] for row in labels)
    report = {
        "schemaVersion": 1,
        "teacher": "HAKU",
        "model": args.model,
        "inputContract": "WORLD_DIRECTOR_PRESSURE_V1_FEATURE_TEXT_ONLY",
        "uniqueContexts": len(contexts),
        "labels": len(labels),
        "requests": requests,
        "estimatedSpendVnd": round(estimated_spend, 4),
        "userBudgetVnd": args.budget_vnd,
        "safetyReserveVnd": args.safety_reserve_vnd,
        "effectiveCapVnd": effective_cap,
        "promptTokens": prompt_tokens,
        "completionTokens": completion_tokens,
        "proposalCounts": dict(sorted(proposal_counts.items())),
        "failureCount": len(failures),
        "failures": failures,
        "stopped": stopped,
        "endpoint": "vilao-openai-compatible",
        "secretPersisted": False,
    }
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in (
        "uniqueContexts", "labels", "requests", "estimatedSpendVnd", "effectiveCapVnd",
        "proposalCounts", "failureCount", "stopped"
    )}, ensure_ascii=False))
    return 0 if labels else 2


if __name__ == "__main__":
    sys.exit(main())
