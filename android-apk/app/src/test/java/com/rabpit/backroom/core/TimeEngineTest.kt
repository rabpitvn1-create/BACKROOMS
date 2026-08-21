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
