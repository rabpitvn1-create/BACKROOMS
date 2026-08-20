package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GameStateCoreTest {
  private fun base(vararg characters: CharacterState): GameState {
    val all = listOf(CharacterState(KAI_ID, "Kai Akechi")) + characters
    return GameState.initial().copy(
      characters = all.associateBy { it.id },
      inventories = all.associate { it.id to InventoryState(it.id) },
      equipment = all.associate { it.id to EquipmentState(it.id) }
    )
  }

  private fun item(id: String, op: ItemCommand.Operation, quantity: Int = 1, target: String? = null, slot: String? = null) =
    ItemCommand("cmd-$id-$op-$quantity-${target.orEmpty()}", "TURN_1", KAI_ID, target, CommandSource.RULE, op, id, id, quantity, slot)

  @Test fun pickupDropAndDuplicateAreDeterministic() {
    val picked = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP))
    assertEquals(1, picked.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    val duplicate = StateReducer.execute(picked.state, item("water", ItemCommand.Operation.PICKUP))
    assertTrue(duplicate.duplicate)
    assertEquals(1, duplicate.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    val dropped = StateReducer.execute(picked.state, item("water", ItemCommand.Operation.DROP))
    assertFalse(dropped.state.inventories.getValue(KAI_ID).items.containsKey("water"))
  }

  @Test fun transferRequiresOwnershipAndKnownTarget() {
    val iris = CharacterState("iris", "Iris")
    val picked = StateReducer.execute(base(iris), item("water", ItemCommand.Operation.PICKUP, 2)).state
    val moved = StateReducer.execute(picked, item("water", ItemCommand.Operation.TRANSFER, 1, "iris"))
    assertTrue(moved.applied)
    assertEquals(1, moved.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    assertEquals(1, moved.state.inventories.getValue("iris").items.getValue("water").quantity)
  }

  @Test fun equipAndUnequipUseOwnedItem() {
    val picked = StateReducer.execute(base(), item("gun", ItemCommand.Operation.PICKUP)).state
    val equipped = StateReducer.execute(picked, item("gun", ItemCommand.Operation.EQUIP, slot = "weapon"))
    assertEquals("gun", equipped.state.equipment.getValue(KAI_ID).slots["weapon"])
    val unequipped = StateReducer.execute(equipped.state, item("gun", ItemCommand.Operation.UNEQUIP, slot = "weapon"))
    assertNull(unequipped.state.equipment.getValue(KAI_ID).slots["weapon"])
  }

  @Test fun partyNeedsPresenceConsentAndHasFourMemberLimit() {
    val people = (1..4).map { CharacterState("p$it", "P$it") }
    var state = base(*people.toTypedArray())
    for (i in 1..3) {
      val command = PartyCommand("join-$i", "TURN_1", KAI_ID, "p$i", CommandSource.UI, PartyCommand.Operation.ADD, true, true)
      state = StateReducer.execute(state, command).state
    }
    assertEquals(4, state.party.memberIds.size)
    val full = StateReducer.execute(state, PartyCommand("join-4", "TURN_1", KAI_ID, "p4", CommandSource.UI, PartyCommand.Operation.ADD, true, true))
    assertEquals("party_full", full.validation.reason)
    val noConsent = StateReducer.execute(base(people[0]), PartyCommand("no-consent", "TURN_1", KAI_ID, "p1", CommandSource.LITERT, PartyCommand.Operation.ADD, false, true))
    assertEquals("join_not_confirmed", noConsent.validation.reason)
  }

  @Test fun statusIsStructuredAndRemovable() {
    val effect = StatusEffect("injury-leg", "INJURY", "validated_event", "TURN_1", persistent = true)
    val applied = StateReducer.execute(base(), StatusCommand("status-add", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = effect))
    assertTrue("injury-leg" in applied.state.statuses)
    val removed = StateReducer.execute(applied.state, StatusCommand("status-remove", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, operation = StatusCommand.Operation.REMOVE, statusId = "injury-leg"))
    assertFalse("injury-leg" in removed.state.statuses)
  }

  @Test fun omnivaultStoreWithdrawAndLivingValidation() {
    val picked = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, 2)).state
    val stored = StateReducer.execute(picked, OmnivaultCommand("store", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.STORE, itemId = "water", itemName = "Water"))
    assertEquals(1, stored.state.omnivault.storedItems.getValue("water").quantity)
    val withdrawn = StateReducer.execute(stored.state, OmnivaultCommand("withdraw", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.WITHDRAW, itemId = "water", itemName = "Water"))
    assertEquals(2, withdrawn.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    val living = StateReducer.execute(withdrawn.state, OmnivaultCommand("living", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.STORE, itemId = "iris", itemName = "Iris", isLiving = true))
    assertEquals("living_target_forbidden", living.validation.reason)
  }

  @Test fun omnivaultThreeSlotsCopiesStackAndRestoreConservesResources() {
    var state = base()
    for (i in 1..4) {
      state = StateReducer.execute(state, item("original-$i", ItemCommand.Operation.PICKUP)).state
      state = StateReducer.execute(state, OmnivaultCommand("scan-$i", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.SCAN, itemId = "original-$i", itemName = "Item $i", timestampEpochMs = i.toLong())).state
    }
    assertEquals(3, state.omnivault.scanSlots.size)
    assertFalse(state.omnivault.scanSlots.any { it.sourceItemId == "original-1" })
    assertTrue("original-1" in state.omnivault.markedSourceIds)

    val copied = StateReducer.execute(state, OmnivaultCommand("copy", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.COPY, itemId = "original-4", itemName = "Item 4", quantity = 2))
    assertEquals(3, copied.state.inventories.getValue(KAI_ID).items.getValue("original-4").quantity)
    assertEquals("2", copied.state.inventories.getValue(KAI_ID).items.getValue("original-4").metadata["omnivaultCopyCount"])

    val emptyBottle = ItemStack(
      "empty-bottle", "Vỏ chai nước rỗng", quantity = 1, condition = "DENTED_OPEN",
      metadata = mapOf("remainingContent" to "0", "contentType" to "water")
    )
    val withBottle = copied.state.copy(
      inventories = copied.state.inventories + (KAI_ID to copied.state.inventories.getValue(KAI_ID).copy(
        items = copied.state.inventories.getValue(KAI_ID).items + (emptyBottle.itemId to emptyBottle)
      ))
    )
    val restored = StateReducer.execute(withBottle, OmnivaultCommand(
      "restore", "TURN_1", KAI_ID, source = CommandSource.UI, operation = OmnivaultCommand.Operation.RESTORE,
      itemId = "empty-bottle", itemName = "Vỏ chai nước rỗng", timestampEpochMs = 1000
    ))
    val restoredBottle = restored.state.inventories.getValue(KAI_ID).items.getValue("empty-bottle")
    assertEquals(1, restoredBottle.quantity)
    assertEquals("BEST_CONDITION", restoredBottle.condition)
    assertEquals("0", restoredBottle.metadata["remainingContent"])
    assertEquals("water", restoredBottle.metadata["contentType"])
    assertEquals("BEST_CONDITION_RESOURCE_CONSERVING", restoredBottle.metadata["restoreMode"])
    assertFalse(restored.state.inventories.getValue(KAI_ID).items.containsKey("water-bottle"))
    assertEquals("empty-bottle", restored.state.metadata["lastReferencedItemId"])

    val cooldown = StateReducer.execute(restored.state, OmnivaultCommand("restore-again", "TURN_1", KAI_ID, source = CommandSource.UI, operation = OmnivaultCommand.Operation.RESTORE, itemId = "empty-bottle", itemName = "Vỏ chai nước rỗng", timestampEpochMs = 1001))
    assertEquals("restore_cooldown_active", cooldown.validation.reason)
  }

  @Test fun geminiWorldDeltaNeedsGameEngineValidation() {
    val rejected = StateReducer.execute(base(), ValidatedLegacyStateCommand(
      "world-invalid", "TURN_1", source = CommandSource.GEMINI, location = "Level 1", validatedByGameEngine = false
    ))
    assertEquals("engine_validation_required", rejected.validation.reason)
    assertNull(rejected.state.world["location"])
    val accepted = StateReducer.execute(base(), ValidatedLegacyStateCommand(
      "world-valid", "TURN_1", source = CommandSource.GEMINI, location = "Level 1", validatedByGameEngine = true
    ))
    assertEquals("Level 1", accepted.state.world["location"])
  }
}
