package com.rabpit.backroom.core

// CharacterStatEngine.applyCompletedTurnRegen is invoked by TurnCoordinator after a completed turn.

private data class ItemTake(val inventory: InventoryState, val taken: ItemStack)

private fun addItem(inventory: InventoryState, rawItem: ItemStack): InventoryState {
  val item = ItemContentRules.normalize(rawItem)
  val old = inventory.items[item.itemId]?.let(ItemContentRules::normalize)
  val merged = if (old == null) item else {
    if (!ItemContentRules.sameStackState(old, item)) return inventory.copy(items = inventory.items + (item.itemId to item))
    ItemIdentity.merge(old, item)
  }
  return inventory.copy(items = inventory.items + (item.itemId to merged))
}

private fun takeItem(inventory: InventoryState, itemId: String, quantity: Int): ItemTake? {
  val old = inventory.items[itemId]?.let(ItemContentRules::normalize) ?: return null
  val split = ItemIdentity.split(old, quantity, "legacy:${inventory.ownerId}:${old.itemId}") ?: return null
  val items = if (split.remaining == null) inventory.items - itemId
    else inventory.items + (itemId to split.remaining)
  return ItemTake(inventory.copy(items = items), split.taken)
}

private fun removeItem(inventory: InventoryState, itemId: String, quantity: Int): InventoryState? =
  takeItem(inventory, itemId, quantity)?.inventory

private fun parsePhysiologyEffects(raw: String?): Set<String>? {
  if (raw == null) return emptySet()
  val effects = raw.split(',', ';', '|').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
  if (effects.isEmpty() || effects.any { it !in setOf("WATER", "FOOD") }) return null
  return effects.toSet()
}

private fun finishItemUse(
  originalState: GameState,
  inventoryResult: ExecutionResult,
  command: ItemCommand,
  beneficiaryId: String,
  physiologyEffects: Set<String>,
  healHp: Int
): ExecutionResult {
  if (!inventoryResult.applied) return inventoryResult
  if (physiologyEffects.isEmpty() && healHp <= 0) return inventoryResult
  var current = inventoryResult.state
  val events = inventoryResult.events.toMutableList()
  physiologyEffects.forEachIndexed { index, effect ->
    val operation = when (effect) {
      "WATER" -> PhysiologyCommand.Operation.RECORD_WATER
      "FOOD" -> PhysiologyCommand.Operation.RECORD_FOOD
      else -> return ExecutionResult(originalState, false, validation = ValidationResult(false, "physiology_effect_invalid"))
    }
    val physiology = PhysiologyEngine.execute(current, PhysiologyCommand(
      commandId = "${command.commandId}:PHYS:$index",
      turnId = command.turnId,
      actorId = beneficiaryId,
      targetId = beneficiaryId,
      source = CommandSource.SYSTEM,
      operation = operation
    ))
    if (!physiology.applied) return ExecutionResult(originalState, false, validation = physiology.validation)
    current = physiology.state
    events += physiology.events
  }
  if (healHp > 0) {
    val character = current.characters[beneficiaryId]
      ?: return ExecutionResult(originalState, false, validation = ValidationResult(false, "actor_unknown"))
    val maxHp = CharacterStatEngine.effective(current, beneficiaryId).maxHp
    val beforeHp = character.vitalState.currentHp.coerceIn(0, maxHp)
    val requested = healHp.toLong() * command.quantity.toLong()
    val nextHp = (beforeHp.toLong() + requested).coerceAtMost(maxHp.toLong()).toInt()
    current = CharacterStatEngine.setCurrentHp(current, beneficiaryId, nextHp)
    events += if (nextHp > beforeHp) "hp_healed:${nextHp - beforeHp}" else "hp_already_full"
  }
  return inventoryResult.copy(state = current, events = events)
}

