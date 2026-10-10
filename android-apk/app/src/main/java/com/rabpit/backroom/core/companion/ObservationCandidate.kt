package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Channel
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Eligible
import org.json.JSONObject

/**
 * M1b.2 immutable observation candidate (issue #499).
 *
 * Built ONLY from an Eligible (policy #495) plus the public projection (#498 §2b).
 * TOLD is not produced here (no native communication event type exists yet — P-C
 * unimplemented). INFERRED is not enabled in v1.
 *
 * Certainty is PLAUSIBLE for all v1 adapter observations: the derivation is
 * deterministic from authoritative Core state but at scene granularity, not direct
 * sensory proof. Certainty answers "how well was this perceived", never "is the
 * proposition true" (claim stance lives on the Claim, Rule Table V1 §2).
 */
internal data class ObservationCandidate(
  val observationId: String,
  val ownerActorId: String,
  val sourceEventId: String,
  val access: AccessKind,
  val certainty: Certainty,
  val slotId: String,
  val turnId: String,
  val revision: Long,
  val sceneId: String,
  val policyVersion: String,
  val publicPayload: JSONObject
) {
  enum class AccessKind { SEEN, HEARD }
  /** Observation certainty per Technical Design V1 SQL specimen; never UNKNOWN. */
  enum class Certainty { CERTAIN, PLAUSIBLE, UNCERTAIN }

  companion object {
    fun fromEligible(eligible: Eligible, projection: JSONObject): ObservationCandidate {
      val access = when (eligible.channel) {
        Channel.SEEN -> AccessKind.SEEN
        Channel.HEARD -> AccessKind.HEARD
      }
      val scope = eligible.scope
      // Deterministic id: same (slot, actor, event, access, revision) -> same id.
      val observationId = CompanionDigests.sha256(
        listOf(scope.slotId, eligible.actorId, scope.eventId, access.name,
          scope.revision.toString()).joinToString("|")
      ).take(32)
      return ObservationCandidate(
        observationId = observationId,
        ownerActorId = eligible.actorId,
        sourceEventId = scope.eventId,
        access = access,
        certainty = Certainty.PLAUSIBLE,
        slotId = scope.slotId,
        turnId = scope.turnId,
        revision = scope.revision,
        sceneId = scope.sceneId,
        policyVersion = eligible.policyVersion,
        publicPayload = projection
      )
    }
  }
}
