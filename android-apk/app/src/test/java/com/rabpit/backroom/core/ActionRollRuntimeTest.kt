package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ActionRollRuntimeTest {
  private fun started(sessionId: String = "TURN_9:EXPLORE:42", turnId: String = "TURN_9"): GameState {
    val started = ActionRuntime.start(
      state = GameState.initial(),
      sessionId = sessionId,
      turnId = turnId,
      actorId = KAI_ID,
      kind = ActionKind.EXPLORE,
      input = "khám phá"
    )
    assertTrue(started.applied)
    return started.state
  }

  @Test fun lockedRollIsStableAcrossRetriesAndSaveRoundTrip() {
    val state = started()
    val seeded = ActionRollRuntime.ensureSeed(state, "TURN_9:EXPLORE:42", seedOverride = 123456789L)
    assertTrue(seeded.applied)

    val first = ActionRollRuntime.lockedRoll(seeded.state, "TURN_9:EXPLORE:42", "entityEncounter", 10_000)
    val retry = ActionRollRuntime.lockedRoll(seeded.state, "TURN_9:EXPLORE:42", "entityEncounter", 10_000)
    assertEquals(first, retry)

    val restored = GameStateCodec.decode(GameStateCodec.encode(seeded.state))
    val afterRestart = ActionRollRuntime.lockedRoll(restored, "TURN_9:EXPLORE:42", "entityEncounter", 10_000)
    assertEquals(first, afterRestart)
  }

  @Test fun existingSeedCannotBeReplacedByRetry() {
    val state = started()
    val first = ActionRollRuntime.ensureSeed(state, "TURN_9:EXPLORE:42", seedOverride = 11L)
    val second = ActionRollRuntime.ensureSeed(first.state, "TURN_9:EXPLORE:42", seedOverride = 99L)

    assertTrue(first.applied)
    assertTrue(second.duplicate)
    assertFalse(second.applied)
    assertEquals("11", second.state.metadata["actionRuntime.rollSeed"])
    assertEquals(
      ActionRollRuntime.lockedRoll(first.state, "TURN_9:EXPLORE:42", "loot", 100),
      ActionRollRuntime.lockedRoll(second.state, "TURN_9:EXPLORE:42", "loot", 100)
    )
  }

  @Test fun labelsProduceIndependentLockedChannelsAndStayInBounds() {
    val seeded = ActionRollRuntime.ensureSeed(started(), "TURN_9:EXPLORE:42", seedOverride = 7L).state
    val labels = listOf("hazard", "entityEncounter", "loot", "roamingEntityKey")
    val rolls = labels.associateWith { ActionRollRuntime.lockedRoll(seeded, "TURN_9:EXPLORE:42", it, 10_000) }

    assertTrue(rolls.values.all { it in 1..10_000 })
    assertEquals(labels.size, rolls.values.toSet().size)
  }

  @Test fun completingActionRemovesTransientRollSeed() {
    val seeded = ActionRollRuntime.ensureSeed(started(), "TURN_9:EXPLORE:42", seedOverride = 5L).state
    assertTrue(seeded.metadata.containsKey("actionRuntime.rollSeed"))

    val completed = ActionRuntime.complete(seeded, "TURN_9:EXPLORE:42")

    assertTrue(completed.applied)
    assertFalse(completed.state.metadata.containsKey("actionRuntime.rollSeed"))
  }

  @Test fun wrongSessionCannotReadOrReplaceSeed() {
    val seeded = ActionRollRuntime.ensureSeed(started(), "TURN_9:EXPLORE:42", seedOverride = 5L).state
    val rejected = ActionRollRuntime.ensureSeed(seeded, "other-session", seedOverride = 9L)

    assertFalse(rejected.applied)
    assertEquals("action_session_mismatch", rejected.error)
    try {
      ActionRollRuntime.lockedRoll(seeded, "other-session", "loot", 100)
      fail("wrong session must not read the locked stream")
    } catch (expected: IllegalStateException) {
      assertEquals("action_session_mismatch", expected.message)
    }
  }

  @Test fun abandonedPendingTurnCanRetryButIsNotMarkedCompleted() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_12", "khám phá")
    val abandoned = TurnCoordinator.abandon(created.state, "TURN_12", "pipeline_error")

    assertNull(abandoned.error)
    assertNull(abandoned.state.turn.pending)
    assertFalse("TURN_12" in abandoned.state.turn.completedTurnIds)
    assertTrue(abandoned.state.metadata["lastAbandonedTurn"]?.startsWith("TURN_12:") == true)

    val retry = TurnCoordinator.createPending(abandoned.state, "TURN_12", "khám phá")
    assertNull(retry.error)
    assertEquals("TURN_12", retry.state.turn.pending?.turnId)
  }
}
