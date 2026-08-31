package com.rabpit.backroom.core

object CommandValidator {
  fun validate(state: GameState, command: GameCommand): ValidationResult {
    if (command.commandId.isBlank()) return ValidationResult(false, "command_id_required")
    if (command.actorId !in state.characters) return ValidationResult(false, "actor_unknown")
    if (command.turnId != null && command.turnId != state.turn.currentTurnId) return ValidationResult(false, "turn_id_mismatch")
    if (command is ValidatedLegacyStateCommand && !command.validatedByGameEngine) return ValidationResult(false, "engine_validation_required")

    // Player-facing pickup commands never create ownership. Inventory acquisition is authoritative
    // only when emitted by validated story/drop progression (GEMINI) or deterministic SYSTEM code.
    if (command is ItemCommand && command.operation == ItemCommand.Operation.PICKUP &&
      command.source !in setOf(CommandSource.GEMINI, CommandSource.SYSTEM)) {
      return ValidationResult(false, "player_pickup_unavailable")
    }
    if (command is ItemCommand && command.operation == ItemCommand.Operation.PICKUP && command.source == CommandSource.GEMINI &&
      command.metadata["acquisitionSource"] !in setOf("SEARCH", "EXPLORE", "WORLD_EVENT", "ENTITY_DROP")) {
      return ValidationResult(false, "acquisition_event_required")
    }

    // Restore remains a narrative capability. It must never mutate authoritative gameplay state.
    if (command is OmnivaultCommand && command.operation == OmnivaultCommand.Operation.RESTORE) {
      return ValidationResult(false, "restore_narrative_only")
    }

    val itemName = when (command) {
      is ItemCommand -> command.itemName
      is OmnivaultCommand -> command.itemName
      else -> null
    }
    if (itemName != null && ItemContentRules.hasForbiddenPreciseAmount(itemName)) return ValidationResult(false, "precise_content_amount_forbidden")
    return ValidationResult(true)
  }
}

object StateReducer {
  fun execute(state: GameState, command: GameCommand): ExecutionResult {
    if (command.commandId in state.turn.executedCommandIds) {
      return ExecutionResult(state, applied = false, duplicate = true)
    }
    val validation = CommandValidator.validate(state, command)
    if (!validation.valid) return ExecutionResult(state, false, validation = validation)
    val result = when (command) {
      is ItemCommand -> InventoryEngine.execute(state, command)
      is OmnivaultCommand -> OmnivaultEngine.execute(state, command)
      is PartyCommand -> PartyEngine.execute(state, command)
      is StatusCommand -> StatusEngine.execute(state, command)
      is TimeAdvanceCommand -> TimeEngine.execute(state, command)
      is PhysiologyCommand -> PhysiologyEngine.execute(state, command)
      is QueryCommand -> ExecutionResult(state, applied = false)
      is ValidatedLegacyStateCommand -> {
        val worldPatch = mapOfNotNull(
          "location" to command.location,
          "title" to command.title,
          "levelJson" to command.levelJson,
          "flagsJson" to command.flagsJson
        )
        val metadataPatch = mapOfNotNull("legacyPlayerJson" to command.playerJson)
        changed(state.copy(world = state.world + worldPatch, metadata = state.metadata + metadataPatch), "validated_world_state")
      }
    }
    if (!result.applied) return result
    val rememberedItemId = when (command) {
      is ItemCommand -> rememberedItemAfter(state, result.state, command)
      is OmnivaultCommand -> command.itemId
      else -> null
    }
    val nextMetadata = if (rememberedItemId != null) result.state.metadata + ("lastReferencedItemId" to rememberedItemId) else result.state.metadata
    return result.copy(state = result.state.copy(
      metadata = nextMetadata,
      turn = result.state.turn.copy(executedCommandIds = result.state.turn.executedCommandIds + command.commandId)
    ))
  }

  private fun rememberedItemAfter(before: GameState, after: GameState, command: ItemCommand): String {
    if (command.operation == ItemCommand.Operation.PICKUP) {
      return ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata)).itemId
    }
    if (command.operation == ItemCommand.Operation.USE) {
      val old = before.inventories[command.actorId]?.items?.get(command.itemId)
      if (old != null) {
        val next = ItemContentRules.nextAfterUse(old)
        if (next != null && after.inventories[command.actorId]?.items?.containsKey(next.itemId) == true) return next.itemId
      }
    }
    return command.itemId
  }

  fun executeAll(state: GameState, commands: List<GameCommand>): ExecutionResult {
    var current = state
    val events = mutableListOf<String>()
    for (command in commands) {
      val result = execute(current, command)
      if (!result.applied && !result.duplicate) return ExecutionResult(state, false, validation = result.validation)
      current = result.state
      events += result.events
    }
    return ExecutionResult(current, applied = current != state, events = events)
  }
}

private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> = pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
