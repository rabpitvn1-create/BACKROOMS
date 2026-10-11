package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LucTramEncounterTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = GameState.initial() }
    private var manual: GameCheckpoint? = null
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { manual = GameCheckpoint(state, presentation) }
    override fun loadCheckpoint(): GameCheckpoint? = manual
  }

  private fun facade(repo: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger)
  }

  private fun atLevel(level: Int): GameState {
    val init = CoreClientProjection.initialize(GameState.initial())
    return init.copy(world = init.world + ("levelJson" to JSONObject()
      .put("number", level).put("id", "$level").put("name", "Level $level").toString()))
  }

  private fun roll(state: GameState, number: Int = 25) =
    JSONObject().put("turn", CoreClientProjection.turnIndex(state)).put("meta", false)
      .put("lucTramEncounter", JSONObject().put("max", 10000).put("threshold", 25)
        .put("eligible", true).put("success", number in 1..25).put("roll", number))

  private fun commit(repo: MemorySave, roll: JSONObject? = null, injectParty: Boolean = false): JSONObject {
    val before = CoreClientProjection.project(JSONObject().put("log", JSONArray()), repo.state)
    val after = JSONObject(before.toString())
    if (roll != null) after.getJSONObject("flags").put("lastRolls", roll)
    if (injectParty) after.put("party", JSONArray().put(JSONObject()
      .put("id", LUC_TRAM_ID).put("name", "Lục Trầm").put("joinConfirmed", true).put("present", true)))
    return JSONObject(facade(repo).processValidatedCandidate(before.toString(), after.toString(), "Khảo sát hành lang"))
  }

  @Test fun registersOutsidePartyWithoutInventingEquipment() {
    val initial = GameState.initial()
    assertTrue(LUC_TRAM_ID in initial.characters)
    assertFalse(LUC_TRAM_ID in initial.party.memberIds)
    assertEquals("avatars/luctram_avatar.png", initial.characters.getValue(LUC_TRAM_ID).avatarRef)
    assertEquals("CAO_MINH_FORGOTTEN", initial.characters.getValue(LUC_TRAM_ID).metadata["postRevivalMemory"])
    assertEquals("0.25%", initial.characters.getValue(LUC_TRAM_ID).metadata["encounterChance"])
    assertTrue(initial.equipment.getValue(LUC_TRAM_ID).slots.isEmpty())
    assertEquals(6, CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).size)
  }

  @Test fun forbiddenLevelsAndCandidatePartySpoofingNeverJoin() {
    for(level in 0..3) {
      val repo = MemorySave(atLevel(level))
      assertFalse(LucTramCanon.validEncounterRoll(repo.state, roll(repo.state)))
      assertTrue(commit(repo, roll(repo.state), injectParty = true).getBoolean("handled"))
      assertFalse(LUC_TRAM_ID in repo.state.party.memberIds)
    }
    val level4 = MemorySave(atLevel(4))
    assertTrue(commit(level4, injectParty = true).getBoolean("handled"))
    assertFalse(LUC_TRAM_ID in level4.state.party.memberIds)
  }

  @Test fun validOnlyAtLevelFourWithExactRollAndNoRepeatAfterJoin() {
    val state = atLevel(4)
    assertTrue(LucTramCanon.validEncounterRoll(state, roll(state, 1)))
    assertTrue(LucTramCanon.validEncounterRoll(state, roll(state, 25)))
    assertFalse(LucTramCanon.validEncounterRoll(state, roll(state, 26)))
    assertFalse(LucTramCanon.validEncounterRoll(state, roll(state, 0)))
    assertFalse(LucTramCanon.validEncounterRoll(state, JSONObject(roll(state).toString()).put("turn", 0)))
    assertFalse(LucTramCanon.validEncounterRoll(state, JSONObject(roll(state).toString()).put("meta", true)))
    val repo = MemorySave(state)
    assertTrue(commit(repo, roll(repo.state)).getBoolean("handled"))
    assertEquals(listOf(CAO_MINH_ID, LUC_TRAM_ID), repo.state.party.memberIds)
    assertFalse(JSONObject(repo.state.world.getValue("flagsJson")).getJSONObject("lucTram").getBoolean("joinPending"))
    assertFalse(LucTramCanon.eligible(repo.state))
  }

  @Test fun partyCapacityPreservesPendingEncounter() {
    val full = atLevel(4).copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID)))
    val repo = MemorySave(full)
    assertTrue(commit(repo, roll(repo.state)).getBoolean("handled"))
    assertFalse(LUC_TRAM_ID in repo.state.party.memberIds)
    assertTrue(JSONObject(repo.state.world.getValue("flagsJson")).getJSONObject("lucTram").getBoolean("joinPending"))
    repo.state = repo.state.copy(party = repo.state.party.copy(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID)))
    assertTrue(commit(repo).getBoolean("handled"))
    assertTrue(LUC_TRAM_ID in repo.state.party.memberIds)
  }
}
