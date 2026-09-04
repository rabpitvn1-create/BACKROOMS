#!/usr/bin/env python3
"""Budget-capped Haku teacher for the exact WorldDirector V2 deployable feature contract.

Haku sees only featureTextV2, a synthetic sample id, and Core's legal proposal set. Gemini-labeled
sample ids are prioritized first so the paid teacher creates a useful cross-teacher agreement audit
before spending budget on additional coverage. No provider rotation or fallback is implemented.
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
REASONS = (
    "RECOVERY", "VARIETY", "ESCALATION", "RESOURCE_PACING", "EXPLORATION_PRESSURE",
    "SAFE_ABSTAIN", "ANTI_REPETITION", "LOCAL_CONTEXT",
)
INPUT_VND_PER_M = 1050.0
OUTPUT_VND_PER_M = 5250.0
MIN_REQUEST_VND = 5.0

SYSTEM = """You are a teacher for a tiny on-device WorldDirector V2 pacing classifier.
You receive ONLY the exact featureTextV2 that the eventual LiteRT model can observe and the legal
proposal set already computed by deterministic Core. Choose one legal proposal for every sample.

V2 pacing semantics:
- h1 is the most recent prior action/pressure, then h2, h3, h4.
- density/streak/since/entropy tokens summarize only recent observable pacing.
- Prefer NONE after dense or repetitive pressure.
- Prefer ITEM_OPPORTUNITY when resource pacing is useful and legal.
- Prefer ENTITY_PRESSURE when exploration has been calm long enough and it is legal.
- Prefer MAZE_PRESSURE for repeated/deep exploration when recent maze pressure is not already dense.
- Preserve variety and avoid mechanically repeating the previous pressure.
Never infer Level identity, exit/escape solution, puzzle facts, secret evidence, Entity/item identity,
inventory, player text, character canon, or anything absent from featureTextV2.

