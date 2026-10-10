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
    val claimed = pickup(recorded, type)
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
        val accepted = pickup(next, type)
        if (accepted == null) remaining += id else next = accepted
      }
    }
    return next.copy(metadata = if (remaining.isEmpty()) next.metadata - QUEUE_KEY
      else next.metadata + (QUEUE_KEY to remaining.joinToString("|")))
  }

  private fun pickup(state: GameState, type: OfflineItemRegistry.Definition): GameState? =
    InventoryEngine.grantDrop(state, type.id)
}
