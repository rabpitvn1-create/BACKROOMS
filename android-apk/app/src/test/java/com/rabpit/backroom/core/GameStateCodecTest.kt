package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameStateCodecTest {
  @Test fun roundTripPreservesStructuredStateAndPendingTurn() {
    val effect = StatusEffect("s1", "INJURY", "event", "TURN_9", persistent = true)
    val physiology = PhysiologyState(
      minutesSinceFood = 360L,
      minutesSinceWater = 95L,
      minutesAwake = 870L,
      painState = "moderate",
      infectionState = "suspected",
      thermalState = "cold",
      metadata = mapOf("source" to "field_observation")
    )
    val state = GameState.initial().copy(
      inventories = mapOf(CAO_MINH_ID to InventoryState(CAO_MINH_ID, mapOf("water" to ItemStack("water", "Almond Water", 2)))),
      statuses = mapOf(effect.id to effect),
      characters = mapOf(CAO_MINH_ID to CharacterState(CAO_MINH_ID, "Cao Minh", statusIds = setOf(effect.id), physiology = physiology)),
      vanKhoGioiChi = VanKhoGioiChiState(scanSlots = listOf(ScanSlot(1, "water", ItemStack("water", "Almond Water"), 10)), markedSourceIds = setOf("water")),
      turn = TurnState("TURN_9", PendingTurn("TURN_9", "Cao Minh nhặt nước", PendingTurnStatus.INTERPRETING)),
      time = GameTimeState(elapsedSubjectiveMinutes = 485L, lastAdvanceMinutes = 15, lastAdvanceReason = "travel")
    )
    val canonicalState = SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(canonicalState, decoded)
    assertEquals(physiology, decoded.characters.getValue(CAO_MINH_ID).physiology)
  }

  @Test fun freshRunStartsWithKnownSatisfiedPhysiologyBaseline() {
    val physiology = GameState.initial().characters.getValue(CAO_MINH_ID).physiology
    assertEquals(0L, physiology.minutesSinceFood)
    assertEquals(0L, physiology.minutesSinceWater)
    assertEquals(0L, physiology.minutesAwake)
    assertEquals("fresh_run_entry", physiology.metadata["baseline"])
  }

  @Test fun currentSaveWithoutTimeDefaultsToZeroSubjectiveMinutes() {
    val raw = JSONObject(GameStateCodec.encode(GameState.initial())).apply { remove("time") }.toString()
    val decoded = GameStateCodec.decode(raw)
    assertEquals(GameTimeState(), decoded.time)
  }

  @Test fun currentCharacterWithoutPhysiologyDefaultsToUnknownState() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial()))
    root.getJSONObject("characters").getJSONObject(CAO_MINH_ID).remove("physiology")
    val decoded = GameStateCodec.decode(root.toString())
    assertEquals(PhysiologyState(), decoded.characters.getValue(CAO_MINH_ID).physiology)
  }

  @Test fun freshStateKeepsSignatureGearOnlyInEquipment() {
    val state = GameState.initial()
    assertTrue(state.inventories.getValue(CAO_MINH_ID).items.isEmpty())
    assertEquals(CAO_MINH_HUYET_MA_KIEM_ID, state.equipment.getValue(CAO_MINH_ID).slots["weapon"])
    assertEquals(CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, state.equipment.getValue(CAO_MINH_ID).slots["armor"])
    assertEquals(CAO_MINH_VAN_KHO_GIOI_CHI_ID, state.equipment.getValue(CAO_MINH_ID).slots["ring"])
  }

  @Test fun legacyWebViewSaveMigratesWithoutLosingNormalInventoryOrParty() {
    val legacy = """{
      "turn":184,
      "title":"BACKROOMS",
      "location":"Level 0",
      "inventory":[
        {"name":"Huyết Ma Kiếm","quantity":1},
        {"name":"Huyết Ma Chiến Khải","quantity":1},
        {"name":"Vạn Khố Giới Chỉ","quantity":1},
        {"name":"Almond Water","quantity":2,"state":"sealed"}
      ],
      "party":[{"id":"luc-tram","name":"Lục Trầm","avatar":"luc-tram.png"}]
    }"""
    val migrated = GameStateCodec.decode(legacy)
    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
    assertEquals("TURN_184", migrated.turn.currentTurnId)
    assertEquals(GameTimeState(), migrated.time)
    assertEquals(0L, migrated.characters.getValue(CAO_MINH_ID).physiology.minutesSinceFood)
    assertEquals(0L, migrated.characters.getValue(CAO_MINH_ID).physiology.minutesSinceWater)
    assertEquals(0L, migrated.characters.getValue(CAO_MINH_ID).physiology.minutesAwake)
    assertEquals(PhysiologyState(), migrated.characters.getValue("luc-tram").physiology)
    assertEquals(1, migrated.inventories.getValue(CAO_MINH_ID).items.size)
    assertEquals(2, migrated.inventories.getValue(CAO_MINH_ID).items.values.single().quantity)
    assertEquals(CAO_MINH_HUYET_MA_KIEM_ID, migrated.equipment.getValue(CAO_MINH_ID).slots["weapon"])
    assertEquals(CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, migrated.equipment.getValue(CAO_MINH_ID).slots["armor"])
    assertEquals(CAO_MINH_VAN_KHO_GIOI_CHI_ID, migrated.equipment.getValue(CAO_MINH_ID).slots["ring"])
    assertEquals(listOf(CAO_MINH_ID, "luc-tram"), migrated.party.memberIds)
    assertEquals("Level 0", migrated.world["location"])
  }

  @Test fun v2CoreSaveMovesSignatureItemsOutOfInventory() {
    val v2 = JSONObject(GameStateCodec.encode(GameState.initial())).apply {
      put("saveVersion", 2)
      put("inventories", JSONObject().put(CAO_MINH_ID, JSONObject().apply {
        put("ownerId", CAO_MINH_ID)
        put("items", JSONObject().apply {
          put("old-sword", JSONObject().apply {
            put("itemId", "old-sword"); put("name", "Huyết Ma Kiếm"); put("quantity", 1); put("metadata", JSONObject()); put("archetypeId", "old-sword"); put("contentState", "NONE")
          })
          put("rope", JSONObject().apply {
            put("itemId", "rope"); put("name", "Rope"); put("quantity", 1); put("metadata", JSONObject()); put("archetypeId", "rope"); put("contentState", "NONE")
          })
        })
      }))
      put("equipment", JSONObject().put(CAO_MINH_ID, JSONObject().put("ownerId", CAO_MINH_ID).put("slots", JSONObject())))
    }
    val migrated = GameStateCodec.decode(v2)
    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
    assertEquals(0L, migrated.characters.getValue(CAO_MINH_ID).physiology.minutesSinceFood)
    assertEquals(0L, migrated.characters.getValue(CAO_MINH_ID).physiology.minutesSinceWater)
    assertEquals(0L, migrated.characters.getValue(CAO_MINH_ID).physiology.minutesAwake)
    assertEquals(setOf("rope"), migrated.inventories.getValue(CAO_MINH_ID).items.keys)
    assertEquals(CAO_MINH_HUYET_MA_KIEM_ID, migrated.equipment.getValue(CAO_MINH_ID).slots["weapon"])
    assertEquals(CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, migrated.equipment.getValue(CAO_MINH_ID).slots["armor"])
    assertEquals(CAO_MINH_VAN_KHO_GIOI_CHI_ID, migrated.equipment.getValue(CAO_MINH_ID).slots["ring"])
  }
}
