from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSET = ROOT / "app/src/main/assets/entity/Kai-TheDevilWithin.png"

if not ASSET.is_file() or ASSET.stat().st_size <= 0 or ASSET.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai-TheDevilWithin.png is missing, empty, or invalid")

text = MAIN.read_text(encoding="utf-8")

old_roll = 'thresholdRoll("kaiDevilWithinEncounter", 10000, 200,'
new_roll = 'thresholdRoll("kaiDevilWithinEncounter", 10000, 1000,'
if old_roll in text:
    if text.count(old_roll) != 1:
        raise RuntimeError(f"Kai Devil Within encounter threshold: expected one 2% anchor, found {text.count(old_roll)}")
    text = text.replace(old_roll, new_roll, 1)
elif new_roll not in text:
    raise RuntimeError("Kai Devil Within encounter threshold anchor missing")

if "Kai - The Devil Within secret form 2% all Levels/sublevels" in text:
    text = text.replace(
        "Kai - The Devil Within secret form 2% all Levels/sublevels",
        "Kai - The Devil Within secret form 10% all Levels/sublevels",
        1,
    )

allowlist = re.search(r"var __entityKeys=\[(.*?)\];", text)
if allowlist is None:
    raise RuntimeError("Snapshot Entity allowlist not found")
allowlist_text = allowlist.group(0)
if "'kai_the_devil_within'" not in allowlist_text:
    expanded = allowlist_text[:-2] + ",'kai_the_devil_within'];"
    text = text[:allowlist.start()] + expanded + text[allowlist.end():]

required = [
    new_roll,
    "Kai - The Devil Within secret form 10% all Levels/sublevels",
    "'kai_the_devil_within'",
    '"kai_the_devil_within".equals(entityKey) ? "Kai-TheDevilWithin.png"',
]
for marker in required:
    if marker not in text:
        raise RuntimeError("Kai Devil Within snapshot/rate contract missing: " + marker)

if old_roll in text or "Kai - The Devil Within secret form 2% all Levels/sublevels" in text:
    raise RuntimeError("Kai Devil Within legacy 2% encounter contract remains")

MAIN.write_text(text, encoding="utf-8")
print("Kai - The Devil Within finalized: Snapshot allowlist enabled, local Kai-TheDevilWithin.png mapping enforced, encounter rate 10%.")
