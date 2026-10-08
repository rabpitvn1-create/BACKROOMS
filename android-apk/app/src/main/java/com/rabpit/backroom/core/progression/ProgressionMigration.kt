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
 *   `entity` (compatibility alias, see below), `diceState`, `encounterId`, ...
 *
 * v2: ONE version field at the boundary root (`progressionSchemaVersion`),
 * plus `worldNodeId` + `progressionRank` snapshot metadata at root, on
 * `combat`, and on every entity. `stageIndex` is NEVER reused with a new
 * meaning; old fields stay frozen as history.
 *
 * Version handling is EXACT (fail closed):
 * - v1 (or missing version) -> migrate,
 * - v2 -> validate against a trusted Core world node (unknown or missing
 *   snapshot IDs reject; known ID/rank mismatches are corrected and flagged),
 * - any other / future version -> explicit reject, never bypass.
 *
 * `combat.entity` alias: projection-only. The runtime rebuilds it from
 * `entities[activeEntityIndex]` before every read (`syncActiveEntityAlias`);
 * migration re-syncs the persisted copy the same way and never treats it
 * as an independent record.
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

sealed interface BoundaryLoadOutcome {
  /** v1 -> v2 migration applied. */
  data class Migrated(val boundary: JSONObject) : BoundaryLoadOutcome

  /**
   * v2 boundary accepted. [snapshotCorrected] is true when a persisted node/rank
   * snapshot disagreed with the authoritative rank and was corrected
   * (audit-worthy, not fatal: snapshots are metadata, not authority).
   */
  data class Valid(val boundary: JSONObject, val authoritativeRank: Long, val snapshotCorrected: Boolean) :
    BoundaryLoadOutcome

  /** Fail closed: unknown node, missing id, malformed v2, or future version. */
  data class Rejected(val reason: String) : BoundaryLoadOutcome
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

  /** Pure world migration; failure never produces a usable world. */
  fun migrateWorldStateV1ToV2(world: Map<String, String>): WorldMigrationOutcome =
    WorldSaveMigration.migrateV3World(world)

  /**
   * The caller supplies the trusted GameState.world node, never a boundary field.
   * Known snapshot mismatches are corrected and flagged; missing/unknown IDs,
   * malformed structures and unsupported versions fail closed.
   */
  fun loadCombatBoundary(
    boundary: JSONObject,
    authoritativeNodeId: WorldNodeId,
  ): BoundaryLoadOutcome {
    val rank = (WorldProgressionCore.rankOf(authoritativeNodeId) as? RankLookup.Known)
      ?.progressionRank ?: return BoundaryLoadOutcome.Rejected("unknown authoritative world node")
    val version = if (!boundary.has("progressionSchemaVersion")) PROGRESSION_SCHEMA_VERSION_1
      else strictInteger(boundary.opt("progressionSchemaVersion"))?.toInt()
        ?: return BoundaryLoadOutcome.Rejected("malformed progressionSchemaVersion")
    if (version != PROGRESSION_SCHEMA_VERSION_1 && version != PROGRESSION_SCHEMA_VERSION_2) {
      return BoundaryLoadOutcome.Rejected("unsupported progressionSchemaVersion: $version")
    }
    val combat = boundary.optJSONObject("combat")
      ?: return BoundaryLoadOutcome.Rejected("boundary missing combat object")
    val entities = combat.optJSONArray("entities")
      ?: return BoundaryLoadOutcome.Rejected("combat missing entities array")
    for (i in 0 until entities.length()) {
      if (entities.optJSONObject(i) == null) {
        return BoundaryLoadOutcome.Rejected("malformed entity at index $i")
      }
    }
    if (combat.optBoolean("active") && entities.length() == 0) {
      return BoundaryLoadOutcome.Rejected("active combat has no entities")
    }
    if (version == PROGRESSION_SCHEMA_VERSION_1) {
      val level = strictInteger(boundary.opt("combatStageIndex"))
        ?: return BoundaryLoadOutcome.Rejected("missing or malformed combatStageIndex")
      val legacy = migrateLegacyLevelNumber(level.toInt())
      if (legacy is MigrationResult.Failed) return BoundaryLoadOutcome.Rejected(legacy.reason)
      val snapshot = legacy as MigrationResult.Migrated
      if (snapshot.nodeId != authoritativeNodeId) {
        return BoundaryLoadOutcome.Rejected("legacy combat node disagrees with authoritative world")
      }
      return BoundaryLoadOutcome.Migrated(applyV1Snapshot(boundary, authoritativeNodeId, rank))
    }
    return validateV2Boundary(boundary, authoritativeNodeId, rank)
  }

  /** JSON integer values only: no truncation, string coercion or default rank. */
  internal fun strictInteger(value: Any?): Long? = when (value) {
    is Int -> value.toLong()
    is Long -> value.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
    else -> null
  }

  private fun applyV1Snapshot(
    boundary: JSONObject,
    nodeId: WorldNodeId,
    progressionRank: Long,
  ): JSONObject {
    val copy = JSONObject(boundary.toString())
    copy.put("progressionSchemaVersion", PROGRESSION_SCHEMA_VERSION_2)
    copy.put("worldNodeId", nodeId.value)
    copy.put("progressionRank", progressionRank)
    val combat = copy.optJSONObject("combat")
    if (combat != null) {
      combat.put("worldNodeId", nodeId.value)
      combat.put("progressionRank", progressionRank)
      val entities = combat.optJSONArray("entities") ?: JSONArray()
      for (i in 0 until entities.length()) {
        val entity = entities.optJSONObject(i) ?: continue
        migrateCombatEntityV1ToV2(entity, nodeId, progressionRank)
      }
      resyncEntityAlias(combat)
    }
    return copy
  }

  private fun validateV2Boundary(
    boundary: JSONObject,
    authoritativeNodeId: WorldNodeId,
    authoritativeRank: Long,
  ): BoundaryLoadOutcome {
    val copy = JSONObject(boundary.toString())
    val combat = copy.getJSONObject("combat")
    val records = mutableListOf(copy, combat)
    val entities = combat.getJSONArray("entities")
    for (i in 0 until entities.length()) records.add(entities.getJSONObject(i))
    var corrected = false
    for (record in records) {
      val id = record.opt("worldNodeId") as? String
        ?: return BoundaryLoadOutcome.Rejected("v2 record missing worldNodeId")
      if (WorldProgressionCore.rankOf(WorldNodeId(id)) == RankLookup.Unknown) {
        return BoundaryLoadOutcome.Rejected("v2 record references unknown node: $id")
      }
      if (id != authoritativeNodeId.value || strictInteger(record.opt("progressionRank")) != authoritativeRank) {
        record.put("worldNodeId", authoritativeNodeId.value)
        record.put("progressionRank", authoritativeRank)
        corrected = true
      }
    }
    resyncEntityAlias(combat)
    return BoundaryLoadOutcome.Valid(copy, authoritativeRank, corrected)
  }

  /**
   * Per-entity v1 -> v2 step, used by boundary migration.
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
   * `combat.entity` is projection-only: rebuild it from
   * `entities[activeEntityIndex]`, mirroring the runtime's
   * `syncActiveEntityAlias`. Never an independent record.
   */
  fun resyncEntityAlias(combat: JSONObject) {
    val entities = combat.optJSONArray("entities")
    if (entities == null || entities.length() == 0) {
      combat.remove("entity")
      return
    }
    val index = combat.optInt("activeEntityIndex", 0).coerceIn(0, entities.length() - 1)
    entities.optJSONObject(index)?.let { combat.put("entity", it) }
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
