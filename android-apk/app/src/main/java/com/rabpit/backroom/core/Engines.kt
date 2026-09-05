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

private val additiveItemEffect = Regex("^(WATER|FOOD|REST|HP)\\+([1-9][0-9]{0,3})$")

private fun parseItemEffects(item: ItemStack): Set<String>? {
  val catalogEffects = ItemDefinitionMetadata.effects(item)
  val legacyEffects = item.metadata["physiologyEffect"]
    ?.split(',', ';', '|')
    ?.map { it.trim().uppercase() }
    ?.filter(String::isNotEmpty)
    ?.toSet()
    .orEmpty()
  val effects = if (catalogEffects.isNotEmpty()) catalogEffects else legacyEffects
  if (effects.any { !isKnownItemEffect(it) }) return null
  return effects
}

private fun isKnownItemEffect(effect: String): Boolean {
  if (effect in setOf("WATER", "FOOD", "CLEAR_BLEED", "CLEAR_MILD_SICKNESS")) return true
  val match = additiveItemEffect.matchEntire(effect) ?: return false
  val kind = match.groupValues[1]
  val amount = match.groupValues[2].toIntOrNull() ?: return false
  return when (kind) {
    "WATER", "FOOD", "REST" -> amount in 1..100
    "HP" -> amount in 1..999
    else -> false
  }
}

private fun finishItemUse(
  originalState: GameState,
  inventoryResult: ExecutionResult,
  command: ItemCommand,
  effects: Set<String>
): ExecutionResult {
  if (!inventoryResult.applied || effects.isEmpty()) return inventoryResult
  var current = inventoryResult.state
  val events = inventoryResult.events.toMutableList()
  effects.forEachIndexed { index, effect ->
    val applied = applyItemEffect(
      state = current,
      actorId = command.actorId,
      effect = effect,
      quantity = command.quantity,
      command = command,
      effectIndex = index
    )
    if (!applied.applied) return ExecutionResult(originalState, false, validation = applied.validation)
    current = applied.state
    events += applied.events
  }
  return inventoryResult.copy(state = current, events = events)
}

private fun applyItemEffect(
  state: GameState,
  actorId: String,
  effect: String,
  quantity: Int,
  command: ItemCommand,
  effectIndex: Int
): ExecutionResult {
  val character = state.characters[actorId] ?: return invalid(state, "target_unknown")
  if (character.presence == CharacterPresence.DEAD) return invalid(state, "item_effect_target_dead")

  if (effect == "WATER" || effect == "FOOD") {
    val operation = if (effect == "WATER") {
      PhysiologyCommand.Operation.RECORD_WATER
    } else {
      PhysiologyCommand.Operation.RECORD_FOOD
    }
    return PhysiologyEngine.execute(
      state,
      PhysiologyCommand(
        commandId = "${command.commandId}:EFFECT:$effectIndex",
        turnId = command.turnId,
        actorId = actorId,
        targetId = actorId,
        source = CommandSource.SYSTEM,
        operation = operation
      )
    )
  }

  additiveItemEffect.matchEntire(effect)?.let { match ->
    val kind = match.groupValues[1]
    val perUse = match.groupValues[2].toInt()
    val amount = if (kind == "HP") perUse * quantity else (perUse * quantity).coerceAtMost(100)
    return when (kind) {
      "FOOD" -> {
        val physiology = character.physiology.copy(
          minutesSinceFood = PhysiologyStatusPolicy.recoverFood(character.physiology.minutesSinceFood, amount)
        )
        changed(
          state.copy(characters = state.characters + (actorId to character.copy(physiology = physiology))),
          "item_effect_food:$amount"
        )
      }
      "WATER" -> {
        val physiology = character.physiology.copy(
          minutesSinceWater = PhysiologyStatusPolicy.recoverWater(character.physiology.minutesSinceWater, amount)
        )
        changed(
          state.copy(characters = state.characters + (actorId to character.copy(physiology = physiology))),
          "item_effect_water:$amount"
        )
      }
      "REST" -> {
        val physiology = character.physiology.copy(
          minutesAwake = PhysiologyStatusPolicy.recoverRest(character.physiology.minutesAwake, amount)
        )
        changed(
          state.copy(characters = state.characters + (actorId to character.copy(physiology = physiology))),
          "item_effect_rest:$amount"
        )
      }
      "HP" -> {
        val stats = CombatProgression.read(character)
        val healed = stats.copy(currentHp = (stats.currentHp + amount).coerceAtMost(stats.maxHp))
        val updated = CombatProgression.write(character, healed)
        changed(state.copy(characters = state.characters + (actorId to updated)), "item_effect_hp:$amount")
      }
      else -> invalid(state, "item_effect_invalid")
    }
  }

  return when (effect) {
    "CLEAR_BLEED" -> clearBleeding(state, character)
    "CLEAR_MILD_SICKNESS" -> clearMildSickness(state, character)
    else -> invalid(state, "item_effect_invalid")
  }
}

