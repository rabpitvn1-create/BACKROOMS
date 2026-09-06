import json
import pathlib
import tempfile
import unittest

import haku_snapshot_teacher as teacher


class HakuSnapshotTeacherTest(unittest.TestCase):
    def test_validate_annotation_accepts_contract(self):
        value = teacher.validate_annotation({
            "lights": [{
                "x": 0.5, "y": 0.2, "w": 0.3, "h": 0.08,
                "kind": "linear", "confidence": 0.95,
            }],
            "ambient": 0.25,
        })
        self.assertEqual("linear", value["lights"][0]["kind"])

    def test_validate_annotation_rejects_extra_keys(self):
        with self.assertRaises(teacher.TeacherError):
            teacher.validate_annotation({"lights": [], "ambient": 0.2, "notes": "no"})

    def test_parse_message_content_accepts_openai_text_parts(self):
        parsed = teacher.parse_message_content([
            {"type": "text", "text": json.dumps({"lights": [], "ambient": 0.1})}
        ])
        self.assertEqual([], parsed["lights"])

    def test_probe_png_and_assessment(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "probe.png"
            expected = teacher.write_probe_png(path)
            self.assertTrue(path.read_bytes().startswith(b"\x89PNG"))
            annotation = {
                "lights": [{
                    "x": expected["x"],
                    "y": expected["y"],
                    "w": expected["w"],
                    "h": expected["h"],
                    "kind": "linear",
                    "confidence": 0.99,
                }],
                "ambient": 0.05,
            }
            teacher.assess_probe(teacher.validate_annotation(annotation), expected)

    def test_consensus_rejects_light_count_disagreement(self):
        one = teacher.validate_annotation({
            "lights": [{
                "x": .5, "y": .2, "w": .3, "h": .08,
                "kind": "linear", "confidence": .9,
            }],
            "ambient": .2,
        })
        none = teacher.validate_annotation({"lights": [], "ambient": .2})
        with self.assertRaises(teacher.TeacherError):
            teacher.consensus_annotation([one, none])

    def test_consensus_averages_close_passes(self):
        a = teacher.validate_annotation({
            "lights": [{
                "x": .50, "y": .20, "w": .30, "h": .08,
                "kind": "linear", "confidence": .9,
            }],
            "ambient": .20,
        })
        b = teacher.validate_annotation({
            "lights": [{
                "x": .52, "y": .22, "w": .32, "h": .09,
                "kind": "linear", "confidence": .8,
            }],
            "ambient": .24,
        })
        result = teacher.consensus_annotation([a, b])
        self.assertAlmostEqual(.51, result["lights"][0]["x"])
        self.assertAlmostEqual(.22, result["ambient"])


if __name__ == "__main__":
    unittest.main()
