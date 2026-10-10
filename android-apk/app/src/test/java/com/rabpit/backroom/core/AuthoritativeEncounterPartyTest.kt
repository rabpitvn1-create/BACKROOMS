package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthoritativeEncounterPartyTest {
  private class MemorySave(var state: GameState = CoreClientProjection.initialize(GameState.initial())) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
    private var manual: GameCheckpoint? = null
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
      manual = GameCheckpoint(state, presentation)
    }
    override fun loadCheckpoint(): GameCheckpoint? = manual
  }

  private fun facade(repo: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }

  private fun candidate(state: GameState, rollKey: String? = null): Pair<String, String> {
    val view = CoreClientProjection.project(JSONObject().put("log", JSONArray()), state)
    val next = JSONObject(view.toString())
    if (rollKey != null) {
      val roll = JSONObject().put("turn", CoreClientProjection.turnIndex(state))
        .put("meta", false)
        .put(rollKey, JSONObject().put("eligible", true).put("success", true))
      next.getJSONObject("flags").put("lastRolls", roll)
    }
    return view.toString() to next.toString()
  }

  @Test fun validatedReunionJoinsCorePartyWithoutSynthesizedClientMember() {
    val repo = MemorySave()
    val (before, after) = candidate(repo.state, "irisReunion")
    assertEquals(0, JSONObject(after).getJSONArray("party").length())
    val response = JSONObject(facade(repo).processValidatedCandidate(before, after, "Quan sát hành lang"))
    assertTrue(response.toString(), response.getBoolean("handled"))
    assertEquals(listOf(KAI_ID, IRIS_ID), repo.state.party.memberIds)
    assertEquals(1, response.getJSONObject("state").getJSONArray("party").length())
    assertFalse(JSONObject(repo.state.world.getValue("flagsJson")).getJSONObject("iris").getBoolean("joinPending"))
  }

  @Test fun partyCapacityDefersEncounterThenAutoJoinsAfterSlotOpens() {
    val initial = CoreClientProjection.initialize(GameState.initial())
    val repo = MemorySave(initial.copy(party = initial.party.copy(
      memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID)
    )))
    val facade = facade(repo)
    val (before, fullCandidate) = candidate(repo.state, "anNhienEncounter")
    assertTrue(JSONObject(facade.processValidatedCandidate(before, fullCandidate, "Khảo sát")).getBoolean("handled"))
    assertEquals(4, repo.state.party.memberIds.size)
    val flags = JSONObject(repo.state.world.getValue("flagsJson"))
    assertTrue(flags.getJSONObject("anNhien").getBoolean("joinPending"))
    assertFalse(flags.getJSONObject("anNhien").getBoolean("follower"))

    // The current slot stays pending until Core has capacity, even without another roll.
    repo.state = repo.state.copy(party = repo.state.party.copy(
      memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID)
    ))
    val (again, retry) = candidate(repo.state)
    assertTrue(JSONObject(facade.processValidatedCandidate(again, retry, "Tiếp tục")).getBoolean("handled"))
    assertEquals(4, repo.state.party.memberIds.size)
    assertTrue(AN_NHIEN_ID in repo.state.party.memberIds)
    val restoredFlags = JSONObject(repo.state.world.getValue("flagsJson"))
    assertFalse(restoredFlags.getJSONObject("anNhien").getBoolean("joinPending"))
    assertTrue(restoredFlags.getJSONObject("anNhien").getBoolean("follower"))
  }

  @Test fun stalePriorTurnRollCannotRecruitAbsentPartyMember() {
    val repo = MemorySave(CoreClientProjection.advance(CoreClientProjection.initialize(GameState.initial())))
    val (before, after) = candidate(repo.state)
    JSONObject(after).getJSONObject("flags").put("lastRolls",
      JSONObject().put("turn", 1).put("meta", false)
        .put("luciaEncounter", JSONObject().put("eligible", true).put("success", true)))
    assertTrue(JSONObject(facade(repo).processValidatedCandidate(before, JSONObject(after).toString(), "Đi tiếp")).getBoolean("handled"))
    assertFalse(LUCIA_ID in repo.state.party.memberIds)
  }
}
