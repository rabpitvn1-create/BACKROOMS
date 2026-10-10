package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainDelta
import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.MoodState

/**
 * P2d one-turn mood reducer (issue #508): MR01 / MR02.
 *
 * Mood is a mechanical presentation state, not a personality or a belief about a
 * threat's cause.
 *
 * - MR01: an eligible native observation of a verified immediate-threat event,
 *   classified by the reviewed salience map, sets WORRIED with cause=eventId and
 *   expiryTurn = coreTurn + 1. ORDINARY events emit no change. IMPORTANT/PIVOTAL
 *   share the one-turn lifetime; no stacking. A later eligible danger replaces
 *   the prior cause/expiry (deterministic order; equal turn -> event id order is
 *   the caller's responsibility). Re-applying the same event is idempotent.
 * - MR02: when the Core turn reaches expiryTurn with no newer eligible trigger,
 *   mood becomes UNSET; the prior cause is retained in history. Reload/retry at
 *   the same turn never changes mood or expiry (Core turn is the only clock).
 *
 * Pure function: no I/O, no provider, no RNG.
 */
internal object MoodReducer {
  data class DangerInput(
    val prior: BrainState,
    /** Native event type, classified by the reviewed salience map. */
    val eventType: String,
    val eventId: String,
    val coreTurn: Long,
    val actorId: String
  )

  data class Result(val state: BrainState, val deltas: List<BrainDelta>)

  /** MR01: eligible immediate danger -> WORRIED, expiry = turn+1. */
  fun reduceDanger(input: DangerInput): Result {
    require(input.prior.ruleVersion == BrainContracts.RULE_VERSION) { "brain_rule_unsupported" }
    require(input.coreTurn >= 0 && input.coreTurn < Long.MAX_VALUE) { "mood_turn_invalid" }
    require(input.actorId == input.prior.actorId) { "mood_cross_actor" }
    require(input.eventId.isNotBlank()) { "mood_event_missing" }
    val salience = BrainContracts.SalienceMap.salienceOf(input.eventType)
    if (salience == EpisodicMemory.Salience.ORDINARY) {
      return Result(input.prior, emptyList())  // ORDINARY emits no mood change
    }
    val current = input.prior.mood
    if (current.cause == input.eventId) return Result(input.prior,emptyList())
    require(current.triggeredTurn == null || input.coreTurn >= current.triggeredTurn) { "mood_stale_trigger" }
    val next = MoodState(MoodState.Mood.WORRIED, cause = input.eventId,
      expiryTurn = input.coreTurn + 1, triggeredTurn = input.coreTurn)
    if (current == next) return Result(input.prior, emptyList())  // idempotent
    val delta = BrainDelta(
      ruleId = "MR01", ruleVersion = BrainContracts.RULE_VERSION,
      targetKind = BrainDelta.TargetKind.MOOD, targetId = input.actorId,
      evidenceObservationIds = emptyList())
    return Result(input.prior.copy(mood = next), listOf(delta))
  }

  /** MR02: turn reaches expiry with no newer trigger -> UNSET, cause retained. */
  fun reduceExpire(prior: BrainState, coreTurn: Long): Result {
    require(prior.ruleVersion == BrainContracts.RULE_VERSION) { "brain_rule_unsupported" }
    require(coreTurn >= 0) { "mood_turn_invalid" }
    val current = prior.mood
    if (current.mood != MoodState.Mood.WORRIED) return Result(prior, emptyList())
    val expiry = current.expiryTurn ?: return Result(prior, emptyList())
    if (coreTurn < expiry) return Result(prior, emptyList())
    // UNSET; prior cause retained in history (cause field kept).
    val next = current.copy(mood = MoodState.Mood.UNSET, expiryTurn = null)
    val delta = BrainDelta(
      ruleId = "MR02", ruleVersion = BrainContracts.RULE_VERSION,
      targetKind = BrainDelta.TargetKind.MOOD, targetId = prior.actorId,
      evidenceObservationIds = emptyList())
    return Result(prior.copy(mood = next), listOf(delta))
  }
}
