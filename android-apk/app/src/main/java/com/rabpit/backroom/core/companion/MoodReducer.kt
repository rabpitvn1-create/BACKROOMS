package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainDelta
import com.rabpit.backroom.core.companion.BrainContracts.BrainState

/**
 * P2d mood reducer interface (issue #508 implements MR01/MR02).
 *
 * Stub throws NotImplementedError: P2a fixtures (#505) are RED until #508 lands.
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

  /** MR01: eligible immediate-danger observation -> WORRIED, expiry = turn+1. */
  fun reduceDanger(input: DangerInput): Result = throw NotImplementedError("MR01 not implemented (#508)")

  /** MR02: turn reaches expiry with no newer trigger -> UNSET, cause retained. */
  fun reduceExpire(prior: BrainState, coreTurn: Long): Result =
    throw NotImplementedError("MR02 not implemented (#508)")
}
