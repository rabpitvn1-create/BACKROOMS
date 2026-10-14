package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthoritativeEncounterPartyTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
    var manual: String? = null
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { manual = GameCheckpointCodec.encode(state, presentation) }
    override fun loadCheckpoint() = manual?.let(GameCheckpointCodec::decode)
  }
  private fun facade(repo: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }
  private fun meeting(id: String, characterId: String) = JSONObject().put("id", id).put("turn", 1)
    .put("kind", "FOLLOWER").put("characterId", characterId).put("action", "Bước ra")
    .put("event", "Gặp Cao Minh").put("outcome", "Đã gặp, chưa tuyển mộ")
  private fun commit(repo: MemorySave, edit: (JSONObject) -> Unit = {}): JSONObject {
    val before = CoreClientProjection.project(JSONObject().put("log", JSONArray()), repo.state)
    val candidate = JSONObject(before.toString()); edit(candidate)
    return JSONObject(TestMilestoneTurn.commit(facade(repo), before.toString(), candidate.toString(), "Khám Phá", "Lời kể không chứa tên nhân vật."))
  }
  @Test fun scheduledMeetingIsRecordedWithoutSynthesizedPartyMemberOrRandomSuccess() {
    val repo = MemorySave(TestCheckpointQuota.withPlan(GameState.initial(), listOf(meeting("iris", IRIS_ID))))
    val result = commit(repo) { it.getJSONObject("flags").put("lastRolls", JSONObject().put("irisReunion", JSONObject().put("success", false))) }
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals("true", repo.state.characters.getValue(IRIS_ID).metadata["encountered"])
    assertEquals(listOf(CAO_MINH_ID), repo.state.party.memberIds)
    assertEquals(IRIS_ID, result.getJSONObject("state").getJSONArray("encounteredCharacters").getJSONObject(0).getString("id"))
  }
  @Test fun candidatePartyAndSpawnFlagsCannotIntroduceUnscheduledLucia() {
    val repo = MemorySave(TestCheckpointQuota.withPlan(GameState.initial()))
    val result = commit(repo) {
      it.put("party", JSONArray().put(JSONObject().put("name", LuciaCanon.NAME).put("joinConfirmed", true)))
      it.getJSONObject("flags").put("lucia", JSONObject().put("encountered", true).put("present", true))
        .put("lastRolls", JSONObject().put("turn", 1).put("luciaEncounter", JSONObject().put("eligible", true).put("success", true)))
    }
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals(listOf(CAO_MINH_ID), repo.state.party.memberIds)
    assertFalse(repo.state.characters.getValue(LUCIA_ID).metadata.containsKey("encountered"))
    assertFalse(JSONObject(repo.state.world.getValue("flagsJson")).has("lucia"))
  }
  @Test fun fullPartyNeverCancelsMeetingAndOpeningASlotDoesNotAutoRecruit() {
    val base = GameState.initial().let { it.copy(party = it.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID))) }
    val repo = MemorySave(TestCheckpointQuota.withPlan(base, listOf(meeting("anNhien", AN_NHIEN_ID))))
    assertTrue(commit(repo).getBoolean("handled"))
    assertEquals("true", repo.state.characters.getValue(AN_NHIEN_ID).metadata["encountered"])
    assertFalse(AN_NHIEN_ID in repo.state.party.memberIds)
    repo.state = repo.state.copy(party = repo.state.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID)))
    assertTrue(commit(repo).getBoolean("handled"))
    assertFalse(AN_NHIEN_ID in repo.state.party.memberIds)
  }
  @Test fun successfulForgedRollWithoutMilestoneCannotStartExplorerTurnOrEncounter() {
    val repo = MemorySave(CoreClientProjection.initialize(GameState.initial()))
    val before = repo.state
    val result = commit(repo) { it.getJSONObject("flags").put("lastRolls", JSONObject().put("turn", 1)
      .put("selectedEntityKey", "hound").put("entityEncounter", JSONObject().put("eligible", true).put("success", true))) }
    assertFalse(result.getBoolean("handled"))
    assertEquals("milestone_plan_required", result.getString("error"))
    assertEquals(before, repo.state)
    assertFalse(JSONObject(facade(repo).beginAction(CoreClientProjection.project(JSONObject(), before).toString(), "EXPLORE", "Khám Phá")).getBoolean("handled"))
  }
}
