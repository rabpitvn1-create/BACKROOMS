package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.TalkExecutor.TapeEntry

/**
 * A2b native MOVE / SEARCH / INSPECT executors (issue #513).
 *
 * Timing contract: MOVE -> EXPLORE 10 minutes, SEARCH -> NORMAL 5 minutes,
 * INSPECT -> QUICK 5 minutes.
 *
 * Authority rules:
 * - MOVE: target must be native-accessible/present/reachable. The transition
 *   itself follows the effective EXIT_STREAK_V1 authority (50/50 roll per turn,
 *   transition on a five-win streak) with native roll order/eligibility. No
 *   v6 restoration, no search bonus, no model-chosen transition. The player
 *   always travels WITH Cao Minh — no independent companion route. Scene and
 *   exposure update only from the approved native transition, never from prose.
 * - SEARCH: native presence/consciousness required; emits findings observation
 *   shell with bound parameters. No bonus mechanics.
 * - INSPECT: target present/reachable required; NEVER picks up items (no
 *   inventory mutation — pickup is a separate typed action).
 * - Failed attempts stay their own intent (never rewritten as NONE).
 * - These isolated bundles are not connected to the atomic Core commit. The
 *   native adapter must bind the captured roll, scene, and finding evidence;
 *   caller-supplied facts do not establish native provenance.
 *
 * Pure Kotlin: no Android, no I/O, no provider, no RNG (roll outcomes arrive as
 * native facts computed from the locked RNG upstream).
 */
internal object MoveSearchInspectExecutor {

  // ---- MOVE ----

  /** Native movement facts. rollWon/streakWins come from EXIT_STREAK_V1 natively. */
  data class MoveNativeFacts(
    val actorId: String,
    val fromSceneId: String,
    val toSceneId: String,
    val targetAccessible: Boolean,
    val targetReachable: Boolean,
    val actorPresent: Boolean,
    /** Native 50/50 roll outcome for this turn (from locked RNG). */
    val rollWon: Boolean,
    /** Current consecutive wins (native streak state). */
    val streakWins: Int,
    val nativeAction: String = "",
    val combatTurn: Boolean = true
  )

  data class MoveEvent(
    val eventId: String,
    val turnId: String,
    val actorId: String,
    val fromSceneId: String,
    val toSceneId: String?,
    val transitioned: Boolean,
    val streakAfter: Int
  )

  data class MoveObservation(
    val observationId: String,
    val turnId: String,
    val actorId: String,
    val fromSceneId: String,
    val toSceneId: String?
  )

  data class MoveBundle(val event: MoveEvent, val observation: MoveObservation, val tape: TapeEntry)

  sealed class MoveResult {
    data class Moved(val bundle: MoveBundle) : MoveResult()
    /** No transition (lost roll / ineligible): still a MOVE attempt, never NONE. */
    data class Stayed(val reason: String) : MoveResult()
  }

  fun executeMove(
    decided: Decided, facts: MoveNativeFacts,
    turnId: String, observationId: String, tapeSequence: Long
  ): MoveResult {
    if (decided.intent != Intent.MOVE) return MoveResult.Stayed("intent_not_move")
    if (decided.binding.actorId != facts.actorId) return MoveResult.Stayed("actor_mismatch")
    if (facts.streakWins !in 0..4) return MoveResult.Stayed("streak_invalid")
    if (decided.targetId != facts.toSceneId) return MoveResult.Stayed("target_mismatch")
    if (!facts.actorPresent) return MoveResult.Stayed("actor_absent")
    if (!facts.targetAccessible) return MoveResult.Stayed("target_inaccessible")
    if (!facts.targetReachable) return MoveResult.Stayed("target_unreachable")
    val route = com.rabpit.backroom.core.progression.FeaturedJourneyRoutes.next(facts.fromSceneId)
      ?: return MoveResult.Stayed("route_unapproved")
    if (route.targetStopKey != facts.toSceneId) return MoveResult.Stayed("route_unapproved")
    // Replay the captured native roll through the authoritative rule; no new RNG draw.
    val outcome = com.rabpit.backroom.core.ExitStreakEngine.advance(
      facts.streakWins, facts.nativeAction, facts.combatTurn) { if (facts.rollWon) 0 else 1 }
    if (!outcome.accepted || !outcome.evaluated) return MoveResult.Stayed("exit_ineligible")
    val transitioned = outcome.completed
    val finalStreak = if (transitioned) 0 else outcome.streak
    val eventId = "move-" + CompanionDigests.sha256(
      listOf(turnId, facts.actorId, facts.fromSceneId, facts.toSceneId,
        transitioned.toString()).joinToString("|")).take(16)
    val event = MoveEvent(eventId, turnId, facts.actorId,
      facts.fromSceneId, if (transitioned) facts.toSceneId else null,
      transitioned, finalStreak)
    val observation = MoveObservation(observationId, turnId, facts.actorId,
      facts.fromSceneId, if (transitioned) facts.toSceneId else null)
    val tape = TapeEntry(tapeSequence, turnId, facts.actorId,
      actionType = "EXPLORE", durationMinutes = 10, eventId = eventId)
    return MoveResult.Moved(MoveBundle(event, observation, tape))
  }

