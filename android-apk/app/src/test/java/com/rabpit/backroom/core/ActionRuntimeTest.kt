package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ActionRuntimeTest {
  private fun stateAt(location: String = "Level 1 / Parking A"): GameState = GameState.initial().copy(
    world = mapOf("location" to location, "worldRevision" to "r1")
  )

  @Test fun searchStartsAtCurrentLocationWithNormalDepth() {
    val result = ActionRuntime.start(
      stateAt(),
      sessionId = "S1",
      turnId = "TURN_1",
      actorId = KAI_ID,
      kind = ActionKind.SEARCH,
      input = "Tìm kiếm khu vực hiện tại"
    )

    assertTrue(result.applied)
    val session = requireNotNull(result.session)
    assertEquals(ActionKind.SEARCH, session.kind)
    assertEquals(SearchDepth.NORMAL, session.searchDepth)
    assertEquals("Level 1 / Parking A", session.locationKey)
    assertEquals(0, session.elapsedMinutes)
    assertEquals(ActionPhase.ACTIVE, session.phase)
  }

  @Test fun partialAdvanceUsesTimeEngineAndPhysiologyCounters() {
    val started = ActionRuntime.start(
      stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.SEARCH, "search", plannedMinutes = 20
    ).state

    val result = ActionRuntime.advance(started, "S1", "cp-1", 7)

    assertTrue(result.applied)
    assertEquals(7L, result.state.time.elapsedSubjectiveMinutes)
    assertEquals(7, result.state.time.lastAdvanceMinutes)
    assertEquals("action_search", result.state.time.lastAdvanceReason)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(7L, physiology.minutesSinceFood)
    assertEquals(7L, physiology.minutesSinceWater)
    assertEquals(7L, physiology.minutesAwake)
    assertEquals(7, requireNotNull(result.session).elapsedMinutes)
  }

  @Test fun duplicateCheckpointNeverAdvancesTimeTwice() {
    val started = ActionRuntime.start(stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.EXPLORE, "explore").state
    val first = ActionRuntime.advance(started, "S1", "cp-1", 5)
    val second = ActionRuntime.advance(first.state, "S1", "cp-1", 5)

    assertTrue(first.applied)
    assertTrue(second.duplicate)
    assertFalse(second.applied)
    assertEquals(5L, second.state.time.elapsedSubjectiveMinutes)
    assertEquals(5, requireNotNull(second.session).elapsedMinutes)
  }

  @Test fun checkpointCannotExceedPlannedDuration() {
    val started = ActionRuntime.start(
      stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.SEARCH, "search", plannedMinutes = 10
    ).state
    val first = ActionRuntime.advance(started, "S1", "cp-1", 6)
    val rejected = ActionRuntime.advance(first.state, "S1", "cp-2", 5)

    assertTrue(first.applied)
    assertFalse(rejected.applied)
    assertEquals("action_checkpoint_exceeds_plan", rejected.error)
    assertEquals(6L, rejected.state.time.elapsedSubjectiveMinutes)
  }

  @Test fun searchCoveragePersistsAndWorldRevisionInvalidatesOldCoverage() {
    val started = ActionRuntime.start(stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.SEARCH, "search").state
    val first = ActionRuntime.markSearchCoverage(started, "S1", setOf("accessible_surface", "containers"))
    val second = ActionRuntime.markSearchCoverage(first.state, "S1", setOf("concealed_spaces"))

    assertTrue(second.applied)
    assertEquals(
      setOf("accessible_surface", "containers", "concealed_spaces"),
      ActionRuntime.searchCoverage(second.state, "Level 1 / Parking A", "r1")
    )
    assertTrue(ActionRuntime.searchCoverage(second.state, "Level 1 / Parking A", "r2").isEmpty())
  }

  @Test fun interruptionKeepsPartialTimeAndCoverageThenReturnsControl() {
    val started = ActionRuntime.start(
      stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.SEARCH, "search", plannedMinutes = 30
    ).state
    val progressed = ActionRuntime.advance(started, "S1", "cp-1", 8).state
    val covered = ActionRuntime.markSearchCoverage(progressed, "S1", setOf("containers")).state

    val interrupted = ActionRuntime.interrupt(covered, "S1", "entity_contact")

    assertTrue(interrupted.applied)
    assertNull(ActionRuntime.activeSession(interrupted.state))
    assertEquals(8L, interrupted.state.time.elapsedSubjectiveMinutes)
    assertEquals(setOf("containers"), ActionRuntime.searchCoverage(interrupted.state, "Level 1 / Parking A", "r1"))
    assertEquals("INTERRUPTED", interrupted.state.metadata["lastAction.phase"])
    assertEquals("8", interrupted.state.metadata["lastAction.elapsedMinutes"])
    assertEquals("entity_contact", interrupted.state.metadata["lastAction.reason"])
  }

  @Test fun exploreRejectsSearchDepthAndDoesNotClaimSearchSemantics() {
    val rejected = ActionRuntime.start(
      stateAt(),
      sessionId = "S1",
      turnId = "TURN_1",
      actorId = KAI_ID,
      kind = ActionKind.EXPLORE,
      input = "Khám phá phía trước",
      searchDepth = SearchDepth.THOROUGH
    )
    assertFalse(rejected.applied)
    assertEquals("search_depth_non_search_action", rejected.error)

    val accepted = ActionRuntime.start(
      stateAt(), "S2", "TURN_1", KAI_ID, ActionKind.EXPLORE, "Khám phá phía trước"
    )
    assertTrue(accepted.applied)
    assertNull(requireNotNull(accepted.session).searchDepth)
  }

  @Test fun secondActionSessionCannotStartWhileOneIsActive() {
    val first = ActionRuntime.start(stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.SEARCH, "search")
    val second = ActionRuntime.start(first.state, "S2", "TURN_1", KAI_ID, ActionKind.EXPLORE, "explore")

    assertTrue(first.applied)
    assertFalse(second.applied)
    assertEquals("action_session_already_active", second.error)
  }

  @Test fun terminalLocalRejectionCanReleaseSessionAndAllowNextTurn() {
    val started = ActionRuntime.start(
      stateAt(), "S1", "TURN_1", KAI_ID, ActionKind.EXECUTE, "Dùng vật phẩm"
    ).state
    val pending = TurnCoordinator.createPending(started, "TURN_1", "Dùng vật phẩm")
    assertNull(pending.error)

    val rejected = TurnCoordinator.reject(pending.state, "item_not_owned")
    assertNull(rejected.state.turn.pending)
    assertTrue("TURN_1" in rejected.state.turn.completedTurnIds)
    assertNotNull(ActionRuntime.activeSession(rejected.state))

    val released = ActionRuntime.interrupt(rejected.state, "S1", "local_terminal")
    assertTrue(released.applied)
    assertNull(ActionRuntime.activeSession(released.state))

    val next = ActionRuntime.start(
      released.state, "S2", "TURN_2", KAI_ID, ActionKind.EXECUTE, "Hành động kế tiếp"
    )
    assertTrue(next.applied)
  }

  @Test fun actionTimeAdvancesKnownPartyPhysiologyWithoutMovingDeadCharacters() {
    val follower = CharacterState(
      id = "follower",
      name = "Follower",
      physiology = PhysiologyState(minutesSinceFood = 10L, minutesSinceWater = 20L, minutesAwake = 30L)
    )
    val dead = CharacterState(
      id = "dead",
      name = "Dead",
      presence = CharacterPresence.DEAD,
      physiology = PhysiologyState(minutesSinceFood = 100L, minutesSinceWater = 100L, minutesAwake = 100L)
    )
    val state = stateAt().copy(
      characters = stateAt().characters + (follower.id to follower) + (dead.id to dead),
      party = PartyState(memberIds = listOf(KAI_ID, follower.id))
    )
    val started = ActionRuntime.start(state, "S1", "TURN_1", KAI_ID, ActionKind.EXPLORE, "explore").state
    val result = ActionRuntime.advance(started, "S1", "cp-1", 12)

    assertTrue(result.applied)
    val advancedFollower = result.state.characters.getValue("follower").physiology
    assertEquals(22L, advancedFollower.minutesSinceFood)
    assertEquals(32L, advancedFollower.minutesSinceWater)
    assertEquals(42L, advancedFollower.minutesAwake)
    assertEquals(dead.physiology, result.state.characters.getValue("dead").physiology)
  }
}