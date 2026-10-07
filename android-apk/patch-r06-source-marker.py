from pathlib import Path

TARGET = Path(__file__).resolve().parent / "patch-drive-canon-gameplay.py"
text = TARGET.read_text(encoding="utf-8")

old = '''if "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06" not in canon:\n    raise RuntimeError("Drive canon: wrong or missing R06 source marker")'''
new = '''if "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON" not in canon:\n    raise RuntimeError("Drive canon: wrong or missing R06 source marker")'''

if old in text:
    TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("R06 source marker validator aligned with drive-canon.txt.")
elif new in text:
    print("R06 source marker validator already aligned.")
elif 'CANON = ROOT / "drive-canon.txt"' not in text:
    print("R06 source marker validator retired with legacy Drive canon injection.")
else:
    raise RuntimeError("R06 marker validator: expected legacy/current marker check or retired canon injection")
