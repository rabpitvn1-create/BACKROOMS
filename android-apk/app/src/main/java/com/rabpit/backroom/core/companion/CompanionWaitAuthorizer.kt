package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.Combat93Runtime
import com.rabpit.backroom.core.CombatRuntime
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.ExitStreakEngine
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * #485: native authorization for the **exit-only WAIT 30-minute fixture**.
 *
 * Preflight requires an exact Core snapshot and accepted native decision. That
 * snapshot, its prior streak and its approved route source are the only
 * authority. The caller MUST supply bytes obtained from CompanionSlotStore;
 * this class cannot prove database provenance from caller-provided bytes.
 *
 * Replay consumes the existing ordered tape WITHOUT an RNG callback. Its
 * verdict is for S1c.2 authorizer tests only; it is NOT a complete WAIT tape,
 * reservation, Core commit, UI response, or gameplay activation.
 */
object CompanionWaitAuthorizer {
  const val WAIT_POLICY = "companion-wait-v1"
  const val EVIDENCE_VERSION = "companion_wait_outcome.v1"

  /** All fields are untrusted assertions until [replay] compares them to Core. */
  data class Evidence(
    val version: String, val policy: String, val revision: Long,
    val snapshotDigest: String, val previousStreak: Int,
    val route: CompanionRollTape.Route, val tape: String,
    val success: Boolean, val nextStreak: Int, val completed: Boolean,
    val decisionDigest: String = ""
  )

  /** Constructed only after the Core snapshot and policy have been checked. */
  class Bound internal constructor(
    val sourceStop: String, val sourceLevel: Int, val previousStreak: Int,
    val snapshotDigest: String, val revision: Long, val input: String,
    val decisionDigest: String, val turnId: String
  )

  data class Gate(val bound: Bound? = null, val error: String? = null)
  data class Validated(
    val success: Boolean, val nextStreak: Int, val completed: Boolean,
    val route: CompanionRollTape.Route,
    val fullWaitTape: Boolean = false
  )
  data class Review(val validated: Validated? = null, val error: String? = null)

  /**
   * All failures occur before any native roll. No RNG API is even accepted here.
   * Core world is the source of the visit, scene, registered route and streak;
   * provider/UI strings may not specify an alternate source or previous value.
   */
  fun preflight(
    persistedSnapshot: ByteArray, state: GameState, turn: CompanionPendingTurn,
    slotId: String, requestId: String, expectedRevision: Long,
    pinnedPolicy: String, exactPlayerInput: String
  ): Gate {
    // Reject unknown implementation policy even if lock and caller agree.
    if (pinnedPolicy != WAIT_POLICY) return Gate(error = "policy_version_unsupported")
    val check = CompanionDecisionBinding.verifyWait(persistedSnapshot, state, turn,
      slotId, requestId, expectedRevision, pinnedPolicy, exactPlayerInput)
    if (!check.accepted) return Gate(error = check.error ?: "wait_binding_rejected")
    // WAIT is an ordinary exploration turn; never force combatTurn=false for a
    // persisted encounter. Check both native combat and its migration source.
    val combatActive = try {
      Combat93Runtime.active(state) || CombatRuntime.active(state) != null
    } catch (_: RuntimeException) {
      return Gate(error = "wait_combat_invalid")
    }
    if (combatActive) return Gate(error = "wait_combat_active")
    val source = state.world["journeyStopKey"] ?: return Gate(error = "wait_source_missing")
    val routes = FeaturedJourneyRoutes
    val level = routes.stopLevelNumber(source) ?: return Gate(error = "wait_source_unknown")
    val node = routes.nodeIdAt(source) ?: return Gate(error = "wait_source_node_missing")
    if (state.world["worldNodeId"] != node) return Gate(error = "wait_source_node_mismatch")
    val savedLevel = try {
      JSONObject(state.world["levelJson"] ?: return Gate(error = "wait_level_missing"))
    } catch (_: RuntimeException) {
      return Gate(error = "wait_level_invalid")
    }
    val number = savedLevel.opt("number")
    if (number !is Number ||
        number.toString().toBigDecimalOrNull()?.compareTo(level.toBigDecimal()) != 0 ||
        savedLevel.optString("stopKey") != source ||
        savedLevel.optString("nodeId") != node)
      return Gate(error = "wait_saved_source_mismatch")

    val flags = try {
      JSONObject(state.world["flagsJson"] ?: return Gate(error = "wait_flags_missing"))
    } catch (_: RuntimeException) {
      return Gate(error = "wait_flags_invalid")
    }
    val exploration = flags.optJSONObject("exploration")
      ?: return Gate(error = "wait_exploration_missing")
    if (exploration.optString("exitStreakNode") != source)
      return Gate(error = "wait_streak_source_mismatch")
    val raw = exploration.opt("exitStreak")
    if (raw !is Number || raw.toInt().toDouble() != raw.toDouble() ||
        raw.toInt() !in 0 until ExitStreakEngine.REQUIRED_WINS)
      return Gate(error = "wait_prior_streak_invalid")
    // Handover stores a digest of the *exact* stable persisted Core bytes,
    // rather than a string value supplied by the model.
    val hash = CompanionDigests.sha256(persistedSnapshot)
    return Gate(bound = Bound(source, level, raw.toInt(), hash,
      expectedRevision, exactPlayerInput, turn.decision.digest, turn.turnId))
  }

