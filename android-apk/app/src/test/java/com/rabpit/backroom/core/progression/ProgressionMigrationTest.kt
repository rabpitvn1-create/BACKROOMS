package com.rabpit.backroom.core.progression

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the v1 -> v2 save migration from issue #453 against the REAL
 * persisted `combat93.state` boundary shape:
 * - exact version dispatch: v1 -> migrate, v2 -> validate, future -> reject,
 * - version lives in exactly one place (boundary root),
 * - the whole `combat.entities[]` array migrates,
 * - materialized combat stats are preserved verbatim (no mid-fight rescale),
 * - `combat.entity` is a projection-only alias, re-synced from entities[],
 * - v2 load never trusts a persisted rank as authority,
 * - unknown nodes fail closed.
 */
class ProgressionMigrationTest {

  /** Mirrors the real v1 boundary: root combatStageIndex + combat.stageIndex + entities[]. */
  private fun v1Boundary(stageIndex: Int = 2): JSONObject {
    val entity = JSONObject()
      .put("key", "smiler")
      .put("name", "Smiler")
      .put("hp", 37)
      .put("maxHp", 110)
      .put("attack", 12)
      .put("baseHp", 100)
      .put("baseDamage", 11)
      .put("stageIndex", stageIndex)
      .put("stagePercent", 100 + 10 * stageIndex)
      .put("criticalChancePercent", 5)
      .put("evasionPercent", 5)
      .put("bleedTurns", 0)
      .put("alive", true)
      .put("status", "alive")
    val diceState = JSONObject()
      .put("values", JSONArray().put(1).put(2).put(3).put(4).put(5))
      .put("held", JSONArray().put(false).put(false).put(false).put(false).put(false))
      .put("rerollsUsed", 1)
      .put("hasRolled", true)
      .put("finalized", false)
    val combat = JSONObject()
      .put("active", true)
      .put("round", 3)
      .put("stageIndex", stageIndex)
      .put("entities", JSONArray().put(entity))
      .put("diceState", diceState)
      .put("encounterId", "turn-9:0:smiler")
      .put("logIndex", 4)
    return JSONObject()
      .put("turn", 7)
      .put("combatStageIndex", stageIndex)
      .put("location", "Level 2")
      .put("flags", JSONObject())
      .put("log", JSONArray())
      .put("characterProgression", JSONObject().put("characters", JSONObject()))
      .put("combat", combat)
  }

  private fun migratedBoundary(stageIndex: Int = 2): JSONObject {
    val outcome = ProgressionMigration.loadCombatBoundary(v1Boundary(stageIndex), WorldNodeId("level-$stageIndex"))
    assertTrue(outcome is BoundaryLoadOutcome.Migrated)
    return (outcome as BoundaryLoadOutcome.Migrated).boundary
  }

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

  @Test fun v1BoundaryMigratesWithSingleRootVersion() {
    val migrated = migratedBoundary()
    // Version in exactly one place: the boundary root.
    assertEquals(PROGRESSION_SCHEMA_VERSION_2, migrated.getInt("progressionSchemaVersion"))
    assertEquals("level-2", migrated.getString("worldNodeId"))
    assertEquals(2_000_000L, migrated.getLong("progressionRank"))

    val combat = migrated.getJSONObject("combat")
    assertEquals("level-2", combat.getString("worldNodeId"))
    // Legacy fields stay frozen as history; never reused with a new meaning.
    assertEquals(2, combat.getInt("stageIndex"))
    assertEquals(2, migrated.getInt("combatStageIndex"))

    val entities = combat.getJSONArray("entities")
    assertEquals(1, entities.length())
    val entity = entities.getJSONObject(0)
    assertEquals("level-2", entity.getString("worldNodeId"))
    assertEquals(2_000_000L, entity.getLong("progressionRank"))
    // No rescale mid-fight: materialized stats are verbatim.
    assertEquals(37, entity.getInt("hp"))
    assertEquals(110, entity.getInt("maxHp"))
    assertEquals(12, entity.getInt("attack"))
    assertEquals(100, entity.getInt("baseHp"))
    assertEquals(11, entity.getInt("baseDamage"))
    assertEquals("alive", entity.getString("status"))
    // Dice and encounter metadata untouched.
    assertEquals("turn-9:0:smiler", combat.getString("encounterId"))
    assertEquals(3, combat.getInt("round"))
    assertEquals(4, combat.getInt("logIndex"))
    val dice = combat.getJSONObject("diceState")
    assertEquals(1, dice.getInt("rerollsUsed"))
    assertEquals(5, dice.getJSONArray("values").length())
  }

