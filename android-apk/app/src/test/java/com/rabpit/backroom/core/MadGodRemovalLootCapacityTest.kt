package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class MadGodRemovalLootCapacityTest {
  @Test fun everyInventoryProfileHasFiveAdditionalTypeSlots() {
    var state = LuciaCanon.ensure(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial())))
    assertEquals(14, ItemSystem.capacityFor(state, KAI_ID).maxTypes)
    assertEquals(11, ItemSystem.capacityFor(state, IRIS_ID).maxTypes)
    assertEquals(11, ItemSystem.capacityFor(state, SYVIAL_ID).maxTypes)
    assertEquals(8, ItemSystem.capacityFor(state, LUCIA_ID).maxTypes)
    assertEquals(7, ItemSystem.capacityFor(state, AN_NHIEN_ID).maxTypes)

    val future = CharacterState(id = "future", name = "Future")
    state = state.copy(characters = state.characters + (future.id to future))
    assertEquals(7, ItemSystem.capacityFor(state, future.id).maxTypes)
  }

  @Test fun lootGetsFlatFivePointBonusWithoutRemovingPity() {
    val state = GameState.initial().copy(world = GameState.initial().world + ("levelJson" to "{\"number\":0}"))
    assertEquals(100, EntityLootEngine.dropChancePercent(state))
    val prepared = LevelLootEngine.prepareAction(state, "FLAT-LOOT-5", ActionKind.SEARCH, "Level 0")
    val preview = requireNotNull(LevelLootEngine.preparedPreview(prepared))
    assertEquals(35, preview.baseThreshold)
    assertEquals(1, preview.pityTurn)
    assertEquals(635, preview.threshold)
  }

  @Test fun retiredMadGodCannotTriggerAndOldStateIsPurged() {
    assertFalse(MadGodCanon.cheat("/madgod"))
    assertNull(EquipmentCatalog.definition("madgod:set"))

    val base = GameState.initial()
    val kaiInventory = base.inventories.getValue(KAI_ID)
    val kaiEquipment = base.equipment.getValue(KAI_ID)
    val legacyItem = ItemStack("madgod:set", "MadGod Set", 1, metadata = mapOf("madGod" to "true"))
    val contaminated = base.copy(
      inventories = base.inventories + (KAI_ID to kaiInventory.copy(items = kaiInventory.items + (legacyItem.itemId to legacyItem))),
      equipment = base.equipment + (KAI_ID to kaiEquipment.copy(slots = kaiEquipment.slots + mapOf("weapon" to legacyItem.itemId, "armor" to legacyItem.itemId))),
      metadata = base.metadata + ("madGod.spawned" to "true")
    )
    val cleaned = CharacterEquipmentSystem.normalize(contaminated)
    assertFalse(cleaned.inventories.getValue(KAI_ID).items.keys.any { it.startsWith("madgod:") })
    assertFalse(cleaned.equipment.getValue(KAI_ID).slots.values.any { it.startsWith("madgod:") })
    assertFalse(cleaned.metadata.keys.any { it.startsWith("madgod", ignoreCase = true) })
  }
}
