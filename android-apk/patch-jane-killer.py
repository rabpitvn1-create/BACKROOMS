from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# Jane mirrors Jeff's independent encounter roll for now. Step 2 will replace encounter triggering.
jeff_roll = '    rolls.put("jeffEncounter", thresholdRoll("jeffEncounter", 10000, 800, physical && entityAllowed && !flagSpawned(state, "jeff"), " JEFF THE KILLER roaming unique"));\n'
jane_roll = jeff_roll + '    rolls.put("janeEncounter", thresholdRoll("janeEncounter", 10000, 800, physical && entityAllowed && !flagSpawned(state, "jane"), " JANE THE KILLER roaming unique"));\n'
if 'thresholdRoll("janeEncounter", 10000, 800' not in text:
    text = replace_once(text, jeff_roll, jane_roll, "Jane encounter roll")

snapshot_old = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "jeffEncounter");\n'
snapshot_new = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "jeffEncounter") || rollSuccess(rolls, "janeEncounter");\n'
if snapshot_new not in text:
    text = replace_once(text, snapshot_old, snapshot_new, "Jane snapshot authority")

jeff_gate = r'''        if (root.equals("jeff") && value instanceof JSONObject) {
          JSONObject jeffPatch = (JSONObject)value;
          boolean proposedPresent = jeffPatch.optBoolean("present", false) || jeffPatch.optBoolean("spawned", false);
          JSONObject beforeJeff = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("jeff") : null;
          boolean alreadyPresent = beforeJeff != null && (beforeJeff.optBoolean("present", false) || beforeJeff.optBoolean("spawned", false));
          if (!alreadyPresent && proposedPresent && !rollSuccess(rolls, "jeffEncounter")) continue;
        }
'''
jane_gate = jeff_gate + r'''        if (root.equals("jane") && value instanceof JSONObject) {
          JSONObject janePatch = (JSONObject)value;
          boolean proposedPresent = janePatch.optBoolean("present", false) || janePatch.optBoolean("spawned", false);
          JSONObject beforeJane = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("jane") : null;
          boolean alreadyPresent = beforeJane != null && (beforeJane.optBoolean("present", false) || beforeJane.optBoolean("spawned", false));
          if (!alreadyPresent && proposedPresent && !rollSuccess(rolls, "janeEncounter")) continue;
        }
'''
if 'root.equals("jane") && value instanceof JSONObject' not in text:
    text = replace_once(text, jeff_gate, jane_gate, "Jane flag authority")

root_old = r'''    if (root.equals("jeff") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      if (root.equals("entityEncounterKey") && flags != null) {
        if (!flags.optString("entityEncounterKey", "").trim().isEmpty()) return true;
        JSONObject jeff = flags.optJSONObject("jeff");
        if (jeff != null && (jeff.optBoolean("present", false) || jeff.optBoolean("spawned", false))) return true;
      }
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
'''
root_new = r'''    if (root.equals("jeff")) {
      JSONObject flags = before.optJSONObject("flags");
      JSONObject jeff = flags != null ? flags.optJSONObject("jeff") : null;
      boolean established = jeff != null && (jeff.optBoolean("present", false) || jeff.optBoolean("spawned", false));
      return established || rollSuccess(rolls, "jeffEncounter");
    }
    if (root.equals("jane")) {
      JSONObject flags = before.optJSONObject("flags");
      JSONObject jane = flags != null ? flags.optJSONObject("jane") : null;
      boolean established = jane != null && (jane.optBoolean("present", false) || jane.optBoolean("spawned", false));
      return established || rollSuccess(rolls, "janeEncounter");
    }
    if (root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      if (root.equals("entityEncounterKey")) {
        if (rollSuccess(rolls, "jeffEncounter") || rollSuccess(rolls, "janeEncounter")) return true;
        if (flags != null) {
          if (!flags.optString("entityEncounterKey", "").trim().isEmpty()) return true;
          JSONObject jeff = flags.optJSONObject("jeff");
          if (jeff != null && (jeff.optBoolean("present", false) || jeff.optBoolean("spawned", false))) return true;
          JSONObject jane = flags.optJSONObject("jane");
          if (jane != null && (jane.optBoolean("present", false) || jane.optBoolean("spawned", false))) return true;
        }
      }
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
'''
if root_new not in text:
    text = replace_once(text, root_old, root_new, "Unique hunter final flag-root gate")

