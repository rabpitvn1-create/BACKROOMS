package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerActionPresentationTest {
  private fun player(id: String, text: String) = JSONObject()
    .put("role", "player").put("sceneMap", "0:1")
    .put("sceneActionId", id).put("text", text)
  private fun gm(text: String) = JSONObject()
    .put("role", "gm").put("sceneMap", "0:1").put("text", text)
  private fun view(vararg items: JSONObject) = JSONObject().put("log", JSONArray().apply {
    items.forEach { put(it) }
  })
  private fun snapshot(x: JSONObject) = JSONArray(x.getJSONArray("log").toString())

  @Test fun threeAuthorizedActionsBecomeOneVerbatimPlayerEntryAndOneGmParagraph() {
    val raw = "  Tôi đi vào phòng, nhìn nền rồi đánh dấu.  "
    val unrelated = JSONObject().put("role", "gm").put("text", "Lời kể trước.")
    val first = view(unrelated, player("step1", "Nhãn Core 1"), gm("Cao Minh bước vào.\nPhòng tối."))
    PlayerActionPresentation.fold(first, "step1", "step1", raw)
    val second = view(*(0 until first.getJSONArray("log").length()).map {
      first.getJSONArray("log").getJSONObject(it)
    }.toTypedArray(), player("step2", "Nhãn Core 2"), gm("Cậu nhìn xuống sàn."))
    PlayerActionPresentation.fold(second, "step1", "step2", raw)
    val third = view(*(0 until second.getJSONArray("log").length()).map {
      second.getJSONArray("log").getJSONObject(it)
    }.toTypedArray(), player("step3", "Nhãn Core 3"), gm("Một dấu mốc được ghi."))
    PlayerActionPresentation.fold(third, "step1", "step3", raw)
    val log = snapshot(third)
    assertEquals(3, log.length()) // Prior narration, player, one GM.
    assertEquals("Lời kể trước.", log.getJSONObject(0).getString("text"))
    assertEquals(raw, log.getJSONObject(1).getString("text"))
    assertEquals("Cao Minh bước vào. Phòng tối. Cậu nhìn xuống sàn. Một dấu mốc được ghi.",
      log.getJSONObject(2).getString("text"))
    assertEquals("player", log.getJSONObject(1).getString("role"))
    assertEquals(3, log.getJSONObject(1).getJSONArray("sceneBatchActions").length())
    assertFalse(log.getJSONObject(2).getString("text").contains("\n"))
  }

  @Test fun encounterWithoutNarrationDoesNotInsertPhantomGmReply() {
    val raw = "Quan sát rồi rời khỏi phòng"
    val first = view(player("one", raw), gm("Quan sát xong."))
    PlayerActionPresentation.fold(first, "one", "one", raw)
    val second = view(
      first.getJSONArray("log").getJSONObject(0),
      first.getJSONArray("log").getJSONObject(1),
      player("two", "Core fallback"))
    PlayerActionPresentation.fold(second, "one", "two", raw)
    assertEquals(2, second.getJSONArray("log").length())
    assertEquals("Quan sát xong.", second.getJSONArray("log").getJSONObject(1).getString("text"))
    val seen = second.getJSONArray("log").getJSONObject(0).getJSONArray("sceneBatchActions")
    assertEquals("two", seen.getString(1))
    assertEquals(2, JSONObject(second.toString()).getJSONArray("log").length())
  }

  @Test fun projectorRecognizesFoldedReceiptAfterReloadWithoutDuplicatingLastAction() {
    val core = WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 418L)
    val old = view(player("one", "Nhãn"), gm("Câu đầu."))
    PlayerActionPresentation.fold(old, "one", "one", "Tôi quan sát rồi ghi dấu.")
    old.getJSONArray("log").put(player("two", "Nhãn thứ hai"))
    old.getJSONArray("log").put(gm("Câu cuối."))
    PlayerActionPresentation.fold(old, "one", "two", "Tôi quan sát rồi ghi dấu.")
    val receipt = JSONObject().put("actionId", "two").put("label", "Ghi dấu")
      .put("reply", "Câu cuối.").put("playerText", "Tôi quan sát rồi ghi dấu.")
    val saved = core.copy(world = core.world +
      (MiniMapRuntime.RECEIPT to receipt.toString()))
    val displayed = CoreClientProjection.project(JSONObject(old.toString()), saved)
    val records = displayed.getJSONArray("log")
    assertEquals(2, records.length())
    assertEquals("Tôi quan sát rồi ghi dấu.", records.getJSONObject(0).getString("text"))
    assertEquals("Câu đầu. Câu cuối.", records.getJSONObject(1).getString("text"))
    assertEquals(2, records.getJSONObject(0).getJSONArray("sceneBatchActions").length())
  }

  @Test fun singleActionIsIdempotentAndKeepsWhitespaceInPlayerText() {
    val raw = "  Tôi hỏi: \"Đây là đâu?\"  "
    val state = view(player("one", "Trò chuyện: nhân vật"), gm("Một câu trả lời.\n\nKhác."))
    PlayerActionPresentation.fold(state, "one", "one", raw)
    val once = state.toString()
    PlayerActionPresentation.fold(state, "one", "one", raw)
    assertEquals(once, state.toString())
    assertEquals(raw, state.getJSONArray("log").getJSONObject(0).getString("text"))
    assertEquals("Một câu trả lời. Khác.", state.getJSONArray("log").getJSONObject(1).getString("text"))
  }
}
