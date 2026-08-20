package com.rabpit.backroom.core

object OmnivaultEngine {
  const val MAX_SCAN_SLOTS = 3
  const val RESTORE_COOLDOWN_MS = 24L * 60L * 60L * 1000L

  fun execute(state: GameState, command: OmnivaultCommand): ExecutionResult {
    if (command.actorId != KAI_ID) return invalid(state, "omnivault_owner_only")
    if (command.isLiving) return invalid(state, "living_target_forbidden")
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    if (command.operation in setOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.RESTORE) && command.isLargeAssembly) {
      return invalid(state, "large_assembly_forbidden")
    }
    return when (command.operation) {
      OmnivaultCommand.Operation.STORE -> store(state, command)
      OmnivaultCommand.Operation.WITHDRAW -> withdraw(state, command)
      OmnivaultCommand.Operation.SCAN -> scan(state, command)
      OmnivaultCommand.Operation.COPY -> copy(state, command)
      OmnivaultCommand.Operation.RESTORE -> restore(state, command)
      OmnivaultCommand.Operation.QUERY -> ExecutionResult(state, applied = false)
    }
  }

  private fun store(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val inventory = state.inventories[c.actorId] ?: return invalid(state, "inventory_missing")
    val owned = inventory.items[c.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < c.quantity) return invalid(state, "insufficient_item_quantity")
    val remaining = owned.quantity - c.quantity
    val nextInventory = inventory.copy(items = if (remaining == 0) inventory.items - c.itemId else inventory.items + (c.itemId to owned.copy(quantity = remaining)))
    val oldStored = state.omnivault.storedItems[c.itemId]
    val stored = (oldStored ?: owned.copy(quantity = 0)).copy(quantity = (oldStored?.quantity ?: 0) + c.quantity)
    return changed(state.copy(
      inventories = state.inventories + (c.actorId to nextInventory),
      omnivault = state.omnivault.copy(storedItems = state.omnivault.storedItems + (c.itemId to stored))
    ), "omnivault_stored")
  }

  private fun withdraw(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val stored = state.omnivault.storedItems[c.itemId] ?: return invalid(state, "item_not_stored")
    if (stored.quantity < c.quantity) return invalid(state, "insufficient_stored_quantity")
    val remaining = stored.quantity - c.quantity
    val nextStored = if (remaining == 0) state.omnivault.storedItems - c.itemId else state.omnivault.storedItems + (c.itemId to stored.copy(quantity = remaining))
    val inventory = state.inventories[c.actorId] ?: InventoryState(c.actorId)
    val old = inventory.items[c.itemId]
    val item = stored.copy(quantity = (old?.quantity ?: 0) + c.quantity)
    return changed(state.copy(
      inventories = state.inventories + (c.actorId to inventory.copy(items = inventory.items + (c.itemId to item))),
      omnivault = state.omnivault.copy(storedItems = nextStored)
    ), "omnivault_withdrawn")
  }

  private fun scan(state: GameState, c: OmnivaultCommand): ExecutionResult {
    if (!c.isOriginal) return invalid(state, "copy_cannot_be_scanned")
    if (c.itemId in state.omnivault.markedSourceIds) return invalid(state, "source_already_marked")
    val slots = state.omnivault.scanSlots.toMutableList()
    val slotNumber = if (slots.size < MAX_SCAN_SLOTS) slots.size + 1 else 1
    if (slots.size == MAX_SCAN_SLOTS) slots.removeAt(0)
    slots += ScanSlot(slotNumber, c.itemId, ItemStack(c.itemId, c.itemName), c.timestampEpochMs)
    return changed(state.copy(omnivault = state.omnivault.copy(
      scanSlots = slots.mapIndexed { index, slot -> slot.copy(slot = index + 1) },
      markedSourceIds = state.omnivault.markedSourceIds + c.itemId
    )), "omnivault_scanned")
  }

  private fun copy(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val template = state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == c.itemId }?.templateItem
      ?: return invalid(state, "scan_template_missing")
    val inventory = state.inventories[c.actorId] ?: InventoryState(c.actorId)
    val copyId = "${c.itemId}:copy"
    val old = inventory.items[copyId]
    val item = template.copy(itemId = copyId, quantity = (old?.quantity ?: 0) + c.quantity, metadata = template.metadata + ("omnivaultCopy" to "true"))
    return changed(state.copy(inventories = state.inventories + (c.actorId to inventory.copy(items = inventory.items + (copyId to item)))), "omnivault_copied")
  }

  private fun restore(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val cooldown = state.omnivault.restoreCooldownUntilEpochMs[c.itemId] ?: 0L
    if (c.timestampEpochMs < cooldown) return invalid(state, "restore_cooldown_active")
    val inventory = state.inventories[c.actorId] ?: return invalid(state, "inventory_missing")
    if (c.itemId !in inventory.items && c.itemId !in state.omnivault.storedItems) return invalid(state, "restore_target_missing")
    return changed(state.copy(omnivault = state.omnivault.copy(
      restoreCooldownUntilEpochMs = state.omnivault.restoreCooldownUntilEpochMs + (c.itemId to c.timestampEpochMs + RESTORE_COOLDOWN_MS)
    )), "omnivault_restored")
  }
}
