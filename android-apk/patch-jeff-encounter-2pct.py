from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# Jeff gets his own independent 2% roaming encounter roll on eligible physical turns.
entity_roll = '    rolls.put("entityEncounter", thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix));\n'
jeff_roll = entity_roll + '    rolls.put("jeffEncounter", thresholdRoll("jeffEncounter", 10000, 200, physical && entityAllowed && !flagSpawned(state, "jeff"), " JEFF THE KILLER roaming unique"));\n'
if 'thresholdRoll("jeffEncounter", 10000, 200' not in text:
    text = replace_once(text, entity_roll, jeff_roll, "Jeff 2 percent roll")

# A Jeff first encounter is a valid entity snapshot trigger even when the normal entity pool roll failed.
old_snapshot = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");\n'
new_snapshot = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "jeffEncounter");\n'
if new_snapshot not in text:
    text = replace_once(text, old_snapshot, new_snapshot, "Jeff snapshot authority")

# Prevent AI state patches from spawning Jeff without the locked roll, and persist a successful first encounter.
flags_tail = '''    mergeObject(safe, patch);\n    return safe;\n  }\n'''
jeff_flags_tail = '''    JSONObject proposedJeff = patch.optJSONObject("jeff");\n    JSONObject currentJeff = safe.optJSONObject("jeff");\n    boolean currentJeffPresent = currentJeff != null && (currentJeff.optBoolean("present", false) || currentJeff.optBoolean("spawned", false));\n    boolean proposedJeffPresent = proposedJeff != null && (proposedJeff.optBoolean("present", false) || proposedJeff.optBoolean("spawned", false));\n    if (!currentJeffPresent && proposedJeffPresent && !rollSuccess(rolls, "jeffEncounter")) patch.remove("jeff");\n\n    mergeObject(safe, patch);\n    if (rollSuccess(rolls, "jeffEncounter")) {\n      JSONObject jeff = safe.optJSONObject("jeff");\n      if (jeff == null) { jeff = new JSONObject(); safe.put("jeff", jeff); }\n      jeff.put("present", true).put("spawned", true).put("encounterChancePercent", 2.0);\n    }\n    return safe;\n  }\n'''
if 'encounterChancePercent", 2.0' not in text:
    text = replace_once(text, flags_tail, jeff_flags_tail, "Jeff flag authority")

# Make the GM treat the 2% roll as authoritative, not as optional flavor text.
prompt_anchor = '            "AN NHIÊN HARD LOCK:'
if 'JEFF THE KILLER HARD LOCK:' not in text:
    start = text.find(prompt_anchor)
    if start < 0:
        raise RuntimeError("Jeff prompt insertion anchor missing")
    end = text.find('\n', start)
    if end < 0:
        raise RuntimeError("Jeff prompt insertion line end missing")
    jeff_prompt = ('            "JEFF THE KILLER HARD LOCK: jeffEncounter là roll độc lập 2.0000% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi Jeff chưa hiện diện. '
                   'Nếu jeffEncounter success=true thì phải xảy ra cuộc gặp Jeff trong chính lượt đó và flags.jeff.present/spawned phải được giữ true. '
                   'Nếu success=false và Jeff chưa hiện diện từ state trước thì không được cho Jeff xuất hiện hoặc khẳng định dấu vết chắc chắn là của hắn. '
                   'Nếu Jeff đã present/spawned từ state trước thì tiếp tục cuộc săn không cần reroll. Jeff chỉ săn con người, không phải đồng minh hay NPC trung lập. " +\n')
    text = text[:end + 1] + jeff_prompt + text[end + 1:]

required = [
    'thresholdRoll("jeffEncounter", 10000, 200',
    'rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "jeffEncounter")',
    'encounterChancePercent", 2.0',
    'JEFF THE KILLER HARD LOCK:',
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Jeff 2% contract missing: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Jeff the Killer encounter locked to 2.0000% per eligible physical turn, with state and snapshot authority.")
