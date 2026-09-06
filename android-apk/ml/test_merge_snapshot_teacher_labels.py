#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("merge_snapshot_teacher_labels.py")
SPEC = importlib.util.spec_from_file_location("merge_snapshot_teacher_labels", MODULE_PATH)
assert SPEC and SPEC.loader
merge = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(merge)


def accepted(*, sha: str = "a" * 64, fixture: bool = True, x: float = 0.25) -> dict:
    return {
        "status": "accepted",
        "image": "android-apk/app/src/main/assets/level_snapshots/rotation/level_0_1.jpg",
        "sha256": sha,
        "detector": "snapshot-light-v3-candidate-geometry",
        "candidate_index": 0,
        "candidate": {"x": x, "y": 0.3, "w": 0.1, "h": 0.2},
        "teacher_model": "claude-test",
        "passes": 2,
        "teacher": {"fixture": fixture, "kind": "linear", "confidence": 0.9},
    }


class MergeSnapshotTeacherLabelsTest(unittest.TestCase):
    def write_jsonl(self, directory: pathlib.Path, name: str, rows: list[dict]) -> pathlib.Path:
        path = directory / name
        path.write_text("\n".join(json.dumps(row) for row in rows) + "\n", encoding="utf-8")
        return path

    def test_dedupes_agreeing_accepted_records_and_ignores_rejected_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            row = accepted()
            seed = self.write_jsonl(root, "seed.jsonl", [row])
            live = self.write_jsonl(
                root,
                "live.jsonl",
                [
                    row,
                    {
                        "status": "rejected",
                        "image": row["image"],
                        "sha256": "b" * 64,
                        "reason": "provider unavailable",
                    },
                    accepted(sha="c" * 64, fixture=False, x=0.8),
                ],
            )
            records = merge.merge_records([seed], [live])
            self.assertEqual(2, len(records))
            self.assertEqual({True, False}, {record["teacher"]["fixture"] for record in records})

    def test_missing_optional_file_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            seed = self.write_jsonl(root, "seed.jsonl", [accepted()])
            records = merge.merge_records([seed], [root / "missing-live.jsonl"])
            self.assertEqual(1, len(records))

    def test_conflicting_labels_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            first = accepted(fixture=True)
            second = accepted(fixture=False)
            seed = self.write_jsonl(root, "seed.jsonl", [first])
            live = self.write_jsonl(root, "live.jsonl", [second])
            with self.assertRaisesRegex(merge.MergeError, "conflicting fixture labels"):
                merge.merge_records([seed], [live])

    def test_malformed_accepted_record_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            row = accepted()
            row["image"] = "../../outside.png"
            seed = self.write_jsonl(root, "seed.jsonl", [row])
            with self.assertRaisesRegex(merge.MergeError, "Snapshot asset directory"):
                merge.merge_records([seed])

    def test_write_records_reports_class_and_image_counts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            records = [
                accepted(sha="a" * 64, fixture=True),
                accepted(sha="b" * 64, fixture=False, x=0.75),
            ]
            output = root / "merged.jsonl"
            stats = merge.write_records(records, output)
            self.assertEqual(
                {"accepted": 2, "fixture": 1, "not_fixture": 1, "unique_images": 2},
                stats,
            )
            self.assertEqual(2, len(output.read_text(encoding="utf-8").splitlines()))


if __name__ == "__main__":
    unittest.main()
