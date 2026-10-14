package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the P0 level transition fix in GameCoreFacade.processValidatedCandidate.
 *
 * The old code rejected ALL cross-parent moves (proposed.parent != currentNode.parent),
 * but valid LevelRouteRegistry edges CAN change parent (e.g. red_rooms -> 1, 1 -> 2).
 * The fix allows transitions ONLY via valid edges AND with Core-confirmed exit readiness.
 */
class LevelTransitionValidationTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    private var checkpoint: GameCheckpoint? = null
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
      checkpoint = GameCheckpoint(state, JSONObject(presentation.toString()))
    }
    override fun loadCheckpoint(): GameCheckpoint? = checkpoint
    override fun exists() = true
    override fun clear() { checkpoint = null }
  }

  private fun facade(save: MemorySave): GameCoreFacade {
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(save, NoOpGamePipelineLogger)
  }

  private fun freshAt(nodeId: String): GameState {
    val node = LevelRouteRegistry.node(nodeId)!!
    val base = CoreClientProjection.initialize(GameState.initial())
    return base.copy(world = base.world + ("levelJson" to node.asJson().toString()))
  }

  private fun beforeJson(turn: Int, streak: Int): String =
    JSONObject()
      .put("turn", turn)
      .put("flags", JSONObject()
        .put("exploration", JSONObject()
          .put(HiddenExitStreak.STREAK_KEY, streak)))
      .toString()

  private fun candidateJson(levelId: String, rollSuccess: Boolean): String {
    val node = LevelRouteRegistry.node(levelId)!!
    val rolls = JSONObject()
      .put(HiddenExitStreak.ROLL_KEY, JSONObject()
        .put("eligible", true)
        .put("success", rollSuccess))
    return JSONObject()
      .put("level", node.asJson())
      .put("location", node.location())
      .put("title", node.title())
      .put("flags", JSONObject()
        .put("currentLevel", node.asJson())
        .put("lastRolls", rolls))
      .toString()
  }

  @Test fun validTransitionViaValidEdgeWithExitReadyIsAllowed() {
    // Core at "0" (parent 0), streak=5 (exit ready).
    // Candidate proposes "0.1" — valid forward edge from "0".
    val save = MemorySave(freshAt("0"))
    val core = facade(save)
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1, streak = 5),
      candidateJson("0.1", rollSuccess = true),
      "đi qua cửa"))
    assertNotEquals("level_change_forbidden", result.optString("error"))
  }

  @Test fun crossParentValidEdgeIsAllowed() {
    // Core at "red_rooms" (parent 0), candidate proposes "1" (parent 1).
    // forward("red_rooms", "1") is a valid edge — the old parent-equality check
    // wrongly rejected this legitimate transition.
    val save = MemorySave(freshAt("red_rooms"))
    val core = facade(save)
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1, streak = 5),
      candidateJson("1", rollSuccess = true),
      "bước qua cửa đỏ"))
    assertNotEquals("level_change_forbidden", result.optString("error"))
  }

  @Test fun teleportToNonAdjacentNodeIsRejected() {
    // Core at "0", candidate proposes "5" — no edge from "0" to "5".
    // Even with exit ready, this teleport must be blocked.
    val save = MemorySave(freshAt("0"))
    val core = facade(save)
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1, streak = 5),
      candidateJson("5", rollSuccess = true),
      "đi qua cửa"))
    assertFalse(result.getBoolean("handled"))
    assertEquals("level_change_forbidden", result.getString("error"))
  }

  @Test fun transitionWithoutExitReadyIsRejected() {
    // Core at "0", streak=0 (no exit readiness).
    // Candidate proposes "0.1" via valid edge, but exit is not ready.
    // GM words alone cannot authorize the move.
    val save = MemorySave(freshAt("0"))
    val core = facade(save)
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1, streak = 0),
      candidateJson("0.1", rollSuccess = false),
      "đi qua cửa"))
    assertFalse(result.getBoolean("handled"))
    assertEquals("level_change_forbidden", result.getString("error"))
  }

  @Test fun levelRetentionWithoutChangeIsAllowed() {
    // Core at "0", candidate stays at "0" — no transition, must not be blocked.
    val save = MemorySave(freshAt("0"))
    val core = facade(save)
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1, streak = 0),
      candidateJson("0", rollSuccess = false),
      "quan sát xung quanh"))
    assertNotEquals("level_change_forbidden", result.optString("error"))
  }

  @Test fun inconsistentLocationDescriptionIsRejected() {
    // Core at "0", candidate proposes "0.1" (valid) but location text describes "Level 5".
    // The smuggled teleport via location must be blocked.
    val save = MemorySave(freshAt("0"))
    val core = facade(save)
    val node01 = LevelRouteRegistry.node("0.1")!!
    val candidate = JSONObject()
      .put("level", node01.asJson())
      .put("location", "Level 5 / Terror Hotel") // contradicts level "0.1"
      .put("title", node01.title())
      .put("flags", JSONObject()
        .put("currentLevel", node01.asJson())
        .put("lastRolls", JSONObject()
          .put(HiddenExitStreak.ROLL_KEY, JSONObject()
            .put("eligible", true).put("success", true))))
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1, streak = 5),
      candidate.toString(),
      "đi qua cửa"))
    assertFalse(result.getBoolean("handled"))
    assertEquals("level_change_forbidden", result.getString("error"))
  }
}
