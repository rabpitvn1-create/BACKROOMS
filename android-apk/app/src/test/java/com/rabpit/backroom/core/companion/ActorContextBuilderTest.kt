package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.ActorContextBuilder.Input
import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.Belief
import com.rabpit.backroom.core.companion.BrainContracts.Claim
import com.rabpit.backroom.core.companion.BrainContracts.MoodState
import com.rabpit.backroom.core.companion.BrainContracts.Stance
import com.rabpit.backroom.core.companion.MemoryRetrieval.MemoryView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * C1 actor-private context builder + knowledge firewall tests (issue #509).
 *
 * Covers: ownership (brain/persona/memory), no whole-Codex paste, no writer
 * secrets, stance/provenance labels, source/version pins, budget/order/reload.
 * No provider calls.
 */
class ActorContextBuilderTest {
  private fun persona(actorId: String = "cao_minh") = CompanionPersonaFixture.load(actorId)

  private fun belief(stance: Stance = Stance.UNKNOWN) = Belief(
    beliefId = "b1",
    claim = Claim("c1", "node-7", BrainContracts.Predicates.AT_LOCATION, "safe",
      Claim.Polarity.POSITIVE, "luc_tram", listOf("obs-1")),
    stance = stance, evidenceObservationIds = listOf("obs-1"))

  private fun brain(actorId: String = "cao_minh", beliefs: List<Belief> = listOf(belief())) =
    BrainState(actorId = actorId, slotId = "slot-1", beliefs = beliefs,
      mood = MoodState(MoodState.Mood.WORRIED, "event-9", 18))

  private fun memory(id: String, owner: String = "cao_minh") = MemoryView(
    memoryId = id, slotId = "slot-1", ownerActorId = owner, observationId = "obs-1",
    createdTurnId = "turn-5", committedRevision = 7, summary = "saw luc_tram",
    topic = "escort", salience = EpisodicMemory.Salience.ORDINARY,
    supersedesMemoryId = null, sceneId = "node-7", involvedActorIds = setOf("cao_minh"))

  private fun input(actorId: String = "cao_minh") = Input(
    slotId = "slot-1", actorId = actorId, persona = persona(actorId), brain = brain(actorId),
    memories = listOf(memory("m1"), memory("m2")),
    sceneEvidence = listOf(ActorContextBuilder.SceneEvidence("slot-1", actorId,
      "COMBAT_STARTED", JSONObject().put("entities", org.json.JSONArray()))))

  @Test fun ownership_wrongBrainRejected() {
    try {
      ActorContextBuilder.build(input().copy(brain = brain("luc_tram")))
      fail("expected context_brain_not_owned")
    } catch (e: IllegalArgumentException) {
      assertEquals("context_brain_not_owned", e.message)
    }
  }

  @Test fun ownership_wrongPersonaRejected() {
    try {
      ActorContextBuilder.build(input().copy(persona = persona("luc_tram")))
      fail("expected context_persona_not_owned")
    } catch (e: IllegalArgumentException) {
      assertEquals("context_persona_not_owned", e.message)
    }
  }

  @Test fun ownership_foreignMemoryRejected() {
    try {
      ActorContextBuilder.build(
        input().copy(memories = listOf(memory("m1"), memory("mX", "luc_tram"))))
      fail("expected context_memory_not_owned")
    } catch (e: IllegalArgumentException) {
      assertEquals("context_memory_not_owned", e.message)
    }
  }

  @Test fun canon_refsOnly_noSecrets() {
    val packet = ActorContextBuilder.build(input())
    // Only whitelisted ref IDs cross the firewall — never whole Codex text.
    assertEquals(persona().traitRefs, packet.canonRefs.traitRefs)
    assertEquals(listOf("CAO-LIFE-02"), packet.canonRefs.ethicalRefs)
    // Voice refs are style-only labels, never history entries.
    assertEquals(persona().voiceRefs, packet.canonRefs.voiceStyleRefs)
    assertTrue(packet.memories.none { it.summary.contains("CAO-VOICE-01") })
    // Knowledge locks have no field in the packet at all — they cannot leak.
  }

  @Test fun stance_provenanceLabeled() {
    val packet = ActorContextBuilder.build(
      input().copy(brain = brain(beliefs = listOf(belief(Stance.DISPUTED)))))
    val b = packet.brain.beliefs.single()
    assertEquals("DISPUTED", b.stance)          // DISPUTED stays DISPUTED: no promotion
    assertEquals("luc_tram", b.speakerRef)      // provenance explicit
    assertEquals(listOf("obs-1"), b.evidenceIds)
    assertEquals("WORRIED", packet.brain.mood)
  }

  @Test fun pins_embedded() {
    val packet = ActorContextBuilder.build(input())
    assertEquals("R17", packet.pins.personaRevision)
    assertEquals(persona().sourceSha256, packet.pins.personaSha256)
    assertEquals(BrainContracts.RULE_VERSION, packet.pins.ruleVersion)
    assertEquals(CompanionExposurePolicy.VERSION, packet.pins.policyVersion)
  }

  @Test fun budget_truncation_deterministic() {
    val many = (1..30).map { memory("m$it") }
    val a = ActorContextBuilder.build(input().copy(memories = many, maxMemories = 10))
    val b = ActorContextBuilder.build(input().copy(memories = many, maxMemories = 10))
    assertEquals(10, a.memories.size)
    assertTrue(a.truncated)
    // Reload parity: same input -> same packet (order preserved, deterministic).
    // (JSONObject has no content equals, so compare structural fields.)
    assertEquals(a.memories.map { it.memoryId }, b.memories.map { it.memoryId })
    assertEquals(a.canonRefs, b.canonRefs)
    assertEquals(a.brain, b.brain)
    assertEquals(a.pins, b.pins)
    assertEquals(a.truncated, b.truncated)
    assertEquals(a.sceneEvidence.map { it.eventType }, b.sceneEvidence.map { it.eventType })
  }

  @Test fun sceneEvidence_publicProjectionOnly() {
    val packet = ActorContextBuilder.build(input())
    val ev = packet.sceneEvidence.single()
    assertEquals("COMBAT_STARTED", ev.eventType)
    assertEquals(0, ev.projection.getJSONArray("entities").length())
    // Raw GM payload fields (payload_full etc.) never enter SceneEvidence by type.
  }
}
