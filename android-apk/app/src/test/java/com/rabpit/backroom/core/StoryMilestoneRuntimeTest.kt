package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryMilestoneRuntimeTest {
  private fun plan(checkpoint: String = "CP-L00-01"): String =
    JSONObject().put("schemaVersion", 2).put("checkpointId", checkpoint)
      .put("events", TestCheckpointQuota.events(JSONArray()
        .put(JSONObject().put("id", "warning").put("turn", 1)
          .put("kind", "STORY").put("action", "TEST_ONLY world action").put("event", "Có dấu hiệu bất thường")
          .put("outcome", "Cao Minh nghe thấy dấu hiệu"))
        .put(JSONObject().put("id", "boss").put("turn", 3)
          .put("kind", "FINAL_ENTITY").put("action", "TEST_ONLY boss action").put("event", "Hound lao ra")
          .put("outcome", "Combat bắt đầu").put("entityKey", "hound")
          .put("hpMultiplier", 9).put("expMultiplier", 9))))
      .put("completion", if (checkpoint == "CP-L00-20") JSONObject().put("type", "EXIT_LEVEL")
        .put("targetNodeId", "1.2").put("nextCheckpointTotal", 3).put("outcome", "TEST_ONLY Level ending")
        else JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "TEST_ONLY completion"))
      .toString()

  private fun initial(): GameState =
    StoryMilestonePlan.installCurrent(
      StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())), plan())

  @Test fun dueEventsAreAppliedByCoreWithoutGmReceiptsAndDoNotRepeatAfterSaveLoad() {
    val before = initial()
    assertEquals(listOf("warning"), StoryMilestoneRuntime.due(before).map { it.id })
    val committed = StoryMilestoneRuntime.acceptTurn(before)
    assertEquals(committed, StoryMilestoneRuntime.acceptTurn(committed))
    val records = JSONObject(committed.world.getValue("story.milestone.eventRecords"))
    assertEquals(setOf("warning"), records.keys().asSequence().toSet())
    val after = CoreClientProjection.advance(committed)
    assertTrue(StoryMilestoneRuntime.due(after).isEmpty())
    val restored = GameStateCodec.decode(GameStateCodec.encode(after))
    StoryMilestoneRuntime.validateStoredState(restored)
    assertNull(CombatRuntime.active(restored))
  }

  @Test fun finalEncounterIsExactlyNineTimesHpAndExpWithoutChangingBaseEntity() {
    val before = initial()
    val warning = TestMilestoneTurn.accept(before, "[\"warning\"]")
    val dueOnThree = TestCheckpointQuota.reachBoss(warning)
    assertEquals(listOf("boss"), StoryMilestoneRuntime.due(dueOnThree).map { it.id })
    val ordinary = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!
    val boss = TestMilestoneTurn.accept(dueOnThree, "[\"boss\"]")
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

  @Test fun preparedSolPlanActivatesNextCheckpointAfterRealBossDefeat() {
    val current = initial()
    val afterWarning = TestMilestoneTurn.accept(current, "[\"warning\"]")
    val onThree = TestCheckpointQuota.reachBoss(afterWarning)
    val boss = TestMilestoneTurn.accept(onThree, "[\"boss\"]")
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
  @Test fun missedEventFailsExplicitlyInsteadOfReplayingLate() {
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.due(CoreClientProjection.advance(initial()))
    }
  }

  @Test fun onlyAuthoritativeEventRecordsArePersistedAndValidated() {
    val state = initial()
    val committed = StoryMilestoneRuntime.acceptTurn(state)
    StoryMilestoneRuntime.validateStoredState(GameStateCodec.decode(GameStateCodec.encode(committed)))
    val records = JSONObject(committed.world.getValue("story.milestone.eventRecords"))
    assertFalse(records.getJSONObject("warning").has("requestToken"))
    assertFalse(records.getJSONObject("warning").has("verified"))
    val missing = committed.copy(world = committed.world - "story.milestone.eventRecords")
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.validateStoredState(missing) }
    val invented = JSONObject(records.toString()).put("fake_event", JSONObject()
      .put("checkpointId", "CP-L00-01").put("turn", 1))
    assertThrows(Exception::class.java) {
      StoryMilestoneRuntime.validateStoredState(committed.copy(
        world = committed.world + ("story.milestone.eventRecords" to invented.toString())))
    }
    val wrongTurn = JSONObject(records.toString())
    wrongTurn.getJSONObject("warning").put("turn", 999)
    assertThrows(Exception::class.java) {
      StoryMilestoneRuntime.validateStoredState(committed.copy(
        world = committed.world + ("story.milestone.eventRecords" to wrongTurn.toString())))
    }
    assertEquals(0, StoryMilestoneRuntime.entityEncounterCount(committed))
  }

  @Test fun bossCannotEscapeByCommandOrAccumulatedEvasion() {
    val warning = TestMilestoneTurn.accept(initial(), "[\"warning\"]")
    val boss = TestMilestoneTurn.accept(TestCheckpointQuota.reachBoss(warning), "[\"boss\"]")
    val fight = CombatRuntime.active(boss)!!
    val loaded = boss.copy(metadata = boss.metadata + ("combat.escapeProgress" to "100"))
    val rejected = runCatching { CombatRuntime.resolve(loaded, "EXECUTE", "bỏ chạy") }
    assertEquals("milestone_boss_escape_forbidden", rejected.exceptionOrNull()?.message)
    val evaded = CombatRuntime.resolve(loaded, "EXECUTE", "né tránh")
    assertFalse(evaded.escaped)
    assertEquals(fight.encounterId, CombatRuntime.active(evaded.state)!!.encounterId)
    assertNull(evaded.state.world["story.milestone.suspendedBoss"])
    assertEquals("CP-L00-01", StoryCheckpointCore.current(evaded.state)!!.id)
    assertNull(evaded.state.metadata["exp.awarded.${fight.encounterId}"])
  }

  @Test fun allTwentyCheckpointsEndLevelFromAnyNodeOncePreparedPlanArrives() {
    var state = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    for (index in 1..20) {
      val cp = StoryCheckpointCore.current(state)!!
      val root = JSONObject(plan(cp.id))
      if (cp.isLast) root.put("completion", JSONObject().put("type", "EXIT_LEVEL")
        .put("targetNodeId", "1.2").put("nextCheckpointTotal", 3)
        .put("outcome", "TEST_ONLY Level ending"))
      state = StoryMilestonePlan.installCurrent(state, root.toString())
      state = TestMilestoneTurn.accept(state, "[\"warning\"]")
      state = TestCheckpointQuota.reachBoss(state)
      state = TestMilestoneTurn.accept(state, "[\"boss\"]")
      val fight = CombatRuntime.active(state)!!
      val nextId = if (cp.isLast) "CP-L01-01" else cp.nextAddress()!!.id
      // Exercise prefetch before conclusion on odd CPs; delayed response on even CPs.
      if (index % 2 == 1) state = StoryMilestonePlan.prepareNextDuringCombat(state, plan(nextId))
      state = GameStateCodec.decode(GameStateCodec.encode(state))
      val defeated = CombatRuntime.resolve(state.copy(metadata = state.metadata +
        ("combat.entityHp" to "0")), "SEARCH", "quan sát")
      assertTrue(defeated.entityDestroyed)
      state = StoryMilestoneRuntime.afterCombat(CoreClientProjection.advance(defeated.state),
        fight.encounterId, true, false)
      if (index % 2 == 0) {
        assertTrue(StoryMilestoneRuntime.awaitingPlan(state))
        state = StoryMilestoneRuntime.activateIfReady(
          StoryMilestonePlan.prepareNextDuringCombat(state, plan(nextId)))
      }
      assertEquals(nextId, StoryCheckpointCore.current(state)!!.id)
      assertEquals("18", state.metadata["exp.awarded.${fight.encounterId}"])
      assertEquals(state, StoryMilestoneRuntime.afterCombat(state, fight.encounterId, true, false))
    }
    val restored = GameStateCodec.decode(GameStateCodec.encode(state))
    StoryMilestoneRuntime.validateStoredState(restored)
    StoryFactLedger.validateStoredState(restored)
    val stats = StoryMilestoneRuntime.statsJson(restored)
    assertEquals(200L, stats.getLong("committedEvents"))
    assertEquals(20L, stats.getLong("defeatedBosses"))
    assertEquals(20L, stats.getLong("completedCheckpoints"))
    assertEquals(1L, stats.getLong("completedLevels"))
    assertEquals(20, JSONArray(restored.world.getValue("story.checkpointHistory")).length())
    val levelHistory = JSONArray(restored.world.getValue("story.levelHistory"))
    assertEquals(1, levelHistory.length())
    assertEquals(20, levelHistory.getJSONObject(0).getJSONArray("checkpointRefs").length())
    assertEquals(StoryCheckpointCore.Position(1, 1, 3), StoryCheckpointCore.current(restored))
    assertEquals("1.2", JSONObject(restored.world.getValue("levelJson")).getString("id"))
    assertEquals("TEST_ONLY Level ending", JSONObject(restored.world.getValue("story.milestone.lastCompletion"))
      .getJSONObject("completion").getString("outcome"))
  }

  @Test fun futurePlansDoNotCountAsCommittedAndCorruptHistoryCannotLoad() {
    val planned = initial()
    assertEquals(0L, StoryMilestoneRuntime.statsJson(planned).getLong("committedEvents"))
    assertFalse(planned.world.containsKey("story.checkpointHistory"))
    val accepted = TestMilestoneTurn.accept(planned, "[\"warning\"]")
    val wrongStats = JSONObject(StoryMilestoneRuntime.statsJson(accepted).toString()).put("committedEvents", 99)
    val corrupt = accepted.copy(world = accepted.world + ("story.stats" to wrongStats.toString()))
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.validateStoredState(corrupt) }
    val raw = JSONObject().put("saveVersion", CURRENT_SAVE_VERSION)
      .put("core", JSONObject(GameStateCodec.encode(corrupt)))
      .put("presentation", CoreClientProjection.project(JSONObject(), corrupt))
    assertThrows(Exception::class.java) { GameCheckpointCodec.decode(raw.toString()) }
  }
}

