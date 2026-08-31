from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# VISUAL_STATE_SYNC_FINAL_V3
# Snapshot state is a projection of authoritative gameplay state. Free-text story fields,
# stale cache keys and historical Entity flags must never keep an obsolete visual scene alive.
main = MAIN.read_text(encoding="utf-8")

# Cache identity follows both the authoritative Level and the current area/location identity.
# Prefer an exact string Level id when present; old numeric saves remain compatible through number.
old_scene_key = "function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where}"
new_scene_key = "function visualSceneKey(){var level=state&&state.level;var l=level&&((level.id!==undefined&&level.id!==null)?level.id:level.number);var where=String(state&&state.location||'').trim().toLowerCase();var area=String(state&&state.flags&&state.flags.visualAreaKey||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where+'|'+area}"
if new_scene_key not in main:
    main = replace_once(main, old_scene_key, new_scene_key, "area-aware Snapshot scene key")

# Legacy snapshot setup used a numeric 0..6 picker and this finalizer historically upgraded it to
# structured state. The catalog-driven snapshot resolver already performs the stronger string-ID
# projection itself, so do not force that new resolver back through the obsolete numeric anchor.
old_level_picker = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;"
new_level_picker = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var structuredLevel=state&&state.level&&state.level.number;var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
generic_snapshot_picker = "function resolveSnapshotPool(id)" in main
if not generic_snapshot_picker and new_level_picker not in main:
    main = replace_once(main, old_level_picker, new_level_picker, "authoritative Snapshot Level picker")

# CombatRuntime is the sole visual-presence authority for Entity pixels. entityEncounterKey remains
# a compatibility/state field, but it must never resurrect an Entity when no combat session is active.
old_active_entity = "function activeEntityKey(){var f=state&&state.flags||{};var direct=normalizeEntityKey(f.entityEncounterKey);if(direct)return direct;if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer';if(f.jane&&f.jane.present===true)return 'jane_the_killer';return '';}"
new_active_entity = "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}"
if new_active_entity not in main:
    main = replace_once(main, old_active_entity, new_active_entity, "authoritative current Entity visual source")

# Reconcile every Level-bearing field before the candidate enters Game State Core. Structured gameplay
# state wins. A stale location that still names another Level is replaced with a canonical new-Level
# location, while a legitimate area name without a conflicting Level marker is preserved.
helper = r'''  private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls) throws Exception {
    int oldLevel = currentLevel(before);
    int structuredLevel = currentLevel(candidateState);
    int describedLevel = mentionedLevel(candidateState);
    int requestedLevel = structuredLevel != oldLevel ? structuredLevel : (describedLevel >= 0 ? describedLevel : oldLevel);
    boolean levelChange = requestedLevel != oldLevel;

    if (levelChange && !canTransition(before, rolls)) {
      JSONObject oldStructured = before.optJSONObject("level");
      candidateState.put("level", oldStructured != null
        ? new JSONObject(oldStructured.toString())
        : new JSONObject().put("number", oldLevel).put("name", levelName(oldLevel)));
      if (before.has("title")) candidateState.put("title", before.optString("title", ""));
      if (before.has("location")) candidateState.put("location", before.optString("location", ""));
      return;
    }

    if (levelChange) {
      candidateState.put("level", new JSONObject().put("number", requestedLevel).put("name", levelName(requestedLevel)));
      candidateState.put("title", "Level " + requestedLevel + " – " + levelName(requestedLevel));

      String candidateLocation = candidateState.optString("location", "").trim();
      int locationLevel = -1;
      if (!candidateLocation.isEmpty()) {
        JSONObject locationProbe = new JSONObject().put("location", candidateLocation);
        locationLevel = mentionedLevel(locationProbe);
      }
      if (candidateLocation.isEmpty() || (locationLevel >= 0 && locationLevel != requestedLevel)) {
        candidateState.put("location", "Level " + requestedLevel + " / " + levelName(requestedLevel));
      }
    } else {
      candidateState.put("level", new JSONObject().put("number", oldLevel).put("name", levelName(oldLevel)));
    }
  }

'''
if "private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls)" not in main:
    anchor = "  private int mentionedLevel(JSONObject state) {\n"
    if anchor not in main:
        raise RuntimeError("mentionedLevel anchor missing for visual world reconciliation")
    main = main.replace(anchor, helper + anchor, 1)

reconcile_call = "          reconcileVisualWorldState(before, candidateState, rolls);\n"
if reconcile_call not in main:
    anchor = "          forceEntityEncounterFlag(candidateState, rolls);\n"
    if anchor not in main:
        raise RuntimeError("validated candidate pre-commit anchor missing for visual world reconciliation")
    main = main.replace(anchor, reconcile_call + anchor, 1)

