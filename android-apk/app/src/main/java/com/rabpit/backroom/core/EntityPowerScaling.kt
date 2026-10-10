package com.rabpit.backroom.core

import org.json.JSONObject

/**
 * Deterministic Entity power scaling.
 *
 * Balance semantics are linear on the Level-0 base:
 * full Level 0/1/2/3 => 100/110/120/130 percent of base.
 * This is NOT compounding against the previous Level.
 */
object EntityPowerScaling {
  const val RANK_PER_FULL_LEVEL: Long = 1_000_000L
  const val MAX_PROGRESSION_RANK: Long = 1_000_000_000L
  private const val DENOMINATOR: Long = 10L * RANK_PER_FULL_LEVEL

  private val legacyLevelRanks = longArrayOf(
    0L,
    1_000_000L,
    2_000_000L,
    3_000_000L,
    4_000_000L,
    5_000_000L,
    6_000_000L
  )

  fun rankForLevel(level: Int): Long {
    require(level in legacyLevelRanks.indices) { "unknown_level:$level" }
    return legacyLevelRanks[level]
  }

  /**
   * Current main still persists the committed Level as legacy levelJson.
   * Missing data is Level 0 for old/fresh saves; malformed/unknown explicit data fails closed.
   */
  fun rankFor(state: GameState): Long {
    val raw = state.world["levelJson"]?.takeIf { it.isNotBlank() } ?: return 0L
    val level = try {
      val json = JSONObject(raw)
      require(json.has("number")) { "level_number_missing" }
      json.getInt("number")
    } catch (error: Exception) {
      throw IllegalArgumentException("invalid_level_json", error)
    }
    return rankForLevel(level)
  }

  fun requireRank(rank: Long): Long {
    require(rank in 0L..MAX_PROGRESSION_RANK) { "invalid_progression_rank:$rank" }
    return rank
  }

  /**
   * Fixed-point half-up scaling. No intermediate integer percent is used, so
   * future fractional Sub-level ranks retain their precision.
   */
  fun scale(baseValue: Int, progressionRank: Long): Int {
    require(baseValue >= 0) { "negative_base_value:$baseValue" }
    val rank = requireRank(progressionRank)
    val numerator = Math.addExact(DENOMINATOR, rank)
    val product = Math.multiplyExact(baseValue.toLong(), numerator)
    val rounded = Math.addExact(product, DENOMINATOR / 2L) / DENOMINATOR
    require(rounded <= Int.MAX_VALUE.toLong()) { "scaled_value_overflow:$rounded" }
    return rounded.toInt()
  }
}
