package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.ObservationCandidate.AccessKind
import com.rabpit.backroom.core.companion.ObservationCandidate.Certainty
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** M2a memory fixtures (issue #503): binding, templates, salience, corrections. */
class EpisodicMemoryTest {
  private val slot = "c".repeat(32)
  private fun candidate(owner: String = "cao_minh", rev: Long=1, payload: JSONObject = JSONObject()
    .put("actor", "cao_minh").put("minutes", 30).put("location", "node-7")) =
    ObservationCandidate(
      observationId = "obs-$rev", ownerActorId = owner, sourceEventId = "event-1",
      access = AccessKind.SEEN, certainty = Certainty.PLAUSIBLE,
      slotId = slot, turnId = "turn-$rev", revision = rev, sceneId = "node-7",
      policyVersion = "companion_exposure.v1", publicPayload = payload)

  @Test fun createsMemoryWithPinnedProvenance() {
    val m = EpisodicMemory.fromObservation(candidate(), slot, "cao_minh", "wait.rest")
    assertEquals(slot, m.slotId)
    assertEquals("cao_minh", m.ownerActorId)
    assertEquals("obs-1", m.observationId)
    assertEquals("turn-1", m.createdTurnId)
    assertEquals(1L, m.committedRevision)
    assertEquals(EpisodicMemory.Salience.ORDINARY, m.salience)
    assertEquals(EpisodicMemory.InterpretationSource.NATIVE, m.interpretationSource)
    assertEquals(EpisodicMemory.Status.ACTIVE, m.status)
    assertNull(m.supersedesMemoryId)
  }

  @Test fun summaryUsesPublicPayloadOnly() {
    val m = EpisodicMemory.fromObservation(candidate(), slot, "cao_minh", "wait.rest")
    assertTrue(m.summary.contains("cao_minh"))
    assertTrue(m.summary.contains("node-7"))
    assertTrue(m.summary.length <= EpisodicMemory.SUMMARY_MAX_CHARS)
  }

  @Test fun summaryBoundEnforced() {
    val big = JSONObject().put("actor", "x".repeat(500)).put("location", "y".repeat(500))
    val m = EpisodicMemory.fromObservation(candidate(payload = big), slot, "cao_minh", "t")
    assertTrue(m.summary.length <= EpisodicMemory.SUMMARY_MAX_CHARS)
  }

  @Test fun wrongSlotRejected() {
    try {
      EpisodicMemory.fromObservation(candidate(), "d".repeat(32), "cao_minh", "t")
      fail("expected memory_slot_mismatch")
    } catch (e: IllegalArgumentException) {
      assertEquals("memory_slot_mismatch", e.message)
    }
  }

  @Test fun wrongOwnerRejected() {
    try {
      EpisodicMemory.fromObservation(candidate(), slot, "luc_tram", "t")
      fail("expected memory_owner_mismatch")
    } catch (e: IllegalArgumentException) {
      assertEquals("memory_owner_mismatch", e.message)
    }
  }

  @Test fun correctionAppendsLinkedRecord() {
    val m = EpisodicMemory.fromObservation(candidate(), slot, "cao_minh", "t")
    val c = EpisodicMemory.correct(m,candidate(rev=2,payload=JSONObject().put("location","new-room")),"t2")
    assertEquals(m.memoryId, c.supersedesMemoryId)
    assertTrue(c.summary.contains("new-room"))
    assertEquals("obs-2",c.observationId)
    assertEquals(2L,c.committedRevision)
    assertNotEquals(m.memoryId, c.memoryId)
    // latest() resolves the chain
    val latest = EpisodicMemory.latest(listOf(m, c))
    assertEquals(listOf(c.memoryId), latest.map { it.memoryId })
  }

  @Test fun duplicateMemoryIdDeterministic() {
    val a = EpisodicMemory.fromObservation(candidate(), slot, "cao_minh", "t")
    val b = EpisodicMemory.fromObservation(candidate(), slot, "cao_minh", "t")
    assertEquals(a.memoryId, b.memoryId)  // retry-safe: same input -> same id
  }

  @Test fun schemaStatementsValid() {
    val stmts = EpisodicMemorySchema.createStatements()
    assertTrue(stmts.any { it.contains("actor_memory") })
    assertTrue(stmts.any { it.contains("immutable_memory") })
  }
}
