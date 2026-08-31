package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class JaneDoeEntityFinalizerTest {
  private class SequenceRng(private vararg val values: Int) : LootRng {
    var calls = 0
    override fun nextInt(bound: Int): Int = values[calls++].mod(bound)
  }

  @Test fun legacyKeyStartsJaneDoeAtExact2323Hp() {
    val active = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "john_doe"))!!
    assertEquals("Jane Doe", active.entityName)
    assertEquals(2323, active.entityMaxHp)
    assertEquals(2323, active.entityHp)
  }

  @Test fun lilithCoreCanAwakenAndDoesNotStack() {
    var awakened: GameState? = null
    for (counter in 0..700) {
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (result.reply.contains("Lilith Core tự kích hoạt")) {
        awakened = result.state
        break
      }
    }
    assertNotNull("Expected the 10% Lilith Core proc to be reachable", awakened)
    assertEquals(2440, CombatRuntime.active(awakened!!)!!.entityMaxHp)
    assertEquals("true", awakened!!.metadata["combat.janeDoeLilithCoreActive"])

    val next = CombatRuntime.resolve(awakened!!, "EXECUTE", "Cả Party cùng né tránh")
    assertEquals(2440, CombatRuntime.active(next.state)!!.entityMaxHp)
  }

  @Test fun allThreeBowSkillsAreReachable() {
    val seen = mutableSetOf<String>()
    for (counter in 0..2500) {
      if (seen.size == 3) break
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (result.reply.contains("Moonpiercer:")) seen += "moon"
      if (result.reply.contains("Thorn Volley:")) seen += "volley"
      if (result.reply.contains("Shadow Pin:")) seen += "pin"
    }
    assertEquals(setOf("moon", "volley", "pin"), seen)
  }

  @Test fun janeDoeDropsTwoRandomItemsAndDuplicateDefeatIsIdempotent() {
    val initial = GameState.initial()
    val before = initial.inventories.getValue(KAI_ID).items.values.sumOf { it.quantity }
    val defeatId = "turn-77:john_doe:9981"
    val rng = SequenceRng(3, 7)
    val dropped = EntityLootEngine.onDefeat(initial, defeatId, rng)
    val after = dropped.inventories.getValue(KAI_ID).items.values.sumOf { it.quantity }

    assertEquals(2, rng.calls)
    assertEquals(before + 2, after)
    assertNotNull(dropped.world["entityLootRolled:$defeatId"])
    assertNotNull(dropped.world["entityLootRolled:$defeatId:2"])
    assertNull(dropped.world["entityLoot:$defeatId"])
    assertNull(dropped.world["entityLoot:$defeatId:2"])

    val duplicate = EntityLootEngine.onDefeat(dropped, defeatId, LootRng { fail("must not reroll"); 0 })
    assertEquals(dropped, duplicate)
  }

  @Test fun ordinaryEntityStillDropsExactlyOneRandomItem() {
    val initial = GameState.initial()
    val before = initial.inventories.getValue(KAI_ID).items.values.sumOf { it.quantity }
    val rng = SequenceRng(2)
    val dropped = EntityLootEngine.onDefeat(initial, "turn-1:hound:2", rng)
    val after = dropped.inventories.getValue(KAI_ID).items.values.sumOf { it.quantity }
    assertEquals(1, rng.calls)
    assertEquals(before + 1, after)
  }
}
