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
    listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
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

  @Test fun boundEquipmentCannotBeStoredAsItems() {
    val state = freshAll()
    val equippedIds = InventoryCapacityPolicy.equippedItemIds(state, CAO_MINH_ID)
    assertTrue("expected bound equipment: $CAO_MINH_ID", equippedIds.isNotEmpty())
    equippedIds.forEach { itemId ->
      assertFalse("bound equipment must not appear in inventory: $itemId",
        state.inventories.getValue(CAO_MINH_ID).items.containsKey(itemId))
    }
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences() {
    val loaded = GameStateCodec.decode(GameStateCodec.encode(freshAll()))
    listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id))
    }
  }

  @Test fun freshNewGameProjectionUsesCanonical1193aStatsAndCapacity() {
    val state = freshAll()
    val caoMinh = CharacterDetailProjector.projectParty(state).members.first { it.id == CAO_MINH_ID }
    assertEquals(50, caoMinh.currentHp)
    assertEquals(60, caoMinh.maxHp)
    assertEquals("∞", caoMinh.energyDisplay)
    assertEquals(0, caoMinh.regenPerCompletedTurn)
    assertEquals(listOf(5,5,5,5,5), listOf(caoMinh.str.base, caoMinh.dex.base, caoMinh.agi.base, caoMinh.vit.base, caoMinh.eva.base))
    assertEquals(listOf(37,25,24,7,6), listOf(caoMinh.str.effective, caoMinh.dex.effective, caoMinh.agi.effective, caoMinh.vit.effective, caoMinh.eva.effective))
    assertEquals(0, caoMinh.inventoryCapacityUsed)
    assertEquals(InventoryPolicy.CAO_MINH.maxTypes, caoMinh.inventoryCapacityMax)
    assertEquals(4, caoMinh.equipment.values.toSet().size)
    assertEquals(0, caoMinh.inventoryDetails.count { it.equipped })
    assertEquals(4, caoMinh.equipmentDetails.size)
  }
}
