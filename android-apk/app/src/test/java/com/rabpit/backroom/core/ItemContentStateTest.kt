package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  private fun seeded(name: String = "Chai nước", quantity: Int = 1) =
    TestInventoryFixtures.place(GameState.initial(), "water", name, quantity)

  private fun use(itemName: String = "water", quantity: Int = 1) = ItemCommand(
    "use-water", "TURN_1", KAI_ID, source = CommandSource.UI,
    operation = ItemCommand.Operation.USE, itemId = "water", itemName = itemName, quantity = quantity
  )

  @Test fun usingBottleConsumesWholeUnitWithoutVariants() {
    val granted = seeded()
    val result = StateReducer.execute(granted, use())
    assertTrue(result.applied)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("water"))
  }

  @Test fun partialStackUsePreservesOnlyRemainingUnits() {
    val granted = seeded(quantity = 3)
    val result = StateReducer.execute(granted, use(quantity = 2))
    assertTrue(result.applied)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
  }

  @Test fun fractionalContentAmountsAreRejectedAtUiBoundary() {
    val granted = seeded()
    for (name in listOf("Chai nước 200ml", "Chai nước một nửa")) {
      val result = StateReducer.execute(granted, use(itemName = name))
      assertFalse(result.applied)
      assertEquals("precise_content_amount_forbidden", result.validation.reason)
    }
  }

  @Test fun typeIdAndQuantityDoNotCreateContentStates() {
    val item = ItemContentRules.normalize(ItemStack("bottle", "Chai nước", 3))
    assertEquals("bottle", item.itemId)
    assertEquals(3, item.quantity)
    assertNull(ItemContentRules.nextAfterUse(item))
  }

  @Test fun reusableToolRemainsInInventory() {
    val granted = seeded(name = "Dụng cụ")
    val result = StateReducer.execute(granted, use())
    assertTrue(result.applied)
    assertEquals(granted.inventories, result.state.inventories)
  }
}
