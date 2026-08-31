package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProceduralSaveMigrationTest {
  @Test fun v5SaveRoundTripPreservesLevelInstance() {
    val state = LevelOneRuntime.install(GameState.initial(), "save-seed")
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
}
