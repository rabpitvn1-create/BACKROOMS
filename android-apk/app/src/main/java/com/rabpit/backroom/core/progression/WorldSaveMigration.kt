package com.rabpit.backroom.core.progression

/**
 * Wiring seam for the GameState save-version bump (issue #453, re-review
 * point 3).
 *
 * Today `CURRENT_SAVE_VERSION = 3` (GameState.kt) and
 * `GameStateCodec.decode()` routes `version >= 3` straight into
 * `decodeCurrent()`, so existing v3 saves would NEVER pass through any
 * progression migration. The pure step below is specified to run exactly
 * once per v3 save, inside a new `migrateV3ToV4` step:
 *
 * ```kotlin
 * const val CURRENT_SAVE_VERSION = 4 // GameState.kt
 *
 * fun decode(root: JSONObject): GameState {
 *   val version = root.optInt("saveVersion", 0)
 *   return when {
 *     version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
 *     version == 3 -> migrateV3ToV4(root) // NEW
 *     version == 2 && root.has("inventories") -> migrateV2Core(root)
 *     else -> LegacySaveMigration.migrate(root)
 *   }
 * }
 *
 * private fun migrateV3ToV4(root: JSONObject): GameState {
 *   val world = root.optJSONObject("world")?.stringsMap() ?: emptyMap()
 *   val migrated = WorldSaveMigration.migrateV3World(world)
 *   root.put("world", JSONObject(migrated))
 *   root.put("saveVersion", CURRENT_SAVE_VERSION)
 *   val state = decodeCurrent(root)
 *   return state.copy(metadata = state.metadata + ("migratedFromVersion" to "3"))
 * }
 * ```
 *
 * Fail-closed: a v3 save whose level cannot resolve to a known node keeps
 * its original world (with `progressionMigrationError` recorded) and never
 * gets a guessed `worldNodeId`. `decodeCurrent` itself is untouched.
 */
object WorldSaveMigration {

  /** Save version that first carries authoritative `worldNodeId`. */
  const val WORLD_NODE_SAVE_VERSION: Int = 4

  /** True when a decoded world map still needs the v3 -> v4 progression step. */
  fun needsMigration(world: Map<String, String>): Boolean =
    !world.containsKey("worldNodeId") && world.containsKey("levelJson")

  /**
   * Pure v3 -> v4 world step over the decoded world string-map
   * (the `GameState.world` shape). Fail-closed: never guesses a node id.
   */
  fun migrateV3World(world: Map<String, String>): Map<String, String> =
    ProgressionMigration.migrateWorldStateV1ToV2(world)
}
