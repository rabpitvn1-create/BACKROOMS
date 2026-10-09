package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import org.junit.Assert.*
import org.junit.Test

class CompanionCoreStageTest {
  private val turn = "stage-1"
  private fun state() = GameStateCodec.decode(GameStateCodec.encode(GameState.initial()))
  private fun grant(id: String = "grant", metadata: Map<String, String> = emptyMap()) = ItemCommand(
    id, turn, KAI_ID, source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
    itemId = "water", itemName = "Water", metadata = metadata)
  private fun time() = TimeAdvanceCommand("time", turn, KAI_ID, source = CommandSource.SYSTEM, minutes = 10, reason = "player_action")
  private fun rejected(state: GameState, commands: List<GameCommand>, expected: String, id: String = turn) {
    val before = GameStateCodec.encode(state)
    val result = CompanionCoreStage.stage(state, id, "đi 10 phút", commands)
    assertNull(result.candidate); assertEquals(expected, result.error)
    assertEquals(before, GameStateCodec.encode(state))
  }
  @Test fun existingCoreStagesGameplayAndTimeWithoutPublishing() {
    val original = state(); val before = GameStateCodec.encode(original)
    val commands = listOf(grant(), time())
    val result = CompanionCoreStage.stage(original, turn, "đi 10 phút", commands)
    assertNull(result.error); val candidate = result.candidate!!
    assertEquals(before, GameStateCodec.encode(original)); assertEquals(before, candidate.beforeSnapshot)
    val expected = TurnCoordinator.commit(TurnCoordinator.createPending(original, turn, "đi 10 phút").state, commands)
    assertEquals(expected.state, GameStateCodec.decode(candidate.afterSnapshot))
    assertEquals(10L, expected.state.time.elapsedSubjectiveMinutes)
    assertTrue(candidate.commandIds.containsAll(listOf("grant", "time")))
    assertTrue(candidate.events.contains("inventory_pickup"))
  }
  @Test fun lateFailureProducesNoCandidateOrPartialState() = rejected(state(), listOf(grant(),
    ItemCommand("drop", turn, KAI_ID, source = CommandSource.RULE, operation = ItemCommand.Operation.DROP,
      itemId = "missing", itemName = "Missing"), time()), "insufficient_item_quantity")
  @Test fun duplicateIdsCannotSilentlySkipPartOfBatch() = rejected(state(), listOf(grant(), time().copy(commandId = "grant")), "stage_duplicate_command")
  @Test fun previouslyExecutedCommandCannotBeReused() = rejected(state().let { it.copy(turn = it.turn.copy(executedCommandIds = setOf("grant"))) }, listOf(grant()), "stage_command_reused")
  @Test fun completedTurnCannotBeRestaged() = rejected(state().let { it.copy(turn = it.turn.copy(completedTurnIds = setOf(turn))) }, listOf(grant()), "turn_already_completed")
  @Test fun existingCorePendingCannotBeOverwritten() = rejected(TurnCoordinator.createPending(state(), "other", "input").state, listOf(grant()), "stage_core_pending")
  @Test fun wrongTurnRejectsBeforeCore() = rejected(state(), listOf(grant().copy(turnId = "other")), "command_turn_mismatch")
  @Test fun unknownActorStillUsesCoreValidation() = rejected(state(), listOf(grant().copy(actorId = "unknown")), "actor_unknown")
  @Test fun playerPickupCannotGainSystemAuthority() = rejected(state(), listOf(grant().copy(source = CommandSource.UI)), "player_pickup_unavailable")
  @Test fun emptyAndOversizedBatchReject() {
    rejected(state(), emptyList(), "stage_command_count")
    rejected(state(), (0..128).map { grant("c$it") }, "stage_command_count")
  }
  @Test fun invalidIdentityAndUnsupportedSnapshotReject() {
    rejected(state(), listOf(grant()), "stage_turn_identity", "../slot")
    rejected(state(), listOf(grant("bad id")), "stage_command_identity")
    rejected(state().copy(saveVersion = CURRENT_SAVE_VERSION + 1), listOf(grant()), "stage_snapshot_version")
  }
  @Test fun codecNormalizationCannotBecomeUnreviewedMutation() {
    val original = state().copy(party = PartyState(maxMembers = 0))
    rejected(original, listOf(grant()), "stage_snapshot_not_stable")
  }
  @Test fun repeatedStagingIsDeterministicAndCandidateDoesNotAliasCaller() {
    val metadata = linkedMapOf("fixture" to "original")
    val commands = mutableListOf<GameCommand>(grant(metadata = metadata), time())
    val original = state()
    val a = CompanionCoreStage.stage(original, turn, "đi 10 phút", commands).candidate!!
    val b = CompanionCoreStage.stage(original, turn, "đi 10 phút", commands).candidate!!
    assertEquals(a.afterSnapshot, b.afterSnapshot); assertEquals(a.events, b.events)
    metadata["fixture"] = "changed"; commands.clear()
    assertEquals("original", GameStateCodec.decode(a.afterSnapshot).inventories.getValue(KAI_ID).items.getValue("water").metadata["fixture"])
    try { (a.commandIds as MutableList<String>).clear(); fail("mutable candidate ids") } catch (_: UnsupportedOperationException) { }
    try { (a.events as MutableList<String>).clear(); fail("mutable candidate events") } catch (_: UnsupportedOperationException) { }
  }
  @Test fun coordinatorGeneratedRestCommandsAreIncluded() {
    val result = CompanionCoreStage.stage(state(), turn, "ngủ 10 phút", listOf(time()))
    assertNull(result.error)
    assertTrue(result.candidate!!.commandIds.any { ":SYSTEM:REST:" in it })
  }
}
