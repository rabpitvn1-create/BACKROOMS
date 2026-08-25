from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
ENGINES = ROOT / "app/src/main/java/com/rabpit/backroom/core/Engines.kt"
MADGOD_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"

main = MAIN.read_text(encoding="utf-8")

# Accept every authoritative equipment projection, not only the compact legacy `set` shape.
old_equipment = "function equippedItem(s){try{var e=state&&state.equipment;if(!e)return null;if(e.set&&String(e.set.id||'')==='madgod:set'&&(s==='armor'||s==='weapon'))return e.set;return e[s]||null}catch(e){return null}}function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||'').indexOf('madgod:')===0)}"
new_equipment = "function equippedItem(s){try{var e=state&&state.equipment||{};if(e.set&&String(e.set.id||e.set)==='madgod:set'&&(s==='armor'||s==='weapon'))return e.set;var direct=e[s];if(direct)return typeof direct==='string'?{id:direct,name:direct}:direct;var members=state&&state.partyDetails&&state.partyDetails.members;if(Array.isArray(members)){var kai=members.find(function(m){return String(m&&m.id)==='kai'});var value=kai&&kai.equipment&&kai.equipment[s];if(value)return typeof value==='string'?{id:value,name:value}:value}return null}catch(e){return null}}function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||x.name||x).toLowerCase().indexOf('madgod')>=0)}"
if old_equipment not in main:
    raise RuntimeError("MadGod overlay equipment detector anchor missing")
main = main.replace(old_equipment, new_equipment, 1)

badge_call = "box.appendChild(kai);appendEquipmentBadge(box);if(!r)"
if badge_call not in main:
    raise RuntimeError("Snapshot MadGod badge call anchor missing")
main = main.replace(badge_call, "box.appendChild(kai);if(!r)", 1)

old_cache = "function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri?r:null;}catch(e){return null;}}function renderSnapshot()"
new_cache = "function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where}function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri&&r.sceneKey===visualSceneKey()?r:null;}catch(e){return null;}}function renderSnapshot()"
if old_cache not in main:
    raise RuntimeError("Snapshot cache anchor missing")
main = main.replace(old_cache, new_cache, 1)
main = main.replace(
    "JSON.stringify({turn:r.turn,model:r.model||'AI',dataUri:r.dataUri})",
    "JSON.stringify({turn:r.turn,sceneKey:visualSceneKey(),model:r.model||'AI',dataUri:r.dataUri})",
    1,
)

current_level_anchor = '''  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
'''
level_helpers = '''  private int mentionedLevel(JSONObject state) {
    String location = state.optString("location", "").toLowerCase(java.util.Locale.ROOT);
    String title = state.optString("title", "").toLowerCase(java.util.Locale.ROOT);
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("level\\\\s*([0-6])", java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher explicit = pattern.matcher(location);
    if (explicit.find()) return Integer.parseInt(explicit.group(1));
    String[] names = {"the lobby", "parking zone", "pipe dreams", "the electrical station", "the abandoned office", "terror hotel", "lights out"};
    for (int n = 0; n < names.length; n++) if (location.contains(names[n])) return n;
    explicit = pattern.matcher(title);
    if (explicit.find()) return Integer.parseInt(explicit.group(1));
    for (int n = 0; n < names.length; n++) if (title.contains(names[n])) return n;
    return -1;
  }

  private int levelTurns(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    return exploration != null ? Math.max(0, exploration.optInt("levelTurns", 0)) : 0;
  }

  private boolean progressionReady(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    boolean explicitlyReady = exploration != null && (exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false));
    return explicitlyReady || levelTurns(state) >= 6;
  }

  private void recordLevelProgress(JSONObject state, int oldLevel, int newLevel) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    exploration.put("levelTurns", oldLevel == newLevel ? levelTurns(state) + 1 : 0);
    exploration.put("minimumTurns", 6);
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }

'''
if current_level_anchor not in main:
    raise RuntimeError("Level helper anchor missing")
