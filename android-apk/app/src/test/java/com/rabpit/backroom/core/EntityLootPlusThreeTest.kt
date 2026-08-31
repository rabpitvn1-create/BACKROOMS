package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityLootPlusThreeTest {
  private class FixedRng(private val chanceRoll: Int, private val itemRoll: Int = 0) : LootRng {
    override fun nextInt(bound: Int): Int = if (bound == 100) chanceRoll.coerceIn(0, 99) else itemRoll.mod(bound)
  }

  @Test fun firstEntityKillStartsAtTenPercent() {
    val state = GameState.initial()
    assertEquals(10, EntityLootEngine.dropChancePercent(state))
    assertEquals(46, EntityLootEngine.GUARANTEED_KILL)
  }

  @Test fun exactTenPercentBoundaryIsApplied() {
    val state = GameState.initial()

    val success = EntityLootEngine.onDefeat(state, "issue-122-success", FixedRng(9))
    assertNotEquals("NONE", success.world["entityLootRolled:issue-122-success"])
    assertNotNull(success.world["entityLoot:issue-122-success"])

    val failure = EntityLootEngine.onDefeat(state, "issue-122-failure", FixedRng(10))
    assertEquals("NONE", failure.world["entityLootRolled:issue-122-failure"])
    assertNull(failure.world["entityLoot:issue-122-failure"])
    assertEquals(12, EntityLootEngine.dropChancePercent(failure))
  }

  @Test fun duplicateDefeatIdCannotRerollLoot() {
    val state = EntityLootEngine.onDefeat(GameState.initial(), "same-defeat", FixedRng(10))
    val rerolled = EntityLootEngine.onDefeat(state, "same-defeat", FixedRng(0))
    assertEquals(state, rerolled)
    assertEquals(12, EntityLootEngine.dropChancePercent(rerolled))
  }
}
