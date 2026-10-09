package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Event
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Publication

/**
 * M1b.2/M1b.3 native verifier (issue #500).
 *
 * Rebuilds expected observations from the ADAPTER + authoritative batch inputs and
 * compares them against stored/published candidates. It never accepts a caller's
 * eligibility list, hash, or JSON as authorization: the adapter re-derives facts
 * from the decoded snapshot, the policy re-computes eligibility, and every stored
 * row must match the rebuild exactly (identity, binding, digest).
 *
 * Pure Kotlin: no Android, no SQLite, no I/O. JVM-testable.
 */
internal object ObservationVerifier {
  data class StoredRow(
    val observationId: String,
    val ownerActorId: String,
    val sourceEventId: String,
    val accessKind: String,
    val certainty: String,
    val turnId: String,
    val revision: Long,
    val sceneId: String,
    val digest: String
  )

  data class BatchInput(
    val state: GameState,
    val scope: CompanionExposurePolicy.Scope,
    val event: Event,
    /** Canonical native event type, from the authoritative batch (not caller JSON). */
    val eventType: String,
    /** Native event payload, from the authoritative batch (not caller JSON). */
    val nativePayload: org.json.JSONObject,
    /** actorIds the authoritative batch claims as participants. */
    val actorIds: List<String>
  )

  fun verify(input: BatchInput, stored: List<StoredRow>) {
    require(input.event.publication == Publication.PERCEPTIBLE) { "verifier_private_event" }
    require(input.scope == input.event.scope) { "verifier_scope_mismatch" }
    require(stored.map { it.observationId }.toSet().size == stored.size) { "verifier_duplicate_row" }
    val projection = PublicEventProjection.project(input.eventType, input.nativePayload)
      ?: throw IllegalArgumentException("verifier_projection_denied")
    // 1. Re-derive facts from the snapshot via the adapter (never trust caller facts).
    val facts = input.actorIds.map { actorId ->
      NativePerceptionAdapter.perceive(input.state, input.scope, actorId)
    }
    // 2. Re-compute eligibility via the policy.
    val eligible = CompanionExposurePolicy.eligible(input.event, facts)
    // 3. Rebuild expected candidates (projection recomputed from the batch payload).
    val expected = eligible.map { e ->
      ObservationCandidate.fromEligible(e, projection)
    }
    // 4. Exact match: every stored row must equal the rebuild, and vice versa.
    val expectedById = expected.associateBy { it.observationId }
    val storedById = stored.associateBy { it.observationId }
    require(storedById.keys == expectedById.keys) { "verifier_identity_mismatch" }
    for ((id, exp) in expectedById) {
      val row = storedById.getValue(id)
      require(row.ownerActorId == exp.ownerActorId) { "verifier_owner_mismatch" }
      require(row.sourceEventId == exp.sourceEventId) { "verifier_event_mismatch" }
      require(row.accessKind == exp.access.name) { "verifier_access_mismatch" }
      require(row.certainty == exp.certainty.name) { "verifier_certainty_mismatch" }
      require(row.turnId == exp.turnId && row.revision == exp.revision) { "verifier_binding_mismatch" }
      require(row.sceneId == exp.sceneId) { "verifier_scene_mismatch" }
      require(row.digest == ObservationPublisherDigest.of(exp)) { "verifier_digest_mismatch" }
    }
  }
}

/** Digest canonicalization shared with the publisher (test-visible seam). */
internal object ObservationPublisherDigest {
  @JvmStatic fun of(c: ObservationCandidate): String = CompanionDigests.sha256(
    CompanionWaitCapture.canonical(org.json.JSONArray(listOf(c.slotId, c.observationId,
      c.ownerActorId, c.sourceEventId, c.access.name, c.certainty.name, c.turnId,
      c.revision, c.sceneId, c.policyVersion, c.publicPayload))))
}