main = main.replace(current_level_anchor, level_helpers + current_level_anchor, 1)

old_transition = '''    return (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
'''
new_transition = '''    boolean exitFound = (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
    return exitFound && progressionReady(before);
'''
if old_transition not in main:
    raise RuntimeError("Transition gate anchor missing")
main = main.replace(old_transition, new_transition, 1)

old_after_commit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          boolean levelChanged = oldLevel != newLevel;
'''
new_after_commit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          int mentioned = mentionedLevel(state);
          if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
            newLevel = mentioned;
            state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
          }
          boolean levelChanged = oldLevel != newLevel;
'''
if old_after_commit not in main:
    raise RuntimeError("Post-commit Level recognition anchor missing")
main = main.replace(old_after_commit, new_after_commit, 1)

progress_anchor = '''            flags.put("currentLevel", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
'''
progress_replacement = progress_anchor + '''            state.put("flags", flags);
            recordLevelProgress(state, oldLevel, newLevel);
            flags = state.optJSONObject("flags");
'''
if progress_anchor not in main:
    raise RuntimeError("Progress recording anchor missing")
main = main.replace(progress_anchor, progress_replacement, 1)

main = main.replace(
    '"EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. "',
    '"EXPLORE HARD LOCK: chủ động mở rộng known space từng khu vực; có thể đổi location cục bộ, gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit. Không hoàn tất cả Level trong 2–3 lượt: cần ít nhất 6 lượt gameplay trong Level và một Exit hợp lệ; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. "',
    1,
)

MAIN.write_text(main, encoding="utf-8")

index = INDEX.read_text(encoding="utf-8")
old_ui = "function madGodSetEquipped(){try{const e=state&&state.equipment;return !!(e&&e.set&&String(e.set.id||'')==='madgod:set')}catch(ignore){return false}}"
new_ui = "function madGodSetEquipped(){try{const e=state&&state.equipment||{};if(e.set&&String(e.set.id||e.set)==='madgod:set')return true;if(['weapon','armor'].some(k=>String((e[k]&&e[k].id)||e[k]||'').toLowerCase().includes('madgod')))return true;const members=state&&state.partyDetails&&state.partyDetails.members;const kai=Array.isArray(members)&&members.find(m=>String(m&&m.id)==='kai');return !!(kai&&kai.equipment&&['weapon','armor'].some(k=>String((kai.equipment[k]&&kai.equipment[k].id)||kai.equipment[k]||'').toLowerCase().includes('madgod')))}catch(ignore){return false}}"
if old_ui not in index:
    raise RuntimeError("Character UI MadGod detector anchor missing")
