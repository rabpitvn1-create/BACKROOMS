package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the linear Entity scaling semantics from issue #453:
 * +10 percentage points over base per full Level. NOT compounding.
 * [EntityScaling.scale] keeps full fixed-point precision; the truncated
 * integer [EntityScaling.percentOfBase] is debug/UI only and is NOT
 * authoritative for computation.
 */
class EntityScalingTest {

  @Test fun percentTableMatchesLockedContract() {
    assertEquals(100, EntityScaling.percentOfBase(0L))
    assertEquals(105, EntityScaling.percentOfBase(500_000L))
    assertEquals(110, EntityScaling.percentOfBase(1_000_000L))
    assertEquals(115, EntityScaling.percentOfBase(1_500_000L))
    assertEquals(120, EntityScaling.percentOfBase(2_000_000L))
  }

  @Test fun scaleKeepsFractionalPrecision() {
    // rank 1_050_000 = true 110.5% of base. The integer percent truncates to
    // 110, but scale() must NOT go through it: 100 * 110.5% = 110.5 -> 111.
    assertEquals(110, EntityScaling.percentOfBase(1_050_000L))
    assertEquals(111, EntityScaling.scale(100, 1_050_000L))
    assertEquals(110, EntityScaling.scale(100, 1_000_000L))
    assertEquals(105, EntityScaling.scale(100, 500_000L))
  }

  @Test fun legacyLevelsMigrateBitIdentical() {
    // Reference: previous EntityStatCore formula with stageIndex semantics.
    // Kept inline (the legacy class is package-private in `core`).
    fun legacyScale(baseValue: Int, stageIndex: Int): Int {
      val pct = 100 + 10 * maxOf(0, stageIndex)
      val scaled = maxOf(0, baseValue).toLong() * pct
      return maxOf(0, ((scaled + 50L) / 100L).toInt())
    }
    val bases = listOf(0, 1, 7, 42, 100, 999, 10_000, 1_000_000)
    for (n in 0..6) {
      assertEquals(100 + 10 * n, EntityScaling.percentOfBase(n * RANK_PER_FULL_LEVEL))
      for (base in bases) {
        assertEquals(
          "base=$base level=$n",
          legacyScale(base, n),
          EntityScaling.scale(base, n * RANK_PER_FULL_LEVEL),
        )
      }
    }
  }

  @Test fun scaleIsDeterministicAndMonotonicInRank() {
    val base = 120
    val atLevel1 = EntityScaling.scale(base, 1_000_000L)
    assertEquals(atLevel1, EntityScaling.scale(base, 1_000_000L))
    assertTrue(EntityScaling.scale(base, 500_000L) <= atLevel1)
    assertTrue(atLevel1 <= EntityScaling.scale(base, 2_000_000L))
  }

  @Test fun invalidRankFailsClosed() {
    for (bad in listOf(-1L, -1_000_000L, EntityScaling.MAX_PROGRESSION_RANK + 1)) {
      try {
        EntityScaling.scale(100, bad)
        fail("rank $bad must fail closed, never clamp or wrap")
      } catch (e: IllegalArgumentException) {
        // expected
      }
    }
  }

  @Test fun scaleDerivesOnlyFromBaseAndRank() {
    // Same (base, rank) -> same result, regardless of any other input.
    // There is no level name, canon text or AI output in the signature.
    assertEquals(EntityScaling.scale(80, 2_000_000L), EntityScaling.scale(80, 2_000_000L))
    assertEquals(96, EntityScaling.scale(80, 2_000_000L)) // 80 * 120% = 96
  }
}
