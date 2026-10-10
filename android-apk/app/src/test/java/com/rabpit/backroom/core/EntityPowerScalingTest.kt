package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityPowerScalingTest {
  private fun levelState(level: Int): GameState = GameState.initial().copy(
    world = mapOf(
      "levelJson" to JSONObject()
        .put("number", level)
        .put("name", "Level $level")
        .toString()
    )
  )

  @Test fun rankUnitAndLegacyRanksArePinnedLiterally() {
    assertEquals(1_000_000L, EntityPowerScaling.RANK_PER_FULL_LEVEL)
    val expected = longArrayOf(0L, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L, 5_000_000L, 6_000_000L)
    expected.forEachIndexed { level, rank -> assertEquals(rank, EntityPowerScaling.rankForLevel(level)) }
  }

  @Test fun linearFullLevelsAreOneHundredTenPercentagePointsPerLevel() {
    val base = 1_000
    assertEquals(1_000, EntityPowerScaling.scale(base, 0L))
    assertEquals(1_100, EntityPowerScaling.scale(base, 1_000_000L))
    assertEquals(1_200, EntityPowerScaling.scale(base, 2_000_000L))
    assertEquals(1_300, EntityPowerScaling.scale(base, 3_000_000L))
  }

  @Test fun fractionalRanksKeepFixedPointPrecision() {
    assertEquals(1_050, EntityPowerScaling.scale(1_000, 500_000L))
    assertEquals(1_105, EntityPowerScaling.scale(1_000, 1_050_000L))
    assertEquals(1_150, EntityPowerScaling.scale(1_000, 1_500_000L))
  }

  @Test fun currentLevelZeroCombatBalanceIsUnchangedAndHigherLevelsScaleFromIt() {
    val hound0 = CombatRuntime.active(CombatRuntime.start(levelState(0), "hound"))!!
    val hound2 = CombatRuntime.active(CombatRuntime.start(levelState(2), "hound"))!!
    assertEquals(110, hound0.entityMaxHp)
    assertEquals(132, hound2.entityMaxHp)
    assertEquals(0L, hound0.progressionRank)
    assertEquals(2_000_000L, hound2.progressionRank)
  }

  @Test fun activeEncounterKeepsItsRankSnapshotWhenWorldLevelChanges() {
    val started = CombatRuntime.start(levelState(2), "hound")
    val before = CombatRuntime.active(started)!!
    val movedWorld = started.copy(world = levelState(6).world)
    val after = CombatRuntime.active(movedWorld)!!
    assertEquals(2_000_000L, before.progressionRank)
    assertEquals(before.progressionRank, after.progressionRank)
    assertEquals(before.entityMaxHp, after.entityMaxHp)
  }

  @Test fun diepMinhUsesTheSameLinearHpScaleWithoutChangingLevelZeroBossBalance() {
    val boss0 = CombatRuntime.active(CombatRuntime.start(levelState(0), "diep_minh"))!!
    val boss1 = CombatRuntime.active(CombatRuntime.start(levelState(1), "diep_minh"))!!
    assertEquals(2999, boss0.entityMaxHp)
    assertEquals(3299, boss1.entityMaxHp)
  }

  @Test(expected = IllegalArgumentException::class)
  fun explicitUnknownLevelFailsClosed() {
    EntityPowerScaling.rankFor(levelState(7))
  }
}
