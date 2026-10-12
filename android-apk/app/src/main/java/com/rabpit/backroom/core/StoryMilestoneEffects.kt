package com.rabpit.backroom.core

/**
 * Commit typed milestone consequences through existing Character HP/Status Core.
 * Neither the GM reply nor the client may write these effects directly.
 */
object StoryMilestoneEffects {
  fun apply(
    state: GameState, checkpointId: String, events: List<StoryMilestonePlan.Event>
  ): GameState {
    var next = state
    for (event in events) {
      for ((index, injury) in event.injuries.withIndex()) {
        val target = next.characters[injury.characterId]
          ?: throw IllegalArgumentException("milestone_injury_target_unknown")
        require(target.presence == CharacterPresence.ACTIVE &&
          target.id in next.party.memberIds && target.vitalState.currentHp > 0) {
          "milestone_injury_target_not_present"
        }

        val statusId = "milestone:$checkpointId:${event.id}:$index"
        val command = StatusCommand(
          commandId = "MILESTONE:$checkpointId:${event.id}:INJURY:$index",
          turnId = null,
          actorId = CAO_MINH_ID,
          targetId = target.id,
          source = CommandSource.SYSTEM,
          operation = StatusCommand.Operation.APPLY,
          effect = StatusEffect(
            id = statusId,
            type = "INJURY",
            source = "MILESTONE:$checkpointId:${event.id}",
            startTurnId = next.turn.currentTurnId,
            persistent = true,
            metadata = mapOf("description" to injury.label)
          )
        )
        val result = StateReducer.execute(next, command)
        require(result.applied && !result.duplicate) {
          "milestone_injury_commit_failed:${result.validation.reason.orEmpty()}"
        }
        // A planned injury is non-fatal; death requires a separately
        // authored and validated Core mechanic, never implicit HP underflow.
        val hp = (target.vitalState.currentHp - injury.hpLoss).coerceAtLeast(1)
        val damaged = CharacterStatEngine.setCurrentHp(result.state, target.id, hp)
        val updated = requireNotNull(damaged.characters[target.id])
        next = damaged.copy(characters = damaged.characters + (target.id to updated.copy(
          injuries = updated.injuries + injury.label
        )))
      }
    }
    return next
  }
}
