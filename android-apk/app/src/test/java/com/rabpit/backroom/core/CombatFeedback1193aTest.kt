package com.rabpit.backroom.core

import org.json.JSONArray
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
    val finalHp = result.second.state.characters[KAI_ID]!!.vitalState.currentHp
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
    val narrative = JSONArray().put(JSONObject().put("role", "gm").put("text", "GM narrative remains separate"))
    val legacy = JSONObject()
      .put("turn", 1)
      .put("log", narrative)
      .put("combatFeedback", JSONObject().put("id", "forged"))
    val output = JSONObject(facade.processCombat(legacy.toString(), "EXECUTE", action)).getJSONObject("state")
    val packet = output.getJSONObject("combatFeedback")
    assertEquals("${active.encounterId}:1", packet.getString("id"))
    assertTrue(packet.getString("summary").isNotBlank())
    assertTrue(packet.getJSONArray("events").length() > 0)
    val log = output.getJSONArray("log")
    assertEquals(1, log.length())
    assertEquals("GM narrative remains separate", log.getJSONObject(0).getString("text"))
    val refreshed = JSONObject(facade.startCombatState(output.toString(), "hound"))
    assertFalse(refreshed.has("combatFeedback"))
  }
}
