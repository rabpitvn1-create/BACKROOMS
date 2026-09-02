package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityLootPlusThreeTest {
  private class SequenceRng(private vararg val values: Int) : LootRng {
    var calls = 0
    override fun nextInt(bound: Int): Int = values[calls++].mod(bound)
  }

  @Test fun everyDefeatDropsExactlyOneCatalogItemAndAutoPicksItUp() {
    val rng = SequenceRng(6)
    val result = EntityLootEngine.onDefeat(GameState.initial(), "guaranteed", rng)
    val itemId = result.world.getValue("entityLootRolled:guaranteed")
    assertEquals(100, EntityLootEngine.dropChancePercent(result))
    assertEquals(1, rng.calls)
    assertTrue(itemId in ItemCatalog.ids)
    assertFalse(result.world.containsKey("entityLoot:guaranteed"))
    val stack = result.inventories.getValue(KAI_ID).items.getValue(itemId)
    assertEquals(1, stack.quantity)
    assertEquals("ENTITY_DROP", stack.metadata["acquisitionSource"])
  }

  @Test fun sameDefeatIdCannotRerollOrDuplicateInventory() {
    val rng = SequenceRng(7)
    val first = EntityLootEngine.onDefeat(GameState.initial(), "same-defeat", rng)
    val itemId = first.world.getValue("entityLootRolled:same-defeat")
    val quantity = first.inventories.getValue(KAI_ID).items.getValue(itemId).quantity
    val duplicate = EntityLootEngine.onDefeat(first, "same-defeat", LootRng { fail("must not reroll"); 0 })
    assertEquals(first, duplicate)
    assertEquals(quantity, duplicate.inventories.getValue(KAI_ID).items.getValue(itemId).quantity)
  }

  @Test fun differentDefeatIdsProduceTwoAcquisitions() {
    val first = EntityLootEngine.onDefeat(GameState.initial(), "defeat-a", SequenceRng(8))
    val second = EntityLootEngine.onDefeat(first, "defeat-b", SequenceRng(8))
    val itemId = second.world.getValue("entityLootRolled:defeat-a")
    assertEquals(itemId, second.world.getValue("entityLootRolled:defeat-b"))
    assertEquals(2, second.inventories.getValue(KAI_ID).items.getValue(itemId).quantity)
  }
}
