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

  private fun item(
    id: String,
    op: ItemCommand.Operation,
    quantity: Int = 1,
    target: String? = null,
    slot: String? = null,
    source: CommandSource = CommandSource.SYSTEM
  ) = ItemCommand("cmd-$id-$op-$quantity-${target.orEmpty()}-$source", "TURN_1", KAI_ID, target, source, op, id, id, quantity, slot)

  @Test fun authoritativeGrantDropAndDuplicateAreDeterministic() {
    val picked = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP))
    assertEquals(1, picked.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    val duplicate = StateReducer.execute(picked.state, item("water", ItemCommand.Operation.PICKUP))
    assertTrue(duplicate.duplicate)
    assertEquals(1, duplicate.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    val dropped = StateReducer.execute(picked.state, item("water", ItemCommand.Operation.DROP))
    assertFalse(dropped.state.inventories.getValue(KAI_ID).items.containsKey("water"))
  }

  @Test fun playerPickupIsRejectedButStoryGrantIsAllowed() {
    val playerPickup = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, source = CommandSource.RULE))
    assertFalse(playerPickup.applied)
    assertEquals("player_pickup_unavailable", playerPickup.validation.reason)
    assertTrue(playerPickup.state.inventories.getValue(KAI_ID).items.isEmpty())

    val ungroundedStoryGrant = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, source = CommandSource.GEMINI))
    assertFalse(ungroundedStoryGrant.applied)
    assertEquals("acquisition_event_required", ungroundedStoryGrant.validation.reason)

    val groundedCommand = item("water", ItemCommand.Operation.PICKUP, source = CommandSource.GEMINI).copy(
      metadata = mapOf("acquisitionSource" to "WORLD_EVENT")
    )
    val storyGrant = StateReducer.execute(base(), groundedCommand)
    assertTrue(storyGrant.applied)
    assertEquals(1, storyGrant.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
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

  @Test fun partyNeedsPresenceConsentAndEnforcesConfiguredMemberLimit() {
    val capacity = GameState.initial().party.maxMembers
    val people = (1..capacity).map { CharacterState("p$it", "P$it") }
    var state = base(*people.toTypedArray())
    for (i in 1 until capacity) {
      val command = PartyCommand("join-$i", "TURN_1", KAI_ID, "p$i", CommandSource.UI, PartyCommand.Operation.ADD, true, true)
      val joined = StateReducer.execute(state, command)
      assertTrue("Party slot ${i + 1} should accept a confirmed, present member", joined.applied)
      state = joined.state
    }
    assertEquals(listOf(KAI_ID) + people.dropLast(1).map { it.id }, state.party.memberIds)
    assertEquals(capacity, state.party.memberIds.size)
    val overflow = people.last().id
    val full = StateReducer.execute(state, PartyCommand("join-overflow", "TURN_1", KAI_ID, overflow, CommandSource.UI, PartyCommand.Operation.ADD, true, true))
    assertFalse(full.applied)
    assertEquals("party_full", full.validation.reason)
    assertEquals(state.party, full.state.party)

    val beforeJoin = base(people[0])
    val noConsent = StateReducer.execute(beforeJoin, PartyCommand("no-consent", "TURN_1", KAI_ID, "p1", CommandSource.LITERT, PartyCommand.Operation.ADD, false, true))
    assertFalse(noConsent.applied)
    assertEquals("join_not_confirmed", noConsent.validation.reason)
    assertEquals(beforeJoin.party, noConsent.state.party)
    val notPresent = StateReducer.execute(beforeJoin, PartyCommand("not-present", "TURN_1", KAI_ID, "p1", CommandSource.UI, PartyCommand.Operation.ADD, true, false))
    assertFalse(notPresent.applied)
    assertEquals("target_not_present", notPresent.validation.reason)
    assertEquals(beforeJoin.party, notPresent.state.party)
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

  @Test fun omnivaultThreeSlotsAndCopyRemainGameplayMechanics() {
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
  }

  @Test fun restoreIsNarrativeOnlyAndCannotMutateInventoryState() {
    val withItem = StateReducer.execute(base(), item("old-gun", ItemCommand.Operation.PICKUP)).state
    val before = withItem.inventories.getValue(KAI_ID).items.getValue("old-gun")
    val restored = StateReducer.execute(withItem, OmnivaultCommand(
      "restore", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = OmnivaultCommand.Operation.RESTORE,
      itemId = "old-gun", itemName = "Old Gun", timestampEpochMs = 1000
    ))
    assertFalse(restored.applied)
    assertEquals("restore_narrative_only", restored.validation.reason)
    assertEquals(before, restored.state.inventories.getValue(KAI_ID).items.getValue("old-gun"))
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