  @Test fun entityAliasIsResyncedProjectionOnly() {
    val migrated = migratedBoundary()
    val combat = migrated.getJSONObject("combat")
    // The alias exists and mirrors entities[activeEntityIndex], like the
    // runtime's syncActiveEntityAlias.
    assertTrue(combat.has("entity"))
    val alias = combat.getJSONObject("entity")
    val first = combat.getJSONArray("entities").getJSONObject(0)
    assertEquals(first.getString("key"), alias.getString("key"))
    assertEquals(first.getInt("hp"), alias.getInt("hp"))
    assertEquals("level-2", alias.getString("worldNodeId"))
  }

  @Test fun boundaryMigrationIsIdempotent() {
    val once = migratedBoundary()
    val outcome = ProgressionMigration.loadCombatBoundary(once, WorldNodeId("level-2"))
    assertTrue(outcome is BoundaryLoadOutcome.Valid)
    val valid = outcome as BoundaryLoadOutcome.Valid
    assertEquals(2_000_000L, valid.authoritativeRank)
    assertFalse(valid.snapshotCorrected)
    assertEquals(once.toString(), valid.boundary.toString())
  }

  @Test fun boundaryWithUnknownStageFailsClosed() {
    val outcome = ProgressionMigration.loadCombatBoundary(v1Boundary(stageIndex = 99), WorldNodeId("level-2"))
    assertTrue(outcome is BoundaryLoadOutcome.Rejected)
    assertTrue((outcome as BoundaryLoadOutcome.Rejected).reason.contains("99"))
  }

  @Test fun futureVersionIsRejectedNotBypassed() {
    val boundary = migratedBoundary()
    boundary.put("progressionSchemaVersion", 99)
    val outcome = ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
    assertTrue("future versions must fail closed, never bypass", outcome is BoundaryLoadOutcome.Rejected)
  }

  @Test fun v2WithUnknownNodeIsRejected() {
    val boundary = migratedBoundary()
    boundary.put("worldNodeId", "level-99")
    val outcome = ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
    assertTrue(outcome is BoundaryLoadOutcome.Rejected)
  }

  @Test fun v2WithMissingNodeIdIsRejected() {
    val boundary = migratedBoundary()
    boundary.remove("worldNodeId")
    val outcome = ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
    assertTrue(outcome is BoundaryLoadOutcome.Rejected)
  }

  @Test fun v2RankMismatchIsCorrectedToAuthoritative() {
    val boundary = migratedBoundary()
    // Tampered snapshot: entity claims a different rank than the authority.
    boundary.getJSONObject("combat").getJSONArray("entities").getJSONObject(0)
      .put("progressionRank", 9_000_000L)
    val outcome = ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
    assertTrue(outcome is BoundaryLoadOutcome.Valid)
    val valid = outcome as BoundaryLoadOutcome.Valid
    assertEquals(2_000_000L, valid.authoritativeRank) // authority wins
    assertTrue(valid.snapshotCorrected)
    assertEquals(
      2_000_000L,
      valid.boundary.getJSONObject("combat").getJSONArray("entities").getJSONObject(0)
        .getLong("progressionRank"),
    )
  }

  @Test fun v2LoadNeverTrustsPersistedRank() {
    val boundary = migratedBoundary()
    val entity = boundary.getJSONObject("combat").getJSONArray("entities").getJSONObject(0)
    entity.put("progressionRank", 9_000_000L)

    val resolution = ProgressionMigration.resolveEncounterRank(entity, WorldNodeId("level-2"))
    assertNotNull(resolution)
    assertEquals(2_000_000L, resolution!!.authoritativeRank) // authority wins
    assertEquals(9_000_000L, resolution.persistedSnapshotRank)
    assertFalse(resolution.snapshotMatches)
  }

  @Test fun resolveEncounterRankFailsClosedForUnknownNode() {
    val entity = v1Boundary().getJSONObject("combat").getJSONArray("entities").getJSONObject(0)
    assertNull(ProgressionMigration.resolveEncounterRank(entity, WorldNodeId("level-99")))
  }

  @Test fun worldStateV1ToV2StoresOnlyNodeId() {
    val world = mapOf("levelJson" to JSONObject().put("number", 3).toString())
    val migrated = (ProgressionMigration.migrateWorldStateV1ToV2(world) as WorldMigrationOutcome.Migrated).world
    assertEquals("level-3", migrated["worldNodeId"])
    // Rank is always derived via rankOf, never persisted as an override.
    assertFalse(migrated.containsKey("progressionRank"))
  }

