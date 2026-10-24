package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityExpRewardsTest {
  @Test fun entityVictoryAwardsOnceAcrossRepeatedRequests() {
    val initial = GameState.initial()
    val once = EntityExpRewards.award(initial, "battle_1", "hound")
    assertTrue(once.amount > 0)
    assertFalse(once.alreadyAwarded)
    val twice = EntityExpRewards.award(once.state, "battle_1", "hound")
    assertEquals(0, twice.amount)
    assertTrue(twice.alreadyAwarded)
    assertEquals(once.state, twice.state)
  }

  @Test fun rewardsNeverAffectCoreUpgradeCurrency() {
    val original = GameState.initial()
    val before = CharacterProgressionCore.coreCount(original)
    val after = EntityExpRewards.award(original, "battle_2", "hound").state
    assertEquals(before, CharacterProgressionCore.coreCount(after))
  }

  @Test fun largeRewardsCapAtMaximumLevelWithoutOverflow() {
    val original = GameState.initial()
    val result = EntityExpRewards.grantExp(original, CAO_MINH_ID, Int.MAX_VALUE)
    assertEquals(EntityExpRewards.MAX_LEVEL, EntityExpRewards.progression(result.state, CAO_MINH_ID).level)
    assertEquals(0, EntityExpRewards.progression(result.state, CAO_MINH_ID).exp)
  }

  @Test fun unrecognizedCharacterCannotBeGrantedExp() {
    val original = GameState.initial()
    val result = EntityExpRewards.grantExp(original, "iris", 500)
    assertEquals(original, result.state)
    assertEquals(0, result.amount)
  }
}
