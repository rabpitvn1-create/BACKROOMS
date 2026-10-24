package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryPolicyTest {
  private fun stateWith(vararg characters: CharacterState): GameState {
    val all = listOf(CharacterState(CAO_MINH_ID, "Cao Minh")) + characters
    return GameState.initial().copy(
      characters = all.associateBy { it.id },
      inventories = all.associate { it.id to InventoryState(it.id) },
      equipment = all.associate { it.id to EquipmentState(it.id) }
    )
  }

  @Test fun profilesMatchCharacterRules() {
    val state = stateWith(CharacterState("luc-tram", "Lục Trầm"), CharacterState("syvial", "Syvial"), CharacterState("bob", "Bob"))
    assertEquals(InventoryProfile(9, 999), InventoryPolicy.profileFor(state, CAO_MINH_ID))
    assertEquals(InventoryProfile(4, 20), InventoryPolicy.profileFor(state, "luc-tram"))
    assertEquals(InventoryProfile(4, 20), InventoryPolicy.profileFor(state, "syvial"))
    assertEquals(InventoryProfile(2, 2), InventoryPolicy.profileFor(state, "bob"))
  }

  @Test fun caoMinhRejectsTenthTypeAndThousandthItem() {
    val items = (1..9).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    val base = stateWith().copy(inventories = mapOf(CAO_MINH_ID to InventoryState(CAO_MINH_ID, items)))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(base, CAO_MINH_ID, base.inventories.getValue(CAO_MINH_ID), ItemStack("i10", "Item 10"), 1))
    val stacked = base.copy(inventories = mapOf(CAO_MINH_ID to InventoryState(CAO_MINH_ID, mapOf("water" to ItemStack("water", "Water", 999)))))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(stacked, CAO_MINH_ID, stacked.inventories.getValue(CAO_MINH_ID), ItemStack("water", "Water"), 1))
  }

  @Test fun irisAndNormalFollowerUseSmallerLimits() {
    val lucTram = CharacterState("luc-tram", "Lục Trầm")
    val bob = CharacterState("bob", "Bob")
    var state = stateWith(lucTram, bob)
    state = state.copy(inventories = state.inventories +
      ("luc-tram" to InventoryState("luc-tram", (1..4).associate { "i$it" to ItemStack("i$it", "I$it", 1) })) +
      ("bob" to InventoryState("bob", mapOf("a" to ItemStack("a", "A", 2), "b" to ItemStack("b", "B", 1)))))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, "luc-tram", state.inventories.getValue("luc-tram"), ItemStack("i5", "I5"), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, "bob", state.inventories.getValue("bob"), ItemStack("a", "A"), 1))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, "bob", state.inventories.getValue("bob"), ItemStack("c", "C"), 1))
  }

  @Test fun equippedCaoMinhSignatureItemCannotBeScanned() {
    val sword = ItemStack("cao-minh:huyet-ma-kiem", "Huyết Ma Kiếm", 1)
    val state = stateWith().copy(
      inventories = mapOf(CAO_MINH_ID to InventoryState(CAO_MINH_ID, mapOf(sword.itemId to sword))),
      equipment = mapOf(CAO_MINH_ID to EquipmentState(CAO_MINH_ID, mapOf("weapon" to sword.itemId)))
    )
    val result = StateReducer.execute(state, VanKhoGioiChiCommand("scan", "TURN_1", CAO_MINH_ID, source = CommandSource.RULE, operation = VanKhoGioiChiCommand.Operation.SCAN, itemId = sword.itemId, itemName = sword.name))
    assertEquals("signature_equipment_locked", result.validation.reason)
  }
}
