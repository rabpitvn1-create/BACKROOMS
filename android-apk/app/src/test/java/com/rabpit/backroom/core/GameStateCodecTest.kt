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
      turn = TurnState("TURN_9", PendingTurn("TURN_9", "Cao Minh nhặt nước", PendingTurnStatus.INTERPRETING)),
      time = GameTimeState(elapsedSubjectiveMinutes = 485L, lastAdvanceMinutes = 15, lastAdvanceReason = "travel")
    )
    val canonicalState = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))))
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

  @Test fun freshStateInventoryOwnsSignatureGearReferencedByEquipment() {
    val state = GameState.initial()
    val owned = state.inventories.getValue(CAO_MINH_ID).items
    state.equipment.getValue(CAO_MINH_ID).slots.values.distinct().forEach { assertFalse(it in owned) }
    assertEquals(CAO_MINH_SWORD_ID, state.equipment.getValue(CAO_MINH_ID).slots["weapon"])
    assertEquals(CAO_MINH_ARMOR_ID, state.equipment.getValue(CAO_MINH_ID).slots["armor"])
  }

  @Test fun olderSaveSchemasAreRejected() {
    val old = JSONObject(GameStateCodec.encode(GameState.initial()))
    old.put("saveVersion", CURRENT_SAVE_VERSION - 1)
    try {
      GameStateCodec.decode(old)
      org.junit.Assert.fail("Old saves must not migrate")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("Unsupported save schema"))
    }
  }
}
