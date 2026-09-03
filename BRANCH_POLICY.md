# Branch lifecycle policy

`main` is the authoritative integration branch. Branches are temporary development refs, not permanent historical records.

## Lifecycle

- A branch with an open pull request is active and must be kept.
- Protected branches must be kept.
- After a pull request is merged, GitHub's native **Automatically delete head branches** setting should remove the head branch.
- `release/*` branches should expire after the release tag and published asset are verified, unless they are intentionally maintained.
- `tmp/*`, `verify/*`, retry branches, intermediate integration branches, and superseded `*-v2` / `*-v3` branches should expire when their task ends.
- Historical versions belong in immutable tags, GitHub Releases, commits, and merged pull requests.

## Cleanup safety rule

Branch cleanup is audit-first. Never bulk-delete solely from a branch name, age, or merged-PR status.

The read-only audit classifies branches into three buckets:

- `KEEP`: default/protected branches or branches with an open PR.
- `SAFE_DELETE`: candidate only when the branch is not protected, has no open PR, and GitHub reports `ahead_by == 0` versus `main`.
- `MANUAL_REVIEW`: any branch with commits ahead of `main`, or any branch whose comparison cannot be verified.

`SAFE_DELETE` is deliberately a candidate label, not permission for automatic deletion. Before any destructive cleanup batch, preserve a manifest containing at least branch name, tip SHA, graph status, related PR, replacement tag/release when applicable, and deletion reason.

## Scheduled audit

The Branch Hygiene Audit workflow runs read-only and publishes JSON/Markdown artifacts. Its token has read-only repository permissions and the audit client implements GET requests only.

A future cleanup workflow must be a separate, explicitly reviewed change. It must not be added by weakening the audit workflow's permissions.
