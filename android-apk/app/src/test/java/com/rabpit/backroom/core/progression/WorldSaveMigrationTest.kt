package com.rabpit.backroom.core.progression

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldSaveMigrationTest {
  private fun v3World(levelNumber: Int = 3): Map<String, String> = mapOf(
    "levelJson" to JSONObject().put("number", levelNumber).put("name", "Level $levelNumber").toString(),
    "location" to "Level $levelNumber", "flagsJson" to "{}",
  )

  @Test fun v3WorldMigratesToAuthoritativeNodeId() {
    val world = v3World()
    assertTrue(WorldSaveMigration.needsMigration(world))
    val migrated = (WorldSaveMigration.migrateV3World(world) as WorldMigrationOutcome.Migrated).world
    assertEquals("level-3", migrated["worldNodeId"])
    assertEquals(world["location"], migrated["location"])
    assertEquals(world["levelJson"], migrated["levelJson"])
    assertFalse(migrated.containsKey("progressionRank"))
  }

  @Test fun newlyConnectedMainLevelsSevenThroughThirteenMigrateWithoutFallback() {
    for (n in 7..13) {
      val migrated = WorldSaveMigration.migrateV3World(v3World(n))
      assertTrue("Level $n must be a registered main Level", migrated is WorldMigrationOutcome.Migrated)
      assertEquals("level-$n", (migrated as WorldMigrationOutcome.Migrated).world["worldNodeId"])
    }
    assertTrue(WorldSaveMigration.migrateV3World(v3World(14)) is WorldMigrationOutcome.Rejected)
  }

  @Test fun alreadyMigratedWorldPassesThrough() {
    val world = v3World() + ("worldNodeId" to "level-3")
    assertFalse(WorldSaveMigration.needsMigration(world))
    assertEquals(world, (WorldSaveMigration.migrateV3World(world) as WorldMigrationOutcome.Migrated).world)
  }

  @Test fun missingMalformedAndUnknownWorldRejectWithoutGuessing() {
    for (world in listOf(emptyMap(), mapOf("location" to "???"), v3World(42),
      mapOf("levelJson" to "not json"), mapOf("levelJson" to "{}"),
      mapOf("levelJson" to "{\"number\":2.5}"), mapOf("levelJson" to "{\"number\":\"2\"}"),
      v3World() + ("worldNodeId" to "level-99"))) {
      assertTrue(WorldSaveMigration.migrateV3World(world) is WorldMigrationOutcome.Rejected)
    }
  }

  @Test fun onlySuccessfulSaveMigrationBumpsVersionAndNeverMutatesInput() {
    val root = JSONObject().put("saveVersion", 3).put("world", JSONObject(v3World()))
      .put("metadata", JSONObject().put("sentinel", "keep"))
    val before = root.toString()
    val migrated = (WorldSaveMigration.migrateV3Save(root) as WorldSaveLoadOutcome.Migrated).root
    assertEquals(4, migrated.getInt("saveVersion"))
    assertEquals("level-3", migrated.getJSONObject("world").getString("worldNodeId"))
    assertEquals("keep", migrated.getJSONObject("metadata").getString("sentinel"))
    assertEquals(before, root.toString())
    for (badWorld in listOf(JSONObject(), JSONObject(v3World(42)), JSONObject().put("levelJson", 3))) {
      val bad = JSONObject().put("saveVersion", 3).put("world", badWorld)
      val original = bad.toString()
      assertTrue(WorldSaveMigration.migrateV3Save(bad) is WorldSaveLoadOutcome.Rejected)
      assertEquals(original, bad.toString())
      assertEquals(3, bad.getInt("saveVersion"))
    }
  }

  @Test fun futureAndMalformedSaveVersionsReject() {
    for (version in listOf<Any>(4, 99, "3", 3.5, JSONObject.NULL)) {
      assertTrue(WorldSaveMigration.migrateV3Save(JSONObject().put("saveVersion", version)
        .put("world", JSONObject(v3World()))) is WorldSaveLoadOutcome.Rejected)
    }
  }

  @Test fun freshStateIsExplicitAndEmptySavedWorldStillRejects() {
    assertEquals(mapOf("worldNodeId" to "level-0"), WorldSaveMigration.freshWorld())
    assertTrue(WorldSaveMigration.needsMigration(emptyMap()))
    assertTrue(WorldSaveMigration.migrateV3World(emptyMap()) is WorldMigrationOutcome.Rejected)
    assertEquals(4, WorldSaveMigration.WORLD_NODE_SAVE_VERSION)
  }
}
