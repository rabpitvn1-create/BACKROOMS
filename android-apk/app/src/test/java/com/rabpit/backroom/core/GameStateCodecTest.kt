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
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf("water" to ItemStack("water", "Almond Water", 2)))),
      statuses = mapOf(effect.id to effect),
      characters = mapOf(KAI_ID to CharacterState(KAI_ID, "Cao Minh", statusIds = setOf(effect.id), physiology = physiology)),
      omnivault = OmnivaultState(scanSlots = listOf(ScanSlot(1, "water", ItemStack("water", "Almond Water"), 10)), markedSourceIds = setOf("water")),
      turn = TurnState("TURN_9", PendingTurn("TURN_9", "Cao Minh nhặt nước", PendingTurnStatus.INTERPRETING)),
      time = GameTimeState(elapsedSubjectiveMinutes = 485L, lastAdvanceMinutes = 15, lastAdvanceReason = "travel")
    )
    val canonicalState = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))))
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
