package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the reward scaling semantics from issue #453 (re-review point 2):
 * rewards stay DISCRETE per full Level; the core victory reward keeps its
 * legacy compounding 1.5^level semantics (exact rational, no floats);
 * treasure buckets keep their legacy discrete-level string keys.
 */
class RewardScalingTest {

  @Test fun discreteLevelOfIgnoresFractionalRanks() {
    assertEquals(0, RewardScaling.discreteLevelOf(0L))
    assertEquals(0, RewardScaling.discreteLevelOf(999_999L))
    assertEquals(1, RewardScaling.discreteLevelOf(1_000_000L))
    assertEquals(1, RewardScaling.discreteLevelOf(1_500_000L))
    assertEquals(2, RewardScaling.discreteLevelOf(2_000_000L))
  }

  @Test fun scaledCoreRewardKeepsLegacyCompoundingBitIdentical() {
    // Reference: legacy Combat93Support.Progression.scaledCoreReward.
    fun legacy(base: Int, stage: Int): Int {
      val value = maxOf(0, base) * Math.pow(1.5, maxOf(0, stage).toDouble())
      return minOf(Int.MAX_VALUE, Math.round(value).toInt())
    }
    for (base in listOf(1, 2, 5, 10, 100)) {
      for (stage in 0..10) {
        assertEquals(
          "base=$base stage=$stage",
          legacy(base, stage),
          RewardScaling.scaledCoreReward(base, stage.toLong() * RANK_PER_FULL_LEVEL),
        )
      }
    }
  }

  @Test fun scaledCoreRewardIsDiscretePerFullLevel() {
    // A sub-level never mints a fractional reward: same discrete level,
    // same reward as its parent level.
    val atLevel1 = RewardScaling.scaledCoreReward(2, 1_000_000L)
    assertEquals(atLevel1, RewardScaling.scaledCoreReward(2, 1_500_000L))
    assertEquals(3, atLevel1) // round(2 * 1.5) = 3
    assertEquals(2, RewardScaling.scaledCoreReward(2, 0L))
  }

  @Test fun treasureBucketKeyKeepsLegacyShape() {
    // Legacy bucket keys ("2") stay valid; sub-levels share the discrete
    // level's bucket so inserting content never inflates the economy.
    assertEquals("2", RewardScaling.treasureBucketKey(2_000_000L))
    assertEquals("1", RewardScaling.treasureBucketKey(1_500_000L))
    assertEquals("0", RewardScaling.treasureBucketKey(0L))
  }

  @Test fun treasureKillRewardIsDiscreteAndLinear() {
    // Same linear shape as the legacy EntityStatCore.scale amounts, but
    // discrete per full level.
    fun legacyScale(baseValue: Int, stageIndex: Int): Int {
      val pct = 100 + 10 * maxOf(0, stageIndex)
      return maxOf(0, ((maxOf(0, baseValue).toLong() * pct + 50L) / 100L).toInt())
    }
    for (base in listOf(10, 50, 200)) {
      for (n in 0..6) {
        assertEquals(
          legacyScale(base, n),
          RewardScaling.treasureKillReward(base, n * RANK_PER_FULL_LEVEL),
        )
      }
      // Sub-level: same as parent level, not fractional.
      assertEquals(
        RewardScaling.treasureKillReward(base, 1_000_000L),
        RewardScaling.treasureKillReward(base, 1_500_000L),
      )
    }
  }

  @Test fun invalidRankFailsClosed() {
    try {
      RewardScaling.discreteLevelOf(-1L)
      fail("negative rank must fail closed")
    } catch (e: IllegalArgumentException) {
      // expected
    }
  }
  @Test fun rewardsAcceptWholeSharedRankDomainAndSaturate() {
    assertEquals(2_217, RewardScaling.scaledCoreReward(1, 19_000_000L))
    assertEquals(Int.MAX_VALUE, RewardScaling.scaledCoreReward(Int.MAX_VALUE, 1_000_000L))
    assertEquals(Int.MAX_VALUE, RewardScaling.scaledCoreReward(1, EntityScaling.MAX_PROGRESSION_RANK))
    assertEquals(0, RewardScaling.scaledCoreReward(0, EntityScaling.MAX_PROGRESSION_RANK))
    assertEquals(0, RewardScaling.scaledCoreReward(-1, EntityScaling.MAX_PROGRESSION_RANK))
    var previous = 0
    for (level in 0..1000) {
      val rank = level * RANK_PER_FULL_LEVEL
      val reward = RewardScaling.scaledCoreReward(1, rank)
      assertTrue(reward >= previous)
      assertEquals(level.toString(), RewardScaling.treasureBucketKey(rank))
      assertTrue(RewardScaling.treasureKillReward(10, rank) >= 10)
      previous = reward
    }
  }

}
