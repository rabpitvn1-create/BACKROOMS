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

  @Test fun invalidlyLongSleepCannotLeaveAnUncommittablePendingScene() {
    val rng = ScriptedRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val before = repo.state
    val command = MiniMapRuntime.freeCommand(before)
    assertThrows(IllegalArgumentException::class.java) {
      core.beginSceneAction(command.toString(), "Tôi ngủ 200 giờ", "Tôi ngủ 200 giờ")
    }
    assertEquals(before, repo.state)
    assertNull(ActionRuntime.activeSession(repo.state))
    assertEquals(0, rng.calls)
  }

  @Test fun fatalDeprivationCommitsTimeButNotStagedSceneProgress() {
    val rng = ScriptedRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val leader = repo.state.characters.getValue(CAO_MINH_ID)
    repo.state = repo.state.copy(characters = repo.state.characters +
      (CAO_MINH_ID to leader.copy(
        physiology = PhysiologyState(minutesSinceFood = 0, minutesSinceWater = 809, minutesAwake = 0),
        vitalState = leader.vitalState.copy(currentHp = 1)
      )))
    val start = MiniMapRuntime.progress(repo.state)
    val cmd = MiniMapRuntime.freeCommand(repo.state)
    core.beginSceneAction(cmd.toString(), "Tôi bước tiếp", "Tôi bước tiếp")
    val event = JSONObject(core.sceneNarrationContext())
    core.commitSceneAction(cmd.getString("actionId"), event.getString("result"), "{}")
    val dead = repo.state.characters.getValue(CAO_MINH_ID)
    assertEquals(CharacterPresence.DEAD, dead.presence)
    assertEquals(0, dead.vitalState.currentHp)
    assertEquals(1L, repo.state.time.elapsedSubjectiveMinutes)
    assertEquals(start.toString(), MiniMapRuntime.progress(repo.state).toString())
    assertFalse(MiniMapRuntime.PENDING in repo.state.world)
    assertNull(CombatRuntime.pending(repo.state))
    assertEquals(true, JSONObject(repo.state.world.getValue(MiniMapRuntime.RECEIPT)).getBoolean("interrupted"))
    assertEquals(repo.state, GameStateCodec.decode(GameStateCodec.encode(repo.state)))
    val saved = repo.state
    core.commitSceneAction(cmd.getString("actionId"), "Do not replay", "{}")
    assertEquals(saved, repo.state)
  }

  @Test fun longSleepChecksEntityThreatDuringTheIntervalAndPersistsFirstInterruption() {
    // Initial scene roll fails; hourly checks fail at 60 then succeed at 120.
    val rng = ScriptedRng(intArrayOf(9999, 9999, 9999, 0))
    val (repo, core) = fixture(rng)
    val action = MiniMapRuntime.freeCommand(repo.state)
    core.beginSceneAction(action.toString(), "Tôi ngủ 3 giờ", "Tôi ngủ 3 giờ")
    val pending = JSONObject(repo.state.world.getValue(MiniMapRuntime.PENDING))
    assertEquals(120, pending.getInt("sleepInterruptAfterMinutes"))
    assertNotNull(CombatRuntime.pending(repo.state))
    val calls = rng.calls
    core.beginSceneAction(action.toString(), "Tôi ngủ 3 giờ", "Tôi ngủ 3 giờ")
    assertEquals(calls, rng.calls)
    val before = repo.state.time.elapsedSubjectiveMinutes
    val event = JSONObject(core.sceneNarrationContext())
    core.commitSceneAction(action.getString("actionId"), event.getString("result"), "{}")
    assertEquals(before + 120, repo.state.time.elapsedSubjectiveMinutes)
    assertNotNull(CombatRuntime.pending(repo.state))
    assertEquals(calls, rng.calls)
    assertEquals(repo.state, GameStateCodec.decode(GameStateCodec.encode(repo.state)))
  }

  @Test fun uninterruptedLongSleepUsesFullPlannedTime() {
    val rng = ScriptedRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val action = MiniMapRuntime.freeCommand(repo.state)
    core.beginSceneAction(action.toString(), "Tôi ngủ 3 giờ", "Tôi ngủ 3 giờ")
    assertEquals(0, JSONObject(repo.state.world.getValue(MiniMapRuntime.PENDING)).getInt("sleepInterruptAfterMinutes"))
    val event = JSONObject(core.sceneNarrationContext())
    core.commitSceneAction(action.getString("actionId"), event.getString("result"), "{}")
    assertEquals(180L, repo.state.time.elapsedSubjectiveMinutes)
    assertNull(CombatRuntime.pending(repo.state))
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
