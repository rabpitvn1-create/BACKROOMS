package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class FreeActionTurnTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) = Unit
    override fun loadCheckpoint(): GameCheckpoint? = null
  }

  private class ScriptedRng(private val sequence: IntArray) : SecureRandom() {
    var calls = 0
    override fun nextInt(bound: Int): Int {
      assertEquals(10000, bound)
      return sequence.getOrElse(calls++) { 9999 }
    }
  }

  private fun fixture(rng: ScriptedRng): Pair<MemorySave, GameCoreFacade> {
    val repo = MemorySave(WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 41L))
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(repo, NoOpGamePipelineLogger)
    GameCoreFacade::class.java.getDeclaredField("spawnRng").apply { isAccessible = true }.set(core, rng)
    return repo to core
  }

  @Test fun freeTurnsSpendTimeAndRollIndependentlyAndRetryIsIdempotent() {
    val rng = ScriptedRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val initial = repo.state
    val raw = "Gọi vào bóng tối rồi chạm nhẹ lên tường"
    core.beginPlayerPresentation("free", raw, "{}")
    core.setPlayerRequestCount(2)
    val first = JSONObject(core.executePlayerFreeStep("Gọi vào bóng tối", raw, "{}"))
    val second = JSONObject(core.executePlayerFreeStep("Chạm nhẹ lên tường", raw, first.getJSONObject("state").toString()))
    assertEquals(initial.time.elapsedSubjectiveMinutes + 2, repo.state.time.elapsedSubjectiveMinutes)
    assertEquals(4, rng.calls)
    assertEquals(2, JSONObject(core.playerPresentationContext()).getJSONArray("receipts").length())
    assertEquals(2, JSONObject(core.playerPresentationContext()).getJSONArray("events").length())
    assertEquals("FREE", JSONObject(core.playerPresentationContext()).getJSONArray("events").getJSONObject(0).getString("kind"))
    assertEquals(MiniMapRuntime.progress(initial).getString("roomId"), MiniMapRuntime.progress(repo.state).getString("roomId"))
    assertEquals(MiniMapRuntime.progress(initial).getInt("version") + 2, MiniMapRuntime.progress(repo.state).getInt("version"))
    core.finishPlayerPresentation(2, false, "GM chưa trả lời", "", "writer_connection", true)
    val saved = repo.state
    core.finishPlayerPresentation(2, false, "", "Có tiếng vọng nhẹ trong phòng.", "", false)
    assertEquals(saved.time.elapsedSubjectiveMinutes, repo.state.time.elapsedSubjectiveMinutes)
    assertEquals(4, rng.calls)
    assertEquals(1, MiniMapScenario.objects(JSONObject(core.currentClientState("{}")).getJSONArray("log"))
      .count { it.optString("role") == "gm" })
  }

  @Test fun entityFromFreeActionStopsFurtherGameplayWithoutReroll() {
    val rng = ScriptedRng(intArrayOf(9999, 9999, 0, 9999))
    val (repo, core) = fixture(rng)
    val raw = "Gõ cửa rồi gọi lớn rồi bước thêm"
    core.beginPlayerPresentation("free-encounter", raw, "{}")
    core.setPlayerRequestCount(3)
    core.executePlayerFreeStep("Gõ cửa", raw, "{}")
    val before = repo.state.time.elapsedSubjectiveMinutes
    val second = JSONObject(core.executePlayerFreeStep("Gọi lớn", raw, "{}"))
    assertTrue(second.getBoolean("encounter"))
    assertNotNull(CombatRuntime.pending(repo.state))
    assertEquals(before + 1, repo.state.time.elapsedSubjectiveMinutes)
    assertEquals(4, rng.calls)
    assertThrows(Exception::class.java) { core.executePlayerFreeStep("Bước thêm", raw, "{}") }
    assertEquals(4, rng.calls)
    assertEquals(2, JSONObject(core.playerPresentationContext()).getJSONArray("receipts").length())
    assertEquals(repo.state, GameStateCodec.decode(GameStateCodec.encode(repo.state)))
  }

  @Test fun forgedOrUnstagedFreeCommandCannotConsumeRng() {
    val rng = ScriptedRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val cmd = MiniMapRuntime.freeCommand(repo.state)
    assertThrows(Exception::class.java) { core.beginSceneAction(cmd.toString(), "Hành động không được phép") }
    cmd.put("actionId", "forged")
    assertThrows(Exception::class.java) { core.beginSceneAction(cmd.toString(), "Hành động", "Gõ cửa") }
    assertEquals(0, rng.calls)
  }

  @Test fun searchAndInteractionEachGetIndependentEntityRolls() {
    val rng = ScriptedRng(intArrayOf())
    val (_, core) = fixture(rng)
    for (target in listOf("observe", "trace")) {
      val scene = JSONObject(core.currentClientState("{}")).getJSONObject("scene")
      val action = MiniMapScenario.objects(scene.getJSONArray("availableActions"))
        .first { it.getString("targetId") == target }
      val command = JSONObject(action.toString()).apply { remove("label") }
      core.beginSceneAction(command.toString(), "Tôi " + target)
      val result = JSONObject(core.sceneNarrationContext())
      core.commitSceneAction(command.getString("actionId"), result.getString("result"), "{}")
    }
    assertEquals(4, rng.calls)
  }
}
