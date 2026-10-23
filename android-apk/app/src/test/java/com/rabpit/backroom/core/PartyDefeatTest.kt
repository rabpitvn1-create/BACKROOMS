package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PartyDefeatTest {
  private fun party(leaderHp: Int, luciaHp: Int): GameState {
    val base = GameState.initial()
    val lucia = CharacterState(id = LUCIA_ID, name = "Lucia")
    val roster = base.copy(characters = base.characters + (LUCIA_ID to lucia),
      party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID)))
    return CharacterStatEngine.setCurrentHp(
      CharacterStatEngine.setCurrentHp(roster, CAO_MINH_ID, leaderHp), LUCIA_ID, luciaHp)
  }

  @Test fun oneFallenMemberKeepsItsSlotAndDoesNotEndTheSurvivorsCombat() {
    val started = CombatRuntime.start(party(0, 30), "LuckyBear")
    val result = CombatRuntime.resolve(started, "SEARCH", "quan sát")
    assertTrue(result.handled)
    assertNotNull(CombatRuntime.active(result.state))
    assertFalse(PartyCombatHealth.isDefeated(result.state))
    assertEquals(CharacterPresence.DEAD, result.state.characters.getValue(CAO_MINH_ID).presence)
    assertEquals(CharacterCondition.DEAD, result.state.characters.getValue(CAO_MINH_ID).vitalState.condition)
    assertEquals(listOf(LUCIA_ID), PartyCombatHealth.actingMembers(result.state))
    assertEquals(PartyCombatHealth.maxHp(started), PartyCombatHealth.maxHp(result.state))
    assertEquals(listOf(CAO_MINH_ID, LUCIA_ID), result.state.party.memberIds)
  }

  @Test fun lethalEnemyRoundEndsCombatAndDeathPersistsThroughCurrentSaveRoundTrip() {
    var state = CombatRuntime.start(party(1, 1), "diep_minh")
    var ending = ""
    for (i in 0 until 60) {
      val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
      state = result.state
      if (PartyCombatHealth.isDefeated(state)) {
        ending = result.completionNarrative
        break
      }
    }
    assertTrue("Lethal damage must finish the encounter", PartyCombatHealth.isDefeated(state))
    assertNull(CombatRuntime.active(state))
    assertNull(CombatRuntime.pending(state))
    assertTrue(ending.contains("GAME OVER"))
    for (id in state.party.memberIds) {
      val character = state.characters.getValue(id)
      assertEquals(0, character.vitalState.currentHp)
      assertEquals(CharacterPresence.DEAD, character.presence)
      assertEquals(CharacterCondition.DEAD, character.vitalState.condition)
    }
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertTrue(PartyCombatHealth.isDefeated(loaded))
    assertTrue(CoreClientProjection.project(JSONObject().put("gameOver", false), loaded).getBoolean("gameOver"))
    assertFalse(CombatRuntime.resolve(loaded, "EXECUTE", "đánh").handled)
    assertThrows(IllegalArgumentException::class.java) { CombatRuntime.start(loaded, "hound") }
    assertTrue(MiniMapRuntime.blocked(loaded))
  }

  @Test fun separatedOrUnrecruitedCharactersCannotPreventGameOver() {
    val base = party(0, 30)
    val separated = base.copy(characters = base.characters +
      (LUCIA_ID to base.characters.getValue(LUCIA_ID).copy(presence = CharacterPresence.SEPARATED)))
    assertTrue(PartyCombatHealth.isDefeated(separated))
    val absent = base.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID)))
    assertTrue(PartyCombatHealth.isDefeated(absent))
    assertFalse(CoreClientProjection.project(JSONObject().put("gameOver", true), GameState.initial()).getBoolean("gameOver"))
  }

  private class MemorySave(var state: GameState) : SaveRepository {
    var checkpoint: String? = null
    override fun save(state: GameState) { this.state = state }
    override fun load(): GameState = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
      checkpoint = GameCheckpointCodec.encode(state, presentation)
    }
    override fun loadCheckpoint(): GameCheckpoint? = checkpoint?.let(GameCheckpointCodec::decode)
  }

  @Test fun terminalFacadeRejectsActionsButAllowsReadingAndLoadingALivingCheckpoint() {
    val repo = MemorySave(CoreClientProjection.initialize(party(30, 30)))
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    constructor.isAccessible = true
    val facade = constructor.newInstance(repo, NoOpGamePipelineLogger)
    assertTrue(JSONObject(facade.saveManualCheckpoint("{\"turn\":1}")).getBoolean("ok"))
    repo.state = PartyCombatHealth.markFallen(party(0, 0))
    assertTrue(JSONObject(facade.currentClientState("{}")).getBoolean("gameOver"))
    val before = repo.state
    assertThrows(IllegalArgumentException::class.java) { facade.processRule("{}", "MadGod") }
    assertThrows(IllegalArgumentException::class.java) { facade.prepareCombatDice("{}", "đánh") }
    assertThrows(IllegalArgumentException::class.java) { facade.processItemAction("{}", "{}") }
    assertThrows(IllegalArgumentException::class.java) { facade.beginSceneAction("{\"actionId\":\"after-death\"}") }
    assertEquals(before, repo.state)
    val restored = JSONObject(facade.loadManualCheckpoint())
    assertTrue(restored.getBoolean("ok"))
    assertFalse(restored.getJSONObject("state").getBoolean("gameOver"))
    assertFalse(PartyCombatHealth.isDefeated(repo.state))
  }
}