  @Test fun worldStateWithUnknownLevelRejects() {
    val world = mapOf("levelJson" to JSONObject().put("number", 42).toString())
    assertTrue(ProgressionMigration.migrateWorldStateV1ToV2(world) is WorldMigrationOutcome.Rejected)
  }

  @Test fun validNodeTamperingAtAllLayersCannotChangeWorldAuthority() {
    val boundary = migratedBoundary()
    val combat = boundary.getJSONObject("combat")
    val entity = combat.getJSONArray("entities").getJSONObject(0)
    for (record in listOf(boundary, combat, entity)) {
      record.put("worldNodeId", "level-6").put("progressionRank", 6_000_000L)
    }
    val before = boundary.toString()
    val outcome = ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
      as BoundaryLoadOutcome.Valid
    assertTrue(outcome.snapshotCorrected)
    assertEquals(2_000_000L, outcome.authoritativeRank)
    val loadedCombat = outcome.boundary.getJSONObject("combat")
    for (record in listOf(outcome.boundary, loadedCombat,
      loadedCombat.getJSONArray("entities").getJSONObject(0), loadedCombat.getJSONObject("entity"))) {
      assertEquals("level-2", record.getString("worldNodeId"))
      assertEquals(2_000_000L, record.getLong("progressionRank"))
    }
    assertEquals(37, loadedCombat.getJSONObject("entity").getInt("hp"))
    assertEquals(110, loadedCombat.getJSONObject("entity").getInt("maxHp"))
    assertEquals(12, loadedCombat.getJSONObject("entity").getInt("attack"))
    assertEquals(before, boundary.toString())
  }

  @Test fun nestedUnknownAndMissingIdsReject() {
    for (key in listOf("combat", "entity")) {
      for (id in listOf("level-99", null)) {
        val boundary = migratedBoundary()
        val combat = boundary.getJSONObject("combat")
        val record = if (key == "combat") combat else combat.getJSONArray("entities").getJSONObject(0)
        if (id == null) record.remove("worldNodeId") else record.put("worldNodeId", id)
        assertTrue(ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
          is BoundaryLoadOutcome.Rejected)
      }
    }
  }

  @Test fun malformedBoundaryAndVersionRejectWithoutMutatingInput() {
    for (version in listOf<Any>(2.5, "2", JSONObject.NULL, 4_294_967_298L)) {
      val boundary = migratedBoundary().put("progressionSchemaVersion", version)
      assertTrue(ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
        is BoundaryLoadOutcome.Rejected)
    }
    for (mutation in listOf<(JSONObject) -> Unit>(
      { it.remove("combat") },
      { it.getJSONObject("combat").remove("entities") },
      { it.getJSONObject("combat").put("entities", JSONArray().put("bad")) },
      { it.getJSONObject("combat").put("entities", JSONArray()) },
    )) {
      val boundary = migratedBoundary()
      mutation(boundary)
      val before = boundary.toString()
      assertTrue(ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-2"))
        is BoundaryLoadOutcome.Rejected)
      assertEquals(before, boundary.toString())
    }
  }

  @Test fun legacyBoundaryCannotOverrideTrustedNode() {
    assertTrue(ProgressionMigration.loadCombatBoundary(v1Boundary(6), WorldNodeId("level-2"))
      is BoundaryLoadOutcome.Rejected)
    assertTrue(ProgressionMigration.loadCombatBoundary(v1Boundary(), WorldNodeId("level-99"))
      is BoundaryLoadOutcome.Rejected)
  }
  @Test fun allLegacyLevelsPreserveEveryMaterializedEntityField() {
    for (level in 0..6) {
      val boundary = v1Boundary(level)
      val combat = boundary.getJSONObject("combat")
      val second = JSONObject(combat.getJSONArray("entities").getJSONObject(0).toString())
        .put("key", "hound").put("hp", 13).put("maxHp", 200).put("attack", 21)
      combat.getJSONArray("entities").put(second)
      combat.put("activeEntityIndex", 1)
      val original = boundary.toString()
      val loaded = (ProgressionMigration.loadCombatBoundary(boundary, WorldNodeId("level-$level"))
        as BoundaryLoadOutcome.Migrated).boundary
      val migrated = loaded.getJSONObject("combat")
      for (i in 0..1) {
        val before = combat.getJSONArray("entities").getJSONObject(i)
        val after = migrated.getJSONArray("entities").getJSONObject(i)
        for (key in before.keys()) assertEquals("level=$level entity=$i field=$key", before.get(key), after.get(key))
      }
      assertEquals("hound", migrated.getJSONObject("entity").getString("key"))
      assertEquals(original, boundary.toString())
    }
  }

}