index = index.replace(old_ui, new_ui, 1)
INDEX.write_text(index, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
old_bind = '''          val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = boundSlots))), "item_equipped")
'''
new_bind = '''          val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)
          val carried = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
          changed(state.copy(
            inventories = state.inventories + (command.actorId to carried),
            equipment = state.equipment + (command.actorId to equipment.copy(slots = boundSlots))
          ), "item_equipped")
'''
if old_bind not in engines:
    raise RuntimeError("MadGod inventory-to-equipment binding anchor missing")
engines = engines.replace(old_bind, new_bind, 1)
ENGINES.write_text(engines, encoding="utf-8")

test = MADGOD_TEST.read_text(encoding="utf-8")
inventory_assert_anchor = '''    assertEquals(MADGOD_SET_ID, after["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, after["ring"])
'''
inventory_assert_replacement = inventory_assert_anchor + '''    assertFalse(equip.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
'''
if inventory_assert_anchor not in test:
    raise RuntimeError("MadGod equipped inventory regression anchor missing")
test = test.replace(inventory_assert_anchor, inventory_assert_replacement, 1)
MADGOD_TEST.write_text(test, encoding="utf-8")

combined = MAIN.read_text(encoding="utf-8") + "\n" + INDEX.read_text(encoding="utf-8") + "\n" + ENGINES.read_text(encoding="utf-8") + "\n" + MADGOD_TEST.read_text(encoding="utf-8")
for marker in (
    "sceneKey:visualSceneKey()",
    "r.sceneKey===visualSceneKey()",
    "private int mentionedLevel(JSONObject state)",
    "return exitFound && progressionReady(before);",
    "exploration.put(\"minimumTurns\", 6)",
    "recordLevelProgress(state, oldLevel, newLevel)",
    "partyDetails&&state.partyDetails.members",
    "val carried = removeItem(source, command.itemId, command.quantity)",
    "assertFalse(equip.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))",
):
    if marker not in combined:
        raise RuntimeError("Progression/snapshot/equipment contract missing: " + marker)

if "appendEquipmentBadge(box)" in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("Snapshot MadGod text badge still renders over the overlay")

print("Installed robust MadGod projection, scene-keyed Snapshot cache, location Level recognition, and six-turn progression gate.")

# Pressure Combat owns active encounters first. The unified pool patch then rewrites the completed
# final runtime so Jeff/Jane share the single Entity encounter channel and cleanup persists in core.
pressure = ROOT / "patch-pressure-combat.py"
if not pressure.is_file():
    raise RuntimeError("Pressure Combat patch missing")
exec(compile(pressure.read_text(encoding="utf-8"), str(pressure), "exec"), {"__name__": "__main__", "__file__": str(pressure)})

unified_pool = ROOT / "patch-unified-entity-spawn-pool.py"
if not unified_pool.is_file():
    raise RuntimeError("Unified Entity spawn pool patch missing")
exec(compile(unified_pool.read_text(encoding="utf-8"), str(unified_pool), "exec"), {"__name__": "__main__", "__file__": str(unified_pool)})

# Character healthbar runs after the final runtime/UI transforms so it binds to the actual Character Detail DOM.
healthbar = ROOT / "patch-character-healthbar.py"
if not healthbar.is_file():
    raise RuntimeError("Character healthbar patch missing")
exec(compile(healthbar.read_text(encoding="utf-8"), str(healthbar), "exec"), {"__name__": "__main__", "__file__": str(healthbar)})

# SCP-173 must run after the deferred final Entity authority sequence injected into
# patch-character-healthbar.py (unified pool -> Diệp Minh -> Monster X -> John Doe).
# This keeps every existing Entity patch intact and lets SCP-173 extend the actual
# finalized combat/state/status/encounter runtime instead of replacing it.
scp173 = ROOT / "patch-scp-173-entity.py"
if not scp173.is_file():
    raise RuntimeError("SCP-173 Entity patch missing")
exec(compile(scp173.read_text(encoding="utf-8"), str(scp173), "exec"), {"__name__": "__main__", "__file__": str(scp173)})

# Preserve established non-SCP contracts after SCP-173 adds target-specific
# mitigation/narration. This runs last and is intentionally tiny.
scp173_compat = ROOT / "patch-scp-173-compat-finalize.py"
if not scp173_compat.is_file():
    raise RuntimeError("SCP-173 compatibility finalizer missing")
exec(compile(scp173_compat.read_text(encoding="utf-8"), str(scp173_compat), "exec"), {"__name__": "__main__", "__file__": str(scp173_compat)})

# Item authority is finalized after every historical compatibility patch so no later
# generator can silently restore name-based profiles, save migration or destructive DROP.
item_system = ROOT / "patch-extensible-item-system-finalize.py"
if not item_system.is_file():
    raise RuntimeError("Extensible item system finalizer missing")
exec(compile(item_system.read_text(encoding="utf-8"), str(item_system), "exec"), {"__name__": "__main__", "__file__": str(item_system)})

# The selected default Kai overlay is finalized last so compatibility patches cannot
# restore an obsolete asset reference or package the retired overlay binaries.
best_kai = ROOT / "patch-best-kai-overlay-finalize.py"
if not best_kai.is_file():
    raise RuntimeError("BestKai overlay finalizer missing")
exec(compile(best_kai.read_text(encoding="utf-8"), str(best_kai), "exec"), {"__name__": "__main__", "__file__": str(best_kai)})
