package com.rabpit.backroom.core

object OmnivaultEngine {
  private const val RESTORE_COOLDOWN_MS = 24L * 60L * 60L * 1000L

  fun execute(state: GameState, command: OmnivaultCommand): ExecutionResult {
    if (command.actorId != KAI_ID) return invalid(state, "omnivault_owner_only")
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    return when (command.operation) {
      OmnivaultCommand.Operation.STORE -> store(state, command)
      OmnivaultCommand.Operation.WITHDRAW -> withdraw(state, command)
      OmnivaultCommand.Operation.RESTORE -> restore(state, command)
      OmnivaultCommand.Operation.QUERY -> ExecutionResult(state, applied = false)
    }
  }

  private fun store(state: GameState, command: OmnivaultCommand): ExecutionResult {
    val inventory = state.inventories[KAI_ID] ?: return invalid(state, "inventory_missing")
    val owned = inventory.items[command.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
    if (InventoryPolicy.isEquipped(state, KAI_ID, command.itemId)) return invalid(state, "item_equipped")
    if (InventoryPolicy.isKaiSignatureEquipment(state, owned)) return invalid(state, "signature_equipment_locked")

    val storedRaw = state.omnivault.storedItems[command.itemId]
    if (storedRaw != null && !ItemContentRules.sameStackState(storedRaw, owned)) return invalid(state, "omnivault_stack_state_conflict")
    val remaining = owned.quantity - command.quantity
    val nextInventory = inventory.copy(items = if (remaining == 0) inventory.items - command.itemId
      else inventory.items + (command.itemId to owned.copy(quantity = remaining)))
    val stored = (storedRaw ?: owned.copy(quantity = 0)).copy(quantity = (storedRaw?.quantity ?: 0) + command.quantity)
    return changed(state.copy(
      inventories = state.inventories + (KAI_ID to nextInventory),
      omnivault = state.omnivault.copy(storedItems = state.omnivault.storedItems + (command.itemId to stored))
    ), "omnivault_stored")
  }

  private fun withdraw(state: GameState, command: OmnivaultCommand): ExecutionResult {
    val stored = state.omnivault.storedItems[command.itemId] ?: return invalid(state, "item_not_stored")
    if (stored.quantity < command.quantity) return invalid(state, "insufficient_stored_quantity")
    val inventory = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val moved = stored.copy(quantity = command.quantity)
    val validation = InventoryPolicy.validateAddition(state, KAI_ID, inventory, moved, command.quantity)
    if (validation != null) return invalid(state, validation)
    val old = inventory.items[command.itemId]
    if (old != null && !ItemContentRules.sameStackState(old, moved)) return invalid(state, "inventory_stack_state_conflict")
    val nextInventoryItem = (old ?: moved.copy(quantity = 0)).copy(quantity = (old?.quantity ?: 0) + command.quantity)
    val remaining = stored.quantity - command.quantity
    val nextStored = if (remaining == 0) state.omnivault.storedItems - command.itemId
      else state.omnivault.storedItems + (command.itemId to stored.copy(quantity = remaining))
    return changed(state.copy(
      inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (command.itemId to nextInventoryItem))),
      omnivault = state.omnivault.copy(storedItems = nextStored)
    ), "omnivault_withdrawn")
  }

  private fun restore(state: GameState, command: OmnivaultCommand): ExecutionResult {
    val equipment = state.equipment[KAI_ID] ?: return invalid(state, "equipment_missing")
    if (command.itemId !in equipment.slots.values) return invalid(state, "restore_equipment_only")
    val now = if (command.timestampEpochMs > 0L) command.timestampEpochMs else System.currentTimeMillis()
    val cooldown = state.omnivault.restoreCooldownUntilEpochMs[command.itemId] ?: 0L
    if (cooldown > now) return invalid(state, "restore_cooldown_active")

    val inventory = state.inventories[KAI_ID]
    val physical = inventory?.items?.get(command.itemId)
    val nextInventories = if (physical == null) state.inventories else {
      state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (command.itemId to physical.copy(condition = null))))
    }
    return changed(state.copy(
      inventories = nextInventories,
      omnivault = state.omnivault.copy(
        restoreCooldownUntilEpochMs = state.omnivault.restoreCooldownUntilEpochMs + (command.itemId to (now + RESTORE_COOLDOWN_MS))
      )
    ), "omnivault_equipment_restored")
  }
}