for marker in (
    "var area=String(state&&state.flags&&state.flags.visualAreaKey||'')",
    "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';",
    "private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls)",
    "structuredLevel != oldLevel ? structuredLevel",
    'candidateState.put("location", "Level " + requestedLevel + " / " + levelName(requestedLevel))',
    "reconcileVisualWorldState(before, candidateState, rolls);",
):
    if marker not in main:
        raise RuntimeError("Visual state synchronization contract missing: " + marker)
if "function resolveSnapshotPool(id)" not in main and "var structuredLevel=state&&state.level&&state.level.number;" not in main:
    raise RuntimeError("Visual state synchronization contract missing an authoritative Snapshot Level picker")
for retired in (
    "if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer'",
    "if(f.jane&&f.jane.present===true)return 'jane_the_killer'",
    "return normalizeEntityKey(f.entityEncounterKey)",
):
    if retired in main:
        raise RuntimeError("Historical/stale Entity state still drives Snapshot overlay: " + retired)

MAIN.write_text(main, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
old_load = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    if (repository.exists()) return repository.load()
    val migrated = GameStateCodec.decode(legacy)
    repository.save(migrated)
    return migrated
  }
'''
new_load = '''  private fun normalizeVisualPresence(state: GameState): GameState {
    if (CombatRuntime.active(state) != null) return state
    val rawFlags = state.world["flagsJson"] ?: return state
    val flags = runCatching { JSONObject(rawFlags) }.getOrNull() ?: return state
    if (flags.optString("entityEncounterKey", "").isBlank()) return state
    flags.put("entityEncounterKey", "")
    return state.copy(world = state.world + ("flagsJson" to flags.toString()))
  }

  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else GameStateCodec.decode(legacy)
    val normalized = normalizeVisualPresence(loaded)
    if (!existed || normalized != loaded) repository.save(normalized)
    return normalized
  }
'''
if new_load not in facade:
    facade = replace_once(facade, old_load, new_load, "persistent stale Entity visual migration")

# Patch processCombat within its own method boundary. Later runtime patches legitimately add HP/state
# synchronization between TimeEngine and repository.save(), so matching the whole historical method
# tail is intentionally avoided.
method_start = facade.find("  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {\n")
if method_start < 0:
    raise RuntimeError("processCombat method missing for targeted Entity cleanup")
method_end = facade.find("\n  fun ", method_start + 1)
if method_end < 0:
    method_end = facade.find("\n  private fun ", method_start + 1)
if method_end < 0:
    raise RuntimeError("processCombat method end missing for targeted Entity cleanup")
method = facade[method_start:method_end]

resolution_anchor = "    var resolution = CombatRuntime.resolve(current, actionKind, action)\n"
resolution_with_key = "    val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()\n    var resolution = CombatRuntime.resolve(current, actionKind, action)\n"
if resolution_with_key not in method:
    method = replace_once(method, resolution_anchor, resolution_with_key, "capture resolved Entity identity")

persistent_cleanup = '''    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = next.world["flagsJson"]?.let { JSONObject(it) }
        ?: legacy.optJSONObject("flags")?.let { JSONObject(it.toString()) }
        ?: JSONObject()
      flags.put("entityEncounterKey", "")
      when (resolvedEntityKey) {
        "jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)
        "jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)
      }
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
'''
if persistent_cleanup not in method:
    save_anchor = "    repository.save(next)\n"
    if method.count(save_anchor) != 1:
        raise RuntimeError(f"processCombat repository.save anchor expected 1, found {method.count(save_anchor)}")
    method = method.replace(save_anchor, persistent_cleanup + save_anchor, 1)

facade = facade[:method_start] + method + facade[method_end:]

for marker in (
    "private fun normalizeVisualPresence(state: GameState): GameState",
    'if (CombatRuntime.active(state) != null) return state',
    'if (!existed || normalized != loaded) repository.save(normalized)',
    'val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()',
    'when (resolvedEntityKey)',
    '"jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)',
    '"jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)',
    'next = next.copy(world = next.world + ("flagsJson" to flags.toString()))',
):
    if marker not in facade:
        raise RuntimeError("Combat visual cleanup persistence missing: " + marker)

unsafe_cleanup = '''      flags.optJSONObject("jeff")?.put("present", false)
      flags.optJSONObject("jane")?.put("present", false)
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
'''
if unsafe_cleanup in facade:
    raise RuntimeError("Unscoped Jeff/Jane cleanup survived visual-state hardening")

FACADE.write_text(facade, encoding="utf-8")
print("Visual state sync V3 applied: authoritative Level/area projection, stale-save migration, and method-scoped Entity cleanup.")
