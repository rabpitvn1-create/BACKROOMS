package com.rabpit.backroom.core

data class TurnResult(
  val state: GameState,
  val execution: ExecutionResult? = null,
  val error: String? = null
)

object TurnCoordinator {
  fun createPending(state: GameState, turnId: String, input: String): TurnResult {
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
    val executing = state.copy(turn = state.turn.copy(pending = pending.copy(
      status = PendingTurnStatus.EXECUTING,
      commandIds = commands.map { it.commandId }
    )))
    val execution = StateReducer.executeAll(executing, commands)
    if (!execution.applied && !commands.all { it is QueryCommand }) {
      return TurnResult(state.copy(turn = state.turn.copy(pending = pending.copy(
        status = PendingTurnStatus.FAILED,
        error = execution.validation.reason
      ))), execution, execution.validation.reason)
    }
    val completed = execution.state.copy(turn = execution.state.turn.copy(
      pending = null,
      completedTurnIds = execution.state.turn.completedTurnIds + pending.turnId
    ))
    return TurnResult(completed, execution.copy(state = completed))
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
