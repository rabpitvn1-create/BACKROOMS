package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  private fun grant(name: String, quantity: Int = 1) = ItemCommand(
    "grant-water", "TURN_1", CAO_MINH_ID, source = CommandSource.SYSTEM,
    operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = name, quantity = quantity
  )
  private fun use(id: String = ItemContentRules.variantId("water-bottle", ContentState.FULL), quantity: Int = 1) = ItemCommand(
    "use-water:$id", "TURN_1", CAO_MINH_ID, source = CommandSource.UI,
    operation = ItemCommand.Operation.USE, itemId = id, itemName = id, quantity = quantity
  )
  @Test fun usingFullBottleProducesLowThenEmptyVariant() {
    val fullId = ItemContentRules.variantId("water-bottle", ContentState.FULL)
    val lowId = ItemContentRules.variantId("water-bottle", ContentState.LOW)
    val emptyId = ItemContentRules.variantId("water-bottle", ContentState.EMPTY)
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước")).state
    assertTrue(fullId in granted.inventories.getValue(CAO_MINH_ID).items)

    val used = StateReducer.execute(granted, use())
    assertTrue(used.validation.reason, used.applied)
    assertFalse(fullId in used.state.inventories.getValue(CAO_MINH_ID).items)
    assertEquals(ContentState.LOW, used.state.inventories.getValue(CAO_MINH_ID).items.getValue(lowId).contentState)
    assertTrue("item_content_reduced" in used.events)

    val emptied = StateReducer.execute(used.state, use(id = lowId))
    assertTrue(emptied.validation.reason, emptied.applied)
    assertEquals(ContentState.EMPTY, emptied.state.inventories.getValue(CAO_MINH_ID).items.getValue(emptyId).contentState)
    assertTrue("item_content_emptied" in emptied.events)
    val cannotUseEmpty = StateReducer.execute(emptied.state, use(id = emptyId))
    assertFalse(cannotUseEmpty.applied)
    assertEquals("item_content_empty", cannotUseEmpty.validation.reason)
  }

  @Test fun usingTwoStackMembersLeavesOneFullAndTwoLowUnits() {
    val fullId = ItemContentRules.variantId("water-bottle", ContentState.FULL)
    val lowId = ItemContentRules.variantId("water-bottle", ContentState.LOW)
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước", 3)).state
    val result = StateReducer.execute(granted, use(quantity = 2))
    assertTrue(result.validation.reason, result.applied)
    val items = result.state.inventories.getValue(CAO_MINH_ID).items
    assertEquals(1, items.getValue(fullId).quantity)
    assertEquals(2, items.getValue(lowId).quantity)
    assertEquals(ContentState.LOW, items.getValue(lowId).contentState)
  }
  @Test fun fractionalAmountsRemainRejected() {
    for (name in listOf("Chai nước 200ml", "Chai nước một nửa")) {
      val result = StateReducer.execute(GameState.initial(), grant(name))
      assertFalse(result.applied)
      assertEquals("precise_content_amount_forbidden", result.validation.reason)
    }
  }
  @Test fun normalizationUsesStableVariantIdentityAndStripsUntrustedContentAmounts() {
    val item = ItemContentRules.normalize(ItemStack("bottle", "Chai nước",
      metadata = mapOf("contentState" to "LOW", "remainingContent" to "một ít", "contentPercent" to "50")))
    assertEquals(ItemContentRules.variantId("water-bottle", ContentState.LOW), item.itemId)
    assertEquals(ContentState.LOW, item.contentState)
    assertEquals("LOW", item.metadata["contentState"])
    assertFalse(item.metadata.containsKey("remainingContent"))
    assertFalse(item.metadata.containsKey("contentPercent"))
    val empty = ItemContentRules.nextAfterUse(item)
    assertNotNull(empty)
    assertEquals(ContentState.EMPTY, empty!!.contentState)
    assertEquals(ItemContentRules.variantId("water-bottle", ContentState.EMPTY), empty.itemId)
  }
  @Test fun reusableToolDoesNotLoseQuantityOrProduceContainerVariant() {
    val granted = StateReducer.execute(GameState.initial(), grant("Dụng cụ")).state
    val result = StateReducer.execute(granted, use())
    assertTrue(result.applied)
    assertEquals(granted.inventories, result.state.inventories)
  }
  @Test fun restoreRemainsNarrativeOnlyAndCannotManufactureItems() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước")).state
    val result = StateReducer.execute(granted, VanKhoGioiChiCommand("restore", "TURN_1", CAO_MINH_ID, source = CommandSource.UI,
      operation = VanKhoGioiChiCommand.Operation.RESTORE, itemId = "water", itemName = "Chai nước", timestampEpochMs = 1000L))
    assertFalse(result.applied)
    assertEquals("restore_narrative_only", result.validation.reason)
    assertEquals(granted.inventories, result.state.inventories)
  }
}
