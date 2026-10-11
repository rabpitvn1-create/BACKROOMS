package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaFollowerTest {
  @Test fun luciaUsesCanonical1193aBaseStats() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    val lucia = state.characters.getValue(LUCIA_ID)
    assertEquals(50, lucia.statProfile.baseMaxHp)
    assertEquals(listOf(5,5,5,5), listOf(lucia.statProfile.str,lucia.statProfile.def,lucia.statProfile.skl,lucia.statProfile.vit))
    assertEquals(50, CharacterStatEngine.effective(state, LUCIA_ID).maxHp)
  }

  @Test fun luciaHasExactlyThreeCanonicalEquipmentSlots() {
    val state = GameState.initial()
    val slots = state.equipment.getValue(LUCIA_ID).slots
    assertEquals(3, slots.size)
    assertEquals(LUCIA_M4A1_ID, slots["weapon"])
    assertEquals(LUCIA_KNIFE_ID, slots["blade"])
    assertEquals(LUCIA_WATCH_ID, slots["wrist"])
    slots.values.forEach { id -> assertFalse(state.inventories.getValue(LUCIA_ID).items.containsKey(id)) }
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, LUCIA_ID))
  }

  @Test fun luciaGiftInventoryAllowsThreeTypesAndOneHundredEach() {
    val state = GameState.initial()
    val profile = InventoryPolicy.profileFor(state, LUCIA_ID)
    assertEquals(3, profile.maxTypes)
    assertEquals(100, profile.maxPerType)

    val three = InventoryState(LUCIA_ID, mapOf(
      "a" to ItemStack("a", "A", 100),
      "b" to ItemStack("b", "B", 1),
      "c" to ItemStack("c", "C", 1)
    ))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, three, ItemStack("d", "D", 1), 1))

    val ninetyNine = InventoryState(LUCIA_ID, mapOf("a" to ItemStack("a", "A", 99)))
    assertNull(InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 1), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 2), 2))
  }

  @Test fun luciaStartsOutsidePartyAndKeepsCanonAmmoSeparateFromGiftSlots() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    assertFalse(LUCIA_ID in state.party.memberIds)
    assertEquals("50%", lucia.metadata["encounterChance"])
    assertEquals("0", lucia.metadata["encounterLevels"])
    assertEquals("EXPLORE", lucia.metadata["encounterAction"])
    assertEquals("60", lucia.metadata["startingLoadedAmmo"])
    assertEquals("90", lucia.metadata["startingReserveAmmo"])
    assertEquals("150", lucia.metadata["startingTotalAmmo"])
  }
}
