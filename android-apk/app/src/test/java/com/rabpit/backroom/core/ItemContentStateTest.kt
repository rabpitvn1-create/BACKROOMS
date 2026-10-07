package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  @Test fun contentStateOnlySupportsWholeUnits() {
    assertArrayEquals(arrayOf(ContentState.NONE), ContentState.values())
  }
  private fun grant(name: String, quantity: Int = 1) = ItemCommand(
    "grant-water", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
    operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = name, quantity = quantity
  )
  private fun use(id: String = "water", quantity: Int = 1) = ItemCommand(
    "use-water", "TURN_1", KAI_ID, source = CommandSource.UI,
    operation = ItemCommand.Operation.USE, itemId = id, itemName = id, quantity = quantity
  )
  @Test fun usingBottleConsumesWholeUnitWithoutCreatingLowOrEmptyVariants() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước")).state
    val result = StateReducer.execute(granted, use())
    assertTrue(result.applied)
    assertEquals(granted.inventories.getValue(KAI_ID).items.keys - "water", result.state.inventories.getValue(KAI_ID).items.keys)
    assertTrue("item_consumed" in result.events)
  }
  @Test fun usingTwoStackMembersLeavesOnlyTheUnusedWholeUnit() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước", 3)).state
    val result = StateReducer.execute(granted, use(quantity = 2))
    assertTrue(result.applied)
    val items = result.state.inventories.getValue(KAI_ID).items
    assertEquals(granted.inventories.getValue(KAI_ID).items.keys, items.keys)
    assertEquals(1, items.getValue("water").quantity)
    assertEquals(ContentState.NONE, items.getValue("water").contentState)
  }
  @Test fun fractionalAmountsRemainRejected() {
    for (name in listOf("Chai nước 200ml", "Chai nước một nửa")) {
      val result = StateReducer.execute(GameState.initial(), grant(name))
      assertFalse(result.applied)
      assertEquals("precise_content_amount_forbidden", result.validation.reason)
    }
  }
  @Test fun normalizationKeepsStableIdAndStripsContentBookkeeping() {
    val item = ItemContentRules.normalize(ItemStack("bottle", "Chai nước", metadata = mapOf("contentState" to "LOW", "remainingContent" to "một ít", "contentPercent" to "50")))
    assertEquals("bottle", item.itemId)
    assertEquals(ContentState.NONE, item.contentState)
    assertFalse(item.metadata.containsKey("contentState"))
    assertFalse(item.metadata.containsKey("remainingContent"))
    assertFalse(item.metadata.containsKey("contentPercent"))
    assertNull(ItemContentRules.nextAfterUse(item))
  }
  @Test fun reusableToolDoesNotLoseQuantityOrProduceContainerVariant() {
    val granted = StateReducer.execute(GameState.initial(), grant("Dụng cụ")).state
    val result = StateReducer.execute(granted, use())
    assertTrue(result.applied)
    assertEquals(granted.inventories, result.state.inventories)
  }
  @Test fun restoreRemainsNarrativeOnlyAndCannotManufactureItems() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước")).state
    val result = StateReducer.execute(granted, OmnivaultCommand("restore", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = OmnivaultCommand.Operation.RESTORE, itemId = "water", itemName = "Chai nước", timestampEpochMs = 1000L))
    assertFalse(result.applied)
    assertEquals("restore_narrative_only", result.validation.reason)
    assertEquals(granted.inventories, result.state.inventories)
  }
}
