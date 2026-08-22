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


# Jane mirrors Jeff's independent 8% roaming encounter roll on eligible physical turns.
jeff_roll = '    rolls.put("jeffEncounter", thresholdRoll("jeffEncounter", 10000, 800, physical && entityAllowed && !flagSpawned(state, "jeff"), " JEFF THE KILLER roaming unique"));\n'
jane_roll = jeff_roll + '    rolls.put("janeEncounter", thresholdRoll("janeEncounter", 10000, 800, physical && entityAllowed && !flagSpawned(state, "jane"), " JANE THE KILLER roaming unique"));\n'
if 'thresholdRoll("janeEncounter", 10000, 800' not in text:
    text = replace_once(text, jeff_roll, jane_roll, "Jane 8 percent roll")

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

root_old = r'''    if (root.equals("jeff") || root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
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
    if (root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
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
roots_new = '      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, jane, madGod, omnivault, survivorRegistry, entityRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +\n'
if roots_new not in text:
    text = replace_once(text, roots_old, roots_new, "Jane prompt root")

overlay_rule = '      "ENTITY OVERLAY HARD LOCK: với Entity đã được xác nhận và đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=exact canon ID dạng ENT-1A/ENT-2C; Jeff the Killer dùng ENT-R01 khi hắn trực tiếp hiện diện. Nếu Entity bị tiêu diệt, Kai chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng tên thường thay cho Entity ID. " +\n'
expanded_overlay_rule = (
    '      "ENTITY OVERLAY HARD LOCK: với Entity đã được xác nhận và đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=exact canon ID dạng ENT-1A/ENT-2C; Jeff the Killer dùng ENT-R01 và Jane the Killer dùng ENT-R02 khi trực tiếp hiện diện. Nếu Entity bị tiêu diệt, Kai chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng tên thường thay cho Entity ID. " +\n'
    '      "ROAMING KILLER HARD LOCK: jeffEncounter và janeEncounter là hai roll riêng, mỗi roll đúng 8.0000% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi nhân vật tương ứng chưa từng được thiết lập trong state. Nếu roll success=true thì nhân vật tương ứng phải xuất hiện trong chính lượt đó; Jeff dùng flag_patch root=jeff present=true spawned=true và Jane dùng flag_patch root=jane present=true spawned=true. Nếu roll false và nhân vật chưa được thiết lập từ trước thì không được tự cho xuất hiện. Cả Jeff và Jane chỉ săn con người, không phải đồng minh hoặc NPC trung lập. Cơ chế Jane giống Jeff: có thể bị thương, hạ gục hoặc bị giết hoàn toàn trong encounter; cái chết kết thúc encounter hiện tại nhưng permadeath bị vô hiệu hóa, trạng thái chuyển RESPAWNING rồi tự trở lại ROAMING sau độ trễ biến thiên ở vị trí không xác định. Không respawn ngay trước mặt người chơi và không dùng respawn để farm. Khi bị tiêu diệt hoặc Kai thoát/chạy trốn thành công, present phải false và entityEncounterKey phải rỗng ngay lượt đó; spawned vẫn giữ true để ghi nhận persistent entity và cho phép trạng thái respawn/pursuit tiếp tục mà không reroll first-spawn. " +\n'
)
if 'ROAMING KILLER HARD LOCK:' not in text:
    text = replace_once(text, overlay_rule, expanded_overlay_rule, "Jeff/Jane final GM hard lock")

active_old = "if(f.jeff&&(f.jeff.present===true||f.jeff.spawned===true))return 'ENT-R01';var reg=f.entityRegistry;"
active_new = "if(f.jeff&&f.jeff.present===true)return 'ENT-R01';if(f.jane&&f.jane.present===true)return 'ENT-R02';var reg=f.entityRegistry;"
if active_new not in text:
    text = replace_once(text, active_old, active_new, "Jane Snapshot Entity ID")

required = [
    'thresholdRoll("janeEncounter", 10000, 800',
    'root.equals("jane") && value instanceof JSONObject',
    'if (root.equals("jane"))',
    'iris, syvial, jeff, jane, madGod',
    'ROAMING KILLER HARD LOCK:',
    'Jane the Killer dùng ENT-R02',
    "if(f.jane&&f.jane.present===true)return 'ENT-R02'",
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Jane roaming contract missing: {marker}")

MAIN.write_text(text, encoding="utf-8")

db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records", [])
if not any(r.get("id") == "ENTITY.JANE_THE_KILLER" for r in records):
    records.append({
        "id": "ENTITY.JANE_THE_KILLER",
        "domain": "ENTITY",
        "kind": "unique-roaming-entity",
        "text": "ENT-R02 — Jane the Killer is a unique humanoid predator / roaming hunter allowed as roaming/incursion across Level 0–6. Jane hunts humans and is never a neutral NPC, ally, merchant or rescue character. Her encounter mechanism mirrors Jeff the Killer: an independent 8.0000% eligible physical-turn roll; once established she may persist through pursuit without reroll. She can be injured, knocked down, forced to retreat or killed completely in a specific encounter. Killing her ends that encounter, but permadeath is disabled: state becomes RESPAWNING, then automatically returns to ROAMING after a variable delay at an unknown/non-deterministic allowed location. Respawn must not occur immediately in front of the player, must create a real breathing period, and must not become an infinite loot farm. Her origin, identity continuity mechanism and any personal relationship to Jeff remain UNKNOWN unless later canon explicitly locks them.",
        "source": {"document": "01_WORLD/entity.md + latest explicit user instruction", "anchor": "ENT-R02 / Jane the Killer"},
        "authority": "USER_OVERRIDE_ENTITY_CANON",
        "mutability": "IMMUTABLE",
        "priority": 22,
        "tags": ["ent-r02", "jane", "jane the killer", "roaming hunter", "respawning", "permadeath disabled"],
        "references": ["ENTITY.GLOBAL_HARD_LOCK", "ENTITY.JEFF_THE_KILLER"],
        "affordances": ["direct_threat", "roaming_incursion"]
    })
    db["records"] = records
    KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Jane the Killer installed: independent 8% Level 0-6 encounter, Jeff-mirrored persistent respawn, validated state gates, ENT-R02 Snapshot wiring, and runtime knowledge record.")
