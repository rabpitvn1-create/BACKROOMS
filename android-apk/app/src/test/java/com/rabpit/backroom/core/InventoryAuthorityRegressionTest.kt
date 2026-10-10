package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryAuthorityRegressionTest {
  @Test fun bandagePickupDoesNotRunContentUseValidation() {
    val state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "world-bandage-pickup",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = BANDAGE_ID,
      itemName = "Băng gạc",
      quantity = 1,
      metadata = mapOf("worldInstanceId" to "world:bandage:1", "itemOrigin" to "WORLD", "omnivaultOriginal" to "true")
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    assertTrue(result.events.contains("inventory_pickup"))
    val bandage = result.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID)
    assertEquals(ContentState.NONE, bandage.contentState)
    assertEquals("true", bandage.metadata["consumable"])
  }

  @Test fun transferFailureLeavesBothInventoriesUntouched() {
    var state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val beforeKai = state.inventories[KAI_ID]
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "bad-transfer",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = "missing-character",
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.TRANSFER,
      itemId = "not-owned",
      itemName = "Không tồn tại",
      quantity = 1
    ))
    assertFalse(result.applied)
    assertEquals(state, result.state)
    assertEquals(beforeKai, result.state.inventories[KAI_ID])
  }

  @Test fun useRequiresOwnershipBeforeAnySuccessEvent() {
    val state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "use-missing-bandage",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = BANDAGE_ID,
      itemName = "Băng gạc",
      quantity = 1
    ))
    assertFalse(result.applied)
    assertEquals("item_not_owned", result.validation.reason)
    assertTrue(result.events.isEmpty())
  }
}
