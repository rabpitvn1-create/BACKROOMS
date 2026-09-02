package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaFollowerTest {
  @Test fun luciaUsesHundredHpAndAllRequestedStatsAreAtMostTen() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    val profile = lucia.statProfile
    assertEquals(100, profile.baseMaxHp)
    assertEquals(100, lucia.vitalState.currentHp)
    assertTrue(profile.str in 0..10)
    assertTrue(profile.df in 0..10)
    assertTrue(profile.agi in 0..10)
    assertTrue(profile.crit in 0..10)
    assertEquals(listOf(7, 7, 8, 7), listOf(profile.str, profile.df, profile.agi, profile.crit))
  }

  @Test fun luciaHasExactlyThreeCanonicalEquipmentSlots() {
    val state = GameState.initial()
    val slots = state.equipment.getValue(LUCIA_ID).slots
    assertEquals(3, slots.size)
    assertEquals(LUCIA_M4A1_ID, slots["weapon"])
    assertEquals(LUCIA_KNIFE_ID, slots["blade"])
    assertEquals(LUCIA_WATCH_ID, slots["wrist"])
    slots.values.forEach { id -> assertTrue(state.inventories.getValue(LUCIA_ID).items.containsKey(id)) }
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, LUCIA_ID))
  }

  @Test fun luciaGiftInventoryAllowsEightTypesAndOneHundredEach() {
    val state = GameState.initial()
    val profile = InventoryPolicy.profileFor(state, LUCIA_ID)
    assertEquals(8, profile.maxTypes)
    assertEquals(100, profile.maxPerType)

    val eight = InventoryState(LUCIA_ID, (listOf("a", "b", "c", "d", "e", "f", "g", "h")).associate { id ->
      id to ItemStack(id, id.uppercase(), if (id == "a") 100 else 1)
    })
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, eight, ItemStack("i", "I", 1), 1))

    val ninetyNine = InventoryState(LUCIA_ID, mapOf("a" to ItemStack("a", "A", 99)))
    assertNull(InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 1), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 2), 2))
  }

  @Test fun luciaStartsOutsidePartyAndKeepsCanonAmmoSeparateFromGiftSlots() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    assertFalse(LUCIA_ID in state.party.memberIds)
    assertEquals("0%", lucia.metadata["encounterChance"])
    assertEquals("0", lucia.metadata["encounterLevels"])
    assertEquals("STORY", lucia.metadata["encounterAction"])
    assertEquals(CharacterPresence.MISSING, lucia.presence)
    assertEquals("0", lucia.metadata["fixedEncounterLevel"])
    assertEquals("false", lucia.metadata["requiresQuest"])
    assertEquals("false", lucia.metadata["randomSpawn"])
    assertEquals("60", lucia.metadata["startingLoadedAmmo"])
    assertEquals("90", lucia.metadata["startingReserveAmmo"])
    assertEquals("150", lucia.metadata["startingTotalAmmo"])
  }
}
