from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

# The unique encounter patches are layered after the shared roaming roll. Before this
# finalizer they can all succeed independently in one action. forceEntityEncounterFlag
# then picks a higher-priority unique Entity while the writer still sees a successful
# lower-priority roll/key, allowing narration and the committed CombatRuntime encounter
# to disagree. Make the existing priority exclusive at dice time instead:
# Diệp Minh > Monster X > John Doe > SCP-173 > shared roaming Entity.
monster_old = '''    JSONObject monsterXRoll = thresholdRoll("monsterXEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && monsterXLevel >= 0 && monsterXLevel <= 999, " Monster X unique roaming 10% Level 0-999");
'''
monster_new = '''    JSONObject monsterXRoll = thresholdRoll("monsterXEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && monsterXLevel >= 0 && monsterXLevel <= 999, " Monster X unique roaming 10% Level 0-999");
'''
text = replace_once(text, monster_old, monster_new, "Monster X encounter priority gate")

john_old = '''    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && johnDoeLevel >= 0 && johnDoeLevel <= 999,
      " John Doe unique roaming 10% Level 0-999");
'''
john_new = '''    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && johnDoeLevel >= 0 && johnDoeLevel <= 999,
      " John Doe unique roaming 10% Level 0-999");
'''
text = replace_once(text, john_old, john_new, "John Doe encounter priority gate")

scp_old = '''    JSONObject scp173Roll = thresholdRoll("scp173Encounter", 10000, 500,
      entityEncounterAction && entityAllowed,
      " SCP-173 independent 5% valid encounter");
'''
scp_new = '''    JSONObject scp173Roll = thresholdRoll("scp173Encounter", 10000, 500,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false),
      " SCP-173 independent 5% valid encounter");
'''
text = replace_once(text, scp_old, scp_new, "SCP-173 encounter priority gate")

normal_old = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);\n'
normal_new = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false), entitySuffix);\n'
text = replace_once(text, normal_old, normal_new, "shared roaming encounter priority gate")

# The normal roaming key is created only when normalEntityRoll succeeds. With the gate
# above a unique encounter therefore cannot leave a competing roamingEntityKey behind for
# the Game Master to narrate. Keep that existing invariant explicit.
roaming_guard = '    if (normalEntityRoll.optBoolean("success", false)) {\n'
if text.count(roaming_guard) != 1:
    raise RuntimeError(f"Expected exactly one normal roaming key success guard, found {text.count(roaming_guard)}")

# Verify the final runtime still commits the same priority order. This patch only removes
# contradictory simultaneous successes; it does not change any Entity's own probability,
# Level eligibility, asset mapping, or CombatRuntime profile.
helper_start = text.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = text.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("Final encounter helper boundary missing")
helper = text[helper_start:helper_end]
priority_markers = [
    'JSONObject boss = rolls.optJSONObject("diepMinhEncounter")',
    'JSONObject monsterX = rolls.optJSONObject("monsterXEncounter")',
    'JSONObject johnDoe = rolls.optJSONObject("johnDoeEncounter")',
    'JSONObject scp173 = rolls.optJSONObject("scp173Encounter")',
    'JSONObject normal = rolls.optJSONObject("entityEncounter")',
]
positions = []
for marker in priority_markers:
    pos = helper.find(marker)
    if pos < 0:
        raise RuntimeError("Encounter priority helper missing: " + marker)
    positions.append(pos)
if positions != sorted(positions):
    raise RuntimeError("Encounter priority helper order changed unexpectedly")

for forbidden in (
    'entityEncounterAction && entityAllowed && monsterXLevel >= 0 && monsterXLevel <= 999',
    'entityEncounterAction && entityAllowed && johnDoeLevel >= 0 && johnDoeLevel <= 999',
    'JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);',
):
    if forbidden in text:
        raise RuntimeError("Ungated competing encounter channel remains: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("Entity encounter authority synchronized: only one encounter channel can succeed per action (Diep Minh > Monster X > John Doe > SCP-173 > shared roaming).")
