import unittest

import claude_model_resolver as resolver


class ClaudeModelResolverTest(unittest.TestCase):
    def test_normalize_candidates_deduplicates_in_order(self):
        self.assertEqual(
            ["ccf/claude-opus-4-8", "occ/claude-opus-4-8"],
            resolver.normalize_candidates([
                " ccf/claude-opus-4-8 ",
                "occ/claude-opus-4-8",
                "ccf/claude-opus-4-8",
            ]),
        )

    def test_resolve_model_skips_forbidden_route(self):
        statuses = {
            "ccf/claude-opus-4-8": (False, 403),
            "occ/claude-opus-4-8": (True, 200),
        }

        def fake_probe(model, **kwargs):
            return statuses[model]

        selected, attempts = resolver.resolve_model(
            statuses,
            api_key="test-key",
            api_url="https://example.invalid/v1/chat/completions",
            probe=fake_probe,
        )
        self.assertEqual("occ/claude-opus-4-8", selected)
        self.assertEqual([
            ("ccf/claude-opus-4-8", 403),
            ("occ/claude-opus-4-8", 200),
        ], attempts)

    def test_resolve_model_fails_when_none_authorized(self):
        def fake_probe(model, **kwargs):
            return False, 403

        with self.assertRaisesRegex(resolver.ResolveError, "no authorized Claude route"):
            resolver.resolve_model(
                ["ccf/claude-opus-4-8", "occ/claude-opus-4-8"],
                api_key="test-key",
                api_url="https://example.invalid/v1/chat/completions",
                probe=fake_probe,
            )


if __name__ == "__main__":
    unittest.main()
