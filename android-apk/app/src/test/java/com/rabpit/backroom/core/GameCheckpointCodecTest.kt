package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameCheckpointCodecTest {
  @Test fun checkpointRestoresCompleteCurrentCoreAndPresentation() {
    val initial = CoreClientProjection.initialize(GameState.initial())
    val equipped = CharacterStatEngine.setCurrentHp(initial, KAI_ID, 31)
    val loot = InventoryEngine.grantDrop(equipped, BANDAGE_ID)!!
    val core = CoreClientProjection.advance(loot.copy(
      world = loot.world + ("location" to "Level 3 / trạm điện")
    ))
    val view = JSONObject()
      .put("turn", 2)
      .put("log", JSONArray().put(JSONObject().put("role", "gm").put("text", "Nhật ký đã lưu")))
      .put("inventory", JSONArray().put(JSONObject().put("id", "forged-gear")))
      .put("player", JSONObject().put("hp", 99999))
      .put("level", JSONObject().put("number", 6))
    val restored = GameCheckpointCodec.decode(GameCheckpointCodec.encode(core, view))
    assertEquals(2, CoreClientProjection.turnIndex(restored.core))
    assertEquals(31, restored.core.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals("Level 3 / trạm điện", restored.core.world["location"])
    assertEquals(1, restored.core.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID).quantity)
    assertEquals(core.equipment[KAI_ID]?.slots, restored.core.equipment[KAI_ID]?.slots)
    assertEquals(2, restored.presentation.getInt("turn"))
    assertEquals("Nhật ký đã lưu", restored.presentation.getJSONArray("log").getJSONObject(0).getString("text"))
    assertEquals(BANDAGE_ID, restored.presentation.getJSONArray("inventory").getJSONObject(0).getString("id"))
    assertEquals(31, restored.presentation.getJSONObject("player").getInt("hp"))
    assertEquals(0, restored.presentation.getJSONObject("level").getInt("number"))
  }

  @Test fun wrongVersionAndMalformedSlotsFailWithoutMigration() {
    val core = CoreClientProjection.initialize(GameState.initial())
    val json = JSONObject(GameCheckpointCodec.encode(core, JSONObject().put("turn", 1)))
    json.put("saveVersion", CURRENT_SAVE_VERSION - 1)
    assertThrows(Exception::class.java) { GameCheckpointCodec.decode(json.toString()) }
    json.put("saveVersion", CURRENT_SAVE_VERSION)
    json.getJSONObject("core").put("saveVersion", CURRENT_SAVE_VERSION - 1)
    assertThrows(Exception::class.java) { GameCheckpointCodec.decode(json.toString()) }
    json.getJSONObject("core").put("saveVersion", CURRENT_SAVE_VERSION)
    json.getJSONObject("presentation").put("turn", 99)
    assertThrows(Exception::class.java) { GameCheckpointCodec.decode(json.toString()) }
    assertThrows(Exception::class.java) { GameCheckpointCodec.decode("{\"saveVersion\":5}") }
  }

  @Test fun pendingTurnCannotBecomeManualCheckpoint() {
    val core = CoreClientProjection.initialize(GameState.initial())
    val pending = TurnCoordinator.createPending(core, "TURN_1", "Đang xử lý").state
    assertThrows(IllegalArgumentException::class.java) {
      GameCheckpointCodec.encode(pending, JSONObject().put("turn", 1))
    }
  }
}
