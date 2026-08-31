package com.rabpit.backroom.core

import java.security.SecureRandom

data class ActionRollSeedResult(
  val state: GameState,
  val applied: Boolean,
  val duplicate: Boolean = false,
  val error: String? = null
)

/**
 * One random seed per persistent ActionRuntime session.
 *
 * A seed is durably stored before any player-facing gameplay roll is returned. Every roll is then a
 * pure function of that saved seed, session ID and roll label. Retrying the same pending action after
 * a provider failure, process restart or WebView resubmission therefore cannot farm a fresh result.
 * ActionRuntime.finish() removes this seed together with the rest of the actionRuntime.* metadata.
 */
object ActionRollRuntime {
  private const val SEED_KEY = "actionRuntime.rollSeed"
  private val secureRandom = SecureRandom()

  fun ensureSeed(state: GameState, sessionId: String, seedOverride: Long? = null): ActionRollSeedResult {
    val session = ActionRuntime.activeSession(state)
      ?: return ActionRollSeedResult(state, applied = false, error = "action_session_missing")
    if (session.sessionId != sessionId) {
      return ActionRollSeedResult(state, applied = false, error = "action_session_mismatch")
    }
    val existingRaw = state.metadata[SEED_KEY]
    if (existingRaw != null) {
      if (existingRaw.toLongOrNull() == null) {
        return ActionRollSeedResult(state, applied = false, error = "action_roll_seed_invalid")
      }
      return ActionRollSeedResult(state, applied = false, duplicate = true)
    }
    val seed = seedOverride ?: secureRandom.nextLong()
    return ActionRollSeedResult(
      state.copy(metadata = state.metadata + (SEED_KEY to seed.toString())),
      applied = true
    )
  }

  fun lockedRoll(state: GameState, sessionId: String, label: String, bound: Int): Int {
    require(bound > 0) { "action_roll_bound_invalid" }
    require(label.isNotBlank()) { "action_roll_label_required" }
    val session = ActionRuntime.activeSession(state)
      ?: throw IllegalStateException("action_session_missing")
    if (session.sessionId != sessionId) throw IllegalStateException("action_session_mismatch")
    val seed = state.metadata[SEED_KEY]?.toLongOrNull()
      ?: throw IllegalStateException("action_roll_seed_missing")
    val combined = seed xor stable64(sessionId) xor java.lang.Long.rotateLeft(stable64(label), 17)
    val positive = mix64(combined) and Long.MAX_VALUE
    return (positive % bound.toLong()).toInt() + 1
  }

  private fun stable64(value: String): Long {
    var hash = 1469598103934665603L
    for (character in value) {
      hash = (hash xor character.code.toLong()) * 1099511628211L
    }
    return hash
  }

  private fun mix64(input: Long): Long {
    var value = input
    value = value xor (value ushr 30)
    value *= -4658895280553007687L
    value = value xor (value ushr 27)
    value *= -7723592293110705685L
    return value xor (value ushr 31)
  }
}
