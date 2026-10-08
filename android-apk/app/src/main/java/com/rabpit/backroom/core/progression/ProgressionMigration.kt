package com.rabpit.backroom.core.progression

import org.json.JSONArray
import org.json.JSONObject

/**
 * Combat / world save schema migration for issue #453.
 *
 * Real persisted shape (v1): the `combat93.state` boundary JSON —
 * - root: `turn`, `combatStageIndex` (level-number semantics), `location`,
 *   `flags`, `player`, `party`, `log`, `characterProgression`,
 * - `combat`: `stageIndex` (level-number semantics), `entities[]` (each with
 *   `key/name/hp/maxHp/attack/baseHp/baseDamage/stageIndex/stagePercent/...`),
 *   `diceState`, `encounterId`, ...
 *
 * v2: ONE version field at the boundary root (`progressionSchemaVersion`),
 * plus `worldNodeId` + `progressionRank` snapshot metadata at root, on
 * `combat`, and on every entity. `stageIndex` is NEVER reused with a new
 * meaning; old fields stay frozen as history.
 *
 * Rules (locked):
 * - Old saves resolve through [WorldProgressionCore]; unknown nodes fail
 *   closed, never silently fall back to Level 0 / rank 0.
 * - Materialized combat stats (hp / maxHp / attack / status / dice) are
 *   preserved VERBATIM. No rescale mid-fight; the new rank applies to NEW
 *   encounters only.
 * - v2 LOAD never trusts a persisted `progressionRank` as authority: the
 *   authoritative rank always derives from the world's authoritative
 *   `worldNodeId` via [WorldProgressionCore.rankOf]. Persisted rank is
 *   snapshot metadata; a mismatch is audit info and the authoritative
 *   rank wins. See [resolveEncounterRank].
 * - World state persists ONLY the authoritative `worldNodeId`. Rank is
 *   always derived, never persisted as an override.
 */
const val PROGRESSION_SCHEMA_VERSION_1: Int = 1
const val PROGRESSION_SCHEMA_VERSION_2: Int = 2

sealed interface MigrationResult {
  data class Migrated(val nodeId: WorldNodeId, val progressionRank: Long) : MigrationResult

  /** Fail closed: surface to the user, do not guess. */
  data class Failed(val reason: String) : MigrationResult
}

/**
 * v2 load resolution of an encounter's rank.
 * [persistedSnapshotRank] is metadata only; [authoritativeRank] always wins.
 */
data class EncounterRankResolution(
  val authoritativeRank: Long,
  val persistedSnapshotRank: Long?,
  val snapshotMatches: Boolean,
)

object ProgressionMigration {

  /** v1 `levelJson.number` / `combatStageIndex` -> v2 authoritative node. */
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
   * v1 `combat93.state` boundary -> v2. Idempotent.
   * Version lives in exactly one place: the boundary root.
   * The whole `combat.entities[]` array is migrated; every materialized
   * field (hp/maxHp/attack/status/dice/...) is preserved verbatim.
   */
  fun migrateCombatBoundaryV1ToV2(boundary: JSONObject): JSONObject {
    if (boundary.optInt("progressionSchemaVersion", PROGRESSION_SCHEMA_VERSION_1) >= PROGRESSION_SCHEMA_VERSION_2) {
      return boundary
    }
    val copy = JSONObject(boundary.toString())
    val stageIndex = copy.optInt("combatStageIndex", Int.MIN_VALUE)
    return when (val result = migrateLegacyLevelNumber(stageIndex)) {
      is MigrationResult.Migrated -> {
        copy.put("progressionSchemaVersion", PROGRESSION_SCHEMA_VERSION_2)
        copy.put("worldNodeId", result.nodeId.value)
        copy.put("progressionRank", result.progressionRank)
        val combat = copy.optJSONObject("combat")
        if (combat != null) {
          combat.put("worldNodeId", result.nodeId.value)
          combat.put("progressionRank", result.progressionRank)
          val entities = combat.optJSONArray("entities") ?: JSONArray()
          for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            migrateCombatEntityV1ToV2(entity, result.nodeId, result.progressionRank)
          }
        }
        copy
      }
      is MigrationResult.Failed ->
        copy.put("progressionMigrationError", result.reason)
    }
  }

  /**
   * Per-entity v1 -> v2 step, used by [migrateCombatBoundaryV1ToV2].
   * Takes the already-resolved node/rank; only ADDS snapshot metadata.
   * Every materialized stat stays verbatim — no rescale, ever.
   */
  fun migrateCombatEntityV1ToV2(
    entity: JSONObject,
    nodeId: WorldNodeId,
    progressionRank: Long,
  ): JSONObject {
    entity.put("worldNodeId", nodeId.value)
    entity.put("progressionRank", progressionRank)
    return entity
  }

  /**
   * v2 LOAD rule: never trust the persisted `progressionRank` as authority.
   * Returns null (fail closed) when the authoritative node is unknown.
   */
  fun resolveEncounterRank(
    entityV2: JSONObject,
    authoritativeNodeId: WorldNodeId,
  ): EncounterRankResolution? {
    val authoritative = when (val lookup = WorldProgressionCore.rankOf(authoritativeNodeId)) {
      is RankLookup.Known -> lookup.progressionRank
      RankLookup.Unknown -> return null
    }
    val persisted = if (entityV2.has("progressionRank")) entityV2.optLong("progressionRank") else null
    return EncounterRankResolution(
      authoritativeRank = authoritative,
      persistedSnapshotRank = persisted,
      snapshotMatches = persisted == authoritative,
    )
  }
}
