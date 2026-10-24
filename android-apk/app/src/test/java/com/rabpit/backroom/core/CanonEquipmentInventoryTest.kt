package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CanonEquipmentInventoryTest {
  private val baseRoster = listOf(
    CAO_MINH_ID, AN_NHIEN_ID, LUC_TRAM_ID, SYVIAL_ID,
    NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID
  )

  @Test fun allIntegratedCharactersOwnTheirRegisteredCanonLoadoutsWithoutInventoryCost() {
    val state = GameState.initial()
    for (id in baseRoster) {
      val loadout = EquipmentCatalog.startingLoadout(id)
      assertTrue("Missing Canon loadout: $id", loadout.isNotEmpty())
      val held = state.inventories[id]?.items.orEmpty()
      val equipped = state.equipment[id]?.slots.orEmpty()
      for ((slot, itemId) in loadout) {
        assertEquals("$id/$slot not equipped", itemId, equipped[slot.key])
        assertTrue("$id does not own $itemId", itemId in held)
        assertEquals(ItemClassification.CANONICAL, EquipmentCatalog.definition(itemId)?.classification)
        assertTrue(InventoryCapacityPolicy.isOwnerCanon(id, itemId))
        assertFalse(InventoryCapacityPolicy.consumesSlot(state, id, itemId))
      }
      assertEquals("$id starting Canon equipment consumed backpack space",
        0, InventoryCapacityPolicy.usedSlots(state, id))
      val character = requireNotNull(CharacterDetailProjector.projectCharacter(state, id))
      assertTrue("$id should not show Canon as carried items", character.inventory.isEmpty())
      assertEquals(loadout.values.toSet(),
        character.equipmentDetails.map { it.id }.toSet())
      assertTrue(character.equipmentDetails.all { it.consumesInventorySlot == false })
      val ui = CharacterDetailJson.encodeCharacter(character)
      assertEquals(0, ui.getJSONArray("inventory").length())
      val uiEquipment = ui.getJSONArray("equipmentItems")
      assertEquals(loadout.values.toSet().size, uiEquipment.length())
      for (index in 0 until uiEquipment.length()) {
        assertFalse(uiEquipment.getJSONObject(index).getBoolean("consumesInventorySlot"))
      }
    }
  }

  @Test fun unequippedCanonStillBelongsInEquipmentNotInventory() {
    val state = GameState.initial()
    val id = CAO_MINH_HUYET_MA_KIEM_ID
    val result = EquipmentEngine.unequip(state, ItemCommand(
      commandId = "unequip-canon", turnId = "TURN_1",
      actorId = CAO_MINH_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.UNEQUIP,
      itemId = id, itemName = "Huyết Ma Kiếm", slot = "weapon"
    ))
    assertTrue(result.validation.reason ?: "unequip", result.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(result.state, CAO_MINH_ID))
    val projected = requireNotNull(CharacterDetailProjector.projectCharacter(result.state, CAO_MINH_ID))
    assertFalse(projected.inventory.any { it.itemId == id })
    val view = projected.equipmentDetails.single { it.id == id }
    assertFalse(view.equipped)
    assertEquals(false, view.consumesInventorySlot)
    assertEquals(id, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(id).itemId)
  }

  @Test fun unrelatedInventoryRemainsCapacityLimitedAndCannotClaimOtherOwnerExemption() {
    val state = GameState.initial()
    assertFalse(InventoryCapacityPolicy.isOwnerCanon(CAO_MINH_ID, "nam:corridor-pistol"))
    val outsider = ItemStack("nam:corridor-pistol", "Hành Lang 01")
    val inv = state.inventories.getValue(CAO_MINH_ID)
      .copy(items = state.inventories.getValue(CAO_MINH_ID).items + (outsider.itemId to outsider))
    assertEquals(1, InventoryCapacityPolicy.usedSlots(state, CAO_MINH_ID, inv))

    val carried = ItemStack("normal-consumable", "Lương khô", 1)
    val withCarried = inv.copy(items = inv.items + (carried.itemId to carried))
    assertEquals(2, InventoryCapacityPolicy.usedSlots(state, CAO_MINH_ID, withCarried))
  }

  @Test fun caoMinhHuyetMaModulesUseExactSlotsAndCloudinaryMapping() {
    val state = GameState.initial()
    val loadout = EquipmentCatalog.startingLoadout(CAO_MINH_ID)
    assertEquals(5, loadout.size)
    val gauntlets = requireNotNull(EquipmentCatalog.definition(CAO_MINH_HUYET_MA_GAUNTLETS_ID))
    val boots = requireNotNull(EquipmentCatalog.definition(CAO_MINH_HUYET_MA_BOOTS_ID))
    assertEquals(EquipmentSlot.GAUNTLETS, gauntlets.primarySlot)
    assertEquals(EquipmentSlot.GREAVES, boots.primarySlot)
    assertEquals("Găng Huyết Ma", gauntlets.name)
    assertEquals("Giày Huyết Ma", boots.name)
    assertEquals("https://res.cloudinary.com/csz2me5b/image/upload/v1792832619/Item_Cao_Minh_Huyet_Ma_Gauntlets.webp", gauntlets.iconRef)
    assertEquals("https://res.cloudinary.com/csz2me5b/image/upload/v1792832622/Item_Cao_Minh_Huyet_Ma_Boots.webp", boots.iconRef)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, CAO_MINH_ID))
    assertEquals(0, gauntlets.bonuses.defPercent + gauntlets.bonuses.vitPercent)
    assertEquals(0, boots.bonuses.defPercent + boots.bonuses.vitPercent)
    val detail = requireNotNull(CharacterDetailProjector.projectCharacter(state, CAO_MINH_ID))
    assertTrue(detail.inventory.isEmpty())
    assertEquals(5, detail.equipmentDetails.size)
    assertTrue(detail.equipmentDetails.any { it.id == gauntlets.id && it.iconRef == gauntlets.iconRef })
    assertTrue(detail.equipmentDetails.any { it.id == boots.id && it.iconRef == boots.iconRef })
  }

  @Test fun canonVisualsAreAttachedOnlyWhenSourceAssetsExist() {
    val registered = listOf(
      CAO_MINH_HUYET_MA_KIEM_ID, CAO_MINH_HUYET_MA_CHIEN_KHAI_ID,
      CAO_MINH_HUYET_MA_GAUNTLETS_ID, CAO_MINH_HUYET_MA_BOOTS_ID,
      LUC_TRAM_KIEM_KHAI_ID, LUC_TRAM_TICH_QUANG_SET_ID,
      SYVIAL_GODKILLER_ID, AN_NHIEN_OUTFIT_ID, AN_NHIEN_FOOTWEAR_ID
    )
    registered.forEach { id ->
      assertTrue("$id missing Canon art", EquipmentCatalog.definition(id)?.iconRef
        ?.startsWith("https://res.cloudinary.com/csz2me5b/image/upload/") == true)
    }
  }
}
