package com.rabpit.backroom.core

/** Safe Entity encounter policy. It consumes no puzzle progress or hidden escape data. */
object EntityEncounterPolicy {
  const val SCALE_NUMERATOR = 7
  const val SCALE_DENOMINATOR = 10

  /** Integer floor is the single deterministic rounding rule; the RNG denominator stays unchanged. */
  @JvmStatic fun scaledThreshold(currentThreshold: Int): Int {
    if (currentThreshold <= 0) return 0
    return ((currentThreshold.toLong() * SCALE_NUMERATOR) / SCALE_DENOMINATOR)
      .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  @JvmStatic fun randomEncounterAllowed(constraints: ProceduralGenerationConstraints?): Boolean =
    constraints?.allowEntities ?: true
}
