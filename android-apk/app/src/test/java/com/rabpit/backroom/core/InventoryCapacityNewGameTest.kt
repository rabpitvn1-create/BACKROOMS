package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryCapacityNewGameTest {
  private fun freshAll(): GameState = CharacterEquipmentSystem.normalize(
    SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial()))
  )

  @Test fun equippedItemsConsumeZeroCapacityForAllFourCharacters() {
    val state = freshAll()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertTrue("character must exist: $id", state.characters.containsKey(id))
      val slots = state.equipment[id]?.slots.orEmpty()
      assertTrue("expected equipped loadout: $id", slots.isNotEmpty())
      val equippedIds = InventoryCapacityPolicy.equippedItemIds(state, id)
      assertTrue(equippedIds.isNotEmpty())
      equippedIds.forEach { itemId ->
        assertTrue("equipped item remains Inventory-owned: $id/$itemId", state.inventories.getValue(id).items.containsKey(itemId))
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
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Blackblood Armor", slot="armor"
    ))
    assertTrue(unequip.applied)
    assertTrue(unequip.state.inventories.getValue(KAI_ID).items.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
    assertEquals(1, InventoryCapacityPolicy.usedSlots(unequip.state, KAI_ID))
    val reEquip = EquipmentEngine.equip(unequip.state, ItemCommand(
      "E", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Blackblood Armor", slot="armor"
    ))
    assertTrue(reEquip.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(reEquip.state, KAI_ID))
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences() {
    val loaded = GameStateCodec.decode(GameStateCodec.encode(freshAll()))
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id))
    }
  }

  @Test fun freshNewGameKaiProjectionIsImmediatelyAuthoritative() {
    val state = CharacterEquipmentSystem.normalize(GameState.initial())
    val kai = CharacterDetailProjector.projectParty(state).members.first { it.id == KAI_ID }
    assertEquals(140, kai.currentHp)
    assertEquals(140, kai.maxHp)
    assertEquals("∞", kai.energyDisplay)
    assertEquals(4, kai.regenPerCompletedTurn)
    assertEquals(107, kai.str.effective)
    assertEquals(109, kai.df.effective)
    assertEquals(112, kai.agi.effective)
    assertEquals(109, kai.crit.effective)
    assertEquals(0, kai.inventoryCapacityUsed)
    assertEquals(ItemSystem.capacityFor(state, KAI_ID).maxTypes, kai.inventoryCapacityMax)
    assertEquals(6, kai.equipment.values.toSet().size)
    assertTrue(kai.inventoryDetails.count { it.equipped } >= 6)
  }
}