  // ---- SEARCH ----

  data class SenseNativeFacts(
    val actorId: String,
    val sceneId: String,
    val targetId: String?,
    val actorConscious: Boolean,
    val actorPresent: Boolean,
    val targetPresent: Boolean,
    val targetReachable: Boolean
  )

  data class SearchEvent(
    val eventId: String, val turnId: String, val actorId: String,
    val sceneId: String, val mode: String
  )

  data class SearchObservation(
    val observationId: String, val turnId: String, val actorId: String,
    val sceneId: String, val mode: String
  )

  data class SearchBundle(val event: SearchEvent, val observation: SearchObservation, val tape: TapeEntry)

  sealed class SearchResult {
    data class Searched(val bundle: SearchBundle) : SearchResult()
    data class NotSearched(val reason: String) : SearchResult()
  }

  fun executeSearch(
    decided: Decided, facts: SenseNativeFacts,
    turnId: String, observationId: String, tapeSequence: Long
  ): SearchResult {
    if (decided.intent != Intent.SEARCH) return SearchResult.NotSearched("intent_not_search")
    if (decided.binding.actorId != facts.actorId) return SearchResult.NotSearched("actor_mismatch")
    if (decided.targetId != facts.targetId) return SearchResult.NotSearched("target_mismatch")
    if (!facts.actorPresent) return SearchResult.NotSearched("actor_absent")
    if (!facts.actorConscious) return SearchResult.NotSearched("actor_unconscious")
    val eventId = "search-" + CompanionDigests.sha256(
      listOf(turnId, facts.actorId, facts.sceneId, "NORMAL").joinToString("|")).take(16)
    val event = SearchEvent(eventId, turnId, facts.actorId, facts.sceneId, mode = "NORMAL")
    val observation = SearchObservation(observationId, turnId, facts.actorId, facts.sceneId, "NORMAL")
    val tape = TapeEntry(tapeSequence, turnId, facts.actorId,
      actionType = "SEARCH", durationMinutes = 5, eventId = eventId)
    return SearchResult.Searched(SearchBundle(event, observation, tape))
  }

  // ---- INSPECT ----

  data class InspectEvent(
    val eventId: String, val turnId: String, val actorId: String,
    val targetId: String, val mode: String
  )

  data class InspectObservation(
    val observationId: String, val turnId: String, val actorId: String,
    val targetId: String, val mode: String
  )

  data class InspectBundle(val event: InspectEvent, val observation: InspectObservation, val tape: TapeEntry)

  sealed class InspectResult {
    data class Inspected(val bundle: InspectBundle) : InspectResult()
    data class NotInspected(val reason: String) : InspectResult()
  }

  fun executeInspect(
    decided: Decided, facts: SenseNativeFacts,
    turnId: String, observationId: String, tapeSequence: Long
  ): InspectResult {
    if (decided.intent != Intent.INSPECT) return InspectResult.NotInspected("intent_not_inspect")
    val target = decided.targetId
    if (decided.binding.actorId != facts.actorId) return InspectResult.NotInspected("actor_mismatch")
    if (target != facts.targetId) return InspectResult.NotInspected("target_mismatch")
    if (target.isNullOrBlank()) return InspectResult.NotInspected("target_ambiguous")
    if (!facts.actorPresent) return InspectResult.NotInspected("actor_absent")
    if (!facts.actorConscious) return InspectResult.NotInspected("actor_unconscious")
    if (!facts.targetPresent) return InspectResult.NotInspected("target_absent")
    if (!facts.targetReachable) return InspectResult.NotInspected("target_unreachable")
    // INSPECT never picks up: no inventory mutation exists on this path by construction.
    val eventId = "inspect-" + CompanionDigests.sha256(
      listOf(turnId, facts.actorId, target, "QUICK").joinToString("|")).take(16)
    val event = InspectEvent(eventId, turnId, facts.actorId, target, mode = "QUICK")
    val observation = InspectObservation(observationId, turnId, facts.actorId, target, "QUICK")
    val tape = TapeEntry(tapeSequence, turnId, facts.actorId,
      actionType = "INSPECT", durationMinutes = 5, eventId = eventId)
    return InspectResult.Inspected(InspectBundle(event, observation, tape))
  }
}
