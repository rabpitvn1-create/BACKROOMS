package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Level0SatelliteWorldDefinitionTest {
  private val nodes = listOf("0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms")

  @Test fun eachNodeHasExactlyFourMainZonesAndFourDistinctSubzonesPerZone() {
    assertEquals(nodes.toSet(), Level0SatelliteEnvironments.catalogs.keys)
    for (id in nodes) {
      val definition = WorldDefinitions.require(id)
      val zones = definition.environments.filter { it.id == it.zoneId }
      assertEquals(id, definition.nodeId)
      assertEquals(20, definition.environments.size)
      assertEquals(4, zones.size)
      assertEquals(20, definition.environments.map { it.id }.toSet().size)
      assertTrue(definition.environments.all { it.canon.length >= 65 && it.name.isNotBlank() })
      for (zone in zones) {
        assertEquals(4, definition.environments.count { it.zoneId == zone.id && it.id != zone.id })
      }
      assertEquals(16, definition.fieldStudies.size)
      assertTrue(definition.fieldStudies.all { it.observation.isNotBlank() })
      assertTrue(definition.requireAlternateRoutes)
      assertEquals(2, definition.evidenceLaws.size)
      assertEquals(definition.evidenceLaws.map { it.id }.toSet(), definition.passageConditions)
      assertEquals(4, definition.evidenceLaws.sumOf { it.inferenceSteps.size })
      assertEquals(8, definition.evidenceLaws.sumOf { it.probes.size })
    }
    assertSame(Level0WorldDefinition, WorldDefinitions.require("0"))
    for (area in listOf("dullness", "epsilon", "ls_2")) {
      val definition = WorldDefinitions.require(area)
      assertEquals(20, definition.environments.size)
      assertEquals(2, definition.evidenceLaws.size)
      assertEquals(4, definition.environments.count { it.id == it.zoneId })
    }
    assertNotNull(WorldDefinitions.forNode("1"))
  }

  @Test fun allGeneratedLayoutsAreDeterministicAndHaveNoSoftlockingBridge() {
    for (node in nodes) for (seed in listOf(0L, 123L, 987654321L)) {
      val definition = WorldDefinitions.require(node)
      val graph = definition.generate(seed)
      WorldGraphRuntime.validate(graph, definition)
      assertEquals(graph.toString(), definition.generate(seed).toString())
      assertEquals(20, graph.getJSONArray("rooms").length())
      assertEquals(0, graph.getJSONArray("passages").length())
      val rooms = graph.getJSONArray("rooms")
      val byId = (0 until rooms.length()).map { rooms.getJSONObject(it) }.associateBy { it.getString("id") }
      for (room in byId.values) {
        val siblings = room.getJSONArray("adjacentRoomIds")
        assertTrue(siblings.length() >= 2)
        for (i in 0 until siblings.length()) {
          val adjacent = byId.getValue(siblings.getString(i))
          val reverse = adjacent.getJSONArray("adjacentRoomIds")
          assertTrue((0 until reverse.length()).any { reverse.getString(it) == room.getString("id") })
        }
      }
      val altered = JSONObject(graph.toString())
      altered.getJSONArray("rooms").getJSONObject(0).put("environmentId", "forged")
      try {
        WorldGraphRuntime.validate(altered, definition)
        fail("forged environment accepted")
      } catch (_: IllegalArgumentException) { /* expected */ }
    }
  }

  @Test fun currentVersionWorldProgressSurvivesCoreSerialization() {
    for (node in nodes) {
      val initial = CoreClientProjection.initialize(GameState.initial()).let {
        it.copy(world = it.world + ("levelJson" to LevelRouteRegistry.node(node).asJson().toString()))
      }
      val definition = WorldDefinitions.require(node)
      val active = WorldGraphRuntime.initialize(initial, 420L, definition)
      val progress = MiniMapRuntime.progress(active)
      WorldGraphRuntime.validateSavedProgress(progress, definition)
      val encoded = JSONObject(GameStateCodec.encode(active)).getJSONObject("world")
        .getString(MiniMapRuntime.STATE)
      WorldGraphRuntime.validateSavedProgress(JSONObject(encoded), definition)
      assertEquals(node, progress.getString("nodeId"))
      assertEquals(1, progress.getJSONArray("visitedRooms").length())
      assertNotNull(WorldGraphRuntime.publicView(active, progress, definition)
        .getJSONObject("environment").getString("canon"))
    }
  }
}
