package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Only the Core-owned graph can unlock an exact-node exit. */
class WorldExitProgressionTest {
  private fun initial(node: String): GameState {
    val base = CoreClientProjection.initialize(GameState.initial())
    return base.copy(world = base.world +
      ("levelJson" to LevelRouteRegistry.node(node).asJson().toString()))
  }

  private fun allVisitedWithProofs(): GameState =
    Level0RiddleTestSupport.visitEveryRoom(Level0RiddleTestSupport.solveAll(
      WorldGraphRuntime.initialize(initial("0"), 777L, Level0WorldDefinition)
    ))

  @Test fun level0ExitRemainsLockedUntilAll37AreasAndBothCoreProofs() {
    val s = WorldGraphRuntime.initialize(initial("0"), 777L, Level0WorldDefinition)
    val p = MiniMapRuntime.progress(s)
    assertFalse(WorldGraphRuntime.exitUnlocked(p))
    assertFalse(WorldGraphRuntime.publicView(s, p).getBoolean("nodeExitUnlocked"))
    assertFalse(MiniMapScenario.objects(WorldGraphRuntime.actions(s, p))
      .any { it.optString("targetId") == "exit:0.1" })

    val completed = allVisitedWithProofs()
    val completedProgress = MiniMapRuntime.progress(completed)
    assertTrue(WorldGraphRuntime.exitUnlocked(completedProgress))
    assertTrue(WorldGraphRuntime.publicView(completed, completedProgress).getBoolean("nodeExitUnlocked"))
    assertTrue(MiniMapScenario.objects(WorldGraphRuntime.actions(completed, completedProgress))
      .any { it.optString("targetId") == "exit:0.1" })

    val unproven = JSONObject(completedProgress.toString())
    unproven.getJSONObject("ruleFlags").remove("fracture-boundary")
    assertFalse(WorldGraphRuntime.exitUnlocked(unproven))
    val unvisited = JSONObject(completedProgress.toString())
    val arr = unvisited.getJSONArray("visitedRooms")
    val graphRooms = unvisited.getJSONObject("worldGraph").getJSONArray("rooms")
    val missed = (0 until graphRooms.length()).map { graphRooms.getJSONObject(it) }
      .first { it.getString("environmentId") == "phong_moc" }.getString("id")
    val kept = JSONArray()
    for (i in 0 until arr.length()) if (arr.getString(i) != missed) kept.put(arr.getString(i))
    unvisited.put("visitedRooms", kept)
    assertFalse(WorldGraphRuntime.exitUnlocked(unvisited))
  }

  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load(): GameState = state
    override fun exists(): Boolean = true
    override fun clear() {}
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {}
    override fun loadCheckpoint(): GameCheckpoint? = null
  }

  @Test fun satelliteDepartureRequiresRiddlesAndVisitedEnvironments() {
    val beginning = WorldGraphRuntime.initialize(initial("0.1"), 55L, WorldDefinitions.require("0.1"))
    val visitedOnly = Level0RiddleTestSupport.visitEveryRoom(beginning)
    assertFalse(WorldGraphRuntime.exitUnlocked(MiniMapRuntime.progress(visitedOnly)))
    val solvedOnly = Level0RiddleTestSupport.solveAll(beginning)
    assertFalse(WorldGraphRuntime.exitUnlocked(MiniMapRuntime.progress(solvedOnly)))
    val completed = Level0RiddleTestSupport.visitEveryRoom(solvedOnly)
    val p = MiniMapRuntime.progress(completed)
    assertTrue(WorldGraphRuntime.exitUnlocked(p))
    assertTrue(WorldGraphRuntime.publicView(completed, p).getBoolean("nodeExitUnlocked"))
    assertTrue(MiniMapScenario.objects(WorldGraphRuntime.actions(completed, p))
      .any { it.getString("targetId") == "exit:0.2" })
  }

  @Test fun fullCoreTransactionCommitsExactTransitionAndNewGraph() {
    val previous = allVisitedWithProofs()
    val source = MiniMapRuntime.progress(previous)
    val choice = MiniMapScenario.objects(WorldGraphRuntime.actions(previous, source))
      .first { it.getString("targetId") == "exit:0.1" }
    val command = JSONObject().put("actionId", choice.getString("actionId"))
      .put("sceneVersion", choice.getInt("sceneVersion"))
      .put("roomId", choice.getString("roomId"))
      .put("kind", choice.getString("kind")).put("targetId", choice.getString("targetId"))
    val save = MemorySave(previous)
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(save, NoOpGamePipelineLogger)
    assertFalse(JSONObject(core.beginSceneAction(command.toString())).getBoolean("committed"))
    val staged = MiniMapRuntime.progress(save.state)
    assertEquals("0", staged.getString("nodeId"))
    assertEquals("0", JSONObject(save.state.world.getValue("levelJson")).getString("id"))
    assertEquals("0", JSONObject(save.state.world.getValue(MiniMapRuntime.PENDING))
      .getJSONObject("progress").getString("nodeId"))
    val client = CoreClientProjection.project(JSONObject(), previous).toString()
    core.commitSceneAction(command.getString("actionId"), "Đã rời Level 0.", client)
    val destination = MiniMapRuntime.progress(save.state)
    assertEquals("0.1", JSONObject(save.state.world.getValue("levelJson")).getString("id"))
    assertEquals("0.1", destination.getString("nodeId"))
    assertEquals("0.1", destination.getJSONObject("worldGraph").getString("nodeId"))
    assertFalse(MiniMapRuntime.PENDING in save.state.world)
    WorldGraphRuntime.validateSavedProgress(destination, WorldDefinitions.require("0.1"))
    assertEquals("0.1", JSONObject(GameStateCodec.encode(save.state))
      .getJSONObject("world").getString("levelJson").let(::JSONObject).getString("id"))
  }

  @Test fun authorizedExitStagesPreviousGraphThenCreatesNextGraphOnCommit() {
    val completed = allVisitedWithProofs()
    val p = MiniMapRuntime.progress(completed)
    val departure = MiniMapScenario.objects(WorldGraphRuntime.actions(completed, p))
      .first { it.getString("targetId") == "exit:0.1" }
    val cmd = JSONObject().put("actionId", departure.getString("actionId"))
      .put("sceneVersion", departure.getInt("sceneVersion"))
      .put("roomId", departure.getString("roomId"))
      .put("kind", departure.getString("kind")).put("targetId", departure.getString("targetId"))
    val staged = WorldGraphRuntime.stage(completed, cmd, JSONObject(p.toString()))
    assertEquals("0.1", staged.nodeId)
    assertTrue(staged.event.getBoolean("transition"))
    assertEquals("0", staged.progress.getString("nodeId"))
    assertEquals("0", MiniMapRuntime.progress(completed).getString("nodeId"))
    assertEquals("0.1", LevelRouteRegistry.exits("0").single())
    assertFalse(LevelRouteRegistry.canMove("0", "manila_room"))
    assertFalse(LevelRouteRegistry.canMove("0.1", "0"))
  }
}
