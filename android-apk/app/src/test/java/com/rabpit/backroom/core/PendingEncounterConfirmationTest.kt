package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PendingEncounterConfirmationTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    var slot: String? = null
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
      slot = GameCheckpointCodec.encode(state, presentation)
    }
    override fun loadCheckpoint() = slot?.let(GameCheckpointCodec::decode)
  }

  private fun facade(repo: MemorySave): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }

  private fun plan(combat: Boolean = true, checkpoint: String = "CP-L00-01"): String {
    val events = TestCheckpointQuota.events(JSONArray()
      .put(JSONObject().put("id", "mention").put("turn", 1).put("kind", "STORY")
        .put("action", "Cao Minh nhìn dấu vết").put("event", "Lời đồn về Duller")
        .put("outcome", "Chưa gặp Entity"))
      .put(JSONObject().put("id", "boss").put("turn", 18).put("kind", "FINAL_ENTITY")
        .put("action", "Boss chắn đường").put("event", "Hound xuất hiện")
        .put("outcome", "Chờ Battle Start").put("entityKey", "hound")
        .put("hpMultiplier", 9).put("expMultiplier", 9)))
    val values = (0 until events.length()).map { events.getJSONObject(it) }
    values.single { it.getString("id") == "encounter1" }
      .put("turn", 2).put("entityKey", "duller").apply { if (!combat) put("combatRequired", false) }
    return JSONObject().put("schemaVersion", StoryMilestonePlan.SCHEMA_VERSION).put("checkpointId", checkpoint).put("storyArc", TestCheckpointQuota.storyArc())
      .put("events", JSONArray(values.sortedBy { it.getInt("turn") }))
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "Qua Checkpoint"))
      .toString()
  }

  private fun initial(combat: Boolean = true): GameState = StoryMilestonePlan.installCurrent(
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())).let {
      it.copy(world = it.world + ("story.runToken" to "test-run"))
    }, plan(combat))

  private fun onEncounter(combat: Boolean = true): GameState =
    CoreClientProjection.advance(TestMilestoneTurn.dispatch(initial(combat)))

  private fun commit(repo: MemorySave, text: String = "Duller bước ra.\n\nNó đứng chắn lối đi."): JSONObject {
    val before = CoreClientProjection.project(JSONObject(), repo.state)
    return JSONObject(TestMilestoneTurn.commit(facade(repo), before.toString(), before.toString(), "Khám Phá", text))
  }

  @Test fun narrativeCommitsPendingEncounterWithoutCombatHpExpOrDuplicateReservation() {
    val repo = MemorySave(onEncounter())
    val result = commit(repo)
    assertTrue(result.toString(), result.getBoolean("handled"))
    val pending = CombatRuntime.pending(repo.state)!!
    assertNull(CombatRuntime.active(repo.state))
    assertEquals("Duller", result.getJSONObject("state").getJSONObject("pendingEncounter").getString("entityName"))
    assertFalse(result.getJSONObject("state").getJSONObject("combat").getBoolean("active"))
    assertEquals(pending.encounterId, StoryMilestoneRuntime.entityEncounterId(repo.state,
      StoryMilestonePlan.current(repo.state)!!.plan.events.single { it.id == "encounter1" }))
    assertFalse(repo.state.metadata.containsKey("exp.awarded.${pending.encounterId}"))
    assertEquals(0, pending.eventCounter)
    assertEquals(repo.state, TestMilestoneTurn.dispatch(repo.state))
  }

  @Test fun confirmationUsesReservedCoreStatsAndIsIdempotentWithoutExplorerTurnAdvance() {
    val repo = MemorySave(onEncounter())
    val view = commit(repo).getJSONObject("state")
    val before = repo.state
    val pending = CombatRuntime.pending(before)!!
    val core = facade(repo)
    val result = JSONObject(core.confirmEncounter(view.toString(), pending.encounterId))
    assertTrue(result.getBoolean("handled"))
    val active = CombatRuntime.active(repo.state)!!
    assertEquals(pending.copy(phase = CombatRuntime.Phase.ACTIVE), active)
    assertEquals(before.turn, repo.state.turn)
    assertEquals(before.time, repo.state.time)
    assertEquals(before.characters, repo.state.characters)
    assertEquals(before.metadata["loot.encounterSerial"], repo.state.metadata["loot.encounterSerial"])
    assertFalse(result.getJSONObject("state").has("pendingEncounter"))
    val once = repo.state
    repeat(5) { assertTrue(JSONObject(core.confirmEncounter(view.toString(), active.encounterId)).getBoolean("handled")) }
    assertEquals(once, repo.state)
    assertEquals("duller", result.getJSONObject("state").getJSONObject("combat").getString("entityKey"))
    assertFalse(repo.state.metadata.containsKey("exp.awarded.${active.encounterId}"))
  }

  @Test fun staleEncounterOrRunCannotActivateCombat() {
    val repo = MemorySave(onEncounter())
    val view = commit(repo).getJSONObject("state")
    val before = repo.state
    val core = facade(repo)
    assertFalse(JSONObject(core.confirmEncounter(view.toString(), "invented-id")).getBoolean("handled"))
    view.getJSONObject("story").put("runToken", "old-run")
    assertFalse(JSONObject(core.confirmEncounter(view.toString(), CombatRuntime.pending(before)!!.encounterId)).getBoolean("handled"))
    assertEquals(before, repo.state)
  }

  @Test fun saveLoadAndReloadRetainEncounterAndWholeNarrativeWithoutDuplicates() {
    val repo = MemorySave(onEncounter())
    val view = commit(repo).getJSONObject("state")
    val core = facade(repo)
    val pending = CombatRuntime.pending(repo.state)!!
    assertTrue(JSONObject(core.saveManualCheckpoint(view.toString())).getBoolean("ok"))
    val restored = GameStateCodec.decode(GameStateCodec.encode(repo.state))
    StoryMilestoneRuntime.validateStoredState(restored)
    repo.state = restored
    val reload = JSONObject(core.currentClientState("{}"))
    assertEquals(pending.encounterId, reload.getJSONObject("pendingEncounter").getString("encounterId"))
    assertTrue(reload.getJSONArray("log").getJSONObject(0).getString("text").startsWith("Duller bước ra.\n\nNó đứng chắn lối đi."))
    assertEquals(1, JSONObject(core.currentClientState(reload.toString())).getJSONArray("log").length())
    assertTrue(JSONObject(core.confirmEncounter(reload.toString(), pending.encounterId)).getBoolean("handled"))
    val loaded = JSONObject(core.loadManualCheckpoint()).getJSONObject("state")
    assertNull(CombatRuntime.active(repo.state))
    assertEquals(pending, CombatRuntime.pending(repo.state))
    assertTrue(JSONObject(core.confirmEncounter(loaded.toString(), pending.encounterId)).getBoolean("handled"))
  }

  @Test fun pendingEncounterBlocksExplorerActionsAndDoesNotAllowReplacement() {
    val repo = MemorySave(onEncounter())
    val view = commit(repo).getJSONObject("state")
    val before = repo.state
    val core = facade(repo)
    assertFalse(JSONObject(core.beginAction(view.toString(), "EXPLORE", "Khám Phá")).getBoolean("handled"))
    assertFalse(JSONObject(core.processRule(view.toString(), "quan sát")).getBoolean("handled"))
    assertFalse(JSONObject(TestMilestoneTurn.commit(core, view.toString(), view.toString(), "Khám Phá", "Tiếp")).getBoolean("handled"))
    assertEquals(before, CombatRuntime.start(before, "hound"))
    assertEquals(before, repo.state)
  }

  @Test fun missingGehihiNarrativeCannotCommitOrStartEncounter() {
    val repo = MemorySave(onEncounter())
    val before = repo.state
    assertEquals("encounter_narrative_missing", commit(repo, "").getString("error"))
    assertEquals(before, repo.state)
    assertNull(CombatRuntime.pending(repo.state))
    assertNull(CombatRuntime.active(repo.state))
  }

  @Test fun failedGehihiRequestDoesNotReserveOrActivateCombat() {
    val repo = MemorySave(onEncounter())
    val core = facade(repo)
    val view = CoreClientProjection.project(JSONObject(), repo.state).toString()
    val turn = CoreClientProjection.turnIndex(repo.state)
    assertTrue(JSONObject(core.beginAction(view, "EXPLORE", "Khám Phá")).getBoolean("handled"))
    assertTrue(core.abortAction("pipeline_error"))
    assertEquals(turn, CoreClientProjection.turnIndex(repo.state))
    assertNull(CombatRuntime.pending(repo.state))
    assertNull(CombatRuntime.active(repo.state))
    assertEquals(listOf("encounter1"), StoryMilestoneRuntime.due(repo.state).map { it.id })
  }

  @Test fun missingMilestoneNeverFallsBackToRandomCombat() {
    val repo = MemorySave(CoreClientProjection.initialize(GameState.initial()))
    val beforeState = repo.state
    val core = facade(repo)
    val before = CoreClientProjection.project(JSONObject(), repo.state)
    val candidate = JSONObject(before.toString())
    candidate.getJSONObject("flags").put("lastRolls", JSONObject().put("turn", 1).put("selectedEntityKey", "hound")
      .put("entityEncounter", JSONObject().put("eligible", true).put("success", true)))
    val result = JSONObject(TestMilestoneTurn.commit(core, before.toString(), candidate.toString(), "Khám Phá", "Hound xuất hiện."))
    assertFalse(result.getBoolean("handled"))
    assertEquals("milestone_plan_required", result.getString("error"))
    assertEquals(beforeState, repo.state)
    assertNull(CombatRuntime.pending(repo.state))
    assertNull(CombatRuntime.active(repo.state))
  }

  @Test fun combatRequiredFlagIsForbiddenAndStoryMentionsCannotClaimEntityCombat() {
    val raw = JSONObject(plan())
    val entries = raw.getJSONArray("events")
    entries.getJSONObject(1).put("combatRequired", "true")
    assertThrows(Exception::class.java) { StoryMilestonePlan.installCurrent(initial(), raw.toString()) }
    entries.getJSONObject(1).put("combatRequired", true)
    entries.getJSONObject(0).put("combatRequired", true)
    assertThrows(Exception::class.java) { StoryMilestonePlan.installCurrent(initial(), raw.toString()) }
  }

  @Test fun storyMentionDoesNotStartCombatButScheduledEntityAlwaysDoes() {
    val mentioned = TestMilestoneTurn.dispatch(initial())
    assertNull(CombatRuntime.pending(mentioned))
    assertNull(CombatRuntime.active(mentioned))
    val repo = MemorySave(onEncounter())
    val result = commit(repo, "Duller bước tới chặn lối, Cao Minh chuẩn bị chiến đấu.")
    assertTrue(result.getBoolean("handled"))
    assertEquals("duller", result.getJSONObject("state").getJSONObject("pendingEncounter").getString("entityKey"))
    assertNull(CombatRuntime.active(repo.state))
  }

  @Test fun bossWaitsForConfirmationAndCheckpointOnlyAdvancesAfterDefeat() {
    val due = TestCheckpointQuota.reachBoss(initial())
    val before = TestMilestoneTurn.dispatch(due)
    val pending = CombatRuntime.pending(before)!!
    assertNull(CombatRuntime.active(before))
    assertEquals(pending.encounterId, StoryMilestoneRuntime.bossEncounterId(before))
    assertEquals(CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp * 9, pending.entityMaxHp)
    assertEquals(before, StoryMilestoneRuntime.activateIfReady(before))
    val confirmed = CombatRuntime.confirmEncounter(CoreClientProjection.advance(before), pending.encounterId)
    val prepared = StoryMilestonePlan.prepareNextDuringCombat(confirmed, plan(true, "CP-L00-02"))
    assertEquals("CP-L00-01", StoryCheckpointCore.current(prepared)!!.id)
    val ended = CombatRuntime.resolve(prepared.copy(metadata = prepared.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    assertTrue(ended.entityDestroyed)
    val next = StoryMilestoneRuntime.afterCombat(ended.state, pending.encounterId, true, false)
    assertEquals("18", next.metadata["exp.awarded.${pending.encounterId}"])
    assertEquals("CP-L00-02", StoryCheckpointCore.current(next)!!.id)
    StoryMilestoneRuntime.validateStoredState(next)
  }
}
