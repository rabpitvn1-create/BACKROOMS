package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class NamedFollowerItemActionsTest {
  private fun party(): GameState {
    val initial = GameState.initial()
    val characters = initial.characters.toMutableMap()
    for (id in VietnameseFollowersCanon.ids) {
      val c = characters.getValue(id)
      characters[id] = c.copy(presence = CharacterPresence.ACTIVE, metadata = c.metadata + ("encountered" to "true"))
    }
    return initial.copy(characters = characters,
      party = initial.party.copy(memberIds = listOf(CAO_MINH_ID) + VietnameseFollowersCanon.ids))
  }

  @Test fun realUnequipAndReequipChangeWeaponAndGearWithoutHealingOrDuplicating() {
    val initial = party()
    val removed = NamedFollowerItemActions.tryExecute(initial, "Nguyễn Quốc Nam tháo Hành Lang 01", "ITEM_1")!!
    assertNull(removed.error)
    assertFalse(EquipmentEngine.isEquipped(removed.state, NGUYEN_QUOC_NAM_ID, "nam:corridor-pistol"))
    assertTrue(removed.state.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.containsKey("nam:corridor-pistol"))
    assertTrue(removed.state.time.elapsedSubjectiveMinutes > initial.time.elapsedSubjectiveMinutes)
    val reequipped = NamedFollowerItemActions.tryExecute(removed.state, "Nam trang bị Hành Lang 01", "ITEM_2")!!
    assertNull(reequipped.error)
    assertEquals(19, CharacterStatEngine.weaponDamage(reequipped.state, NGUYEN_QUOC_NAM_ID))
    assertEquals(1, reequipped.state.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.getValue("nam:corridor-pistol").quantity)

    val watch = NamedFollowerItemActions.tryExecute(reequipped.state, "Nam tháo Đồng Hồ Lời Hứa", "ITEM_3")!!
    assertNull(watch.error)
    assertEquals(0, CharacterStatEngine.equippedBonuses(watch.state, NGUYEN_QUOC_NAM_ID).accuracyPercent)
    val back = NamedFollowerItemActions.tryExecute(watch.state, "Nam đeo Đồng Hồ Lời Hứa", "ITEM_4")!!
    assertNull(back.error)
    assertEquals(10, CharacterStatEngine.equippedBonuses(back.state, NGUYEN_QUOC_NAM_ID).accuracyPercent)
  }

  @Test fun transferFindsRealSourceAndRecipientWithoutConfusingCaoMinhWithTarget() {
    var state = party()
    state = NamedFollowerItemActions.tryExecute(state, "Nam tháo Hành Lang 01", "TRANSFER_1")!!.state
    val transferred = NamedFollowerItemActions.tryExecute(state, "Nam đưa Hành Lang 01 cho Cao Minh", "TRANSFER_2")!!
    assertNull(transferred.error)
    assertFalse(transferred.state.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.containsKey("nam:corridor-pistol"))
    assertTrue(transferred.state.inventories.getValue(CAO_MINH_ID).items.containsKey("nam:corridor-pistol"))
    assertTrue(CAO_MINH_ID in transferred.changedOwners)
    assertTrue(NGUYEN_QUOC_NAM_ID in transferred.changedOwners)
    assertTrue("nam:corridor-pistol" in transferred.changedItems)
    val returned = NamedFollowerItemActions.tryExecute(transferred.state,
      "Cao Minh đưa Hành Lang 01 cho Nguyễn Quốc Nam", "TRANSFER_3")!!
    assertNull(returned.error)
    assertTrue(returned.state.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.containsKey("nam:corridor-pistol"))
    assertFalse(EquipmentEngine.isEquipped(returned.state, NGUYEN_QUOC_NAM_ID, "nam:corridor-pistol"))
  }

  @Test fun multiClauseIsAtomicAndUnknownItemsDoNotAppear() {
    val initial = party()
    val rejected = NamedFollowerItemActions.tryExecute(initial,
      "Nam tháo Hành Lang 01 rồi Nam vứt Kiếm Tưởng Tượng", "ATOMIC_1")!!
    assertNotNull(rejected.error)
    assertEquals(initial, rejected.state)
    assertEquals(initial.time.elapsedSubjectiveMinutes, rejected.state.time.elapsedSubjectiveMinutes)
    assertFalse("ATOMIC_1" in rejected.state.turn.completedTurnIds)
    val valid = NamedFollowerItemActions.tryExecute(initial,
      "Nam tháo Hành Lang 01 rồi vứt nó", "ATOMIC_2")!!
    assertNull(valid.error)
    assertFalse(valid.state.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.containsKey("nam:corridor-pistol"))
    assertFalse(EquipmentEngine.isEquipped(valid.state, NGUYEN_QUOC_NAM_ID, "nam:corridor-pistol"))
    val ensured = VietnameseFollowersCanon.ensure(valid.state)
    assertFalse(ensured.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.containsKey("nam:corridor-pistol"))
  }

  @Test fun absentRegistryUnencounteredUnrecruitedAndDefeatedActorsAreRejectedWithoutTime() {
    val present = party()
    val actor = present.characters.getValue(NGUYEN_QUOC_NAM_ID)
    val missing = present.copy(characters = present.characters - NGUYEN_QUOC_NAM_ID)
    val dead = present.copy(characters = present.characters + (actor.id to actor.copy(vitalState = actor.vitalState.copy(currentHp = 0))))
    val absent = present.copy(characters = present.characters + (actor.id to actor.copy(presence = CharacterPresence.SEPARATED)))
    val unrecruited = present.copy(party = present.party.copy(memberIds = listOf(CAO_MINH_ID)))
    for (state in listOf(missing, dead, absent, unrecruited, GameState.initial())) {
      val denied = NamedFollowerItemActions.tryExecute(state, "Nguyễn Quốc Nam tháo Hành Lang 01", "DENIED_1")!!
      assertNotNull(denied.error)
      assertEquals(state, denied.state)
    }
    val unknown = NamedFollowerItemActions.tryExecute(present, "Nam trang bị Kiếm Tưởng Tượng", "DENIED_2")!!
    assertNotNull(unknown.error)
    assertEquals(present, unknown.state)
    assertNull(NamedFollowerItemActions.tryExecute(present, "Nam nói chuyện với Tiểu Lan", "DENIED_3"))
    assertNull(NamedFollowerItemActions.tryExecute(present, "Mời Nam vào Party", "DENIED_4"))
    assertNull(NamedFollowerItemActions.tryExecute(present, "Cao Minh bỏ xuống vật ở phía Nam", "DENIED_5"))
  }

  @Test fun existingTransferRestrictionsCapacityAndQuantitiesRemainAuthoritative() {
    val initial = party()
    val equipped = NamedFollowerItemActions.tryExecute(initial,
      "Nam đưa Hành Lang 01 cho Cao Minh", "RULES_1")!!
    assertEquals("item_equipped_locked", equipped.error)
    assertEquals(initial, equipped.state)
    val unarmed = NamedFollowerItemActions.tryExecute(initial, "Nam tháo Hành Lang 01", "RULES_2")!!.state
    val self = NamedFollowerItemActions.tryExecute(unarmed, "Nam đưa Hành Lang 01 cho Nam", "RULES_3")!!
    assertEquals("cannot_transfer_to_self", self.error)
    val zero = NamedFollowerItemActions.tryExecute(unarmed, "Nam đưa 0 Hành Lang 01 cho Cao Minh", "RULES_4")!!
    assertNotNull(zero.error)
    assertEquals(unarmed, zero.state)
    val excess = NamedFollowerItemActions.tryExecute(unarmed, "Nam đưa 999 Hành Lang 01 cho Cao Minh", "RULES_5")!!
    assertEquals("insufficient_item_quantity", excess.error)
    val fullInventory = initial.inventories.getValue(HUA_TIEU_LAN_ID).let { it.copy(items = it.items + mapOf(
      "supplies:a" to ItemStack("supplies:a", "Vật A"), "supplies:b" to ItemStack("supplies:b", "Vật B"))) }
    val full = unarmed.copy(inventories = unarmed.inventories + (HUA_TIEU_LAN_ID to fullInventory))
    val denied = NamedFollowerItemActions.tryExecute(full,
      "Nam đưa Hành Lang 01 cho Tiểu Lan", "RULES_6")!!
    assertEquals("inventory_slot_limit", denied.error)
    assertEquals(full, denied.state)
  }

  @Test fun useConsumesRealOwnedItemAndReplayOrPendingCannotCommitAgain() {
    val initial = party()
    val food = ItemStack("nam:food", "Bánh Mì", metadata = mapOf("consumable" to "true", "consumedOnUse" to "true"))
    val inventory = initial.inventories.getValue(NGUYEN_QUOC_NAM_ID)
    val state = initial.copy(inventories = initial.inventories + (inventory.ownerId to inventory.copy(items = inventory.items + (food.itemId to food))))
    val used = NamedFollowerItemActions.tryExecute(state, "Nam dùng Bánh Mì", "USE_1")!!
    assertNull(used.error)
    assertFalse(used.state.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.containsKey(food.itemId))
    assertTrue(food.itemId in used.changedItems)
    val replay = NamedFollowerItemActions.tryExecute(used.state, "Nam tháo Đồng Hồ Lời Hứa", "USE_1")!!
    assertEquals("turn_already_completed", replay.error)
    assertEquals(used.state, replay.state)
    val pending = state.copy(turn = state.turn.copy(pending = PendingTurn("OTHER", "Khám phá")))
    val denied = NamedFollowerItemActions.tryExecute(pending, "Nam dùng Bánh Mì", "USE_2")!!
    assertEquals("another_turn_pending", denied.error)
    assertEquals(pending, denied.state)
  }
}
