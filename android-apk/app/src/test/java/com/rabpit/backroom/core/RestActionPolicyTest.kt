package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RestActionPolicyTest {
  private fun partyState(): GameState {
    val base = GameState.initial()
    val tiredKai = base.characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    val lucia = CharacterState(
      "lucia",
      "Lucia \"Lục\"",
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    return base.copy(
      characters = base.characters + (KAI_ID to tiredKai) + (lucia.id to lucia),
      party = PartyState(memberIds = listOf(KAI_ID, lucia.id))
    )
  }

  @Test fun ordinaryKaiRestOnlyTargetsKai() {
    val state = partyState()
    assertEquals(listOf(KAI_ID), RestActionPolicy.targets(state, "Tôi chợp mắt một lúc"))
  }

  @Test fun explicitShiftRestTargetsActiveParty() {
    val state = partyState()
    assertEquals(listOf(KAI_ID, "lucia"), RestActionPolicy.targets(state, "Cả hai chia ca nghỉ ngơi và chợp mắt"))
  }

  @Test fun unrelatedWaitingDoesNotFakeSleepRecovery() {
    val state = partyState()
    assertTrue(RestActionPolicy.targets(state, "Đứng chờ và quan sát hành lang").isEmpty())
  }

  @Test fun coordinatorCommitsTimeThenKaiSleepRecoveryAtomically() {
    val state = partyState()
    val pending = TurnCoordinator.createPending(state, "TURN_REST", "Tôi ngủ một tiếng").state
    val time = TimeAdvanceCommand(
      "TURN_REST:SYSTEM:TIME",
      "TURN_REST",
      KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 60,
      reason = "player_action"
    )

    val result = TurnCoordinator.commit(pending, listOf(time))

    assertNull(result.error)
    val kai = result.state.characters.getValue(KAI_ID).physiology
    val lucia = result.state.characters.getValue("lucia").physiology
    assertEquals(0L, kai.minutesAwake)
    assertEquals(1960L, lucia.minutesAwake)
    assertEquals(1060L, kai.minutesSinceFood)
    assertEquals(1060L, kai.minutesSinceWater)
    assertEquals(100, PhysiologyStatusPolicy.restPercent(kai.minutesAwake))
    assertTrue("TURN_REST:SYSTEM:REST:0" in result.state.turn.executedCommandIds)
  }

  @Test fun coordinatorSharedShiftRestRecoversKaiAndLucia() {
    val state = partyState()
    val pending = TurnCoordinator.createPending(state, "TURN_SHIFT", "Cả hai chia ca nghỉ ngơi và chợp mắt một tiếng").state
    val time = TimeAdvanceCommand(
      "TURN_SHIFT:SYSTEM:TIME",
      "TURN_SHIFT",
      KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 60,
      reason = "player_action"
    )

    val result = TurnCoordinator.commit(pending, listOf(time))

    assertNull(result.error)
    assertEquals(0L, result.state.characters.getValue(KAI_ID).physiology.minutesAwake)
    assertEquals(0L, result.state.characters.getValue("lucia").physiology.minutesAwake)
    assertTrue("TURN_SHIFT:SYSTEM:REST:0" in result.state.turn.executedCommandIds)
    assertTrue("TURN_SHIFT:SYSTEM:REST:1" in result.state.turn.executedCommandIds)
  }
}
