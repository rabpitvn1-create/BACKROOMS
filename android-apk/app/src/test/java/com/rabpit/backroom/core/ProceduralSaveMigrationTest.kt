package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProceduralSaveMigrationTest {
  @Test fun v5SaveRoundTripPreservesLevelInstance() {
    val registry = LevelRegistry.from(listOf(saveFixtureDefinition()))
    val state = GenericLevelRuntime.install(GameState.initial(), registry, "1", "save-seed")
    val restored = GameStateCodec.decode(GameStateCodec.encode(state))

    assertEquals(CURRENT_SAVE_VERSION, restored.saveVersion)
    assertEquals(state.levelInstance, restored.levelInstance)
    assertEquals(state.world["worldRevision"], restored.world["worldRevision"])
  }

  @Test fun v4CoreSaveNormalizesWithoutInventingProceduralLevel() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial())).apply {
      put("saveVersion", 4)
      remove("levelInstance")
      getJSONObject("world").put("location", "Level 1 / Parking A")
    }

    val normalized = requireNotNull(SaveCompatibility.normalize(root.toString()))
    val normalizedJson = JSONObject(normalized)
    val migrated = GameStateCodec.decode(normalized)

    assertEquals(CURRENT_SAVE_VERSION, normalizedJson.optInt("saveVersion"))
    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
    assertNull(migrated.levelInstance)
    assertEquals("Level 1 / Parking A", migrated.world["location"])
    assertEquals("4", migrated.metadata["migratedFromVersion"])
  }

  @Test fun unsupportedSaveVersionIsRejectedBeforeCodecDecode() {
    val raw = JSONObject(GameStateCodec.encode(GameState.initial())).apply { put("saveVersion", 3) }.toString()
    assertNull(SaveCompatibility.normalize(raw))
  }

  private fun saveFixtureDefinition(): LevelDefinition = LevelDefinition(
    id = "1",
    name = "Save Fixture",
    initialZoneId = "entry",
    zones = mapOf(
      "entry" to ZoneState("entry", "Entry", setOf("exit")),
      "exit" to ZoneState("exit", "Exit", emptySet(), setOf("escape"))
    ),
    escapeBlueprint = EscapeBlueprintState(
      solutionId = "save-fixture",
      requiredFacts = setOf("EXIT_FACT"),
      requiredActions = listOf("enter_exit"),
      locked = true
    ),
    evidence = mapOf(
      "e-search" to EvidenceState("e-search", setOf("EXIT_FACT"), setOf(EvidenceSource.SEARCH), "entry"),
      "e-environment" to EvidenceState("e-environment", setOf("EXIT_FACT"), setOf(EvidenceSource.ENVIRONMENT), "entry")
    ),
    exploreRoute = listOf("exit"),
    actions = mapOf(
      "enter_exit" to LevelActionRule(
        id = "enter_exit",
        matchGroups = listOf(setOf("vào")),
        conditions = setOf("zone:exit"),
        effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
      )
    )
  )
}
