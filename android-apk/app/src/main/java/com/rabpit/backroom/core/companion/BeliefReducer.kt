package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainDelta
import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.Claim

/**
 * P2b belief reducer interface (issue #506 implements BR01/BR02).
 *
 * Stub throws NotImplementedError: P2a fixtures (#505) are RED until #506 lands.
 */
internal object BeliefReducer {
  data class ToldInput(
    val prior: BrainState,
    val claim: Claim,
    /** The validated TOLD observation's id; speaker/listener already verified. */
    val observationId: String,
    val actorId: String
  )

  data class ContradictionInput(
    val prior: BrainState,
    val newClaim: Claim,
    val newObservationId: String,
    val actorId: String
  )

  data class Result(val state: BrainState, val deltas: List<BrainDelta>)

  /** BR01: valid TOLD observation -> CREATE belief UNKNOWN. */
  fun reduceTold(input: ToldInput): Result = throw NotImplementedError("BR01 not implemented (#506)")

  /** BR02: validated contradicting evidence -> stance DISPUTED, append evidence. */
  fun reduceContradiction(input: ContradictionInput): Result =
    throw NotImplementedError("BR02 not implemented (#506)")
}
