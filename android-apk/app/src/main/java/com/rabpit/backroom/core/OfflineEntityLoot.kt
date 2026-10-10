package com.rabpit.backroom.core

/**
 * Offline reward ledger. Only the authoritative combat resolution may call award().
 * Each defeated Entity is rewarded exactly once, even after Save/Load.
 */
object OfflineEntityLoot {
  data class Reward(
    val state: GameState,
    val itemId: String?,
    val text: String,
    val queued: Boolean
  )

  private const val AWARDED_PREFIX = "loot.awarded."
  private const val QUEUE_KEY = "loot.pendingEntityDrops"

  fun award(state: GameState, encounterId: String, seed: Long): Reward {
    require(encounterId.isNotBlank()) { "Entity encounter ID required" }
    val marker = AWARDED_PREFIX + encounterId
    if (marker in state.metadata) return Reward(state, null, "", false)

    val type = OfflineItemRegistry.random(seed)
    val recorded = state.copy(metadata = state.metadata + (marker to type.id))
    val claimed = pickup(recorded, type, "loot:$encounterId")
    if (claimed != null) return Reward(claimed, type.id, "Nhận được ${type.name}.", false)

    val queue = recorded.metadata[QUEUE_KEY].orEmpty().split('|').filter { it.isNotBlank() } + type.id
    return Reward(
      recorded.copy(metadata = recorded.metadata + (QUEUE_KEY to queue.joinToString("|"))),
      type.id, "${type.name} được giữ chờ vì kho đồ đầy.", true
    )
  }

  /** Run after a validated Inventory UI action has freed a slot. */
  fun collectPending(state: GameState): GameState {
    val ids = state.metadata[QUEUE_KEY].orEmpty().split('|').filter { it.isNotBlank() }
    if (ids.isEmpty()) return state

    var next = state
    val remaining = mutableListOf<String>()
    ids.forEachIndexed { index, id ->
      val type = OfflineItemRegistry.get(id)
      if (type == null) {
        remaining += id
      } else {
        val accepted = pickup(next, type, "loot:pending:$index")
        if (accepted == null) remaining += id else next = accepted
      }
    }
    return next.copy(metadata = if (remaining.isEmpty()) next.metadata - QUEUE_KEY
      else next.metadata + (QUEUE_KEY to remaining.joinToString("|")))
  }

  private fun pickup(
    state: GameState,
    type: OfflineItemRegistry.Definition,
    commandId: String
  ): GameState? {
    val current = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    // Keep existing stack metadata to avoid replacing a saved stack on merge.
    val metadata = current.items[type.id]?.metadata ?: type.metadata
    val stack = ItemStack(type.id, type.name, 1, metadata = metadata)
    if (InventoryPolicy.validateAddition(state, KAI_ID, current, stack, 1) != null) return null

    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = commandId,
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = type.id,
      itemName = type.name,
      quantity = 1,
      metadata = metadata
    ))
    return result.state.takeIf { result.applied }
  }
}
