#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("train_snapshot_fixture_filter.py")
SPEC = importlib.util.spec_from_file_location("train_snapshot_fixture_filter", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
trainer = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = trainer
SPEC.loader.exec_module(trainer)


class TrainSnapshotFixtureFilterTest(unittest.TestCase):
    def _sample(self, sha: str, label: int) -> object:
        return trainer.Sample(
            image=pathlib.Path(f"{sha}.jpg"),
            sha256=sha,
            candidate={},
            label=label,
            confidence=0.95,
        )

    def test_split_holds_out_two_negative_source_images_when_possible(self) -> None:
        samples = [
            self._sample("positive-a", 1),
            self._sample("positive-a", 1),
            self._sample("mixed-a", 1),
            self._sample("mixed-a", 0),
            self._sample("mixed-b", 1),
            self._sample("mixed-b", 0),
            self._sample("mixed-c", 1),
            self._sample("mixed-c", 0),
            self._sample("positive-b", 1),
            self._sample("positive-c", 1),
        ]

        train, test = trainer.split_by_image(samples, test_fraction=0.5)
        test_negative_sources = {sample.sha256 for sample in test if sample.label == 0}

        self.assertEqual(2, len(test_negative_sources))
        self.assertGreater(sum(sample.label == 0 for sample in train), 0)
        self.assertGreater(sum(sample.label == 1 for sample in train), 0)
        self.assertGreater(sum(sample.label == 0 for sample in test), 0)
        self.assertGreater(sum(sample.label == 1 for sample in test), 0)
        self.assertTrue({sample.sha256 for sample in train}.isdisjoint({sample.sha256 for sample in test}))

    def test_candidate_features_are_normalized_and_resolution_independent(self) -> None:
        candidate = {
            "x": 0.5,
            "y": 0.25,
            "w": 0.2,
            "h": 0.1,
            "aspect": 2.0,
            "avg_contrast": 0.3,
            "avg_luma": 0.8,
            "detector_confidence": 0.9,
            "fill": 0.6,
        }

        features = trainer.candidate_features(candidate)

        self.assertEqual(len(trainer.FEATURE_NAMES), len(features))
        self.assertAlmostEqual(0.02, features[-1])
        self.assertTrue(all(value == value for value in features))


if __name__ == "__main__":
    unittest.main()
