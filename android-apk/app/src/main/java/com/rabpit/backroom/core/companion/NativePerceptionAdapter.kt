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
 * v1 perception model is SCENE-GRANULAR by explicit contract (reviewed in #498):
 * - sceneMember: presence=ACTIVE + member of party + event scene == state's scene key.
 *   DEAD/MISSING/SEPARATED -> NO. Anything else -> UNKNOWN (fail closed).
 * - inReach: YES iff sceneMember=YES. v1 defines reach as scene-scoped; there is no
 *   distance model in Core, and this rule is the reviewed contract — not a guess.
 * - conscious: YES iff presence=ACTIVE (ACTIVE is Core's functioning signal; Core has
 *   no unconscious flag — inventing one from physiology counters would be guessing).
 *   NO iff DEAD. Otherwise UNKNOWN.
 * - visible / audible: YES iff sceneMember=YES && conscious=YES. v1 has no
 *   invisibility/silence model in Core; documented here, not hidden.
 *
 * UNKNOWN never becomes YES. A denied channel stays denied.
 */
internal object NativePerceptionAdapter {
  /** Effective post-chain protagonist id. Raw "kai" is PRE-CHAIN/RETIRED. */
  const val PROTAGONIST_ID = "cao_minh"

  /** State's current scene key: worldNodeId > journeyStopKey > location. */
  fun sceneKeyOf(state: GameState): String? =
    state.world["worldNodeId"]?.takeIf { it.isNotBlank() }
      ?: state.world["journeyStopKey"]?.takeIf { it.isNotBlank() }
      ?: state.world["location"]?.takeIf { it.isNotBlank() }

  fun perceive(state: GameState, scope: Scope, actorId: String): NativeFacts {
    val actor = state.characters[actorId] ?: return NativeFacts(
      scope, actorId, Fact.UNKNOWN, Fact.UNKNOWN, Fact.UNKNOWN, Fact.UNKNOWN, Fact.UNKNOWN)
    val sceneMember = when {
      actor.presence == CharacterPresence.DEAD -> Fact.NO
      actor.presence == CharacterPresence.MISSING -> Fact.NO
      actor.presence == CharacterPresence.SEPARATED -> Fact.NO
      actor.presence == CharacterPresence.ACTIVE &&
        actorId in state.party.memberIds &&
        scope.sceneId == sceneKeyOf(state) -> Fact.YES
      else -> Fact.UNKNOWN
    }
    if (sceneMember != Fact.YES) {
      val conscious = if (actor.presence == CharacterPresence.DEAD) Fact.NO else Fact.UNKNOWN
      return NativeFacts(scope, actorId, sceneMember, Fact.UNKNOWN, conscious,
        Fact.UNKNOWN, Fact.UNKNOWN)
    }
    // Scene-granular v1: present + active + in party + at the event's scene
    // implies in-reach, conscious, and perceptible. No hidden models.
    return NativeFacts(scope, actorId, Fact.YES, Fact.YES, Fact.YES, Fact.YES, Fact.YES)
  }
}
