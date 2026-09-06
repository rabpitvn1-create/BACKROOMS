import unittest

import claude_snapshot_compat as compat
import haku_snapshot_candidate_teacher as candidate_teacher
import haku_snapshot_teacher as annotation_teacher


class ClaudeSnapshotCompatTest(unittest.TestCase):
    def setUp(self):
        compat.install()

    def test_annotation_accepts_reasoning_preamble_before_json(self):
        content = (
            "I need to inspect the fixture first.\n"
            '{"lights":[{"x":0.5,"y":0.2,"w":0.3,"h":0.08,'
            '"kind":"linear","confidence":0.95}],"ambient":0.2}'
        )
        value = annotation_teacher.parse_message_content(content)
        self.assertEqual("linear", value["lights"][0]["kind"])

    def test_choice_text_reads_reasoning_content_when_content_empty(self):
        payload = {
            "choices": [{
                "message": {
                    "reasoning_content": '{"lights":[],"ambient":0.1}',
                    "content": "",
                }
            }]
        }
        text = annotation_teacher._extract_choice_content(payload)
        self.assertIn('"ambient":0.1', text)
        parsed = annotation_teacher.parse_message_content(text)
        self.assertEqual([], parsed["lights"])

    def test_candidate_accepts_json_after_thinking_text(self):
        content = '<thinking>check marked region</thinking>\n{"fixture":false,"kind":"none","confidence":0.93}'
        value = candidate_teacher.parse_candidate_content(content)
        self.assertFalse(value["fixture"])

    def test_candidate_rejects_disagreeing_valid_objects(self):
        content = (
            '{"fixture":false,"kind":"none","confidence":0.9}\n'
            '{"fixture":true,"kind":"linear","confidence":0.9}'
        )
        with self.assertRaisesRegex(candidate_teacher.TeacherError, "disagreeing"):
            candidate_teacher.parse_candidate_content(content)

    def test_schema_scanner_ignores_unrelated_json(self):
        content = (
            'debug={"foo":"bar"}\n'
            '{"fixture":true,"kind":"point","confidence":0.8}'
        )
        value = candidate_teacher.parse_candidate_content(content)
        self.assertTrue(value["fixture"])


if __name__ == "__main__":
    unittest.main()
