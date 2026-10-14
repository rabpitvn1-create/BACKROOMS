package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MilestoneEncounterFlowTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    var checkpoint: String? = null
    var failWrites = false
    override fun save(state: GameState) { check(!failWrites) { "test_save_failed" }; this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { checkpoint = GameCheckpointCodec.encode(state, presentation) }
    override fun loadCheckpoint() = checkpoint?.let(GameCheckpointCodec::decode)
  }
  private fun facade(repo: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }
  private fun characterEvent(id: String, turn: Int, kind: String, characterId: String) = JSONObject()
    .put("id", id).put("turn", turn).put("kind", kind).put("characterId", characterId)
    .put("action", "Nhân vật bước ra").put("event", "Gặp Cao Minh").put("outcome", "Đã gặp, chưa gia nhập")
  private fun initial(): GameState {
    val base = CoreClientProjection.initialize(GameState.initial()).let { state ->
      state.copy(characters = state.characters + ("test_npc" to CharacterState("test_npc", "TEST_ONLY NPC", metadata = mapOf("npcType" to "npc"))),
        world = state.world + ("story.runToken" to "flow-test"))
    }
    val seeded = StoryCheckpointCore.seedLevelZero(base)
    val values = TestCheckpointQuota.events(JSONArray().put(JSONObject()
      .put("id", "boss").put("turn", 18).put("kind", "FINAL_ENTITY").put("entityKey", "hound")
      .put("action", "Boss xuất hiện").put("event", "Boss chặn lối").put("outcome", "Chờ Battle Start")
      .put("hpMultiplier", 9).put("expMultiplier", 9)))
    val list = (0 until values.length()).map { values.getJSONObject(it) }.toMutableList()
    list.single { it.getString("id") == "encounter1" }.put("turn", 5)
    list.single { it.getString("id") == "encounter2" }.put("turn", 4).put("entityKey", "duller")
    list += characterEvent("lucia3", 3, "FOLLOWER", LUCIA_ID)
    list += characterEvent("iris5", 5, "FOLLOWER", IRIS_ID)
    list += characterEvent("npc5", 5, "NPC", "test_npc")
    list += JSONObject().put("id", "story5").put("turn", 5).put("kind", "STORY")
      .put("action", "Cánh cửa khép lại").put("event", "Căn phòng đổi ánh sáng").put("outcome", "Lối đi đã thay đổi")
    val raw = JSONObject().put("schemaVersion", StoryMilestonePlan.SCHEMA_VERSION).put("checkpointId", "CP-L00-01").put("storyArc", TestCheckpointQuota.storyArc())
      .put("events", JSONArray(list.sortedBy { it.getInt("turn") }))
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "Qua CP")).toString()
    return StoryMilestonePlan.installCurrent(seeded, raw)
  }
  private fun view(core: GameCoreFacade) = JSONObject(core.currentClientState("{}"))
  private fun commit(core: GameCoreFacade, edit: (JSONObject) -> Unit = {}): JSONObject {
    val before = view(core); val candidate = JSONObject(before.toString()); edit(candidate)
    val result = JSONObject(TestMilestoneTurn.commit(core, before.toString(), candidate.toString(), "Khám Phá", "Một bóng người bước ra.\n\nCao Minh dừng lại."))
    assertTrue(result.toString(), result.getBoolean("handled")); return result.getJSONObject("state")
  }
  /** Synthetic victory: resolve a mandatory ordinary encounter without consuming Explorer Turns. */
  private fun finishCombat(repo: MemorySave) {
    val pending = requireNotNull(CombatRuntime.pending(repo.state))
    val active = CombatRuntime.confirmEncounter(repo.state, pending.encounterId)
    val outcome = CombatRuntime.resolve(active.copy(metadata = active.metadata +
      ("combat.entityHp" to "0")), "SEARCH", "TEST_ONLY resolve ordinary Entity")
    assertTrue(outcome.entityDestroyed)
    repo.state = StoryMilestoneRuntime.afterCombat(outcome.state, pending.encounterId, true, false)
    assertNull(CombatRuntime.pending(repo.state))
    assertNull(CombatRuntime.active(repo.state))
  }
  private fun reach(core: GameCoreFacade, repo: MemorySave, turn: Int) {
    while (view(core).getInt("turn") < turn) {
      if (CombatRuntime.pending(repo.state) != null) finishCombat(repo)
      commit(core)
    }
    // The target Explorer Turn may already have been reached by an encounter,
    // but the next action must not be attempted while the preceding Combat is pending.
    if (CombatRuntime.pending(repo.state) != null) finishCombat(repo)
  }

  @Test fun executeActionStillConsumesExplorerTurnAndAppliesScheduledLuciaMeeting() {
    val repo = MemorySave(initial()); val core = facade(repo)
    reach(core, repo, 3)
    val before = view(core)
    val action = "Cao Minh dùng thần thức tìm Lục Trầm"
    assertTrue(JSONObject(core.beginAction(before.toString(), "EXECUTE", action)).getBoolean("handled"))
    val response = JSONObject(TestMilestoneTurn.commit(core, before.toString(), before.toString(),
      action, "Cao Minh dùng thần thức. Lucia nhìn hắn cảnh giác và nói: \"Anh đang tìm ai?\""))
    assertTrue(response.toString(), response.getBoolean("handled"))
    assertEquals(4, view(core).getInt("turn"))
    assertEquals("3", repo.state.characters.getValue(LUCIA_ID).metadata["encounterTurn"])
    assertTrue(StoryMilestoneRuntime.due(repo.state).none { it.id == "lucia3" })
  }

  @Test fun plannerLeaseContainsActualRegisteredNpcIdsAndNames() {
    val state = initial().let { it.copy(world = it.world - "story.milestone.active") }
    val registry = JSONObject(facade(MemorySave(state)).milestonePlanRequest()).getJSONArray("characterRegistry")
    val npc = (0 until registry.length()).map { registry.getJSONObject(it) }.single { it.getString("id") == "test_npc" }
    assertEquals("TEST_ONLY NPC", npc.getString("name"))
    assertEquals("NPC", npc.getString("kind"))
    assertTrue(npc.getBoolean("alive"))
  }

  @Test fun luciaTurnThreeAndHoundTurnFiveExecuteWithoutNamesOrRandomDecisions() {
    val repo = MemorySave(initial()); val core = facade(repo)
    reach(core, repo, 3)
    assertFalse(repo.state.characters.getValue(LUCIA_ID).metadata.containsKey("encountered"))
    val scheduled = JSONArray(core.milestoneDueEvents()).getJSONObject(0)
    val lucia = commit(core) { candidate -> candidate.getJSONObject("flags").put("lastRolls", JSONObject()
      .put("luciaEncounter", JSONObject().put("success", false))) }
    assertEquals(scheduled.getString("encounterId"), repo.state.characters.getValue(LUCIA_ID).metadata["encounterId"])
    assertEquals("3", repo.state.characters.getValue(LUCIA_ID).metadata["encounterTurn"])
    assertEquals(0, lucia.getJSONArray("party").length())
    val duller = commit(core)
    assertTrue(JSONObject(repo.state.world.getValue("story.milestone.eventRecords")).has("encounter2"))
    assertEquals("duller", duller.getJSONObject("pendingEncounter").getString("entityKey"))
    assertNull(CombatRuntime.active(repo.state))
    finishCombat(repo)
    val hound = commit(core) { candidate -> candidate.getJSONObject("flags").put("lastRolls", JSONObject()
      .put("selectedEntityKey", "duller").put("entityEncounter", JSONObject().put("success", false))) }
    assertEquals("hound", hound.getJSONObject("pendingEncounter").getString("entityKey"))
    assertEquals(CombatRuntime.entityName("hound"), hound.getJSONObject("pendingEncounter").getString("entityName"))
    assertNull(CombatRuntime.active(repo.state))
    assertEquals("5", repo.state.characters.getValue(IRIS_ID).metadata["encounterTurn"])
    assertEquals("5", repo.state.characters.getValue("test_npc").metadata["encounterTurn"])
  }

  @Test fun multipleDueEventsKeepAcceptedOrderAndEachCommitsOnce() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 5)
    val due = StoryMilestoneRuntime.due(repo.state)
    assertEquals(listOf("encounter1", "iris5", "npc5", "story5"), due.map { it.id })
    commit(core)
    val recent = JSONObject(StoryMilestoneRuntime.continuityContext(repo.state)).getJSONArray("recentCommittedEvents")
    assertEquals(due.map { it.id }, (recent.length()-4 until recent.length()).map { recent.getJSONObject(it).getString("id") })
    val records = JSONObject(repo.state.world.getValue("story.milestone.eventRecords"))
    due.forEach { assertEquals(5, records.getJSONObject(it.id).getInt("turn")) }
    assertEquals(repo.state, TestMilestoneTurn.dispatch(repo.state))
  }

  @Test fun failedNarrationAndRetryKeepTheSameDueEncounterWithoutPartialCommit() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 3)
    val due = core.milestoneDueEvents()
    repeat(2) {
      assertTrue(JSONObject(core.beginAction(view(core).toString(), "EXPLORE", "Khám Phá")).getBoolean("handled"))
      assertTrue(core.abortAction("pipeline_error"))
      assertEquals(due, core.milestoneDueEvents())
      assertEquals(3, view(core).getInt("turn"))
      assertFalse(repo.state.characters.getValue(LUCIA_ID).metadata.containsKey("encountered"))
    }
    val before = view(core)
    assertFalse(JSONObject(TestMilestoneTurn.commit(core, before.toString(), before.toString(), "Khám Phá", "")).getBoolean("handled"))
    commit(core)
    assertEquals("3", repo.state.characters.getValue(LUCIA_ID).metadata["encounterTurn"])
    assertTrue(StoryMilestoneRuntime.due(repo.state).none { it.id == "lucia3" })
  }

  @Test fun coreRejectsIdOnlyAndMissingCommunicationWithoutTurnTimeOrMeetingChanges() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 3)
    val before = view(core)
    val due = JSONArray(core.milestoneDueEvents())
    val generated = TestMilestoneTurn.narration(due)
    generated.getJSONArray("eventNarrations").getJSONObject(0).remove("communication")
    val unchanged = repo.state
    val rejected = JSONObject(core.processValidatedCandidate(before.toString(), before.toString(),
      "Khám Phá", "Lucia đứng quan sát.", generated.toString()))
    assertEquals("milestone_character_communication_missing", rejected.getString("error"))
    assertEquals(unchanged, repo.state)
    generated.remove("eventNarrations")
    assertFalse(JSONObject(core.processValidatedCandidate(before.toString(), before.toString(),
      "Khám Phá", "Cao Minh nhìn tường.", generated.toString())).getBoolean("handled"))
    assertEquals(unchanged, repo.state)
    // Calling the facade directly with arbitrary prose cannot bypass the envelope.
    assertFalse(JSONObject(core.processValidatedCandidate(before.toString(), before.toString(),
      "Khám Phá", "Lucia bước ra.")).getBoolean("handled"))
    assertEquals(unchanged, repo.state)
  }

  @Test fun failureInLaterSceneNeverCommitsEarlierEventsAndRetryKeepsEncounterIdentity() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 5)
    val before = view(core); val due = JSONArray(core.milestoneDueEvents())
    val unchanged = repo.state
    val generated = TestMilestoneTurn.narration(due)
    generated.getJSONArray("eventNarrations").getJSONObject(2).remove("communication")
    assertFalse(JSONObject(core.processValidatedCandidate(before.toString(), before.toString(),
      "Khám Phá", "invalid later scene", generated.toString())).getBoolean("handled"))
    assertEquals(unchanged, repo.state)
    assertEquals(due.toString(), core.milestoneDueEvents())
    val accepted = commit(core)
    assertEquals(due.getJSONObject(0).getString("encounterId"),
      accepted.getJSONObject("pendingEncounter").getString("encounterId"))
    assertEquals(0, accepted.getJSONArray("party").length())
  }

  @Test fun coreRejectsDifferentRenderedProseAndPreservesCommunicationThroughSaveLoad() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 3)
    val before = view(core); val due = JSONArray(core.milestoneDueEvents())
    val generated = TestMilestoneTurn.narration(due)
    val unchanged = repo.state
    assertEquals("milestone_rendered_narrative_mismatch", JSONObject(core.processValidatedCandidate(
      before.toString(), before.toString(), "Khám Phá", "Một lời kể khác.", generated.toString())).getString("error"))
    assertEquals(unchanged, repo.state)
    commit(core)
    val saved = JSONObject(repo.state.world.getValue("story.milestone.eventRecords"))
      .getJSONObject("lucia3").getJSONObject("narration")
    repo.state = GameStateCodec.decode(GameStateCodec.encode(repo.state))
    StoryMilestoneRuntime.validateStoredState(repo.state)
    val restored = JSONObject(repo.state.world.getValue("story.milestone.eventRecords"))
      .getJSONObject("lucia3").getJSONObject("narration")
    assertEquals(saved.toString(), restored.toString())
    assertEquals(LUCIA_ID, restored.getJSONObject("communication").getString("speakerId"))
    assertEquals(4, CoreClientProjection.turnIndex(repo.state))
    assertTrue(StoryMilestoneRuntime.due(repo.state).none { it.id == "lucia3" })
  }

  @Test fun failedStorageWriteCannotApplyNarratedMeetingOrAdvanceTurnAndRetryIsSafe() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 3)
    val before = view(core); val due = JSONArray(core.milestoneDueEvents())
    val generated = TestMilestoneTurn.narration(due)
    val rendered = MilestoneNarrationValidator.validate(generated, due).reply
    val unchanged = repo.state
    repo.failWrites = true
    assertThrows(IllegalStateException::class.java) {
      core.processValidatedCandidate(before.toString(), before.toString(), "Khám Phá", rendered, generated.toString())
    }
    assertEquals(unchanged, repo.state)
    repo.failWrites = false
    commit(core)
    assertEquals(4, CoreClientProjection.turnIndex(repo.state))
    assertEquals(1, JSONObject(repo.state.world.getValue("story.milestone.eventRecords")).length())
  }

  @Test fun staleTurnCannotReplayMeetingAndMissedTurnCannotExecuteLate() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 3)
    val old = view(core); commit(core); val once = repo.state
    assertFalse(JSONObject(TestMilestoneTurn.commit(core, old.toString(), old.toString(), "Khám Phá", "Lời kể")).getBoolean("handled"))
    assertEquals(once, repo.state)
    val missed = CoreClientProjection.advance(TestMilestoneTurn.dispatch(CoreClientProjection.advance(TestMilestoneTurn.dispatch(initial()))))
    assertEquals(3, CoreClientProjection.turnIndex(missed))
    assertThrows(Exception::class.java) { StoryMilestoneRuntime.due(CoreClientProjection.advance(missed)) }
  }

  @Test fun meetingAndRecruitmentAreSeparateAndSaveLoadPreservesBothAndPendingCombat() {
    val repo = MemorySave(initial()); val core = facade(repo); reach(core, repo, 3); val meeting = commit(core)
    val beforeJoin = repo.state
    val joined = JSONObject(core.recruitFollower(meeting.toString(), LUCIA_ID))
    assertTrue(joined.toString(), joined.getBoolean("handled"))
    assertEquals(beforeJoin.turn, repo.state.turn); assertEquals(beforeJoin.time, repo.state.time)
    assertEquals(beforeJoin.characters, repo.state.characters)
    assertEquals(beforeJoin.world["story.milestone.eventRecords"], repo.state.world["story.milestone.eventRecords"])
    val once = repo.state
    assertTrue(JSONObject(core.recruitFollower(joined.getJSONObject("state").toString(), LUCIA_ID)).getBoolean("handled"))
    assertEquals(once, repo.state)
    val duller = commit(core)
    assertEquals("duller", duller.getJSONObject("pendingEncounter").getString("entityKey"))
    finishCombat(repo)
    val pending = commit(core)
    val id = pending.getJSONObject("pendingEncounter").getString("encounterId")
    assertTrue(JSONObject(core.saveManualCheckpoint(pending.toString())).getBoolean("ok"))
    val restored = JSONObject(core.loadManualCheckpoint()).getJSONObject("state")
    assertEquals(listOf(CAO_MINH_ID, LUCIA_ID), repo.state.party.memberIds)
    assertEquals(id, restored.getJSONObject("pendingEncounter").getString("encounterId"))
    val beforeFight = repo.state
    assertTrue(JSONObject(core.confirmEncounter(restored.toString(), id)).getBoolean("handled"))
    assertEquals(id, CombatRuntime.active(repo.state)!!.encounterId)
    assertEquals(beforeFight.turn, repo.state.turn); assertEquals(beforeFight.characters, repo.state.characters)
  }

  @Test fun recruitmentRequiresEncounterConsentAndCapacityWithoutErasingMeeting() {
    val repo = MemorySave(initial()); val core = facade(repo)
    assertFalse(JSONObject(core.recruitFollower(view(core).toString(), LUCIA_ID)).getBoolean("handled"))
    reach(core, repo, 3); commit(core)
    repo.state = repo.state.copy(characters = repo.state.characters + (LUCIA_ID to repo.state.characters.getValue(LUCIA_ID)
      .copy(metadata = repo.state.characters.getValue(LUCIA_ID).metadata + ("followsPlayer" to "false"))))
    assertEquals("join_not_confirmed", JSONObject(core.recruitFollower(view(core).toString(), LUCIA_ID)).getString("error"))
    repo.state = repo.state.copy(characters = repo.state.characters + (LUCIA_ID to repo.state.characters.getValue(LUCIA_ID)
      .copy(metadata = repo.state.characters.getValue(LUCIA_ID).metadata + ("followsPlayer" to "true"))),
      party = repo.state.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, LUC_TRAM_ID)))
    assertEquals("party_full", JSONObject(core.recruitFollower(view(core).toString(), LUCIA_ID)).getString("error"))
    assertEquals("3", repo.state.characters.getValue(LUCIA_ID).metadata["encounterTurn"])
  }
}
