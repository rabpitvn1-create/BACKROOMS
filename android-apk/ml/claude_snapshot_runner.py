#!/usr/bin/env python3
"""Run Snapshot teacher commands with Claude gateway response compatibility."""
from __future__ import annotations

import sys

import claude_snapshot_compat
import haku_snapshot_candidate_teacher
import haku_snapshot_teacher


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in {"probe", "candidates"}:
        raise SystemExit("usage: claude_snapshot_runner.py probe|candidates [args...]")

    command = sys.argv[1]
    remainder = sys.argv[2:]
    claude_snapshot_compat.install()

    if command == "probe":
        sys.argv = [sys.argv[0], "probe", *remainder]
        haku_snapshot_teacher.main()
    else:
        sys.argv = [sys.argv[0], *remainder]
        haku_snapshot_candidate_teacher.main()


if __name__ == "__main__":
    main()
