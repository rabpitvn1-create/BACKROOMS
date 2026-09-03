import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("archive_stale_branches.py")
spec = importlib.util.spec_from_file_location("archive_stale_branches", MODULE_PATH)
archive = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(archive)


class AnchorClient:
    def __init__(self):
        self.posts = []
        self.counter = 0

    def post_json(self, path, payload):
        self.posts.append((path, payload))
        self.counter += 1
        return {"sha": f"anchor-{self.counter}"}


class ClassificationTest(unittest.TestCase):
    def test_default_branch_is_kept(self):
        status, _ = archive.classify_branch(
            name="main", default_branch="main", protected=False, open_pr_numbers=[]
        )
        self.assertEqual("KEEP", status)

    def test_protected_branch_is_kept(self):
        status, _ = archive.classify_branch(
            name="fix/x", default_branch="main", protected=True, open_pr_numbers=[]
        )
        self.assertEqual("KEEP", status)

    def test_open_pr_branch_is_kept(self):
        status, reason = archive.classify_branch(
            name="fix/x", default_branch="main", protected=False, open_pr_numbers=[12]
        )
        self.assertEqual("KEEP", status)
        self.assertIn("#12", reason)

    def test_unprotected_branch_without_open_pr_is_archive_candidate(self):
        status, _ = archive.classify_branch(
            name="fix/x", default_branch="main", protected=False, open_pr_numbers=[]
        )
        self.assertEqual("ARCHIVE", status)


class AnchorConstructionTest(unittest.TestCase):
    def test_anchor_fans_in_tips_in_small_chunks(self):
        client = AnchorClient()
        tips = [f"tip-{index}" for index in range(45)]
        final_sha, created = archive.create_anchor_commit(
            client,
            "o/r",
            tree_sha="tree",
            main_sha="main-sha",
            tip_shas=tips,
        )
        self.assertEqual("anchor-4", final_sha)
        self.assertEqual(4, len(created))
        chunk_posts = client.posts[:3]
        self.assertEqual([21, 21, 6], [len(payload["parents"]) for _, payload in chunk_posts])
        final_parents = client.posts[-1][1]["parents"]
        self.assertEqual(["main-sha", "anchor-1", "anchor-2", "anchor-3"], final_parents)
        for _, payload in client.posts:
            self.assertEqual("tree", payload["tree"])

    def test_duplicate_and_main_tips_are_not_repeated(self):
        client = AnchorClient()
        archive.create_anchor_commit(
            client,
            "o/r",
            tree_sha="tree",
            main_sha="main-sha",
            tip_shas=["main-sha", "a", "a", "b"],
        )
        first_parents = client.posts[0][1]["parents"]
        self.assertEqual(["main-sha", "a", "b"], first_parents)


class FakeValidationClient:
    def __init__(self, *, tip="tip", protected=False, prs=None):
        self.tip = tip
        self.protected = protected
        self.prs = list(prs or [])

    def get_json(self, path, params=None):
        if "/branches/" in path:
            return {"commit": {"sha": self.tip}, "protected": self.protected}
        raise AssertionError(path)

    def paginate(self, path, params=None):
        if path.endswith("/pulls"):
            return [{"number": number} for number in self.prs]
        raise AssertionError(path)


class RevalidationTest(unittest.TestCase):
    def candidate(self):
        return {"branch": "fix/x", "tipSha": "tip"}

    def test_exact_stale_tip_passes(self):
        safe, reason = archive.validate_candidate(
            FakeValidationClient(), "o/r", "main", self.candidate()
        )
        self.assertTrue(safe)
        self.assertEqual("exact stale tip", reason)

    def test_moved_tip_is_blocked(self):
        safe, reason = archive.validate_candidate(
            FakeValidationClient(tip="moved"), "o/r", "main", self.candidate()
        )
        self.assertFalse(safe)
        self.assertIn("tip moved", reason)

    def test_protected_tip_is_blocked(self):
        safe, reason = archive.validate_candidate(
            FakeValidationClient(protected=True), "o/r", "main", self.candidate()
        )
        self.assertFalse(safe)
        self.assertIn("protected", reason)

    def test_open_pr_is_blocked(self):
        safe, reason = archive.validate_candidate(
            FakeValidationClient(prs=[77]), "o/r", "main", self.candidate()
        )
        self.assertFalse(safe)
        self.assertIn("#77", reason)


if __name__ == "__main__":
    unittest.main()
