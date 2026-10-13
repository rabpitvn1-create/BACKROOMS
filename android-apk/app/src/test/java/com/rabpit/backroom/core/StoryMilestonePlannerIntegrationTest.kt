package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryMilestonePlannerIntegrationTest {
  private class MemorySave : SaveRepository {
    var stored: GameState? = null
    var checkpoint: GameCheckpoint? = null
    override fun save(state: GameState) { stored = state }
    override fun load(): GameState = stored ?: GameState.initial()
    override fun exists(): Boolean = stored != null
    override fun clear() { stored = null; checkpoint = null }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { checkpoint = GameCheckpoint(state, JSONObject(presentation.toString())) }
    override fun loadCheckpoint(): GameCheckpoint? = checkpoint
  }

  private fun facade(repo: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }

  private fun plan(id: String = "CP-L00-01"): String = JSONObject()
    .put("schemaVersion", 2).put("checkpointId", id)
    .put("events", TestCheckpointQuota.events(JSONArray().put(JSONObject()
      .put("id", "boss").put("turn", 3).put("kind", "FINAL_ENTITY").put("action", "TEST_ONLY world action")
      .put("event", "Hound xuất hiện").put("outcome", "Combat bắt đầu")
      .put("entityKey", "hound").put("hpMultiplier", 9).put("expMultiplier", 9))))
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
    assertEquals(1, JSONObject(core.currentClientState("{}")).getJSONObject("story").getInt("generatedMilestones"))
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
    assertEquals(0, JSONObject(core.currentClientState("{}")).getJSONObject("story").getInt("generatedMilestones"))
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
  @Test fun coreRejectsClientLevelChangesAndItemTurnSkipping() {
    val repo = MemorySave()
    val core = facade(repo)
    val lease = JSONObject(core.milestonePlanRequest())
    val duePlan = JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("turn", 1) }.toString()
    assertTrue(core.installMilestonePlan(duePlan, lease.getString("runToken"), "CP-L00-01", 1))
    val before = JSONObject(core.currentClientState("{}"))
    val forbidden = JSONObject(before.toString()).put("level", LevelRouteRegistry.node("1").asJson())
    val rejected = JSONObject(core.processValidatedCandidate(before.toString(), forbidden.toString(), "TEST_ONLY action"))
    assertFalse(rejected.getBoolean("handled"))
    assertEquals("milestone_level_change_forbidden", rejected.getString("error"))
    assertEquals(1, CoreClientProjection.turnIndex(repo.stored!!))
    val item = JSONObject(core.processItemAction(before.toString(), "{}"))
    assertEquals("milestone_requires_gm", item.getString("error"))
    assertEquals(1, CoreClientProjection.turnIndex(repo.stored!!))
  }

  @Test fun crossLevelPrefetchLeaseUsesAuthoredTargetAndLateResponseAfterResetIsRejected() {
    val repo = MemorySave()
    val core = facade(repo)
    core.milestonePlanRequest()
    val first = repo.stored!!
    val last = first.copy(world = first.world + ("story.checkpoint" to
      JSONObject().put("level", 0).put("index", 20).put("total", 20).toString()))
    val raw = JSONObject(plan("CP-L00-20")).apply {
      getJSONArray("events").getJSONObject(0).put("turn", 1)
      put("completion", JSONObject().put("type", "EXIT_LEVEL").put("targetNodeId", "1.2")
        .put("nextCheckpointTotal", 3).put("outcome", "TEST_ONLY ending"))
    }.toString()
    val boss = TestMilestoneTurn.accept(TestCheckpointQuota.reachBoss(StoryMilestonePlan.installCurrent(last, raw)), "[\"boss\"]")
    repo.save(boss)
    val lease = JSONObject(core.milestoneNextPlanRequest())
    assertEquals("CP-L01-01", lease.getString("nextCheckpointId"))
    assertEquals("1.2", lease.getString("levelNode"))
    assertEquals(3, lease.getInt("checkpointTotal"))
    val before = repo.stored!!
    assertThrows(Exception::class.java) {
      core.installNextMilestone(plan("CP-L01-02"), lease.getString("runToken"),
        lease.getString("checkpointId"), lease.getString("bossEncounterId"))
    }
    assertEquals(before, repo.stored)
    core.resetNewGame()
    assertFalse(core.installNextMilestone(plan("CP-L01-01"), lease.getString("runToken"),
      lease.getString("checkpointId"), lease.getString("bossEncounterId")))
  }

  @Test fun manualLoadInvalidatesPendingPrefetchAndCanRecoverAnExplicitLoadError() {
    val repo = MemorySave()
    val core = facade(repo)
    val request = JSONObject(core.milestonePlanRequest())
    val raw = JSONObject(plan()).apply { getJSONArray("events").getJSONObject(0).put("turn", 1) }.toString()
    core.installMilestonePlan(raw, request.getString("runToken"), "CP-L00-01", 1)
    repo.save(TestMilestoneTurn.accept(TestCheckpointQuota.reachBoss(repo.stored!!), "[\"boss\"]"))
    val lease = JSONObject(core.milestoneNextPlanRequest())
    assertTrue(JSONObject(core.saveManualCheckpoint(core.currentClientState("{}"))).getBoolean("ok"))
    repo.save(GameState.initial().copy(metadata = mapOf("loadError" to "TEST_ONLY corrupt current slot")))
    assertThrows(Exception::class.java) { core.currentClientState("{}") }
    assertTrue(JSONObject(core.loadManualCheckpoint()).getBoolean("ok"))
    assertNotEquals(lease.getString("runToken"), repo.stored!!.world["story.runToken"])
    assertFalse(core.installNextMilestone(plan("CP-L00-02"), lease.getString("runToken"),
      lease.getString("checkpointId"), lease.getString("bossEncounterId")))
    assertNotNull(CombatRuntime.active(repo.stored!!))
  }

  @Test fun localNodeMovementRemainsAvailableWithoutLegacyExitReadiness() {
    val repo = MemorySave()
    val core = facade(repo)
    val lease = JSONObject(core.milestonePlanRequest())
    core.installMilestonePlan(plan(), lease.getString("runToken"), "CP-L00-01", 1)
    val before = JSONObject(core.currentClientState("{}"))
    val local = JSONObject(before.toString()).put("level", LevelRouteRegistry.node("0.1").asJson())
      .put("location", LevelRouteRegistry.node("0.1").location())
    val result = JSONObject(core.processValidatedCandidate(before.toString(), local.toString(), "TEST_ONLY local movement"))
    assertTrue(result.getBoolean("handled"))
    assertEquals("0.1", result.getJSONObject("state").getJSONObject("level").getString("id"))
    assertEquals("CP-L00-01", StoryCheckpointCore.current(repo.stored!!)!!.id)
  }

}



