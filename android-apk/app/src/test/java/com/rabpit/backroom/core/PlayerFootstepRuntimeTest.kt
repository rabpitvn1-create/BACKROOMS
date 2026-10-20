package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class PlayerFootstepRuntimeTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) = Unit
    override fun loadCheckpoint(): GameCheckpoint? = null
  }
  private class SequenceRng(private val rolls: IntArray) : SecureRandom() {
    var calls = 0
    override fun nextInt(bound: Int): Int {
      assertEquals(10000, bound)
      return rolls.getOrElse(calls++) { bound - 1 }
    }
  }
  private fun fixture(rng: SecureRandom): Pair<MemorySave, GameCoreFacade> {
    val repo = MemorySave(WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 41L))
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(repo, NoOpGamePipelineLogger)
    GameCoreFacade::class.java.getDeclaredField("spawnRng").apply { isAccessible = true }.set(core, rng)
    return repo to core
  }

  @Test fun threeFootstepsCommitThreeDistinctReceiptsWithoutChangingRooms() {
    val rng = SequenceRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val before = MiniMapRuntime.progress(repo.state)
    core.beginPlayerPresentation("steps", "Tiến lên 3 bước", "{}")
    val result = JSONObject(core.executePlayerFootsteps("Tiến lên 3 bước", 3, "{}"))
    val after = MiniMapRuntime.progress(repo.state)
    assertEquals(3, result.getInt("executed"))
    assertFalse(result.getBoolean("interrupted"))
    assertEquals(3, result.getJSONArray("events").length())
    assertEquals(before.getString("roomId"), after.getString("roomId"))
    assertEquals(before.getInt("version") + 3, after.getInt("version"))
    assertEquals(3, JSONObject(core.playerPresentationContext()).getJSONArray("receipts").length())
    assertEquals(6, rng.calls) // One Entity + one Level-0 Lucia eligibility roll per step.
    assertEquals(repo.state, GameStateCodec.decode(GameStateCodec.encode(repo.state)))
    core.finishPlayerPresentation(3, false, "GM đang lỗi", "", "writer_connection", true)
    assertTrue(JSONObject(core.currentClientState("{}")).getJSONObject("playerAction").getBoolean("recoverable"))
    assertEquals(6, rng.calls)
  }

  @Test fun entityOnSecondFootstepStopsThirdWithoutDuplicateRoll() {
    val rng = SequenceRng(intArrayOf(9999, 9999, 0, 9999))
    val (repo, core) = fixture(rng)
    core.beginPlayerPresentation("encounter", "Tiến thêm 3 bước", "{}")
    val result = JSONObject(core.executePlayerFootsteps("Tiến thêm 3 bước", 3, "{}"))
    assertEquals(2, result.getInt("executed"))
    assertTrue(result.getBoolean("interrupted"))
    assertEquals(2, result.getJSONArray("events").length())
    assertEquals(2, JSONObject(core.playerPresentationContext()).getJSONArray("receipts").length())
    assertNotNull(CombatRuntime.pending(repo.state))
    assertEquals(4, rng.calls)
    assertEquals(repo.state, GameStateCodec.decode(GameStateCodec.encode(repo.state)))
    assertThrows(Exception::class.java) { core.executePlayerFootsteps("Tiến thêm 3 bước", 3, "{}") }
    assertEquals(4, rng.calls)
  }

  @Test fun forgedFootstepCommandCannotConsumeRng() {
    val rng = SequenceRng(intArrayOf())
    val (repo, core) = fixture(rng)
    val cmd = WorldGraphRuntime.footstepCommand(MiniMapRuntime.progress(repo.state))
    cmd.put("actionId", "forged")
    assertThrows(Exception::class.java) { core.beginSceneAction(cmd.toString(), "Tiến 1 bước") }
    assertEquals(0, rng.calls)
  }
}
