package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** All exact nodes must be traversed in the authored order by real Core exits. */
class MandatoryLevel0RouteTest {
  private val route = listOf("0", "0.1", "0.2", "0.5", "0.7",
    "dullness", "epsilon", "ls_2", "manila_room", "the_torment", "red_rooms", "1")

  private fun initial(node: String): GameState {
    val core = CoreClientProjection.initialize(GameState.initial())
    return core.copy(world = core.world + ("levelJson" to LevelRouteRegistry.node(node).asJson().toString()))
  }
  private fun progress(state: GameState) = MiniMapRuntime.progress(state)
  private fun finished(state: GameState) =
    Level0RiddleTestSupport.visitEveryRoom(Level0RiddleTestSupport.solveAll(state))

  private class MemorySave(var current: GameState) : SaveRepository {
    override fun save(state: GameState) { current = state }
    override fun load() = current
    override fun exists() = true
    override fun clear() {}
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {}
    override fun loadCheckpoint(): GameCheckpoint? = null
  }
  private fun facade(save: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(
      SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(save, NoOpGamePipelineLogger)
  }
  private fun exit(state: GameState, destination: String): JSONObject =
    MiniMapScenario.objects(WorldGraphRuntime.actions(state, progress(state)))
      .single { it.getString("targetId") == "exit:$destination" }
      .let { JSONObject(it.toString()).apply { remove("label") } }

  @Test fun everyStageIsLockedUntilTwoDistinctRiddlesAndEveryEnvironmentAreComplete() {
    for (node in route.dropLast(1)) {
      val definition = WorldDefinitions.require(node)
      val started = WorldGraphRuntime.initialize(initial(node), 1234L, definition)
      assertEquals(if (node == "0") 4 else 2, definition.evidenceLaws.size)
      assertFalse(WorldGraphRuntime.exitUnlocked(progress(started)))
      val visitedOnly = Level0RiddleTestSupport.visitEveryRoom(started)
      assertFalse("visited bypass: $node", WorldGraphRuntime.exitUnlocked(progress(visitedOnly)))
      val completed = finished(started)
      val p = progress(completed)
      assertTrue("completed: $node", WorldGraphRuntime.exitUnlocked(p))
      WorldGraphRuntime.validateSavedProgress(p, definition)
      val next = LevelRouteRegistry.exits(node).single()
      assertEquals(route[route.indexOf(node) + 1], next)
      assertEquals("exit:$next", exit(completed, next).getString("targetId"))
      for (wrong in route.filter { it != next })
        assertFalse("skipping $node -> $wrong", LevelRouteRegistry.canMove(node, wrong))
      // A forged destination is not an offered Core action.
      val forged = JSONObject(exit(completed, next).toString()).put("targetId", "exit:1")
      if (next != "1") assertThrows(Exception::class.java) {
        WorldGraphRuntime.stage(completed, forged, JSONObject(p.toString()))
      }
    }
  }

  @Test fun allElevenDeparturesCommitThroughCoreAndReachParkingZoneWithoutSkippingAreaNodes() {
    val save = MemorySave(WorldGraphRuntime.initialize(initial("0"), 73L, Level0WorldDefinition))
    val facade = facade(save)
    val visitedNodes = mutableListOf<String>()
    for (i in 0 until route.size - 1) {
      val source = route[i]; val target = route[i + 1]
      assertEquals(source, progress(save.current).getString("nodeId"))
      val completed = finished(save.current)
      save.save(completed)
      WorldGraphRuntime.validateSavedProgress(progress(save.current))
      val command = exit(save.current, target)
      assertFalse(JSONObject(facade.beginSceneAction(command.toString())).getBoolean("committed"))
      val pending = JSONObject(save.current.world.getValue(MiniMapRuntime.PENDING))
      assertEquals(source, pending.getJSONObject("progress").getString("nodeId"))
      assertEquals(source, progress(save.current).getString("nodeId"))
      assertEquals(source, JSONObject(save.current.world.getValue("levelJson")).getString("id"))
      val view = facade.currentClientState("{}")
      facade.commitSceneAction(command.getString("actionId"), "Đã xác nhận lối nối.", view)
      assertFalse(save.current.world.containsKey(MiniMapRuntime.PENDING))
      assertEquals(target, progress(save.current).getString("nodeId"))
      assertEquals(target, JSONObject(save.current.world.getValue("levelJson")).getString("id"))
      WorldGraphRuntime.validateSavedProgress(progress(save.current), WorldDefinitions.require(target))
      val loaded = GameStateCodec.decode(GameStateCodec.encode(save.current))
      assertEquals(target, progress(loaded).getString("nodeId"))
      visitedNodes.add(source)
    }
    visitedNodes.add(progress(save.current).getString("nodeId"))
    assertEquals(route, visitedNodes)
    assertEquals("1", progress(save.current).getString("nodeId"))
    val arrival = WorldGraphRuntime.publicView(save.current, progress(save.current))
    assertEquals("Sảnh Đỗ Xe", arrival.getJSONObject("environment").getString("name"))
    assertFalse(arrival.getBoolean("nodeExitUnlocked"))
  }

  @Test fun eachAreaHasIndependentSeededMapAndExactlyFourZonesWithFourSubzones() {
    for (node in listOf("dullness", "epsilon", "ls_2")) {
      val def = WorldDefinitions.require(node)
      assertEquals(20, def.environments.size)
      val zoneHubs = def.environments.filter { it.id == it.zoneId }
      assertEquals(4, zoneHubs.size)
      for (hub in zoneHubs)
        assertEquals(4, def.environments.count { it.zoneId == hub.id && it.id != hub.id })
      for (seed in listOf(0L, 39L, Long.MIN_VALUE)) {
        val state = WorldGraphRuntime.initialize(initial(node), seed, def)
        WorldGraphRuntime.validateSavedProgress(progress(state), def)
        val graph = progress(state).getJSONObject("worldGraph")
        assertEquals(graph.toString(), def.generate(seed).toString())
        assertEquals(20, graph.getJSONArray("rooms").length())
      }
    }
  }
}
