package com.rabpit.backroom.core

enum class PhysiologyBand {
  UNKNOWN,
  NORMAL,
  MILD,
  MODERATE,
  SEVERE,
  CRITICAL
}

data class DerivedPhysiologyStatus(
  val hunger: PhysiologyBand,
  val thirst: PhysiologyBand,
  val sleepDeprivation: PhysiologyBand,
  val pain: String?,
  val infection: String?,
  val thermal: String?,
  val foodPercent: Int? = null,
  val waterPercent: Int? = null,
  val restPercent: Int? = null
)

/**
 * Gameplay-facing interpretation of persisted physiology counters.
 *
 * These thresholds are balance constants, not medical diagnoses. Unknown source data remains
 * UNKNOWN instead of being guessed. Character-specific canon can leave counters unset when the
 * standard human survival model is not applicable or has not been established yet.
 */
object PhysiologyStatusPolicy {
  private const val FOOD_CRITICAL_MINUTES = 72L * 60L
  private const val WATER_CRITICAL_MINUTES = 48L * 60L
  private const val REST_CRITICAL_MINUTES = 36L * 60L

  fun derive(state: PhysiologyState, survivalMultiplier: Double = 1.0): DerivedPhysiologyStatus = DerivedPhysiologyStatus(
    hunger = hungerBand(state.minutesSinceFood, survivalMultiplier),
    thirst = thirstBand(state.minutesSinceWater, survivalMultiplier),
    sleepDeprivation = awakeBand(state.minutesAwake, survivalMultiplier),
    pain = state.painState?.trim()?.takeIf { it.isNotEmpty() },
    infection = state.infectionState?.trim()?.takeIf { it.isNotEmpty() },
    thermal = state.thermalState?.trim()?.takeIf { it.isNotEmpty() },
    foodPercent = remainingPercent(state.minutesSinceFood, scaled(FOOD_CRITICAL_MINUTES, survivalMultiplier)),
    waterPercent = remainingPercent(state.minutesSinceWater, scaled(WATER_CRITICAL_MINUTES, survivalMultiplier)),
    restPercent = remainingPercent(state.minutesAwake, scaled(REST_CRITICAL_MINUTES, survivalMultiplier))
  )

  fun hungerBand(minutesSinceFood: Long?, survivalMultiplier: Double = 1.0): PhysiologyBand = band(
    minutesSinceFood,
    mildAt = scaled(12L * 60L, survivalMultiplier),
    moderateAt = scaled(24L * 60L, survivalMultiplier),
    severeAt = scaled(48L * 60L, survivalMultiplier),
    criticalAt = scaled(FOOD_CRITICAL_MINUTES, survivalMultiplier)
  )

  fun thirstBand(minutesSinceWater: Long?, survivalMultiplier: Double = 1.0): PhysiologyBand = band(
    minutesSinceWater,
    mildAt = scaled(6L * 60L, survivalMultiplier),
    moderateAt = scaled(12L * 60L, survivalMultiplier),
    severeAt = scaled(24L * 60L, survivalMultiplier),
    criticalAt = scaled(WATER_CRITICAL_MINUTES, survivalMultiplier)
  )

  fun awakeBand(minutesAwake: Long?, survivalMultiplier: Double = 1.0): PhysiologyBand = band(
    minutesAwake,
    mildAt = scaled(16L * 60L, survivalMultiplier),
    moderateAt = scaled(20L * 60L, survivalMultiplier),
    severeAt = scaled(24L * 60L, survivalMultiplier),
    criticalAt = scaled(REST_CRITICAL_MINUTES, survivalMultiplier)
  )

  fun foodPercent(minutesSinceFood: Long?, survivalMultiplier: Double = 1.0): Int? = remainingPercent(minutesSinceFood, scaled(FOOD_CRITICAL_MINUTES, survivalMultiplier))
  fun waterPercent(minutesSinceWater: Long?, survivalMultiplier: Double = 1.0): Int? = remainingPercent(minutesSinceWater, scaled(WATER_CRITICAL_MINUTES, survivalMultiplier))
  fun restPercent(minutesAwake: Long?, survivalMultiplier: Double = 1.0): Int? = remainingPercent(minutesAwake, scaled(REST_CRITICAL_MINUTES, survivalMultiplier))

  private fun scaled(minutes: Long, multiplier: Double): Long {
    val safe = if (multiplier.isFinite() && multiplier > 0.0) multiplier else 1.0
    return (minutes.toDouble() * safe).toLong().coerceAtLeast(1L)
  }

  private fun band(
    minutes: Long?,
    mildAt: Long,
    moderateAt: Long,
    severeAt: Long,
    criticalAt: Long
  ): PhysiologyBand {
    if (minutes == null) return PhysiologyBand.UNKNOWN
    if (minutes < 0L) return PhysiologyBand.UNKNOWN
    return when {
      minutes >= criticalAt -> PhysiologyBand.CRITICAL
      minutes >= severeAt -> PhysiologyBand.SEVERE
      minutes >= moderateAt -> PhysiologyBand.MODERATE
      minutes >= mildAt -> PhysiologyBand.MILD
      else -> PhysiologyBand.NORMAL
    }
  }

  private fun remainingPercent(minutes: Long?, emptyAt: Long): Int? {
    if (minutes == null || minutes < 0L) return null
    if (emptyAt <= 0L) return null
    val remaining = (emptyAt - minutes).coerceIn(0L, emptyAt)
    return ((remaining * 100L) / emptyAt).toInt().coerceIn(0, 100)
  }
}
