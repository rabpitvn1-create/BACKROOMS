package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.CharacterPresence
import com.rabpit.backroom.core.CharacterState
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.PartyState
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Channel
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Event
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Publication
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Scope
import com.rabpit.backroom.core.companion.ObservationVerifier.BatchInput
import com.rabpit.backroom.core.companion.ObservationVerifier.StoredRow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** M1b.3 verifier fixtures (issue #500). Rebuild-from-adapter, never trust caller. */
class ObservationVerifierTest {
  private val slot = "b".repeat(32)
  private fun scope() = Scope(slot, "turn-3", 3, "event-3-0", "node-7")
  private fun state(vararg actors: CharacterState) = GameState(
    characters = actors.associateBy { it.id },
    party = PartyState(leaderId = "cao_minh", memberIds = actors.map { it.id }),
    world = mapOf("worldNodeId" to "node-7"))
  private fun actor(id: String, p: CharacterPresence = CharacterPresence.ACTIVE) =
    CharacterState(id = id, name = id, presence = p)
  private fun payload() = JSONObject().put("actor", "cao_minh").put("minutes", 30)
    .put("location", "node-7").put("elapsedMinutes", 30)

  private fun input(vararg actors: CharacterState): BatchInput {
    val sc = scope()
    return BatchInput(state(*actors), sc,
      Event(sc, Publication.PERCEPTIBLE, setOf(Channel.SEEN)),
      "WAIT_COMPLETED", payload(), actors.map { it.id })
  }

  private fun storedRows(inp: BatchInput): List<StoredRow> {
    val facts = inp.actorIds.map { NativePerceptionAdapter.perceive(inp.state, inp.scope, it) }
    val eligible = CompanionExposurePolicy.eligible(inp.event, facts)
    return eligible.map { e ->
      val c = ObservationCandidate.fromEligible(e,
        PublicEventProjection.project(inp.eventType, inp.nativePayload)!!)
      StoredRow(c.observationId, c.ownerActorId, c.sourceEventId, c.access.name,
        c.certainty.name, c.turnId, c.revision, c.sceneId, ObservationPublisherDigest.of(c))
    }
  }

  @Test fun honestRebuildVerifies() {
    val inp = input(actor("cao_minh"))
    ObservationVerifier.verify(inp, storedRows(inp))  // no throw
  }

  @Test fun tamperedDigestRejected() {
    val inp = input(actor("cao_minh"))
    val tampered = storedRows(inp).map { it.copy(digest = "0".repeat(64)) }
    try {
      ObservationVerifier.verify(inp, tampered)
      fail("expected verifier_digest_mismatch")
    } catch (e: IllegalArgumentException) {
      assertEquals("verifier_digest_mismatch", e.message)
    }
  }

  @Test fun extraCallerRowRejected() {
    val inp = input(actor("cao_minh"))
    val rows = storedRows(inp) + storedRows(inp).first().copy(observationId = "forged")
    try {
      ObservationVerifier.verify(inp, rows)
      fail("expected verifier_identity_mismatch")
    } catch (e: IllegalArgumentException) {
      assertEquals("verifier_identity_mismatch", e.message)
    }
  }

  @Test fun callerEligibilityNotTrusted() {
    // Batch claims luc_tram participates, but she is SEPARATED: adapter denies.
    val inp = input(actor("cao_minh"), actor("luc_tram", CharacterPresence.SEPARATED))
    // A dishonest caller stores a row for luc_tram anyway.
    val honest = storedRows(inp)
    val forged = honest.first().copy(observationId = "forged-lt", ownerActorId = "luc_tram")
    try {
      ObservationVerifier.verify(inp, honest + forged)
      fail("expected verifier_identity_mismatch")
    } catch (e: IllegalArgumentException) {
      assertEquals("verifier_identity_mismatch", e.message)
    }
  }

  @Test fun privateEventRejected() {
    val sc = scope()
    val inp = BatchInput(state(actor("cao_minh")), sc,
      Event(sc, Publication.PRIVATE_GM, emptySet()),
      "WAIT_COMPLETED", payload(), listOf("cao_minh"))
    try {
      ObservationVerifier.verify(inp, emptyList())
      fail("expected verifier_private_event")
    } catch (e: IllegalArgumentException) {
      assertEquals("verifier_private_event", e.message)
    }
  }

  @Test fun deniedProjectionRejected() {
    val sc = scope()
    val inp = BatchInput(state(actor("cao_minh")), sc,
      Event(sc, Publication.PERCEPTIBLE, setOf(Channel.SEEN)),
      "UNKNOWN_TYPE", payload(), listOf("cao_minh"))
    try {
      ObservationVerifier.verify(inp, emptyList())
      fail("expected verifier_projection_denied")
    } catch (e: IllegalArgumentException) {
      assertEquals("verifier_projection_denied", e.message)
    }
  }
}
