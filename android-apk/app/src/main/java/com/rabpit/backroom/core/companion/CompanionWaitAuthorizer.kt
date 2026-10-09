package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameState

/**
 * S1c.2 #485 isolated outcome authorization gate.
 * Stub deliberately rejects until the reviewer probes demonstrate RED in Actions.
 */
object CompanionWaitAuthorizer {
  const val WAIT_POLICY = "companion-wait-v1"
  const val EVIDENCE_VERSION = "companion_wait_outcome.v1"

  data class Evidence(
    val version: String, val policy: String, val revision: Long,
    val snapshotDigest: String, val previousStreak: Int,
    val route: CompanionRollTape.Route, val tape: String,
    val success: Boolean, val nextStreak: Int, val completed: Boolean
  )
  class Bound internal constructor(
    val sourceStop: String, val sourceLevel: Int, val previousStreak: Int,
    val snapshotDigest: String, val revision: Long, val input: String
  )
  data class Gate(val bound: Bound? = null, val error: String? = null)
  data class Validated(
    val success: Boolean, val nextStreak: Int, val completed: Boolean,
    val route: CompanionRollTape.Route
  )
  data class Review(val validated: Validated? = null, val error: String? = null)

  fun preflight(
    persistedSnapshot: ByteArray, state: GameState, turn: CompanionPendingTurn,
    slotId: String, requestId: String, expectedRevision: Long,
    pinnedPolicy: String, exactPlayerInput: String
  ): Gate = Gate(error = "wait_not_implemented")

  fun replay(bound: Bound, evidence: Evidence): Review =
    Review(error = "wait_not_implemented")
}
