#!/usr/bin/env python3
"""Plan and apply conservative GitHub branch cleanup.

Safety model:
- planning is GET-only and writes a manifest before any destructive action;
- application consumes the pinned manifest and revalidates every candidate;
- the default branch, protected branches, branches with an open PR, moved tips,
  compare failures, and branches with commits ahead of the current default branch
  are never deleted;
- deletion is limited to the Git ref named by a manifest candidate.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

API_ROOT = "https://api.github.com"

KEEP = "KEEP"
SAFE_DELETE = "SAFE_DELETE"
MANUAL_REVIEW = "MANUAL_REVIEW"


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def classify_branch(
    *,
    name: str,
    default_branch: str,
    protected: bool,
    open_pr_numbers: list[int],
    ahead_by: int | None,
) -> tuple[str, str]:
    if name == default_branch:
        return KEEP, "default branch"
    if protected:
        return KEEP, "protected branch"
    if open_pr_numbers:
        joined = ", ".join(f"#{number}" for number in open_pr_numbers)
        return KEEP, f"open PR(s): {joined}"
    if ahead_by is None:
        return MANUAL_REVIEW, "compare against default branch unavailable"
    if ahead_by > 0:
        return MANUAL_REVIEW, f"{ahead_by} unique commit(s) ahead of {default_branch}"
    return SAFE_DELETE, f"0 commits ahead of {default_branch}; no open PR; not protected"


class GitHubClient:
    def __init__(self, token: str) -> None:
        self.token = token

    def _request(self, path: str, *, method: str = "GET", params: dict[str, Any] | None = None) -> urllib.request.Request:
        query = ""
        if params:
            query = "?" + urllib.parse.urlencode(params)
        return urllib.request.Request(
            f"{API_ROOT}{path}{query}",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "backrooms-branch-cleanup",
            },
            method=method,
        )

    def get_json(self, path: str, params: dict[str, Any] | None = None) -> Any:
        with urllib.request.urlopen(self._request(path, params=params), timeout=30) as response:
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


def compare_branch(client: GitHubClient, repo: str, default_branch: str, branch: str) -> tuple[int | None, int | None, str | None, str | None]:
    base = urllib.parse.quote(default_branch, safe="")
    head = urllib.parse.quote(branch, safe="")
    try:
        comparison = client.get_json(f"/repos/{repo}/compare/{base}...{head}")
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
        return None, None, None, f"{type(exc).__name__}: {exc}"
    return (
        comparison.get("ahead_by"),
        comparison.get("behind_by"),
        comparison.get("status"),
        None,
    )


def open_pr_numbers(client: GitHubClient, repo: str, branch: str) -> list[int]:
    owner = repo.split("/", 1)[0]
    pulls = client.paginate(
        f"/repos/{repo}/pulls",
        {"state": "open", "head": f"{owner}:{branch}"},
    )
    return sorted(pr["number"] for pr in pulls)


def get_branch(client: GitHubClient, repo: str, branch: str) -> dict[str, Any] | None:
    encoded = urllib.parse.quote(branch, safe="")
    try:
        result = client.get_json(f"/repos/{repo}/branches/{encoded}")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise
    return result


def plan_repository(client: GitHubClient, repo: str) -> dict[str, Any]:
    repo_path = f"/repos/{repo}"
    repository = client.get_json(repo_path)
    default_branch = repository["default_branch"]
    branches = client.paginate(f"{repo_path}/branches")
    pulls = client.paginate(f"{repo_path}/pulls", {"state": "open"})

    open_prs_by_branch: dict[str, list[int]] = {}
    for pr in pulls:
        head = pr.get("head") or {}
        head_repo = head.get("repo") or {}
        if head_repo.get("full_name") != repo:
            continue
        ref = head.get("ref")
        if not ref:
            continue
        open_prs_by_branch.setdefault(ref, []).append(pr["number"])

    candidates: list[dict[str, Any]] = []
    retained: list[dict[str, Any]] = []
    counts: Counter[str] = Counter()

    for branch in sorted(branches, key=lambda item: item["name"].lower()):
        name = branch["name"]
        tip_sha = branch["commit"]["sha"]
        protected = bool(branch.get("protected"))
        prs = sorted(open_prs_by_branch.get(name, []))
        if name == default_branch:
            ahead_by, behind_by, compare_status, compare_error = 0, 0, "identical", None
        else:
            ahead_by, behind_by, compare_status, compare_error = compare_branch(client, repo, default_branch, name)

        classification, reason = classify_branch(
            name=name,
            default_branch=default_branch,
            protected=protected,
            open_pr_numbers=prs,
            ahead_by=ahead_by,
        )
        counts[classification] += 1
        row = {
            "branch": name,
            "tipSha": tip_sha,
            "protected": protected,
            "openPrNumbers": prs,
            "aheadBy": ahead_by,
            "behindBy": behind_by,
            "compareStatus": compare_status,
            "compareError": compare_error,
            "classification": classification,
            "reason": reason,
        }
        if classification == SAFE_DELETE:
            candidates.append(row)
        else:
            retained.append(row)

    return {
        "schemaVersion": 1,
        "repository": repo,
        "defaultBranch": default_branch,
        "generatedAt": now_iso(),
        "branchCount": len(branches),
        "counts": {
            KEEP: counts[KEEP],
            SAFE_DELETE: counts[SAFE_DELETE],
            MANUAL_REVIEW: counts[MANUAL_REVIEW],
        },
        "safetyRule": "Only exact pinned tips with no protection, no open PR, and ahead_by == 0 may be deleted after revalidation.",
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

    ahead_by, _, _, compare_error = compare_branch(client, repo, default_branch, name)
    if compare_error or ahead_by is None:
        return False, "compare unavailable"
    if ahead_by != 0:
        return False, f"branch now has {ahead_by} commit(s) ahead of {default_branch}"

    # Final ref/protection check immediately before deletion.
    branch = get_branch(client, repo, name)
    if branch is None:
        return False, "branch already absent"
    current_sha = (branch.get("commit") or {}).get("sha")
    if current_sha != expected_sha:
        return False, f"tip moved during revalidation: expected {expected_sha}, found {current_sha}"
    if bool(branch.get("protected")):
        return False, "branch became protected during revalidation"

    prs = open_pr_numbers(client, repo, name)
    if prs:
        return False, "open PR appeared during revalidation: " + ", ".join(f"#{number}" for number in prs)

    return True, "revalidated safe"


def apply_manifest(client: GitHubClient, manifest: dict[str, Any]) -> dict[str, Any]:
    repo = manifest["repository"]
    repository = client.get_json(f"/repos/{repo}")
    default_branch = repository["default_branch"]

    deleted: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []

    for candidate in manifest.get("candidates", []):
        name = candidate["branch"]
        expected_sha = candidate["tipSha"]
        try:
            safe, reason = validate_candidate(client, repo, default_branch, candidate)
            if not safe:
                skipped.append({"branch": name, "tipSha": expected_sha, "reason": reason})
                continue
            client.delete_ref(repo, name)
            deleted.append({"branch": name, "tipSha": expected_sha, "reason": "deleted after exact revalidation"})
        except urllib.error.HTTPError as exc:
            if exc.code == 422:
                skipped.append({"branch": name, "tipSha": expected_sha, "reason": f"GitHub refused deletion: HTTP {exc.code}"})
            elif exc.code == 404:
                skipped.append({"branch": name, "tipSha": expected_sha, "reason": "branch already absent"})
            else:
                errors.append({"branch": name, "tipSha": expected_sha, "reason": f"HTTP {exc.code}: {exc.reason}"})
        except (urllib.error.URLError, TimeoutError, RuntimeError) as exc:
            errors.append({"branch": name, "tipSha": expected_sha, "reason": f"{type(exc).__name__}: {exc}"})

    return {
        "schemaVersion": 1,
        "repository": repo,
        "defaultBranch": default_branch,
        "appliedAt": now_iso(),
        "sourceManifestGeneratedAt": manifest.get("generatedAt"),
        "candidateCount": len(manifest.get("candidates", [])),
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
    parser = argparse.ArgumentParser(description="Plan/apply conservative branch cleanup")
    parser.add_argument("--repo", required=True, help="owner/name")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--plan", action="store_true")
    mode.add_argument("--apply-manifest", metavar="PATH")
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
        payload = plan_repository(client, args.repo)
        write_json(args.output, payload)
        print(json.dumps({"branchCount": payload["branchCount"], "counts": payload["counts"]}, sort_keys=True))
        return 0

    manifest = json.loads(Path(args.apply_manifest).read_text(encoding="utf-8"))
    if manifest.get("repository") != args.repo:
        print("Manifest repository does not match --repo", file=sys.stderr)
        return 2
    payload = apply_manifest(client, manifest)
    write_json(args.output, payload)
    print(json.dumps({
        "candidateCount": payload["candidateCount"],
        "deletedCount": payload["deletedCount"],
        "skippedCount": payload["skippedCount"],
        "errorCount": payload["errorCount"],
    }, sort_keys=True))
    return 1 if payload["errorCount"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
