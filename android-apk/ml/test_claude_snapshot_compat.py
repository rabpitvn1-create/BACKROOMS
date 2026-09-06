import unittest

import claude_snapshot_compat as compat
import haku_snapshot_candidate_teacher as candidate_teacher
import haku_snapshot_teacher as annotation_teacher


class ClaudeSnapshotCompatTest(unittest.TestCase):
    def setUp(self):
        compat.install()

    def test_resolve_openai_compatible_v1_base(self):
        self.assertEqual(
            "https://gateway.example/v1/chat/completions",
            compat.resolve_api_url("https://gateway.example/v1/"),
        )

    def test_resolve_preserves_complete_chat_endpoint(self):
        endpoint = "https://gateway.example/custom/chat/completions"
        self.assertEqual(endpoint, compat.resolve_api_url(endpoint))

    def test_resolve_anthropic_base_uses_messages(self):
        self.assertEqual(
            "https://api.anthropic.com/v1/messages",
            compat.resolve_api_url("https://api.anthropic.com"),
        )
        self.assertTrue(compat.uses_anthropic_messages("https://api.anthropic.com/v1/messages"))

    def test_resolve_rejects_relative_base(self):
        with self.assertRaisesRegex(ValueError, "absolute http"):
            compat.resolve_api_url("api.example/v1")

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

    def test_choice_text_accepts_anthropic_payload_without_choices(self):
        payload = {
            "content": [{"type": "text", "text": '{"lights":[],"ambient":0.1}'}],
            "stop_reason": "end_turn",
        }
        text = compat.extract_choice_text(payload, annotation_teacher.TeacherError)
        self.assertEqual('{"lights":[],"ambient":0.1}', text)

    def test_choice_text_accepts_nested_data_envelope(self):
        payload = {
            "data": {
                "choices": [{"message": {"content": '{"lights":[],"ambient":0.2}'}}]
            }
        }
        text = compat.extract_choice_text(payload, annotation_teacher.TeacherError)
        self.assertEqual('{"lights":[],"ambient":0.2}', text)

    def test_provider_error_summary_omits_message(self):
        payload = {
            "type": "error",
            "error": {
                "type": "routing_error",
                "code": "MODEL_ROUTE_FAILED",
                "message": "contains configured values that must not be echoed",
            },
        }
        with self.assertRaises(annotation_teacher.TeacherError) as caught:
            compat.extract_choice_text(payload, annotation_teacher.TeacherError)
        text = str(caught.exception)
        self.assertIn("type=routing_error", text)
        self.assertIn("code=MODEL_ROUTE_FAILED", text)
        self.assertNotIn("configured values", text)

    def test_anthropic_response_extracts_only_final_text_blocks(self):
        payload = {
            "content": [
                {"type": "thinking", "thinking": "internal reasoning"},
                {"type": "text", "text": '{"lights":[],"ambient":0.1}'},
            ],
            "stop_reason": "end_turn",
        }
        text = compat.extract_anthropic_text(payload, annotation_teacher.TeacherError)
        self.assertEqual('{"lights":[],"ambient":0.1}', text)

    def test_anthropic_response_requires_final_text(self):
        payload = {
            "content": [{"type": "thinking", "thinking": "internal reasoning"}],
            "stop_reason": "max_tokens",
        }
        with self.assertRaisesRegex(annotation_teacher.TeacherError, "no final text"):
            compat.extract_anthropic_text(payload, annotation_teacher.TeacherError)

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
