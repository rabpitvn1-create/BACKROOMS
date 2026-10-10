package com.rabpit.backroom.core

import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Regression for #551: Turn-1 New Game could never commit a GM result without a Core world node. */
class FreshNewGameWorldRegressionTest {
  @Test fun levelZeroOpenerSeedsCoreOwnedRouteAndResetsUntrustedStreak() {
    val input = JSONObject()
      .put("turn", 1)
      .put("level", JSONObject().put("number", 0).put("name", "The Lobby"))
      .put("title", "Level 0 – The Lobby")
      .put("location", "Level 0 / The Lobby")
      .put("inventory", org.json.JSONArray())
      .put("party", org.json.JSONArray())
      .put("flags", JSONObject().put("exploration", JSONObject().put("exitStreak", 4)))
    val migrated = LegacySaveMigration.migrate(input)
    val route = FeaturedJourneyRoutes.nodeIdAt("level-0")
    assertNotNull(route)
    assertEquals("level-0", migrated.world["journeyStopKey"])
    assertEquals(route, migrated.world["worldNodeId"])
    val level = JSONObject(migrated.world.getValue("levelJson"))
    assertEquals(0, level.getInt("number"))
    assertEquals("level-0", level.getString("stopKey"))
    assertEquals(route, level.getString("nodeId"))
    val flags = JSONObject(migrated.world.getValue("flagsJson"))
    assertEquals(0, flags.getJSONObject("exploration").getInt("exitStreak"))
    assertEquals("level-0", flags.getJSONObject("exploration").getString("exitStreakNode"))
    val saved = GameStateCodec.decode(GameStateCodec.encode(migrated))
    assertEquals(route, saved.world["worldNodeId"])
    assertEquals(migrated.world["levelJson"], saved.world["levelJson"])
  }

  @Test fun unknownOrLaterLegacySceneNeverManufacturesRoute() {
    for (turn in listOf(1, 2)) {
      val otherLevel = JSONObject()
        .put("turn", turn)
        .put("level", JSONObject().put("number", 3))
        .put("location", "unknown")
      val migrated = LegacySaveMigration.migrate(otherLevel)
      assertNull(migrated.world["worldNodeId"])
      assertNull(migrated.world["journeyStopKey"])
    }
    val missingLevel = LegacySaveMigration.migrate(JSONObject().put("turn", 1))
    assertNull(missingLevel.world["worldNodeId"])
    val resumed = LegacySaveMigration.migrate(
      JSONObject().put("turn", 2).put("level", JSONObject().put("number", 0)))
    assertNull(resumed.world["worldNodeId"])
  }
}
