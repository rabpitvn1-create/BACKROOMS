from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# VISUAL_STATE_SYNC_FINAL_V1
# Snapshot state is a projection of authoritative gameplay state. Free-text story fields and
# historical Entity flags must never keep an obsolete visual scene alive.
main = MAIN.read_text(encoding="utf-8")

# Level fallback art must prefer the structured Level selected by the gameplay reducer. Text parsing
# remains only as an old-save fallback when state.level is missing.
old_level_picker = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;"
new_level_picker = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var structuredLevel=state&&state.level&&state.level.number;var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
if new_level_picker not in main:
    main = replace_once(main, old_level_picker, new_level_picker, "authoritative Snapshot Level picker")

# Current Entity rendering follows active CombatRuntime first, then the explicit current-presence key.
# Jeff/Jane historical flags are deliberately excluded: they describe continuity, not current pixels.
old_active_entity = "function activeEntityKey(){var f=state&&state.flags||{};var direct=normalizeEntityKey(f.entityEncounterKey);if(direct)return direct;if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer';if(f.jane&&f.jane.present===true)return 'jane_the_killer';return '';}"
new_active_entity = "function activeEntityKey(){var c=state&&state.combat;if(c&&c.active===true){var combatKey=normalizeEntityKey(c.entityKey);if(combatKey)return combatKey;}var f=state&&state.flags||{};return normalizeEntityKey(f.entityEncounterKey);}"
if new_active_entity not in main:
    main = replace_once(main, old_active_entity, new_active_entity, "current Entity visual source")

# Reconcile all Level-bearing fields before they are committed into Game State Core. This fixes the
# previous ordering bug where a model could describe Level N in location/title, the legacy layer would
# recognize it after the Core commit, and the next turn would resurrect the older Core Level.
helper = r'''  private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls) throws Exception {
    int oldLevel = currentLevel(before);
    int structuredLevel = currentLevel(candidateState);
    int describedLevel = mentionedLevel(candidateState);
    // Structured gameplay state wins. Free-text location/title only repairs old or incomplete candidates.
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
    } else {
      // Keep the structured Level canonical even if free-text area/location changes within that Level.
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
    "var structuredLevel=state&&state.level&&state.level.number;",
    "function activeEntityKey(){var c=state&&state.combat;",
    "private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls)",
    "structuredLevel != oldLevel ? structuredLevel",
    "reconcileVisualWorldState(before, candidateState, rolls);",
):
    if marker not in main:
        raise RuntimeError("Visual state synchronization contract missing: " + marker)
for retired in (
    "if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer'",
    "if(f.jane&&f.jane.present===true)return 'jane_the_killer'",
):
    if retired in main:
        raise RuntimeError("Historical Entity presence still drives Snapshot overlay: " + retired)

MAIN.write_text(main, encoding="utf-8")

# Combat resolution previously cleared entityEncounterKey only in the WebView response after saving
# Core state. The following turn could therefore project stale flagsJson back into the UI and resurrect
# the dead Entity overlay. Persist the clear before repository.save().
facade = FACADE.read_text(encoding="utf-8")
old_combat_tail = '''    if (time.applied) next = time.state
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    appendLog(output, action, resolution.reply)
'''
new_combat_tail = '''    if (time.applied) next = time.state
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = next.world["flagsJson"]?.let { JSONObject(it) }
        ?: legacy.optJSONObject("flags")?.let { JSONObject(it.toString()) }
        ?: JSONObject()
      flags.put("entityEncounterKey", "")
      flags.optJSONObject("jeff")?.put("present", false)
      flags.optJSONObject("jane")?.put("present", false)
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    appendLog(output, action, resolution.reply)
'''
if new_combat_tail not in facade:
    facade = replace_once(facade, old_combat_tail, new_combat_tail, "persist Entity visual clear in Core")

for marker in (
    'flags.put("entityEncounterKey", "")',
    'next.world["flagsJson"]?.let { JSONObject(it) }',
    'next = next.copy(world = next.world + ("flagsJson" to flags.toString()))',
    'flags.optJSONObject("jeff")?.put("present", false)',
    'flags.optJSONObject("jane")?.put("present", false)',
):
    if marker not in facade:
        raise RuntimeError("Combat visual cleanup persistence missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
print("Visual state sync final applied: authoritative Level/area projection and persistent Entity cleanup.")
