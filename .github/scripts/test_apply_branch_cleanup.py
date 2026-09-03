import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("apply_branch_cleanup.py")
spec = importlib.util.spec_from_file_location("apply_branch_cleanup", MODULE_PATH)
cleanup = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(cleanup)


class FakeClient:
    def __init__(self, *, branch_sha="abc", protected=False, open_prs=None, ahead_by=0):
        self.branch_sha = branch_sha
        self.protected = protected
        self.open_prs = list(open_prs or [])
        self.ahead_by = ahead_by
        self.deleted = []

    def get_json(self, path, params=None):
        if path == "/repos/o/r":
            return {"default_branch": "main"}
        if "/branches/" in path:
            return {
                "name": "fix/example",
                "protected": self.protected,
                "commit": {"sha": self.branch_sha},
            }
        if "/compare/" in path:
            return {"ahead_by": self.ahead_by, "behind_by": 5, "status": "behind" if self.ahead_by == 0 else "diverged"}
        raise AssertionError(f"unexpected get_json path: {path}")

    def paginate(self, path, params=None):
        if path == "/repos/o/r/pulls":
            return [{"number": number} for number in self.open_prs]
        raise AssertionError(f"unexpected paginate path: {path}")

    def delete_ref(self, repo, branch):
        self.deleted.append((repo, branch))


class ClassificationTest(unittest.TestCase):
    def classify(self, **overrides):
        values = {
            "name": "fix/example",
            "default_branch": "main",
            "protected": False,
            "open_pr_numbers": [],
            "ahead_by": 0,
        }
        values.update(overrides)
        return cleanup.classify_branch(**values)

    def test_default_branch_is_never_candidate(self):
        result, _ = self.classify(name="main")
        self.assertEqual(cleanup.KEEP, result)

    def test_protected_branch_is_never_candidate(self):
        result, _ = self.classify(protected=True)
        self.assertEqual(cleanup.KEEP, result)

    def test_open_pr_branch_is_never_candidate(self):
        result, _ = self.classify(open_pr_numbers=[42])
        self.assertEqual(cleanup.KEEP, result)

    def test_unique_commits_require_manual_review(self):
        result, _ = self.classify(ahead_by=1)
        self.assertEqual(cleanup.MANUAL_REVIEW, result)

    def test_compare_failure_requires_manual_review(self):
        result, _ = self.classify(ahead_by=None)
        self.assertEqual(cleanup.MANUAL_REVIEW, result)

    def test_zero_ahead_is_candidate(self):
        result, _ = self.classify(ahead_by=0)
        self.assertEqual(cleanup.SAFE_DELETE, result)


class RevalidationTest(unittest.TestCase):
    def candidate(self, sha="abc"):
        return {"branch": "fix/example", "tipSha": sha}

    def test_exact_safe_candidate_passes(self):
        client = FakeClient()
        safe, reason = cleanup.validate_candidate(client, "o/r", "main", self.candidate())
        self.assertTrue(safe)
        self.assertEqual("revalidated safe", reason)

    def test_tip_move_blocks_candidate(self):
        client = FakeClient(branch_sha="moved")
        safe, reason = cleanup.validate_candidate(client, "o/r", "main", self.candidate())
        self.assertFalse(safe)
        self.assertIn("tip moved", reason)

    def test_protection_blocks_candidate(self):
        client = FakeClient(protected=True)
        safe, reason = cleanup.validate_candidate(client, "o/r", "main", self.candidate())
        self.assertFalse(safe)
        self.assertIn("protected", reason)

    def test_open_pr_blocks_candidate(self):
        client = FakeClient(open_prs=[99])
        safe, reason = cleanup.validate_candidate(client, "o/r", "main", self.candidate())
        self.assertFalse(safe)
        self.assertIn("#99", reason)

    def test_new_unique_commit_blocks_candidate(self):
        client = FakeClient(ahead_by=1)
        safe, reason = cleanup.validate_candidate(client, "o/r", "main", self.candidate())
        self.assertFalse(safe)
        self.assertIn("ahead", reason)

    def test_apply_only_deletes_revalidated_candidate(self):
        client = FakeClient()
        manifest = {
            "repository": "o/r",
            "generatedAt": "2026-09-03T00:00:00Z",
            "candidates": [self.candidate()],
        }
        report = cleanup.apply_manifest(client, manifest)
        self.assertEqual(1, report["deletedCount"])
        self.assertEqual(0, report["errorCount"])
        self.assertEqual([("o/r", "fix/example")], client.deleted)

    def test_apply_skips_moved_tip_without_delete(self):
        client = FakeClient(branch_sha="moved")
        manifest = {
            "repository": "o/r",
            "generatedAt": "2026-09-03T00:00:00Z",
            "candidates": [self.candidate()],
        }
        report = cleanup.apply_manifest(client, manifest)
        self.assertEqual(0, report["deletedCount"])
        self.assertEqual(1, report["skippedCount"])
        self.assertEqual([], client.deleted)


if __name__ == "__main__":
    unittest.main()
