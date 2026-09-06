#!/usr/bin/env python3
"""Run Snapshot teacher commands with explicit Claude configuration."""
from __future__ import annotations

import os
import sys

import claude_snapshot_compat
import haku_snapshot_candidate_teacher
import haku_snapshot_teacher


def _required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"{name} is required")
    return value


def configure_claude_environment() -> None:
    api_key = _required_env("CLAUDE_API_KEY")
    model = _required_env("CLAUDE_MODEL")
    base_url = _required_env("CLAUDE_BASE_URL")
    try:
        api_url = claude_snapshot_compat.resolve_api_url(base_url)
    except ValueError as error:
        raise SystemExit(str(error)) from error

    # The existing teacher modules keep their stable internal argument names.
    # Map the explicit Claude contract at the runner boundary rather than
    # duplicating transport/training logic or leaking provider credentials.
    os.environ["HAKU_API_KEY"] = api_key
    os.environ["HAKU_SNAPSHOT_MODEL"] = model
    os.environ["HAKU_API_URL"] = api_url


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in {"probe", "candidates"}:
        raise SystemExit("usage: claude_snapshot_runner.py probe|candidates [args...]")

    command = sys.argv[1]
    remainder = sys.argv[2:]
    configure_claude_environment()
    claude_snapshot_compat.install()

    if command == "probe":
        sys.argv = [sys.argv[0], "probe", *remainder]
        haku_snapshot_teacher.main()
    else:
        sys.argv = [sys.argv[0], *remainder]
        haku_snapshot_candidate_teacher.main()


if __name__ == "__main__":
    main()
