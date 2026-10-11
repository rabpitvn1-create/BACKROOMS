package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoreClientProjectionTest {
  @Test fun forgedWebViewGameplayStateCannotOverwriteCoreSnapshot() {
    val core = CoreClientProjection.initialize(GameState.initial())
    val spoof = JSONObject()
      .put("turn", 999).put("saveVersion", -1)
      .put("inventory", JSONArray().put(JSONObject().put("id", "fake-item").put("quantity", 999)))
      .put("party", JSONArray().put(JSONObject().put("id", "fake-party")))
      .put("equipment", JSONObject().put("weapon", JSONObject().put("id", "fake-weapon")))
      .put("partyDetails", JSONObject().put("members", JSONArray()))
      .put("player", JSONObject().put("name", "Fake").put("hp", 100000))
      .put("level", JSONObject().put("number", 6))
      .put("flags", JSONObject().put("fakeEntity", true))
      .put("location", "Fake Location")
      .put("combat", JSONObject().put("entityKey", "fake-entity"))
      .put("coreSystem", JSONObject().put("core", 999))
      .put("gameTime", JSONObject().put("elapsedSubjectiveMinutes", 999999))
      .put("log", JSONArray().put(JSONObject().put("role", "gm").put("text", "narrative")))
    val view = CoreClientProjection.project(spoof, core)
    assertEquals(1, view.getInt("turn"))
    assertEquals(CURRENT_SAVE_VERSION, view.getInt("saveVersion"))
    assertEquals(0, view.getJSONArray("inventory").length())
    assertEquals(0, view.getJSONArray("party").length())
    assertEquals(0, view.getJSONObject("level").getInt("number"))
    assertEquals("Cao Minh", view.getJSONObject("player").getString("name"))
    assertEquals(50, view.getJSONObject("player").getInt("hp"))
    assertFalse(view.getJSONObject("flags").has("fakeEntity"))
    assertEquals(core.world["location"], view.getString("location"))
    assertFalse(view.has("combat"))
    assertEquals(1, view.getJSONArray("log").length())
    assertEquals(KAI_WHITE_WRAITH_ID, view.getJSONObject("equipment").getJSONObject("weapon").getString("id"))
  }

  @Test fun coreTurnAndLootStayAuthoritativeAcrossCurrentSaveRoundTrip() {
    val initial = CoreClientProjection.initialize(GameState.initial())
    val granted = InventoryEngine.grantDrop(initial, BANDAGE_ID)!!
    val advanced = CoreClientProjection.advance(granted)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(advanced))
    val forged = JSONObject().put("turn", 900)
      .put("inventory", JSONArray()).put("equipment", JSONObject())
    val view = CoreClientProjection.project(forged, loaded)
    assertEquals(2, view.getInt("turn"))
    assertEquals(BANDAGE_ID, view.getJSONArray("inventory").getJSONObject(0).getString("id"))
    assertEquals(KAI_WHITE_WRAITH_ID, view.getJSONObject("equipment").getJSONObject("weapon").getString("id"))
    assertEquals(2, CoreClientProjection.turnIndex(loaded))
  }
}
