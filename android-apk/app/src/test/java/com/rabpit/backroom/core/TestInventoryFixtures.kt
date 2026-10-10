package com.rabpit.backroom.core

/** Explicit test setup. Game runtime cannot mint items from commands or narration. */
object TestInventoryFixtures {
  fun place(
    state: GameState,
    itemId: String,
    name: String,
    quantity: Int = 1,
    metadata: Map<String, String> = emptyMap()
  ): GameState {
    val inventory = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val item = ItemContentRules.normalize(ItemStack(itemId, name, quantity, metadata))
    return state.copy(inventories = state.inventories +
      (KAI_ID to inventory.copy(items = inventory.items + (itemId to item))))
  }
}
