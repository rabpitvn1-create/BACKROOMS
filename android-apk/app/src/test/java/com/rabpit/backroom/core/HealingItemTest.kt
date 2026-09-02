package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class HealingItemTest {
  @Test fun officialHealingCompatibilityUsesFinalStatsAndPool() {
    assertEquals(ItemCatalog.BANDAGE, BANDAGE_ID)
    assertEquals(ItemCatalog.ANTISEPTIC, ANTISEPTIC_ID)
    assertEquals(15, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(10, HealingItems.ANTISEPTIC_HEAL_HP)
    assertEquals("official-item-pool", HealingItems.DROP_ROLL_KEY)
    assertEquals(12, ItemCatalog.items.size)
  }
}
