package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.CharacterPresence
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Fact
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.NativeFacts
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Scope

/**
 * M1b.2 native perception adapter (issue #499).
 *
 * Pure function: (decoded GameState snapshot, event Scope) -> NativeFacts.
 * No I/O, no RNG, no storage writes, no provider calls, no UI.
 *
 * The current snapshot has party/presence but no event-bound scene membership,
 * consciousness, visibility, hearing or reach producer. ACTIVE is not sensory
 * evidence. Known absence/death can deny; all positive facts remain UNKNOWN.
 * Observation creation stays disabled until the native producer qualifies.
 */
internal object NativePerceptionAdapter {
  /** Effective post-chain protagonist id. Raw "kai" is PRE-CHAIN/RETIRED. */
  const val PROTAGONIST_ID = "cao_minh"

  /** State's current scene key: worldNodeId > journeyStopKey > location. */
  fun sceneKeyOf(state: GameState): String? =
    state.world["worldNodeId"]?.takeIf { it.isNotBlank() }
      ?: state.world["journeyStopKey"]?.takeIf { it.isNotBlank() }
      ?: state.world["location"]?.takeIf { it.isNotBlank() }

  /**
   * Narrow native witness: Cao Minh knows he completed HIS OWN action.
   * This NEVER confers visibility of an item, enemy, other actor, exit or
   * previously UNKNOWN fact. External scene perception still uses perceive()
   * and remains UNKNOWN without a qualified sensor.
   *
   * Must be called only with a deterministic Core stage that is replayed
   * under the writer transaction; generated actor prose is not evidence.
   */
  fun perceiveOwnCompletedAction(
    before: GameState, after: GameState, scope: Scope, actorId: String
  ): NativeFacts {
    val unknown = NativeFacts(scope,actorId,Fact.UNKNOWN,Fact.UNKNOWN,
      Fact.UNKNOWN,Fact.UNKNOWN,Fact.UNKNOWN)
    val actor = before.characters[actorId] ?: return unknown
    val later = after.characters[actorId] ?: return unknown
    if (actorId != PROTAGONIST_ID ||
        actor.presence != CharacterPresence.ACTIVE ||
        later.presence != CharacterPresence.ACTIVE ||
        actorId !in before.party.memberIds ||
        actorId !in after.party.memberIds ||
        scope.turnId !in after.turn.completedTurnIds ||
        scope.revision <= 0L ||
        after.metadata["lastAction.turnId"] != scope.turnId ||
        after.metadata["lastAction.actorId"] != actorId ||
        after.metadata["lastAction.phase"] != "COMPLETED" ||
        after.metadata["lastAction.elapsedMinutes"]?.toIntOrNull()?.let { it>0 } != true ||
        before.world["journeyStopKey"] != scope.sceneId)
      return unknown
    // The event is the actor's OWN completed action, not any sensory claim
    // about the environment. SEEN here denotes first-person witnessed action.
    return NativeFacts(scope,actorId,Fact.YES,Fact.YES,Fact.YES,Fact.YES,Fact.UNKNOWN)
  }

  fun perceive(state: GameState, scope: Scope, actorId: String): NativeFacts {
    val actor = state.characters[actorId] ?: return NativeFacts(
      scope, actorId, Fact.UNKNOWN, Fact.UNKNOWN, Fact.UNKNOWN, Fact.UNKNOWN, Fact.UNKNOWN)
    val absent = actor.presence in setOf(CharacterPresence.DEAD,
      CharacterPresence.MISSING, CharacterPresence.SEPARATED)
    return NativeFacts(scope, actorId,
      if (absent) Fact.NO else Fact.UNKNOWN,
      Fact.UNKNOWN,
      if (actor.presence == CharacterPresence.DEAD) Fact.NO else Fact.UNKNOWN,
      Fact.UNKNOWN, Fact.UNKNOWN)
  }
}
