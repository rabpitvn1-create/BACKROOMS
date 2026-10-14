package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemDetailActionsTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    private var checkpoint: GameCheckpoint? = null
    override fun saveCheckpoint(state: GameState, presentation: org.json.JSONObject) {
      checkpoint = GameCheckpoint(state, org.json.JSONObject(presentation.toString()))
    }
    override fun loadCheckpoint(): GameCheckpoint? = checkpoint
    override fun exists() = true
    override fun clear() { checkpoint = null }
  }
  private fun fresh(): GameState {
    val initial = CharacterEquipmentSystem.seedFresh(GameState.initial())
    return initial.copy(party = initial.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID)),
      inventories = initial.inventories + (CAO_MINH_ID to InventoryState(CAO_MINH_ID, initial.inventories.getValue(CAO_MINH_ID).items +
        (BANDAGE_ID to HealingItems.normalize(ItemStack(BANDAGE_ID, "Băng gạc", 3))!!))))
  }
  private fun facade(save: MemorySave): GameCoreFacade {
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(save, NoOpGamePipelineLogger)
  }
  private fun request(operation: String, quantity: Number = 1, actor: String = CAO_MINH_ID, target: String? = null, item: String = BANDAGE_ID) =
    JSONObject().put("operation", operation).put("actorId", actor).put("itemId", item).put("quantity", quantity).also { if (target != null) it.put("targetId", target) }.toString()
  private fun run(save: MemorySave, request: String, turn: Int = 1) = JSONObject(facade(save).processItemAction("""{"turn":$turn,"log":[]}""", request))

  @Test fun useCommitsExactOwnedItemWithoutAdvancingExplorerTurnOrTime() {
    val save = MemorySave(fresh())
    val before = save.state
    val result = run(save, request("USE"))
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals(2, save.state.inventories.getValue(CAO_MINH_ID).items.getValue(BANDAGE_ID).quantity)
    assertEquals(1, result.getJSONObject("state").getInt("turn"))
    assertEquals(before.time, save.state.time)
    assertEquals(before.turn.currentTurnId, save.state.turn.currentTurnId)
    assertEquals(before.turn.completedTurnIds, save.state.turn.completedTurnIds)
  }

  @Test fun itemCanBeUsedWhileEntityEncounterPendingWithoutConsumingIt() {
    // Core-authoritative entity encounter pending (entityEncounterKey flag).
    // Item actions must not consume or clear the pending encounter.
    val withEncounter = fresh().let { state ->
      val flags = JSONObject(state.world["flagsJson"] ?: "{}").put("entityEncounterKey", "hound")
      state.copy(world = state.world + ("flagsJson" to flags.toString()))
    }
    val save = MemorySave(withEncounter)
    val beforeKey = JSONObject(save.state.world["flagsJson"]).optString("entityEncounterKey")
    val first = run(save, request("USE"))
    assertTrue(first.toString(), first.getBoolean("handled"))
    assertEquals(beforeKey, JSONObject(save.state.world["flagsJson"]).optString("entityEncounterKey"))
    assertEquals(1, first.getJSONObject("state").getInt("turn"))
    val second = run(save, request("TRANSFER", target = IRIS_ID))
    assertTrue(second.toString(), second.getBoolean("handled"))
    assertEquals(beforeKey, JSONObject(save.state.world["flagsJson"]).optString("entityEncounterKey"))
  }
  @Test fun transferMovesRequestedQuantityToChosenMember() {
    val save = MemorySave(fresh())
    assertTrue(run(save, request("TRANSFER", 2, target = IRIS_ID)).getBoolean("handled"))
    assertEquals(1, save.state.inventories.getValue(CAO_MINH_ID).items.getValue(BANDAGE_ID).quantity)
    assertEquals(2, save.state.inventories.getValue(IRIS_ID).items.getValue(BANDAGE_ID).quantity)
  }
  @Test fun followerCanDropItsOwnItemWithoutAffectingLeader() {
    val initial = fresh()
    val save = MemorySave(initial.copy(inventories = initial.inventories + (IRIS_ID to InventoryState(IRIS_ID, mapOf("scrap" to ItemStack("scrap", "Mảnh vụn", 2))))))
    assertTrue(run(save, request("DROP", 1, IRIS_ID, item = "scrap")).getBoolean("handled"))
    assertEquals(1, save.state.inventories.getValue(IRIS_ID).items.getValue("scrap").quantity)
    assertEquals(initial.inventories[CAO_MINH_ID], save.state.inventories[CAO_MINH_ID])
  }
  @Test fun invalidRequestsNeverConsumeItemsOrAdvanceTime() {
    for (request in listOf(request("DROP", 0), request("DROP", -1), request("DROP", 1.5), request("DROP", 4),
      request("PICKUP"), request("TRANSFER", target = CAO_MINH_ID), request("TRANSFER", target = "missing"),
      request("DROP", actor = "missing"), request("USE", item = "missing"))) {
      val save = MemorySave(fresh()); val before = save.state
      assertFalse(request, run(save, request).getBoolean("handled"))
      assertEquals(before, save.state)
    }
  }
  @Test fun equippedDropAndFullRecipientRejectAtomically() {
    val initial = fresh()
    val cases = listOf(
      initial to request("DROP", item = CAO_MINH_SWORD_ID),
      initial.copy(inventories = initial.inventories + (IRIS_ID to InventoryState(IRIS_ID,
        (1..6).associate { "scrap$it" to ItemStack("scrap$it", "Mảnh $it") }))) to request("TRANSFER", target = IRIS_ID)
    )
    for ((state, request) in cases) {
      val save = MemorySave(state)
      assertFalse(run(save, request).getBoolean("handled"))
      assertEquals(state, save.state)
    }
  }
  @Test fun consecutiveItemActionsWorkAtSameTurnButInvalidQuantityIsRejected() {
    val save = MemorySave(fresh())
    assertTrue(run(save, request("DROP", 2)).getBoolean("handled"))
    assertEquals(1, save.state.inventories.getValue(CAO_MINH_ID).items.getValue(BANDAGE_ID).quantity)
    val after = save.state
    assertFalse(run(save, request("DROP", 2)).getBoolean("handled"))
    assertEquals(after, save.state)
    assertTrue(run(save, request("DROP", 1)).getBoolean("handled"))
    assertFalse(save.state.inventories.getValue(CAO_MINH_ID).items.containsKey(BANDAGE_ID))
    assertEquals(1, CoreClientProjection.turnIndex(save.state))
    assertEquals("stale_client_state", run(save, request("USE", item = CAO_MINH_SWORD_ID), turn = 2).getString("error"))
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
    assertFalse(core.blocksTextItemAction("Cao Minh dùng thần thức tìm Lục Trầm"))
    assertFalse(core.blocksTextItemAction("Cao Minh sử dụng thần thức để dò xét"))
    assertTrue(core.blocksTextItemAction("Cao Minh dùng thần thức rồi dùng Băng gạc"))
  }

}
