package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainDelta
import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.Goal
import com.rabpit.backroom.core.companion.BrainContracts.Promise
import com.rabpit.backroom.core.companion.BrainContracts.RelationshipAppraisal

/**
 * P2c goal / promise / relationship reducer (issue #507): GR01-GR03, RR01.
 *
 * - GR01: validated PROMISE_ACCEPTED (structured event, never prose) with the
 *   actor as promisor -> CREATE one ACTIVE goal per actor/promise. Idempotent.
 * - GR02: verified outcome satisfying exactly the accepted predicate+args ->
 *   DONE, fulfillment evidence linked. At most once; replay emits no new delta.
 * - GR03: verified breach (deadline crossing or incompatible outcome under a
 *   known predicate) -> ABANDONED with reason. Missing success alone is not
 *   breach; ambiguous outcomes keep status and record no delta.
 * - RR01: beneficiary/promisor appraisal FULFILLED or BREACHED per promise
 *   outcome. One appraisal per promise; trustBand/Disposition untouched in v1
 *   (no scalar trust change, ever, in this reducer).
 *
 * Pure function: no I/O, no provider, no RNG.
 */
internal object GoalReducer {
  data class AcceptInput(
    val prior: BrainState,
    val promise: Promise,
    /** Validated PROMISE_ACCEPTED event id. */
    val eventId: String
  )

  data class OutcomeInput(
    val prior: BrainState,
    val promiseId: String,
    /** Verified Core outcome bound to the accepted predicate. */
    val outcome: PredicateOutcome
  )

  data class PredicateOutcome(
    val predicateId: String,
    val args: Map<String, String>,
    val satisfied: Boolean,
    val breached: Boolean,
    val evidenceEventId: String
  )

  data class Result(val state: BrainState, val deltas: List<BrainDelta>)

  /** Deterministic goal identity: one goal per actor/promise. */
  internal fun goalId(actorId: String, promiseId: String): String =
    CompanionDigests.sha256(listOf(actorId, promiseId, "goal").joinToString("|")).take(32)

  /** GR01: validated PROMISE_ACCEPTED -> CREATE ACTIVE goal (idempotent). */
  fun reduceAccept(input: AcceptInput): Result {
    require(input.prior.actorId == input.promise.promisorActorId) { "goal_actor_not_promisor" }
    require(input.eventId.isNotBlank()) { "goal_acceptance_event_missing" }
    val id = goalId(input.prior.actorId, input.promise.promiseId)
    val existing = input.prior.goals.find { it.goalId == id }
    if (existing != null) return Result(input.prior, emptyList())
    val goal = Goal(goalId = id, promise = input.promise, status = Goal.GoalStatus.ACTIVE)
    val delta = BrainDelta(
      ruleId = "GR01", ruleVersion = BrainContracts.RULE_VERSION,
      targetKind = BrainDelta.TargetKind.GOAL, targetId = id,
      evidenceObservationIds = emptyList())
    return Result(input.prior.copy(goals = input.prior.goals + goal), listOf(delta))
  }

  /** GR02/GR03: verified outcome -> DONE / ABANDONED, or no delta. */
  fun reduceOutcome(input: OutcomeInput): Result {
    BrainContracts.Predicates.requireKnown(input.outcome.predicateId)
    val goal = input.prior.goals.find { it.promise.promiseId == input.promiseId }
      ?: return Result(input.prior, emptyList())
    if (goal.status != Goal.GoalStatus.ACTIVE) {
      return Result(input.prior, emptyList())  // already resolved: at most once
    }
    val promise = goal.promise
    val next = when {
      input.outcome.satisfied &&
        input.outcome.predicateId == promise.completionPredicateId &&
        input.outcome.args == promise.predicateArgs -> Goal.GoalStatus.DONE
      input.outcome.breached -> Goal.GoalStatus.ABANDONED
      else -> null  // wrong actor/target, unknown args, mere missing success: no delta
    } ?: return Result(input.prior, emptyList())
    val updated = goal.copy(status = next)
    val goals = input.prior.goals.map { if (it.goalId == goal.goalId) updated else it }
    val delta = BrainDelta(
      ruleId = if (next == Goal.GoalStatus.DONE) "GR02" else "GR03",
      ruleVersion = BrainContracts.RULE_VERSION,
      targetKind = BrainDelta.TargetKind.GOAL, targetId = goal.goalId,
      evidenceObservationIds = emptyList())
    return Result(input.prior.copy(goals = goals), listOf(delta))
  }

  /**
   * RR01: record promise appraisal FULFILLED/BREACHED. One appraisal per promise;
   * re-application is idempotent. No trust scalar or disposition change.
   */
  fun reduceAppraisal(prior: BrainState, promiseId: String, breached: Boolean): Result {
    val goal = prior.goals.find { it.promise.promiseId == promiseId }
      ?: return Result(prior, emptyList())
    val existing = prior.appraisals.find { it.promiseId == promiseId }
    if (existing != null) return Result(prior, emptyList())
    val otherActorId = if (prior.actorId == goal.promise.promisorActorId)
      goal.promise.beneficiaryActorId else goal.promise.promisorActorId
    val appraisal = RelationshipAppraisal(
      promiseId = promiseId,
      otherActorId = otherActorId,
      appraisal = if (breached) RelationshipAppraisal.Appraisal.BREACHED
      else RelationshipAppraisal.Appraisal.FULFILLED)
    val delta = BrainDelta(
      ruleId = "RR01", ruleVersion = BrainContracts.RULE_VERSION,
      targetKind = BrainDelta.TargetKind.APPRAISAL, targetId = promiseId,
      evidenceObservationIds = emptyList())
    return Result(prior.copy(appraisals = prior.appraisals + appraisal), listOf(delta))
  }
}
