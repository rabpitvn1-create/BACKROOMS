from pathlib import Path

TARGET = Path(__file__).resolve().parent / "patch-drive-canon-gameplay.py"
text = TARGET.read_text(encoding="utf-8")

old = '''if "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06" not in canon:\n    raise RuntimeError("Drive canon: wrong or missing R06 source marker")'''
new = '''if "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON" not in canon:\n    raise RuntimeError("Drive canon: wrong or missing R06 source marker")'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"R06 marker validator: expected exactly 1 legacy check, found {count}")

TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")
print("R06 source marker validator aligned with drive-canon.txt.")
