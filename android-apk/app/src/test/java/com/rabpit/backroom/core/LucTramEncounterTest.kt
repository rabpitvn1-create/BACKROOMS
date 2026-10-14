package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Post-Milestone: Lục Trầm encounter tests via Core-authoritative spawn.
 *
 * Lục Trầm: 0.25% on Level 4+ physical actions, once-only.
 * Decided by NpcSpawnTable/SpawnMechanism (Core), narrated by GM.
 */
class LucTramEncounterTest {
  private fun atLevel(level: Int): GameState = CoreClientProjection.initialize(GameState.initial()).let {
    it.copy(world = it.world + ("levelJson" to LevelRouteRegistry.node(level.toString())!!.asJson().toString()))
  }
  /** Deterministic "always succeed" RNG for tests. */
  private fun succeed(bound: Int) = 0

  @Test fun registryMetadataDoesNotSeedUnconfirmedGearOrParty() {
    val state = GameState.initial()
    // encounterPolicy remains write-only metadata (no production path reads it).
    assertEquals("CORE_SPAWN", state.characters.getValue(LUC_TRAM_ID).metadata["encounterPolicy"])
    assertFalse(state.characters.getValue(LUC_TRAM_ID).metadata.containsKey("encounterChance"))
    assertFalse(LUC_TRAM_ID in state.party.memberIds)
    assertFalse(LucTramCanon.hasConfirmedSword(state))
  }

  @Test fun lucTramIneligibleBelowLevelFour() {
    for (level in 0..3) {
      val state = atLevel(level)
      val encounters = NpcSpawnTable.rollNpcs(state, ActionKind.EXECUTE, "đi khám phá", level, ::succeed)
      assertTrue("Level $level should not roll Luc Tram", encounters.none { it.characterId == LUC_TRAM_ID })
    }
  }

  @Test fun lucTramEligibleOnLevelFourPhysical() {
    val state = atLevel(4)
    val encounters = NpcSpawnTable.rollNpcs(state, ActionKind.EXECUTE, "đi khám phá", 4, ::succeed)
    assertTrue(encounters.any { it.characterId == LUC_TRAM_ID && it.flagKey == "lucTram" })
  }

  @Test fun acceptedLevelFourEncounterSetsMetadataWithoutRecruitment() {
    val state = atLevel(4)
    val result = SpawnMechanism.SpawnResult(
      entityKey = null,
      npcEncounters = listOf(NpcSpawnTable.NpcEncounter(LUC_TRAM_ID, "lucTram"))
    )
    val committed = SpawnMechanism.applySpawns(state, result)
    // Core recorded the encounter.
    assertEquals("true", committed.characters.getValue(LUC_TRAM_ID).metadata["encountered"])
    // Not auto-recruited.
    assertFalse(LUC_TRAM_ID in committed.party.memberIds)
    // Once-only: a second roll cannot re-encounter.
    val second = NpcSpawnTable.rollNpcs(committed, ActionKind.EXECUTE, "đi khám phá", 4, ::succeed)
    assertTrue(second.none { it.characterId == LUC_TRAM_ID })
  }

  @Test fun recruitFollowerRequiresCoreEncounterFirst() {
    val repo = object : SaveRepository {
      var state: GameState = atLevel(4)
      override fun save(state: GameState) { this.state = state }
      override fun load() = state
      override fun exists() = true
      override fun clear() { state = GameState.initial() }
      override fun saveCheckpoint(state: GameState, presentation: JSONObject) {}
      override fun loadCheckpoint(): GameCheckpoint? = null
    }
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(repo, NoOpGamePipelineLogger)
    val client = CoreClientProjection.project(JSONObject().put("log", JSONArray()), repo.state)
    // No encounter yet: recruitment rejected.
    val rejected = JSONObject(core.recruitFollower(client.toString(), LUC_TRAM_ID))
    assertFalse(rejected.getBoolean("handled"))
    assertEquals("follower_not_encountered", rejected.getString("error"))
  }
}
