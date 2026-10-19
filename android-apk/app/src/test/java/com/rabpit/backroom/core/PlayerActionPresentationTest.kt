package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class PlayerActionPresentationTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    var slot: String? = null
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { slot = GameCheckpointCodec.encode(state, presentation) }
    override fun loadCheckpoint() = slot?.let(GameCheckpointCodec::decode)
  }
  private class CountingRng : SecureRandom() {
    var calls = 0
    override fun nextInt(bound: Int): Int { calls++; return bound - 1 }
  }
  private val rng = CountingRng()
  private fun fixture(): Pair<MemorySave, GameCoreFacade> {
    val repo = MemorySave(WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 41L))
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(repo, NoOpGamePipelineLogger)
    GameCoreFacade::class.java.getDeclaredField("spawnRng").apply { isAccessible = true }.set(core, rng)
    return repo to core
  }
  private fun view(core: GameCoreFacade, previous: JSONObject = JSONObject()) = JSONObject(core.currentClientState(previous.toString()))
  private fun lines(view: JSONObject, role: String) = MiniMapScenario.objects(view.getJSONArray("log")).filter { it.optString("role") == role }
  private fun commit(core: GameCoreFacade, text: String, target: String, previous: JSONObject): JSONObject {
    val action = MiniMapScenario.objects(view(core).getJSONObject("scene").getJSONArray("availableActions"))
      .first { it.getString("targetId") == target }
    val cmd = JSONObject(action.toString()).apply { remove("label") }
    core.beginSceneAction(cmd.toString(), text)
    val event = JSONObject(core.sceneNarrationContext())
    return JSONObject(core.commitSceneAction(cmd.getString("actionId"), event.getString("result"), previous.toString()))
  }
  @Test fun validInputIsRecordedBeforePlannerOrWriterAndFailureDoesNotEraseIt() {
    val (repo, core) = fixture(); val raw = "  Tôi xem xung quanh.\n  "
    val before = repo.state
    core.beginPlayerPresentation("request", raw, "{}")
    assertEquals(raw, lines(view(core), "player").single().getString("text"))
    core.finishPlayerPresentation(0, true, "Planner lỗi; chưa thực hiện bước nào.", "", "planner_failure", true)
    assertEquals(raw, lines(view(core), "player").single().getString("text"))
    assertTrue(lines(view(core), "gm").isEmpty())
    assertEquals(before.copy(world = before.world + (PlayerActionPresentation.JOURNAL to repo.state.world.getValue(PlayerActionPresentation.JOURNAL))), repo.state)
  }
  @Test fun unchangedDraftAfterZeroStepPlannerFailureIsOnePlayerTurnButEditedDraftIsNew() {
    val (_, core) = fixture(); val raw = "  Quan sát quanh mình.  "
    var display = JSONObject(core.beginPlayerPresentation("first", raw, "{}"))
    core.finishPlayerPresentation(0, true, "Planner lỗi; chưa có bước thực hiện.", "", "planner_failure", true)
    display = view(core, display)
    display = JSONObject(core.beginPlayerPresentation("retry", raw, display.toString()))
    assertEquals("first", display.getJSONObject("playerAction").getString("id"))
    assertEquals(1, lines(display, "player").size)
    core.finishPlayerPresentation(0, true, "Không xác định được hành động.", "", "planner_failure", false)
    display = JSONObject(core.beginPlayerPresentation("edited", "Đánh dấu vị trí.", display.toString()))
    assertEquals(2, lines(display, "player").size)
    assertEquals("edited", display.getJSONObject("playerAction").getString("id"))
  }

  @Test fun freeOnlyFailureAndSuccessfulRetryKeepOnePlayerAndOneMultilineGmWithoutGameplayChanges() {
    val (repo, core) = fixture(); val original = repo.state
    core.beginPlayerPresentation("free", "  Tôi gọi vào bóng tối.  ", "{}")
    core.setPlayerRequestCount(1); core.recordFreePlayerStep("Gọi vào bóng tối")
    core.finishPlayerPresentation(1, false, "GM chưa hoàn tất.", "", "writer_connection", true)
    var display = view(core)
    assertEquals(1, lines(display, "player").size); assertEquals(0, lines(display, "gm").size)
    assertTrue(display.getJSONObject("playerAction").getBoolean("recoverable"))
    assertThrows(IllegalArgumentException::class.java) { core.beginPlayerPresentation("duplicate", "Không được chạy lại.", display.toString()) }
    val retry = JSONObject(core.playerPresentationContext())
    assertEquals("Gọi vào bóng tối", retry.getJSONArray("events").getJSONObject(0).getString("text"))
    val prose = "Tiếng gọi lịm trong hành lang.\n\nKhông có lời đáp."
    core.finishPlayerPresentation(1, false, "", prose, "", false)
    display = view(core, display); display = view(core, display)
    assertEquals(1, lines(display, "player").size); assertEquals(prose, lines(display, "gm").single().getString("text"))
    assertEquals(original, repo.state.copy(world = repo.state.world - PlayerActionPresentation.JOURNAL))
    assertEquals(0, rng.calls)
  }
  @Test fun coreOnlyFactsAreNeverReportedAsSuccessfulGmAndRetryCannotReplayCommittedSteps() {
    val (repo, core) = fixture(); val raw = "Quan sát rồi đánh dấu."
    var display = JSONObject(core.beginPlayerPresentation("core", raw, "{}"))
    core.setPlayerRequestCount(2)
    display = commit(core, raw, "observe", display); display = commit(core, raw, "mark", display)
    val afterCommit = repo.state; val calls = rng.calls
    core.finishPlayerPresentation(2, false, "GM chưa kể.", "", "writer_json_invalid", false)
    display = view(core, display)
    assertEquals(1, lines(display, "player").size); assertEquals(0, lines(display, "gm").size)
    assertEquals(1, lines(display, "core").size)
    assertEquals(2, lines(display, "player").single().getJSONArray("sceneBatchActions").length())
    core.finishPlayerPresentation(2, false, "", "Hắn quan sát rồi ghi dấu.\n\nDấu mốc đã lưu.", "", false)
    repeat(3) { display = view(core, display) }
    assertEquals(1, lines(display, "gm").size); assertEquals(0, lines(display, "core").size)
    assertEquals(afterCommit.copy(world = afterCommit.world + (PlayerActionPresentation.JOURNAL to repo.state.world.getValue(PlayerActionPresentation.JOURNAL))), repo.state)
    assertEquals(calls, rng.calls)
    assertEquals("0", MiniMapRuntime.progress(repo.state).getString("nodeId"))
  }
  @Test fun mixedPartialFailureSurvivesProcessRestartAndManualSaveLoad() {
    val (repo, core) = fixture(); val raw = "Gọi vào bóng tối, đánh dấu, rồi đi tiếp."
    var display = JSONObject(core.beginPlayerPresentation("mixed", raw, "{}"))
    core.setPlayerRequestCount(3); core.recordFreePlayerStep("Gọi vào bóng tối")
    display = commit(core, raw, "mark", display)
    core.finishPlayerPresentation(3, true, "Dừng tại bước 3; chưa di chuyển.", "", "planner_failure", true)
    repo.state = GameStateCodec.decode(GameStateCodec.encode(repo.state))
    display = view(core, display)
    assertEquals(raw, lines(display, "player").single().getString("text"))
    assertTrue(lines(display, "player").single().getString("actionStatus").contains("1 bước chưa thực hiện"))
    assertEquals(2, JSONObject(core.playerPresentationContext()).getJSONArray("events").length())
    assertTrue(JSONObject(core.saveManualCheckpoint(display.toString())).getBoolean("ok"))
    core.finishPlayerPresentation(3, true, "", "Lời kể tạm.", "", false)
    val restored = JSONObject(core.loadManualCheckpoint()).getJSONObject("state")
    assertEquals(display.getJSONArray("log").toString(), restored.getJSONArray("log").toString())
    assertTrue(restored.getJSONObject("playerAction").getBoolean("recoverable"))
  }
  @Test fun pendingCoreRetryJoinsSameUtteranceAndReservesRngOnce() {
    val (repo, core) = fixture(); val raw = "Đánh dấu rồi di chuyển."
    var display = JSONObject(core.beginPlayerPresentation("pending", raw, "{}")); core.setPlayerRequestCount(2)
    display = commit(core, raw, "mark", display)
    val action = MiniMapScenario.objects(view(core).getJSONObject("scene").getJSONArray("availableActions")).first { it.getString("kind") == "MOVE" }
    val command = JSONObject(action.toString()).apply { remove("label") }
    core.beginSceneAction(command.toString(), raw); val calls = rng.calls
    core.finishPlayerPresentation(2, true, "Core chưa commit bước 2.", "", "core_failure", false)
    repo.state = GameStateCodec.decode(GameStateCodec.encode(repo.state))
    core.beginSceneAction(core.retrySceneAction())
    val event = JSONObject(core.sceneNarrationContext())
    core.commitSceneAction(command.getString("actionId"), event.getString("result"), display.toString())
    val after = repo.state
    core.commitSceneAction(command.getString("actionId"), "Không được ghi đè.", display.toString())
    assertEquals(after, repo.state); assertEquals(calls, rng.calls)
    display = view(core, display)
    assertEquals(1, lines(display, "player").size)
    assertEquals(2, JSONObject(core.playerPresentationContext()).getJSONArray("events").length())
    assertEquals(2, lines(display, "player").single().getJSONArray("sceneBatchActions").length())
  }
  @Test fun rejectedCoreCommandLeavesPlayerAndConfirmedStepsIntact() {
    val (_, core) = fixture(); val raw = "Đi qua lối chưa biết."
    val display = JSONObject(core.beginPlayerPresentation("reject", raw, "{}"))
    assertThrows(Exception::class.java) { core.beginSceneAction("{}", raw) }
    core.finishPlayerPresentation(1, true, "Không có lối hợp lệ; chưa di chuyển.", "", "core_failure", false)
    val after = view(core, display)
    assertEquals(raw, lines(after, "player").single().getString("text"))
    assertEquals(0, lines(after, "core").size); assertEquals(0, lines(after, "gm").size)
  }
  @Test fun directPendingChoiceKeepsPlayerDuringWriterFailureAndAddsExactlyOneGmOnRetry() {
    val (_, core) = fixture()
    val raw = "  Nhìn kỹ căn phòng.  "
    val action = MiniMapScenario.objects(view(core).getJSONObject("scene").getJSONArray("availableActions"))
      .first { it.getString("targetId") == "observe" }
    val command = JSONObject(action.toString()).apply { remove("label") }
    core.beginSceneAction(command.toString(), raw)
    var display = view(core)
    assertEquals(raw, lines(display, "player").single().getString("text"))
    assertEquals(0, lines(display, "gm").size)
    display = view(core, display)
    assertEquals(1, lines(display, "player").size)
    core.beginSceneAction(core.retrySceneAction())
    core.commitSceneAction(command.getString("actionId"), "Hắn nhìn căn phòng.\n\nKhông có gì mới.", display.toString())
    repeat(3) { display = view(core, display) }
    assertEquals(1, lines(display, "player").size); assertEquals(1, lines(display, "gm").size)
    assertFalse(lines(display, "player").single().has("pendingSceneAction"))
  }

  @Test fun writerContextOmitsAtlasControlsAndPrologueButRetainsBoundedChronologicalContext() {
    val source = JSONObject().put("log", JSONArray().put(JSONObject().put("text", "x".repeat(33700))))
      .put("scene", JSONObject().put("map", JSONObject().put("overviewRooms", "SECRET"))
        .put("records", "ANSWER").put("availableActions", "UNLOCK").put("room", JSONObject().put("name", "Vàng Úa")))
    for (i in 1..8) source.getJSONArray("log").put(JSONObject().put("role", "gm").put("text", "Cảnh $i"))
    val packet = PlayerActionPresentation.writerView(source)
    assertFalse(packet.toString().contains("SECRET")); assertFalse(packet.toString().contains("ANSWER")); assertFalse(packet.toString().contains("UNLOCK"))
    assertTrue(packet.toString().length < 1000)
    assertEquals("Cảnh 5", packet.getJSONArray("recentContext").getJSONObject(0).getString("text"))
    assertEquals("Cảnh 8", packet.getJSONArray("recentContext").getJSONObject(3).getString("text"))
  }
}
