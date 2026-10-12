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
    .put("schemaVersion", 2).put("checkpointId", id)
    .put("events", JSONArray().put(JSONObject()
      .put("id", "boss").put("turn", 3).put("kind", "FINAL_ENTITY").put("action", "TEST_ONLY world action")
      .put("event", "Hound xuất hiện").put("outcome", "Combat bắt đầu")
      .put("entityKey", "hound").put("hpMultiplier", 9).put("expMultiplier", 9)))
    .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "TEST_ONLY completion"))
    .toString()

  @Test fun onlyFirstPlayableCheckpointRequiresOnePlannerCall() {
    val repo = MemorySave()
    val core = facade(repo)
    val request = JSONObject(core.milestonePlanRequest())
    assertTrue(request.getBoolean("needed"))
    assertEquals("CP-L00-01", request.getString("checkpointId"))
    assertEquals(1, request.getInt("startTurn"))
    assertEquals(20, request.getInt("checkpointTotal"))
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
      "CP-L00-01", 1, "0", "<ENTITY.HOUND> Dangerous, four-legged predator.", "Level 0, empty lobby", 20)
    assertTrue(prompt.contains("blackroot_sentinel"))
    assertTrue(prompt.contains("hound"))
    assertTrue(prompt.contains("hpMultiplier"))
    assertTrue(prompt.contains("expMultiplier"))
    assertTrue(prompt.contains("Checkpoint"))
    assertFalse(prompt.contains("fake_entity"))
    assertTrue(prompt.length < 10000)
    assertTrue(prompt.contains("\"schemaVersion\":2"))
    assertTrue(prompt.contains("NEXT_CHECKPOINT"))
    val last = StoryMilestonePlannerPrompt.build("CP-L00-20", 1, "0", "", "", 20)
    assertTrue(last.contains("\"type\":\"EXIT_LEVEL\""))
    assertTrue(last.contains("1.2"))
    assertTrue(last.contains("nextCheckpointTotal"))
    // Preserve a complete, bounded Canon packet rather than clipping hard locks.
    val fullCanon = "TEST_ONLY ".repeat(600)
    assertTrue(StoryMilestonePlannerPrompt.build("CP-L00-01", 1, "0", fullCanon, "", 20).contains(fullCanon))
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlannerPrompt.build("CP-L01-01", 1, "0", "", "", 20)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestonePlannerPrompt.build("bad", 1, "0", "", "", 20)
    }
  }
}

