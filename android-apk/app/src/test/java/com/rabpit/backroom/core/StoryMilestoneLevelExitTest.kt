package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * All event prose is TEST_ONLY; tests never seed the actual game's Canon.
 * Level completion belongs to Gemini-authored Milestone, not HiddenExitStreak.
 */
class StoryMilestoneLevelExitTest {
  private fun plan(id: String, completion: JSONObject): String =
    JSONObject().put("schemaVersion", 2).put("checkpointId", id)
      .put("events", JSONArray().put(JSONObject()
        .put("id", "final").put("turn", 1).put("kind", "FINAL_ENTITY")
        .put("action", "TEST_ONLY_ACTION").put("event", "TEST_ONLY_FINAL_ENCOUNTER")
        .put("outcome", "TEST_ONLY_START_COMBAT").put("entityKey", "hound")
        .put("hpMultiplier", 9).put("expMultiplier", 9)))
      .put("completion", completion).toString()

  private fun continuePlan(id: String): String =
    plan(id, JSONObject().put("type", "NEXT_CHECKPOINT"))

  private fun exitPlan(): String = plan("CP-L00-20", JSONObject()
    .put("type", "EXIT_LEVEL").put("targetNodeId", "1")
    .put("nextCheckpointTotal", 3).put("outcome", "TEST_ONLY_LEVEL_EXIT"))

  private fun lastCheckpoint(): GameState {
    val base = StoryCheckpointCore.seedLevelZero(
      CoreClientProjection.initialize(GameState.initial()))
    val world = base.world + ("story.checkpoint" to JSONObject()
      .put("level", 0).put("index", 20).put("total", 20).toString())
    return StoryMilestonePlan.installCurrent(base.copy(world = world), exitPlan())
  }

  private fun destroyed(state: GameState): Pair<GameState, String> {
    val id = requireNotNull(CombatRuntime.active(state)).encounterId
    val defeated = CombatRuntime.resolve(state.copy(
      metadata = state.metadata + ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY")
    assertTrue(defeated.entityDestroyed)
    return StoryMilestoneRuntime.afterCombat(CoreClientProjection.advance(defeated.state),
      id, true, false) to id
  }

  @Test fun completionChangesMainLevelAfterConfirmedFinalCombatEvenWithoutOldExitRoll() {
    val initial = lastCheckpoint()
    val boss = StoryMilestoneRuntime.acceptTurn(initial, """["final"]""")
    val active = requireNotNull(CombatRuntime.active(boss))
    assertEquals("hound", active.entityKey)
    val stage = StoryMilestonePlan.prepareNextDuringCombat(boss, continuePlan("CP-L01-01"))
    val (completed, _) = destroyed(stage)
    assertEquals("CP-L01-01", StoryCheckpointCore.current(completed)!!.id)
    assertEquals(3, StoryCheckpointCore.current(completed)!!.total)
    assertEquals("1", JSONObject(completed.world.getValue("levelJson")).getString("id"))
    assertEquals("TEST_ONLY_LEVEL_EXIT", JSONObject(completed.world.getValue("story.levelExitLast"))
      .getString("outcome"))
    assertEquals("CP-L01-01", StoryMilestonePlan.current(completed)!!.plan.checkpointId)
    assertNull(CombatRuntime.active(completed))
    assertFalse(StoryMilestoneRuntime.awaitingPlan(completed))
    assertTrue(StoryMilestoneRuntime.due(completed).map { it.id }.contains("final"))
    val decoded = GameStateCodec.decode(GameStateCodec.encode(completed))
    assertEquals("CP-L01-01", StoryCheckpointCore.current(decoded)!!.id)
    assertEquals("1", JSONObject(decoded.world.getValue("levelJson")).getString("id"))
  }

  @Test fun lateNextPlanAfterCombatStillPromotesLevelExactlyOnce() {
    val boss = StoryMilestoneRuntime.acceptTurn(lastCheckpoint(), """["final"]""")
    val (waiting, _) = destroyed(boss)
    assertEquals("CP-L00-20", StoryCheckpointCore.current(waiting)!!.id)
    assertEquals("0", JSONObject(waiting.world.getValue("levelJson")).optString("number"))
    assertTrue(StoryMilestoneRuntime.awaitingPlan(waiting))
    val stage = StoryMilestonePlan.prepareNextDuringCombat(waiting, continuePlan("CP-L01-01"))
    val promoted = StoryMilestoneRuntime.activateIfReady(stage)
    assertEquals("CP-L01-01", StoryCheckpointCore.current(promoted)!!.id)
    assertEquals(promoted, StoryMilestoneRuntime.activateIfReady(promoted))
  }

  @Test fun unsupportedOrPrematureLevelExitIsRejectedByPlanContract() {
    val last = StoryCheckpointCore.Position(0, 20, 20)
    for (target in listOf("invalid", "7", "0.1")) {
      assertThrows(Exception::class.java) {
        StoryMilestonePlan.parse(JSONObject(exitPlan()).apply {
          getJSONObject("completion").put("targetNodeId", target)
        }.toString(), last)
      }
    }
    assertThrows(Exception::class.java) {
      StoryMilestonePlan.parse(JSONObject(exitPlan()).apply {
        getJSONObject("completion").put("nextCheckpointTotal", 0)
      }.toString(), last)
    }
    assertThrows(Exception::class.java) {
      StoryMilestonePlan.parse(exitPlan(), StoryCheckpointCore.Position(0, 1, 20))
    }
  }

  @Test fun skippedScheduledTurnIsNeverSilentlyReplayedAtLaterTurn() {
    val base = StoryCheckpointCore.seedLevelZero(
      CoreClientProjection.initialize(GameState.initial()))
    val installed = StoryMilestonePlan.installCurrent(base, continuePlan("CP-L00-01"))
    val skipped = CoreClientProjection.advance(installed)
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.due(skipped)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.acceptTurn(skipped, """["final"]""")
    }
  }
}