private fun useItem(state: GameState, source: InventoryState, command: ItemCommand): ExecutionResult {
  val ownedRaw = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
  if (ownedRaw.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
  val owned = ItemContentRules.normalize(ownedRaw)
  val official = ItemCatalog.find(owned.itemId)
  if (official?.type == OfficialItemType.CONSUMABLE && command.quantity != 1) return invalid(state, "consumable_use_one_unit")
  if (official?.type == OfficialItemType.TOOL) {
    if (command.quantity != 1) return invalid(state, "tool_use_one_unit")
    val resource = if (owned.itemId == ItemCatalog.FLASHLIGHT) "battery" else "fuel"
    val amount = owned.metadata[resource]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val nextState = if (amount == 0) "OFF" else if (owned.metadata["state"].equals("ON", true)) "OFF" else "ON"
    val updated = owned.copy(metadata = owned.metadata + ("state" to nextState))
    val inventory = source.copy(items = source.items + (owned.itemId to updated))
    return changed(state.copy(inventories = state.inventories + (command.actorId to inventory)), "tool_${nextState.lowercase()}")
  }
  val physiologyEffects = parsePhysiologyEffects(owned.metadata["physiologyEffect"])
    ?: return invalid(state, "physiology_effect_invalid")
  val targetable = owned.metadata["healHp"]?.toIntOrNull()?.let { it > 0 } == true ||
    physiologyEffects.isNotEmpty() || owned.metadata.containsKey("statusTreatment") || owned.metadata.containsKey("conditionReduction")
  val beneficiaryId = if (targetable) command.targetId ?: command.actorId else command.actorId
  if (beneficiaryId !in state.characters) return invalid(state, "target_unknown")
  val healingAmount = 0 // OfficialItemEffects owns all healing for the 11-item catalog.
  if (healingAmount > 0) {
    val actor = state.characters[command.actorId] ?: return invalid(state, "actor_unknown")
    if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) {
      return invalid(state, "healing_target_defeated")
    }
  }
  if (owned.contentState == ContentState.EMPTY) return invalid(state, "item_content_empty")
  if (owned.contentState == ContentState.FULL || owned.contentState == ContentState.LOW) {
    val removal = takeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    val nextVariant = ItemContentRules.nextAfterUse(removal.taken) ?: return invalid(state, "item_content_empty")
    var nextInventory = removal.inventory
    val validation = InventoryPolicy.validateAddition(state, command.actorId, nextInventory, nextVariant, command.quantity)
    if (validation != null) return invalid(state, validation)
    nextInventory = addItem(nextInventory, nextVariant)
    val inventoryResult = changed(
      state.copy(inventories = state.inventories + (command.actorId to nextInventory)),
      if (nextVariant.contentState == ContentState.EMPTY) "item_content_emptied" else "item_content_reduced"
    )
    return finishItemUse(state, inventoryResult, command, beneficiaryId, physiologyEffects, healingAmount)
  }
  val consumedOnUse = owned.metadata["consumedOnUse"].equals("true", true) ||
    (owned.metadata["consumable"].equals("true", true) && !owned.metadata["containerPersistent"].equals("true", true))
  if (consumedOnUse) {
    val effected = OfficialItemEffects.apply(state, beneficiaryId, source, owned)
    if (!effected.applied) return effected
    val effectedInventory = effected.state.inventories[command.actorId] ?: source
    val next = removeItem(effectedInventory, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    val inventoryResult = changed(effected.state.copy(inventories = effected.state.inventories + (command.actorId to next)), "item_consumed")
    return finishItemUse(state, inventoryResult, command, beneficiaryId, physiologyEffects, healingAmount)
  }
  return finishItemUse(state, changed(state, "item_used"), command, beneficiaryId, physiologyEffects, healingAmount)
}

private object OfficialItemEffects {
  fun apply(state: GameState, actorId: String, inventory: InventoryState, item: ItemStack): ExecutionResult {
    val requestedHeal = item.metadata["healHp"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (requestedHeal > 0) {
      val actor = state.characters[actorId] ?: return invalid(state, "actor_unknown")
      if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) return invalid(state, "healing_target_defeated")
    }
    var next = state
    when (item.itemId) {
      ItemCatalog.BATTERY -> next = recharge(next, inventory, ItemCatalog.FLASHLIGHT, "battery") ?: return invalid(state, "flashlight_cannot_receive_battery")
      ItemCatalog.LIGHTER_FUEL -> next = recharge(next, inventory, ItemCatalog.LIGHTER, "fuel") ?: return invalid(state, "lighter_cannot_receive_fuel")
    }
    item.metadata["healHp"]?.toIntOrNull()?.takeIf { it > 0 }?.let { next = heal(next, actorId, it) }
    when (item.metadata["statusTreatment"]) {
      "BLEEDING_LIGHT" -> next = treatLightBleeding(next, actorId)
    }
    when (item.metadata["conditionReduction"]) {
      "INFECTION_50" -> next = reduceCondition(next, actorId, infection = true)
      "PAIN_50" -> next = reduceCondition(next, actorId, infection = false)
    }
    return changed(next, "official_item_effect_applied")
  }

  private fun recharge(state: GameState, inventory: InventoryState, toolId: String, resource: String): GameState? {
    val raw = inventory.items[toolId] ?: return null
    val tool = ItemContentRules.normalize(raw)
    val max = tool.metadata["${resource}Max"]?.toIntOrNull() ?: 100
    val current = tool.metadata[resource]?.toIntOrNull()?.coerceIn(0, max) ?: max
    if (current >= max) return null
    val updated = tool.copy(metadata = tool.metadata + (resource to (current + 50).coerceAtMost(max).toString()))
    return state.copy(inventories = state.inventories + (inventory.ownerId to inventory.copy(items = inventory.items + (toolId to updated))))
  }

  private fun heal(state: GameState, actorId: String, amount: Int): GameState {
    val character = state.characters[actorId] ?: return state
    val maxHp = CharacterStatEngine.effective(state, actorId).maxHp
    val currentHp = character.vitalState.currentHp.coerceIn(0, maxHp)
    if (character.presence == CharacterPresence.DEAD || currentHp <= 0) return state
    return CharacterStatEngine.setCurrentHp(state, actorId, (currentHp + amount).coerceAtMost(maxHp))
  }

  private fun treatLightBleeding(state: GameState, actorId: String): GameState {
    val actor = state.characters[actorId] ?: return state
    val bleeding = actor.statusIds.mapNotNull(state.statuses::get).firstOrNull {
      it.type.equals("BLEEDING", true) && (it.metadata["tier"]?.lowercase() in setOf(null, "light", "mild", "1"))
    } ?: return state
    return state.copy(statuses = state.statuses - bleeding.id, characters = state.characters + (actorId to actor.copy(statusIds = actor.statusIds - bleeding.id)))
  }

  private fun reduceCondition(state: GameState, actorId: String, infection: Boolean): GameState {
    val actor = state.characters[actorId] ?: return state
    val p = actor.physiology
    val current = if (infection) p.infectionState else p.painState
    val reduced = when (current?.lowercase()) {
      "critical", "severe" -> "moderate"
      "moderate" -> "mild"
      "mild", "light" -> "none"
      else -> current
    }
    val nextP = if (infection) p.copy(infectionState = reduced) else p.copy(painState = reduced)
    return state.copy(characters = state.characters + (actorId to actor.copy(physiology = nextP)))
  }
}

object InventoryEngine {
  fun execute(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    if (ItemContentRules.hasForbiddenPreciseAmount(command.itemName)) return invalid(state, "precise_content_amount_forbidden")
    val source = state.inventories[command.actorId] ?: InventoryState(command.actorId)
    val normalizedItem = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))
    val item = if (command.operation == ItemCommand.Operation.PICKUP)
      ItemIdentity.ensureOriginalInstances(normalizedItem, command.metadata["worldInstanceId"] ?: command.commandId)
    else normalizedItem
    return when (command.operation) {
      ItemCommand.Operation.PICKUP -> {
        val validation = InventoryPolicy.validateAddition(state, command.actorId, source, item, command.quantity)
        if (validation != null) return invalid(state, validation)
        changed(state.copy(inventories = state.inventories + (command.actorId to addItem(source, item))), "inventory_pickup")
      }
      ItemCommand.Operation.DROP -> {
        if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")
        val removal = takeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val withoutItem = state.copy(inventories = state.inventories + (command.actorId to removal.inventory))
        changed(ItemSystem.placeInWorld(withoutItem, removal.taken), "inventory_dropped_to_world")
      }
      ItemCommand.Operation.USE -> useItem(state, source, command)
      ItemCommand.Operation.TRANSFER -> {
        if (EquipmentEngine.isEquipped(state, command.actorId, command.itemId)) return invalid(state, "item_equipped_locked")
        val targetId = command.targetId ?: return invalid(state, "target_required")
        if (!state.characters.containsKey(targetId)) return invalid(state, "target_unknown")
        val owned = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
        if (owned.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
        if (command.actorId == KAI_ID && InventoryPolicy.isKaiSignatureEquipment(state, owned)) return invalid(state, "signature_equipment_locked")
        val removal = takeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val transferred = removal.taken
        val targetInventory = state.inventories[targetId] ?: InventoryState(targetId)
        val validation = InventoryPolicy.validateAddition(state, targetId, targetInventory, transferred, command.quantity)
        if (validation != null) return invalid(state, validation)
        val from = removal.inventory
        val to = addItem(targetInventory, transferred)
        changed(state.copy(inventories = state.inventories + (command.actorId to from) + (targetId to to)), "inventory_transfer")
      }
      ItemCommand.Operation.EQUIP -> EquipmentEngine.equip(state, command)
      ItemCommand.Operation.UNEQUIP -> EquipmentEngine.unequip(state, command)
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
      val character = state.characters.getValue(command.targetId)
      changed(
        state.copy(
          party = state.party.copy(memberIds = state.party.memberIds + command.targetId),
          characters = state.characters + (command.targetId to character.copy(presence = CharacterPresence.ACTIVE))
        ),
        "party_member_added"
      )
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
      nextCharacters[id] = character.copy(physiology = physiology.copy(
        minutesSinceFood = food,
        minutesSinceWater = water,
        minutesAwake = awake
      ))
    }

    val drainedInventories = state.inventories.mapValues { (_, inventory) ->
      inventory.copy(items = inventory.items.mapValues { (_, raw) ->
        val item = ItemContentRules.normalize(raw)
        val resource = when (item.itemId) { ItemCatalog.FLASHLIGHT -> "battery"; ItemCatalog.LIGHTER -> "fuel"; else -> null }
        if (resource == null || !item.metadata["state"].equals("ON", true)) item else {
          val current = item.metadata[resource]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
          item.copy(metadata = item.metadata + (resource to (current - command.minutes).coerceAtLeast(0).toString()))
        }
      })
    }
    val nextTime = state.time.copy(
      elapsedSubjectiveMinutes = elapsed + delta,
      lastAdvanceMinutes = command.minutes,
      lastAdvanceReason = reason
    )
    return changed(state.copy(time = nextTime, characters = nextCharacters, inventories = drainedInventories), "time_advanced")
  }

  private fun advanceKnownCounter(value: Long?, delta: Long): Long? {
    if (value == null) return null
    if (value < 0L || value > Long.MAX_VALUE - delta) return null
    return value + delta
  }
}

internal fun invalid(state: GameState, reason: String) = ExecutionResult(state, false, validation = ValidationResult(false, reason))
internal fun changed(state: GameState, event: String) = ExecutionResult(state, true, events = listOf(event))
