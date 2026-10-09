package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import java.util.Collections

/**
 * S1c staging seam only: invokes the existing Core coordinator on a detached snapshot.
 * A candidate is NOT a durable commit or an approved companion batch. No save/provider/RNG calls.
 * Caller must supply native-resolved commands; lock/tape/manifest authorization is a later slice.
 */
object CompanionCoreStage {
  class Candidate internal constructor(
    val beforeSnapshot: String,
    val afterSnapshot: String,
    val turnId: String,
    commandIds: List<String>,
    events: List<String>
  ) {
    val commandIds: List<String> = Collections.unmodifiableList(ArrayList(commandIds))
    val events: List<String> = Collections.unmodifiableList(ArrayList(events))
  }
  data class Outcome(val candidate: Candidate? = null, val error: String? = null)

  fun stage(state: GameState, turnId: String, input: String, commands: List<GameCommand>): Outcome {
    if (state.saveVersion != CURRENT_SAVE_VERSION) return Outcome(error = "stage_snapshot_version")
    if (!turnId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) return Outcome(error = "stage_turn_identity")
    if (state.turn.pending != null) return Outcome(error = "stage_core_pending")
    if (turnId in state.turn.completedTurnIds) return Outcome(error = "turn_already_completed")
    if (commands.isEmpty() || commands.size > 128) return Outcome(error = "stage_command_count")
    val detachedCommands = commands.map(::copyCommand)
    val ids = detachedCommands.map { it.commandId }
    if (ids.any { !it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) }) return Outcome(error = "stage_command_identity")
    if (ids.toSet().size != ids.size) return Outcome(error = "stage_duplicate_command")
    if (ids.any { it in state.turn.executedCommandIds }) return Outcome(error = "stage_command_reused")
    if (detachedCommands.any { it.turnId != turnId }) return Outcome(error = "command_turn_mismatch")

    val before = GameStateCodec.encode(state)
    // decode() can normalize/migrate. Never silently apply that as part of staging.
    val detached = GameStateCodec.decode(before)
    if (detached != state) return Outcome(error = "stage_snapshot_not_stable")
    val pending = TurnCoordinator.createPending(detached, turnId, input)
    if (pending.error != null) return Outcome(error = pending.error)
    val result = TurnCoordinator.commit(pending.state, detachedCommands)
    if (result.error != null) return Outcome(error = result.error)
    if (result.state.turn.pending != null || turnId !in result.state.turn.completedTurnIds)
      return Outcome(error = "stage_core_incomplete")
    val after = GameStateCodec.encode(result.state)
    if (GameStateCodec.decode(after) != result.state) return Outcome(error = "stage_result_not_stable")
    // Includes coordinator-generated commands (e.g. rest), not just the caller's list.
    val executed = result.state.turn.executedCommandIds - detached.turn.executedCommandIds
    return Outcome(candidate = Candidate(before, after, turnId, executed.sorted(), result.execution?.events.orEmpty()))
  }

  // Exhaustive sealed-command copy: compiler forces review when Core gains a new command kind.
  private fun copyCommand(command: GameCommand): GameCommand = when (command) {
    is ItemCommand -> command.copy(metadata = LinkedHashMap(command.metadata))
    is StatusCommand -> command.copy(effect = command.effect?.let { it.copy(metadata = LinkedHashMap(it.metadata)) })
    is OmnivaultCommand -> command.copy()
    is PartyCommand -> command.copy()
    is TimeAdvanceCommand -> command.copy()
    is PhysiologyCommand -> command.copy()
    is QueryCommand -> command.copy()
    is ValidatedLegacyStateCommand -> command.copy()
  }
}
