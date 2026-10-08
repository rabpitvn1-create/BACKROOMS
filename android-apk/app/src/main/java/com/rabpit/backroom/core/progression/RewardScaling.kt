package com.rabpit.backroom.core.progression

/**
 * Pure, deterministic reward scaling.
 *
 * Design skeleton for https://github.com/rabpitvn1-create/BACKROOMS/issues/453
 * (re-review point 2: reward progression also needs explicit Core semantics).
 *
 * LOCKED semantics: rewards stay DISCRETE per full Level.
 * - Rationale: rewards are economy tuning, not difficulty. Inserting a
 *   Sub-level (fractional rank) must not mint fractional rewards or inflate
 *   the economy. Legacy behavior is bit-identical by construction.
 * - Considered and rejected: scaling rewards proportionally to fractional
 *   difficulty (couples the economy to content insertion; harder to tune).
 *
 * Honest naming: unlike entity stats (linear +10pp of base), the core
 * victory reward IS compounding: base * 1.5^level. Documented as such —
 * the two progressions deliberately differ.
 *
 * Covers the legacy consumers that read authoritative `stageIndex` from JSON:
 * - `Combat93Support.Progression.scaledCoreReward` (1.5^stage),
 * - treasure first/repeat kill amounts (`EntityStatCore.scale`),
 * - the `treasureEntityStageKills` first-kill bucket.
 * None of these may keep taking the legacy `stageIndex` as authority.
 */
object RewardScaling {

  /** Discrete full level for a rank: rewards never see fractional ranks. */
  fun discreteLevelOf(progressionRank: Long): Int {
    require(progressionRank in 0L..EntityScaling.MAX_PROGRESSION_RANK) {
      "progressionRank out of supported domain: $progressionRank"
    }
    return (progressionRank / RANK_PER_FULL_LEVEL).toInt()
  }

  /**
   * Core victory reward. Keeps the legacy 1.5^level semantics, computed as
   * the exact rational roundHalfUp(base * 3^L / 2^L) — no floating point,
   * deterministic across devices. Bit-identical to
   * `Math.round(base * 1.5^L)` for game-scale levels.
   */
  fun scaledCoreReward(base: Int, progressionRank: Long): Int {
    val level = discreteLevelOf(progressionRank)
    require(level <= MAX_REWARD_LEVEL) { "reward level out of supported domain: $level" }
    var num = maxOf(0, base).toLong() // base * 3^level
    var den = 1L // 2^level
    repeat(level) { num *= 3L; den *= 2L }
    return ((2L * num + den) / (2L * den)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  /**
   * Treasure first-kill bucket key. Keys stay discrete-level strings, so the
   * persisted `treasureEntityStageKills` shape is UNCHANGED for legacy saves
   * ("2" stays "2"); sub-levels share their discrete level's bucket, which
   * keeps the economy stable when new content is inserted.
   */
  fun treasureBucketKey(progressionRank: Long): String =
    discreteLevelOf(progressionRank).toString()

  /**
   * Treasure kill reward amounts: linear EntityStatCore-style scaling, but
   * discrete per full level (see class KDoc).
   */
  fun treasureKillReward(baseCore: Int, progressionRank: Long): Int =
    EntityScaling.scale(baseCore, discreteLevelOf(progressionRank).toLong() * RANK_PER_FULL_LEVEL)

  /** Supported reward levels: 1.5^18 ≈ 1478x is already far beyond tuning range. */
  const val MAX_REWARD_LEVEL: Int = 18
}
