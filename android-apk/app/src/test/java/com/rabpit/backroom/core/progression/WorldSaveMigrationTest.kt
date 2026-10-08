package com.rabpit.backroom.core.progression

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the v3 -> v4 GameState save wiring from issue #453 (re-review
 * point 3): existing v3 saves must actually pass through the progression
 * migration exactly once, via the specified GameStateCodec routing.
 */
class WorldSaveMigrationTest {

  /** Realistic v3 world map shape: levelJson present, no worldNodeId yet. */
  private fun v3World(levelNumber: Int = 3): Map<String, String> = mapOf(
    "levelJson" to JSONObject().put("number", levelNumber).put("name", "Level $levelNumber").toString(),
    "location" to "Level $levelNumber",
    "flagsJson" to JSONObject().toString(),
  )

  @Test fun v3WorldMigratesToAuthoritativeNodeId() {
    val world = v3World(3)
    assertTrue(WorldSaveMigration.needsMigration(world))
    val migrated = WorldSaveMigration.migrateV3World(world)
    assertEquals("level-3", migrated["worldNodeId"])
    // Everything else untouched.
    assertEquals(world["location"], migrated["location"])
    assertEquals(world["levelJson"], migrated["levelJson"])
    assertFalse(migrated.containsKey("progressionRank"))
  }

  @Test fun alreadyMigratedWorldPassesThrough() {
    val world = v3World(3) + ("worldNodeId" to "level-3")
    assertFalse(WorldSaveMigration.needsMigration(world))
    assertEquals(world, WorldSaveMigration.migrateV3World(world))
  }

  @Test fun worldWithoutLevelJsonNeedsNoMigration() {
    val world = mapOf("location" to "???")
    assertFalse(WorldSaveMigration.needsMigration(world))
    assertEquals(world, WorldSaveMigration.migrateV3World(world))
  }

  @Test fun unknownLevelFailsClosedWithoutGuessing() {
    val migrated = WorldSaveMigration.migrateV3World(v3World(42))
    assertTrue(migrated.containsKey("progressionMigrationError"))
    assertFalse(migrated.containsKey("worldNodeId"))
    // Original fields preserved for forensics.
    assertEquals("Level 42", migrated["location"])
  }

  @Test fun targetSaveVersionIsPinned() {
    // The GameStateCodec wiring spec (see WorldSaveMigration KDoc) bumps
    // CURRENT_SAVE_VERSION 3 -> 4; the constant pins the design side.
    assertEquals(4, WorldSaveMigration.WORLD_NODE_SAVE_VERSION)
  }
}
