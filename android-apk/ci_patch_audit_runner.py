from pathlib import Path
import hashlib
import runpy
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
ORIGINAL_RUN_PATH = runpy.run_path
_depth = 0


def git_lines(*args: str) -> set[str]:
    result = subprocess.run(
        ["git", *args],
        cwd=REPO,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return {line for line in result.stdout.splitlines() if line}


def dirty_paths() -> set[str]:
    tracked = git_lines("diff", "--name-only", "HEAD")
    untracked = git_lines("ls-files", "--others", "--exclude-standard")
    return tracked | untracked


def fingerprint(relative_path: str) -> str:
    path = REPO / relative_path
    if not path.is_file():
        return "<missing>"
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fingerprints(paths: set[str]) -> dict[str, str]:
    return {path: fingerprint(path) for path in paths}


def label(path_name: object) -> str:
    path = Path(path_name).resolve()
    try:
        return path.relative_to(REPO).as_posix()
    except ValueError:
        return str(path)


def audited_run_path(path_name, *args, **kwargs):
    global _depth
    before_paths = dirty_paths()
    before = fingerprints(before_paths)
    name = label(path_name)
    indent = "  " * _depth
    print(f"{indent}PATCH_AUDIT_BEGIN {name}", flush=True)
    _depth += 1
    try:
        return ORIGINAL_RUN_PATH(path_name, *args, **kwargs)
    finally:
        _depth -= 1
        after_paths = dirty_paths()
        after = fingerprints(after_paths)
        changed = sorted(
            (before_paths ^ after_paths)
            | {
                path
                for path in before_paths & after_paths
                if before.get(path) != after.get(path)
            }
        )
        indent = "  " * _depth
        print(f"{indent}PATCH_AUDIT_END {name} changed={len(changed)}", flush=True)
        for path in changed:
            print(f"{indent}PATCH_AUDIT_FILE {name} {path}", flush=True)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: ci_patch_audit_runner.py <patch-script>")
    patch = Path(sys.argv[1])
    if not patch.is_file():
        raise SystemExit(f"missing patch script: {patch}")

    # Patch scripts that use runpy.run_path for nested finalizers inherit this
    # wrapper, so the CI log exposes both top-level and nested mutation boundaries.
    runpy.run_path = audited_run_path
    audited_run_path(str(patch), run_name="__main__")


if __name__ == "__main__":
    main()
