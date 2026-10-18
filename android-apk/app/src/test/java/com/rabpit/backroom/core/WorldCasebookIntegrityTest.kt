package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldCasebookIntegrityTest {
  private fun fresh() =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 11L)
  private fun action(s: GameState, target: String): GameState {
    val choice = MiniMapScenario.objects(MiniMapRuntime.actions(s))
      .first { it.getString("targetId") == target }
    val staged = MiniMapRuntime.stage(s, JSONObject(choice.toString()).apply { remove("label") })
    return s.copy(world = s.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }
  private fun mutate(s: GameState, edit: (JSONObject) -> Unit): JSONObject {
    val root = JSONObject(GameStateCodec.encode(s))
    val world = root.getJSONObject("world")
    val progress = JSONObject(world.getString(MiniMapRuntime.STATE))
    edit(progress)
    world.put(MiniMapRuntime.STATE, progress.toString())
    return root
  }

  @Test fun forgedObservationOrAdditionalEvidenceCannotBecomeAuthoritative() {
    val observed = action(fresh(), "observe")
    val changed = mutate(observed) { it.getJSONArray("records").getJSONObject(0)
      .put("text", "Lời GM giả mạo do save đưa vào.") }
    assertEquals("world_saved_record_integrity",
      assertThrows(IllegalArgumentException::class.java) {
        GameStateCodec.decode(changed)
      }.message)

    val fake = mutate(observed) {
      val room = it.getString("roomId")
      it.getJSONArray("records").put(JSONObject()
        .put("id", room).put("kind", "EVIDENCE")
        .put("text", "Vật chứng chưa từng được Core xác nhận."))
    }
    assertEquals("world_saved_record_integrity",
      assertThrows(IllegalArgumentException::class.java) {
        GameStateCodec.decode(fake)
      }.message)
  }

  @Test fun forgedVerificationCannotCreatePuzzleProgress() {
    val s = action(fresh(), "observe")
    val fake = mutate(s) {
      it.getJSONArray("records").put(JSONObject()
        .put("id", it.getString("roomId")).put("kind", "VERIFICATION")
        .put("text", "Đã mở mọi con đường."))
    }
    assertEquals("world_saved_record_integrity",
      assertThrows(IllegalArgumentException::class.java) {
        GameStateCodec.decode(fake)
      }.message)
    assertEquals(0, MiniMapRuntime.progress(s).getJSONObject("ruleFlags").length())
  }
}
