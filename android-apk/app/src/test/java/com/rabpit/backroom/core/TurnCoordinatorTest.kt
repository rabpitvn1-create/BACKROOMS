package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class TurnCoordinatorTest {
  @Test fun pendingTurnSurvivesAndCannotCommitTwice() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_184", "story grant water")
    assertEquals("TURN_184", TurnCoordinator.recover(created.state)?.turnId)
    val command = ItemCommand("TURN_184:0", "TURN_184", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = "Water")
    val committed = TurnCoordinator.commit(created.state, listOf(command))
    assertNull(committed.error)
    assertEquals(1, committed.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    assertNull(TurnCoordinator.recover(committed.state))
    val retry = TurnCoordinator.createPending(committed.state, "TURN_184", "story grant water")
    assertEquals("turn_already_completed", retry.error)
    assertEquals(1, retry.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
  }

  @Test fun failedBatchIsAtomic() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_2", "multi")
    val grant = ItemCommand("c1", "TURN_2", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = "Water")
    val invalid = ItemCommand("c2", "TURN_2", KAI_ID, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DROP, itemId = "gun", itemName = "Gun")
    val result = TurnCoordinator.commit(created.state, listOf(grant, invalid))
    assertEquals("insufficient_item_quantity", result.error)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("water"))
    assertNotNull(TurnCoordinator.recover(result.state))
  }
}