roots_old = '      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, madGod, omnivault, survivorRegistry, entityRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +\n'
roots_new = '      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, jane, madGod, omnivault, survivorRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +\n'
if roots_new not in text:
    text = replace_once(text, roots_old, roots_new, "Jane prompt root")

overlay_rule = '      "ENTITY OVERLAY HARD LOCK: với Entity đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=canonical Entity key đúng tên asset bỏ .png, ví dụ hound, smiler, skin-stealer, slenderman. Nếu Entity bị tiêu diệt, Kai chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey chỉ là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng mã Entity legacy hoặc alias theo Level. " +\n'
expanded_overlay_rule = (
    '      "ENTITY OVERLAY HARD LOCK: với Entity đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=canonical Entity key đúng tên asset bỏ .png, ví dụ hound, smiler, skin-stealer, slenderman, jeff_the_killer, jane_the_killer. Nếu Entity bị tiêu diệt, Kai chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey chỉ là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng mã cũ hoặc alias theo Level. " +\n'
    '      "ROAMING KILLER HARD LOCK: jeffEncounter và janeEncounter tạm là hai roll riêng ở bước hiện tại. Nếu roll success=true thì nhân vật tương ứng phải xuất hiện trong chính lượt đó; Jeff dùng canonical key jeff_the_killer và Jane dùng canonical key jane_the_killer. Khi bị tiêu diệt hoặc Kai thoát/chạy trốn thành công, present phải false và entityEncounterKey phải rỗng ngay lượt đó. Không dùng bất kỳ mã Entity legacy nào. " +\n'
)
if 'ROAMING KILLER HARD LOCK:' not in text:
    text = replace_once(text, overlay_rule, expanded_overlay_rule, "Jeff/Jane canonical GM hard lock")

required = [
    'thresholdRoll("janeEncounter", 10000, 800',
    'root.equals("jane") && value instanceof JSONObject',
    'if (root.equals("jane"))',
    'iris, syvial, jeff, jane, madGod',
    'ROAMING KILLER HARD LOCK:',
    'canonical key jane_the_killer',
    "return 'jane_the_killer'",
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Jane roaming contract missing: {marker}")
if ("ENT" + "-") in text:
    raise RuntimeError("Legacy Entity identifier remains in Jane runtime patch")
for forbidden_runtime_marker in (
    "var reg=f.entityRegistry",
    "return normalizeEntityId(",
    "roamingEntityId",
):
    if forbidden_runtime_marker in text:
        raise RuntimeError("Legacy Entity rendering/runtime marker remains: " + forbidden_runtime_marker)

MAIN.write_text(text, encoding="utf-8")

db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records", [])
canonical_jane = {
    "id": "ENTITY.JANE_THE_KILLER",
    "domain": "ENTITY",
    "kind": "unique-roaming-entity",
    "text": "Jane the Killer is a unique humanoid predator / roaming hunter. Runtime identity uses canonical key jane_the_killer. Jane hunts humans and is never a neutral NPC, ally, merchant or rescue character.",
    "source": {"document": "latest explicit user instruction", "anchor": "Jane the Killer"},
    "authority": "USER_OVERRIDE_ENTITY_CANON",
    "mutability": "IMMUTABLE",
    "priority": 22,
    "tags": ["jane", "jane the killer", "roaming hunter"],
    "references": ["ENTITY.GLOBAL_HARD_LOCK", "ENTITY.JEFF"],
    "affordances": ["direct_threat", "roaming_incursion"]
}
records = [r for r in records if r.get("id") != "ENTITY.JANE_THE_KILLER"]
records.append(canonical_jane)
db["records"] = records
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Jane the Killer installed with canonical Entity key jane_the_killer and no legacy Entity IDs.")
