package com.rabpit.backroom.core

private fun addItem(inventory: InventoryState, rawItem: ItemStack): InventoryState {
  val item = ItemContentRules.normalize(rawItem)
  val old = inventory.items[item.itemId]?.let(ItemContentRules::normalize)
  val merged = when {
    old == null -> item
    ItemContentRules.sameStackState(old, item) -> old.copy(quantity = old.quantity + item.quantity)
    else -> return inventory
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

private fun parseEffects(item: ItemStack): Set<String>? {
  val catalogEffects = ItemDefinitionMetadata.effects(item)
  val raw = if (catalogEffects.isNotEmpty()) catalogEffects else item.metadata["physiologyEffect"]
    ?.split(',', ';', '|')?.map { it.trim().uppercase() }?.filter(String::isNotEmpty)?.toSet().orEmpty()
  if (raw.any { !isKnownItemEffect(it) }) return null
  return raw
}

private fun isKnownItemEffect(effect: String): Boolean {
  if (effect in setOf("WATER", "FOOD", "CLEAR_BLEED", "CLEAR_MILD_SICKNESS")) return true
  val match = additiveItemEffect.matchEntire(effect) ?: return false
  val amount = match.groupValues[2].toIntOrNull() ?: return false
  return when (match.groupValues[1]) {
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
    val result = applyItemEffect(current, command, effect, index)
    if (!result.applied) return ExecutionResult(originalState, false, validation = result.validation)
    current = result.state
    events += result.events
  }
  return inventoryResult.copy(state = current, events = events)
}

private fun applyItemEffect(
  state: GameState,
  command: ItemCommand,
  effect: String,
  effectIndex: Int
): ExecutionResult {
  val character = state.characters[command.actorId] ?: return invalid(state, "target_unknown")
  if (character.presence == CharacterPresence.DEAD) return invalid(state, "item_effect_target_dead")

  if (effect == "WATER" || effect == "FOOD") {
    val operation = if (effect == "WATER") {
      PhysiologyCommand.Operation.RECORD_WATER
    } else {
      PhysiologyCommand.Operation.RECORD_FOOD
    }
    return PhysiologyEngine.execute(current = state, command = PhysiologyCommand(
      commandId = "${command.commandId}:EFFECT:$effectIndex",
      turnId = command.turnId,
      actorId = command.actorId,
      targetId = command.actorId,
      source = CommandSource.SYSTEM,
      operation = operation
    ))
  }

  additiveItemEffect.matchEntire(effect)?.let { match ->
    val kind = match.groupValues[1]
    val perUse = match.groupValues[2].toInt()
    val amount = if (kind == "HP") perUse * command.quantity else (perUse * command.quantity).coerceAtMost(100)
    return when (kind) {
      "FOOD" -> {
        val nextPhysiology = character.physiology.copy(
          minutesSinceFood = PhysiologyStatusPolicy.recoverFood(character.physiology.minutesSinceFood, amount)
        )
        changed(
          state.copy(characters = state.characters + (character.id to character.copy(physiology = nextPhysiology))),
          "item_effect_food:$amount"
        )
      }
      "WATER" -> {
        val nextPhysiology = character.physiology.copy(
          minutesSinceWater = PhysiologyStatusPolicy.recoverWater(character.physiology.minutesSinceWater, amount)
        )
        changed(
          state.copy(characters = state.characters + (character.id to character.copy(physiology = nextPhysiology))),
          "item_effect_water:$amount"
        )
      }
      "REST" -> {
        val nextPhysiology = character.physiology.copy(
          minutesAwake = PhysiologyStatusPolicy.recoverRest(character.physiology.minutesAwake, amount)
        )
        changed(
          state.copy(characters = state.characters + (character.id to character.copy(physiology = nextPhysiology))),
          "item_effect_rest:$amount"
        )
      }
      "HP" -> {
        val stats = CombatProgression.read(character)
        val nextStats = stats.copy(currentHp = (stats.currentHp + amount).coerceAtMost(stats.maxHp))
        val updated = CombatProgression.write(character, nextStats)
        changed(state.copy(characters = state.characters + (character.id to updated)), "item_effect_hp:$amount")
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
  val remainingInjuries = character.injuries.filterNot { injury ->
    val key = injury.lowercase()
    key.contains("bleed") || key.contains("chảy máu") || key.contains("chay mau")
  }
  val updated = character.copy(
    statusIds = character.statusIds - bleedStatusIds,
    injuries = remainingInjuries
  )
  return changed(
    state.copy(statuses = state.statuses - bleedStatusIds, characters = state.characters + (character.id to updated)),
    "item_effect_bleed_cleared"
  )
}

private fun clearMildSickness(state: GameState, character: CharacterState): ExecutionResult {
  fun mild(value: String?): Boolean {
    val normalized = value?.trim()?.lowercase() ?: return false
    return normalized in setOf("mild", "light", "minor", "nhẹ", "nhe")
  }

  val removableStatusIds = character.statusIds.filter { statusId ->
    val status = state.statuses[statusId] ?: return@filter false
    val key = "$statusId ${status.type}".lowercase()
    val sickness = key.contains("sickness") || key.contains("infection") || key.contains("illness")
    sickness && mild(status.metadata["severity"])
  }.toSet()
  val nextPhysiology = character.physiology.copy(
    infectionState = if (mild(character.physiology.infectionState)) null else character.physiology.infectionState
  )
  val updated = character.copy(
    physiology = nextPhysiology,
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
  val effects = parseEffects(owned) ?: return invalid(state, "item_effect_invalid")
  if (owned.contentState == ContentState.EMPTY) return invalid(state, "item_content_empty")

  if (owned.contentState == ContentState.FULL || owned.contentState == ContentState.LOW) {
    val nextVariant = ItemContentRules.nextAfterUse(owned) ?: return invalid(state, "item_content_empty")
    var nextInventory = removeItem(source, command.itemId, command.quantity)
      ?: return invalid(state, "insufficient_item_quantity")
    val validation = InventoryPolicy.validateAddition(state, command.actorId, nextInventory, nextVariant, command.quantity)
    if (validation != null) return invalid(state, validation)
    nextInventory = addItem(nextInventory, nextVariant.copy(quantity = command.quantity))
    val event = if (nextVariant.contentState == ContentState.EMPTY) "item_content_emptied" else "item_content_reduced"
    return finishItemUse(
      state,
      changed(state.copy(inventories = state.inventories + (command.actorId to nextInventory)), event),
      command,
      effects
    )
  }

  val catalogConsumable = owned.metadata["catalog.category"].equals("CONSUMABLE", true)
  val consumedOnUse = catalogConsumable || owned.metadata["consumedOnUse"].equals("true", true) ||
    (owned.metadata["consumable"].equals("true", true) && !owned.metadata["containerPersistent"].equals("true", true))
  if (consumedOnUse) {
    val next = removeItem(source, command.itemId, command.quantity)
      ?: return invalid(state, "insufficient_item_quantity")
    return finishItemUse(
      state,
      changed(state.copy(inventories = state.inventories + (command.actorId to next)), "item_consumed"),
      command,
      effects
    )
  }
  return finishItemUse(state, changed(state, "item_used"), command, effects)
}

object InventoryEngine {
  fun execute(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    return when (command.operation) {
      ItemCommand.Operation.DISCARD -> {
        val owned = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
        if (owned.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
        if (!ItemDefinitionMetadata.discardable(owned)) return invalid(state, "item_not_discardable")
        if (InventoryPolicy.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped")
        if (command.actorId == KAI_ID && InventoryPolicy.isKaiSignatureEquipment(state, owned)) return invalid(state, "signature_equipment_locked")
        val next = removeItem(source, command.itemId, command.quantity)
          ?: return invalid(state, "insufficient_item_quantity")
        changed(state.copy(inventories = state.inventories + (command.actorId to next)), "item_discarded")
      }
      ItemCommand.Operation.USE -> useItem(state, source, command)
      ItemCommand.Operation.TRANSFER -> transfer(state, command)
      ItemCommand.Operation.EQUIP -> {
        if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
        val owned = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
        if (owned.quantity < 1) return invalid(state, "item_not_owned")
        val slot = command.slot ?: ItemDefinitionMetadata.equipmentSlot(owned) ?: "weapon"
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
      }
      ItemCommand.Operation.UNEQUIP -> {
        if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
        val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
        val slot = command.slot ?: equipment.slots.entries.firstOrNull { it.value == command.itemId }?.key
          ?: return invalid(state, "item_not_equipped")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        if (KaiStartingEquipment.isSignature(command.itemId, command.itemName)) return invalid(state, "signature_equipment_locked")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot)), "item_unequipped")
      }
    }
  }

  fun transfer(state: GameState, command: ItemCommand): ExecutionResult {
    val targetId = command.targetId ?: return invalid(state, "target_required")
    if (targetId == command.actorId) return invalid(state, "same_inventory_transfer")
    if (!state.characters.containsKey(targetId)) return invalid(state, "target_unknown")
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val owned = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
    if (!ItemDefinitionMetadata.transferable(owned)) return invalid(state, "item_not_transferable")
    if (InventoryPolicy.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped")
    if (command.actorId == KAI_ID && InventoryPolicy.isKaiSignatureEquipment(state, owned)) return invalid(state, "signature_equipment_locked")

    val targetInventory = state.inventories[targetId] ?: InventoryState(targetId)
    val transferred = ItemContentRules.normalize(owned).copy(quantity = command.quantity)
    val validation = InventoryPolicy.validateAddition(state, targetId, targetInventory, transferred, command.quantity)
    if (validation != null) return invalid(state, validation)
    val from = removeItem(source, command.itemId, command.quantity)
      ?: return invalid(state, "insufficient_item_quantity")
    val to = addItem(targetInventory, transferred)
    return changed(state.copy(inventories = state.inventories + (command.actorId to from) + (targetId to to)), "inventory_transfer")
  }
}

object GiveAndUseEngine {
  fun execute(state: GameState, command: GiveAndUseItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    val transfer = InventoryEngine.transfer(state, ItemCommand(
      commandId = "${command.commandId}:TRANSFER",
      turnId = command.turnId,
      actorId = command.actorId,
      targetId = command.targetId,
      source = command.source,
      operation = ItemCommand.Operation.TRANSFER,
      itemId = command.itemId,
      itemName = command.itemName,
      quantity = command.quantity
    ))
    if (!transfer.applied) return ExecutionResult(state, false, validation = transfer.validation)
    val use = InventoryEngine.execute(transfer.state, ItemCommand(
      commandId = "${command.commandId}:USE",
      turnId = command.turnId,
      actorId = command.targetId,
      source = command.source,
      operation = ItemCommand.Operation.USE,
      itemId = command.itemId,
      itemName = command.itemName,
      quantity = command.quantity
    ))
    if (!use.applied) return ExecutionResult(state, false, validation = use.validation)
    return use.copy(events = transfer.events + use.events)
  }
}

object LootGrantEngine {
  fun execute(state: GameState, command: LootGrantCommand): ExecutionResult {
    if (command.source != CommandSource.SYSTEM) return invalid(state, "loot_source_not_authoritative")
    if (command.quantity <= 0 || command.item.quantity != command.quantity) return invalid(state, "loot_quantity_invalid")
    if (command.item.metadata["loot.origin"] != command.origin.name) return invalid(state, "loot_origin_mismatch")
    if (command.item.metadata["loot.sourceId"] != command.sourceId) return invalid(state, "loot_source_mismatch")
    val processedKey = "loot.processed.${command.sourceId}"
    if (state.metadata.containsKey(processedKey)) return ExecutionResult(state, applied = false, duplicate = true)
    val inventory = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val item = ItemContentRules.normalize(command.item.copy(quantity = command.quantity))
    val validation = InventoryPolicy.validateAddition(state, command.actorId, inventory, item, command.quantity)
    if (validation != null) {
      return changed(state.copy(metadata = state.metadata + (processedKey to "lost:$validation")), "loot_lost_inventory_capacity")
    }
    val next = addItem(inventory, item)
    return changed(
      state.copy(
        inventories = state.inventories + (command.actorId to next),
        metadata = state.metadata + (processedKey to item.itemId)
      ),
      "loot_granted:${command.origin.name}"
    )
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
      if (command.targetId == AN_NHIEN_ID) return invalid(state, "an_nhien_follower_locked")
      if (command.targetId == state.party.leaderId) return invalid(state, "cannot_remove_leader")
      if (command.targetId !in state.party.memberIds) return invalid(state, "not_in_party")
      changed(state.copy(party = state.party.copy(memberIds = state.party.memberIds - command.targetId)), "party_member_removed")
    }
    PartyCommand.Operation.SET_LEADER -> {
      if (command.targetId == AN_NHIEN_ID) return invalid(state, "an_nhien_cannot_lead")
      if (command.targetId !in state.party.memberIds) return invalid(state, "leader_not_in_party")
      changed(state.copy(party = state.party.copy(leaderId = command.targetId)), "party_leader_changed")
    }
    PartyCommand.Operation.SEPARATE -> {
      if (command.targetId == AN_NHIEN_ID) return invalid(state, "an_nhien_follower_locked")
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
      nextCharacters[id] = character.copy(physiology = physiology.copy(minutesSinceFood = food, minutesSinceWater = water, minutesAwake = awake))
    }
    return changed(state.copy(
      time = state.time.copy(elapsedSubjectiveMinutes = elapsed + delta, lastAdvanceMinutes = command.minutes, lastAdvanceReason = reason),
      characters = nextCharacters
    ), "time_advanced")
  }

  private fun advanceKnownCounter(value: Long?, delta: Long): Long? {
    if (value == null) return null
    if (value < 0L || value > Long.MAX_VALUE - delta) return null
    return value + delta
  }
}

internal fun invalid(state: GameState, reason: String) = ExecutionResult(state, false, validation = ValidationResult(false, reason))
internal fun changed(state: GameState, event: String) = ExecutionResult(state, true, events = listOf(event))
