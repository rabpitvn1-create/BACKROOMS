#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Any

ANDROID_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ANDROID_ROOT.parent
DEFAULT_MANIFEST = ANDROID_ROOT / "patches" / "patch-chain.json"
REPORT_PATH = ANDROID_ROOT / "build" / "generated" / "patch-report.json"


class PatchError(RuntimeError):
    pass


def load_manifest(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise PatchError(f"Patch manifest not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise PatchError(f"Invalid patch manifest JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise PatchError("Patch manifest root must be an object")
    return data


def patch_map(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    patches = manifest.get("patches", [])
    if not isinstance(patches, list):
        raise PatchError("patches must be an array")
    result: dict[str, dict[str, Any]] = {}
    for patch in patches:
        if not isinstance(patch, dict):
            raise PatchError("Every patch entry must be an object")
        patch_id = patch.get("id")
        if not isinstance(patch_id, str) or not patch_id.strip():
            raise PatchError("Every patch needs a non-empty id")
        if patch_id in result:
            raise PatchError(f"Duplicate patch id: {patch_id}")
        result[patch_id] = patch
    return result


def validate_manifest(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    if manifest.get("schemaVersion") != 1:
        raise PatchError("Unsupported patch manifest schemaVersion")

    profiles = manifest.get("profiles", [])
    if not isinstance(profiles, list) or not profiles or any(not isinstance(x, str) for x in profiles):
        raise PatchError("profiles must be a non-empty array of strings")
    if manifest.get("defaultProfile") not in profiles:
        raise PatchError("defaultProfile must be listed in profiles")

    phases = manifest.get("phases", [])
    if not isinstance(phases, list) or not phases:
        raise PatchError("phases must be a non-empty array")
    phase_ids: set[int] = set()
    for phase in phases:
        if not isinstance(phase, dict) or not isinstance(phase.get("id"), int):
            raise PatchError("Every phase needs an integer id")
        phase_id = phase["id"]
        if phase_id in phase_ids:
            raise PatchError(f"Duplicate phase id: {phase_id}")
        phase_ids.add(phase_id)

    patches = patch_map(manifest)
    for patch_id, patch in patches.items():
        phase = patch.get("phase")
        if phase not in phase_ids:
            raise PatchError(f"{patch_id}: unknown phase {phase}")

        script = patch.get("script")
        if not isinstance(script, str) or not script:
            raise PatchError(f"{patch_id}: missing script")
        script_path = REPO_ROOT / script
        if not script_path.is_file():
            raise PatchError(f"{patch_id}: script does not exist: {script}")

        patch_profiles = patch.get("profiles", profiles)
        if not isinstance(patch_profiles, list) or any(p not in profiles for p in patch_profiles):
            raise PatchError(f"{patch_id}: invalid profiles")

        after = patch.get("after", [])
        if not isinstance(after, list) or any(not isinstance(dep, str) for dep in after):
            raise PatchError(f"{patch_id}: after must be an array of patch ids")
        for dep in after:
            if dep not in patches:
                raise PatchError(f"{patch_id}: unknown dependency {dep}")
            if patches[dep].get("phase", 0) > phase:
                raise PatchError(f"{patch_id}: dependency {dep} is in a later phase")

    _topological_order(manifest, include_disabled=True)
    _validate_no_nested_patch_calls(patches)
    _validate_workflow_entrypoint(patches)
    return patches


def _topological_order(
    manifest: dict[str, Any],
    *,
    profile: str | None = None,
    include_disabled: bool = False,
) -> list[dict[str, Any]]:
    all_patches = patch_map(manifest)
    ordered_ids = list(all_patches)

    selected: dict[str, dict[str, Any]] = {}
    for patch_id in ordered_ids:
        patch = all_patches[patch_id]
        if not include_disabled and not patch.get("enabled", True):
            continue
        if profile is not None and profile not in patch.get("profiles", manifest["profiles"]):
            continue
        selected[patch_id] = patch

    indegree = {patch_id: 0 for patch_id in selected}
    edges = {patch_id: [] for patch_id in selected}
    for patch_id, patch in selected.items():
        for dep in patch.get("after", []):
            if dep not in selected:
                if include_disabled:
                    continue
                raise PatchError(f"{patch_id}: selected patch depends on unavailable patch {dep}")
            indegree[patch_id] += 1
            edges[dep].append(patch_id)

    index = {patch_id: i for i, patch_id in enumerate(ordered_ids)}
    ready = [patch_id for patch_id, degree in indegree.items() if degree == 0]
    ready.sort(key=lambda pid: (selected[pid]["phase"], index[pid]))

    result: list[dict[str, Any]] = []
    while ready:
        patch_id = ready.pop(0)
        result.append(selected[patch_id])
        for child in edges[patch_id]:
            indegree[child] -= 1
            if indegree[child] == 0:
                ready.append(child)
                ready.sort(key=lambda pid: (selected[pid]["phase"], index[pid]))

    if len(result) != len(selected):
        blocked = sorted(pid for pid, degree in indegree.items() if degree > 0)
        raise PatchError("Patch dependency cycle detected: " + ", ".join(blocked))
    return result


def _validate_no_nested_patch_calls(patches: dict[str, dict[str, Any]]) -> None:
    registered = {patch["script"] for patch in patches.values()}
    for patch_id, patch in patches.items():
        script_path = REPO_ROOT / patch["script"]
        text = script_path.read_text(encoding="utf-8")
        if "runpy.run_path" in text:
            raise PatchError(f"{patch_id}: nested runpy.run_path is forbidden; declare dependencies in the manifest")
        for other in registered:
            if other != patch["script"] and other in text:
                raise PatchError(f"{patch_id}: directly references registered patch {other}")


def _validate_workflow_entrypoint(patches: dict[str, dict[str, Any]]) -> None:
    workflow_root = REPO_ROOT / ".github" / "workflows"
    if not workflow_root.is_dir():
        return
    registered = {patch["script"] for patch in patches.values()}
    for workflow in sorted(workflow_root.glob("*.yml")) + sorted(workflow_root.glob("*.yaml")):
        text = workflow.read_text(encoding="utf-8")
        for script in registered:
            if script in text:
                raise PatchError(
                    f"{workflow.relative_to(REPO_ROOT)} directly invokes {script}; workflows must call patchctl.py"
                )


def selected_patches(manifest: dict[str, Any], profile: str) -> list[dict[str, Any]]:
    if profile not in manifest["profiles"]:
        raise PatchError(f"Unknown patch profile: {profile}")
    return _topological_order(manifest, profile=profile)


def chain_hash(manifest: dict[str, Any], patches: list[dict[str, Any]]) -> str:
    digest = hashlib.sha256()
    digest.update(json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8"))
    for patch in patches:
        script = patch["script"]
        digest.update(script.encode("utf-8"))
        digest.update((REPO_ROOT / script).read_bytes())
    return digest.hexdigest()


def current_commit() -> str:
    if os.environ.get("GITHUB_SHA"):
        return os.environ["GITHUB_SHA"]
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=REPO_ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def write_report(profile: str, manifest: dict[str, Any], patches: list[dict[str, Any]], results: list[dict[str, Any]]) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "schemaVersion": 1,
        "baseCommit": current_commit(),
        "profile": profile,
        "chainHash": chain_hash(manifest, patches),
        "patchCount": len(patches),
        "patches": results,
    }
    REPORT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def apply(profile: str, manifest: dict[str, Any]) -> None:
    patches = selected_patches(manifest, profile)
    results: list[dict[str, Any]] = []
    try:
        for patch in patches:
            patch_id = patch["id"]
            script = patch["script"]
            print(f"==> [{patch['phase']:02d}] {patch_id}: {script}", flush=True)
            subprocess.run([sys.executable, str(REPO_ROOT / script)], cwd=REPO_ROOT, check=True)
            results.append({"id": patch_id, "phase": patch["phase"], "script": script, "status": "applied"})
    except subprocess.CalledProcessError as exc:
        results.append({"id": patch_id, "phase": patch["phase"], "script": script, "status": "failed", "exitCode": exc.returncode})
        write_report(profile, manifest, patches, results)
        raise PatchError(f"Patch failed: {patch_id}") from exc
    write_report(profile, manifest, patches, results)
    print(f"Patch profile '{profile}' applied: {len(patches)} patch(es)")
    print(f"Report: {REPORT_PATH.relative_to(REPO_ROOT)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="BACKROOMS deterministic patch pipeline")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("validate", help="Validate manifest, dependencies, scripts, and workflow entrypoints")
    plan_parser = sub.add_parser("plan", help="Print the deterministic patch order")
    plan_parser.add_argument("profile", nargs="?")
    apply_parser = sub.add_parser("apply", help="Apply one patch profile")
    apply_parser.add_argument("profile", nargs="?")

    args = parser.parse_args()
    manifest_path = args.manifest.resolve()
    manifest = load_manifest(manifest_path)
    validate_manifest(manifest)

    if args.command == "validate":
        print(f"Patch graph valid: {len(manifest.get('patches', []))} registered patch(es)")
        return 0

    profile = args.profile or manifest["defaultProfile"]
    patches = selected_patches(manifest, profile)
    if args.command == "plan":
        for patch in patches:
            print(f"{patch['phase']:02d} {patch['id']} {patch['script']}")
        print(f"Profile '{profile}': {len(patches)} enabled patch(es)")
        return 0

    apply(profile, manifest)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PatchError as exc:
        print(f"patchctl: {exc}", file=sys.stderr)
        raise SystemExit(2)
