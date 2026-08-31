package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ItemInteractionCoherenceTest {
  private fun luciaState(): GameState {
    val state = LuciaCanon.ensure(GameState.initial())
    return state.copy(party = state.party.copy(memberIds = (state.party.memberIds + LUCIA_ID).distinct()))
  }

  private fun grant(state: GameState, ownerId: String, itemId: String): GameState {
    val item = ItemCatalog.find(itemId)!!
    val result = StateReducer.execute(state, ItemCommand(
      commandId = "grant-$ownerId-$itemId", turnId = state.turn.currentTurnId, actorId = ownerId,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = item.id, itemName = item.name, metadata = item.metadata
    ))
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    return result.state
  }

  @Test fun narratedBandageIsAvailableBeforePickupAndOwnedCopiesAreNotDuplicated() {
    val prose = "Trong hốc tường có một gói Bandage còn nguyên niêm phong nằm trên nền."
    val flags = WorldItemLedger.reconcileNarrative(null, "Level 0 / Lobby", prose, "[]")
    val item = JSONObject(flags).getJSONArray("worldItems").getJSONObject(0)
    assertEquals(ItemCatalog.BANDAGE, item.getString("id"))
    assertTrue(item.getBoolean("available"))
    assertEquals("Level 0 / Lobby", item.getString("locationKey"))

    val owned = JSONArray().put(JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage"))
    val noDuplicate = WorldItemLedger.reconcileNarrative(null, "Level 0 / Lobby", prose, owned.toString())
    assertEquals(0, JSONObject(noDuplicate).getJSONArray("worldItems").length())
  }

  @Test fun omittedTransferUsesRememberedPickupInsteadOfRecipientName() {
    val state = grant(luciaState(), KAI_ID, ItemCatalog.BANDAGE)
    assertEquals(ItemCatalog.BANDAGE, state.metadata["lastReferencedItemId"])
    val context = GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    val command = CommandResolver().resolve(
      IntentCandidate("Đưa cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, state.turn.currentTurnId, context
    ) as ItemCommand
    assertEquals(KAI_ID, command.actorId)
    assertEquals(LUCIA_ID, command.targetId)
    assertEquals(ItemCatalog.BANDAGE, command.itemId)
    val result = StateReducer.execute(state, command)
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(ItemCatalog.BANDAGE))
    assertEquals(1, result.state.inventories.getValue(LUCIA_ID).items.getValue(ItemCatalog.BANDAGE).quantity)
  }

  @Test fun omittedTransferWithoutRememberedItemFailsClosed() {
    val state = luciaState()
    val context = GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    assertNull(CommandResolver().resolve(
      IntentCandidate("Đưa cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, state.turn.currentTurnId, context
    ))
  }

  @Test fun kaiCanUseOwnedBandageOnLowHpLucia() {
    var state = grant(luciaState(), KAI_ID, ItemCatalog.BANDAGE)
    state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 20)
    val kaiHp = state.characters.getValue(KAI_ID).vitalState.currentHp
    val command = CommandResolver().resolve(
      IntentCandidate("Dùng băng gạc cho Lucia", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, state.turn.currentTurnId, GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    ) as ItemCommand
    assertEquals(KAI_ID, command.actorId)
    assertEquals(LUCIA_ID, command.targetId)
    val result = StateReducer.execute(state, command)
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    assertEquals(35, result.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(kaiHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(ItemCatalog.BANDAGE))
  }

  @Test fun otherHealingConsumablesUseTheSameTargetPath() {
    listOf(ItemCatalog.ANTISEPTIC to 10, ItemCatalog.PAINKILLER to 10, ItemCatalog.ALMOND_WATER to 5).forEachIndexed { index, pair ->
      var state = grant(luciaState(), KAI_ID, pair.first)
      state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 20)
      val item = ItemCatalog.find(pair.first)!!
      val result = StateReducer.execute(state, ItemCommand(
        commandId = "target-use-$index", turnId = state.turn.currentTurnId,
        actorId = KAI_ID, targetId = LUCIA_ID, source = CommandSource.RULE,
        operation = ItemCommand.Operation.USE, itemId = item.id, itemName = item.name
      ))
      assertTrue("${item.name}: ${result.validation.reason}", result.applied)
      assertEquals(20 + pair.second, result.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    }
  }

  @Test fun generatedRuntimeCarriesHighlightAndPartyVitalContracts() {
    val html = File("src/main/assets/index.html").readText()
    assertTrue(html.contains("item.available===false"))
    assertTrue(html.contains("worldItemNames().concat(ownedItemNames())"))
    val knowledge = File("src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt").readText()
    assertTrue(knowledge.contains("out.put(\"partyVitals\", vitals)"))
    assertTrue(knowledge.contains("\"currentHp\", \"maxHp\""))
  }
}
