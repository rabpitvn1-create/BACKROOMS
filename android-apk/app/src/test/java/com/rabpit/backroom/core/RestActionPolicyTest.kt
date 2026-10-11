package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RestActionPolicyTest {
  private fun partyState(): GameState {
    val base = GameState.initial()
    val tiredCaoMinh = base.characters.getValue(CAO_MINH_ID).copy(
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    val lucia = CharacterState(
      "lucia",
      "Lucia \"Lục\"",
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    return base.copy(
      characters = base.characters + (CAO_MINH_ID to tiredCaoMinh) + (lucia.id to lucia),
      party = PartyState(memberIds = listOf(CAO_MINH_ID, lucia.id))
    )
  }

  @Test fun ordinaryCaoMinhRestOnlyTargetsCaoMinh() {
    val state = partyState()
    assertEquals(listOf(CAO_MINH_ID), RestActionPolicy.targets(state, "Tôi chợp mắt một lúc"))
  }

  @Test fun explicitShiftRestTargetsActiveParty() {
    val state = partyState()
    assertEquals(listOf(CAO_MINH_ID, "lucia"), RestActionPolicy.targets(state, "Cả hai chia ca nghỉ ngơi và chợp mắt"))
  }

  @Test fun unrelatedWaitingDoesNotFakeSleepRecovery() {
    val state = partyState()
    assertTrue(RestActionPolicy.targets(state, "Đứng chờ và quan sát hành lang").isEmpty())
  }

  @Test fun coordinatorCommitsTimeThenCaoMinhSleepRecoveryAtomically() {
    val state = partyState()
    val pending = TurnCoordinator.createPending(state, "TURN_REST", "Tôi ngủ một tiếng").state
    val time = TimeAdvanceCommand(
      "TURN_REST:SYSTEM:TIME",
      "TURN_REST",
      CAO_MINH_ID,
      source = CommandSource.SYSTEM,
      minutes = 60,
      reason = "player_action"
    )

    val result = TurnCoordinator.commit(pending, listOf(time))

    assertNull(result.error)
    val caoMinh = result.state.characters.getValue(CAO_MINH_ID).physiology
    val lucia = result.state.characters.getValue("lucia").physiology
    assertEquals(0L, caoMinh.minutesAwake)
    assertEquals(1960L, lucia.minutesAwake)
    assertEquals(1060L, caoMinh.minutesSinceFood)
    assertEquals(1060L, caoMinh.minutesSinceWater)
    assertEquals(100, PhysiologyStatusPolicy.restPercent(caoMinh.minutesAwake))
    assertTrue("TURN_REST:SYSTEM:REST:0" in result.state.turn.executedCommandIds)
  }

  @Test fun coordinatorSharedShiftRestRecoversCaoMinhAndLucia() {
    val state = partyState()
    val pending = TurnCoordinator.createPending(state, "TURN_SHIFT", "Cả hai chia ca nghỉ ngơi và chợp mắt một tiếng").state
    val time = TimeAdvanceCommand(
      "TURN_SHIFT:SYSTEM:TIME",
      "TURN_SHIFT",
      CAO_MINH_ID,
      source = CommandSource.SYSTEM,
      minutes = 60,
      reason = "player_action"
    )

    val result = TurnCoordinator.commit(pending, listOf(time))

    assertNull(result.error)
    assertEquals(0L, result.state.characters.getValue(CAO_MINH_ID).physiology.minutesAwake)
    assertEquals(0L, result.state.characters.getValue("lucia").physiology.minutesAwake)
    assertTrue("TURN_SHIFT:SYSTEM:REST:0" in result.state.turn.executedCommandIds)
    assertTrue("TURN_SHIFT:SYSTEM:REST:1" in result.state.turn.executedCommandIds)
  }
}