  /**
   * Replay-only comparison, no RNG callback or fallback path exists.
   * The native ExitStreakEngine decides whether five wins were reached.
   * FeaturedJourneyRoutes then decides whether a transition exists.
   * A valid graph edge alone never authorizes completion.
   */
  fun replay(bound: Bound, evidence: Evidence): Review {
    if (evidence.version != EVIDENCE_VERSION) return Review(error = "wait_evidence_version_unsupported")
    if (evidence.policy != WAIT_POLICY) return Review(error = "wait_evidence_policy_unsupported")
    if (evidence.revision != bound.revision) return Review(error = "wait_evidence_revision_mismatch")
    if (evidence.snapshotDigest != bound.snapshotDigest)
      return Review(error = "wait_evidence_snapshot_mismatch")
    if (evidence.decisionDigest != bound.decisionDigest)
      return Review(error = "wait_evidence_decision_mismatch")
    if (evidence.previousStreak != bound.previousStreak)
      return Review(error = "wait_evidence_streak_mismatch")
    if (evidence.route.sourceStop != bound.sourceStop ||
        evidence.route.sourceLevel != bound.sourceLevel)
      return Review(error = "wait_route_source_mismatch")

    return try {
      val tape = CompanionRollTape.decode(evidence.tape)
      if (tape.route != evidence.route) return Review(error = "wait_route_tape_mismatch")
      val playback = tape.replay()
      val outcome = ExitStreakEngine.advance(
        bound.previousStreak, bound.input, false
      ) { boundValue ->
        playback.next(CompanionRollTape.Purpose.EXIT_STREAK, boundValue)
      }
      playback.finish() // An unrecorded or extra WAIT-exit draw is always fatal.
      if (!outcome.accepted || !outcome.evaluated ||
          outcome.success == null) return Review(error = "wait_exit_not_evaluated")
      val nativeNext = FeaturedJourneyRoutes.next(bound.sourceStop)
      val completed = outcome.completed && nativeNext != null
      val expectedRoute = if (completed) {
        CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop,
          nativeNext!!.targetLevelNumber, nativeNext.targetStopKey, true)
      } else CompanionRollTape.Route(bound.sourceLevel, bound.sourceStop,
        bound.sourceLevel, bound.sourceStop, false)
      if (tape.route != expectedRoute) return Review(error = "wait_route_outcome_mismatch")
      // Fifth win resets the level streak whether a route exists or not.
      val expectedStreak = if (outcome.completed) 0 else outcome.streak
      if (evidence.success != outcome.success ||
          evidence.nextStreak != expectedStreak ||
          evidence.completed != completed)
        return Review(error = "wait_recorded_outcome_mismatch")
      Review(validated = Validated(outcome.success == true,
        expectedStreak, completed, expectedRoute))
    } catch (_: IllegalArgumentException) {
      Review(error = "wait_tape_invalid")
    } catch (_: IllegalStateException) {
      Review(error = "wait_tape_replay_mismatch")
    }
  }
}
