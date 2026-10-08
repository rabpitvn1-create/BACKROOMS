package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemRegistryTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  @Test fun canonicalWorldItemsHaveStableCoreIds() {
    assertEquals("Almond Water", ItemRegistry.displayName(ItemRegistry.ITEM_ALMOND_WATER_ID))
    assertEquals("Băng gạc", ItemRegistry.displayName(ItemRegistry.ITEM_BANDAGE_ID))
    assertEquals("Thuốc sát trùng", ItemRegistry.displayName(ItemRegistry.ITEM_ANTISEPTIC_ID))
    assertEquals("Greek Fire", ItemRegistry.displayName(ItemRegistry.ITEM_GREEK_FIRE_ID))
    assertEquals("Liquid Pain", ItemRegistry.displayName(ItemRegistry.ITEM_LIQUID_PAIN_ID))
    assertTrue(ItemRegistry.contains(KAI_WHITE_WRAITH_ID))
    assertFalse(ItemRegistry.contains("ai-invented-item"))
  }

  @Test fun authoritativeEntityPickupRejectsUnknownId() {
    val result = InventoryEngine.execute(fresh(), ItemCommand(
      commandId = "entity:unknown",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = "ai-invented-item",
      itemName = "AI Invented Item",
      metadata = mapOf("itemOrigin" to "ENTITY")
    ))
    assertFalse(result.applied)
    assertEquals("unknown_item_id", result.validation.reason)
  }

  @Test fun registryControlsStoredNameForAuthoritativePickup() {
    val result = InventoryEngine.execute(fresh(), ItemCommand(
      commandId = "entity:almond",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = ItemRegistry.ITEM_ALMOND_WATER_ID,
      itemName = "Tên do model tự bịa",
      metadata = mapOf("itemOrigin" to "ENTITY")
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    val item = result.state.inventories.getValue(KAI_ID).items.getValue(ItemRegistry.ITEM_ALMOND_WATER_ID)
    assertEquals("Almond Water", item.name)
    assertEquals(ItemRegistry.ITEM_ALMOND_WATER_ID, item.metadata["registryItemId"])
  }

  @Test fun chestPickupRequiresStableChestId() {
    val result = InventoryEngine.execute(fresh(), ItemCommand(
      commandId = "chest:missing-id",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = ItemRegistry.ITEM_BANDAGE_ID,
      itemName = "Băng gạc",
      metadata = mapOf("itemOrigin" to "CHEST")
    ))
    assertFalse(result.applied)
    assertEquals("chest_source_missing", result.validation.reason)
  }
}
