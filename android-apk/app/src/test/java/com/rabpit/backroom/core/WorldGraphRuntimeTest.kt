package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldGraphRuntimeTest {
  /** Synthetic content at an existing node, never registered or shipped as Level 1 Canon. */
  private object FixtureDefinition : WorldDefinition {
    override val nodeId = "1"
    override val version = 7
    override val objective = "Mục tiêu kiểm thử riêng."
    override val requireAlternateRoutes = false
    override val environments = listOf(
      WorldEnvironment("fixture_a", "Khu thử A", "fixture_zone", "Quan sát thử A."),
      WorldEnvironment("fixture_b", "Khu thử B", "fixture_zone", "Quan sát thử B.")
    )
    override fun generate(seed: Long) = JSONObject().put("version", WorldGraphRuntime.GRAPH_VERSION)
      .put("definitionVersion", version).put("nodeId", nodeId).put("seed", seed.toString())
      .put("entryRoomId", "test:a").put("rooms", JSONArray().apply {
        // Different room count, ID format and topology; two rooms share one environment.
        for ((id, environment, adjacent) in listOf(
          Triple("test:a", "fixture_a", listOf("test:b")),
          Triple("test:b", "fixture_a", listOf("test:a", "test:c")),
          Triple("test:c", "fixture_b", listOf("test:b"))
        )) put(JSONObject().put("id", id).put("environmentId", environment)
          .put("zoneId", "fixture_zone").put("adjacentRoomIds", JSONArray(adjacent)))
      })
    override fun validateLayout(graph: JSONObject) {
      require(graph.getJSONArray("rooms").length() == 3) { "fixture_room_count" }
    }
  }
  private fun initial() = CoreClientProjection.initialize(GameState.initial()).let {
    it.copy(world = it.world + ("levelJson" to LevelRouteRegistry.node("1").asJson().toString()))
  }
  private fun progress(s: GameState) = MiniMapRuntime.progress(s)
  private fun act(s: GameState, target: String): GameState {
    val p = progress(s)
    val action = MiniMapScenario.objects(WorldGraphRuntime.actions(s, p, FixtureDefinition))
      .first { it.getString("targetId") == target }
    val command = JSONObject(action.toString()).apply { remove("label") }
    val staged = WorldGraphRuntime.stage(s, command, p, FixtureDefinition)
    assertEquals("1", staged.nodeId)
    assertEquals("1", staged.event.getString("node"))
    assertFalse(staged.event.has("zoneId")) // No Level 0 index inferred from environment IDs.
    return s.copy(world = s.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }

  @Test fun sameRuntimeExploresDifferentLevelWithMultipleRoomsPerEnvironmentAndRoundTripState() {
    var state = WorldGraphRuntime.initialize(initial(), 123, FixtureDefinition)
    assertEquals(3, progress(state).getJSONObject("worldGraph").getJSONArray("rooms").length())
    state = act(act(state, "observe"), "mark")
    state = act(act(state, "room:test:b"), "room:test:c")
    state = act(state, "observe")
    // Generic persistence payload round-trip with explicit test-only definition.
    val encoded = JSONObject(GameStateCodec.encode(state)).getJSONObject("world").getString(MiniMapRuntime.STATE)
    val loaded = JSONObject(encoded)
    WorldGraphRuntime.validateSavedProgress(loaded, FixtureDefinition)
    state = state.copy(world = state.world + (MiniMapRuntime.STATE to loaded.toString()))
    val view = WorldGraphRuntime.publicView(state, progress(state), FixtureDefinition)
    assertEquals("1", view.getString("nodeId")); assertEquals(1, view.getInt("parentLevel"))
    assertEquals(FixtureDefinition.objective, view.getString("objective"))
    assertEquals("fixture_b", view.getJSONObject("environment").getString("id"))
    assertEquals("fixture_b", view.getJSONObject("map").getJSONObject("snapshots")
      .getString(view.getJSONObject("map").getString("currentRoomId")))
    state = act(act(state, "room:test:b"), "room:test:a")
    assertTrue(progress(state).getJSONObject("markers").getBoolean("test:a"))
    assertEquals(2, progress(state).getJSONArray("records").length())
    assertEquals(3, progress(state).getJSONArray("visitedRooms").length())
  }

  @Test fun redundancyIsDefinitionPolicyWhileReferenceAndReachabilityChecksAreShared() {
    val graph = FixtureDefinition.generate(4)
    WorldGraphRuntime.validate(graph, FixtureDefinition)
    val redundant = object : WorldDefinition by FixtureDefinition { override val requireAlternateRoutes = true }
    assertEquals("world_single_passage_softlock", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.validate(graph, redundant)
    }.message)
    val unknown = JSONObject(graph.toString())
    unknown.getJSONArray("rooms").getJSONObject(1).put("environmentId", "yellow_rot")
    assertEquals("world_environment", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.validate(unknown, FixtureDefinition)
    }.message)
    val isolated = JSONObject(graph.toString())
    isolated.getJSONArray("rooms").getJSONObject(1).put("adjacentRoomIds", JSONArray().put("test:a"))
    isolated.getJSONArray("rooms").getJSONObject(2).put("adjacentRoomIds", JSONArray())
    assertEquals("world_unreachable", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.validate(isolated, FixtureDefinition)
    }.message)
  }

  @Test fun nodeAndDefinitionVersionCannotBeReboundBySavedData() {
    val graph = FixtureDefinition.generate(1).put("nodeId", "0")
    assertEquals("world_definition_mismatch", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.validate(graph, FixtureDefinition)
    }.message)
    val version = FixtureDefinition.generate(1).put("definitionVersion", 8)
    assertThrows(IllegalArgumentException::class.java) { WorldGraphRuntime.validate(version, FixtureDefinition) }
    val valid = WorldGraphRuntime.initialize(initial(), 1, FixtureDefinition)
    val wrongNode = valid.copy(world = valid.world + ("levelJson" to LevelRouteRegistry.node("0").asJson().toString()))
    assertThrows(IllegalArgumentException::class.java) { WorldGraphRuntime.actions(wrongNode, progress(valid), FixtureDefinition) }
    val lyingGenerator = object : WorldDefinition by FixtureDefinition {
      override fun generate(seed: Long) = FixtureDefinition.generate(seed + 1)
    }
    assertEquals("world_seed_mismatch", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.initialize(initial(), 1, lyingGenerator)
    }.message)
  }

  @Test fun npcRoomIdentityIncludesNodeAndTestDefinitionNeverEnablesAProductionRoute() {
    val state = WorldGraphRuntime.initialize(initial(), 1, FixtureDefinition)
    val npc = state.characters.values.first { it.id != CAO_MINH_ID }.copy(presence = CharacterPresence.ACTIVE,
      metadata = mapOf("encountered" to "true", "world.nodeId" to "0", "world.roomId" to "test:a"))
    assertFalse(WorldGraphRuntime.characterPresent(state, npc))
    assertTrue(WorldGraphRuntime.characterPresent(state, npc.copy(metadata = npc.metadata + ("world.nodeId" to "1"))))
    assertNull(WorldDefinitions.forNode("1"))
    assertNotNull(WorldDefinitions.forNode("0"))
    assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(GameStateCodec.encode(state)) }
    assertFalse(LevelRouteRegistry.canMove("0", "1"))
  }
}
