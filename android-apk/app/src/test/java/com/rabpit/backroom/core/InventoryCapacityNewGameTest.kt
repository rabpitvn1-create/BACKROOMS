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
    listOf(CAO_MINH_ID, LUC_TRAM_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
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

  @Test fun canonEquipmentDoesNotConsumeInventorySlotEvenWhileUnequipped() {
    val initial = freshAll()
    val unequip = EquipmentEngine.unequip(initial, ItemCommand(
      "U", null, CAO_MINH_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP,
      itemId=CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertTrue(unequip.applied)
    assertTrue(unequip.state.inventories.getValue(CAO_MINH_ID).items.containsKey(CAO_MINH_HUYET_MA_CHIEN_KHAI_ID))
    assertEquals(0, InventoryCapacityPolicy.usedSlots(unequip.state, CAO_MINH_ID))
    val reEquip = EquipmentEngine.equip(unequip.state, ItemCommand(
      "E", null, CAO_MINH_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=CAO_MINH_HUYET_MA_CHIEN_KHAI_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertTrue(reEquip.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(reEquip.state, CAO_MINH_ID))
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences() {
    val loaded = GameStateCodec.decode(GameStateCodec.encode(freshAll()))
    listOf(CAO_MINH_ID, LUC_TRAM_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id))
    }
  }

  @Test fun freshNewGameProjectionUsesCanonical1193aStatsAndCapacity() {
    val state = freshAll()
    val kai = CharacterDetailProjector.projectParty(state).members.first { it.id == CAO_MINH_ID }
    assertEquals(70, kai.currentHp)
    assertEquals(70, kai.maxHp)
    assertEquals("∞", kai.energyDisplay)
    assertEquals(0, kai.regenPerCompletedTurn)
    assertEquals(listOf(5,5,5,5), listOf(kai.str.base, kai.def.base, kai.skl.base, kai.vit.base))
    assertEquals(listOf(9,9,9,9), listOf(kai.str.effective, kai.def.effective, kai.skl.effective, kai.vit.effective))
    assertEquals(0, kai.inventoryCapacityUsed)
    assertEquals(InventoryPolicy.CAO_MINH.maxTypes, kai.inventoryCapacityMax)
    assertEquals(5, kai.equipment.values.toSet().size)
    assertEquals(0, kai.inventoryDetails.size)
    assertEquals(5, kai.equipmentDetails.count { it.equipped })
  }
}
