package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryMilestoneRuntimeTest {
  private fun plan(checkpoint: String = "CP-L00-01"): String =
    JSONObject().put("schemaVersion", 2).put("checkpointId", checkpoint)
      .put("events", JSONArray()
        .put(JSONObject().put("id", "warning").put("turn", 1)
          .put("kind", "STORY").put("action", "TEST_ONLY_ACTION").put("event", "Có dấu hiệu bất thường")
          .put("outcome", "Cao Minh nghe thấy dấu hiệu"))
        .put(JSONObject().put("id", "boss").put("turn", 3)
          .put("kind", "FINAL_ENTITY").put("action", "TEST_ONLY_ACTION").put("event", "Hound lao ra")
          .put("outcome", "Combat bắt đầu").put("entityKey", "hound")
          .put("hpMultiplier", 9).put("expMultiplier", 9)))
      .toString()

  private fun initial(): GameState =
    StoryMilestonePlan.installCurrent(
      StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())), plan())

  @Test fun dueEventsRequireExactReceiptsAndDoNotRepeatAfterSaveLoad() {
    val before = initial()
    assertEquals(listOf("warning"), StoryMilestoneRuntime.due(before).map { it.id })
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.acceptTurn(before, "[]")
    }
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.acceptTurn(before, "[\"boss\"]")
    }
    val committed = StoryMilestoneRuntime.acceptTurn(before, "[\"warning\"]")
    val after = CoreClientProjection.advance(committed)
    assertEquals(0, StoryMilestoneRuntime.due(after).size)
    val restored = GameStateCodec.decode(GameStateCodec.encode(after))
    assertEquals(0, StoryMilestoneRuntime.due(restored).size)
    assertNull(CombatRuntime.active(restored))
  }

  @Test fun finalEncounterIsExactlyNineTimesHpAndExpWithoutChangingBaseEntity() {
    val before = initial()
    val warning = StoryMilestoneRuntime.acceptTurn(before, "[\"warning\"]")
    val dueOnThree = CoreClientProjection.advance(CoreClientProjection.advance(warning))
    assertEquals(listOf("boss"), StoryMilestoneRuntime.due(dueOnThree).map { it.id })
    val ordinary = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!
    val boss = StoryMilestoneRuntime.acceptTurn(dueOnThree, "[\"boss\"]")
    val active = CombatRuntime.active(boss)!!
    assertEquals(ordinary.entityMaxHp * 9, active.entityMaxHp)
    assertEquals(active.entityMaxHp, active.entityHp)
    assertEquals(active.encounterId, StoryMilestoneRuntime.bossEncounterId(boss))
    assertEquals("hound", JSONObject(boss.world.getValue("flagsJson")).getString("entityEncounterKey"))
    val restored = GameStateCodec.decode(GameStateCodec.encode(boss))
    assertEquals(active.entityMaxHp, CombatRuntime.active(restored)!!.entityMaxHp)

    val defeated = CombatRuntime.resolve(restored.copy(
      metadata = restored.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    assertTrue(defeated.entityDestroyed)
    val expMarker = "exp.awarded." + active.encounterId
    assertEquals("18", defeated.state.metadata[expMarker])
    val closed = StoryMilestoneRuntime.afterCombat(defeated.state, active.encounterId,
      defeated.entityDestroyed, defeated.escaped)
    assertEquals("DEFEATED", StoryMilestoneRuntime.bossResult(closed))
    assertEquals("CP-L00-01", StoryCheckpointCore.current(closed)!!.id)
    assertTrue(StoryMilestoneRuntime.awaitingPlan(closed))
    assertEquals(110, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)
  }

  @Test fun lateGeminiPlanActivatesNextCheckpointAfterVerifiedBossDefeat() {
    val current = initial()
    val afterWarning = StoryMilestoneRuntime.acceptTurn(current, "[\"warning\"]")
    val onThree = CoreClientProjection.advance(CoreClientProjection.advance(afterWarning))
    val boss = StoryMilestoneRuntime.acceptTurn(onThree, "[\"boss\"]")
    val id = CombatRuntime.active(boss)!!.encounterId
    val defeated = CombatRuntime.resolve(
      boss.copy(metadata = boss.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    val waiting = StoryMilestoneRuntime.afterCombat(CoreClientProjection.advance(defeated.state),
      id, defeated.entityDestroyed, defeated.escaped)
    assertTrue(StoryMilestoneRuntime.awaitingPlan(waiting))
    val staged = StoryMilestonePlan.prepareNextDuringCombat(waiting, plan("CP-L00-02"))
    val promoted = StoryMilestoneRuntime.activateIfReady(staged)
    assertEquals("CP-L00-02", StoryCheckpointCore.current(promoted)!!.id)
    assertEquals("CP-L00-02", StoryMilestonePlan.current(promoted)!!.plan.checkpointId)
    assertNull(StoryMilestonePlan.prepared(promoted))
    assertNull(StoryMilestoneRuntime.bossEncounterId(promoted))
    assertNull(StoryMilestoneRuntime.bossResult(promoted))
    assertEquals(listOf("warning"), StoryMilestoneRuntime.due(promoted).map { it.id })
    val reloaded = GameStateCodec.decode(GameStateCodec.encode(promoted))
    assertEquals("CP-L00-02", StoryCheckpointCore.current(reloaded)!!.id)
  }

  @Test fun anUnrelatedEntityDoesNotReceiveBossExpOrAdvanceCheckpoint() {
    val current = initial()
    val normal = CombatRuntime.start(current, "hound")
    val id = CombatRuntime.active(normal)!!.encounterId
    val defeated = CombatRuntime.resolve(normal.copy(
      metadata = normal.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    assertTrue(defeated.entityDestroyed)
    assertEquals("2", defeated.state.metadata["exp.awarded.$id"])
    val after = StoryMilestoneRuntime.afterCombat(defeated.state, id, true, false)
    assertEquals("CP-L00-01", StoryCheckpointCore.current(after)!!.id)
    assertNull(StoryMilestoneRuntime.bossResult(after))
    assertEquals(listOf("warning"), StoryMilestoneRuntime.due(after).map { it.id })
  }

  @Test fun finalCheckpointLevelEndingTransitionsOnlyAfterVerifiedCombatAndPreparedPlan() {
    val initialState = StoryCheckpointCore.seedLevelZero(
      CoreClientProjection.initialize(GameState.initial()))
    val last = initialState.copy(world = initialState.world +
      ("story.checkpoint" to JSONObject()
        .put("level", 0).put("index", 20).put("total", 20).toString()))
    val finalScript = JSONObject(plan("CP-L00-20"))
      .put("levelEnd", JSONObject()
        .put("targetNode", "1")
        .put("nextCheckpointTotal", 5)
        .put("outcome", "TEST_ONLY_AUTHORED_LEVEL_END"))
      .toString()
    val active = StoryMilestonePlan.installCurrent(last, finalScript)
    val atTwo = StoryMilestoneRuntime.acceptTurn(active, "[\"warning\"]")
    val atThree = CoreClientProjection.advance(CoreClientProjection.advance(atTwo))
    val boss = StoryMilestoneRuntime.acceptTurn(atThree, "[\"boss\"]")
    val encounter = CombatRuntime.active(boss)!!.encounterId
    // A route proposal by the GM is not a reason to update the main Level.
    assertEquals(0, JSONObject(boss.world.getValue("levelJson")).getInt("number"))
    assertEquals("CP-L00-20", StoryCheckpointCore.current(boss)!!.id)
    val destroyed = CombatRuntime.resolve(boss.copy(
      metadata = boss.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    assertTrue(destroyed.entityDestroyed)
    val waiting = StoryMilestoneRuntime.afterCombat(
      CoreClientProjection.advance(destroyed.state), encounter, true, false)
    assertTrue(StoryMilestoneRuntime.awaitingPlan(waiting))
    assertEquals(0, JSONObject(waiting.world.getValue("levelJson")).getInt("number"))
    val prepared = StoryMilestonePlan.prepareNextDuringCombat(waiting, plan("CP-L01-01"))
    val completed = StoryMilestoneRuntime.activateIfReady(prepared)
    assertEquals("CP-L01-01", StoryCheckpointCore.current(completed)!!.id)
    assertEquals(5, StoryCheckpointCore.current(completed)!!.total)
    assertEquals(1, JSONObject(completed.world.getValue("levelJson")).getInt("number"))
    assertEquals("1", JSONObject(completed.world.getValue("levelJson")).getString("id"))
    assertTrue(completed.world.getValue("location").contains("Level 1"))
    assertEquals("TEST_ONLY_AUTHORED_LEVEL_END",
      JSONObject(completed.world.getValue("story.lastLevelEnding")).getString("outcome"))
    assertEquals("CP-L01-01", StoryMilestonePlan.current(completed)!!.plan.checkpointId)
    assertNull(CombatRuntime.active(completed))
    val restored = GameStateCodec.decode(GameStateCodec.encode(completed))
    assertEquals(completed.world["story.checkpoint"], restored.world["story.checkpoint"])
    assertEquals("CP-L01-01", StoryCheckpointCore.current(
      StoryMilestoneRuntime.activateIfReady(restored))!!.id)
  }

  @Test fun missedMandatoryTurnIsRejectedRatherThanQuietlyShiftedLater() {
    val current = initial()
    val afterMissed = CoreClientProjection.advance(current)
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.due(afterMissed)
    }
  }
}
