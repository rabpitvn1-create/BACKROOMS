package com.rabpit.backroom.core

import org.json.JSONObject
import java.util.Locale

/**
 * Narrative checkpoints, not manual save checkpoints, action-session checkpoints or Level nodes.
 *
 * This deliberately stores only structural identity. Gemini's compulsory turn events and
 * outcomes belong to milestone planning (step 4); verified final combat and next-plan
 * activation will be wired through Game Core in step 6. A normal player turn never advances
 * a checkpoint.
 */
object StoryCheckpointCore {
  private const val WORLD_KEY = "story.checkpoint"
  const val LEVEL_ZERO_TOTAL = 20

  data class Position(val level: Int, val index: Int, val total: Int) {
    init {
      require(level >= 0) { "checkpoint_level_invalid" }
      require(total > 0) { "checkpoint_total_invalid" }
      require(index in 1..total) { "checkpoint_index_invalid" }
      if (level == 0) require(total == LEVEL_ZERO_TOTAL) { "checkpoint_level_zero_total_locked" }
    }

    val id: String get() = String.format(Locale.ROOT, "CP-L%02d-%02d", level, index)
    val isLast: Boolean get() = index == total

    /** This is an address, not permission to transition. */
    fun nextAddress(): Position? = if (isLast) null else copy(index = index + 1)
  }

  fun current(state: GameState): Position? {
    val raw = state.world[WORLD_KEY] ?: return null
    val record = JSONObject(raw)
    return Position(
      level = record.getInt("level"),
      index = record.getInt("index"),
      total = record.getInt("total")
    )
  }

  /**
   * Seed the first checkpoint only when the authoritative Level is 0.
   * Existing checkpoint progress is never reset on a Core projection, save/load or turn.
   */
  fun seedLevelZero(state: GameState): GameState {
    if (WORLD_KEY in state.world) {
      current(state) // Validate, do not silently replace corrupt state.
      return state
    }
    val level = state.world["levelJson"]?.let { JSONObject(it).optInt("number", -1) } ?: return state
    if (level != 0) return state
    return state.copy(world = state.world + (WORLD_KEY to encode(Position(0, 1, LEVEL_ZERO_TOTAL))))
  }

  /** For future Level planners. No counts for Levels 1+ have been canonically decided. */
  fun addressForLevel(level: Int, index: Int, plannedTotal: Int): Position =
    Position(level, index, plannedTotal)

  private fun encode(value: Position): String = JSONObject()
    .put("level", value.level)
    .put("index", value.index)
    .put("total", value.total)
    .toString()
}
