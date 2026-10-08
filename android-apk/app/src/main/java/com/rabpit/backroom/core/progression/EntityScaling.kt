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
 * True percent (rational): 100 + rank * 10 / RANK_PER_FULL_LEVEL, e.g.
 *
 *     rank 0         -> 100%
 *     rank 500,000   -> 105%
 *     rank 1,000,000 -> 110%
 *     rank 1,050,000 -> 110.5%
 *     rank 1,500,000 -> 115%
 *     rank 2,000,000 -> 120%
 *
 * [scale] computes with the FULL fixed-point rational and never truncates
 * through an integer percent: rank 1_050_000 yields 110.5% of base, not 110%.
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
   * Supported rank domain. Fail closed above it rather than silently overflow:
   * 1_000_000_000 ranks = 1000 full Levels of headroom (the game has 7).
   */
  const val MAX_PROGRESSION_RANK: Long = 1_000_000_000L

  /**
   * Integer percent of base, e.g. 110 means "110% of base".
   * TRUNCATED (110.5% -> 110). Debug / UI display ONLY — never authoritative
   * for computation. [scale] does not use this function.
   */
  fun percentOfBase(progressionRank: Long): Int {
    requireValidRank(progressionRank)
    return (100L + progressionRank * 10L / RANK_PER_FULL_LEVEL).toInt()
  }

  /**
   * Scale a single base stat (HP or damage) with full fixed-point precision.
   *
   *   scale = base * (100 + rank * 10 / R) / 100
   *         = base + base * rank / (10 * R)          [R = RANK_PER_FULL_LEVEL]
   *
   * computed as base + roundHalfUp(2 * base * rank + 10*R, 20*R), i.e.
   * (2 * base * rank + 10_000_000) / 20_000_000 with R = 1_000_000.
   * Long-safe inside the supported domain; result clamped to Int range.
   */
  fun scale(baseValue: Int, progressionRank: Long): Int {
    requireValidRank(progressionRank)
    val base = maxOf(0, baseValue).toLong()
    // 10 * R = 10_000_000; round-half-up of (base * rank / 10_000_000).
    val fractional = (2L * base * progressionRank + 10_000_000L) / 20_000_000L
    return (base + fractional).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  private fun requireValidRank(progressionRank: Long) {
    require(progressionRank in 0L..MAX_PROGRESSION_RANK) {
      "progressionRank out of supported domain [0, $MAX_PROGRESSION_RANK]: $progressionRank"
    }
  }
}
