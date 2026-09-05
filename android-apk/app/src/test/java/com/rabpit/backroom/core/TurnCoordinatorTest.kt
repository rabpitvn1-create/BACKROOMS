package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class TurnCoordinatorTest {
  private fun loot(commandId: String, turnId: String, itemId: String, quantity: Int = 1): LootGrantCommand {
    val sourceId = "test:$commandId"
    val item = ItemStack(
      itemId = itemId,
      name = itemId,
      quantity = quantity,
      metadata = mapOf(
        "loot.origin" to LootOrigin.EXPLORE_LOOT.name,
        "loot.sourceId" to sourceId,
        "loot.turnId" to turnId
      )
    )
    return LootGrantCommand(
      commandId = commandId,
      turnId = turnId,
      actorId = KAI_ID,
      origin = LootOrigin.EXPLORE_LOOT,
      sourceId = sourceId,
      item = item,
      quantity = quantity
    )
  }

  @Test fun pendingTurnSurvivesAndCannotCommitTwice() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_184", "story grant water")
    assertEquals("TURN_184", TurnCoordinator.recover(created.state)?.turnId)
    val command = loot("TURN_184:0", "TURN_184", "water")
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
    val grant = loot("c1", "TURN_2", "water")
    val invalid = ItemCommand(
      "c2", "TURN_2", KAI_ID, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DISCARD, itemId = "gun", itemName = "Gun"
    )
    val result = TurnCoordinator.commit(created.state, listOf(grant, invalid))
    assertEquals("item_not_owned", result.error)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("water"))
    assertFalse(LootEngine.wasGrantCommitted(result.state, "test:c1"))
    assertNotNull(TurnCoordinator.recover(result.state))
  }

  @Test fun gameplayAndTimeCommitAtomically() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_7", "đi 30 phút rồi dùng rope")
    val grant = loot("TURN_7:grant", "TURN_7", "rope")
    val time = TimeAdvanceCommand(
      "TURN_7:SYSTEM:TIME", "TURN_7", KAI_ID, source = CommandSource.SYSTEM,
      minutes = 30, reason = "player_action"
    )

    val result = TurnCoordinator.commit(created.state, listOf(grant, time))

    assertNull(result.error)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("rope").quantity)
    assertTrue(LootEngine.wasGrantCommitted(result.state, "test:TURN_7:grant"))
    assertEquals(30L, result.state.time.elapsedSubjectiveMinutes)
    assertEquals(30, result.state.time.lastAdvanceMinutes)
    assertEquals("player_action", result.state.time.lastAdvanceReason)
    assertTrue("TURN_7:SYSTEM:TIME" in result.state.turn.executedCommandIds)
    assertTrue("TURN_7" in result.state.turn.completedTurnIds)
  }

  @Test fun failedGameplayRollsBackTimeAdvance() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_8", "đi 10 phút rồi bỏ vật không có")
    val invalid = ItemCommand(
      "TURN_8:discard", "TURN_8", KAI_ID, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DISCARD, itemId = "missing", itemName = "Missing"
    )
    val time = TimeAdvanceCommand(
      "TURN_8:SYSTEM:TIME", "TURN_8", KAI_ID, source = CommandSource.SYSTEM,
      minutes = 10, reason = "player_action"
    )

    val result = TurnCoordinator.commit(created.state, listOf(invalid, time))

    assertEquals("item_not_owned", result.error)
    assertEquals(0L, result.state.time.elapsedSubjectiveMinutes)
    assertEquals(0, result.state.time.lastAdvanceMinutes)
    assertNull(result.state.time.lastAdvanceReason)
    assertFalse("TURN_8:SYSTEM:TIME" in result.state.turn.executedCommandIds)
    assertNotNull(TurnCoordinator.recover(result.state))
  }
}
