from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
KNOWLEDGE = APP / "src/main/assets/knowledge/knowledge_db.json"
KCE = APP / "src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
MAIN = APP / "src/main/java/com/rabpit/backroom/MainActivity.java"
ADDITIONS = ROOT / "entity-runtime-additions.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# Merge the missing Entity records and the global roaming rule.
db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
add = json.loads(ADDITIONS.read_text(encoding="utf-8"))
records = db.get("records")
if not isinstance(records, list):
    raise RuntimeError("knowledge_db.json records missing")
by_id = {record.get("id"): index for index, record in enumerate(records) if isinstance(record, dict)}
for record in add["records"]:
    rid = record["id"]
    if rid in by_id:
        records[by_id[rid]] = record
    else:
        by_id[rid] = len(records)
        records.append(record)

entity_ids = [record["id"] for record in records if record.get("domain") == "ENTITY" and record.get("kind") == "entity"]
expected = add["expectedEntityIds"]
missing = [rid for rid in expected if rid not in entity_ids]
if missing:
    raise RuntimeError(f"Entity roster incomplete after merge: {missing}")
if len(expected) != 19 or len(set(expected)) != 19:
    raise RuntimeError("Entity roaming roster must contain exactly 19 unique Entity IDs")
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Teach the budgeted knowledge builder to inject the exact Entity records that won their
# independent appearance rolls. No Level pool is consulted here.
kce = KCE.read_text(encoding="utf-8")
old_entity_state = '''      val confirmedEntities = flags?.optInt("entitiesConfirmedLocal", 0) ?: 0
      val entityRoll = rolls.optJSONObject("entityEncounter")?.optBoolean("success", false) ?: false
      if (confirmedEntities > 0 || entityRoll || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff")) {
        add("ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
      }'''
new_entity_state = '''      val confirmedEntities = flags?.optInt("entitiesConfirmedLocal", 0) ?: 0
      val entityEncounter = rolls.optJSONObject("entityEncounter")
      val entityRoll = entityEncounter?.optBoolean("success", false) ?: false
      if (confirmedEntities > 0 || entityRoll || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff", "async member")) {
        add("ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
        add("ENTITY.ROAMING_POLICY", "entity state/scene requires roaming policy")
      }
      val successIds = entityEncounter?.optJSONArray("successIds")
      if (successIds != null) {
        for (i in 0 until successIds.length()) {
          val id = successIds.optString(i, "")
          if (id.startsWith("ENTITY.")) add(id, "successful independent 3% roaming roll")
        }
      }'''
if new_entity_state not in kce:
    kce = replace_once(kce, old_entity_state, new_entity_state, "knowledge Entity roll routing")
KCE.write_text(kce, encoding="utf-8")

# Replace the old one-pool-per-Level roll plus Jeff's special 2% roll with 19 independent
# 3% rolls. The only eligibility gate retained is a physical gameplay action; Level and
# environment never participate in the decision.
main = MAIN.read_text(encoding="utf-8")
roll_anchor_re = re.compile(
    r'''(?m)^    String entitySuffix = level == 0 \|\| level == 4 \|\| level == 6 \? " incursion/roaming only" : "";\n'''
    r'''    rolls\.put\("entityEncounter", thresholdRoll\("entityEncounter", 10000, entityThresholds\[level\], physical && entityAllowed, entitySuffix\)\);\n'''
    r'''    rolls\.put\("jeffEncounter", thresholdRoll\("jeffEncounter", 10000, 200, physical && entityAllowed && !flagSpawned\(state, "jeff"\), " JEFF THE KILLER roaming unique"\)\);\n'''
)

specs = [
    ("ENTITY.HOUND", "Hound"),
    ("ENTITY.CLUMP", "Clump"),
    ("ENTITY.DULLER", "Duller"),
    ("ENTITY.DEATHMOTH", "Deathmoth"),
    ("ENTITY.HOSTILE_FACELING", "Hostile Faceling"),
    ("ENTITY.FALSE_PUDDLE", "False Puddle"),
    ("ENTITY.PAINTINGS", "Paintings"),
    ("ENTITY.SMILER", "Smiler"),
    ("ENTITY.SKIN_STEALER", "Skin-Stealer"),
    ("ENTITY.PREDATORY_WINDOW", "Predatory Window"),
    ("ENTITY.BIOLOGICAL_PIPELINE", "Biological Pipeline"),
    ("ENTITY.WRETCH", "Wretch"),
    ("ENTITY.CABLE_MIMIC", "Cable Mimic"),
    ("ENTITY.BEAST_LEVEL_5", "The Beast of Level 5"),
    ("ENTITY.HOTEL_CORPSE_LURE", "Hotel Corpse Lure"),
    ("ENTITY.JEFF", "Jeff the Killer"),
    ("ENTITY.JANE", "Jane the Killer"),
    ("ENTITY.SLENDERMAN", "Slenderman"),
    ("ENTITY.ASYNC_MEMBER", "Async Member"),
]
if [entity_id for entity_id, _ in specs] != expected:
    raise RuntimeError("Java encounter roster differs from entity-runtime-additions.json")

