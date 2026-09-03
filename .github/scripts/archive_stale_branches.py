#!/usr/bin/env python3
"""Archive stale branch tips behind one tag, then remove their branch refs.

The archive tag points to a synthetic commit whose tree is exactly the current
main tree. Stale branch tips are parents (through small fan-in anchor commits),
so their full commit graphs stay reachable without merging their content into
main. Deletion happens only after the archive tag exists and each branch is
revalidated against the pinned manifest.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

API_ROOT = "https://api.github.com"
PARENT_CHUNK = 20


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class GitHubClient:
    def __init__(self, token: str) -> None:
        self.token = token

    def _request(
        self,
        path: str,
        *,
        method: str = "GET",
        params: dict[str, Any] | None = None,
        payload: dict[str, Any] | None = None,
    ) -> urllib.request.Request:
        query = ""
        if params:
            query = "?" + urllib.parse.urlencode(params)
        data = None
        headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {self.token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "backrooms-branch-archive",
        }
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        return urllib.request.Request(
            f"{API_ROOT}{path}{query}",
            headers=headers,
            data=data,
            method=method,
        )

    def get_json(self, path: str, params: dict[str, Any] | None = None) -> Any:
        with urllib.request.urlopen(self._request(path, params=params), timeout=30) as response:
            return json.load(response)

    def post_json(self, path: str, payload: dict[str, Any]) -> Any:
        with urllib.request.urlopen(self._request(path, method="POST", payload=payload), timeout=30) as response:
            return json.load(response)

    def paginate(self, path: str, params: dict[str, Any] | None = None) -> list[Any]:
        items: list[Any] = []
        page = 1
        while True:
            page_params = dict(params or {})
            page_params.update({"per_page": 100, "page": page})
            chunk = self.get_json(path, page_params)
            if not isinstance(chunk, list):
                raise RuntimeError(f"Expected list from {path}, got {type(chunk).__name__}")
            items.extend(chunk)
            if len(chunk) < 100:
                return items
            page += 1

    def delete_ref(self, repo: str, branch: str) -> None:
        ref = urllib.parse.quote(f"heads/{branch}", safe="/")
        path = f"/repos/{repo}/git/refs/{ref}"
        with urllib.request.urlopen(self._request(path, method="DELETE"), timeout=30) as response:
            if response.status not in (200, 204):
                raise RuntimeError(f"Unexpected delete status {response.status} for {branch}")


def classify_branch(*, name: str, default_branch: str, protected: bool, open_pr_numbers: list[int]) -> tuple[str, str]:
    if name == default_branch:
        return "KEEP", "default branch"
    if protected:
        return "KEEP", "protected branch"
    if open_pr_numbers:
        return "KEEP", "open PR(s): " + ", ".join(f"#{number}" for number in open_pr_numbers)
    return "ARCHIVE", "no open PR and not protected"


def get_branch(client: GitHubClient, repo: str, branch: str) -> dict[str, Any] | None:
    encoded = urllib.parse.quote(branch, safe="")
    try:
        return client.get_json(f"/repos/{repo}/branches/{encoded}")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise


def open_pr_numbers(client: GitHubClient, repo: str, branch: str) -> list[int]:
    owner = repo.split("/", 1)[0]
    pulls = client.paginate(
        f"/repos/{repo}/pulls",
        {"state": "open", "head": f"{owner}:{branch}"},
    )
    return sorted(pr["number"] for pr in pulls)


def plan_repository(client: GitHubClient, repo: str) -> dict[str, Any]:
    repository = client.get_json(f"/repos/{repo}")
    default_branch = repository["default_branch"]
    branches = client.paginate(f"/repos/{repo}/branches")
    pulls = client.paginate(f"/repos/{repo}/pulls", {"state": "open"})

    open_prs_by_branch: dict[str, list[int]] = {}
    for pr in pulls:
        head = pr.get("head") or {}
        head_repo = head.get("repo") or {}
        if head_repo.get("full_name") != repo:
            continue
        ref = head.get("ref")
        if ref:
            open_prs_by_branch.setdefault(ref, []).append(pr["number"])

    candidates: list[dict[str, Any]] = []
    retained: list[dict[str, Any]] = []
    for branch in sorted(branches, key=lambda item: item["name"].lower()):
        name = branch["name"]
        protected = bool(branch.get("protected"))
        prs = sorted(open_prs_by_branch.get(name, []))
        classification, reason = classify_branch(
            name=name,
            default_branch=default_branch,
            protected=protected,
            open_pr_numbers=prs,
        )
        row = {
            "branch": name,
            "tipSha": branch["commit"]["sha"],
            "protected": protected,
            "openPrNumbers": prs,
            "classification": classification,
            "reason": reason,
        }
        (candidates if classification == "ARCHIVE" else retained).append(row)

    return {
        "schemaVersion": 1,
        "repository": repo,
        "defaultBranch": default_branch,
        "generatedAt": now_iso(),
        "branchCount": len(branches),
        "archiveCandidateCount": len(candidates),
        "retainedCount": len(retained),
        "safetyRule": "Archive tag must preserve each exact pinned tip before its branch ref can be deleted.",
        "candidates": candidates,
        "retained": retained,
    }


def validate_candidate(client: GitHubClient, repo: str, default_branch: str, candidate: dict[str, Any]) -> tuple[bool, str]:
    name = candidate["branch"]
    expected_sha = candidate["tipSha"]
    if name == default_branch:
        return False, "default branch"
    branch = get_branch(client, repo, name)
    if branch is None:
        return False, "branch already absent"
    current_sha = (branch.get("commit") or {}).get("sha")
    if current_sha != expected_sha:
        return False, f"tip moved: expected {expected_sha}, found {current_sha}"
    if bool(branch.get("protected")):
        return False, "branch is protected"
    prs = open_pr_numbers(client, repo, name)
    if prs:
        return False, "open PR(s): " + ", ".join(f"#{number}" for number in prs)
    return True, "exact stale tip"


def get_default_tip_and_tree(client: GitHubClient, repo: str, default_branch: str) -> tuple[str, str]:
    branch = get_branch(client, repo, default_branch)
    if branch is None:
        raise RuntimeError("default branch is missing")
    tip_sha = branch["commit"]["sha"]
    commit = client.get_json(f"/repos/{repo}/git/commits/{tip_sha}")
    tree_sha = (commit.get("tree") or {}).get("sha")
    if not tree_sha:
        raise RuntimeError("default branch tree SHA unavailable")
    return tip_sha, tree_sha


def create_anchor_commit(
    client: GitHubClient,
    repo: str,
    *,
    tree_sha: str,
    main_sha: str,
    tip_shas: list[str],
) -> tuple[str, list[str]]:
    unique_tips = []
    seen = {main_sha}
    for sha in tip_shas:
        if sha not in seen:
            seen.add(sha)
            unique_tips.append(sha)

    layer: list[str] = []
    created: list[str] = []
    for index in range(0, len(unique_tips), PARENT_CHUNK):
        chunk = unique_tips[index : index + PARENT_CHUNK]
        commit = client.post_json(
            f"/repos/{repo}/git/commits",
            {
                "message": f"Archive branch hygiene tips chunk {index // PARENT_CHUNK + 1}",
                "tree": tree_sha,
                "parents": [main_sha, *chunk],
            },
        )
        sha = commit["sha"]
        layer.append(sha)
        created.append(sha)

    if not layer:
        raise RuntimeError("no stale tips available for archive anchor")

    final_commit = client.post_json(
        f"/repos/{repo}/git/commits",
        {
            "message": "Archive stale branch histories after branch hygiene audit",
            "tree": tree_sha,
            "parents": [main_sha, *layer],
        },
    )
    final_sha = final_commit["sha"]
    created.append(final_sha)
    return final_sha, created


def get_tag_sha(client: GitHubClient, repo: str, tag: str) -> str | None:
    ref = urllib.parse.quote(f"tags/{tag}", safe="/")
    try:
        payload = client.get_json(f"/repos/{repo}/git/ref/{ref}")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise
    return ((payload.get("object") or {}).get("sha"))


def create_tag_ref(client: GitHubClient, repo: str, tag: str, sha: str) -> None:
    client.post_json(
        f"/repos/{repo}/git/refs",
        {"ref": f"refs/tags/{tag}", "sha": sha},
    )


def verify_anchor_contains_tips(client: GitHubClient, repo: str, anchor_sha: str, main_sha: str, required_tip_shas: set[str]) -> bool:
    final_commit = client.get_json(f"/repos/{repo}/git/commits/{anchor_sha}")
    final_parents = [parent["sha"] for parent in final_commit.get("parents", [])]
    layer_shas = [sha for sha in final_parents if sha != main_sha]
    reachable = set()
    for layer_sha in layer_shas:
        layer_commit = client.get_json(f"/repos/{repo}/git/commits/{layer_sha}")
        reachable.update(parent["sha"] for parent in layer_commit.get("parents", []))
    reachable.discard(main_sha)
    return required_tip_shas.issubset(reachable)


def apply_manifest(client: GitHubClient, manifest: dict[str, Any], archive_tag: str) -> dict[str, Any]:
    repo = manifest["repository"]
    repository = client.get_json(f"/repos/{repo}")
    default_branch = repository["default_branch"]

    validated: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []

    for candidate in manifest.get("candidates", []):
        try:
            safe, reason = validate_candidate(client, repo, default_branch, candidate)
            if safe:
                validated.append(candidate)
            else:
                skipped.append({"branch": candidate["branch"], "tipSha": candidate["tipSha"], "reason": reason})
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, RuntimeError) as exc:
            errors.append({"branch": candidate["branch"], "tipSha": candidate["tipSha"], "reason": f"{type(exc).__name__}: {exc}"})

    if errors:
        return {
            "schemaVersion": 1,
            "repository": repo,
            "archiveTag": archive_tag,
            "appliedAt": now_iso(),
            "archiveCreated": False,
            "deletedCount": 0,
            "skipped": skipped,
            "errors": errors,
            "deleted": [],
        }

    if not validated:
        return {
            "schemaVersion": 1,
            "repository": repo,
            "archiveTag": archive_tag,
            "appliedAt": now_iso(),
            "archiveCreated": False,
            "deletedCount": 0,
            "skipped": skipped,
            "errors": [],
            "deleted": [],
        }

    main_sha, tree_sha = get_default_tip_and_tree(client, repo, default_branch)
    required_tips = {candidate["tipSha"] for candidate in validated if candidate["tipSha"] != main_sha}

    anchor_sha = get_tag_sha(client, repo, archive_tag)
    created_anchor_commits: list[str] = []
    if anchor_sha is None:
        anchor_sha, created_anchor_commits = create_anchor_commit(
            client,
            repo,
            tree_sha=tree_sha,
            main_sha=main_sha,
            tip_shas=sorted(required_tips),
        )
        create_tag_ref(client, repo, archive_tag, anchor_sha)

    confirmed_tag_sha = get_tag_sha(client, repo, archive_tag)
    if confirmed_tag_sha != anchor_sha:
        raise RuntimeError("archive tag verification failed")
    if not verify_anchor_contains_tips(client, repo, anchor_sha, main_sha, required_tips):
        raise RuntimeError("archive anchor does not preserve every validated tip")

    deleted: list[dict[str, Any]] = []
    for candidate in validated:
        name = candidate["branch"]
        expected_sha = candidate["tipSha"]
        try:
            safe, reason = validate_candidate(client, repo, default_branch, candidate)
            if not safe:
                skipped.append({"branch": name, "tipSha": expected_sha, "reason": reason})
                continue
            client.delete_ref(repo, name)
            deleted.append({"branch": name, "tipSha": expected_sha, "reason": f"archived by {archive_tag}"})
        except urllib.error.HTTPError as exc:
            if exc.code in (404, 422):
                skipped.append({"branch": name, "tipSha": expected_sha, "reason": f"GitHub refused or ref disappeared: HTTP {exc.code}"})
            else:
                errors.append({"branch": name, "tipSha": expected_sha, "reason": f"HTTP {exc.code}: {exc.reason}"})
        except (urllib.error.URLError, TimeoutError, RuntimeError) as exc:
            errors.append({"branch": name, "tipSha": expected_sha, "reason": f"{type(exc).__name__}: {exc}"})

    return {
        "schemaVersion": 1,
        "repository": repo,
        "defaultBranch": default_branch,
        "archiveTag": archive_tag,
        "archiveAnchorSha": anchor_sha,
        "archiveCreated": True,
        "createdAnchorCommits": created_anchor_commits,
        "appliedAt": now_iso(),
        "validatedCount": len(validated),
        "deletedCount": len(deleted),
        "skippedCount": len(skipped),
        "errorCount": len(errors),
        "deleted": deleted,
        "skipped": skipped,
        "errors": errors,
    }


def write_json(path: str, payload: dict[str, Any]) -> None:
    Path(path).write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Archive stale branch histories and remove refs")
    parser.add_argument("--repo", required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--plan", action="store_true")
    mode.add_argument("--apply-manifest", metavar="PATH")
    parser.add_argument("--archive-tag")
    parser.add_argument("--output", required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        print("GITHUB_TOKEN is required", file=sys.stderr)
        return 2
    client = GitHubClient(token)

    if args.plan:
        report = plan_repository(client, args.repo)
        write_json(args.output, report)
        print(json.dumps({"branchCount": report["branchCount"], "archiveCandidateCount": report["archiveCandidateCount"], "retainedCount": report["retainedCount"]}, sort_keys=True))
        return 0

    if not args.archive_tag:
        print("--archive-tag is required with --apply-manifest", file=sys.stderr)
        return 2
    manifest = json.loads(Path(args.apply_manifest).read_text(encoding="utf-8"))
    if manifest.get("repository") != args.repo:
        print("Manifest repository does not match --repo", file=sys.stderr)
        return 2

    try:
        report = apply_manifest(client, manifest, args.archive_tag)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, RuntimeError) as exc:
        report = {
            "schemaVersion": 1,
            "repository": args.repo,
            "archiveTag": args.archive_tag,
            "archiveCreated": False,
            "appliedAt": now_iso(),
            "deletedCount": 0,
            "skippedCount": 0,
            "errorCount": 1,
            "deleted": [],
            "skipped": [],
            "errors": [{"reason": f"{type(exc).__name__}: {exc}"}],
        }
    write_json(args.output, report)
    print(json.dumps({"archiveTag": args.archive_tag, "archiveCreated": report.get("archiveCreated"), "deletedCount": report.get("deletedCount", 0), "errorCount": report.get("errorCount", len(report.get("errors", [])))}, sort_keys=True))
    return 1 if report.get("errors") else 0


if __name__ == "__main__":
    raise SystemExit(main())
