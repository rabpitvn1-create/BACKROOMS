package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.MoveSearchInspectExecutor.InspectResult
import com.rabpit.backroom.core.companion.MoveSearchInspectExecutor.MoveNativeFacts
import com.rabpit.backroom.core.companion.MoveSearchInspectExecutor.MoveResult
import com.rabpit.backroom.core.companion.MoveSearchInspectExecutor.SearchResult
import com.rabpit.backroom.core.companion.MoveSearchInspectExecutor.SenseNativeFacts
import org.junit.Assert.*
import org.junit.Test

/**
 * A2b native MOVE/SEARCH/INSPECT tests (issue #513).
 *
 * Covers: timing contract (10/5/5), EXIT_STREAK_V1 streak semantics, native
 * preconditions, no-pickup on INSPECT, failed attempts staying their intent,
 * tape ordering.
 */
class MoveSearchInspectExecutorTest {
  private fun decided(intent: Intent, target: String?) = Decided(
    binding = DecisionPreflight.DecisionBinding(
      proposalDigest = "d".repeat(64), actorId = "luc_tram", slotId = "slot-1",
      slotRevision = 42, intent = intent, targetId = target, itemId = null,
      canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION),
    intent = intent, targetId = target, itemId = null,
    rngValue = 777L, providerCalls = 1)

  private fun moveFacts() = MoveNativeFacts(
    actorId = "luc_tram", fromSceneId = "node-7", toSceneId = "node-8",
    targetAccessible = true, targetReachable = true, actorPresent = true,
    rollWon = true, streakWins = 4)

  // ---- MOVE: EXIT_STREAK_V1 ----

  @Test fun move_fifthWin_transitions() {
    val r = MoveSearchInspectExecutor.executeMove(
      decided(Intent.MOVE, "node-8"), moveFacts(),
      "turn-9", "obs-1", 7L)
    assertTrue(r is MoveResult.Moved)
    val b = (r as MoveResult.Moved).bundle
    assertTrue(b.event.transitioned)
    assertEquals("node-8", b.event.toSceneId)
    assertEquals(0, b.event.streakAfter)       // streak resets on transition
    assertEquals(10, b.tape.durationMinutes)   // EXPLORE 10
    assertEquals("EXPLORE", b.tape.actionType)
  }

  @Test fun move_winBelowFive_staysWithStreak() {
    val r = MoveSearchInspectExecutor.executeMove(
      decided(Intent.MOVE, "node-8"), moveFacts().copy(streakWins = 2),
      "turn-9", "obs-1", 7L)
    val b = (r as MoveResult.Moved).bundle
    assertFalse(b.event.transitioned)
    assertNull(b.event.toSceneId)
    assertEquals(3, b.event.streakAfter)        // win increments, no transition yet
  }

  @Test fun move_lostRoll_resetsStreak() {
    val r = MoveSearchInspectExecutor.executeMove(
      decided(Intent.MOVE, "node-8"), moveFacts().copy(rollWon = false, streakWins = 4),
      "turn-9", "obs-1", 7L)
    val b = (r as MoveResult.Moved).bundle
    assertFalse(b.event.transitioned)
    assertEquals(0, b.event.streakAfter)        // loss resets
  }

  @Test fun move_inaccessibleTarget_staysMove() {
    val r = MoveSearchInspectExecutor.executeMove(
      decided(Intent.MOVE, "node-8"), moveFacts().copy(targetAccessible = false),
      "turn-9", "obs-1", 7L)
    assertTrue(r is MoveResult.Stayed)
    assertEquals("target_inaccessible", (r as MoveResult.Stayed).reason)
    // Still a MOVE attempt — never rewritten as NONE.
  }

  @Test fun move_wrongIntent() {
    val r = MoveSearchInspectExecutor.executeMove(
      decided(Intent.SEARCH, null), moveFacts(), "turn-9", "obs-1", 7L)
    assertEquals("intent_not_move", (r as MoveResult.Stayed).reason)
  }

  // ---- SEARCH ----

  private fun senseFacts() = SenseNativeFacts(
    actorId = "luc_tram", sceneId = "node-7", targetId = null,
    actorConscious = true, actorPresent = true,
    targetPresent = true, targetReachable = true)

  @Test fun search_normalFiveMinutes() {
    val r = MoveSearchInspectExecutor.executeSearch(
      decided(Intent.SEARCH, null), senseFacts(), "turn-9", "obs-2", 8L)
    assertTrue(r is SearchResult.Searched)
    val b = (r as SearchResult.Searched).bundle
    assertEquals("NORMAL", b.event.mode)
    assertEquals(5, b.tape.durationMinutes)
    assertEquals("SEARCH", b.tape.actionType)
  }

  @Test fun search_unconscious_notSearched() {
    val r = MoveSearchInspectExecutor.executeSearch(
      decided(Intent.SEARCH, null), senseFacts().copy(actorConscious = false),
      "turn-9", "obs-2", 8L)
    assertEquals("actor_unconscious", (r as SearchResult.NotSearched).reason)
  }

  // ---- INSPECT ----

  @Test fun inspect_quickFiveMinutes_noPickup() {
    val r = MoveSearchInspectExecutor.executeInspect(
      decided(Intent.INSPECT, "crate"), senseFacts().copy(targetId = "crate"),
      "turn-9", "obs-3", 9L)
    assertTrue(r is InspectResult.Inspected)
    val b = (r as InspectResult.Inspected).bundle
    assertEquals("QUICK", b.event.mode)
    assertEquals("crate", b.event.targetId)
    assertEquals(5, b.tape.durationMinutes)
    // No inventory mutation exists on the INSPECT path by construction:
    // InspectBundle carries no inventory delta type.
  }

  @Test fun inspect_unreachableTarget() {
    val r = MoveSearchInspectExecutor.executeInspect(
      decided(Intent.INSPECT, "crate"),
      senseFacts().copy(targetId = "crate", targetReachable = false),
      "turn-9", "obs-3", 9L)
    assertEquals("target_unreachable", (r as InspectResult.NotInspected).reason)
  }

  @Test fun inspect_ambiguousTarget() {
    val r = MoveSearchInspectExecutor.executeInspect(
      decided(Intent.INSPECT, null), senseFacts(), "turn-9", "obs-3", 9L)
    assertEquals("target_ambiguous", (r as InspectResult.NotInspected).reason)
  }

  @Test fun tape_orderedAcrossIntents() {
    val m = (MoveSearchInspectExecutor.executeMove(
      decided(Intent.MOVE, "node-8"), moveFacts(), "turn-9", "obs-1", 7L) as MoveResult.Moved).bundle.tape
    val s = (MoveSearchInspectExecutor.executeSearch(
      decided(Intent.SEARCH, null), senseFacts(), "turn-9", "obs-2", 8L) as SearchResult.Searched).bundle.tape
    assertTrue(s.sequence > m.sequence)
  }
}
