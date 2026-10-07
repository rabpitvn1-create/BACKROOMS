"""Install after the release patch chain: all combat writes use the imported 1.1.93a engine."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError(f"Combat 93 patch expected one anchor: {old[:80]!r}")
    return text.replace(old, new, 1)

def method(text, name, replacement):
    start = text.index(f"  fun {name}(")
    end = text.index("\n  }", start) + len("\n  }")
    return text[:start] + replacement + text[end:]

facade = FACADE.read_text()
if "COMBAT_93_BRIDGE_V1" not in facade:
    facade = method(facade, "prepareCombatDice", '''  // COMBAT_93_BRIDGE_V1: the engine's automatically prepared hand belongs to one actor.
  @Synchronized fun prepareCombatDice(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return response(Combat93Runtime.active(current), syncLegacy(legacy, current, false),
      if (Combat93Runtime.active(current)) null else "combat_inactive", "combat_dice_prepared")
  }''')
    for name, call, signature in (
        ("setCombatDiceHold", "hold(current, dieIndex, held, expectedCombatRevision(legacy))", ", dieIndex: Int, held: Boolean"),
        ("rerollCombatDice", "roll(current, expectedCombatRevision(legacy))", ""),
        ("finishCombatDice", "finish(current, expectedCombatRevision(legacy))", ""),
    ):
        facade = method(facade, name, f'''  @Synchronized fun {name}(legacyStateJson: String{signature}): String {{
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {{
      val next = Combat93Runtime.{call}
      repository.save(next)
      response(true, syncLegacy(legacy, next, false), null, "combat_dice_updated")
    }} catch (error: Exception) {{
      response(false, syncLegacy(legacy, current, false), error.message ?: "combat_dice_rejected", "combat_dice_rejected")
    }}
  }}''')
    facade = method(facade, "startCombatState", '''  @Synchronized fun startCombatState(legacyStateJson: String, entityKey: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val next = Combat93Runtime.start(current, listOf(entityKey), legacy.optInt("turn", 1), Combat93Runtime.stageIndex(current))
    repository.save(next)
    return syncLegacy(legacy, next, false).toString()
  }''')
    facade = method(facade, "processCombat", '''  @Synchronized fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    if (!Combat93Runtime.active(current)) {
      if (legacy.optJSONObject("combat")?.optBoolean("active") == true && Combat93Runtime.toJson(current) != null)
        return response(true, syncLegacy(legacy, current, false), "combat_revision_mismatch", "combat_stale_request")
      return response(false, syncLegacy(legacy, current, false), null, "combat_inactive")
    }
    if (legacy.optJSONObject("combat")?.has("revision") != true) {
      legacy.put("combat", JSONObject().put("revision", Combat93Runtime.revision(current)))
    }
    val resolution = Combat93Runtime.resolve(current, expectedCombatRevision(legacy))
    if (!resolution.handled) return response(true, syncLegacy(legacy, current, false),
      "combat_dice_required", "combat_dice_required", "Hãy hoàn tất hand hiện tại trước khi giải quyết combat.")
    var next = resolution.state
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT93:${Combat93Runtime.toJson(current)?.optString("encounterId")}:${Combat93Runtime.revision(current)}",
      turnId = null, actorId = KAI_ID, source = CommandSource.SYSTEM, minutes = 1, reason = "combat_action"
    ))
    if (time.applied) next = time.state
    repository.save(next)
    val output = syncLegacy(legacy, next, false)
    val resolvedCombat = Combat93Runtime.toJson(next)!!
    output.put("turn", resolvedCombat.getInt("explorationTurn"))
    val feedback = resolvedCombat.optJSONArray("feedbackEvents")
    if (feedback != null && feedback.length() > 0) {
      output.put("combatFeedback", JSONObject()
        .put("id", resolvedCombat.getString("encounterId") + ":" +
          resolvedCombat.optInt("resolvedRound", resolvedCombat.optInt("round", 1)))
        .put("encounterId", resolvedCombat.getString("encounterId"))
        .put("events", JSONArray(feedback.toString())))
    }
    val log = output.optJSONArray("log") ?: JSONArray().also { output.put("log", it) }
    log.put(JSONObject().put("role", "gm").put("text", resolution.reply))
    return response(true, output, null, "combat_resolved", resolution.reply)
  }

  private fun expectedCombatRevision(legacy: JSONObject): Long =
    legacy.optJSONObject("combat")?.optLong("revision", -1L) ?: -1L

  @Synchronized fun setCombatTarget(legacyStateJson: String, entityIndex: Int): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = Combat93Runtime.target(current, entityIndex, expectedCombatRevision(legacy))
      repository.save(next)
      response(true, syncLegacy(legacy, next, false), null, "combat_target_updated")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, false), error.message ?: "combat_target_rejected", "combat_target_rejected")
    }
  }''')
    facade = facade.replace("CombatRuntime.active(state) != null", "Combat93Runtime.active(state)")
    facade = facade.replace("CombatRuntime.active(current) != null", "Combat93Runtime.active(current)")
    facade = replace_once(facade, "    val normalized = normalizeVisualPresence(loaded)",
        "    val migrated = Combat93Runtime.migrate(loaded, legacy.optInt(\"turn\", 1))\n    val recovered = Combat93Runtime.recoverCompanions(migrated, legacy.optInt(\"turn\", 1))\n    val normalized = normalizeVisualPresence(recovered)")
    facade = replace_once(facade, '''    CombatRuntime.toJson(state)?.let { combat ->
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      output.put("combat", combat)
    } ?: output.remove("combat")''', '''    Combat93Runtime.toJson(state)?.let { combat ->
      if (combat.optBoolean("active")) output.put("turn", combat.getInt("explorationTurn"))
      output.put("combat", combat)
    } ?: output.remove("combat")''')
    # All fallback entrypoints must also refuse mutations while an actor's hand is pending.
    facade = replace_once(facade, "    val state = loadOrMigrate(legacy)\n    if (blocksTextItemAction(action))",
        '''    val state = loadOrMigrate(legacy)
    if (Combat93Runtime.active(state)) return response(true, syncLegacy(legacy, state, false),
      "combat_active", "combat_active", "Hãy hoàn tất hand combat hiện tại.")
    if (blocksTextItemAction(action))''')
    facade = replace_once(facade, "    val core = loadOrMigrate(before)\n    if (blocksTextItemAction(action))",
        '''    val core = loadOrMigrate(before)
    if (Combat93Runtime.active(core)) return response(false, syncLegacy(before, core, false), "combat_active", "combat_active")
    if (blocksTextItemAction(action))''')
    FACADE.write_text(facade)

java = MAIN.read_text()
if "COMBAT_93_POST_COMMIT_START" not in java:
    java = replace_once(java, '    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);\n', '')
    java = replace_once(java, '''          state.put("log", log);
          emit("backroomTurn", state.toString());''', '''          state.put("log", log);
          // COMBAT_93_POST_COMMIT_START: commit the exploration delta before locking actor hands.
          JSONObject entityRoll = rolls.optJSONObject("entityEncounter");
          JSONObject bossRoll = rolls.optJSONObject("diepMinhEncounter");
          if (!meta && ((entityRoll != null && entityRoll.optBoolean("success")) ||
              (bossRoll != null && bossRoll.optBoolean("success")))) {
            JSONObject encounterFlags = state.optJSONObject("flags");
            String encounterKey = encounterFlags == null ? "" : encounterFlags.optString("entityEncounterKey", "");
            if (com.rabpit.backroom.core.CombatChoiceEngine.isKnownEntity(encounterKey))
              state = new JSONObject(requireGameCore().startCombatState(state.toString(), encounterKey));
          }
          emit("backroomTurn", state.toString());''')
if "public void combatTarget(" not in java:
    java = replace_once(java, "    @JavascriptInterface public void combatDiceRoll(String stateJson) {", '''    @JavascriptInterface public void combatTarget(String stateJson, int entityIndex) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().setCombatTarget(stateJson, entityIndex)));
    }

    @JavascriptInterface public void combatDiceRoll(String stateJson) {''')
MAIN.write_text(java)

assert "CombatRuntime.resolve(" not in facade
assert "PokerDiceCore.finish(current)" not in facade
assert "Combat93Runtime.resolve(current, expectedCombatRevision(legacy))" in facade
print("Combat 1.1.93a engine connected to authoritative save, dice bridge and actor turns.")
