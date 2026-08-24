package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  @Test fun legacyLowWaterMigratesToOneWholeCanonicalItemWithoutMultiplication() {
    val low = ItemContentRules.normalize(ItemStack(
      "water-bottle:low", "Chai nước còn ít nước", 3,
      metadata = mapOf("contentState" to "LOW", "contentPercent" to "25"),
      archetypeId = "water-bottle", contentState = ContentState.LOW
    ))
    assertEquals(ItemCatalog.ALMOND_WATER, low.itemId)
    assertEquals("Almond Water", low.name)
    assertEquals(3, low.quantity)
    assertEquals(ContentState.NONE, low.contentState)
    assertFalse(low.metadata.containsKey("contentPercent"))
  }

  @Test fun officialConsumablesNeverCreateLowOrEmptyVariants() {
    ItemCatalog.items.filter { it.type == OfficialItemType.CONSUMABLE }.forEach {
      val stack = ItemContentRules.normalize(it.stack())
      assertEquals(ContentState.NONE, stack.contentState)
      assertNull(ItemContentRules.nextAfterUse(stack))
      assertFalse(stack.itemId.contains(":low"))
      assertFalse(stack.itemId.contains(":empty"))
    }
  }

  @Test fun preciseAmountsRemainForbidden() {
    assertTrue(ItemContentRules.hasForbiddenPreciseAmount("Chai nước 200ml"))
  }

  @Test fun unknownLegacyEmptyContainerIsPreservedRatherThanDeleted() {
    val empty = ItemContentRules.normalize(ItemStack("box", "Vỏ hộp rỗng"))
    assertEquals(ContentState.EMPTY, empty.contentState)
    assertEquals(1, empty.quantity)
  }
}
