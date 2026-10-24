package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LootItemCatalogTest {
  @Test fun onlyFiveConsumablesAndNoCanonEquipmentAreEligible() {
    val expected = setOf("com-tam", "banh-tet", "coconut", "liquid-pain", "greek-fire")
    assertEquals(expected, LootItemCatalog.droppable.map { it.id }.toSet())
    assertEquals(5, LootItemCatalog.droppable.size)
    assertTrue(LootItemCatalog.droppable.all { it.consumedOnUse })
    assertTrue(LootItemCatalog.droppable.all { it.healPercent in 1..100 })
    assertTrue(LootItemCatalog.droppable.all { it.cloudinaryPublicId.startsWith("Item_") })
    assertTrue(LootItemCatalog.droppable.none { it.category == "equipment" || it.slotLike() })
  }

  private fun LootItemCatalog.Definition.slotLike(): Boolean =
    this.stack().metadata.containsKey("slot")

  @Test fun allFiveHaveAuthorDefinedMaxHpPercentages() {
    val expected = mapOf("com-tam" to 24, "banh-tet" to 28, "coconut" to 16,
      "liquid-pain" to 8, "greek-fire" to 6)
    assertEquals(expected, LootItemCatalog.droppable.associate { it.id to it.healPercent })
  }

  @Test fun percentageHealingUsesEffectiveMaxHpAndConsumesOneWholeUnit() {
    val base = GameState.initial()
    val target = base.characters.getValue(CAO_MINH_ID)
    val damaged = base.copy(characters = base.characters +
      (CAO_MINH_ID to target.copy(vitalState = target.vitalState.copy(currentHp = 10))))
    val item = requireNotNull(LootItemCatalog.definition("com-tam")).stack()
    val pickup = StateReducer.execute(damaged, ItemCommand(
      "loot-pickup-1", "TURN_1", CAO_MINH_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = item.itemId,
      itemName = item.name, metadata = item.metadata
    ))
    assertTrue(pickup.validation.reason ?: "pickup", pickup.applied)
    val used = StateReducer.execute(pickup.state, ItemCommand(
      "loot-use-1", "TURN_1", CAO_MINH_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.USE, itemId = item.itemId,
      itemName = item.name
    ))
    assertTrue(used.validation.reason ?: "use", used.applied)
    val max = CharacterStatEngine.effective(pickup.state, CAO_MINH_ID).maxHp
    val expected = (10 + ((max.toLong() * 24 + 99) / 100).toInt()).coerceAtMost(max)
    assertEquals(expected, used.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertFalse(used.state.inventories.getValue(CAO_MINH_ID).items.containsKey("com-tam"))
  }

  @Test fun forgedItemMetadataCannotInflateHealing() {
    assertEquals(24, LootItemCatalog.healPercent(ItemStack("com-tam", "Cơm tấm",
      metadata = mapOf("healPercentMaxHp" to "100000"))))
    assertEquals(0, LootItemCatalog.healPercent(ItemStack("unknown", "Cơm tấm",
      metadata = mapOf("healPercentMaxHp" to "100000"))))
  }

  @Test fun individualLootSelectionNeverIncludesCanonEquipment() {
    val excluded = setOf("pink-crocs", "pink-outfit", "military-watch", "combat-knife", "m4a1",
      AN_NHIEN_FOOTWEAR_ID, AN_NHIEN_OUTFIT_ID)
    val poolSize = 4 + LootItemCatalog.droppable.size
    for (i in 0 until poolSize) {
      val roll = CoreLootEngine.roll(GameState.initial(), CoreLootEngine.Source.EXPLORE, "turn:$i") {
        bound -> if (bound == 100) 0 else i
      }
      assertNotNull(roll.dropped)
      assertFalse(roll.dropped!!.itemId in excluded)
    }
  }
}
