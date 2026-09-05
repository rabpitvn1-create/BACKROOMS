package com.rabpit.backroom.core

object CommandValidator {
  fun validate(state: GameState, command: GameCommand): ValidationResult {
    if (command.commandId.isBlank()) return ValidationResult(false, "command_id_required")
    if (command.actorId !in state.characters) return ValidationResult(false, "actor_unknown")
    if (command.turnId != null && command.turnId != state.turn.currentTurnId) return ValidationResult(false, "turn_id_mismatch")
    if (command is ValidatedLegacyStateCommand && !command.validatedByGameEngine) return ValidationResult(false, "engine_validation_required")
    if (command is LootGrantCommand && command.source != CommandSource.SYSTEM) return ValidationResult(false, "loot_source_not_authoritative")

    val itemName = when (command) {
      is ItemCommand -> command.itemName
      is GiveAndUseItemCommand -> command.itemName
      is OmnivaultCommand -> command.itemName
      else -> null
    }
    if (itemName != null && ItemContentRules.hasForbiddenPreciseAmount(itemName)) {
      return ValidationResult(false, "precise_content_amount_forbidden")
    }
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
      is GiveAndUseItemCommand -> GiveAndUseEngine.execute(state, command)
      is LootGrantCommand -> LootGrantEngine.execute(state, command)
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
      is ItemCommand -> command.itemId
      is GiveAndUseItemCommand -> command.itemId
      is LootGrantCommand -> command.item.itemId
      is OmnivaultCommand -> command.itemId
      else -> null
    }
    val metadata = if (rememberedItemId == null) result.state.metadata
      else result.state.metadata + ("lastReferencedItemId" to rememberedItemId)
    return result.copy(state = result.state.copy(
      metadata = metadata,
      turn = result.state.turn.copy(executedCommandIds = result.state.turn.executedCommandIds + command.commandId)
    ))
  }

  fun executeAll(state: GameState, commands: List<GameCommand>): ExecutionResult {
    var current = state
    val events = mutableListOf<String>()
    for (command in commands) {
      val result = execute(current, command)
      if (!result.applied && !result.duplicate && command !is QueryCommand) {
        return ExecutionResult(state, false, validation = result.validation)
      }
      current = result.state
      events += result.events
    }
    return ExecutionResult(current, applied = current != state, events = events)
  }
}

private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
  pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
