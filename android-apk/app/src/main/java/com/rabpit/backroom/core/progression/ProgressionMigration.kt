package com.rabpit.backroom.core.progression

import org.json.JSONObject

/**
 * Combat / world save schema migration for issue #453.
 *
 * v1: difficulty derived from `stageIndex` (level-number semantics) stored in
 *     world `levelJson.number` and combat entity JSON.
 * v2: `worldNodeId` (stable id) + `progressionRank` (Core-owned Long).
 *     `stageIndex` is NEVER reused with a new meaning.
 *
 * Rules (locked):
 * - Old saves resolve through [WorldProgressionCore]; unknown nodes fail closed,
 *   never silently fall back to Level 0 / rank 0.
 * - Materialized combat stats (hp / maxHp / damage) are preserved VERBATIM.
 *   No rescale mid-fight; the new rank applies to NEW encounters only.
 * - World state persists ONLY the authoritative `worldNodeId`. Rank is always
 *   derived via [WorldProgressionCore.rankOf], never persisted as an override.
 */
const val COMBAT_SCHEMA_VERSION_1: Int = 1
const val COMBAT_SCHEMA_VERSION_2: Int = 2

sealed interface MigrationResult {
  data class Migrated(val nodeId: WorldNodeId, val progressionRank: Long) : MigrationResult

  /** Fail closed: surface to the user, do not guess. */
  data class Failed(val reason: String) : MigrationResult
}

object ProgressionMigration {

  /** v1 `levelJson.number` -> v2 authoritative node. */
  fun migrateLegacyLevelNumber(levelNumber: Int): MigrationResult {
    val nodeId = WorldProgressionCore.nodeIdForLegacyLevelNumber(levelNumber)
      ?: return MigrationResult.Failed("unknown legacy level number: $levelNumber")
    val rank = when (val lookup = WorldProgressionCore.rankOf(nodeId)) {
      is RankLookup.Known -> lookup.progressionRank
      RankLookup.Unknown -> return MigrationResult.Failed("no rank for node ${nodeId.value}")
    }
    return MigrationResult.Migrated(nodeId, rank)
  }

  /**
   * v1 world map -> v2 world map.
   * On failure the error is recorded and NO `worldNodeId` is written, so a
   * half-migrated state can never be mistaken for a valid one.
   */
  fun migrateWorldStateV1ToV2(world: Map<String, String>): Map<String, String> {
    val levelJson = world["levelJson"] ?: return world
    val number = JSONObject(levelJson).optInt("number", Int.MIN_VALUE)
    return when (val result = migrateLegacyLevelNumber(number)) {
      is MigrationResult.Migrated ->
        world + ("worldNodeId" to result.nodeId.value)
      is MigrationResult.Failed ->
        world + ("progressionMigrationError" to result.reason)
    }
  }

  /**
   * v1 combat entity JSON -> v2. Idempotent.
   * Keeps every materialized stat untouched; only ADDS v2 fields.
   * On failure the entity keeps its materialized stats and records the error;
   * it is never rescaled and never assigned a guessed rank.
   */
  fun migrateCombatEntityV1ToV2(entity: JSONObject): JSONObject {
    if (entity.optInt("combatSchemaVersion", COMBAT_SCHEMA_VERSION_1) >= COMBAT_SCHEMA_VERSION_2) {
      return entity
    }
    val copy = JSONObject(entity.toString())
    val stageIndex = entity.optInt("stageIndex", Int.MIN_VALUE)
    return when (val result = migrateLegacyLevelNumber(stageIndex)) {
      is MigrationResult.Migrated -> copy
        .put("combatSchemaVersion", COMBAT_SCHEMA_VERSION_2)
        .put("worldNodeId", result.nodeId.value)
        .put("progressionRank", result.progressionRank)
      is MigrationResult.Failed -> copy
        .put("progressionMigrationError", result.reason)
    }
  }
}
