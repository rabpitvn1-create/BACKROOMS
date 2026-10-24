package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Only the current save schema is contractual; old-save migration is not. */
class GameStateCodecTest {
  @Test fun roundTripPreservesCurrentStatePendingTurnAndPhysiology() {
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
    val initial = GameState.initial()
    val original = initial.copy(
      inventories = initial.inventories + (CAO_MINH_ID to initial.inventories.getValue(CAO_MINH_ID).copy(
        items = initial.inventories.getValue(CAO_MINH_ID).items +
          ("water" to ItemStack("water", "Almond Water", 2))
      )),
      statuses = mapOf(effect.id to effect),
      characters = initial.characters + (CAO_MINH_ID to initial.characters.getValue(CAO_MINH_ID).copy(
        statusIds = setOf(effect.id), physiology = physiology
      )),
      vanKhoGioiChi = VanKhoGioiChiState(
        scanSlots = listOf(ScanSlot(1, "water", ItemStack("water", "Almond Water"), 10)),
        markedSourceIds = setOf("water")
      ),
      turn = TurnState("TURN_9", PendingTurn("TURN_9", "Cao Minh nhặt nước", PendingTurnStatus.INTERPRETING)),
      time = GameTimeState(elapsedSubjectiveMinutes = 485L, lastAdvanceMinutes = 15, lastAdvanceReason = "travel")
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(original))
    assertEquals(physiology, decoded.characters.getValue(CAO_MINH_ID).physiology)
    assertEquals(effect, decoded.statuses.getValue("s1"))
    assertTrue("s1" in decoded.characters.getValue(CAO_MINH_ID).statusIds)
    assertEquals(original.turn, decoded.turn)
    assertEquals(original.time, decoded.time)
    assertEquals(2, decoded.inventories.getValue(CAO_MINH_ID).items.getValue("water").quantity)
    assertEquals(1, decoded.vanKhoGioiChi.scanSlots.size)

    // A normalized current save must be stable on subsequent save/load cycles.
    val reloaded = GameStateCodec.decode(GameStateCodec.encode(decoded))
    assertEquals(decoded, reloaded)
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

  @Test fun equippedSignatureItemsAreOwnedButUseNoBackpackSlots() {
    val state = GameState.initial()
    val equipped = state.equipment.getValue(CAO_MINH_ID).slots
    assertEquals(CAO_MINH_HUYET_MA_KIEM_ID, equipped["weapon"])
    assertEquals(CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, equipped["armor"])
    assertEquals(CAO_MINH_VAN_KHO_GIOI_CHI_ID, equipped["ring"])
    equipped.values.forEach { assertTrue(it in state.inventories.getValue(CAO_MINH_ID).items) }
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, CAO_MINH_ID))
  }

  @Test fun currentSaveRetainsInventoryPartyAndWorldWithoutDiscardingEquippedItems() {
    val initial = GameState.initial()
    val inventory = initial.inventories.getValue(CAO_MINH_ID).copy(
      items = initial.inventories.getValue(CAO_MINH_ID).items +
        ("rope" to ItemStack("rope", "Rope", 3))
    )
    val state = initial.copy(
      inventories = initial.inventories + (CAO_MINH_ID to inventory),
      party = initial.party.copy(memberIds = listOf(CAO_MINH_ID, LUC_TRAM_ID)),
      world = mapOf("location" to "Level 0", "title" to "The Lobby"),
      turn = TurnState("TURN_184")
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(CURRENT_SAVE_VERSION, decoded.saveVersion)
    assertEquals("TURN_184", decoded.turn.currentTurnId)
    assertEquals(listOf(CAO_MINH_ID, LUC_TRAM_ID), decoded.party.memberIds)
    assertEquals("Level 0", decoded.world["location"])
    assertEquals("The Lobby", decoded.world["title"])
    assertEquals(3, decoded.inventories.getValue(CAO_MINH_ID).items.getValue("rope").quantity)
    assertEquals(state.equipment.getValue(CAO_MINH_ID).slots, decoded.equipment.getValue(CAO_MINH_ID).slots)
    assertTrue(CAO_MINH_HUYET_MA_KIEM_ID in decoded.inventories.getValue(CAO_MINH_ID).items)
  }

  @Test fun currentSaveNeverHealsWoundedCharacterDuringDecode() {
    val wounded = CharacterStatEngine.setCurrentHp(GameState.initial(), CAO_MINH_ID, 31)
    val decoded = GameStateCodec.decode(GameStateCodec.encode(wounded))
    assertEquals(31, decoded.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(CharacterStatEngine.effective(wounded, CAO_MINH_ID).maxHp,
      CharacterStatEngine.effective(decoded, CAO_MINH_ID).maxHp)
  }
}
