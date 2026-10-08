package com.rabpit.backroom.core.progression

import org.json.JSONException
import org.json.JSONObject

sealed interface WorldMigrationOutcome {
  data class Migrated(val world: Map<String, String>) : WorldMigrationOutcome
  data class Rejected(val reason: String) : WorldMigrationOutcome
}

sealed interface WorldSaveLoadOutcome {
  data class Migrated(val root: JSONObject) : WorldSaveLoadOutcome
  data class Rejected(val reason: String) : WorldSaveLoadOutcome
}

/**
 * Design seam, not yet wired into GameStateCodec. Implementation routing:
 * - saveVersion == 4: validate worldNodeId before decodeCurrent;
 * - saveVersion == 3: migrateV3Save, decode ONLY a Migrated result;
 * - future version: reject;
 * - older versions: run legacy decoding followed by this world migration,
 *   and only publish v4 after success (never bypass via the old decoder).
 * Rejected results surface a load error; no version bump and no fallback.
 * New games explicitly seed freshWorld(), never migrate an empty saved world.
 */
object WorldSaveMigration {
  const val WORLD_NODE_SAVE_VERSION: Int = 4

  fun freshWorld(): Map<String, String> = mapOf("worldNodeId" to "level-0")

  fun needsMigration(world: Map<String, String>): Boolean = !world.containsKey("worldNodeId")

  fun migrateV3World(world: Map<String, String>): WorldMigrationOutcome {
    val existing = world["worldNodeId"]
    if (existing != null) {
      if (WorldProgressionCore.rankOf(WorldNodeId(existing)) == RankLookup.Unknown) {
        return WorldMigrationOutcome.Rejected("unknown worldNodeId: $existing")
      }
      return WorldMigrationOutcome.Migrated(world - "progressionRank" - "progressionMigrationError")
    }
    val raw = world["levelJson"] ?: return WorldMigrationOutcome.Rejected("missing legacy levelJson")
    val number = try {
      ProgressionMigration.strictInteger(JSONObject(raw).opt("number"))
    } catch (_: JSONException) {
      return WorldMigrationOutcome.Rejected("malformed legacy levelJson")
    } ?: return WorldMigrationOutcome.Rejected("missing or malformed legacy level number")
    return when (val result = ProgressionMigration.migrateLegacyLevelNumber(number.toInt())) {
      is MigrationResult.Failed -> WorldMigrationOutcome.Rejected(result.reason)
      is MigrationResult.Migrated -> WorldMigrationOutcome.Migrated(
        (world - "progressionRank" - "progressionMigrationError") + ("worldNodeId" to result.nodeId.value),
      )
    }
  }

  /** Executable shape: clone first, stamp v4 only after successful migration. */
  fun migrateV3Save(root: JSONObject): WorldSaveLoadOutcome {
    if (ProgressionMigration.strictInteger(root.opt("saveVersion")) != 3L) {
      return WorldSaveLoadOutcome.Rejected("expected saveVersion 3")
    }
    val worldJson = root.optJSONObject("world")
      ?: return WorldSaveLoadOutcome.Rejected("missing world object")
    val world = linkedMapOf<String, String>()
    for (key in worldJson.keys()) {
      val value = worldJson.opt(key) as? String
        ?: return WorldSaveLoadOutcome.Rejected("world field is not a string: $key")
      world[key] = value
    }
    return when (val result = migrateV3World(world)) {
      is WorldMigrationOutcome.Rejected -> WorldSaveLoadOutcome.Rejected(result.reason)
      is WorldMigrationOutcome.Migrated -> {
        val copy = JSONObject(root.toString())
        copy.put("world", JSONObject(result.world))
        copy.put("saveVersion", WORLD_NODE_SAVE_VERSION)
        WorldSaveLoadOutcome.Migrated(copy)
      }
    }
  }
}
