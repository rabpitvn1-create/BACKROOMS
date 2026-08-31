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

  fun derive(state: PhysiologyState): DerivedPhysiologyStatus = DerivedPhysiologyStatus(
    hunger = hungerBand(state.minutesSinceFood),
    thirst = thirstBand(state.minutesSinceWater),
    sleepDeprivation = awakeBand(state.minutesAwake),
    pain = state.painState?.trim()?.takeIf { it.isNotEmpty() },
    infection = state.infectionState?.trim()?.takeIf { it.isNotEmpty() },
    thermal = state.thermalState?.trim()?.takeIf { it.isNotEmpty() },
    foodPercent = remainingPercent(state.minutesSinceFood, FOOD_CRITICAL_MINUTES),
    waterPercent = remainingPercent(state.minutesSinceWater, WATER_CRITICAL_MINUTES),
    restPercent = remainingPercent(state.minutesAwake, REST_CRITICAL_MINUTES)
  )

  fun hungerBand(minutesSinceFood: Long?): PhysiologyBand = band(
    minutesSinceFood,
    mildAt = 12L * 60L,
    moderateAt = 24L * 60L,
    severeAt = 48L * 60L,
    criticalAt = FOOD_CRITICAL_MINUTES
  )

  fun thirstBand(minutesSinceWater: Long?): PhysiologyBand = band(
    minutesSinceWater,
    mildAt = 6L * 60L,
    moderateAt = 12L * 60L,
    severeAt = 24L * 60L,
    criticalAt = WATER_CRITICAL_MINUTES
  )

  fun awakeBand(minutesAwake: Long?): PhysiologyBand = band(
    minutesAwake,
    mildAt = 16L * 60L,
    moderateAt = 20L * 60L,
    severeAt = 24L * 60L,
    criticalAt = REST_CRITICAL_MINUTES
  )

  fun foodPercent(minutesSinceFood: Long?): Int? = remainingPercent(minutesSinceFood, FOOD_CRITICAL_MINUTES)
  fun waterPercent(minutesSinceWater: Long?): Int? = remainingPercent(minutesSinceWater, WATER_CRITICAL_MINUTES)
  fun restPercent(minutesAwake: Long?): Int? = remainingPercent(minutesAwake, REST_CRITICAL_MINUTES)

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
