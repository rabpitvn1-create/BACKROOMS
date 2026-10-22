package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** GM items are carried in the UI checkpoint, never in Core gameplay fields. */
class GmQuestItemsCheckpointTest {
  @Test fun manualCheckpointKeepsGmLedgerBesideCoreWithoutAddingCoreItems() {
    val core = CoreClientProjection.initialize(GameState.initial())
    val originalItems = core.inventories
    val gm = JSONObject()
      .put("version", 1)
      .put("items", JSONArray().put(JSONObject()
        .put("id", "clue_001").put("name", "Mảnh giấy mã")
        .put("description", "Manh mối dùng một lần")
        .put("nodeId", "ls_2").put("status", "held")))
      .put("appliedActions", JSONArray().put("turn:001"))
    val fakeClient = JSONObject().put("gmQuestItems", gm)
      .put("inventory", JSONArray().put(JSONObject().put("id", "forge_inventory")))
      .put("log", JSONArray())
    val encoded = GameCheckpointCodec.encode(core, fakeClient)
    val restored = GameCheckpointCodec.decode(encoded)
    assertEquals(gm.toString(), restored.presentation.getJSONObject("gmQuestItems").toString())
    assertEquals(originalItems, restored.core.inventories)
    assertFalse(restored.core.world.keys.any { it.contains("gmQuestItems") })
    assertFalse(restored.core.metadata.keys.any { it.contains("gmQuestItems") })
    assertFalse(restored.presentation.getJSONArray("inventory").toString().contains("forge_inventory"))
    val saved = JSONObject(encoded)
    assertFalse(saved.getJSONObject("core").getJSONObject("world").has("gmQuestItems"))
    assertFalse(saved.getJSONObject("core").getJSONObject("inventories").has("gmQuestItems"))
  }

  @Test fun restoringPreviousCheckpointUsesItsGmItemsRatherThanCurrentPresentation() {
    val core = CoreClientProjection.initialize(GameState.initial())
    val checkpointItems = JSONObject().put("version", 1)
      .put("items", JSONArray().put(JSONObject().put("id", "old_token")
        .put("name", "Mảnh gỗ").put("description", "Dấu cũ")
        .put("nodeId", "0").put("status", "held")))
      .put("appliedActions", JSONArray().put("turn:old"))
    val checkpoint = GameCheckpointCodec.encode(core,
      JSONObject().put("gmQuestItems", checkpointItems).put("log", JSONArray()))
    val current = JSONObject().put("gmQuestItems", JSONObject().put("version", 1)
      .put("items", JSONArray()).put("appliedActions", JSONArray()))
    val restored = GameCheckpointCodec.decode(checkpoint)
    assertNotEquals(current.getJSONObject("gmQuestItems").toString(),
      restored.presentation.getJSONObject("gmQuestItems").toString())
    assertEquals("old_token", restored.presentation.getJSONObject("gmQuestItems")
      .getJSONArray("items").getJSONObject(0).getString("id"))
  }
  private class ReadOnlySave(
    var live: GameState,
    val checkpoint: GameCheckpoint?
  ) : SaveRepository {
    var writes = 0
    override fun save(state: GameState) { writes++; live = state }
    override fun load(): GameState = live
    override fun exists() = true
    override fun clear() {}
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {}
    override fun loadCheckpoint(): GameCheckpoint? = checkpoint
  }

  @Test fun checkpointPreflightDoesNotOverwriteLiveGameState() {
    val live = CoreClientProjection.initialize(GameState.initial())
    val gmItems = JSONObject().put("version", 1)
      .put("items", JSONArray().put(JSONObject().put("id", "mark_123")
        .put("name", "Mảnh dấu").put("description", "Vật chứng GM")
        .put("nodeId", "ls_2").put("status", "held")))
      .put("appliedActions", JSONArray())
    val saved = GameCheckpoint(live.copy(metadata = live.metadata + ("checkpointProof" to "saved")),
      JSONObject().put("gmQuestItems", gmItems).put("log", JSONArray()))
    val storage = ReadOnlySave(live, saved)
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val facade = ctor.newInstance(storage, NoOpGamePipelineLogger)
    val before = GameStateCodec.encode(storage.live)
    val preview = JSONObject(facade.previewManualCheckpoint())
    assertTrue(preview.getBoolean("ok"))
    assertEquals(gmItems.toString(), preview.getJSONObject("state")
      .getJSONObject("gmQuestItems").toString())
    assertEquals(before, GameStateCodec.encode(storage.live))
    assertEquals(0, storage.writes)
  }

  @Test fun missingCheckpointPreflightDoesNotChangeCore() {
    val live = CoreClientProjection.initialize(GameState.initial())
    val storage = ReadOnlySave(live, null)
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val facade = ctor.newInstance(storage, NoOpGamePipelineLogger)
    val response = JSONObject(facade.previewManualCheckpoint())
    assertFalse(response.getBoolean("ok"))
    assertEquals("checkpoint_missing", response.getString("error"))
    assertEquals(0, storage.writes)
    assertEquals(live, storage.live)
  }

}
