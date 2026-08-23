from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

# Jeff and Jane are Entity choices, not extra encounter dice. One successful Entity roll may
# produce exactly one canonical roaming key, so concurrent independent rolls can never overwrite
# one another or create hidden second encounters.
old_pool = '      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","slenderman"};\n'
new_pool = '      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"};\n'
if new_pool not in text:
    text = replace_once(text, old_pool, new_pool, "shared Entity roaming pool")

for label in ("jeffEncounter", "janeEncounter"):
    lines = text.splitlines(keepends=True)
    matches = [line for line in lines if f'rolls.put("{label}"' in line]
    if len(matches) != 1:
        raise RuntimeError(f"{label} roll removal: expected exactly 1 line, found {len(matches)}")
    text = "".join(line for line in lines if f'rolls.put("{label}"' not in line)

# Any remaining authorization that used the old unique roll now derives from the single selected
# roamingEntityKey. Existing flags remain readable for save compatibility, but cannot create a new
# Jeff/Jane encounter without the shared Entity roll selecting that key.
text = text.replace(
    'rollSuccess(rolls, "jeffEncounter")',
    '(rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", "")))'
)
text = text.replace(
    'rollSuccess(rolls, "janeEncounter")',
    '(rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")))'
)

# The final overlay bridge must never overwrite the selected normal Entity with a second unique roll.
helper_start = text.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = text.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("forceEntityEncounterFlag boundary missing")
unified_helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    JSONObject normal = rolls.optJSONObject("entityEncounter");
    if (normal == null || !normal.optBoolean("success", false)) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    if (entityKey.isEmpty()) return;
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    flags.put("entityEncounterKey", normalizedEntityKey(entityKey));
  }
'''
text = text[:helper_start] + unified_helper + text[helper_end:]

# Current visual presence is authoritative only through entityEncounterKey. Old Jeff/Jane presence
# flags may survive migrated saves and must not resurrect a defeated/escaped overlay.
old_active = "function activeEntityKey(){var f=state&&state.flags||{};var direct=normalizeEntityKey(f.entityEncounterKey);if(direct)return direct;if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer';if(f.jane&&f.jane.present===true)return 'jane_the_killer';return '';}"
new_active = "function activeEntityKey(){var f=state&&state.flags||{};return normalizeEntityKey(f.entityEncounterKey);}"
if new_active not in text:
    text = replace_once(text, old_active, new_active, "direct-only Entity visual state")

# Rewrite temporary Step-1 prompt language so the GM cannot reason about removed rolls.
text = text.replace(
    'Jeff the Killer và Jane the Killer tạm giữ roll độc lập riêng ở bước hiện tại nhưng dùng key jeff_the_killer và jane_the_killer.',
    'Jeff the Killer và Jane the Killer nằm trong cùng LOCAL ROAMING POOL và chỉ xuất hiện khi roamingEntityKey chọn đúng canonical key jeff_the_killer hoặc jane_the_killer.'
)
text = text.replace(
    'SEARCH không được khởi tạo encounter Entity mới và entityEncounter/jeffEncounter/janeEncounter phải ineligible;',
    'SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible;'
)

lines = []
for line in text.splitlines(keepends=True):
    if '"JEFF THE KILLER HARD LOCK:' in line:
        lines.append('            "JEFF THE KILLER HARD LOCK: Jeff là roaming Entity trong pool chung. Không dùng roll riêng cho Jeff; chỉ khi entityEncounter success=true và roamingEntityKey=jeff_the_killer mới được khởi tạo encounter Jeff mới. Jeff chỉ săn con người, không phải đồng minh hay NPC trung lập. " +\n')
    elif '"ROAMING KILLER HARD LOCK:' in line:
        lines.append('      "ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter và cùng roamingEntityKey với mọi Entity khác. Mỗi entityEncounter thành công chỉ chọn đúng một canonical Entity key; không có roll Jeff/Jane độc lập và không được tạo encounter thứ hai trong cùng lượt. " +\n')
    else:
        lines.append(line)
text = "".join(lines)

# The old snapshot condition becomes redundant after unique-roll replacement; normalize it to one
# source of truth so later patches cannot mistake Jeff/Jane for separate encounter channels.
old_snapshot = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || (rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", ""))) || (rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")));\n'
new_snapshot = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");\n'
if old_snapshot in text:
    text = text.replace(old_snapshot, new_snapshot, 1)

if "jeffEncounter" in text or "janeEncounter" in text:
    raise RuntimeError("Independent Jeff/Jane encounter channel remains in final MainActivity")
for marker in (
    '"hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"',
    'rolls.put("roamingEntityKey"',
    'String entityKey = rolls.optString("roamingEntityKey", "").trim();',
    'function activeEntityKey(){var f=state&&state.flags||{};return normalizeEntityKey(f.entityEncounterKey);}',
    'ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter',
):
    if marker not in text:
        raise RuntimeError("Unified Entity pool contract missing: " + marker)

MAIN.write_text(text, encoding="utf-8")

# Combat cleanup must be persisted into the authoritative core world state. Clearing only the
# WebView projection lets the repository rehydrate the old entityEncounterKey on a later turn.
facade = FACADE.read_text(encoding="utf-8")
old_cleanup = '''    if (time.applied) next = time.state
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    appendLog(output, action, resolution.reply)
'''
new_cleanup = '''    if (time.applied) next = time.state
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = JSONObject(next.world["flagsJson"] ?: "{}")
      flags.put("entityEncounterKey", "")
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    appendLog(output, action, resolution.reply)
'''
if new_cleanup not in facade:
    facade = replace_once(facade, old_cleanup, new_cleanup, "persistent combat Entity cleanup")

for marker in (
    'val flags = JSONObject(next.world["flagsJson"] ?: "{}")',
    'flags.put("entityEncounterKey", "")',
    'next = next.copy(world = next.world + ("flagsJson" to flags.toString()))',
    'repository.save(next)',
):
    if marker not in facade:
        raise RuntimeError("Persistent combat cleanup contract missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
print("Unified Entity spawn pool installed: Jeff/Jane share entityEncounter + roamingEntityKey, and combat cleanup persists direct visual state.")
