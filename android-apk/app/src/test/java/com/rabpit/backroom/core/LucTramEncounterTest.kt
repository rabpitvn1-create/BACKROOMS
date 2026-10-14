package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LucTramEncounterTest {
  private fun atLevel(level: Int): GameState = CoreClientProjection.initialize(GameState.initial()).let {
    it.copy(world = it.world + ("levelJson" to LevelRouteRegistry.node(level.toString())!!.asJson().toString()))
  }
  private fun meeting() = JSONObject().put("id", "lucTram").put("turn", 3).put("kind", "FOLLOWER")
    .put("characterId", LUC_TRAM_ID).put("action", "Bước ra").put("event", "Gặp Cao Minh").put("outcome", "Đã gặp")
  @Test fun registryIsScheduledAndDoesNotSeedUnconfirmedGearOrParty() {
    val state = GameState.initial()
    assertEquals("MILESTONE", state.characters.getValue(LUC_TRAM_ID).metadata["encounterPolicy"])
    assertFalse(state.characters.getValue(LUC_TRAM_ID).metadata.containsKey("encounterChance"))
    assertFalse(LUC_TRAM_ID in state.party.memberIds)
    assertFalse(LucTramCanon.hasConfirmedSword(state))
  }
  @Test fun unsupportedLevelIsRejectedBeforePlanAcceptanceRatherThanSkippingTheEvent() {
    for (level in 0..3) assertThrows(Exception::class.java) { TestCheckpointQuota.withPlan(atLevel(level), listOf(meeting())) }
  }
  @Test fun acceptedLevelFourEncounterOccursOnTurnThreeWithoutRecruitment() {
    var state = TestCheckpointQuota.withPlan(atLevel(4), listOf(meeting()))
    repeat(2) { state = CoreClientProjection.advance(TestMilestoneTurn.dispatch(state)) }
    assertEquals(listOf("lucTram"), StoryMilestoneRuntime.due(state).map { it.id })
    val committed = TestMilestoneTurn.dispatch(state)
    assertEquals("3", committed.characters.getValue(LUC_TRAM_ID).metadata["encounterTurn"])
    assertFalse(LUC_TRAM_ID in committed.party.memberIds)
    assertEquals(committed, TestMilestoneTurn.dispatch(committed))
  }
}
