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

# Gate Jeff flag mutations through the same validated state-op path used by the final runtime.
old_flag_gate = '''        Object value = op.get("value");
        if (root.equals("exploration") && value instanceof JSONObject) {
'''
new_flag_gate = '''        Object value = op.get("value");
        if (root.equals("jeff") && value instanceof JSONObject) {
          JSONObject jeffPatch = (JSONObject)value;
          boolean proposedPresent = jeffPatch.optBoolean("present", false) || jeffPatch.optBoolean("spawned", false);
          JSONObject beforeJeff = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("jeff") : null;
          boolean alreadyPresent = beforeJeff != null && (beforeJeff.optBoolean("present", false) || beforeJeff.optBoolean("spawned", false));
          if (!alreadyPresent && proposedPresent && !rollSuccess(rolls, "jeffEncounter")) continue;
        }
        if (root.equals("exploration") && value instanceof JSONObject) {
'''
if 'root.equals("jeff") && value instanceof JSONObject' not in text:
    text = replace_once(text, old_flag_gate, new_flag_gate, "Jeff flag authority")

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
                   'Nếu jeffEncounter success=true thì phải xảy ra cuộc gặp Jeff trong chính lượt đó và phải trả flag_patch root=jeff với present=true, spawned=true. '
                   'Nếu success=false và Jeff chưa hiện diện từ state trước thì không được cho Jeff xuất hiện hoặc khẳng định dấu vết chắc chắn là của hắn. '
                   'Nếu Jeff đã present/spawned từ state trước thì tiếp tục cuộc săn không cần reroll. Jeff chỉ săn con người, không phải đồng minh hay NPC trung lập. " +\n')
    text = text[:end + 1] + jeff_prompt + text[end + 1:]

required = [
    'thresholdRoll("jeffEncounter", 10000, 200',
    'rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "jeffEncounter")',
    'root.equals("jeff") && value instanceof JSONObject',
    'JEFF THE KILLER HARD LOCK:',
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Jeff 2% contract missing: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Jeff the Killer encounter locked to 2.0000% per eligible physical turn through validated state operations.")
