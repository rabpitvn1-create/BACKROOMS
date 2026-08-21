package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PhysiologyStatusPolicyTest {
  @Test fun unknownAndNegativeCountersStayUnknown() {
    assertEquals(PhysiologyBand.UNKNOWN, PhysiologyStatusPolicy.hungerBand(null))
    assertEquals(PhysiologyBand.UNKNOWN, PhysiologyStatusPolicy.thirstBand(null))
    assertEquals(PhysiologyBand.UNKNOWN, PhysiologyStatusPolicy.awakeBand(null))
    assertEquals(PhysiologyBand.UNKNOWN, PhysiologyStatusPolicy.hungerBand(-1L))
    assertEquals(PhysiologyBand.UNKNOWN, PhysiologyStatusPolicy.thirstBand(-1L))
    assertEquals(PhysiologyBand.UNKNOWN, PhysiologyStatusPolicy.awakeBand(-1L))
  }

  @Test fun hungerThresholdsAreDeterministicAtBoundaries() {
    assertEquals(PhysiologyBand.NORMAL, PhysiologyStatusPolicy.hungerBand(0L))
    assertEquals(PhysiologyBand.NORMAL, PhysiologyStatusPolicy.hungerBand(719L))
    assertEquals(PhysiologyBand.MILD, PhysiologyStatusPolicy.hungerBand(720L))
    assertEquals(PhysiologyBand.MODERATE, PhysiologyStatusPolicy.hungerBand(1440L))
    assertEquals(PhysiologyBand.SEVERE, PhysiologyStatusPolicy.hungerBand(2880L))
    assertEquals(PhysiologyBand.CRITICAL, PhysiologyStatusPolicy.hungerBand(4320L))
  }

  @Test fun thirstThresholdsAreDeterministicAtBoundaries() {
    assertEquals(PhysiologyBand.NORMAL, PhysiologyStatusPolicy.thirstBand(359L))
    assertEquals(PhysiologyBand.MILD, PhysiologyStatusPolicy.thirstBand(360L))
    assertEquals(PhysiologyBand.MODERATE, PhysiologyStatusPolicy.thirstBand(720L))
    assertEquals(PhysiologyBand.SEVERE, PhysiologyStatusPolicy.thirstBand(1440L))
    assertEquals(PhysiologyBand.CRITICAL, PhysiologyStatusPolicy.thirstBand(2880L))
  }

  @Test fun awakeThresholdsAreDeterministicAtBoundaries() {
    assertEquals(PhysiologyBand.NORMAL, PhysiologyStatusPolicy.awakeBand(959L))
    assertEquals(PhysiologyBand.MILD, PhysiologyStatusPolicy.awakeBand(960L))
    assertEquals(PhysiologyBand.MODERATE, PhysiologyStatusPolicy.awakeBand(1200L))
    assertEquals(PhysiologyBand.SEVERE, PhysiologyStatusPolicy.awakeBand(1440L))
    assertEquals(PhysiologyBand.CRITICAL, PhysiologyStatusPolicy.awakeBand(2160L))
  }

  @Test fun derivedStatusKeepsExplicitConditionTextWithoutInventingMeaning() {
    val derived = PhysiologyStatusPolicy.derive(PhysiologyState(
      minutesSinceFood = 800L,
      minutesSinceWater = 100L,
      minutesAwake = 1300L,
      painState = "  moderate  ",
      infectionState = " suspected ",
      thermalState = " cold "))

    assertEquals(PhysiologyBand.MILD, derived.hunger)
    assertEquals(PhysiologyBand.NORMAL, derived.thirst)
    assertEquals(PhysiologyBand.MODERATE, derived.sleepDeprivation)
    assertEquals("moderate", derived.pain)
    assertEquals("suspected", derived.infection)
    assertEquals("cold", derived.thermal)
  }

  @Test fun blankConditionTextIsNotExposedAsAStatus() {
    val derived = PhysiologyStatusPolicy.derive(PhysiologyState(
      painState = "   ",
      infectionState = "",
      thermalState = " \t "))

    assertNull(derived.pain)
    assertNull(derived.infection)
    assertNull(derived.thermal)
  }
}
