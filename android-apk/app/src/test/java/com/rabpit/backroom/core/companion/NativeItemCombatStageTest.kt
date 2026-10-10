package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeItemCombatStageTest {
  private fun stable(state: GameState) = GameStateCodec.decode(GameStateCodec.encode(state))
  private fun command(state: GameState, operation: NativeItemCombatStage.Operation,
    id: String = "native-command") = NativeItemCombatStage.Command(operation, KAI_ID, id,
    CompanionDigests.sha256(GameStateCodec.encode(state)),
    expectedCombatRevision = if (operation == NativeItemCombatStage.Operation.USE_ITEM) null else Combat93Runtime.revision(state))
  private fun denies(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return } catch (_: IllegalStateException) { return }
    fail("unproven native command accepted")
  }

  @Test fun itemUsesOwnedNativeEffectsAndReplayCannotGrantQuantityOrMetadata() {
    val raw = GameState.initial()
    val stack = ItemStack("native-water", "Water", quantity = 2,
      metadata = mapOf("physiologyEffect" to "WATER", "consumedOnUse" to "true"))
    val state = stable(raw.copy(inventories = raw.inventories +
      (KAI_ID to InventoryState(KAI_ID, mapOf(stack.itemId to stack)))))
    val command = command(state, NativeItemCombatStage.Operation.USE_ITEM).copy(itemId = stack.itemId)
    val staged = NativeItemCombatStage.capture(state, command)
    val after = GameStateCodec.decode(staged.afterSnapshot)
    assertEquals(1, after.inventories.getValue(KAI_ID).items.getValue(stack.itemId).quantity)
    assertEquals(0L, after.characters.getValue(KAI_ID).physiology.minutesSinceWater)
    assertEquals(state.time, after.time) // This primitive does not charge an ordinary gameplay turn.
    assertTrue(staged.draws.isEmpty())
    assertEquals(staged.encode(), NativeItemCombatStage.replay(state, command, staged.encode()).encode())
    val forged = JSONObject(staged.encode()).put("after", GameStateCodec.encode(state))
    denies { NativeItemCombatStage.replay(state, command, CompanionWaitCapture.canonical(forged)) }
    denies { NativeItemCombatStage.capture(state, command.copy(itemId = "unowned")) }
    denies { NativeItemCombatStage.capture(after, command.copy(expectedSnapshotDigest =
      CompanionDigests.sha256(staged.afterSnapshot))) }
  }

  private fun combat(): GameState {
    val started = Combat93Runtime.start(stable(GameState.initial()), listOf("diep_minh"), 1, 0)
    var state = started
    // Actual native HOLD API; ensure a subsequent ROLL consumes the scoped original producer.
    for (i in 0 until 5) state = Combat93Runtime.hold(state, i, false, Combat93Runtime.revision(state))
    return stable(state)
  }

  @Test fun fullOriginalCombatRollReplaysWithNoExitDrawOrGenerator() {
    val state = combat()
    val command = command(state, NativeItemCombatStage.Operation.ROLL)
    val staged = NativeItemCombatStage.capture(state, command)
    assertEquals(5, staged.draws.size)
    assertTrue(staged.draws.all { it.bound == 6 && it.value in 0..5 })
    assertEquals(staged.encode(), NativeItemCombatStage.replay(state, command, staged.encode()).encode())
    val missing = JSONObject(staged.encode())
    val records = missing.getJSONArray("draws")
    missing.put("draws", JSONArray((0 until records.length() - 1).map { records.get(it) }))
    denies { NativeItemCombatStage.replay(state, command, CompanionWaitCapture.canonical(missing)) }
    val extra = JSONObject(staged.encode())
    extra.getJSONArray("draws").put(JSONArray(listOf(100, 0)))
    denies { NativeItemCombatStage.replay(state, command, CompanionWaitCapture.canonical(extra)) }
    denies { NativeItemCombatStage.capture(state, command.copy(expectedCombatRevision = -1)) }
    denies { NativeItemCombatStage.capture(state, command.copy(actorId = "unknown")) }
  }

  @Test fun resolveCapturesWholeNativeOperationIncludingNextHandAndResult() {
    val state = combat()
    val finished = stable(Combat93Runtime.finish(state, Combat93Runtime.revision(state)))
    val command = command(finished, NativeItemCombatStage.Operation.RESOLVE)
    val staged = NativeItemCombatStage.capture(finished, command)
    assertEquals(staged.encode(), NativeItemCombatStage.replay(finished, command, staged.encode()).encode())
    val native = Combat93Runtime.resolve(finished, Combat93Runtime.revision(finished))
    assertTrue(native.handled)
    val expected = native.state.copy(turn = native.state.turn.copy(
      executedCommandIds = native.state.turn.executedCommandIds + command.commandId))
    assertEquals(GameStateCodec.encode(expected), staged.afterSnapshot)
  }

  @Test fun nativeTargetAndHoldValidationRejectWrongArguments() {
    val state = combat()
    denies { NativeItemCombatStage.capture(state, command(state, NativeItemCombatStage.Operation.TARGET).copy(entityIndex = 999)) }
    denies { NativeItemCombatStage.capture(state, command(state, NativeItemCombatStage.Operation.HOLD).copy(dieIndex = 9, held = true)) }
    denies { NativeItemCombatStage.capture(state, command(state, NativeItemCombatStage.Operation.ROLL).copy(itemId = "forged-item")) }
  }
}
