package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GameStateCoreTest {
  private fun base(vararg characters: CharacterState): GameState {
    val all = listOf(CharacterState(KAI_ID, "Cao Minh")) + characters
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

    val storyGrant = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, source = CommandSource.GEMINI))
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
    assertFalse(equipped.applied)
    assertEquals("equipment_bound_forever", equipped.validation.reason)
    assertNull(equipped.state.equipment.getValue(KAI_ID).slots["weapon"])
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
