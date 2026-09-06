import unittest

import haku_snapshot_candidate_teacher as teacher


class HakuSnapshotCandidateTeacherTest(unittest.TestCase):
    def test_validate_candidate_label_accepts_fixture(self):
        value = teacher.validate_candidate_label({
            "fixture": True,
            "kind": "linear",
            "confidence": 0.91,
        })
        self.assertTrue(value["fixture"])
        self.assertEqual("linear", value["kind"])

    def test_validate_candidate_label_rejects_false_with_fixture_kind(self):
        with self.assertRaises(teacher.TeacherError):
            teacher.validate_candidate_label({
                "fixture": False,
                "kind": "linear",
                "confidence": 0.8,
            })

    def test_parse_candidate_content_accepts_identical_duplicate_objects(self):
        text = (
            '{"fixture":false,"kind":"none","confidence":0.9}\n'
            '{"fixture":false,"kind":"none","confidence":0.9}'
        )
        value = teacher.parse_candidate_content(text)
        self.assertFalse(value["fixture"])
        self.assertEqual("none", value["kind"])

    def test_parse_candidate_content_rejects_disagreeing_duplicate_objects(self):
        text = (
            '{"fixture":false,"kind":"none","confidence":0.9}\n'
            '{"fixture":true,"kind":"linear","confidence":0.9}'
        )
        with self.assertRaises(teacher.TeacherError):
            teacher.parse_candidate_content(text)

    def test_consensus_candidate_requires_semantic_agreement(self):
        a = {"fixture": True, "kind": "linear", "confidence": 0.9}
        b = {"fixture": False, "kind": "none", "confidence": 0.9}
        with self.assertRaises(teacher.TeacherError):
            teacher.consensus_candidate([a, b])

    def test_consensus_candidate_averages_confidence(self):
        a = {"fixture": True, "kind": "point", "confidence": 0.8}
        b = {"fixture": True, "kind": "point", "confidence": 1.0}
        value = teacher.consensus_candidate([a, b])
        self.assertAlmostEqual(0.9, value["confidence"])

    def test_extract_candidates_finds_bright_linear_fixture(self):
        pixels = [(6, 6, 6)] * (teacher.W * teacher.H)
        pixels = list(pixels)
        x0, x1, y0, y1 = 20, 83, 12, 18
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                pixels[y * teacher.W + x] = (255, 255, 255)
        candidates = teacher.extract_candidates_from_rgb(pixels, max_candidates=8)
        self.assertGreaterEqual(len(candidates), 1)
        best = candidates[0]
        self.assertEqual("linear", best["kind_hint"])
        expected_x = ((x0 + x1 + 1) / 2) / teacher.W
        expected_y = ((y0 + y1 + 1) / 2) / teacher.H
        self.assertAlmostEqual(expected_x, best["x"], places=3)
        self.assertAlmostEqual(expected_y, best["y"], places=3)

    def test_extract_candidates_rejects_uniform_overexposure(self):
        pixels = [(255, 255, 255)] * (teacher.W * teacher.H)
        candidates = teacher.extract_candidates_from_rgb(pixels, max_candidates=8)
        self.assertEqual([], candidates)


if __name__ == "__main__":
    unittest.main()
