package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import java.nio.charset.StandardCharsets

/**
 * S1c.2 isolated locked-decision binding gate. This gate never stages/publishes a
 * Core result, requests a roll, or authorizes UI/provider exposure.
 *
 * [persistedSnapshot] MUST be read from the companion slot store under its native
 * writer/reader verification, not supplied by a provider or a client. Passing an
 * arbitrary byte array does not establish database provenance. Full typed action
 * resolution, RNG tape completeness, and final commit are separate gated slices.
 *
 * V1 deliberately supports only canonical WAIT at the current native scene,
 * with the existing default 30-minute policy. All other intents and explicit
 * durations fail closed; the existing gameplay path is not activated here.
 */
object CompanionDecisionBinding {
  data class Result(val accepted: Boolean, val error: String? = null)
  private fun deny(reason: String) = Result(false, reason)

  fun verifyWait(
    persistedSnapshot: ByteArray,
    state: GameState,
    turn: CompanionPendingTurn,
    slotId: String,
    requestId: String,
    expectedRevision: Long,
    pinnedPolicy: String,
    exactPlayerInput: String
  ): Result {
    if (slotId != turn.slotId) return deny("slot_mismatch")
    if (requestId !in turn.requestAliases) return deny("request_alias_unknown")
    if (expectedRevision != turn.expectedRevision ||
        expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) return deny("revision_mismatch")
    val lock = turn.decision ?: return deny("decision_phase_invalid")
    val active = turn.phase == CompanionPendingTurn.Phase.DECISION_LOCKED ||
      turn.phase == CompanionPendingTurn.Phase.RESERVED ||
      (turn.phase == CompanionPendingTurn.Phase.SUSPENDED &&
        (turn.resumePhase == CompanionPendingTurn.Phase.DECISION_LOCKED ||
         turn.resumePhase == CompanionPendingTurn.Phase.RESERVED))
    if (!active) return deny("decision_phase_invalid")
    if (pinnedPolicy != lock.policyVersion) return deny("policy_mismatch")
    if (lock.actorId != "cao_minh" || lock.sceneRevision != expectedRevision)
      return deny("decision_identity_mismatch")
    if (turn.reservation != null &&
        (turn.reservation.policyVersion != pinnedPolicy ||
         turn.reservation.decisionDigest != lock.digest))
      return deny("reservation_lock_mismatch")

    val nativeRequest = try {
      CompanionPendingTurn.Request.fromPlayerInput(
        slotId, requestId, expectedRevision, "cao_minh", exactPlayerInput
      )
    } catch (_: IllegalArgumentException) {
      return deny("input_invalid")
    }
    if (nativeRequest.inputDigest != turn.inputDigest) return deny("input_mismatch")
    if (!ExitStreakEngine.hasMinimumInput(exactPlayerInput)) return deny("input_too_short")

    // The shipped Game Core uses KAI_ID, while the companion actor contract uses
    // cao_minh. Preserve that existing identity mapping: no second Core actor.
    if (state.characters[KAI_ID]?.presence != CharacterPresence.ACTIVE ||
        KAI_ID !in state.party.memberIds) return deny("actor_unavailable")
    if (state.saveVersion != CURRENT_SAVE_VERSION) return deny("snapshot_version")
    val encoded = try { GameStateCodec.encode(state) } catch (_: RuntimeException) {
      return deny("snapshot_invalid")
    }
    if (!persistedSnapshot.contentEquals(encoded.toByteArray(StandardCharsets.UTF_8)))
      return deny("snapshot_mismatch")
    val stable = try { GameStateCodec.decode(encoded) == state } catch (_: RuntimeException) { false }
    if (!stable) return deny("snapshot_not_stable")

    val target = state.world["location"]?.takeIf { it.isNotEmpty() }
      ?: return deny("scene_location_missing")
    val targetSize = target.toByteArray(StandardCharsets.UTF_8).size
    if (targetSize > 512) return deny("scene_location_unsupported")
    // Exact canonical typed envelope. A matching hash over a different, otherwise
    // valid DecisionLock payload never substitutes for this native comparison.
    val nativePayload = "companion_decision.v1|cao_minh|WAIT|" + expectedRevision +
      "|30|" + targetSize + ":" + target
    if (lock.canonicalPayload != nativePayload) return deny("decision_payload_mismatch")
    return Result(true)
  }
}
