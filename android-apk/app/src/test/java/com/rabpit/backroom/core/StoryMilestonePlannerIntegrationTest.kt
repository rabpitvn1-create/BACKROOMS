package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryMilestonePlannerIntegrationTest {
  private class MemorySave : SaveRepository {
    var stored: GameState? = null
    override fun save(state: GameState) { stored = state }
    override fun load(): GameState = stored ?: GameState.initial()
    override fun exists(): Boolean = stored != null
    override fun clear() { stored = null }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {}
    override fun loadCheckpoint(): GameCheckpoint? = null
  }

  private fun facade(repo: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }

  private fun plan(id: String = "CP-L00-01"): String = JSONObject()
    .put("schemaVersion", 1).put("checkpointId", id)
    .put("events", JSONArray().put(JSONObject()
      .put("id", "boss").put("turn", 3).put("kind", "FINAL_ENTITY")
      .put("event", "Hound xuất hiện").put("outcome", "Combat bắt đầu")
      .put("entityKey", "hound").put("hpMultiplier", 9).put("expMultiplier", 9)))
    .toString()

  @Test fun onlyFirstPlayableCheckpointRequiresOnePlannerCall() {
    val repo = MemorySave()
    val core = facade(repo)
    val request = JSONObject(core.milestonePlanRequest())
    assertTrue(request.getBoolean("needed"))
    assertEquals("CP-L00-01", request.getString("checkpointId"))
    assertEquals(1, request.getInt("startTurn"))
    assertTrue(request.getString("runToken").isNotEmpty())
    assertEquals("0", request.getString("levelNode"))
    assertTrue(core.installMilestonePlan(plan(), request.getString("runToken"),
      request.getString("checkpointId"), request.getInt("startTurn")))
    assertFalse(JSONObject(core.milestonePlanRequest()).getBoolean("needed"))
    assertEquals("CP-L00-01", StoryMilestonePlan.current(repo.stored!!)!!.plan.checkpointId)
    val decoded = GameStateCodec.decode(GameStateCodec.encode(repo.stored!!))
    assertEquals("CP-L00-01", StoryMilestonePlan.current(decoded)!!.plan.checkpointId)
  }

  @Test fun staleGeminiResponseCannotMutateRestartedOrAdvancedRun() {
    val repo = MemorySave()
    val core = facade(repo)
    val lease = JSONObject(core.milestonePlanRequest())
    core.resetNewGame()
    assertFalse(core.installMilestonePlan(plan(), lease.getString("runToken"),
      lease.getString("checkpointId"), lease.getInt("startTurn")))
    assertNull(StoryMilestonePlan.current(repo.stored!!))
    val fresh = JSONObject(core.milestonePlanRequest())
    assertNotEquals(lease.getString("runToken"), fresh.getString("runToken"))
    repo.save(CoreClientProjection.advance(repo.stored!!))
    assertFalse(core.installMilestonePlan(plan(), fresh.getString("runToken"),
      fresh.getString("checkpointId"), fresh.getInt("startTurn")))
  }

  @Test fun invalidMilestoneIsNeverPersistedOrAccepted() {
    val repo = MemorySave()
    val core = facade(repo)
    val request = JSONObject(core.milestonePlanRequest())
    val before = GameStateCodec.encode(repo.stored!!)
    assertThrows(Exception::class.java) {
      core.installMilestonePlan(JSONObject(plan()).put("checkpointId", "CP-L00-19").toString(),
        request.getString("runToken"), request.getString("checkpointId"), 1)
    }
    assertEquals(before, GameStateCodec.encode(repo.stored!!))
    assertTrue(JSONObject(core.milestonePlanRequest()).getBoolean("needed"))
  }

  @Test fun plannerPromptOnlyIncludesRealCombatEntityKeysAndLightCanon() {
    val prompt = StoryMilestonePlannerPrompt.build(
      "CP-L00-01", 1, "0", "<ENTITY.HOUND> Dangerous, four-legged predator.", "Level 0, empty lobby")
    assertTrue(prompt.contains("blackroot_sentinel"))
    assertTrue(prompt.contains("hound"))
    assertTrue(prompt.contains("hpMultiplier"))
    assertTrue(prompt.contains("expMultiplier"))
    assertTrue(prompt.contains("Checkpoint"))
    assertFalse(prompt.contains("fake_entity"))
    assertTrue(prompt.length < 8500)
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlannerPrompt.build("bad", 1, "0", "", "")
    }
  }
}
