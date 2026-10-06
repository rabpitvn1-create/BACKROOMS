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

  @Test fun gameplayAndTimeCommitAtomically() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_7", "đi 30 phút rồi dùng rope")
    val grant = ItemCommand("TURN_7:grant", "TURN_7", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "rope", itemName = "Rope")
    val time = TimeAdvanceCommand("TURN_7:SYSTEM:TIME", "TURN_7", KAI_ID, source = CommandSource.SYSTEM,
      minutes = 30, reason = "player_action")

    val result = TurnCoordinator.commit(created.state, listOf(grant, time))

    assertNull(result.error)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("rope").quantity)
    assertEquals(30L, result.state.time.elapsedSubjectiveMinutes)
    assertEquals(30, result.state.time.lastAdvanceMinutes)
    assertEquals("player_action", result.state.time.lastAdvanceReason)
    assertTrue("TURN_7:SYSTEM:TIME" in result.state.turn.executedCommandIds)
    assertTrue("TURN_7" in result.state.turn.completedTurnIds)
  }

  @Test fun failedGameplayRollsBackTimeAdvance() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_8", "đi 10 phút rồi bỏ vật không có")
    val invalid = ItemCommand("TURN_8:drop", "TURN_8", KAI_ID, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DROP, itemId = "missing", itemName = "Missing")
    val time = TimeAdvanceCommand("TURN_8:SYSTEM:TIME", "TURN_8", KAI_ID, source = CommandSource.SYSTEM,
      minutes = 10, reason = "player_action")

    val result = TurnCoordinator.commit(created.state, listOf(invalid, time))

    assertEquals("insufficient_item_quantity", result.error)
    assertEquals(0L, result.state.time.elapsedSubjectiveMinutes)
    assertEquals(0, result.state.time.lastAdvanceMinutes)
    assertNull(result.state.time.lastAdvanceReason)
    assertFalse("TURN_8:SYSTEM:TIME" in result.state.turn.executedCommandIds)
    assertNotNull(TurnCoordinator.recover(result.state))
  }
}
