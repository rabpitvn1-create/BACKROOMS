package com.rabpit.backroom.core

private fun addItem(inventory: InventoryState, rawItem: ItemStack): InventoryState {
  val item = ItemContentRules.normalize(rawItem)
  val old = inventory.items[item.itemId]?.let(ItemContentRules::normalize)
  val merged = if (old == null) item else {
    if (!ItemContentRules.sameStackState(old, item)) return inventory.copy(items = inventory.items + (item.itemId to item))
    old.copy(quantity = old.quantity + item.quantity)
  }
  return inventory.copy(items = inventory.items + (item.itemId to merged))
}

private fun removeItem(inventory: InventoryState, itemId: String, quantity: Int): InventoryState? {
  val old = inventory.items[itemId] ?: return null
  if (quantity <= 0 || old.quantity < quantity) return null
  val items = if (old.quantity == quantity) inventory.items - itemId
  else inventory.items + (itemId to old.copy(quantity = old.quantity - quantity))
  return inventory.copy(items = items)
}

private fun useItem(state: GameState, source: InventoryState, command: ItemCommand): ExecutionResult {
  val ownedRaw = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
  if (ownedRaw.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
  val owned = ItemContentRules.normalize(ownedRaw)

  if (owned.contentState == ContentState.EMPTY) return invalid(state, "item_content_empty")

  if (owned.contentState == ContentState.FULL || owned.contentState == ContentState.LOW) {
    val nextVariant = ItemContentRules.nextAfterUse(owned) ?: return invalid(state, "item_content_empty")
    var nextInventory = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    nextInventory = addItem(nextInventory, nextVariant.copy(quantity = command.quantity))
    return changed(
      state.copy(inventories = state.inventories + (command.actorId to nextInventory)),
      if (nextVariant.contentState == ContentState.EMPTY) "item_content_emptied" else "item_content_reduced"
    )
  }

  val consumedOnUse = owned.metadata["consumedOnUse"].equals("true", true) ||
    (owned.metadata["consumable"].equals("true", true) && !owned.metadata["containerPersistent"].equals("true", true))
  if (consumedOnUse) {
    val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    return changed(state.copy(inventories = state.inventories + (command.actorId to next)), "item_consumed")
  }

  return changed(state, "item_used")
}

object InventoryEngine {
  fun execute(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    if (ItemContentRules.hasForbiddenPreciseAmount(command.itemName)) return invalid(state, "precise_content_amount_forbidden")
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val item = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity))
    return when (command.operation) {
      ItemCommand.Operation.PICKUP -> changed(state.copy(inventories = state.inventories + (command.actorId to addItem(source, item))), "inventory_pickup")
      ItemCommand.Operation.DROP -> {
        val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        changed(state.copy(inventories = state.inventories + (command.actorId to next)), "inventory_remove")
      }
      ItemCommand.Operation.USE -> useItem(state, source, command)
      ItemCommand.Operation.TRANSFER -> {
        val targetId = command.targetId ?: return invalid(state, "target_required")
        if (!state.characters.containsKey(targetId)) return invalid(state, "target_unknown")
        val owned = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
        if (owned.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
        val from = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val transferred = ItemContentRules.normalize(owned).copy(quantity = command.quantity)
        val to = addItem(state.inventories[targetId] ?: InventoryState(targetId), transferred)
        changed(state.copy(inventories = state.inventories + (command.actorId to from) + (targetId to to)), "inventory_transfer")
      }
      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
      }
      ItemCommand.Operation.UNEQUIP -> {
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
      }
      ItemCommand.Operation.STORE, ItemCommand.Operation.WITHDRAW -> invalid(state, "use_omnivault_command")
    }
  }
}

object PartyEngine {
  fun execute(state: GameState, command: PartyCommand): ExecutionResult {
    return when (command.operation) {
    PartyCommand.Operation.ADD -> {
      if (!state.characters.containsKey(command.targetId)) return invalid(state, "target_unknown")
      if (!command.targetPresent) return invalid(state, "target_not_present")
      if (!command.consentConfirmed) return invalid(state, "join_not_confirmed")
      if (command.targetId in state.party.memberIds) return invalid(state, "already_in_party")
      if (state.party.memberIds.size >= state.party.maxMembers) return invalid(state, "party_full")
      changed(state.copy(party = state.party.copy(memberIds = state.party.memberIds + command.targetId)), "party_member_added")
    }
    PartyCommand.Operation.REMOVE -> {
      if (command.targetId == state.party.leaderId) return invalid(state, "cannot_remove_leader")
      if (command.targetId !in state.party.memberIds) return invalid(state, "not_in_party")
      changed(state.copy(party = state.party.copy(memberIds = state.party.memberIds - command.targetId)), "party_member_removed")
    }
    PartyCommand.Operation.SET_LEADER -> {
      if (command.targetId !in state.party.memberIds) return invalid(state, "leader_not_in_party")
      changed(state.copy(party = state.party.copy(leaderId = command.targetId)), "party_leader_changed")
    }
    PartyCommand.Operation.SEPARATE -> {
      val character = state.characters[command.targetId] ?: return invalid(state, "target_unknown")
      changed(state.copy(characters = state.characters + (command.targetId to character.copy(presence = CharacterPresence.SEPARATED))), "party_member_separated")
    }
    PartyCommand.Operation.FOLLOW, PartyCommand.Operation.QUERY -> ExecutionResult(state, applied = false)
    }
  }
}

object StatusEngine {
  fun execute(state: GameState, command: StatusCommand): ExecutionResult {
    if (!state.characters.containsKey(command.targetId)) return invalid(state, "target_unknown")
    return when (command.operation) {
      StatusCommand.Operation.APPLY -> {
        val effect = command.effect ?: return invalid(state, "status_effect_required")
        if (effect.id in state.statuses) return invalid(state, "status_already_exists")
        val character = state.characters.getValue(command.targetId)
        changed(state.copy(
          statuses = state.statuses + (effect.id to effect),
          characters = state.characters + (command.targetId to character.copy(statusIds = character.statusIds + effect.id))
        ), "status_applied")
      }
      StatusCommand.Operation.REMOVE -> {
        val id = command.statusId ?: return invalid(state, "status_id_required")
        if (id !in state.statuses) return invalid(state, "status_missing")
        val character = state.characters.getValue(command.targetId)
        changed(state.copy(statuses = state.statuses - id, characters = state.characters + (command.targetId to character.copy(statusIds = character.statusIds - id))), "status_removed")
      }
      StatusCommand.Operation.UPDATE -> {
        val effect = command.effect ?: return invalid(state, "status_effect_required")
        if (effect.id !in state.statuses) return invalid(state, "status_missing")
        changed(state.copy(statuses = state.statuses + (effect.id to effect)), "status_updated")
      }
      StatusCommand.Operation.QUERY -> ExecutionResult(state, applied = false)
    }
  }
}

internal fun invalid(state: GameState, reason: String) = ExecutionResult(state, false, validation = ValidationResult(false, reason))
internal fun changed(state: GameState, event: String) = ExecutionResult(state, true, events = listOf(event))
