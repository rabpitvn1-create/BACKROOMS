from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# Latest user balance authority: every Entity encounter channel that is currently
# 10% or higher is reduced to exactly 5%. Rates already below 10% are preserved.
# The dice basis is 10,000, so 10% == 1000 and 5% == 500.
TEN_PERCENT_THRESHOLD = 1000
FIVE_PERCENT_THRESHOLD = 500

text = MAIN.read_text(encoding="utf-8")

# Shared roaming Entity encounter rates are Level-specific. Cap only values that
# are at least 10%; do not raise or otherwise rewrite lower rates.
array_pattern = re.compile(r'(?m)^(\s*int\[\] entityThresholds = \{)([0-9, ]+)(\};\s*)$')
array_matches = list(array_pattern.finditer(text))
if len(array_matches) != 1:
    raise RuntimeError(f"Entity threshold array: expected exactly 1 match, found {len(array_matches)}")

array_match = array_matches[0]
original_values = [int(value.strip()) for value in array_match.group(2).split(",")]
capped_values = [FIVE_PERCENT_THRESHOLD if value >= TEN_PERCENT_THRESHOLD else value for value in original_values]
replacement = array_match.group(1) + ", ".join(str(value) for value in capped_values) + array_match.group(3)
text = text[:array_match.start()] + replacement + text[array_match.end():]

# Unique Entity channels use the same 10,000-point dice basis. Diệp Minh is 3%
# and SCP-173 is already 5%, so they remain unchanged. Monster X and John Doe
# are currently 10% and therefore become 5%.
entity_channels = (
    "diepMinhEncounter",
    "monsterXEncounter",
    "johnDoeEncounter",
    "scp173Encounter",
)
for channel in entity_channels:
    pattern = re.compile(r'(thresholdRoll\("' + re.escape(channel) + r'",\s*10000,\s*)(\d+)')
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise RuntimeError(f"{channel}: expected exactly 1 thresholdRoll, found {len(matches)}")
    match = matches[0]
    current = int(match.group(2))
    resolved = FIVE_PERCENT_THRESHOLD if current >= TEN_PERCENT_THRESHOLD else current
    text = text[:match.start(2)] + str(resolved) + text[match.end(2):]

# Keep the dice audit text honest after lowering the two unique 10% channels.
text = text.replace("Monster X unique roaming 10% Level 0-999", "Monster X unique roaming 5% Level 0-999")
text = text.replace("John Doe unique roaming 10% Level 0-999", "John Doe unique roaming 5% Level 0-999")

MAIN.write_text(text, encoding="utf-8")

# Final contract checks run against the fully generated MainActivity, after every
# earlier encounter/balance patch. This is intentionally the last spawn authority.
final_text = MAIN.read_text(encoding="utf-8")
final_array_match = array_pattern.search(final_text)
if final_array_match is None:
    raise RuntimeError("Final Entity threshold array missing")
final_values = [int(value.strip()) for value in final_array_match.group(2).split(",")]
if any(value >= TEN_PERCENT_THRESHOLD for value in final_values):
    raise RuntimeError(f"Shared Entity encounter rate >=10% survived cap: {final_values}")

expected_channels = {
    "diepMinhEncounter": 300,
    "monsterXEncounter": 500,
    "johnDoeEncounter": 500,
    "scp173Encounter": 500,
}
for channel, expected in expected_channels.items():
    pattern = re.compile(r'thresholdRoll\("' + re.escape(channel) + r'",\s*10000,\s*(\d+)')
    match = pattern.search(final_text)
    if match is None:
        raise RuntimeError(f"Final {channel} threshold missing")
    actual = int(match.group(1))
    if actual != expected:
        raise RuntimeError(f"Final {channel} threshold expected {expected}, found {actual}")

if 'Monster X unique roaming 10% Level 0-999' in final_text:
    raise RuntimeError("Stale Monster X 10% encounter audit text remains")
if 'John Doe unique roaming 10% Level 0-999' in final_text:
    raise RuntimeError("Stale John Doe 10% encounter audit text remains")
if 'jeffEncounter' in final_text or 'janeEncounter' in final_text:
    raise RuntimeError("Removed Jeff/Jane independent encounter channels must not return")

print(
    "Entity spawn cap applied: shared roaming >=10% -> 5%; "
    "Monster X 10% -> 5%; John Doe 10% -> 5%; SCP-173 stays 5%; Diệp Minh stays 3%."
)
