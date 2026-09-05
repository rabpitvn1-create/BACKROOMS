package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryV2CapacityContractTest {
  private fun stack(id: String = "battery") = ItemStack(
    itemId = id,
    name = "Battery",
    metadata = mapOf(
      "catalog.definitionId" to id,
      "catalog.stackMode" to "STACK",
      "catalog.maxStack" to "9999",
      "catalog.transferable" to "true",
      "catalog.discardable" to "true"
    )
  )

  @Test fun kaiAllows9999UnitsButRejects10000() {
    val state = GameState.initial()
    val inventory = state.inventories.getValue(KAI_ID)
    val item = stack()
    assertEquals(null, InventoryPolicy.validateAddition(state, KAI_ID, inventory, item, 9999))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, KAI_ID, inventory, item, 10000))
  }

  @Test fun normalCharacterAllows99UnitsButRejects100() {
    val base = GameState.initial()
    val iris = CharacterState("iris", "Iris")
    val state = base.copy(
      characters = base.characters + ("iris" to iris),
      inventories = base.inventories + ("iris" to InventoryState("iris"))
    )
    val inventory = state.inventories.getValue("iris")
    val item = stack()
    assertEquals(null, InventoryPolicy.validateAddition(state, "iris", inventory, item, 99))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, "iris", inventory, item, 100))
  }
}