private fun clearBleeding(state: GameState, character: CharacterState): ExecutionResult {
  val bleedStatusIds = character.statusIds.filter { statusId ->
    val status = state.statuses[statusId]
    val key = listOfNotNull(statusId, status?.type).joinToString(" ").lowercase()
    key.contains("bleed") || key.contains("chảy máu") || key.contains("chay mau")
  }.toSet()
  val remainingStatuses = state.statuses - bleedStatusIds
  val remainingInjuries = character.injuries.filterNot { injury ->
    val key = injury.lowercase()
    key.contains("bleed") || key.contains("chảy máu") || key.contains("chay mau")
  }
  val updated = character.copy(
    statusIds = character.statusIds - bleedStatusIds,
    injuries = remainingInjuries
  )
  return changed(
    state.copy(statuses = remainingStatuses, characters = state.characters + (character.id to updated)),
    "item_effect_bleed_cleared"
  )
}

private fun clearMildSickness(state: GameState, character: CharacterState): ExecutionResult {
  fun isMild(value: String?): Boolean {
    val normalized = value?.trim()?.lowercase() ?: return false
    return normalized in setOf("mild", "light", "minor", "nhẹ", "nhe")
  }

  val removableStatusIds = character.statusIds.filter { statusId ->
    val status = state.statuses[statusId] ?: return@filter false
    val key = "$statusId ${status.type}".lowercase()
    val sickness = key.contains("sickness") || key.contains("infection") || key.contains("illness")
    sickness && isMild(status.metadata["severity"])
  }.toSet()
  val physiology = character.physiology.copy(
    infectionState = if (isMild(character.physiology.infectionState)) null else character.physiology.infectionState
  )
  val updated = character.copy(
    physiology = physiology,
    statusIds = character.statusIds - removableStatusIds
  )
  return changed(
    state.copy(
      statuses = state.statuses - removableStatusIds,
      characters = state.characters + (character.id to updated)
    ),
    "item_effect_mild_sickness_cleared"
  )
}

