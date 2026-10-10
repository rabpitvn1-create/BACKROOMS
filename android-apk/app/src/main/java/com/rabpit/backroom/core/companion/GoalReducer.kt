package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainDelta
import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.Promise

/**
 * P2c goal/promise/relationship reducer interface (issue #507 implements
 * GR01-GR03 and RR01).
 *
 * Stub throws NotImplementedError: P2a fixtures (#505) are RED until #507 lands.
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

  /** GR01: validated PROMISE_ACCEPTED -> CREATE ACTIVE goal. */
  fun reduceAccept(input: AcceptInput): Result = throw NotImplementedError("GR01 not implemented (#507)")

  /** GR02/GR03: verified outcome -> DONE (fulfilled) or ABANDONED (breached). */
  fun reduceOutcome(input: OutcomeInput): Result =
    throw NotImplementedError("GR02/GR03 not implemented (#507)")

  /** RR01: beneficiary appraisal FULFILLED/BREACHED; no trust scalar change. */
  fun reduceAppraisal(prior: BrainState, promiseId: String, breached: Boolean): Result =
    throw NotImplementedError("RR01 not implemented (#507)")
}
