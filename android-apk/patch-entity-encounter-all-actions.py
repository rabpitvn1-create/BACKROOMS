from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Final encounter authority: only EXPLORE may start a NEW roaming Entity encounter.
# SEARCH is observation/resource discovery inside the current location. EXECUTE is the
# player's explicit freeform intent, including dialogue. Neither may ambush the player
# with a fresh Entity merely because another ordinary turn was submitted.
#
# Keep entityEncounterAction as a compatibility alias because later unique-Entity
# patches share this gate instead of inventing their own action semantics.
explore_line = '    boolean exploreAction = "EXPLORE".equals(actionKindNormalized);\n'
encounter_line = '    boolean entityEncounterAction = exploreAction;\n'
if encounter_line not in text:
    if explore_line not in text:
        raise RuntimeError("Typed action encounter anchor missing")
    text = text.replace(explore_line, explore_line + encounter_line, 1)

normal_old = (
    '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, '
    'entityThresholds[level], exploreAction && entityAllowed, entitySuffix);\n'
)
normal_new = (
    '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, '
    'entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);\n'
)
text = replace_once(text, normal_old, normal_new, "shared EXPLORE-only Entity encounter gate")

# Jeff/Jane may still exist as temporary independent rolls at this point in the patch chain.
# The final unified-pool pass can remove those channels later, but while they exist they must
# obey the same EXPLORE-only authority.
for label in ("jeffEncounter", "janeEncounter"):
    old = f'    rolls.put("{label}", thresholdRoll("{label}", 10000, 800, exploreAction && entityAllowed'
    new = f'    rolls.put("{label}", thresholdRoll("{label}", 10000, 800, entityEncounterAction && entityAllowed'
    if old in text:
        text = text.replace(old, new, 1)

# Keep GM prose aligned with local authoritative dice. A dialogue/EXECUTE turn must never be
# narrated as a fresh random encounter, and SEARCH must not silently become EXPLORE.
search_required = "SEARCH không được khởi tạo encounter Entity mới và entityEncounter/jeffEncounter/janeEncounter phải ineligible;"
search_fallback = "SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible;"
if search_required not in text and search_fallback not in text:
    raise RuntimeError("SEARCH EXPLORE-only Entity prompt gate missing")

explore_required = "đây là action duy nhất được phép kích hoạt roll encounter Entity mới;"
if explore_required not in text:
    raise RuntimeError("EXPLORE Entity prompt authority missing")

execute_required = "không tự đổi mục tiêu và không khởi tạo encounter Entity mới."
if execute_required not in text:
    raise RuntimeError("EXECUTE Entity prompt authority missing")

for marker in (
    'boolean exploreAction = "EXPLORE".equals(actionKindNormalized);',
    'boolean entityEncounterAction = exploreAction;',
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed',
    explore_required,
    execute_required,
):
    if marker not in text:
        raise RuntimeError("EXPLORE-only Entity encounter contract missing: " + marker)

for forbidden in (
    'boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);',
    "SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới;",
    "EXPLORE roll Entity theo cùng cơ chế với SEARCH và EXECUTE;",
    "EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới.",
):
    if forbidden in text:
        raise RuntimeError("Obsolete all-action Entity encounter contract survived: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("Final Entity encounter action gate installed: new roaming Entity encounters are EXPLORE-only; SEARCH/EXECUTE remain encounter-safe.")
