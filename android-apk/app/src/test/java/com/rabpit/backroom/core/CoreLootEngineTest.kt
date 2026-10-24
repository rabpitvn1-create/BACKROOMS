package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoreLootEngineTest {
  private fun fresh(): GameState = GameState.initial()
  private fun rollAt(pct: Int, poolIndex: Int = 0): (Int) -> Int = { bound ->
    if (bound == 100) pct else poolIndex
  }
  private fun world(state: GameState): org.json.JSONArray =
    JSONObject(state.world["flagsJson"] ?: "{}").optJSONArray("worldItems")
      ?: org.json.JSONArray()

  @Test fun exactlyEightyPercentEntityAndEightPercentExploration() {
    assertEquals(80, CoreLootEngine.ENTITY_DROP_PERCENT)
    assertEquals(8, CoreLootEngine.EXPLORATION_DROP_PERCENT)
    assertNotNull(CoreLootEngine.roll(fresh(), CoreLootEngine.Source.ENTITY, "kill:1", rollAt(79)).dropped)
    assertNull(CoreLootEngine.roll(fresh(), CoreLootEngine.Source.ENTITY, "kill:1", rollAt(80)).dropped)
    assertNotNull(CoreLootEngine.roll(fresh(), CoreLootEngine.Source.EXPLORE, "turn:1", rollAt(7)).dropped)
    assertNull(CoreLootEngine.roll(fresh(), CoreLootEngine.Source.EXPLORE, "turn:1", rollAt(8)).dropped)
  }

  @Test fun droppedItemLivesInWorldNotInventoryAndHasUniquePhysicalInstance() {
    val start = fresh()
    val done = CoreLootEngine.roll(start, CoreLootEngine.Source.ENTITY, "encounter:7", rollAt(0))
    assertTrue(done.rolled)
    assertEquals(start.inventories, done.state.inventories)
    assertEquals(1, world(done.state).length())
    val objectAt = world(done.state).getJSONObject(0)
    assertEquals("medical:bandage", objectAt.getString("id"))
    assertEquals(1, objectAt.getInt("quantity"))
    assertEquals("core:entity:encounter:7", objectAt.getString("instanceId"))
    assertTrue(objectAt.getBoolean("available"))
  }

  @Test fun oneEventCannotRollAgainEvenWhenFirstAttemptMisses() {
    val missed = CoreLootEngine.roll(fresh(), CoreLootEngine.Source.EXPLORE, "TURN_1", rollAt(99))
    assertTrue(missed.rolled)
    val retry = CoreLootEngine.roll(missed.state, CoreLootEngine.Source.EXPLORE, "TURN_1", rollAt(0))
    assertFalse(retry.rolled)
    assertEquals(missed.state, retry.state)
    assertEquals(0, world(retry.state).length())
  }

  @Test fun twoIndependentEventsGetTwoSeparateWorldInstances() {
    val first = CoreLootEngine.roll(fresh(), CoreLootEngine.Source.ENTITY, "encounter:1", rollAt(0))
    val next = CoreLootEngine.roll(first.state, CoreLootEngine.Source.ENTITY, "encounter:2", rollAt(0))
    assertEquals(2, world(next.state).length())
    assertNotEquals(world(next.state).getJSONObject(0).getString("instanceId"),
      world(next.state).getJSONObject(1).getString("instanceId"))
  }

  @Test fun explorationRequiresAnActualSearchNotANarrativeOrInventoryQuery() {
    assertTrue(CoreLootEngine.isExplorationAction("Tôi khám phá căn phòng"))
    assertTrue(CoreLootEngine.isExplorationAction("Tôi lục soát chiếc tủ"))
    assertFalse(CoreLootEngine.isExplorationAction("Không tìm kiếm vật phẩm"))
    assertFalse(CoreLootEngine.isExplorationAction("Xem inventory"))
    assertFalse(CoreLootEngine.isExplorationAction("Đứng yên quan sát"))
  }

  @Test fun gmCannotGrantItemsOrForgeWorldLootThroughValidatedSnapshot() {
    val start = fresh()
    val flagForgery = JSONObject().put("worldItems",
      org.json.JSONArray().put(JSONObject().put("id", "fake").put("name", "Fake"))).toString()
    val fake = StateReducer.execute(start, ItemCommand(
      "gm-grant", "TURN_1", CAO_MINH_ID, source = CommandSource.GEMINI,
      operation = ItemCommand.Operation.PICKUP, itemId = "fake", itemName = "Fake"))
    assertFalse(fake.applied)
    assertEquals("player_pickup_unavailable", fake.validation.reason)
    val forged = StateReducer.execute(start, ValidatedLegacyStateCommand(
      "gm-world", "TURN_1", CAO_MINH_ID, source = CommandSource.GEMINI,
      flagsJson = flagForgery, validatedByGameEngine = true))
    assertTrue(forged.applied)
    assertEquals(0, world(forged.state).length())
  }
}
