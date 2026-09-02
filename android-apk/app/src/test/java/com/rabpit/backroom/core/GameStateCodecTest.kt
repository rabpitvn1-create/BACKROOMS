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
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(ItemCatalog.ALMOND_WATER to ItemCatalog.stack(ItemCatalog.ALMOND_WATER)!!.copy(quantity = 2)))),
      statuses = mapOf(effect.id to effect),
      characters = mapOf(KAI_ID to CharacterState(KAI_ID, "Kai Akechi", statusIds = setOf(effect.id), physiology = physiology)),
      omnivault = OmnivaultState(scanSlots = listOf(ScanSlot(1, ItemCatalog.ALMOND_WATER, ItemCatalog.stack(ItemCatalog.ALMOND_WATER)!!, 10)), markedSourceIds = setOf(ItemCatalog.ALMOND_WATER)),
      turn = TurnState("TURN_9", PendingTurn("TURN_9", "Kai nhặt nước", PendingTurnStatus.INTERPRETING)),
      time = GameTimeState(elapsedSubjectiveMinutes = 485L, lastAdvanceMinutes = 15, lastAdvanceReason = "travel")
    )
    val canonicalState = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state)))
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(canonicalState, decoded)
    assertEquals(physiology, decoded.characters.getValue(KAI_ID).physiology)
  }

  @Test fun freshRunStartsWithKnownSatisfiedPhysiologyBaseline() {
    val physiology = GameState.initial().characters.getValue(KAI_ID).physiology
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
    root.getJSONObject("characters").getJSONObject(KAI_ID).remove("physiology")
    val decoded = GameStateCodec.decode(root.toString())
    assertEquals(PhysiologyState(), decoded.characters.getValue(KAI_ID).physiology)
  }

  @Test fun freshStateInventoryOwnsSignatureGearReferencedByEquipment() {
    val state = GameState.initial()
    val owned = state.inventories.getValue(KAI_ID).items
    state.equipment.getValue(KAI_ID).slots.values.distinct().forEach { assertTrue(it in owned) }
    assertEquals(KAI_WHITE_WRAITH_ID, state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, state.equipment.getValue(KAI_ID).slots["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, state.equipment.getValue(KAI_ID).slots["ring"])
  }

  @Test fun legacyWebViewSaveIsRejectedInsteadOfMigrated() {
    val legacy = """{
      "turn":184,
      "title":"BACKROOMS",
      "location":"Level 0",
      "inventory":[
        {"name":"White Wraith Magnum","quantity":1},
        {"name":"Blackblood Armor & linked modules","quantity":1},
        {"name":"Omnivault Ring / Nhẫn Vạn Tàng","quantity":1},
        {"name":"Almond Water","quantity":2,"state":"sealed"}
      ],
      "party":[{"id":"iris","name":"Iris","avatar":"iris.png"}]
    }"""
    assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(legacy) }
  }

  @Test fun v2CoreSaveIsRejectedInsteadOfMigrated() {
    val v2 = JSONObject(GameStateCodec.encode(GameState.initial())).apply {
      put("saveVersion", 2)
      put("inventories", JSONObject().put(KAI_ID, JSONObject().apply {
        put("ownerId", KAI_ID)
        put("items", JSONObject().apply {
          put("old-gun", JSONObject().apply {
            put("itemId", "old-gun"); put("name", "White Wraith Magnum"); put("quantity", 1); put("metadata", JSONObject()); put("archetypeId", "old-gun"); put("contentState", "NONE")
          })
          put("rope", JSONObject().apply {
            put("itemId", "rope"); put("name", "Rope"); put("quantity", 1); put("metadata", JSONObject()); put("archetypeId", "rope"); put("contentState", "NONE")
          })
        })
      }))
      put("equipment", JSONObject().put(KAI_ID, JSONObject().put("ownerId", KAI_ID).put("slots", JSONObject())))
    }
    assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(v2) }
  }

  @Test fun currentLegacyLowStackLoadsAsCanonicalWholeUnitsWithoutDuplication() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial()))
    root.getJSONObject("inventories").getJSONObject(KAI_ID).getJSONObject("items").put(
      "water-bottle:low",
      JSONObject().apply {
        put("itemId", "water-bottle:low"); put("name", "Chai nước còn ít nước"); put("quantity", 2)
        put("metadata", JSONObject().put("contentState", "LOW").put("contentPercent", "25"))
        put("archetypeId", "water-bottle"); put("contentState", "LOW")
      }
    )
    val migrated = GameStateCodec.decode(root.toString()).inventories.getValue(KAI_ID).items
    assertEquals(setOf(ItemCatalog.ALMOND_WATER), migrated.filterKeys { it in ItemCatalog.ids }.keys)
    assertEquals(2, migrated.getValue(ItemCatalog.ALMOND_WATER).quantity)
    assertEquals(ContentState.NONE, migrated.getValue(ItemCatalog.ALMOND_WATER).contentState)
  }
}
