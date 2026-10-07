package com.rabpit.backroom.core.progression

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the v1 -> v2 save migration from issue #453:
 * - `stageIndex` is never reused with a new meaning,
 * - materialized combat stats are preserved verbatim (no mid-fight rescale),
 * - unknown nodes fail closed.
 */
class ProgressionMigrationTest {

  @Test fun legacyLevelNumberMigratesToExpectedNodeAndRank() {
    val result = ProgressionMigration.migrateLegacyLevelNumber(2)
    assertTrue(result is MigrationResult.Migrated)
    val migrated = result as MigrationResult.Migrated
    assertEquals(WorldNodeId("level-2"), migrated.nodeId)
    assertEquals(2_000_000L, migrated.progressionRank)
  }

  @Test fun unknownLegacyLevelFailsClosed() {
    for (n in listOf(Int.MIN_VALUE, -1, 7, 99)) {
      val result = ProgressionMigration.migrateLegacyLevelNumber(n)
      assertTrue("level $n must fail closed, not guess", result is MigrationResult.Failed)
    }
  }

  private fun v1Entity(): JSONObject = JSONObject()
    .put("key", "test-entity")
    .put("hp", 37)
    .put("maxHp", 110)
    .put("attack", 12)
    .put("baseHp", 100)
    .put("baseDamage", 11)
    .put("stageIndex", 2)
    .put("stagePercent", 120)

  @Test fun combatEntityV1ToV2PreservesMaterializedStats() {
    val migrated = ProgressionMigration.migrateCombatEntityV1ToV2(v1Entity())
    assertEquals(COMBAT_SCHEMA_VERSION_2, migrated.getInt("combatSchemaVersion"))
    assertEquals("level-2", migrated.getString("worldNodeId"))
    assertEquals(2_000_000L, migrated.getLong("progressionRank"))
    // No rescale mid-fight: materialized stats are verbatim.
    assertEquals(37, migrated.getInt("hp"))
    assertEquals(110, migrated.getInt("maxHp"))
    assertEquals(12, migrated.getInt("attack"))
    assertEquals(100, migrated.getInt("baseHp"))
    assertEquals(11, migrated.getInt("baseDamage"))
  }

  @Test fun combatEntityMigrationIsIdempotent() {
    val once = ProgressionMigration.migrateCombatEntityV1ToV2(v1Entity())
    val twice = ProgressionMigration.migrateCombatEntityV1ToV2(once)
    assertEquals(once.toString(), twice.toString())
  }

  @Test fun combatEntityWithUnknownStageFailsClosed() {
    val migrated = ProgressionMigration.migrateCombatEntityV1ToV2(v1Entity().put("stageIndex", 99))
    assertTrue(migrated.has("progressionMigrationError"))
    // Materialized stats still preserved; nothing is rescaled or guessed.
    assertEquals(37, migrated.getInt("hp"))
    assertEquals(110, migrated.getInt("maxHp"))
    assertFalse(migrated.has("worldNodeId"))
  }

  @Test fun worldStateV1ToV2StoresOnlyNodeId() {
    val world = mapOf("levelJson" to JSONObject().put("number", 3).toString())
    val migrated = ProgressionMigration.migrateWorldStateV1ToV2(world)
    assertEquals("level-3", migrated["worldNodeId"])
    // Rank is always derived via rankOf, never persisted as an override.
    assertFalse(migrated.containsKey("progressionRank"))
  }

  @Test fun worldStateWithUnknownLevelRecordsError() {
    val world = mapOf("levelJson" to JSONObject().put("number", 42).toString())
    val migrated = ProgressionMigration.migrateWorldStateV1ToV2(world)
    assertTrue(migrated.containsKey("progressionMigrationError"))
    assertFalse(migrated.containsKey("worldNodeId"))
  }
}
