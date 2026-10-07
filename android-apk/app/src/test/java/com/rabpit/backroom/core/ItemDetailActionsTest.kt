package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemDetailActionsTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
  }
  private fun fresh(): GameState {
    val initial = CharacterEquipmentSystem.seedFresh(GameState.initial())
    return initial.copy(party = initial.party.copy(memberIds = listOf(KAI_ID, IRIS_ID)),
      inventories = initial.inventories + (KAI_ID to InventoryState(KAI_ID, initial.inventories.getValue(KAI_ID).items +
        (BANDAGE_ID to HealingItems.normalize(ItemStack(BANDAGE_ID, "Băng gạc", 3))!!))))
  }
  private fun facade(save: MemorySave): GameCoreFacade {
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(save, NoOpGamePipelineLogger)
  }
  private fun request(operation: String, quantity: Number = 1, actor: String = KAI_ID, target: String? = null, item: String = BANDAGE_ID) =
    JSONObject().put("operation", operation).put("actorId", actor).put("itemId", item).put("quantity", quantity).also { if (target != null) it.put("targetId", target) }.toString()
  private fun run(save: MemorySave, request: String, turn: Int = 1) = JSONObject(facade(save).processItemAction("""{"turn":$turn,"log":[]}""", request))

  @Test fun useCommitsExactOwnedItemAndAdvancesTurn() {
    val save = MemorySave(fresh())
    val result = run(save, request("USE"))
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals(2, save.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID).quantity)
    assertEquals(2, result.getJSONObject("state").getInt("turn"))
    assertTrue(save.state.turn.completedTurnIds.contains("TURN_1"))
  }
  @Test fun transferMovesRequestedQuantityToChosenMember() {
    val save = MemorySave(fresh())
    assertTrue(run(save, request("TRANSFER", 2, target = IRIS_ID)).getBoolean("handled"))
    assertEquals(1, save.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID).quantity)
    assertEquals(2, save.state.inventories.getValue(IRIS_ID).items.getValue(BANDAGE_ID).quantity)
  }
  @Test fun followerCanDropItsOwnItemWithoutAffectingLeader() {
    val initial = fresh()
    val save = MemorySave(initial.copy(inventories = initial.inventories + (IRIS_ID to InventoryState(IRIS_ID, mapOf("scrap" to ItemStack("scrap", "Mảnh vụn", 2))))))
    assertTrue(run(save, request("DROP", 1, IRIS_ID, item = "scrap")).getBoolean("handled"))
    assertEquals(1, save.state.inventories.getValue(IRIS_ID).items.getValue("scrap").quantity)
    assertEquals(initial.inventories[KAI_ID], save.state.inventories[KAI_ID])
  }
  @Test fun invalidRequestsNeverConsumeItemsOrAdvanceTime() {
    for (request in listOf(request("DROP", 0), request("DROP", -1), request("DROP", 1.5), request("DROP", 4),
      request("PICKUP"), request("TRANSFER", target = KAI_ID), request("TRANSFER", target = "missing"),
      request("DROP", actor = "missing"), request("USE", item = "missing"))) {
      val save = MemorySave(fresh()); val before = save.state
      assertFalse(request, run(save, request).getBoolean("handled"))
      assertEquals(before, save.state)
    }
  }
  @Test fun equippedDropAndFullRecipientRejectAtomically() {
    val initial = fresh()
    val cases = listOf(
      initial to request("DROP", item = KAI_WHITE_WRAITH_ID),
      initial.copy(inventories = initial.inventories + (IRIS_ID to InventoryState(IRIS_ID,
        (1..6).associate { "scrap$it" to ItemStack("scrap$it", "Mảnh $it") }))) to request("TRANSFER", target = IRIS_ID)
    )
    for ((state, request) in cases) {
      val save = MemorySave(state)
      assertFalse(run(save, request).getBoolean("handled"))
      assertEquals(state, save.state)
    }
  }
  @Test fun staleTurnCannotRepeatSuccessfulDrop() {
    val save = MemorySave(fresh())
    assertTrue(run(save, request("DROP")).getBoolean("handled"))
    val after = save.state
    assertFalse(run(save, request("DROP")).getBoolean("handled"))
    assertEquals(after, save.state)
  }
  @Test fun executeTextCannotUseTransferOrDropItems() {
    for (action in listOf("Dùng Băng gạc", "Chuyển Băng gạc cho Iris", "Bỏ xuống Băng gạc", "Uống một nửa chai nước")) {
      val save = MemorySave(fresh()); val before = save.state
      val core = facade(save)
      assertTrue(action, core.blocksTextItemAction(action))
      val result = JSONObject(core.processRule("""{"turn":1,"log":[]}""", action))
      assertEquals("item_ui_required", result.getString("error"))
      assertEquals(1, result.getJSONObject("state").getInt("turn"))
      assertEquals(before, save.state)
      val candidate = JSONObject(core.processValidatedCandidate("""{"turn":1}""", "{}", action))
      assertFalse(candidate.getBoolean("handled"))
      assertEquals(before, save.state)
    }
  }
  @Test fun executeStillAcceptsOrdinaryExplorationInput() {
    val core = facade(MemorySave(fresh()))
    assertFalse(core.blocksTextItemAction("Quan sát hành lang"))
    assertFalse(core.blocksTextItemAction("Kiểm tra túi đồ"))
  }

}
