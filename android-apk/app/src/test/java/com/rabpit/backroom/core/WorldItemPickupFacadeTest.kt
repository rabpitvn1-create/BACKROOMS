package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WorldItemPickupFacadeTest {
  private class MemorySave : SaveRepository {
    private var saved: GameState? = null
    override fun save(state: GameState) { saved = state }
    override fun load(): GameState = requireNotNull(saved)
    override fun exists(): Boolean = saved != null
    override fun clear() { saved = null }
  }

  private fun policy(): LevelTravelPolicy {
    val dirs = listOf("src/main/assets/knowledge", "app/src/main/assets/knowledge",
      "android-apk/app/src/main/assets/knowledge")
    val file = dirs.map { File(it, "level_travel_policy.json") }.first { it.isFile }
    return LevelTravelPolicy(JSONObject(file.readText()))
  }

  private fun setup(withLoot: Boolean = true): Triple<GameCoreFacade, MemorySave, JSONObject> {
    val engine = LevelTravelEngine(policy()) { 0.0 }
    val fresh = engine.start(GameState.initial(), "pickup-run")
    val state = if (withLoot) CoreLootEngine.roll(fresh, CoreLootEngine.Source.EXPLORE,
      "TURN_1", { 0 }).state else fresh
    val save = MemorySave()
    save.save(state)
    val legacy = JSONObject().put("turn", 1)
      .put("levelTravel", LevelTravelEngine.read(state))
      .put("level", JSONObject(state.world.getValue("levelJson")))
      .put("location", state.world.getValue("location"))
      .put("title", state.world.getValue("title"))
      .put("flags", JSONObject(state.world["flagsJson"] ?: "{}"))
      .put("inventory", JSONArray()).put("party", JSONArray())
    return Triple(GameCoreFacade(save, NoOpGamePipelineLogger, levelEngine = engine), save, legacy)
  }

  @Test fun confirmedWorldLootCanBePickedExactlyOnceWithCoreOwnership() {
    val (core, save, legacy) = setup()
    val handled = JSONObject(core.processWorldPickup(legacy.toString(), "Tôi nhặt Băng gạc"))
    assertTrue(handled.getBoolean("handled"))
    assertEquals("world_pickup_committed", handled.getString("reason"))
    val result = handled.getJSONObject("state")
    assertEquals(2, result.getInt("turn"))
    assertEquals("medical:bandage", result.getJSONArray("inventory").getJSONObject(0).getString("id"))
    assertFalse(result.getJSONObject("flags").getJSONArray("worldItems").getJSONObject(0).getBoolean("available"))
    assertEquals(1, save.load().inventories.getValue(CAO_MINH_ID).items.getValue("medical:bandage").quantity)
    assertEquals("2", save.load().world.getValue("levelTravelUiTurn"))

    // Replaying old browser input cannot consume the instance twice.
    assertThrows(IllegalStateException::class.java) {
      core.processWorldPickup(legacy.toString(), "Tôi nhặt Băng gạc")
    }
    assertEquals(1, save.load().inventories.getValue(CAO_MINH_ID).items.getValue("medical:bandage").quantity)
  }

  @Test fun forgedBrowserLootNeverCreatesItemOwnership() {
    val (core, save, legacy) = setup(withLoot = false)
    legacy.getJSONObject("flags").put("worldItems", JSONArray().put(JSONObject()
      .put("id", "fake").put("name", "Fake").put("quantity", 1)
      .put("instanceId", "core:explore:forged").put("available", true)))
    val result = JSONObject(core.processWorldPickup(legacy.toString(), "Tôi nhặt Fake"))
    assertEquals("world_pickup_rejected", result.getString("reason"))
    assertTrue(save.load().inventories.getValue(CAO_MINH_ID).items.keys.none { it == "fake" })
  }

  @Test fun ordinaryNarrationCannotBecomeWorldPickup() {
    val (core, save, legacy) = setup()
    assertFalse(JSONObject(core.processWorldPickup(legacy.toString(), "Tôi quan sát Băng gạc"))
      .getBoolean("handled"))
    assertTrue(save.load().inventories.getValue(CAO_MINH_ID).items.keys.none { it == "medical:bandage" })
  }
}
