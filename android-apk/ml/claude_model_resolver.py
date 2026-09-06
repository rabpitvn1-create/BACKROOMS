#!/usr/bin/env python3
"""Resolve an actually authorized Claude route for the project gateway.

The gateway uses provider-prefixed model IDs (for example ``ccf/`` or ``occ/``).
Its model catalogue is not an authorization guarantee, so this resolver performs a
small real completion against candidate routes and selects the first one that the
configured key can actually invoke.
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import urllib.error
import urllib.request
from typing import Any, Iterable

DEFAULT_API_URL = "https://api.vilao.ai/v1/chat/completions"
DEFAULT_CANDIDATES = (
    "ccf/claude-opus-4-8",
    "occ/claude-opus-4-8",
    "krr/claude-opus-4-8",
    "claude-opus-4-8",
)


class ResolveError(RuntimeError):
    pass


def normalize_candidates(values: Iterable[str]) -> list[str]:
    result: list[str] = []
    for raw in values:
        value = raw.strip()
        if value and value not in result:
            result.append(value)
    if not result:
        raise ResolveError("no Claude model candidates configured")
    return result


def _valid_openai_response(payload: Any) -> bool:
    if not isinstance(payload, dict):
        return False
    choices = payload.get("choices")
    return isinstance(choices, list) and bool(choices) and isinstance(choices[0], dict)


def probe_model(
    model: str,
    *,
    api_key: str,
    api_url: str,
    timeout: int = 30,
    opener=urllib.request.urlopen,
) -> tuple[bool, int]:
    body = {
        "model": model,
        "messages": [{"role": "user", "content": "Reply with the single word OK."}],
        "max_tokens": 32,
        "stream": False,
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with opener(request, timeout=timeout) as response:
            status = int(getattr(response, "status", 200))
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        # Do not print provider bodies here. Model id + status is enough to
        # diagnose subscription routing without risking credential-adjacent data.
        return False, int(error.code)
    except urllib.error.URLError as error:
        raise ResolveError(f"Claude route probe connection failed: {error.reason}") from error

    if status != 200:
        return False, status
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return False, status
    return _valid_openai_response(payload), status


def resolve_model(
    candidates: Iterable[str],
    *,
    api_key: str,
    api_url: str,
    timeout: int = 30,
    probe=probe_model,
) -> tuple[str, list[tuple[str, int]]]:
    attempts: list[tuple[str, int]] = []
    for model in normalize_candidates(candidates):
        ok, status = probe(
            model,
            api_key=api_key,
            api_url=api_url,
            timeout=timeout,
        )
        attempts.append((model, status))
        if ok:
            return model, attempts
    summary = ", ".join(f"{model}={status}" for model, status in attempts)
    raise ResolveError(f"no authorized Claude route found ({summary})")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-url", default=os.environ.get("HAKU_API_URL", DEFAULT_API_URL))
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--candidate", action="append", default=[])
    parser.add_argument("--github-env", default=os.environ.get("GITHUB_ENV", ""))
    return parser


def main() -> None:
    args = build_parser().parse_args()
    api_key = os.environ.get("CLAUDE_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("CLAUDE_API_KEY is required")
    candidates = args.candidate or list(DEFAULT_CANDIDATES)
    try:
        selected, attempts = resolve_model(
            candidates,
            api_key=api_key,
            api_url=args.api_url,
            timeout=args.timeout,
        )
    except ResolveError as error:
        raise SystemExit(str(error)) from error

    print("Claude route probe: " + ", ".join(f"{model}={status}" for model, status in attempts))
    print(f"Authorized Claude route: {selected}")
    if not args.github_env:
        raise SystemExit("GITHUB_ENV is required to export HAKU_SNAPSHOT_MODEL")
    env_path = pathlib.Path(args.github_env)
    with env_path.open("a", encoding="utf-8") as handle:
        handle.write(f"HAKU_SNAPSHOT_MODEL={selected}\n")


if __name__ == "__main__":
    main()
