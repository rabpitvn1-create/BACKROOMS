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

  @Test fun v4CoreSaveMigratesWithoutInventingProceduralLevel() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial())).apply {
      put("saveVersion", 4)
      remove("levelInstance")
      getJSONObject("world").put("location", "Level 1 / Parking A")
    }

    val migrated = GameStateCodec.decode(root)

    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
    assertNull(migrated.levelInstance)
    assertEquals("Level 1 / Parking A", migrated.world["location"])
    assertEquals("4", migrated.metadata["migratedFromVersion"])
  }
}
