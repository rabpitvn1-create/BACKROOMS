#!/usr/bin/env python3
"""Read-only GitHub branch hygiene audit.

This script never mutates repository state. It classifies branches using graph
reachability against the default branch and open-PR/protection state.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

API_ROOT = "https://api.github.com"
KEEP = "KEEP"
SAFE_DELETE = "SAFE_DELETE"
MANUAL_REVIEW = "MANUAL_REVIEW"


def classify_branch(
    *,
    name: str,
    default_branch: str,
    protected: bool,
    ahead_by: int | None,
    open_pr_numbers: list[int],
) -> tuple[str, str]:
    """Return a conservative branch classification and human-readable reason."""
    if name == default_branch:
        return KEEP, "default branch"
    if protected:
        return KEEP, "protected branch"
    if open_pr_numbers:
        return KEEP, f"open PR(s): {', '.join(f'#{n}' for n in open_pr_numbers)}"
    if ahead_by is None:
        return MANUAL_REVIEW, "compare against default branch unavailable"
    if ahead_by > 0:
        return MANUAL_REVIEW, f"{ahead_by} unique commit(s) ahead of {default_branch}"
    return SAFE_DELETE, f"0 commits ahead of {default_branch}; no open PR; not protected"


class GitHubClient:
    """Minimal read-only GitHub REST client. Only GET requests are implemented."""

    def __init__(self, token: str) -> None:
        self.token = token

    def get_json(self, path: str, params: dict[str, Any] | None = None) -> Any:
        query = ""
        if params:
            query = "?" + urllib.parse.urlencode(params)
        request = urllib.request.Request(
            f"{API_ROOT}{path}{query}",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "backrooms-branch-hygiene-audit",
            },
            method="GET",
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)

    def paginate(self, path: str, params: dict[str, Any] | None = None) -> list[Any]:
        page = 1
        items: list[Any] = []
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


def iso_age_days(timestamp: str | None) -> int | None:
    if not timestamp:
        return None
    stamp = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    return max(0, (datetime.now(timezone.utc) - stamp).days)


def audit_repository(client: GitHubClient, repo: str) -> dict[str, Any]:
    repo_path = f"/repos/{repo}"
    repository = client.get_json(repo_path)
    default_branch = repository["default_branch"]

    branches = client.paginate(f"{repo_path}/branches")
    pulls = client.paginate(f"{repo_path}/pulls", {"state": "all"})
    tags = client.paginate(f"{repo_path}/tags")

    prs_by_branch: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for pr in pulls:
        head = pr.get("head") or {}
        if head.get("repo") and head.get("repo", {}).get("full_name") == repo:
            prs_by_branch[head.get("ref", "")].append(pr)

    tags_by_sha: dict[str, list[str]] = defaultdict(list)
    for tag in tags:
        commit = tag.get("commit") or {}
        sha = commit.get("sha")
        if sha:
            tags_by_sha[sha].append(tag.get("name", ""))

    rows: list[dict[str, Any]] = []
    for branch in sorted(branches, key=lambda item: item["name"].lower()):
        name = branch["name"]
        tip_sha = branch["commit"]["sha"]
        related_prs = sorted(prs_by_branch.get(name, []), key=lambda pr: pr["number"], reverse=True)
        open_pr_numbers = [pr["number"] for pr in related_prs if pr.get("state") == "open"]

        ahead_by: int | None
        behind_by: int | None
        compare_status: str | None
        compare_error: str | None = None
        if name == default_branch:
            ahead_by = 0
            behind_by = 0
            compare_status = "identical"
        else:
            base = urllib.parse.quote(default_branch, safe="")
            head = urllib.parse.quote(name, safe="")
            try:
                comparison = client.get_json(f"{repo_path}/compare/{base}...{head}")
                ahead_by = comparison.get("ahead_by")
                behind_by = comparison.get("behind_by")
                compare_status = comparison.get("status")
            except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
                ahead_by = None
                behind_by = None
                compare_status = None
                compare_error = f"{type(exc).__name__}: {exc}"

        commit_date: str | None = None
        try:
            commit = client.get_json(f"{repo_path}/commits/{tip_sha}")
            commit_date = ((commit.get("commit") or {}).get("committer") or {}).get("date")
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
            pass

        classification, reason = classify_branch(
            name=name,
            default_branch=default_branch,
            protected=bool(branch.get("protected")),
            ahead_by=ahead_by,
            open_pr_numbers=open_pr_numbers,
        )

        latest_pr = related_prs[0] if related_prs else None
        rows.append(
            {
                "branch": name,
                "tipSha": tip_sha,
                "protected": bool(branch.get("protected")),
                "aheadBy": ahead_by,
                "behindBy": behind_by,
                "compareStatus": compare_status,
                "compareError": compare_error,
                "tipCommittedAt": commit_date,
                "tipAgeDays": iso_age_days(commit_date),
                "openPrNumbers": open_pr_numbers,
                "latestPr": (
                    {
                        "number": latest_pr["number"],
                        "state": latest_pr.get("state"),
                        "mergedAt": latest_pr.get("merged_at"),
                        "closedAt": latest_pr.get("closed_at"),
                        "base": (latest_pr.get("base") or {}).get("ref"),
                    }
                    if latest_pr
                    else None
                ),
                "tagsAtTip": sorted(tags_by_sha.get(tip_sha, [])),
                "classification": classification,
                "reason": reason,
            }
        )

    counts = Counter(row["classification"] for row in rows)
    return {
        "schemaVersion": 1,
        "repository": repo,
        "defaultBranch": default_branch,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "branchCount": len(rows),
        "counts": {
            KEEP: counts[KEEP],
            SAFE_DELETE: counts[SAFE_DELETE],
            MANUAL_REVIEW: counts[MANUAL_REVIEW],
        },
        "readOnly": True,
        "branches": rows,
    }


def markdown_report(report: dict[str, Any]) -> str:
    counts = report["counts"]
    lines = [
        "# Branch Hygiene Audit",
        "",
        f"Repository: `{report['repository']}`",
        f"Default branch: `{report['defaultBranch']}`",
        f"Total branches: **{report['branchCount']}**",
        "",
        f"- KEEP: **{counts[KEEP]}**",
        f"- SAFE_DELETE candidates: **{counts[SAFE_DELETE]}**",
        f"- MANUAL_REVIEW: **{counts[MANUAL_REVIEW]}**",
        "",
        "> This report is read-only. SAFE_DELETE is a candidate label, not an automatic deletion instruction.",
        "",
        "| Classification | Branch | Ahead | Behind | Open PR | Age (days) | Reason |",
        "|---|---|---:|---:|---|---:|---|",
    ]
    order = {MANUAL_REVIEW: 0, SAFE_DELETE: 1, KEEP: 2}
    for row in sorted(report["branches"], key=lambda item: (order[item["classification"]], item["branch"].lower())):
        branch = row["branch"].replace("|", "\\|")
        reason = row["reason"].replace("|", "\\|")
        open_pr = ", ".join(f"#{n}" for n in row["openPrNumbers"]) or "-"
        ahead = "?" if row["aheadBy"] is None else str(row["aheadBy"])
        behind = "?" if row["behindBy"] is None else str(row["behindBy"])
        age = "?" if row["tipAgeDays"] is None else str(row["tipAgeDays"])
        lines.append(
            f"| {row['classification']} | `{branch}` | {ahead} | {behind} | {open_pr} | {age} | {reason} |"
        )
    lines.append("")
    return "\n".join(lines)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Read-only branch hygiene audit")
    parser.add_argument("--repo", required=True, help="owner/name")
    parser.add_argument("--output-json", default="branch-audit.json")
    parser.add_argument("--output-markdown", default="branch-audit.md")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        print("GITHUB_TOKEN is required", file=sys.stderr)
        return 2
    report = audit_repository(GitHubClient(token), args.repo)
    Path(args.output_json).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    Path(args.output_markdown).write_text(markdown_report(report), encoding="utf-8")
    print(json.dumps({"branchCount": report["branchCount"], "counts": report["counts"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
