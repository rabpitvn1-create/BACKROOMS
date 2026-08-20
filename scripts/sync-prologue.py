from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "android-apk/app/src/main/assets/index.html"
CANON = ROOT / "lib/canon.js"

index = INDEX.read_text(encoding="utf-8")
canon = CANON.read_text(encoding="utf-8")

match = re.search(r"const prologue=`(?P<body>.*?)`;\s*\n\s*const initial=", index, re.DOTALL)
if not match:
    raise RuntimeError("Không tìm thấy const prologue trong APK index.html")

prologue = match.group("body")
canon_pattern = re.compile(
    r"export const PROLOGUE_TEXT = `.*?`;\s*\n\s*export const GAME_MASTER_CANON",
    re.DOTALL,
)
replacement = f"export const PROLOGUE_TEXT = `{prologue}`;\n\nexport const GAME_MASTER_CANON"
canon, count = canon_pattern.subn(lambda _: replacement, canon, count=1)
if count != 1:
    raise RuntimeError(f"PROLOGUE_TEXT sync expected 1 match, found {count}")

# patch-drive-canon-gameplay.py expects the original compact state anchor.
compact_anchor = 'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;let busy=false;'
pretty_anchor = 'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;\nlet busy=false;'
if compact_anchor not in index:
    if pretty_anchor not in index:
        raise RuntimeError("Không tìm thấy state anchor để chuẩn bị Drive patch")
    index = index.replace(pretty_anchor, compact_anchor, 1)

CANON.write_text(canon, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(f"Synced Prologue ({len(prologue)} chars) from APK to lib/canon.js and prepared Android anchor.")
