package com.rabpit.backroom.core

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FreshGameStartupTest {
  private class MemoryRepository : SaveRepository {
    var raw: String? = null
    var saves = 0
    override fun exists() = raw != null
    override fun load() = GameStateCodec.decode(checkNotNull(raw))
    override fun save(state: GameState) { raw = GameStateCodec.encode(state); saves++ }
    override fun clear() { raw = null }
  }

  private val initialUi = """{"turn":1,"party":[],"inventory":[],"flags":{}}"""
  private fun facade(repo: MemoryRepository, random: () -> Double = { 0.0 }): GameCoreFacade {
    val policyFile = listOf("src/main/assets", "app/src/main/assets", "android-apk/app/src/main/assets")
      .map { File(it, "knowledge/level_travel_policy.json") }.first { it.isFile }
    return GameCoreFacade(repo, NoOpGamePipelineLogger,
      LevelTravelEngine(LevelTravelPolicy(JSONObject(policyFile.readText())), random))
  }

  private fun leader(ui: JSONObject) = ui.getJSONObject("partyDetails").getJSONArray("members").getJSONObject(0)

  private fun assertPlayable(ui: JSONObject) {
    assertTrue(ui.getJSONObject("levelTravel").getString("nodeId").isNotBlank())
    assertTrue(ui.getString("levelBackground").isNotBlank())
    assertTrue(ui.has("coreSnapshot"))
    val player = leader(ui)
    assertEquals(CAO_MINH_ID, player.getString("id"))
    assertTrue(player.getInt("maxHp") > 0)
    assertEquals(player.getInt("maxHp"), player.getInt("currentHp"))
    for (stat in listOf("STR", "DEF", "SKL", "VIT")) {
      assertTrue(player.getJSONObject("stats").getJSONObject(stat).getInt("effective") > 0)
    }
    val equipment = player.getJSONArray("equipmentItems")
    val ids = (0 until equipment.length()).map { equipment.getJSONObject(it).getString("id") }.toSet()
    assertTrue(ids.containsAll(CaoMinhStartingEquipment.slots.values))
  }

  @Test fun hudReadsBeforeFirstBootDoNotCreateAPartialSaveOrConsumeRandomness() {
    val repo = MemoryRepository()
    var draws = 0
    val core = facade(repo) { draws++; 0.0 }
    // index.html renders the Core panel before its final level-travel.js script.
    repeat(3) { core.coreStats(initialUi, CAO_MINH_ID) }
    assertFalse("A HUD read must not create a save before Level initialization", repo.exists())
    assertEquals(0, repo.saves)
    assertEquals(0, draws)
    val started = JSONObject(core.resumeLevelGame(initialUi, false))
    assertPlayable(started)
    assertEquals(1, repo.saves)
    assertEquals(1, draws)
    val committed = repo.raw
    repeat(3) { assertTrue(JSONObject(core.coreStats("{}", CAO_MINH_ID)).has("stats")) }
    assertEquals(committed, repo.raw)
    assertEquals(1, repo.saves)
    assertEquals(1, draws)
  }

  @Test fun explicitNewGameReturnsStatsAndAllStartingEquipmentBeforeAnyAction() {
    val repo = MemoryRepository()
    val core = facade(repo)
    val started = JSONObject(core.initializeLevelGame(initialUi, true))
    assertPlayable(started)
    assertEquals(0, leader(started).getJSONArray("inventory").length())
    assertEquals(0, started.getJSONArray("inventory").length())
  }

  @Test fun resumeAndLoadRebuildItemDetailsFromCommittedCoreNotStaleUi() {
    val repo = MemoryRepository()
    val core = facade(repo)
    val started = core.initializeLevelGame(initialUi, true)
    val saved = repo.load()
    val inventory = saved.inventories.getValue(CAO_MINH_ID)
    repo.save(saved.copy(inventories = saved.inventories + (CAO_MINH_ID to inventory.copy(
      items = inventory.items + ("test-water" to ItemStack("test-water", "Test Water", quantity = 2))
    ))))
    val committed = repo.raw
    val reopened = facade(repo) { error("resume/load must not reroll the Level") }
    val resumed = JSONObject(reopened.resumeLevelGame(initialUi, false))
    assertPlayable(resumed)
    assertEquals("resume must not rewrite the committed save", committed, repo.raw)
    val loaded = JSONObject(reopened.initializeLevelGame(started, false))
    for (ui in listOf(resumed, loaded)) {
      assertPlayable(ui)
      val items = leader(ui).getJSONArray("inventory")
      assertEquals(1, items.length())
      assertEquals("test-water", items.getJSONObject(0).getString("id"))
      assertEquals(2, items.getJSONObject(0).getInt("quantity"))
      assertEquals(2, ui.getJSONArray("inventory").getJSONObject(0).getInt("quantity"))
    }
  }
}
