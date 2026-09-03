from collections import deque
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
PATCH_PATTERN = re.compile(r"patch-[A-Za-z0-9_.-]+\.py")
CODE_SUFFIXES = {".py", ".sh", ".yml", ".yaml", ".gradle", ".kts"}
SKIP_DIRS = {".git", ".gradle", ".idea", "build"}

patches = {path.name: path for path in ROOT.glob("patch-*.py") if path.is_file()}


def references(path: Path) -> set[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return set()
    return {name for name in PATCH_PATTERN.findall(text) if name in patches}


edges = {name: references(path) - {name} for name, path in patches.items()}
roots: set[str] = set()

for path in REPO.rglob("*"):
    if not path.is_file():
        continue
    if any(part in SKIP_DIRS for part in path.parts):
        continue
    if path.parent == ROOT and path.name in patches:
        continue
    if path.suffix.lower() not in CODE_SUFFIXES:
        continue
    roots.update(references(path))

reachable: set[str] = set()
queue = deque(sorted(roots))
while queue:
    name = queue.popleft()
    if name in reachable:
        continue
    reachable.add(name)
    queue.extend(sorted(edges.get(name, ())))

orphans = sorted(set(patches) - reachable)
print(
    "PATCH_ORPHAN_AUDIT|"
    f"patches={len(patches)}|roots={len(roots)}|reachable={len(reachable)}|orphans={len(orphans)}",
    flush=True,
)
for name in orphans:
    print(f"PATCH_ORPHAN|{name}", flush=True)
