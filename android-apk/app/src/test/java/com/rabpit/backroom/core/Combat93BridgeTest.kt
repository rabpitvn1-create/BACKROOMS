package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Combat93BridgeTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load(): GameState = state
    override fun exists(): Boolean = true
    override fun clear() { state = GameState.initial() }
  }

  private fun facade(save: MemorySave): GameCoreFacade {
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(save, NoOpGamePipelineLogger)
  }

  private fun initial(): GameState = GameState.initial().let { state ->
    val boosted = state.copy(party = state.party.copy(memberIds = listOf(KAI_ID, IRIS_ID)),
      characters = state.characters.mapValues { (_, c) -> c.copy(statProfile = c.statProfile.copy(vit = 999)) },
      world = mapOf("location" to "Level 0", "levelJson" to "{\"number\":0}", "flagsJson" to "{}"))
    CharacterProgressionCore.normalize(boosted).let { normalized ->
      normalized.copy(characters = normalized.characters.mapValues { (id, c) ->
        c.copy(vitalState = c.vitalState.copy(currentHp = CharacterStatEngine.effective(normalized, id).maxHp))
      })
    }
  }

  private fun legacy(state: GameState): JSONObject = JSONObject().put("turn", 7)
    .put("player", JSONObject().put("name", "Cao Minh")).put("log", JSONArray())
    .put("combat", Combat93Runtime.toJson(state))

  private fun hand(save: MemorySave, values: List<Int>) {
    val boundary = JSONObject(save.state.metadata.getValue("combat93.state"))
    boundary.getJSONObject("combat").getJSONObject("diceState").put("values", JSONArray(values)).put("hasRolled", true)
    save.state = save.state.copy(metadata = save.state.metadata + ("combat93.state" to boundary.toString()))
  }

  @Test fun bridgeFreezesExplorerTurnRotatesActorAndRejectsRepeatedHand() {
    val save = MemorySave(initial())
    val core = facade(save)
    var ui = JSONObject(core.startCombatState(legacy(save.state).toString(), "diep_minh"))
    hand(save, listOf(1, 2, 3, 4, 6))
    ui = JSONObject(core.finishCombatDice(ui.toString())).getJSONObject("state")
    val oldRequest = ui.toString()
    ui = JSONObject(core.processCombat(oldRequest, "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)).getJSONObject("state")
    assertEquals(7, ui.getInt("turn"))
    assertEquals(1, ui.getJSONObject("combat").getInt("actorIndex"))
    assertEquals(0, ui.getJSONObject("combat").getInt("resolvedActorIndex"))
    assertEquals(1L, save.state.time.elapsedSubjectiveMinutes)
    val committed = save.state
    val repeated = JSONObject(core.processCombat(oldRequest, "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION))
    assertTrue(repeated.getBoolean("handled"))
    assertEquals(committed, save.state)
    assertEquals(7, repeated.getJSONObject("state").getInt("turn"))
  }

  @Test fun oldEncounterMigrationRetainsHpAndDiceBudget() {
    var state = CombatRuntime.start(initial(), "diep_minh")
    val old = CombatRuntime.active(state)!!
    state = PokerDiceCore.prepare(state, PokerDiceCore.DIRECT_COMBAT_ACTION, old.encounterId)
    state = PokerDiceCore.setHold(state, 0, false)
    state = PokerDiceCore.reroll(state)
    val savedDice = PokerDiceCore.diceJson(state)!!
    val save = MemorySave(state)
    val response = JSONObject(facade(save).prepareCombatDice("{\"turn\":7}", PokerDiceCore.DIRECT_COMBAT_ACTION))
    val combat = response.getJSONObject("state").getJSONObject("combat")
    assertEquals(old.entityHp, combat.getInt("entityHp"))
    assertEquals(old.entityMaxHp, combat.getInt("entityMaxHp"))
    assertEquals(savedDice.getJSONArray("values").toString(), combat.getJSONObject("diceState").getJSONArray("values").toString())
    assertEquals(savedDice.getInt("rerollsUsed"), combat.getJSONObject("diceState").getInt("rerollsUsed"))
    assertNull(CombatRuntime.active(save.state))
    assertNull(PokerDiceCore.diceJson(save.state))
  }

  @Test fun finalizedVictoryAdvancesOnceAndStaleSubmitCannotFallThroughToExploration() {
    val save = MemorySave(initial())
    val core = facade(save)
    var ui = JSONObject(core.startCombatState(legacy(save.state).toString(), "hound"))
    hand(save, listOf(2, 2, 2, 2, 2))
    ui = JSONObject(core.finishCombatDice(ui.toString())).getJSONObject("state")
    val stale = ui.toString()
    ui = JSONObject(core.processCombat(stale, "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)).getJSONObject("state")
    assertEquals(8, ui.getInt("turn"))
    assertEquals("victory", ui.getJSONObject("combat").getString("outcome"))
    assertTrue(ui.getJSONObject("combat").getJSONArray("feedbackEvents").length() > 0)
    assertEquals(2, PokerDiceCore.coreCount(save.state))
    val committed = save.state
    assertTrue(JSONObject(core.processCombat(stale, "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)).getBoolean("handled"))
    assertEquals(committed, save.state)
    assertFalse(JSONObject(core.processCombat(ui.toString(), "EXPLORE", "Khám phá")).getBoolean("handled"))
  }

  @Test fun pendingHandBlocksAiItemAndUpgradeMutations() {
    val save = MemorySave(Combat93Runtime.start(initial(), listOf("diep_minh"), 7, 0))
    val core = facade(save)
    val ui = legacy(save.state).toString()
    val before = save.state
    assertEquals("combat_active", JSONObject(core.processValidatedCandidate(ui, ui, "tìm kiếm")).getString("error"))
    assertEquals("combat_active", JSONObject(core.processItemAction(ui, "{}")).getString("error"))
    assertFalse(JSONObject(core.processCoreUpgrade(ui, KAI_ID, "STR")).getBoolean("handled"))
    assertEquals(before, save.state)
  }

  @Test fun companionRecoversOnTheSourceTenExplorerTurnDeadlineOnly() {
    var state = CharacterStatEngine.setCurrentHp(initial(), IRIS_ID, 0)
    val iris = state.characters.getValue(IRIS_ID)
    state = state.copy(characters = state.characters + (IRIS_ID to iris.copy(metadata = iris.metadata + ("combat93.reviveAtTurn" to "17"))))
    assertEquals(0, Combat93Runtime.recoverCompanions(state, 16).characters.getValue(IRIS_ID).vitalState.currentHp)
    val recovered = Combat93Runtime.recoverCompanions(GameStateCodec.decode(GameStateCodec.encode(state)), 17)
    assertEquals(1, recovered.characters.getValue(IRIS_ID).vitalState.currentHp)
    assertFalse(recovered.characters.getValue(IRIS_ID).metadata.containsKey("combat93.reviveAtTurn"))
    val active = Combat93Runtime.start(state, listOf("diep_minh"), 7, 0)
    assertEquals(0, Combat93Runtime.recoverCompanions(active, 99).characters.getValue(IRIS_ID).vitalState.currentHp)
  }
}
