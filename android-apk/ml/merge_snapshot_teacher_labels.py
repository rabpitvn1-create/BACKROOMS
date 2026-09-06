#!/usr/bin/env python3
"""Merge trusted Snapshot teacher labels without weakening semantic-label safety.

Only accepted records are carried forward. Duplicate detector candidates must agree
on their fixture label, otherwise the merge fails. Live Claude output can therefore
enrich a checked-in trusted seed corpus without making provider availability a hard
dependency of the tiny fixture-filter training job.
"""
from __future__ import annotations

import argparse
import json
import math
import pathlib
import re
from typing import Iterable

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SNAPSHOT_PREFIX = "android-apk/app/src/main/assets/level_snapshots/"


class MergeError(RuntimeError):
    pass


def _finite_unit(value: object, name: str, *, positive: bool = False) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise MergeError(f"{name} must be numeric")
    number = float(value)
    if not math.isfinite(number):
        raise MergeError(f"{name} must be finite")
    if positive:
        if not 0.0 < number <= 1.0:
            raise MergeError(f"{name} must be in (0, 1]")
    elif not 0.0 <= number <= 1.0:
        raise MergeError(f"{name} must be in [0, 1]")
    return number


def _validate_image_path(value: object) -> str:
    if not isinstance(value, str) or not value.startswith(SNAPSHOT_PREFIX):
        raise MergeError("image must point inside the Snapshot asset directory")
    path = pathlib.PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts:
        raise MergeError("image path must be a safe repository-relative Snapshot path")
    return value


def _validate_accepted(record: object, *, source: pathlib.Path, line_number: int) -> dict:
    if not isinstance(record, dict):
        raise MergeError(f"{source}:{line_number}: accepted record must be an object")
    if record.get("status") != "accepted":
        raise MergeError(f"{source}:{line_number}: record is not accepted")

    _validate_image_path(record.get("image"))
    sha256 = record.get("sha256")
    if not isinstance(sha256, str) or SHA256_RE.fullmatch(sha256) is None:
        raise MergeError(f"{source}:{line_number}: sha256 must be 64 lowercase hex characters")

    candidate = record.get("candidate")
    if not isinstance(candidate, dict):
        raise MergeError(f"{source}:{line_number}: candidate must be an object")
    _finite_unit(candidate.get("x"), "candidate.x")
    _finite_unit(candidate.get("y"), "candidate.y")
    _finite_unit(candidate.get("w"), "candidate.w", positive=True)
    _finite_unit(candidate.get("h"), "candidate.h", positive=True)

    teacher = record.get("teacher")
    if not isinstance(teacher, dict):
        raise MergeError(f"{source}:{line_number}: teacher must be an object")
    fixture = teacher.get("fixture")
    if not isinstance(fixture, bool):
        raise MergeError(f"{source}:{line_number}: teacher.fixture must be boolean")
    _finite_unit(teacher.get("confidence"), "teacher.confidence")

    teacher_model = record.get("teacher_model")
    if not isinstance(teacher_model, str) or not teacher_model.strip():
        raise MergeError(f"{source}:{line_number}: teacher_model must be non-empty")

    passes = record.get("passes")
    if isinstance(passes, bool) or not isinstance(passes, int) or passes < 1:
        raise MergeError(f"{source}:{line_number}: passes must be a positive integer")

    return record


def _identity(record: dict) -> tuple[str, float, float, float, float]:
    candidate = record["candidate"]
    return (
        record["sha256"],
        round(float(candidate["x"]), 9),
        round(float(candidate["y"]), 9),
        round(float(candidate["w"]), 9),
        round(float(candidate["h"]), 9),
    )


def _sort_key(record: dict) -> tuple:
    candidate = record["candidate"]
    return (
        record["image"],
        record["sha256"],
        int(record.get("candidate_index", 0)),
        round(float(candidate["x"]), 9),
        round(float(candidate["y"]), 9),
        round(float(candidate["w"]), 9),
        round(float(candidate["h"]), 9),
    )


def merge_records(paths: Iterable[pathlib.Path], optional_paths: Iterable[pathlib.Path] = ()) -> list[dict]:
    merged: dict[tuple[str, float, float, float, float], dict] = {}

    def consume(path: pathlib.Path, *, optional: bool) -> None:
        if not path.is_file():
            if optional:
                return
            raise MergeError(f"labels file not found: {path}")

        for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if not raw.strip():
                continue
            try:
                record = json.loads(raw)
            except json.JSONDecodeError as error:
                raise MergeError(f"{path}:{line_number}: invalid JSON: {error}") from error

            if not isinstance(record, dict) or record.get("status") != "accepted":
                continue

            accepted = _validate_accepted(record, source=path, line_number=line_number)
            key = _identity(accepted)
            previous = merged.get(key)
            if previous is None:
                merged[key] = accepted
                continue
            if previous["teacher"]["fixture"] != accepted["teacher"]["fixture"]:
                raise MergeError(
                    "conflicting fixture labels for the same detector candidate "
                    f"(sha256={accepted['sha256']}, image={accepted['image']})"
                )

    for path in paths:
        consume(path, optional=False)
    for path in optional_paths:
        consume(path, optional=True)

    return sorted(merged.values(), key=_sort_key)


def write_records(records: list[dict], output: pathlib.Path) -> dict[str, int]:
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = "".join(
        json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n"
        for record in records
    )
    output.write_text(payload, encoding="utf-8")
    stats = {
        "accepted": len(records),
        "fixture": sum(record["teacher"]["fixture"] is True for record in records),
        "not_fixture": sum(record["teacher"]["fixture"] is False for record in records),
        "unique_images": len({record["sha256"] for record in records}),
    }
    return stats


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", action="append", default=[], type=pathlib.Path)
    parser.add_argument("--optional-input", action="append", default=[], type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if not args.input:
        raise MergeError("at least one --input is required")
    records = merge_records(args.input, args.optional_input)
    stats = write_records(records, args.output)
    print(
        "Merged Snapshot labels: "
        f"accepted={stats['accepted']} fixture={stats['fixture']} "
        f"not_fixture={stats['not_fixture']} unique_images={stats['unique_images']}"
    )


if __name__ == "__main__":
    main()
