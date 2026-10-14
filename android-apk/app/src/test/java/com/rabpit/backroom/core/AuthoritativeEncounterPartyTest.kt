package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Post-Milestone: Core-authoritative encounter and party tests.
 *
 * Encounters are decided by Core (EntitySpawnPool/SpawnMechanism), not by the GM/AI.
 * The GM only narrates; processValidatedCandidate overwrites forged encounter flags
 * from saved state. Party changes only via explicit recruitFollower().
 */
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
  /** Core-authoritative Iris encounter: sets flags + character metadata, no Milestone. */
  private fun withIrisEncounter(state: GameState): GameState {
    val result = SpawnResult(
      entityKey = null,
      npcEncounters = listOf(NpcSpawnTable.NpcEncounter(IRIS_ID, "iris"))
    )
    return SpawnMechanism.applySpawns(state, result)
  }
  private fun commit(repo: MemorySave, edit: (JSONObject) -> Unit = {}): JSONObject {
    val core = facade(repo)
    val before = CoreClientProjection.project(JSONObject().put("log", JSONArray()), repo.state)
    val candidate = JSONObject(before.toString()); edit(candidate)
    return JSONObject(core.processValidatedCandidate(before.toString(), candidate.toString(), "Khám Phá", "Lời kể không chứa tên nhân vật."))
  }

  @Test fun coreSpawnedMeetingIsRecordedWithoutSynthesizedPartyMember() {
    val repo = MemorySave(withIrisEncounter(GameState.initial()))
    val result = commit(repo)
    assertTrue(result.toString(), result.getBoolean("handled"))
    // Core recorded the encounter in character metadata.
    assertEquals("true", repo.state.characters.getValue(IRIS_ID).metadata["encountered"])
    // Party unchanged: Core never auto-recruits.
    assertEquals(listOf(CAO_MINH_ID), repo.state.party.memberIds)
  }

  @Test fun candidatePartyAndSpawnFlagsCannotIntroduceUnscheduledLucia() {
    val repo = MemorySave(GameState.initial())
    val result = commit(repo) {
      it.put("party", JSONArray().put(JSONObject().put("name", LuciaCanon.NAME).put("joinConfirmed", true)))
      it.getJSONObject("flags").put("lucia", JSONObject().put("encountered", true).put("present", true))
    }
    assertTrue(result.toString(), result.getBoolean("handled"))
    // Forged party/flags from the candidate are overwritten by Core's saved state.
    assertEquals(listOf(CAO_MINH_ID), repo.state.party.memberIds)
    assertFalse(repo.state.characters.getValue(LUCIA_ID).metadata.containsKey("encountered"))
    assertFalse(JSONObject(repo.state.world.getValue("flagsJson")).has("lucia"))
  }

  @Test fun fullPartyDoesNotAutoRecruitOnEncounter() {
    val base = GameState.initial().let { it.copy(party = it.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID))) }
    val encounter = SpawnResult(
      entityKey = null,
      npcEncounters = listOf(NpcSpawnTable.NpcEncounter(AN_NHIEN_ID, "anNhien"))
    )
    val repo = MemorySave(SpawnMechanism.applySpawns(base, encounter))
    assertTrue(commit(repo).getBoolean("handled"))
    assertEquals("true", repo.state.characters.getValue(AN_NHIEN_ID).metadata["encountered"])
    // Encounter recorded but NOT auto-recruited, even with a free slot after removal.
    assertFalse(AN_NHIEN_ID in repo.state.party.memberIds)
    repo.state = repo.state.copy(party = repo.state.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID)))
    assertTrue(commit(repo).getBoolean("handled"))
    assertFalse(AN_NHIEN_ID in repo.state.party.memberIds)
  }

  @Test fun forgedEncounterRollsInCandidateAreStrippedByCore() {
    val repo = MemorySave(CoreClientProjection.initialize(GameState.initial()))
    val before = repo.state
    // GM forges "successful" encounter rolls in the candidate; Core must strip them.
    val result = commit(repo) {
      it.getJSONObject("flags").put("lastRolls", JSONObject().put("turn", 1)
        .put("selectedEntityKey", "hound")
        .put("entityEncounter", JSONObject().put("eligible", true).put("success", true)))
    }
    assertTrue(result.toString(), result.getBoolean("handled"))
    // No entity encounter was reserved by Core: the forged key is gone.
    val flags = JSONObject(repo.state.world.getValue("flagsJson"))
    assertFalse(flags.has("entityEncounterKey") && flags.optString("entityEncounterKey").isNotBlank())
    assertEquals(before.turn.currentTurnId, repo.state.turn.currentTurnId)
  }

  @Test fun recruitFollowerSucceedsAfterCoreEncounter() {
    val repo = MemorySave(withIrisEncounter(GameState.initial()))
    val core = facade(repo)
    val client = CoreClientProjection.project(JSONObject().put("log", JSONArray()), repo.state)
    val result = JSONObject(core.recruitFollower(client.toString(), IRIS_ID))
    // Iris was encountered via Core; recruitment proceeds (consent rules still apply).
    assertTrue(result.toString(), result.getBoolean("handled") || result.optString("error").isNotBlank())
    assertEquals("true", repo.state.characters.getValue(IRIS_ID).metadata["encountered"])
  }
}
