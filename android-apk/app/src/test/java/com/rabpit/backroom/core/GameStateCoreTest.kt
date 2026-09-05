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

  private fun loot(
    id: String,
    quantity: Int = 1,
    actorId: String = KAI_ID,
    commandId: String = "loot-$id-$quantity-$actorId",
    source: CommandSource = CommandSource.SYSTEM
  ): LootGrantCommand {
    val sourceId = "test:$commandId"
    val item = ItemStack(
      itemId = id,
      name = id,
      quantity = quantity,
      metadata = mapOf(
        "loot.origin" to LootOrigin.EXPLORE_LOOT.name,
        "loot.sourceId" to sourceId,
        "loot.turnId" to "TURN_1"
      )
    )
    return LootGrantCommand(
      commandId = commandId,
      turnId = "TURN_1",
      actorId = actorId,
      source = source,
      origin = LootOrigin.EXPLORE_LOOT,
      sourceId = sourceId,
      item = item,
      quantity = quantity
    )
  }

  @Test fun authoritativeLootDiscardAndDuplicateAreDeterministic() {
    val command = loot("water")
    val granted = StateReducer.execute(base(), command)
    assertTrue(granted.applied)
    assertEquals(1, granted.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    assertTrue(LootEngine.wasGrantCommitted(granted.state, command.sourceId))

    val duplicate = StateReducer.execute(granted.state, command)
    assertTrue(duplicate.duplicate)
    assertEquals(1, duplicate.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)

    val discarded = StateReducer.execute(granted.state, ItemCommand(
      commandId = "discard-water",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.DISCARD,
      itemId = "water",
      itemName = "water"
    ))
    assertTrue(discarded.applied)
    assertFalse(discarded.state.inventories.getValue(KAI_ID).items.containsKey("water"))
  }

  @Test fun nonSystemLootGrantIsRejected() {
    val rejected = StateReducer.execute(base(), loot("water", source = CommandSource.UI))
    assertFalse(rejected.applied)
    assertEquals("loot_source_not_authoritative", rejected.validation.reason)
    assertTrue(rejected.state.inventories.getValue(KAI_ID).items.isEmpty())
    assertFalse(LootEngine.wasGrantCommitted(rejected.state, "test:loot-water-1-kai"))
  }

  @Test fun transferRequiresOwnershipAndKnownTarget() {
    val iris = CharacterState("iris", "Iris")
    val granted = StateReducer.execute(base(iris), loot("water", 2)).state
    val moved = StateReducer.execute(granted, ItemCommand(
      commandId = "transfer-water",
      turnId = "TURN_1",
      actorId = KAI_ID,
      targetId = "iris",
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.TRANSFER,
      itemId = "water",
      itemName = "water",
      quantity = 1
    ))
    assertTrue(moved.applied)
    assertEquals(1, moved.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    assertEquals(1, moved.state.inventories.getValue("iris").items.getValue("water").quantity)

    val unknown = StateReducer.execute(granted, ItemCommand(
      commandId = "transfer-unknown",
      turnId = "TURN_1",
      actorId = KAI_ID,
      targetId = "missing-character",
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.TRANSFER,
      itemId = "water",
      itemName = "water",
      quantity = 1
    ))
    assertFalse(unknown.applied)
    assertEquals("target_unknown", unknown.validation.reason)
  }

  @Test fun equipAndUnequipUseOwnedItem() {
    val granted = StateReducer.execute(base(), loot("gun")).state
    val equipped = StateReducer.execute(granted, ItemCommand(
      commandId = "equip-gun",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.EQUIP,
      itemId = "gun",
      itemName = "gun",
      slot = "weapon"
    ))
    assertTrue(equipped.applied)
    assertEquals("gun", equipped.state.equipment.getValue(KAI_ID).slots["weapon"])

    val unequipped = StateReducer.execute(equipped.state, ItemCommand(
      commandId = "unequip-gun",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.UNEQUIP,
      itemId = "gun",
      itemName = "gun",
      slot = "weapon"
    ))
    assertTrue(unequipped.applied)
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
    val granted = StateReducer.execute(base(), loot("water", 2)).state
    val stored = StateReducer.execute(granted, OmnivaultCommand(
      "store", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.STORE, itemId = "water", itemName = "Water"
    ))
    assertTrue(stored.applied)
    assertEquals(1, stored.state.omnivault.storedItems.getValue("water").quantity)

    val withdrawn = StateReducer.execute(stored.state, OmnivaultCommand(
      "withdraw", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.WITHDRAW, itemId = "water", itemName = "Water"
    ))
    assertTrue(withdrawn.applied)
    assertEquals(2, withdrawn.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)

    val living = StateReducer.execute(withdrawn.state, OmnivaultCommand(
      "living", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.STORE, itemId = "iris", itemName = "Iris"
    ))
    assertFalse(living.applied)
    assertEquals("item_not_owned", living.validation.reason)
  }

  @Test fun omnivaultScanAndCopyOperationsAreRetired() {
    val operationNames = OmnivaultCommand.Operation.values().map { it.name }.toSet()
    assertFalse("SCAN" in operationNames)
    assertFalse("COPY" in operationNames)
    assertEquals(setOf("STORE", "WITHDRAW", "RESTORE", "QUERY"), operationNames)
  }

  @Test fun restoreTargetsExistingEquipmentAndEnforcesCooldown() {
    val state = base().copy(
      equipment = base().equipment + (KAI_ID to EquipmentState(KAI_ID, mapOf("weapon" to "old-gun")))
    )
    val restored = StateReducer.execute(state, OmnivaultCommand(
      "restore", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = OmnivaultCommand.Operation.RESTORE,
      itemId = "old-gun", itemName = "Old Gun", timestampEpochMs = 1000L
    ))
    assertTrue(restored.applied)
    assertEquals(1000L + 24L * 60L * 60L * 1000L, restored.state.omnivault.restoreCooldownUntilEpochMs["old-gun"])

    val cooldown = StateReducer.execute(restored.state, OmnivaultCommand(
      "restore-again", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = OmnivaultCommand.Operation.RESTORE,
      itemId = "old-gun", itemName = "Old Gun", timestampEpochMs = 1001L
    ))
    assertFalse(cooldown.applied)
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
