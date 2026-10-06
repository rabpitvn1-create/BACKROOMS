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


# Final authority: all three primary gameplay actions may start a new Entity encounter.
# Keep exploreAction because existing release verification still checks the typed-action bridge,
# then derive one explicit encounter gate for SEARCH / EXECUTE / EXPLORE.
explore_line = '    boolean exploreAction = "EXPLORE".equals(actionKindNormalized);\n'
encounter_line = (
    '    boolean entityEncounterAction = exploreAction || '
    '"SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);\n'
)
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
text = replace_once(text, normal_old, normal_new, "shared Entity encounter gate")

# Jeff/Jane may still exist as temporary independent rolls at this point in the patch chain.
# The final unified-pool pass removes those channels, but making them use the same action gate here
# keeps this patch correct even if it is inspected or executed before that final unification.
for label in ("jeffEncounter", "janeEncounter"):
    old = f'    rolls.put("{label}", thresholdRoll("{label}", 10000, 800, exploreAction && entityAllowed'
    new = f'    rolls.put("{label}", thresholdRoll("{label}", 10000, 800, entityEncounterAction && entityAllowed'
    if old in text:
        text = text.replace(old, new, 1)

# The GM prompt must agree with the authoritative local dice. Otherwise the model can suppress a
# successful local roll because an obsolete prose rule still says SEARCH/EXECUTE cannot encounter.
search_old_variants = (
    "SEARCH không được khởi tạo encounter Entity mới và entityEncounter/jeffEncounter/janeEncounter phải ineligible;",
    "SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible;",
)
search_new = "SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới;"
if search_new not in text:
    replaced = False
    for old in search_old_variants:
        if old in text:
            text = text.replace(old, search_new, 1)
            replaced = True
            break
    if not replaced:
        raise RuntimeError("SEARCH Entity prompt gate anchor missing")

explore_old = "đây là action duy nhất được phép kích hoạt roll encounter Entity mới;"
explore_new = "EXPLORE roll Entity theo cùng cơ chế với SEARCH và EXECUTE;"
if explore_new not in text:
    if explore_old in text:
        text = text.replace(explore_old, explore_new, 1)

execute_old = "không tự đổi mục tiêu và không khởi tạo encounter Entity mới."
execute_new = "không tự đổi mục tiêu; EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới."
if execute_new not in text:
    if execute_old not in text:
        raise RuntimeError("EXECUTE Entity prompt gate anchor missing")
    text = text.replace(execute_old, execute_new, 1)

for marker in (
    'boolean exploreAction = "EXPLORE".equals(actionKindNormalized);',
    'boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);',
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed',
    search_new,
    execute_new,
):
    if marker not in text:
        raise RuntimeError("All-action Entity encounter contract missing: " + marker)

for forbidden in (
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level], exploreAction && entityAllowed',
    "SEARCH không được khởi tạo encounter Entity mới",
    "đây là action duy nhất được phép kích hoạt roll encounter Entity mới",
    "không tự đổi mục tiêu và không khởi tạo encounter Entity mới",
):
    if forbidden in text:
        raise RuntimeError("Obsolete EXPLORE-only Entity gate survived: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("Final Entity encounter action gate installed: SEARCH, EXECUTE and EXPLORE can all spawn Entity encounters.")
