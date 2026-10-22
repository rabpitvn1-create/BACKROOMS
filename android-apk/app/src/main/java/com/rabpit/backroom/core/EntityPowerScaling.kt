package com.rabpit.backroom.core

/** Linear power: +10 percentage points of base per five Cao Minh levels. */
object EntityPowerScaling {
  const val LEVELS_PER_STEP = 5
  const val RANK_PER_STEP: Long = 1_000_000L
  const val MAX_PROGRESSION_RANK: Long = 1_000_000_000L
  private const val DENOMINATOR: Long = 10L * RANK_PER_STEP

  fun rankForCharacterLevel(level: Int): Long {
    require(level in 0..CharacterProgressionCore.MAX_LEVEL) { "invalid_character_level:$level" }
    return (level / LEVELS_PER_STEP) * RANK_PER_STEP
  }

  fun rankFor(state: GameState): Long =
    rankForCharacterLevel(state.characters.getValue(CAO_MINH_ID).progression.level)

  fun requireRank(rank: Long): Long {
    require(rank in 0L..MAX_PROGRESSION_RANK) { "invalid_progression_rank:$rank" }
    return rank
  }

  /**
   * Fixed-point half-up scaling. No intermediate integer percent is used, so
   * rounding remains identical for HP and damage.
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
