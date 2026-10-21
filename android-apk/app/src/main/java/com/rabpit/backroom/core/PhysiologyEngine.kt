package com.rabpit.backroom.core

object PhysiologyEngine {
  fun execute(state: GameState, command: PhysiologyCommand): ExecutionResult {
    val character = state.characters[command.targetId] ?: return invalid(state, "target_unknown")
    if (character.presence == CharacterPresence.DEAD) return invalid(state, "physiology_target_dead")

    val current = character.physiology
    val recovery = command.recoveryMinutes
    if (recovery != null && (recovery <= 0L || recovery > 10080L * 100L))
      return invalid(state, "physiology_recovery_invalid")
    val next = when (command.operation) {
      PhysiologyCommand.Operation.RECORD_FOOD -> current.copy(
        minutesSinceFood = current.minutesSinceFood?.let { (it - minOf(it, recovery ?: 720L)) })
      PhysiologyCommand.Operation.RECORD_WATER -> current.copy(
        minutesSinceWater = current.minutesSinceWater?.let { (it - minOf(it, recovery ?: 360L)) })
      PhysiologyCommand.Operation.RECORD_SLEEP -> current.copy(
        minutesAwake = current.minutesAwake?.let { (it - minOf(it, recovery ?: 60L)) })
      PhysiologyCommand.Operation.UPDATE_CONDITION -> {
        val pain = normalizedCondition(command.painState) ?: command.painState?.let { return invalid(state, "physiology_condition_blank") }
        val infection = normalizedCondition(command.infectionState) ?: command.infectionState?.let { return invalid(state, "physiology_condition_blank") }
        val thermal = normalizedCondition(command.thermalState) ?: command.thermalState?.let { return invalid(state, "physiology_condition_blank") }
        if (pain == null && infection == null && thermal == null) return invalid(state, "physiology_condition_required")
        current.copy(
          painState = pain ?: current.painState,
          infectionState = infection ?: current.infectionState,
          thermalState = thermal ?: current.thermalState
        )
      }
    }

    val event = when (command.operation) {
      PhysiologyCommand.Operation.RECORD_FOOD -> "physiology_food_recorded"
      PhysiologyCommand.Operation.RECORD_WATER -> "physiology_water_recorded"
      PhysiologyCommand.Operation.RECORD_SLEEP -> "physiology_sleep_recorded"
      PhysiologyCommand.Operation.UPDATE_CONDITION -> "physiology_condition_updated"
    }
    val updated = character.copy(physiology = next)
    return changed(state.copy(characters = state.characters + (character.id to updated)), event)
  }

  private fun normalizedCondition(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