Return compact JSON only:
{"labels":[{"sampleId":"...","proposal":"...","confidence":0.0,"reasonCode":"..."}]}
reasonCode must be RECOVERY, VARIETY, ESCALATION, RESOURCE_PACING, EXPLORATION_PRESSURE,
SAFE_ABSTAIN, ANTI_REPETITION, or LOCAL_CONTEXT. No extra keys or explanations.
"""


def stable_id(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:24]


def load_priority_ids(path: Path | None) -> set[str]:
    if path is None or not path.exists():
        return set()
    result = set()
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            sample_id = str(row.get("sampleId") or "").strip()
            if sample_id:
                result.add(sample_id)
    return result


def load_unique_contexts(path: Path, max_unique: int, priority_ids: set[str]) -> list[dict]:
    by_id: dict[str, dict] = {}
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            text = str(row.get("featureTextV2") or "").strip()
            sample_id = str(row.get("sampleIdV2") or "").strip()
            legal = tuple(str(value).upper() for value in ((row.get("state") or {}).get("legalProposals") or []))
            if not text or not sample_id or not legal:
                continue
            if sample_id != stable_id(text):
                raise SystemExit(f"V2 sample id mismatch: {sample_id}")
            if any(value not in PROPOSALS for value in legal):
                raise SystemExit(f"invalid legal proposal set for {sample_id}")
            compact = {"sampleId": sample_id, "featureTextV2": text, "legalProposals": list(legal)}
            previous = by_id.get(sample_id)
            if previous is not None and previous != compact:
                raise SystemExit(f"conflicting V2 context for {sample_id}")
            by_id[sample_id] = compact

    rows = list(by_id.values())
    rows.sort(key=lambda row: (
        0 if row["sampleId"] in priority_ids else 1,
        -len(row["legalProposals"]),
        row["sampleId"],
    ))
    return rows[:max_unique] if max_unique > 0 else rows


def compact_prompt(batch: list[dict]) -> str:
    return SYSTEM + "\nBATCH=" + json.dumps(batch, ensure_ascii=False, separators=(",", ":"))


def strip_json(text: str) -> str:
    value = text.strip()
    if value.startswith("```"):
        first = value.find("\n")
        if first >= 0:
            value = value[first + 1:]
        fence = value.rfind("```")
        if fence >= 0:
            value = value[:fence]
    start = value.find("{")
    end = value.rfind("}")
    return value[start:end + 1] if start >= 0 and end > start else value.strip()


def cost_vnd(prompt_tokens: int, completion_tokens: int) -> float:
    metered = prompt_tokens * INPUT_VND_PER_M / 1_000_000.0
    metered += completion_tokens * OUTPUT_VND_PER_M / 1_000_000.0
    return max(MIN_REQUEST_VND, metered)


def conservative_reserve_vnd(prompt: str, max_output_tokens: int) -> float:
    # Two chars per token is deliberately pessimistic for the ASCII-heavy structured feature text.
    input_upper = max(1, len(prompt) // 2)
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
    return json.loads(strip_json(content)), raw.get("usage") or {}


def validate_labels(batch: list[dict], parsed: dict) -> list[dict]:
    requested = {row["sampleId"]: row for row in batch}
    seen = set()
    result = []
    for item in parsed.get("labels") or []:
        sample_id = str(item.get("sampleId") or "")
        if sample_id not in requested or sample_id in seen:
            continue
        proposal = str(item.get("proposal") or "").upper()
        reason = str(item.get("reasonCode") or "LOCAL_CONTEXT").upper()
        try:
            confidence = float(item.get("confidence"))
        except (TypeError, ValueError):
            continue
        if proposal not in requested[sample_id]["legalProposals"] or not 0.0 <= confidence <= 1.0:
            continue
        if reason not in REASONS:
            reason = "LOCAL_CONTEXT"
        seen.add(sample_id)
        result.append({
            "sampleId": sample_id,
            "teacher": "HAKU",
            "model": DEFAULT_MODEL,
            "featureTextV2": requested[sample_id]["featureTextV2"],
            "legalProposals": requested[sample_id]["legalProposals"],
            "label": {"proposal": proposal, "confidence": confidence, "reasonCode": reason},
        })
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--gemini-labels")
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--max-unique", type=int, default=8192)
    parser.add_argument("--max-output-tokens", type=int, default=1900)
    parser.add_argument("--budget-vnd", type=float, default=3945.0)
    parser.add_argument("--safety-reserve-vnd", type=float, default=250.0)
    parser.add_argument("--max-requests", type=int, default=240)
    args = parser.parse_args()

    api_key = os.environ.get("HAKU_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("missing HAKU_API_KEY")
    if args.batch_size < 1 or args.max_requests < 1:
        raise SystemExit("invalid batch/request limit")
    effective_cap = max(0.0, args.budget_vnd - args.safety_reserve_vnd)
    if effective_cap < MIN_REQUEST_VND:
        raise SystemExit("effective Haku budget is below one minimum request")

    gemini_path = Path(args.gemini_labels) if args.gemini_labels else None
    priority_ids = load_priority_ids(gemini_path)
    contexts = load_unique_contexts(Path(args.input), args.max_unique, priority_ids)
    labels = []
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
        batch = contexts[start:start + args.batch_size]
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
            estimated_spend += cost_vnd(pt, ct) if pt or ct else reserve
            labels.extend(batch_labels)
            if len(batch_labels) != len(batch):
                failures.append({"request": requests, "status": "partial_labels", "expected": len(batch), "accepted": len(batch_labels)})
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

    proposal_counts = Counter(row["label"]["proposal"] for row in labels)
    overlap = sum(row["sampleId"] in priority_ids for row in labels)
    report = {
        "schemaVersion": 1,
        "contract": "WORLD_DIRECTOR_PRESSURE_V2",
        "teacher": "HAKU",
        "model": args.model,
        "uniqueContextsConsidered": len(contexts),
        "geminiPriorityIds": len(priority_ids),
        "geminiOverlapLabeled": overlap,
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
    print(json.dumps(report, ensure_ascii=False))
    return 0 if labels else 2


if __name__ == "__main__":
    sys.exit(main())
