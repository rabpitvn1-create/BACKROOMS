from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

old = '    int[] entityThresholds = {5, 200, 350, 350, 10, 400, 5};\n'
new = '    int[] entityThresholds = {805, 1000, 1150, 1150, 810, 1200, 805};\n'

if new not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Entity threshold anchor: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

if 'int[] entityThresholds = {805, 1000, 1150, 1150, 810, 1200, 805}' not in text:
    raise RuntimeError("Entity +8 percentage-point contract missing")

MAIN.write_text(text, encoding="utf-8")
print("Entity encounter chances increased by +8 percentage points on Levels 0-6: 8.05%, 10%, 11.5%, 11.5%, 8.1%, 12%, 8.05%.")
