import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("branch_audit.py")
spec = importlib.util.spec_from_file_location("branch_audit", MODULE_PATH)
branch_audit = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(branch_audit)


class BranchClassificationTest(unittest.TestCase):
    def classify(self, **overrides):
        values = {
            "name": "fix/example",
            "default_branch": "main",
            "protected": False,
            "ahead_by": 0,
            "open_pr_numbers": [],
        }
        values.update(overrides)
        return branch_audit.classify_branch(**values)

    def test_default_branch_is_kept(self):
        classification, _ = self.classify(name="main")
        self.assertEqual(branch_audit.KEEP, classification)

    def test_protected_branch_is_kept(self):
        classification, _ = self.classify(protected=True)
        self.assertEqual(branch_audit.KEEP, classification)

    def test_open_pr_branch_is_kept(self):
        classification, reason = self.classify(open_pr_numbers=[291])
        self.assertEqual(branch_audit.KEEP, classification)
        self.assertIn("#291", reason)

    def test_unique_commits_require_manual_review(self):
        classification, reason = self.classify(ahead_by=3)
        self.assertEqual(branch_audit.MANUAL_REVIEW, classification)
        self.assertIn("3 unique commit", reason)

    def test_compare_failure_requires_manual_review(self):
        classification, _ = self.classify(ahead_by=None)
        self.assertEqual(branch_audit.MANUAL_REVIEW, classification)

    def test_zero_ahead_is_only_a_safe_delete_candidate(self):
        classification, reason = self.classify(ahead_by=0)
        self.assertEqual(branch_audit.SAFE_DELETE, classification)
        self.assertIn("0 commits ahead", reason)

    def test_client_has_no_mutation_methods(self):
        public_methods = {
            name
            for name in dir(branch_audit.GitHubClient)
            if not name.startswith("_")
        }
        self.assertEqual({"get_json", "paginate"}, public_methods)


if __name__ == "__main__":
    unittest.main()
