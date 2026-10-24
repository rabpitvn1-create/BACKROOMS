package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Checks the live single-hit CombatRuntime contract. The previous 1.1.93a test
 * referenced poison procs and bridge APIs removed from this runtime generation.
 * Feedback below is always based on actual HP mutations, never guessed from log text.
 */
class CombatFeedback1193aTest {
  private fun started(seed: Int = 0): GameState {
    val state = CombatRuntime.start(GameState.initial(), "hound")
    return state.copy(metadata = state.metadata + ("combat.seed" to seed.toString()))
  }

  @Test fun feedbackDoesNotInventPoisonForRetiredProcMetadata() {
    val results = (0..200).map { seed ->
      val state = started(seed).copy(metadata = started(seed).metadata + ("combat.caoMinhProc3" to "1"))
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }
    assertTrue(results.any { it.feedback.any { event -> event.target == "entity" } })
    assertTrue(results.all { it.feedback.all { event -> event.amount > 0 && event.toJson().getString("text") == "-${event.amount} HP" } })
    assertTrue(results.all { it.feedback.none { event -> event.toJson().has("status") } })
  }

  @Test fun criticalFlagComesFromTheRuntimeRoll() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed).copy(metadata = started(seed).metadata + ("combat.daiDaoMaTonStacks" to "5"))
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }.first { it.feedback.any { event -> event.critical } }
    assertTrue(result.feedback.any { it.target == "entity" && it.critical })
    assertTrue(result.feedback.all { it.amount > 0 })
  }

  // Đại Ma Đầu runs in parallel with Đại Đạo Ma Tôn inside the single passive funnel
  // (CharacterStatCore.effective) that every combat number in CombatRuntime reads:
  // basic damage (effective.str), skill damage (effective.skl), crit chance
  // (effective.criticalChancePercent), incoming damage (effective.def) and
  // evasion/resistances (effective evasion/res fields). Base 100 is used because the
  // two rounding conventions for additive passives (one combined percent vs one
  // rounding per passive) agree exactly there: 100 + 10 (Đại Đạo) + 50 (Đại Ma Đầu) = 160.
  @Test fun daiMaDauAppliesInParallelWithDaiDaoInEffectiveStats() {
    val initial = GameState.initial()
    val cao = initial.characters.getValue(CAO_MINH_ID)
    val boosted = initial.copy(characters = initial.characters + (CAO_MINH_ID to cao.copy(
      statProfile = cao.statProfile.copy(str = 100, def = 100, skl = 100, vit = 100)
    )))
    val effective = CharacterStatCore.effective(boosted, CAO_MINH_ID)
    assertEquals(160, effective.str)
    assertEquals(160, effective.def)
    assertEquals(160, effective.skl)
    assertEquals(160, effective.vit)
    // Passives are Cao Minh-only: a companion's effective stats stay at base.
    val allyProfile = CharacterStatProfiles.forId(LUC_TRAM_ID)
    val allyEffective = CharacterStatCore.effective(initial, LUC_TRAM_ID)
    assertEquals(allyProfile.str, allyEffective.str)
    assertEquals(allyProfile.def, allyEffective.def)
    assertEquals(allyProfile.skl, allyEffective.skl)
    assertEquals(allyProfile.vit, allyEffective.vit)
  }

  @Test fun lethalFeedbackIsClippedToActualHpAndSurvivesCombatCleanup() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed).let { it.copy(metadata = it.metadata + ("combat.entityHp" to "1")) }
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }.first { it.entityDestroyed }
    assertNull(CombatRuntime.active(result.state))
    assertEquals(1, result.feedback.filter { it.target == "entity" }.sumOf { it.amount })
    assertFalse(result.state.metadata.keys.any { it.contains("feedback", ignoreCase = true) })
  }

  @Test fun incomingDamageFeedbackEqualsRealHpDecrease() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed)
      state.characters.getValue(CAO_MINH_ID).vitalState.currentHp to
        CombatRuntime.resolve(state, "EXECUTE", "Đứng yên")
    }.first { (_, resolution) -> resolution.feedback.any { it.target == "actor" } }
    val total = result.second.feedback.filter { it.target == "actor" }.sumOf { it.amount }
    val finalHp = result.second.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
    assertEquals(result.first - finalHp, total)
    assertEquals("entity", result.second.feedback.single { it.target == "actor" }.phase)
  }

  @Test fun feedbackIsTransientAndDoesNotBecomeSaveMetadata() {
    val result = (0..200).asSequence().map { seed ->
      CombatRuntime.resolve(started(seed), "EXECUTE", "Tấn công")
    }.first { it.feedback.isNotEmpty() }
    assertTrue(result.state.metadata.keys.none { it.contains("feedback", ignoreCase = true) })
    assertTrue(result.feedback.all { it.toJson().optString("actorId") == CAO_MINH_ID })
  }
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load(): GameState = state
    override fun exists(): Boolean = true
    override fun clear() { state = GameState.initial() }
  }

  private fun facade(save: MemorySave): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java, LevelTravelEngine::class.java)
    ctor.isAccessible = true
    val policyFile = listOf("src/main/assets/knowledge/level_travel_policy.json", "app/src/main/assets/knowledge/level_travel_policy.json", "android-apk/app/src/main/assets/knowledge/level_travel_policy.json")
      .map { java.io.File(it) }.first { it.isFile }
    return ctor.newInstance(save, NoOpGamePipelineLogger, LevelTravelEngine(LevelTravelPolicy(org.json.JSONObject(policyFile.readText()))))
  }

  @Test fun combatBridgeRequiresLockedDiceAndPreservesOtherNarrativeState() {
    val save = MemorySave(GameState.initial())
    val core = facade(save)
    val legacy = org.json.JSONObject()
      .put("turn", 3)
      .put("inventory", org.json.JSONArray().put(org.json.JSONObject().put("name", "Vật phẩm đã xác nhận")))
      .put("party", org.json.JSONArray().put(org.json.JSONObject().put("id", LUC_TRAM_ID).put("name", "Lục Trầm")))
    val started = org.json.JSONObject(core.startCombatState(legacy.toString(), "hound"))
    assertTrue(started.toString(), started.getBoolean("handled"))
    assertTrue(started.getJSONObject("state").getJSONObject("combat").getBoolean("active"))
    assertNotNull(CombatRuntime.active(save.state))

    val startedState = started.getJSONObject("state")
    val action = PokerDiceCore.DIRECT_COMBAT_ACTION
    val denied = org.json.JSONObject(core.processCombat(startedState.toString(), "EXECUTE", action))
    assertFalse(denied.getBoolean("handled"))
    assertEquals("combat_dice_not_finalized", denied.getString("error"))
    assertEquals(3, denied.getJSONObject("state").getInt("turn"))

    val prepared = org.json.JSONObject(core.prepareCombatDice(startedState.toString(), action))
    assertTrue(prepared.toString(), prepared.getBoolean("handled"))
    assertNotNull(prepared.getJSONObject("state").getJSONObject("combat").optJSONObject("diceState"))
    val preparedState = prepared.getJSONObject("state")
    assertEquals(legacy.getJSONArray("inventory").toString(), preparedState.getJSONArray("inventory").toString())
    assertEquals(legacy.getJSONArray("party").toString(), preparedState.getJSONArray("party").toString())
    val finalDice = org.json.JSONObject(core.finishCombatDice(preparedState.toString()))
    assertTrue(finalDice.toString(), finalDice.getBoolean("handled"))
    assertTrue(finalDice.getJSONObject("state").getJSONObject("combat")
      .getJSONObject("diceState").getBoolean("finalized"))

    val finalizedState = finalDice.getJSONObject("state")
    assertEquals(legacy.getJSONArray("inventory").toString(), finalizedState.getJSONArray("inventory").toString())
    assertEquals(legacy.getJSONArray("party").toString(), finalizedState.getJSONArray("party").toString())
    val finished = org.json.JSONObject(core.processCombat(finalizedState.toString(), "EXECUTE", action))
    assertTrue(finished.toString(), finished.getBoolean("handled"))
    val out = finished.getJSONObject("state")
    assertEquals(4, out.getInt("turn"))
    assertEquals(legacy.getJSONArray("inventory").toString(), out.getJSONArray("inventory").toString())
    assertEquals(legacy.getJSONArray("party").toString(), out.getJSONArray("party").toString())
    val feedback = out.getJSONObject("combatFeedback")
    assertTrue(feedback.getString("encounterId").isNotBlank())
    assertFalse(save.state.metadata.keys.any { it.contains("feedback", ignoreCase = true) })

    val replay = org.json.JSONObject(core.processCombat(legacy.toString(), "EXECUTE", action))
    assertFalse(replay.getBoolean("handled"))
  }

  @Test fun repeatedEncountersNeverReuseTheSameFeedbackIdentity() {
    val firstState = CombatRuntime.start(GameState.initial(), "hound")
    val first = CombatRuntime.active(firstState)!!.encounterId
    val cleared = CombatRuntime.clear(firstState)
    assertNull(CombatRuntime.active(cleared))
    val again = CombatRuntime.start(cleared, "hound")
    val second = CombatRuntime.active(again)!!.encounterId
    assertNotEquals(first, second)
    assertEquals("2", again.metadata["encounter.serial"])
  }

}
