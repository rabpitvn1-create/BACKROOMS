package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFollowerInventoryPolicyTest {
  @Test fun irisAndSyvialUseElevenTypesAndTwentyPerType() {
    val state = GameState.initial()
    for (id in listOf(IRIS_ID, SYVIAL_ID)) {
      val profile = InventoryPolicy.profileFor(state, id)
      assertEquals(11, profile.maxTypes)
      assertEquals(20, profile.maxPerType)
    }
  }

  @Test fun twelfthItemTypeIsRejectedForBothSpecialFollowers() {
    val state = GameState.initial()
    val elevenItems = (1..11).associate { index ->
      val id = "item-$index"
      id to ItemStack(id, "Item $index", 1)
    }
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(ownerId, elevenItems)
      val error = InventoryPolicy.validateAddition(
        state,
        ownerId,
        inventory,
        ItemStack("item-12", "Item 12", 1),
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
        mapOf(ItemCatalog.ALMOND_WATER to ItemStack(ItemCatalog.ALMOND_WATER, "Almond Water", 19))
      )
      assertNull(
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack(ItemCatalog.ALMOND_WATER, "Almond Water", 1),
          1
        )
      )
      assertEquals(
        "inventory_stack_limit",
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack(ItemCatalog.ALMOND_WATER, "Almond Water", 2),
          2
        )
      )
    }
  }
}
