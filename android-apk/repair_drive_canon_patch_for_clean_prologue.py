from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
TARGET = ROOT / "patch-drive-canon-gameplay.py"
SELF = Path(__file__)
WORKFLOW = REPO / ".github/workflows/repair-drive-canon-clean-prologue.yml"

text = TARGET.read_text(encoding="utf-8")
old = '''for old, new, label in (
    ("Kênh nội bộ Black Blood im lặng.", "Kênh nội bộ SRU Force im lặng.", "prologue internal channel"),
    ("Không biết Black Blood còn có thể tìm thấy dấu vết của ba người từ phía bên kia hay không.", "Không biết SRU Force còn có thể tìm thấy dấu vết của ba người từ phía bên kia hay không.", "prologue recovery question"),
    ("Không có liên lạc với Iris, Syvial hay Black Blood.", "Không có liên lạc với Iris, Syvial hay SRU Force.", "prologue first turn status"),
):
    index = replace_once(index, old, new, label)
'''
new = '''for old, new, label in (
    ("Kênh nội bộ Black Blood im lặng.", "Kênh nội bộ SRU Force im lặng.", "prologue internal channel"),
    ("Không biết Black Blood còn có thể tìm thấy dấu vết của ba người từ phía bên kia hay không.", "Không biết SRU Force còn có thể tìm thấy dấu vết của ba người từ phía bên kia hay không.", "prologue recovery question"),
    ("Không có liên lạc với Iris, Syvial hay Black Blood.", "Không có liên lạc với Iris, Syvial hay SRU Force.", "prologue first turn status"),
):
    count = index.count(old)
    if count > 1:
        raise RuntimeError(f"{label}: expected at most 1 legacy match, found {count}")
    if count == 1:
        index = index.replace(old, new, 1)

# Source-clean builds no longer contain the legacy restaurant/Black Blood prologue,
# so the compatibility rewrite above is intentionally allowed to be a no-op.
prologue_start = index.find("const prologue=`")
initial_start = index.find("const initial={", prologue_start)
if prologue_start < 0 or initial_start < 0:
    raise RuntimeError("Drive canon: prologue boundary missing")
prologue_block = index[prologue_start:initial_start]
if "Black Blood" in prologue_block:
    raise RuntimeError("Drive canon: legacy Black Blood prologue survived")
'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"drive_canon_patch_block_anchor_count:{count}")
TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")

if WORKFLOW.exists():
    WORKFLOW.unlink()
if SELF.exists():
    SELF.unlink()

print("patch-drive-canon-gameplay adapted to source-clean prologue")
