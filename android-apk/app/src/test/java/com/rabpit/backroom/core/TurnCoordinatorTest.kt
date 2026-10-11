package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class TurnCoordinatorTest {
  private fun apply(turn: String, id: String) = StatusCommand(
    "status:$id", turn, KAI_ID, source = CommandSource.SYSTEM,
    operation = StatusCommand.Operation.APPLY, effect = StatusEffect(id, "BUFF", "test")
  )

  @Test fun pendingTurnSurvivesAndCannotCommitTwice() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_184", "lượt hợp lệ")
    assertEquals("TURN_184", TurnCoordinator.recover(created.state)?.turnId)
    val committed = TurnCoordinator.commit(created.state, listOf(apply("TURN_184", "focus")))
    assertNull(committed.error)
    assertTrue("focus" in committed.state.statuses)
    assertNull(TurnCoordinator.recover(committed.state))
    val retry = TurnCoordinator.createPending(committed.state, "TURN_184", "lượt hợp lệ")
    assertEquals("turn_already_completed", retry.error)
  }

  @Test fun failedBatchIsAtomic() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_2", "multi")
    val invalid = ItemCommand("c2", "TURN_2", KAI_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.DROP, itemId = "gun", itemName = "Gun")
    val result = TurnCoordinator.commit(created.state, listOf(apply("TURN_2", "focus"), invalid))
    assertEquals("insufficient_item_quantity", result.error)
    assertFalse("focus" in result.state.statuses)
    assertNotNull(TurnCoordinator.recover(result.state))
  }

  @Test fun gameplayAndTimeCommitAtomically() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_7", "đi 30 phút")
    val time = TimeAdvanceCommand("TURN_7:SYSTEM:TIME", "TURN_7", KAI_ID,
      source = CommandSource.SYSTEM, minutes = 30, reason = "player_action")
    val result = TurnCoordinator.commit(created.state, listOf(apply("TURN_7", "focus"), time))
    assertNull(result.error)
    assertTrue("focus" in result.state.statuses)
    assertEquals(30L, result.state.time.elapsedSubjectiveMinutes)
    assertTrue("TURN_7" in result.state.turn.completedTurnIds)
  }

  @Test fun failedGameplayRollsBackTimeAdvance() {
    val created = TurnCoordinator.createPending(GameState.initial(), "TURN_8", "đi rồi bỏ vật không có")
    val invalid = ItemCommand("TURN_8:drop", "TURN_8", KAI_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.DROP, itemId = "missing", itemName = "Missing")
    val time = TimeAdvanceCommand("TURN_8:SYSTEM:TIME", "TURN_8", KAI_ID,
      source = CommandSource.SYSTEM, minutes = 10, reason = "player_action")
    val result = TurnCoordinator.commit(created.state, listOf(invalid, time))
    assertEquals("insufficient_item_quantity", result.error)
    assertEquals(0L, result.state.time.elapsedSubjectiveMinutes)
    assertNotNull(TurnCoordinator.recover(result.state))
  }
}
