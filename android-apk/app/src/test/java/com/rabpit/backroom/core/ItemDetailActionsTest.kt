package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/** Item actions are dispatched through StateReducer in this runtime generation. */
class ItemDetailActionsTest {
  private val bandageId = "bandage"

  private fun fresh(): GameState {
    val initial = GameState.initial()
    val owned = ItemStack(bandageId, "Băng gạc", 3,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true"))
    return initial.copy(
      party = initial.party.copy(memberIds = listOf(CAO_MINH_ID, LUC_TRAM_ID)),
      inventories = initial.inventories + (CAO_MINH_ID to
        initial.inventories.getValue(CAO_MINH_ID).copy(items =
          initial.inventories.getValue(CAO_MINH_ID).items + (bandageId to owned)))
    )
  }

  private fun command(op: ItemCommand.Operation, quantity: Int = 1,
                      actor: String = CAO_MINH_ID, target: String? = null,
                      item: String = bandageId, id: String = "UI:ITEM") =
    ItemCommand(id, null, actor, targetId = target, source = CommandSource.UI,
      operation = op, itemId = item, itemName = if (item == bandageId) "Băng gạc" else item,
      quantity = quantity)

  @Test fun consumingOwnedItemDecrementsExactlyOne() {
    val result = StateReducer.execute(fresh(), command(ItemCommand.Operation.USE))
    assertTrue(result.validation.reason, result.applied)
    assertEquals(2, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(bandageId).quantity)
    assertTrue(result.state.turn.executedCommandIds.contains("UI:ITEM"))
  }

  @Test fun transferMovesQuantityBetweenOwners() {
    val result = StateReducer.execute(fresh(),
      command(ItemCommand.Operation.TRANSFER, quantity = 2, target = LUC_TRAM_ID))
    assertTrue(result.validation.reason, result.applied)
    assertEquals(1, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(bandageId).quantity)
    assertEquals(2, result.state.inventories.getValue(LUC_TRAM_ID).items.getValue(bandageId).quantity)
  }

  @Test fun followerCanDropOwnItemWithoutChangingLeaderInventory() {
    val initial = fresh().let { it.copy(inventories = it.inventories +
      (LUC_TRAM_ID to InventoryState(LUC_TRAM_ID, mapOf("scrap" to ItemStack("scrap", "Mảnh vụn", 2))))) }
    val result = StateReducer.execute(initial, command(ItemCommand.Operation.DROP,
      actor = LUC_TRAM_ID, item = "scrap"))
    assertTrue(result.validation.reason, result.applied)
    assertEquals(1, result.state.inventories.getValue(LUC_TRAM_ID).items.getValue("scrap").quantity)
    assertEquals(initial.inventories[CAO_MINH_ID], result.state.inventories[CAO_MINH_ID])
  }

  @Test fun invalidCommandsAreAtomic() {
    val invalid = listOf(
      command(ItemCommand.Operation.DROP, quantity = 0),
      command(ItemCommand.Operation.DROP, quantity = -1),
      command(ItemCommand.Operation.DROP, quantity = 4),
      command(ItemCommand.Operation.PICKUP),
      command(ItemCommand.Operation.TRANSFER, target = CAO_MINH_ID),
      command(ItemCommand.Operation.TRANSFER, target = "missing"),
      command(ItemCommand.Operation.DROP, actor = "missing"),
      command(ItemCommand.Operation.USE, item = "missing")
    )
    invalid.forEach { cmd ->
      val initial = fresh()
      val result = StateReducer.execute(initial, cmd)
      assertFalse(cmd.toString(), result.applied)
      assertEquals(initial, result.state)
    }
  }

  @Test fun cannotDropEquippedGearOrTransferIntoFullInventory() {
    val initial = fresh()
    val equipped = StateReducer.execute(initial,
      command(ItemCommand.Operation.DROP, item = CAO_MINH_HUYET_MA_KIEM_ID))
    assertFalse(equipped.applied)
    assertEquals(initial, equipped.state)

    val full = initial.copy(inventories = initial.inventories +
      (LUC_TRAM_ID to InventoryState(LUC_TRAM_ID, (1..4).associate {
        "scrap$it" to ItemStack("scrap$it", "Mảnh $it")
      })))
    val transfer = StateReducer.execute(full,
      command(ItemCommand.Operation.TRANSFER, target = LUC_TRAM_ID))
    assertFalse(transfer.applied)
    assertEquals("inventory_slot_limit", transfer.validation.reason)
    assertEquals(full, transfer.state)
  }

  @Test fun duplicateCommandCannotConsumeItemAgain() {
    val initial = fresh()
    val cmd = command(ItemCommand.Operation.DROP, id = "fixed-id")
    val once = StateReducer.execute(initial, cmd)
    assertTrue(once.applied)
    val again = StateReducer.execute(once.state, cmd)
    assertFalse(again.applied)
    assertTrue(again.duplicate)
    assertEquals(once.state, again.state)
  }

  @Test fun quotedNegatedOrRememberedActionsCannotBecomeCommands() {
    val parser = RuleIntentInterpreter()
    val context = GameContext(fresh())
    listOf("Không nhặt băng gạc", "Cao Minh nhớ lần trước mình dùng băng gạc",
      "Lục Trầm nói: “dùng băng gạc”").forEach { action ->
      assertEquals(GameIntent.NO_ACTION, parser.interpretSync(action, context).candidates.single().intent)
    }
  }
}
