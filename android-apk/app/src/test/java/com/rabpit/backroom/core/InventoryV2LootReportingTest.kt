package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryV2LootReportingTest {
  private fun command(state: GameState, sourceId: String, itemId: String = "loot-item"): LootGrantCommand {
    val item = ItemStack(
      itemId = itemId,
      name = itemId,
      quantity = 1,
      metadata = mapOf(
        "loot.origin" to LootOrigin.ENTITY_DROP.name,
        "loot.sourceId" to sourceId,
        "loot.turnId" to state.turn.currentTurnId
      )
    )
    return LootGrantCommand(
      commandId = "loot:$sourceId",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      origin = LootOrigin.ENTITY_DROP,
      sourceId = sourceId,
      item = item
    )
  }

  @Test fun absentMarkerIsNeverReportedAsGranted() {
    assertFalse(LootEngine.wasGrantCommitted(GameState.initial(), "entity:missing"))
  }

  @Test fun successfulGrantIsReportedOnlyAfterAuthoritativeCommit() {
    val state = GameState.initial()
    val result = StateReducer.execute(state, command(state, "entity:encounter:hound"))
    assertTrue(result.applied)
    assertTrue(LootEngine.wasGrantCommitted(result.state, "entity:encounter:hound"))
  }

  @Test fun capacityLossMarkerIsNotReportedAsGranted() {
    val base = GameState.initial()
    val full = (1..14).associate { index ->
      val id = "existing-$index"
      id to ItemStack(id, id)
    }
    val state = base.copy(inventories = base.inventories + (KAI_ID to InventoryState(KAI_ID, full)))
    val result = StateReducer.execute(state, command(state, "entity:encounter:clump", "overflow-item"))

    assertTrue(result.applied)
    assertTrue(result.state.metadata.getValue("loot.processed.entity:encounter:clump").startsWith("lost:"))
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("overflow-item"))
    assertFalse(LootEngine.wasGrantCommitted(result.state, "entity:encounter:clump"))
  }
}