private fun useItem(state: GameState, source: InventoryState, command: ItemCommand): ExecutionResult {
  val ownedRaw = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
  if (ownedRaw.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
  val owned = ItemContentRules.normalize(ownedRaw)
  val effects = parseItemEffects(owned) ?: return invalid(state, "item_effect_invalid")
  if (owned.contentState == ContentState.EMPTY) return invalid(state, "item_content_empty")
  if (owned.contentState == ContentState.FULL || owned.contentState == ContentState.LOW) {
    val nextVariant = ItemContentRules.nextAfterUse(owned) ?: return invalid(state, "item_content_empty")
    var nextInventory = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    val validation = InventoryPolicy.validateAddition(state, command.actorId, nextInventory, nextVariant, command.quantity)
    if (validation != null) return invalid(state, validation)
    nextInventory = addItem(nextInventory, nextVariant.copy(quantity = command.quantity))
    val inventoryResult = changed(
      state.copy(inventories = state.inventories + (command.actorId to nextInventory)),
      if (nextVariant.contentState == ContentState.EMPTY) "item_content_emptied" else "item_content_reduced"
    )
    return finishItemUse(state, inventoryResult, command, effects)
  }
  val catalogConsumable = owned.metadata["catalog.category"].equals("CONSUMABLE", true)
  val consumedOnUse = catalogConsumable || owned.metadata["consumedOnUse"].equals("true", true) ||
    (owned.metadata["consumable"].equals("true", true) && !owned.metadata["containerPersistent"].equals("true", true))
  if (consumedOnUse) {
    val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    val inventoryResult = changed(state.copy(inventories = state.inventories + (command.actorId to next)), "item_consumed")
    return finishItemUse(state, inventoryResult, command, effects)
  }
  return finishItemUse(state, changed(state, "item_used"), command, effects)
}

object InventoryEngine {
  fun execute(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    if (ItemContentRules.hasForbiddenPreciseAmount(command.itemName)) return invalid(state, "precise_content_amount_forbidden")
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val item = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))
    return when (command.operation) {
      ItemCommand.Operation.PICKUP -> {
        val validation = InventoryPolicy.validateAddition(state, command.actorId, source, item, command.quantity)
        if (validation != null) return invalid(state, validation)
        changed(state.copy(inventories = state.inventories + (command.actorId to addItem(source, item))), "inventory_pickup")
      }
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
        if (command.actorId == KAI_ID && InventoryPolicy.isKaiSignatureEquipment(state, owned)) return invalid(state, "signature_equipment_locked")
        val transferred = ItemContentRules.normalize(owned).copy(quantity = command.quantity)
        val targetInventory = state.inventories[targetId] ?: InventoryState(targetId)
        val validation = InventoryPolicy.validateAddition(state, targetId, targetInventory, transferred, command.quantity)
        if (validation != null) return invalid(state, validation)
        val from = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val to = addItem(targetInventory, transferred)
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
  fun execute(state: GameState, command: PartyCommand): ExecutionResult = when (command.operation) {
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

object StatusEngine {
  fun execute(state: GameState, command: StatusCommand): ExecutionResult {
    if (!state.characters.containsKey(command.targetId)) return invalid(state, "target_unknown")
    return when (command.operation) {
      StatusCommand.Operation.APPLY -> {
        val effect = command.effect ?: return invalid(state, "status_effect_required")
        if (effect.id in state.statuses) return invalid(state, "status_already_exists")
        val character = state.characters.getValue(command.targetId)
        changed(state.copy(statuses = state.statuses + (effect.id to effect), characters = state.characters + (command.targetId to character.copy(statusIds = character.statusIds + effect.id))), "status_applied")
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

object TimeEngine {
  fun execute(state: GameState, command: TimeAdvanceCommand): ExecutionResult {
    if (command.minutes <= 0) return invalid(state, "time_minutes_must_be_positive")
    val reason = command.reason.trim()
    if (reason.isEmpty()) return invalid(state, "time_reason_required")
    val delta = command.minutes.toLong()
    val elapsed = state.time.elapsedSubjectiveMinutes
    if (elapsed > Long.MAX_VALUE - delta) return invalid(state, "time_overflow")

    val nextCharacters = linkedMapOf<String, CharacterState>()
    state.characters.forEach { (id, character) ->
      if (character.presence == CharacterPresence.DEAD) {
        nextCharacters[id] = character
        return@forEach
      }
      val physiology = character.physiology
      val food = advanceKnownCounter(physiology.minutesSinceFood, delta) ?: if (physiology.minutesSinceFood != null) return invalid(state, "physiology_time_overflow") else null
      val water = advanceKnownCounter(physiology.minutesSinceWater, delta) ?: if (physiology.minutesSinceWater != null) return invalid(state, "physiology_time_overflow") else null
      val awake = advanceKnownCounter(physiology.minutesAwake, delta) ?: if (physiology.minutesAwake != null) return invalid(state, "physiology_time_overflow") else null
      nextCharacters[id] = character.copy(physiology = physiology.copy(
        minutesSinceFood = food,
        minutesSinceWater = water,
        minutesAwake = awake
      ))
    }

    val nextTime = state.time.copy(
      elapsedSubjectiveMinutes = elapsed + delta,
      lastAdvanceMinutes = command.minutes,
      lastAdvanceReason = reason
    )
    return changed(state.copy(time = nextTime, characters = nextCharacters), "time_advanced")
  }

  private fun advanceKnownCounter(value: Long?, delta: Long): Long? {
    if (value == null) return null
    if (value < 0L || value > Long.MAX_VALUE - delta) return null
    return value + delta
  }
}

internal fun invalid(state: GameState, reason: String) = ExecutionResult(state, false, validation = ValidationResult(false, reason))
internal fun changed(state: GameState, event: String) = ExecutionResult(state, true, events = listOf(event))
