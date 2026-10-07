package com.rabpit.backroom.core.progression

/**
 * Pure, deterministic Entity scaling.
 *
 * Design skeleton for https://github.com/rabpitvn1-create/BACKROOMS/issues/453
 * (design phase: additive only; the legacy `EntityStatCore` is untouched).
 *
 * LOCKED semantics: LINEAR over base.
 * One full Level step adds 10 percentage points over base. NOT compounding.
 * Documented honestly: this is NOT "+10% versus the previous node".
 *
 * percentOfBase(rank) = 100 + rank * 10 / RANK_PER_FULL_LEVEL
 *
 *     rank 0         -> 100%
 *     rank 500,000   -> 105%
 *     rank 1,000,000 -> 110%
 *     rank 1,500,000 -> 115%
 *     rank 2,000,000 -> 120%
 *
 * Legacy Levels 0..6 (rank = N * RANK_PER_FULL_LEVEL) produce bit-identical
 * results to the previous stageIndex formula (100 + 10 * N), so migrating
 * changes no existing balance.
 *
 * Knows nothing about Levels, Sub-levels, AI, prompts, canon or locations.
 * Effective stats derive ONLY from (base stat, Core progressionRank).
 */
object EntityScaling {

  /**
   * Percent of base, e.g. 110 means "110% of base".
   * Integer math, deterministic across devices. Never a float.
   */
  fun percentOfBase(progressionRank: Long): Int {
    require(progressionRank >= 0L) { "progressionRank must be >= 0, was $progressionRank" }
    return (100L + progressionRank * 10L / RANK_PER_FULL_LEVEL).toInt()
  }

  /**
   * Scale a single base stat (HP or damage).
   * Same rounding as the legacy formula: round-half-up at the percent step.
   */
  fun scale(baseValue: Int, progressionRank: Long): Int {
    val percent = percentOfBase(progressionRank)
    val scaled = maxOf(0, baseValue).toLong() * percent
    return maxOf(0, ((scaled + 50L) / 100L).toInt())
  }
}
