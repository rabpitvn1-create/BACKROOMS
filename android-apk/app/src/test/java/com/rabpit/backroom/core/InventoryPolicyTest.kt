package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryPolicyTest {
  private fun stateWith(vararg characters: CharacterState): GameState {
    val all = listOf(CharacterState(KAI_ID, "Kai Akechi")) + characters
    return GameState.initial().copy(
      characters = all.associateBy { it.id },
      inventories = all.associate { it.id to InventoryState(it.id) },
      equipment = all.associate { it.id to EquipmentState(it.id) }
    )
  }

  @Test fun profilesMatchCharacterRules() {
    val state = stateWith(CharacterState("iris", "Iris"), CharacterState("syvial", "Syvial"), CharacterState("bob", "Bob"))
    assertEquals(InventoryProfile(9, 999), InventoryPolicy.profileFor(state, KAI_ID))
    assertEquals(InventoryProfile(4, 20), InventoryPolicy.profileFor(state, "iris"))
    assertEquals(InventoryProfile(4, 20), InventoryPolicy.profileFor(state, "syvial"))
    assertEquals(InventoryProfile(2, 2), InventoryPolicy.profileFor(state, "bob"))
  }

  @Test fun kaiRejectsTenthTypeAndThousandthItem() {
    val items = (1..9).associate { "i$it" to ItemStack("i$it", "Item $it", 1) }
    val base = stateWith().copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, items)))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(base, KAI_ID, base.inventories.getValue(KAI_ID), ItemStack("i10", "Item 10"), 1))
    val stacked = base.copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf("water" to ItemStack("water", "Water", 999)))))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(stacked, KAI_ID, stacked.inventories.getValue(KAI_ID), ItemStack("water", "Water"), 1))
  }

  @Test fun irisAndNormalFollowerUseSmallerLimits() {
    val iris = CharacterState("iris", "Iris")
    val bob = CharacterState("bob", "Bob")
    var state = stateWith(iris, bob)
    state = state.copy(inventories = state.inventories +
      ("iris" to InventoryState("iris", (1..4).associate { "i$it" to ItemStack("i$it", "I$it", 1) })) +
      ("bob" to InventoryState("bob", mapOf("a" to ItemStack("a", "A", 2), "b" to ItemStack("b", "B", 1)))))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, "iris", state.inventories.getValue("iris"), ItemStack("i5", "I5"), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, "bob", state.inventories.getValue("bob"), ItemStack("a", "A"), 1))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, "bob", state.inventories.getValue("bob"), ItemStack("c", "C"), 1))
  }

  @Test fun equippedKaiSignatureItemCannotBeScanned() {
    val gun = ItemStack("kai-gun", "Kai Gun", 1)
    val state = stateWith().copy(
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(gun.itemId to gun))),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, mapOf("weapon" to gun.itemId)))
    )
    val result = StateReducer.execute(state, OmnivaultCommand("scan", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.SCAN, itemId = gun.itemId, itemName = gun.name))
    assertEquals("signature_equipment_locked", result.validation.reason)
  }
}
