package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test

class InventoryCapacityNewGameTest {
  private fun freshAll(): GameState =
    CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(
      SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial()))
    ))

  @Test fun equippedItemsConsumeZeroCapacityForAllFourCharacters() {
    val state = freshAll()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertTrue("character must exist: $id", state.characters.containsKey(id))
      val equippedIds = InventoryCapacityPolicy.equippedItemIds(state, id)
      assertTrue("expected equipped loadout: $id", equippedIds.isNotEmpty())
      equippedIds.forEach { itemId ->
        assertTrue(state.inventories.getValue(id).items.containsKey(itemId))
        assertFalse(InventoryCapacityPolicy.consumesSlot(state, id, itemId))
      }
      assertEquals(0, InventoryCapacityPolicy.usedSlots(state, id))
      assertEquals(InventoryPolicy.profileFor(state, id).maxTypes, InventoryCapacityPolicy.maxSlots(state, id))
    }
  }

  @Test fun unequipMakesTheSameOwnedItemConsumeOneSlotAndReequipReleasesIt() {
    val initial = freshAll()
    val unequip = EquipmentEngine.unequip(initial, ItemCommand(
      "U", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertTrue(unequip.applied)
    assertTrue(unequip.state.inventories.getValue(KAI_ID).items.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
    assertEquals(1, InventoryCapacityPolicy.usedSlots(unequip.state, KAI_ID))
    val reEquip = EquipmentEngine.equip(unequip.state, ItemCommand(
      "E", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertTrue(reEquip.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(reEquip.state, KAI_ID))
  }

  @Test fun madGodWeaponIsOneOwnedZeroCapacityItemAndDoesNotAlterBaseStats() {
    var state = freshAll()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(
      items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID))
    )))
    val before = state.characters.getValue(KAI_ID).statProfile
    val equip = EquipmentEngine.equip(state, ItemCommand(
      "M", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=MADGOD_SET_ID, itemName="Huyết Ma Kiếm · Ma Tôn", slot="weapon"
    ))
    assertTrue(equip.applied)
    assertEquals(1, equip.state.equipment.getValue(KAI_ID).slots.values.count { it == MADGOD_SET_ID })
    assertTrue(equip.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, MADGOD_SET_ID))
    assertEquals(before, equip.state.characters.getValue(KAI_ID).statProfile)
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences() {
    val loaded = GameStateCodec.decode(GameStateCodec.encode(freshAll()))
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id))
    }
  }

  @Test fun freshNewGameProjectionUsesCanonical1193aStatsAndCapacity() {
    val state = freshAll()
    val kai = CharacterDetailProjector.projectParty(state).members.first { it.id == KAI_ID }
    assertEquals(50, kai.currentHp)
    assertEquals(55, kai.maxHp)
    assertEquals("∞", kai.energyDisplay)
    assertEquals(0, kai.regenPerCompletedTurn)
    assertEquals(listOf(5,5,5,5), listOf(kai.str.base, kai.def.base, kai.skl.base, kai.vit.base))
    assertEquals(listOf(6,6,6,6), listOf(kai.str.effective, kai.def.effective, kai.skl.effective, kai.vit.effective))
    assertEquals(0, kai.inventoryCapacityUsed)
    assertEquals(InventoryPolicy.KAI.maxTypes, kai.inventoryCapacityMax)
    assertEquals(2, kai.equipment.values.toSet().size)
    assertEquals(2, kai.inventoryDetails.count { it.equipped })
  }
}
