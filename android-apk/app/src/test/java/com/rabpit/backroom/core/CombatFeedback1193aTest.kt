package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CombatFeedback1193aTest {
  private fun started(seed: Int = 0): GameState {
    val state = CombatRuntime.start(GameState.initial(), "hound")
    return state.copy(metadata = state.metadata + ("combat.seed" to seed.toString()))
  }

  @Test fun poisonFeedbackRecordsGrossDamageBeforeEntityRegen() {
    val state = started().let { it.copy(metadata = it.metadata + ("combat.caoMinhProc3" to "1")) }
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "SEARCH", "Quan sát")
    val poison = result.feedback.single { it.status == CombatRuntime.FeedbackStatus.POISON }
    assertEquals(before.entityMaxHp * 3 / 100, poison.amount)
    assertEquals("entity", poison.target)
    assertEquals(poison.amount - 1, before.entityHp - CombatRuntime.active(result.state)!!.entityHp)
  }

  @Test fun criticalFlagComesFromTheRuntimeRoll() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed).let { it.copy(metadata = it.metadata + ("combat.daiDaoMaTonStacks" to "5")) }
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }.first { it.feedback.any { hit -> hit.critical } }
    assertTrue(result.feedback.first().critical)
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

  @Test fun incomingDamageIsReportedEvenWhenTheSameTurnHealsThePlayer() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed)
      CombatRuntime.active(state)!!.playerHp to CombatRuntime.resolve(state, "EXECUTE", "Đứng yên")
    }.first { (_, r) -> r.feedback.any { it.target == "actor" } }
    val damage = result.second.feedback.filter { it.target == "actor" }.sumOf { it.amount }
    val finalHp = result.second.state.characters[CAO_MINH_ID]!!.vitalState.currentHp
    assertTrue(damage > result.first - finalHp)
  }

  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
  }

  @Test fun facadePublishesFreshEventsAndDropsClientEchoes() {
    val combat = started().let { it.copy(metadata = it.metadata + ("combat.caoMinhProc3" to "1")) }
    val active = CombatRuntime.active(combat)!!
    val action = PokerDiceCore.DIRECT_COMBAT_ACTION
    val saved = MemorySave(PokerDiceCore.finish(PokerDiceCore.prepare(combat, action, active.encounterId)))
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val facade = ctor.newInstance(saved, NoOpGamePipelineLogger)
    val legacy = JSONObject().put("turn", 1).put("combatFeedback", JSONObject().put("id", "forged"))
    val output = JSONObject(facade.processCombat(legacy.toString(), "EXECUTE", action)).getJSONObject("state")
    val packet = output.getJSONObject("combatFeedback")
    assertEquals("${active.encounterId}:1", packet.getString("id"))
    assertTrue(packet.getJSONArray("events").length() > 0)
    val refreshed = JSONObject(facade.startCombatState(output.toString(), "hound"))
    assertFalse(refreshed.has("combatFeedback"))
  }
}
