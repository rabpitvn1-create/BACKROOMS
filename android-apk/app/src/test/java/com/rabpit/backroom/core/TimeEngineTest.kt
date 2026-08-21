package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class TimeEngineTest {
  @Test fun reducerAdvancesSubjectiveTimeAndRemembersLastAdvance() {
    val state = GameState.initial()
    val command = TimeAdvanceCommand(
      commandId = "TURN_1:TIME:0",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 90,
      reason = "travel"
    )

    val result = StateReducer.execute(state, command)

    assertTrue(result.applied)
    assertEquals(90L, result.state.time.elapsedSubjectiveMinutes)
    assertEquals(90, result.state.time.lastAdvanceMinutes)
    assertEquals("travel", result.state.time.lastAdvanceReason)
    assertTrue(command.commandId in result.state.turn.executedCommandIds)
    assertEquals(listOf("time_advanced"), result.events)
  }

  @Test fun knownPhysiologyCountersAdvanceWithSubjectiveTime() {
    val kai = GameState.initial().characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(
        minutesSinceFood = 120L,
        minutesSinceWater = 45L,
        minutesAwake = 600L,
        painState = "mild",
        infectionState = "none",
        thermalState = "cold"
      )
    )
    val state = GameState.initial().copy(characters = mapOf(KAI_ID to kai))
    val result = TimeEngine.execute(state, TimeAdvanceCommand("t-phys", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 30, reason = "travel"))

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(150L, physiology.minutesSinceFood)
    assertEquals(75L, physiology.minutesSinceWater)
    assertEquals(630L, physiology.minutesAwake)
    assertEquals("mild", physiology.painState)
    assertEquals("none", physiology.infectionState)
    assertEquals("cold", physiology.thermalState)
  }

  @Test fun unknownPhysiologyCountersRemainUnknown() {
    val state = GameState.initial()
    val result = TimeEngine.execute(state, TimeAdvanceCommand("t-unknown", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 30, reason = "search"))

    assertTrue(result.applied)
    assertEquals(PhysiologyState(), result.state.characters.getValue(KAI_ID).physiology)
  }

  @Test fun deadCharacterPhysiologyDoesNotAdvance() {
    val dead = CharacterState(
      id = "dead-survivor",
      name = "Dead Survivor",
      presence = CharacterPresence.DEAD,
      physiology = PhysiologyState(minutesSinceFood = 300L, minutesSinceWater = 90L, minutesAwake = 900L)
    )
    val state = GameState.initial().copy(characters = GameState.initial().characters + (dead.id to dead))
    val result = TimeEngine.execute(state, TimeAdvanceCommand("t-dead", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 60, reason = "wait"))

    assertTrue(result.applied)
    assertEquals(dead.physiology, result.state.characters.getValue(dead.id).physiology)
  }

  @Test fun physiologyOverflowRejectsWholeTimeAdvance() {
    val kai = GameState.initial().characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(minutesSinceFood = Long.MAX_VALUE - 5L, minutesSinceWater = 10L, minutesAwake = 20L)
    )
    val state = GameState.initial().copy(characters = mapOf(KAI_ID to kai), time = GameTimeState(elapsedSubjectiveMinutes = 100L))
    val result = TimeEngine.execute(state, TimeAdvanceCommand("t-overflow", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 10, reason = "wait"))

    assertFalse(result.applied)
    assertEquals("physiology_time_overflow", result.validation.reason)
    assertEquals(state, result.state)
  }

  @Test fun duplicateTimeCommandNeverAdvancesTwice() {
    val command = TimeAdvanceCommand(
      commandId = "TURN_1:TIME:0",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 15,
      reason = "search"
    )
    val first = StateReducer.execute(GameState.initial(), command)
    val second = StateReducer.execute(first.state, command)

    assertTrue(first.applied)
    assertTrue(second.duplicate)
    assertFalse(second.applied)
    assertEquals(15L, second.state.time.elapsedSubjectiveMinutes)
  }

  @Test fun timeEngineRejectsNonPositiveMinutesAndBlankReason() {
    val state = GameState.initial()
    val zero = TimeEngine.execute(state, TimeAdvanceCommand("t0", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 0, reason = "wait"))
    val blank = TimeEngine.execute(state, TimeAdvanceCommand("t1", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 5, reason = "   "))

    assertFalse(zero.applied)
    assertEquals("time_minutes_must_be_positive", zero.validation.reason)
    assertFalse(blank.applied)
    assertEquals("time_reason_required", blank.validation.reason)
    assertEquals(0L, state.time.elapsedSubjectiveMinutes)
  }
}