lines = [
    '    JSONObject entityRolls = new JSONObject();',
    '    JSONArray entitySuccessIds = new JSONArray();',
]
for index, (entity_id, display_name) in enumerate(specs):
    variable = f"entityRoll{index}"
    suffix = display_name.replace('"', '\\"')
    lines.append(
        f'    JSONObject {variable} = thresholdRoll("{entity_id}", 10000, 300, physical, " {suffix} roaming all-levels");'
    )
    lines.append(f'    entityRolls.put("{entity_id}", {variable});')
    lines.append(f'    if ({variable}.optBoolean("success", false)) entitySuccessIds.put("{entity_id}");')
lines.extend([
    '    JSONObject entityEncounter = new JSONObject()',
    '      .put("label", "entityEncounter")',
    '      .put("eligible", physical)',
    '      .put("success", entitySuccessIds.length() > 0)',
    '      .put("chancePerEntityPercent", 3.0)',
    '      .put("independentPerEntity", true)',
    '      .put("allPlayableLevels", true)',
    '      .put("environmentRestricted", false)',
    '      .put("successIds", entitySuccessIds)',
    '      .put("rolls", entityRolls);',
    '    rolls.put("entityEncounter", entityEncounter);',
    # Keep Jeff compatibility for the existing validated Jeff state gate. It is the same
    # roll, copied without rerolling, so Jeff is exactly 3% like every other Entity.
    '    rolls.put("jeffEncounter", new JSONObject(entityRolls.getJSONObject("ENTITY.JEFF").toString()).put("label", "jeffEncounter"));',
])
new_roll_block = "\n".join(lines) + "\n"

if 'chancePerEntityPercent", 3.0' not in main:
    main, count = roll_anchor_re.subn(new_roll_block, main, count=1)
    if count != 1:
        raise RuntimeError(f"Entity roaming roll block: expected exactly 1 match, found {count}")

# The later knowledge-context patch replaces the older writer prompt entirely, so insert the
# current roaming rule into the final writer prompt rather than depending on Jeff's retired 2% text.
policy_text = (
    'ENTITY ROAMING HARD LOCK: entityEncounter chứa đúng 19 roll độc lập, mỗi Entity 3.0000% trên mỗi lượt physical. '
    'Không giới hạn Level hoặc môi trường. Mọi ID trong entityEncounter.successIds phải thật sự xuất hiện trong lượt đó; '
    'Entity không thắng roll không được tự nhiên xuất hiện mới. Nhiều ID có thể cùng thắng và cùng xuất hiện trong một lượt. '
    'Tất cả Entity encounter đều thù địch với con người; riêng Async Member là một combatant con người và không suy rộng thành mọi nhân sự Async đều thù địch. '
    'Jeff dùng chính roll ENTITY.JEFF này, không có tỷ lệ riêng. '
)
if "ENTITY ROAMING HARD LOCK:" not in main:
    prompt_anchor = '      "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc database/context/state/roll/prompt trong văn xuôi.\\n\\n" +\n'
    if prompt_anchor not in main:
        raise RuntimeError("Final knowledge writer prompt anchor missing")
    prompt_line = f'      "{policy_text}" +\n'
    main = main.replace(prompt_anchor, prompt_anchor + prompt_line, 1)

required = [
    'thresholdRoll("ENTITY.HOUND", 10000, 300, physical',
    'thresholdRoll("ENTITY.JEFF", 10000, 300, physical',
    'thresholdRoll("ENTITY.JANE", 10000, 300, physical',
    'thresholdRoll("ENTITY.SLENDERMAN", 10000, 300, physical',
    'thresholdRoll("ENTITY.ASYNC_MEMBER", 10000, 300, physical',
    '.put("chancePerEntityPercent", 3.0)',
    '.put("independentPerEntity", true)',
    '.put("allPlayableLevels", true)',
    '.put("environmentRestricted", false)',
    'ENTITY ROAMING HARD LOCK:',
]
for marker in required:
    if marker not in main:
        raise RuntimeError(f"3% roaming contract missing: {marker}")
if 'thresholdRoll("jeffEncounter", 10000, 200' in main:
    raise RuntimeError("Legacy Jeff 2% roll still present after roaming patch")

MAIN.write_text(main, encoding="utf-8")
print("19 Entity entries now roll independently at 3.0000% on physical turns across every playable Level with no environment gate.")
