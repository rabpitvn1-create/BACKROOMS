package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameState

/** S1c.2 fail-closed binding seam. No second Core reducer, RNG, provider or persistence path. */
object CompanionDecisionBinding {
  data class Result(val accepted: Boolean, val error: String? = null)

  fun verifyWait(
    persistedSnapshot: ByteArray,
    state: GameState,
    turn: CompanionPendingTurn,
    slotId: String,
    requestId: String,
    expectedRevision: Long,
    pinnedPolicy: String,
    exactPlayerInput: String
  ): Result = Result(false, "binding_not_implemented")
}
