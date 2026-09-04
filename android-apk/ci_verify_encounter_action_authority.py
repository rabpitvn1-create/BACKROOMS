from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

if not MAIN.is_file():
    raise SystemExit("generated MainActivity.java missing; run ci_apply_runtime_patches.py first")

text = MAIN.read_text(encoding="utf-8")

required = (
    'boolean exploreAction = "EXPLORE".equals(actionKindNormalized);',
    'boolean entityEncounterAction = exploreAction;',
    'SEARCH không được khởi tạo encounter Entity mới',
    'đây là action duy nhất được phép kích hoạt roll encounter Entity mới',
    'không tự đổi mục tiêu và không khởi tạo encounter Entity mới.',
    'StoryCompanionContinuity.canMaterialize("lucia", level',
    'put("label", "luciaEncounter").put("storyOwned", true).put("requiresQuest", false)',
)
for marker in required:
    if marker not in text:
        raise SystemExit("encounter action authority missing: " + marker)

# Companion first contact is deterministic story authority, not a random encounter roll.
if 'thresholdRoll("luciaEncounter"' in text:
    raise SystemExit("Lucia first contact regressed to a random threshold roll")

# Every remaining random *Encounter threshold in the generated Android bridge is an Entity
# channel. Verify the final composed runtime, after all scaling/balance finalizers, rather than
# pinning the test to a particular threshold expression that later patches may legitimately wrap.
encounter_calls = re.findall(
    r'thresholdRoll\("([^"]*Encounter)"[\s\S]*?\);',
    text,
)
if not encounter_calls:
    raise SystemExit("no random Entity encounter threshold calls found")

for label in sorted(set(encounter_calls)):
    # Re-find each complete call so its eligibility expression can be inspected.
    match = re.search(
        r'thresholdRoll\("' + re.escape(label) + r'"[\s\S]*?\);',
        text,
    )
    if match is None:
        raise SystemExit("could not inspect encounter call: " + label)
    call = match.group(0)
    if 'entityEncounterAction && entityAllowed' not in call:
        raise SystemExit("random Entity encounter bypasses EXPLORE-only gate: " + label)

forbidden = (
    'boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);',
    'SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới;',
    'EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới.',
)
for marker in forbidden:
    if marker in text:
        raise SystemExit("unsafe all-action encounter authority survived: " + marker)

print(
    "Encounter action authority verified: all random Entity channels are EXPLORE-only; "
    "dialogue/EXECUTE and SEARCH cannot spawn new Entities; Lucia first contact is story-owned."
)
