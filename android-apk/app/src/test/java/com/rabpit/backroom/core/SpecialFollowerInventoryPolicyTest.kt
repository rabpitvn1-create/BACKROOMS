package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFollowerInventoryPolicyTest {
  @Test fun irisAndSyvialUseSixTypesAndTwentyPerType() {
    val state = GameState.initial()
    for (id in listOf(IRIS_ID, SYVIAL_ID)) {
      val profile = InventoryPolicy.profileFor(state, id)
      assertEquals(6, profile.maxTypes)
      assertEquals(20, profile.maxPerType)
    }
  }

  @Test fun seventhItemTypeIsRejectedForBothSpecialFollowers() {
    val state = GameState.initial()
    val sixItems = (1..6).associate { index ->
      val id = "item-$index"
      id to ItemStack(id, "Item $index", 1)
    }
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(ownerId, sixItems)
      val error = InventoryPolicy.validateAddition(
        state,
        ownerId,
        inventory,
        ItemStack("item-7", "Item 7", 1),
        1
      )
      assertEquals("inventory_slot_limit", error)
    }
  }

  @Test fun eachExistingTypeCanReachTwentyButNotTwentyOne() {
    val state = GameState.initial()
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(
        ownerId,
        mapOf("water" to ItemStack("water", "Almond Water", 19))
      )
      assertNull(
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack("water", "Almond Water", 1),
          1
        )
      )
      assertEquals(
        "inventory_stack_limit",
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack("water", "Almond Water", 2),
          2
        )
      )
    }
  }
}
