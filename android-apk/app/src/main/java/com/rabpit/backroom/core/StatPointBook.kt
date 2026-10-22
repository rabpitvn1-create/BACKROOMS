package com.rabpit.backroom.core

object StatPointBook {
  val stats = setOf("STR", "DEX", "AGI", "VIT", "EVA")

  /** One UI-owned transaction updates exactly one base stat and consumes exactly one book. */
  fun use(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.source != CommandSource.UI || command.itemId != STAT_POINT_BOOK_ID ||
      command.operation != ItemCommand.Operation.USE) return invalid(state, "item_ui_required")
    if (command.quantity != 1) return invalid(state, "stat_book_one_at_a_time")
    val stat = command.metadata["targetStat"]
    if (stat !in stats) return invalid(state, "stat_book_selection_required")
    val actor = state.characters[command.actorId] ?: return invalid(state, "actor_unknown")
    if (actor.id !in state.party.memberIds || actor.presence != CharacterPresence.ACTIVE ||
      actor.vitalState.currentHp <= 0 || actor.vitalState.condition == CharacterCondition.DEAD)
      return invalid(state, "stat_book_actor_unavailable")
    val inventory = state.inventories[actor.id] ?: return invalid(state, "item_not_owned")
    val item = inventory.items[STAT_POINT_BOOK_ID]?.takeIf { it.quantity > 0 } ?: return invalid(state, "item_not_owned")
    if (CharacterProgressionCore.baseStat(state, actor.id, stat!!) >= CharacterProgressionCore.MAX_STAT)
      return invalid(state, "stat_book_stat_capped")
    val p = actor.statProfile
    val raised = when (stat) {
      "STR" -> p.copy(str = p.str + 1)
      "DEX" -> p.copy(dex = p.dex + 1)
      "AGI" -> p.copy(agi = p.agi + 1)
      "VIT" -> p.copy(vit = p.vit + 1)
      "EVA" -> p.copy(eva = p.eva + 1)
      else -> return invalid(state, "stat_book_selection_required")
    }
    val items = if (item.quantity == 1) inventory.items - STAT_POINT_BOOK_ID
      else inventory.items + (STAT_POINT_BOOK_ID to item.copy(quantity = item.quantity - 1))
    val next = state.copy(characters = state.characters + (actor.id to actor.copy(statProfile = raised)),
      inventories = state.inventories + (actor.id to inventory.copy(items = items)))
    // VIT changes Max HP through the existing stat engine, never heals or resurrects.
    return changed(CharacterStatEngine.setCurrentHp(next, actor.id, actor.vitalState.currentHp), "stat_book_used:$stat")
  }

  /** One roll for the combined book pool; only Stat Books exist in this first phase. */
  fun dropForRoll(roll: Int): String? {
    require(roll in 0 until 10000) { "dungeon_book_roll_invalid" }
    return if (roll < DungeonRuntime.BOOK_DROP_BASIS_POINTS) STAT_POINT_BOOK_ID else null
  }

  fun collectPending(input: GameState): GameState {
    var state = input
    var pending = state.dungeon.pendingBooks
    while (pending > 0) {
      state = InventoryEngine.grantDrop(state, STAT_POINT_BOOK_ID, "DUNGEON_DROP") ?: break
      pending--
    }
    return if (pending == input.dungeon.pendingBooks) state
      else state.copy(dungeon = state.dungeon.copy(pendingBooks = pending))
  }
}
