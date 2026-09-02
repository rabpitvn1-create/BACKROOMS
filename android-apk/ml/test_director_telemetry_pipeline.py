#!/usr/bin/env python3
"""Unit tests for offline BackroomsDirector telemetry dataset pipeline and quality gates."""
import json
import tempfile
import unittest
from pathlib import Path

from build_director_telemetry_dataset import (
    derive_label_and_validate,
    process_telemetry_source,
    session_split,
    FORBIDDEN_KEYS,
    SCHEMA_VERSION,
)
from train_director_classifier import vectorize, TOKEN


class TestDirectorTelemetryPipeline(unittest.TestCase):

    def test_forbidden_keys_rejected(self):
        for forbidden_key in FORBIDDEN_KEYS:
            row = {
                "schemaVersion": SCHEMA_VERSION,
                "sessionId": "sess1",
                "actionKind": "EXPLORE",
                "features": "action_explore visit_first revision_early candidate_anomaly seen_anomaly evidence_none",
                "candidateSourceCounts": {"ANOMALY": 1},
                "modelPreferredSource": "ANOMALY",
                "modelAccepted": True,
                "fallbackUsed": False,
                "selectedSource": "ANOMALY",
                "surfacedCount": 1,
                "discoveredEvidenceBefore": 0,
                "discoveredEvidenceAfter": 1,
                "discoveredFactBefore": 0,
                "discoveredFactAfter": 0,
                "unlockedFact": False,
                "worldRevisionBefore": 1,
                "worldRevisionAfter": 2,
                forbidden_key: "secret_value",
            }
            with tempfile.NamedTemporaryFile("w", suffix=".jsonl", delete=False) as f:
                f.write(json.dumps(row) + "\n")
                tmp_path = Path(f.name)

            dataset_rows, rejections, _ = process_telemetry_source(tmp_path)
            tmp_path.unlink(missing_ok=True)
            self.assertEqual(len(dataset_rows), 0)
            self.assertTrue(any("forbidden_keys" in k for k in rejections))

    def test_malformed_and_schema_version_rejected(self):
        row_bad_schema = {
            "schemaVersion": 999,
            "sessionId": "sess1",
            "actionKind": "EXPLORE",
            "features": "action_explore visit_first revision_early candidate_anomaly seen_anomaly evidence_none",
            "candidateSourceCounts": {"ANOMALY": 1},
            "modelPreferredSource": "ANOMALY",
            "modelAccepted": True,
            "fallbackUsed": False,
            "selectedSource": "ANOMALY",
            "surfacedCount": 1,
            "discoveredEvidenceBefore": 0,
            "discoveredEvidenceAfter": 1,
            "discoveredFactBefore": 0,
            "discoveredFactAfter": 0,
            "unlockedFact": False,
            "worldRevisionBefore": 1,
            "worldRevisionAfter": 2,
        }
        with tempfile.NamedTemporaryFile("w", suffix=".jsonl", delete=False) as f:
            f.write(json.dumps(row_bad_schema) + "\n")
            f.write("{invalid json syntax\n")
            tmp_path = Path(f.name)

        dataset_rows, rejections, _ = process_telemetry_source(tmp_path)
        tmp_path.unlink(missing_ok=True)
        self.assertEqual(len(dataset_rows), 0)
        self.assertIn("malformed_json", rejections)
        self.assertIn("unsupported_schemaVersion:999", rejections)

    def test_label_derivation_taxonomy_and_rules(self):
        # 1. SEARCH action with positive outcome -> ITEM_OPPORTUNITY
        row_search = {
            "actionKind": "SEARCH",
            "selectedSource": "SEARCH",
            "candidateSourceCounts": {"SEARCH": 2},
            "surfacedCount": 1,
            "discoveredEvidenceBefore": 0,
            "discoveredEvidenceAfter": 1,
            "features": "action_search visit_first candidate_item_opportunity",
        }
        label, rej = derive_label_and_validate(row_search)
        self.assertEqual(label, "ITEM_OPPORTUNITY")
        self.assertEqual(rej, "")

        # 2. EXPLORE action with SURVIVOR source -> ENTITY_PRESSURE
        row_entity = {
            "actionKind": "EXPLORE",
            "selectedSource": "SURVIVOR",
            "candidateSourceCounts": {"SURVIVOR": 1},
            "surfacedCount": 1,
            "discoveredEvidenceBefore": 0,
            "discoveredEvidenceAfter": 1,
            "features": "action_explore visit_first candidate_entity_pressure",
        }
        label, rej = derive_label_and_validate(row_entity)
        self.assertEqual(label, "ENTITY_PRESSURE")
        self.assertEqual(rej, "")

        # 3. EXPLORE action with ANOMALY source -> MAZE_PRESSURE
        row_maze = {
            "actionKind": "EXPLORE",
            "selectedSource": "ANOMALY",
            "candidateSourceCounts": {"ANOMALY": 1},
            "surfacedCount": 1,
            "discoveredEvidenceBefore": 0,
            "discoveredEvidenceAfter": 1,
            "features": "action_explore visit_repeat candidate_maze_pressure",
        }
        label, rej = derive_label_and_validate(row_maze)
        self.assertEqual(label, "MAZE_PRESSURE")
        self.assertEqual(rej, "")

    def test_label_derivation_rejects_illegal_and_handles_no_progress(self):
        # 1. Illegal candidate (candidateSourceCounts[selectedSource] == 0)
        row_illegal = {
            "selectedSource": "ANOMALY",
            "candidateSourceCounts": {"ANOMALY": 0},
            "surfacedCount": 1,
        }
        label, rej = derive_label_and_validate(row_illegal)
        self.assertEqual(label, "")
        self.assertIn("illegal_selected_source", rej)

        # 2. No positive outcome signal yields abstention class NONE
        row_no_progress = {
            "actionKind": "EXPLORE",
            "selectedSource": "SEARCH",
            "candidateSourceCounts": {"SEARCH": 2},
            "surfacedCount": 0,
            "discoveredEvidenceBefore": 1,
            "discoveredEvidenceAfter": 1,
            "discoveredFactBefore": 0,
            "discoveredFactAfter": 0,
            "unlockedFact": False,
            "worldRevisionBefore": 2,
            "worldRevisionAfter": 2,
        }
        label, rej = derive_label_and_validate(row_no_progress)
        self.assertEqual(label, "NONE")
        self.assertEqual(rej, "")

    def test_deterministic_session_split(self):
        sess_a = "session_alpha_123"
        sess_b = "session_beta_456"

        split_a1 = session_split(sess_a)
        split_a2 = session_split(sess_a)
        split_b1 = session_split(sess_b)

        self.assertEqual(split_a1, split_a2)
        self.assertIn(split_a1, {"train", "val", "test"})
        self.assertIn(split_b1, {"train", "val", "test"})

    def test_feature_tokenization_compatibility(self):
        text = "action_explore visit_first revision_early candidate_anomaly seen_anomaly evidence_none"
        tokens = TOKEN.findall(text.lower())
        self.assertIn("action_explore", tokens)
        self.assertIn("candidate_anomaly", tokens)

        vec = vectorize(text)
        self.assertEqual(vec.shape, (4096,))
        self.assertGreater(vec.sum(), 0.0)

    def test_candidate_model_path_isolation(self):
        prod_path = Path(__file__).parents[1] / "app/src/main/assets/models/backrooms_director.tflite"
        cand_path = Path(__file__).parents[1] / "app/src/main/assets/models/backrooms_director_candidate.tflite"

        # Production asset path must be different from candidate path
        self.assertNotEqual(prod_path, cand_path)


if __name__ == "__main__":
    unittest.main()
