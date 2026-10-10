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
        assertFalse(state.inventories.getValue(id).items.containsKey(itemId))
        assertFalse(InventoryCapacityPolicy.consumesSlot(state, id, itemId))
      }
      assertEquals(0, InventoryCapacityPolicy.usedSlots(state, id))
      assertEquals(InventoryPolicy.profileFor(state, id).maxTypes, InventoryCapacityPolicy.maxSlots(state, id))
    }
  }

  @Test fun boundEquipmentCannotBeUnequippedOrStoredAsItems() {
    val state = freshAll()
    val result = EquipmentEngine.unequip(state, ItemCommand(
      "U", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertFalse(result.applied)
    assertEquals("equipment_bound_forever", result.validation.reason)
    assertFalse(state.inventories.getValue(KAI_ID).items.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
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
    assertEquals(0, kai.inventoryDetails.count { it.equipped })
    assertEquals(2, kai.equipmentDetails.size)
  }
}
