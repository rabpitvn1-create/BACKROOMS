package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExtensibleItemSystemTest {
  @Test fun existingCatalogAndKaiCodexLimitsRemainLocked() {
    assertEquals(11, ItemCatalog.items.size)
    assertEquals(11, ItemCatalog.ids.size)
    assertEquals(
      mapOf(
        ItemCatalog.FLASHLIGHT to OfficialItemType.TOOL,
        ItemCatalog.LIGHTER to OfficialItemType.TOOL,
        ItemCatalog.ALMOND_WATER to OfficialItemType.CONSUMABLE,
        ItemCatalog.CANNED_FOOD to OfficialItemType.CONSUMABLE,
        ItemCatalog.BATTERY to OfficialItemType.CONSUMABLE,
        ItemCatalog.LIGHTER_FUEL to OfficialItemType.CONSUMABLE,
        ItemCatalog.BANDAGE to OfficialItemType.CONSUMABLE,
        ItemCatalog.ANTISEPTIC to OfficialItemType.CONSUMABLE,
        ItemCatalog.PAINKILLER to OfficialItemType.CONSUMABLE,
        ItemCatalog.SARDINES to OfficialItemType.CONSUMABLE,
        ItemCatalog.LA_VIE to OfficialItemType.CONSUMABLE
      ),
      ItemCatalog.items.associate { it.id to it.type }
    )
    assertEquals(3, OmnivaultEngine.MAX_SCAN_SLOTS)
    val initial = GameState.initial()
    assertEquals(ItemCapacity(9, 999), ItemSystem.capacityFor(initial, KAI_ID))
    mapOf(
      "special_companion" to ItemCapacity(6, 20),
      "lucia_gift_inventory" to ItemCapacity(3, 100),
      "an_nhien_food_only" to ItemCapacity(2, 20),
      "normal" to ItemCapacity(2, 2)
    ).forEach { (profile, expected) ->
      val id = "locked-profile:$profile"
      val state = initial.copy(characters = initial.characters + (id to CharacterState(
        id = id, name = id, metadata = mapOf("inventoryProfile" to profile)
      )))
      assertEquals(expected, ItemSystem.capacityFor(state, id))
    }
  }

  @Test fun futureCharacterUsesMetadataWithoutCoreNameBranch() {
    val character = CharacterState(
      id = "future-follower-2040",
      name = "A Character Added Later",
      metadata = mapOf("inventoryMaxTypes" to "7", "inventoryMaxPerType" to "42")
    )
    val state = GameState.initial().copy(
      characters = GameState.initial().characters + (character.id to character),
      inventories = GameState.initial().inventories + (character.id to InventoryState(character.id))
    )
    assertEquals(InventoryProfile(7, 42), InventoryPolicy.profileFor(state, character.id))
  }

  @Test fun futureItemCarriesItsOwnInformationAndCapabilities() {
    val item = ItemStack(
      itemId = "future:signal-crystal",
      name = "Signal Crystal",
      quantity = 3,
      metadata = mapOf(
        "description" to "A data-defined crystal added after the first test build.",
        "itemType" to "TOOL",
        "usable" to "true"
      )
    )
    val inspection = ItemSystem.inspect(item, ownerId = "future-follower")
    assertEquals(3, inspection.quantity)
    assertEquals("A data-defined crystal added after the first test build.", inspection.description)
    assertEquals("future-follower", inspection.ownerId)
    assertTrue(setOf("INSPECT", "USE", "SCAN", "COPY_FROM_SCAN", "TRANSFER", "DROP").all { it in inspection.capabilities })
  }

  @Test fun dropMovesExactQuantityBackIntoWorldLedger() {
    val item = ItemStack("future:rope", "Future Rope", 3, metadata = mapOf("description" to "A rope."))
    val state = GameState.initial().copy(
      world = mapOf("location" to "future-level:9000"),
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(item.itemId to item)))
    )
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "drop-future-rope", turnId = state.turn.currentTurnId, actorId = KAI_ID,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.DROP,
      itemId = item.itemId, itemName = item.name, quantity = 2
    ))
    assertTrue(result.applied)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue(item.itemId).quantity)
    val dropped = JSONObject(result.state.world.getValue("flagsJson")).getJSONArray("worldItems").getJSONObject(0)
    assertEquals(2, dropped.getInt("quantity"))
    assertEquals("future-level:9000", dropped.getString("locationKey"))
    assertTrue(dropped.getBoolean("available"))
  }

  @Test fun copiedItemCannotBecomeAnotherScanSource() {
    val copy = ItemStack(
      "omnivault-copy:future-template",
      "Signal Crystal",
      metadata = mapOf("itemOrigin" to "OMNIVAULT_COPY", "copySourceTemplateId" to "future-template")
    )
    val inspection = ItemSystem.inspect(copy)
    assertFalse("SCAN" in inspection.capabilities)
    assertEquals(3, OmnivaultEngine.MAX_SCAN_SLOTS)
  }

  @Test fun futureContentCompletesPickupInspectScanCopyTransferUseAndDrop() {
    val follower = CharacterState(
      "future:follower:alpha",
      "Follower Added Tomorrow",
      metadata = mapOf("inventoryMaxTypes" to "5", "inventoryMaxPerType" to "50")
    )
    val itemJson = JSONObject()
      .put("id", "future:field-kit")
      .put("name", "Future Field Kit")
      .put("quantity", 1)
      .put("metadata", JSONObject()
        .put("description", "A future data-defined multipurpose kit.")
        .put("itemType", "TOOL")
        .put("usable", "true"))
    val flags = WorldItemLedger.record(null, "future-world:1001", itemJson.toString())
    val worldPickup = requireNotNull(
      WorldItemLedger.consume(flags, "future-world:1001", "nhặt Future Field Kit")
    )

    var state = GameState.initial().copy(
      characters = GameState.initial().characters + (follower.id to follower),
      inventories = GameState.initial().inventories + (follower.id to InventoryState(follower.id)),
      world = mapOf("location" to "future-world:1001", "flagsJson" to worldPickup.flagsJson)
    )
    val record = worldPickup.items.single()
    val pickup = InventoryEngine.execute(state, ItemCommand(
      "future-pickup", state.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = record.itemId, itemName = record.itemName,
      quantity = record.quantity, metadata = record.metadata
    ))
    assertTrue(pickup.applied)
    state = pickup.state
    val original = state.inventories.getValue(KAI_ID).items.getValue(record.itemId)
    assertEquals("A future data-defined multipurpose kit.", ItemSystem.inspect(original, KAI_ID).description)

    val scan = OmnivaultEngine.execute(state, OmnivaultCommand(
      "future-scan", state.turn.currentTurnId, KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.SCAN, itemId = original.itemId, itemName = original.name,
      timestampEpochMs = 1001L
    ))
    assertTrue(scan.applied)
    assertEquals(1, scan.state.omnivault.scanSlots.size)
    state = scan.state

    val copied = OmnivaultEngine.execute(state, OmnivaultCommand(
      "future-copy", state.turn.currentTurnId, KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = original.itemId, itemName = original.name,
      quantity = 2
    ))
    assertTrue(copied.applied)
    state = copied.state
    val copy = state.inventories.getValue(KAI_ID).items.values.single { ItemSystem.isOmnivaultCopy(it) }
    assertEquals(2, copy.quantity)

    val transferred = InventoryEngine.execute(state, ItemCommand(
      "future-transfer", state.turn.currentTurnId, KAI_ID, targetId = follower.id,
      source = CommandSource.RULE, operation = ItemCommand.Operation.TRANSFER,
      itemId = copy.itemId, itemName = copy.name, quantity = 1
    ))
    assertTrue(transferred.applied)
    state = transferred.state
    assertEquals(1, state.inventories.getValue(follower.id).items.getValue(copy.itemId).quantity)

    val used = InventoryEngine.execute(state, ItemCommand(
      "future-use", state.turn.currentTurnId, KAI_ID, source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE, itemId = original.itemId, itemName = original.name
    ))
    assertTrue(used.applied)
    state = used.state

    val dropped = InventoryEngine.execute(state, ItemCommand(
      "future-drop", state.turn.currentTurnId, follower.id, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DROP, itemId = copy.itemId, itemName = copy.name
    ))
    assertTrue(dropped.applied)
    assertFalse(dropped.state.inventories.getValue(follower.id).items.containsKey(copy.itemId))
    val worldItems = JSONObject(dropped.state.world.getValue("flagsJson")).getJSONArray("worldItems")
    assertTrue((0 until worldItems.length()).map { worldItems.getJSONObject(it) }.any {
      it.getString("id") == copy.itemId && it.getBoolean("available")
    })
  }
}
