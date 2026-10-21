package com.rabpit.backroom.core

data class TurnResult(
  val state: GameState,
  val execution: ExecutionResult? = null,
  val error: String? = null
)

object TurnCoordinator {
  fun createPending(state: GameState, turnId: String, input: String, coreAuthorizedEncounterNarration: Boolean = false): TurnResult {
    if (CombatRuntime.pending(state) != null && !coreAuthorizedEncounterNarration)
      return TurnResult(state, error = "encounter_confirmation_required")
    if (turnId in state.turn.completedTurnIds) return TurnResult(state, error = "turn_already_completed")
    val existing = state.turn.pending
    if (existing != null) {
      return if (existing.turnId == turnId && existing.input == input) TurnResult(state)
      else TurnResult(state, error = "another_turn_pending")
    }
    val pending = PendingTurn(turnId, input)
    return TurnResult(state.copy(turn = state.turn.copy(currentTurnId = turnId, pending = pending)))
  }

  fun updatePending(state: GameState, status: PendingTurnStatus, commandIds: List<String> = emptyList(), error: String? = null): TurnResult {
    val pending = state.turn.pending ?: return TurnResult(state, error = "pending_turn_missing")
    return TurnResult(state.copy(turn = state.turn.copy(pending = pending.copy(status = status, commandIds = commandIds, error = error))))
  }

  fun commit(state: GameState, commands: List<GameCommand>): TurnResult {
    val pending = state.turn.pending ?: return TurnResult(state, error = "pending_turn_missing")
    if (pending.turnId in state.turn.completedTurnIds) return TurnResult(state, error = "turn_already_completed")
    if (commands.any { it.turnId != pending.turnId }) return TurnResult(state, error = "command_turn_mismatch")
    // A rest is a time interval, not a post-turn instant reset.
    // Direct time commands receive the same context as ActionRuntime checkpoints.
    val mode = RestActionPolicy.activity(pending.input)
    val restTargets = RestActionPolicy.targets(state, pending.input).toSet()
    val authoritativeCommands = commands.map { command ->
      if (command is TimeAdvanceCommand && mode != TimeActivity.NORMAL &&
          command.activity == TimeActivity.NORMAL) command.copy(activity = mode, restTargets = restTargets)
      else command
    }
    val executing = state.copy(turn = state.turn.copy(pending = pending.copy(
      status = PendingTurnStatus.EXECUTING,
      commandIds = authoritativeCommands.map { it.commandId }
    )))
    val execution = StateReducer.executeAll(executing, authoritativeCommands)
    if (!execution.applied && !authoritativeCommands.all { it is QueryCommand }) {
      return TurnResult(state.copy(turn = state.turn.copy(pending = pending.copy(
        status = PendingTurnStatus.FAILED,
        error = execution.validation.reason
      ))), execution, execution.validation.reason)
    }
    val completed = execution.state.copy(turn = execution.state.turn.copy(
      pending = null,
      completedTurnIds = execution.state.turn.completedTurnIds + pending.turnId
    ))
    val regenerated = CharacterStatEngine.applyCompletedTurnRegen(completed, pending.turnId)
    return TurnResult(regenerated, execution.copy(state = regenerated))
  }

  fun recover(state: GameState): PendingTurn? = state.turn.pending?.takeUnless {
    it.turnId in state.turn.completedTurnIds || it.status == PendingTurnStatus.COMMITTED
  }

  fun reject(state: GameState, reason: String): TurnResult {
    val pending = state.turn.pending ?: return TurnResult(state, error = "pending_turn_missing")
    val rejected = state.copy(turn = state.turn.copy(
      pending = null,
      completedTurnIds = state.turn.completedTurnIds + pending.turnId
    ), metadata = state.metadata + ("lastRejectedTurn" to "${pending.turnId}:$reason"))
    return TurnResult(rejected, error = reason)
  }
}
