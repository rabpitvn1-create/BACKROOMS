package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/** Item quantities are whole units; contents never split an item into variants. */
class ItemContentStateTest {
  private fun grant(name: String, quantity: Int = 1, metadata: Map<String, String> = emptyMap()) = ItemCommand(
    "grant:$name:$quantity", "TURN_1", CAO_MINH_ID, source = CommandSource.SYSTEM,
    operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = name,
    quantity = quantity, metadata = metadata
  )
  private fun use(id: String = "water", quantity: Int = 1, commandId: String = "use:1") = ItemCommand(
    commandId, "TURN_1", CAO_MINH_ID, source = CommandSource.UI,
    operation = ItemCommand.Operation.USE, itemId = id, itemName = id, quantity = quantity
  )

  @Test fun usingBottleConsumesOneWholeItemWithoutCreatingAContainerVariant() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước")).state
    assertEquals(ContentState.NONE, granted.inventories.getValue(CAO_MINH_ID).items.getValue("water").contentState)

    val used = StateReducer.execute(granted, use())
    assertTrue(used.validation.reason, used.applied)
    val items = used.state.inventories.getValue(CAO_MINH_ID).items
    assertFalse(items.containsKey("water"))
    assertFalse(items.keys.any { it.startsWith("water-bottle:") || it.contains(":low") || it.contains(":empty") })
    assertTrue("item_consumed" in used.events)

    val again = StateReducer.execute(used.state, use(commandId = "use:2"))
    assertFalse(again.applied)
    assertEquals("item_not_owned", again.validation.reason)
  }

  @Test fun stackUseOnlyDecrementsRequestedWholeUnits() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước", 3)).state
    val used = StateReducer.execute(granted, use(quantity = 2))
    assertTrue(used.validation.reason, used.applied)
    val items = used.state.inventories.getValue(CAO_MINH_ID).items
    assertEquals(1, items.getValue("water").quantity)
    assertEquals(1, items.keys.count { it == "water" })
    assertFalse(items.keys.any { it.startsWith("water-bottle:") })

    val last = StateReducer.execute(used.state, use(commandId = "use:2"))
    assertTrue(last.applied)
    assertFalse(last.state.inventories.getValue(CAO_MINH_ID).items.containsKey("water"))
  }

  @Test fun normalizingAnItemKeepsIdentityAndRemovesLegacyContentMetadata() {
    val item = ItemContentRules.normalize(ItemStack("water", "Chai nước",
      metadata = mapOf("contentState" to "LOW", "remainingContent" to "ít",
        "contentPercent" to "50", "physiologyEffect" to "WATER")))
    assertEquals("water", item.itemId)
    assertEquals(ContentState.NONE, item.contentState)
    assertTrue(item.metadata["consumedOnUse"].equals("true", true))
    assertFalse(item.metadata.containsKey("contentState"))
    assertFalse(item.metadata.containsKey("remainingContent"))
    assertFalse(item.metadata.containsKey("contentPercent"))
    assertNull(ItemContentRules.nextAfterUse(item))
  }

  @Test fun reusableToolsRemainUsableAndPersistentContainersHonorExplicitPolicy() {
    val granted = StateReducer.execute(GameState.initial(), grant("Dụng cụ")).state
    val used = StateReducer.execute(granted, use())
    assertTrue(used.applied)
    assertEquals(granted.inventories, used.state.inventories)
    assertNotNull(ItemContentRules.nextAfterUse(granted.inventories.getValue(CAO_MINH_ID).items.getValue("water")))

    val persistent = StateReducer.execute(GameState.initial(),
      grant("Chai nước", metadata = mapOf("containerPersistent" to "true"))).state
    val held = StateReducer.execute(persistent, use())
    assertTrue(held.applied)
    assertEquals(1, held.state.inventories.getValue(CAO_MINH_ID).items.getValue("water").quantity)
  }

  @Test fun fractionalAmountsRemainRejected() {
    for (name in listOf("Chai nước 200ml", "Chai nước một nửa")) {
      val result = StateReducer.execute(GameState.initial(), grant(name))
      assertFalse(result.applied)
      assertEquals("precise_content_amount_forbidden", result.validation.reason)
    }
  }

  @Test fun restoreRemainsNarrativeOnlyAndCannotManufactureItems() {
    val granted = StateReducer.execute(GameState.initial(), grant("Chai nước")).state
    val result = StateReducer.execute(granted, VanKhoGioiChiCommand(
      "restore", "TURN_1", CAO_MINH_ID, source = CommandSource.UI,
      operation = VanKhoGioiChiCommand.Operation.RESTORE, itemId = "water",
      itemName = "Chai nước", timestampEpochMs = 1000L))
    assertFalse(result.applied)
    assertEquals("restore_narrative_only", result.validation.reason)
    assertEquals(granted.inventories, result.state.inventories)
  }
}
