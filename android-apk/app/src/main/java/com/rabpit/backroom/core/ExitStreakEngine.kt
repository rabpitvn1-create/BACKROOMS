package com.rabpit.backroom.core

/**
 * Core-owned exit progress for ordinary player actions.
 *
 * SEARCH, EXPLORE, and freeform EXECUTE share the same rule. A combat turn
 * never evaluates the exit RNG and never changes an existing streak.
 * The caller commits this result at most once per accepted ordinary turn,
 * and performs the level transition on [ExitStreakOutcome.completed].
 */
data class ExitStreakOutcome(
  val accepted: Boolean,
  val evaluated: Boolean,
  val success: Boolean?,
  val streak: Int,
  val completed: Boolean,
  val error: String? = null,
)

object ExitStreakEngine {
  const val MIN_INPUT_CODE_POINTS = 15
  const val REQUIRED_WINS = 5
  const val RNG_BOUND = 2

  /** Whitespace at both ends is ignored; Unicode code points, not UTF-16 units, count. */
  @JvmStatic fun hasMinimumInput(action: String): Boolean {
    val text = action.trim()
    return text.codePointCount(0, text.length) >= MIN_INPUT_CODE_POINTS
  }

  /**
   * Pure state transition. nextInt(2) == 0 is a success, == 1 is a failure.
   * No semantic filters: any ordinary input meeting the length floor is valid.
   * Inputs rejected for length, and all combat actions, consume no RNG.
   *
   * Streak must be stored per level by the integration layer and reset atomically
   * when a level transition is committed.
   */
  @JvmStatic fun advance(
    previousStreak: Int,
    action: String,
    combatTurn: Boolean,
    nextInt: (Int) -> Int,
  ): ExitStreakOutcome {
    if (combatTurn) return ExitStreakOutcome(
      accepted = true, evaluated = false, success = null,
      streak = previousStreak, completed = false,
    )
    if (!hasMinimumInput(action)) return ExitStreakOutcome(
      accepted = false, evaluated = false, success = null,
      streak = previousStreak, completed = false, error = "input_too_short",
    )
    // Ignore impossible persisted streaks rather than letting forged progress win.
    val prior = previousStreak.takeIf { it in 0 until REQUIRED_WINS } ?: 0
    val roll = nextInt(RNG_BOUND)
    require(roll in 0 until RNG_BOUND) { "Exit streak RNG must return 0 or 1" }
    val success = roll == 0
    val streak = if (success) prior + 1 else 0
    return ExitStreakOutcome(
      accepted = true, evaluated = true, success = success,
      streak = streak, completed = streak == REQUIRED_WINS,
    )
  }
}
