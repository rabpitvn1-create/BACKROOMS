from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSET = ROOT / "app/src/main/assets/entity/Kai-TheDevilWithin.png"

if not ASSET.is_file() or ASSET.stat().st_size <= 0 or ASSET.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai-TheDevilWithin.png is missing, empty, or invalid")

text = MAIN.read_text(encoding="utf-8")

legacy_rolls = [
    'thresholdRoll("kaiDevilWithinEncounter", 10000, 200,',
    'thresholdRoll("kaiDevilWithinEncounter", 10000, EntityEncounterPolicy.scaledThreshold(200),',
]
new_roll = 'thresholdRoll("kaiDevilWithinEncounter", 10000, 1000,'
matched = [anchor for anchor in legacy_rolls if anchor in text]
if matched:
    if len(matched) != 1 or text.count(matched[0]) != 1:
        raise RuntimeError("Kai Devil Within encounter threshold is ambiguous")
    text = text.replace(matched[0], new_roll, 1)
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

for old_roll in legacy_rolls:
    if old_roll in text:
        raise RuntimeError("Kai Devil Within legacy encounter threshold remains: " + old_roll)
if "Kai - The Devil Within secret form 2% all Levels/sublevels" in text:
    raise RuntimeError("Kai Devil Within legacy 2% encounter label remains")

MAIN.write_text(text, encoding="utf-8")
print("Kai - The Devil Within finalized: Snapshot allowlist enabled, local Kai-TheDevilWithin.png mapping enforced, exact encounter rate 10%.")
