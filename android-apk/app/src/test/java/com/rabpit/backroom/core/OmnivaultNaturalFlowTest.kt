package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class OmnivaultNaturalFlowTest {
  private fun withAlmondWater(): GameState {
    val state = GameState.initial()
    val inventory = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val almond = ItemStack("almond-water", "Almond Water", 1, "SEALED")
    return state.copy(inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (almond.itemId to almond))))
  }

  @Test fun scanThenCopyCarriesReferenceAndTargetsRequestedTotal() {
    val state = withAlmondWater()
    val context = GameContext(state)
    val interpreted = RuleIntentInterpreter().interpretSync("Cao Minh quét Almond Water rồi nhân bản thành 10 chai", context)
    assertEquals(listOf(GameIntent.OMNIVAULT_SCAN, GameIntent.OMNIVAULT_COPY), interpreted.candidates.map { it.intent })
    val commands = CommandResolver().resolveSequence(interpreted.candidates, state.turn.currentTurnId, context).filterNotNull()
    assertEquals(2, commands.size)
    val scan = commands[0] as OmnivaultCommand
    val copy = commands[1] as OmnivaultCommand
    assertEquals("almond-water", scan.itemId)
    assertEquals("almond-water", copy.itemId)
    assertEquals(10, copy.quantity)
    assertEquals(10, copy.targetTotal)
    val result = StateReducer.executeAll(state, commands)
    assertTrue(result.applied)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("almond-water").quantity)
    val copyStack = result.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }
    assertEquals(9, copyStack.quantity)
    assertEquals("9", copyStack.metadata["omnivaultCopyCount"])
    assertEquals(10, result.state.inventories.getValue(KAI_ID).items.values.filter { it.name == "Almond Water" }.sumOf { it.quantity })
    assertTrue("almond-water" in result.state.omnivault.markedSourceIds)
    assertEquals("almond-water", result.state.omnivault.scanSlots.single().sourceItemId)
  }

  @Test fun copyWithoutTemplateStillRequiresCanonicalScan() {
    val state = withAlmondWater()
    val rejected = StateReducer.execute(state, OmnivaultCommand(
      "copy-without-scan", state.turn.currentTurnId, KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "almond-water", itemName = "Almond Water", quantity = 9
    ))
    assertFalse(rejected.applied)
    assertEquals("scan_template_missing", rejected.validation.reason)
  }

  @Test fun previousTurnReferenceResolvesCopyClauseWithoutRepeatingItemName() {
    val seed = withAlmondWater()
    val state = seed.copy(metadata = seed.metadata + ("lastReferencedItemId" to "almond-water"))
    val context = GameContext(state)
    val candidate = RuleIntentInterpreter().interpretSync("nhân bản thành 10 chai", context).candidates.single()
    assertEquals(GameIntent.OMNIVAULT_COPY, candidate.intent)
    val command = CommandResolver().resolve(candidate, 0, state.turn.currentTurnId, context) as OmnivaultCommand
    assertEquals("almond-water", command.itemId)
    assertEquals(10, command.quantity)
    assertEquals(10, command.targetTotal)
  }

  @Test fun createAdditionalCopiesKeepsAdditiveMeaning() {
    val state = withAlmondWater()
    val context = GameContext(state)
    val candidate = RuleIntentInterpreter().interpretSync("tạo thêm 3 bản Almond Water", context).candidates.single()
    val command = CommandResolver().resolve(candidate, 0, state.turn.currentTurnId, context) as OmnivaultCommand
    assertEquals(3, command.quantity)
  }
}
