package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.CharacterPresence
import com.rabpit.backroom.core.CharacterState
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.PartyState
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Channel
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Event
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Fact
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Publication
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Scope
import org.junit.Assert.*
import org.junit.Test

/**
 * M1b.2 adapter fixtures (issue #499). Exact expectations for scope, senses,
 * absent / unconscious-equivalent / out-of-reach, scene change, and unsupported input.
 * The adapter is pure: these tests run on the JVM without Android.
 */
class NativePerceptionAdapterTest {
  private val slot = "a".repeat(32)
  private fun scope(scene: String = "node-7") = Scope(slot, "turn-9", 9, "event-9-0", scene)

  private fun state(
    actors: Map<String, CharacterState>,
    party: List<String> = listOf("cao_minh"),
    world: Map<String, String> = mapOf("worldNodeId" to "node-7")
  ) = GameState(characters = actors, party = PartyState(leaderId = "cao_minh", memberIds = party), world = world)

  private fun actor(id: String, presence: CharacterPresence = CharacterPresence.ACTIVE) =
    CharacterState(id = id, name = id, presence = presence)

  @Test fun activePartyMemberDoesNotProvePerception() {
    val s = state(mapOf("cao_minh" to actor("cao_minh")))
    val f = NativePerceptionAdapter.perceive(s, scope(), "cao_minh")
    assertEquals(Fact.UNKNOWN, f.sceneMember)
    assertEquals(Fact.UNKNOWN, f.inReach)
    assertEquals(Fact.UNKNOWN, f.conscious)
    assertEquals(Fact.UNKNOWN, f.visible)
    assertEquals(Fact.UNKNOWN, f.audible)
  }

  @Test fun deadActorDenied() {
    val s = state(mapOf("cao_minh" to actor("cao_minh", CharacterPresence.DEAD)))
    val f = NativePerceptionAdapter.perceive(s, scope(), "cao_minh")
    assertEquals(Fact.NO, f.sceneMember)
    assertEquals(Fact.NO, f.conscious)
    assertEquals(Fact.UNKNOWN, f.inReach)
  }

  @Test fun separatedActorNotSceneMember() {
    val s = state(mapOf("luc_tram" to actor("luc_tram", CharacterPresence.SEPARATED)),
      party = listOf("cao_minh", "luc_tram"))
    val f = NativePerceptionAdapter.perceive(s, scope(), "luc_tram")
    assertEquals(Fact.NO, f.sceneMember)
    assertEquals(Fact.UNKNOWN, f.visible)
  }

  @Test fun missingActorNotSceneMember() {
    val s = state(mapOf("luc_tram" to actor("luc_tram", CharacterPresence.MISSING)),
      party = listOf("cao_minh", "luc_tram"))
    val f = NativePerceptionAdapter.perceive(s, scope(), "luc_tram")
    assertEquals(Fact.NO, f.sceneMember)
  }

  @Test fun wrongSceneFailsClosedToUnknown() {
    val s = state(mapOf("cao_minh" to actor("cao_minh")))
    // Event at node-9, snapshot at node-7: snapshot is not at the event's scene.
    val f = NativePerceptionAdapter.perceive(s, scope("node-9"), "cao_minh")
    assertEquals(Fact.UNKNOWN, f.sceneMember)
    assertEquals(Fact.UNKNOWN, f.inReach)
    assertEquals(Fact.UNKNOWN, f.visible)
    assertEquals(Fact.UNKNOWN, f.audible)
  }

  @Test fun absentActorFailsClosedToUnknown() {
    val s = state(mapOf("cao_minh" to actor("cao_minh")))
    val f = NativePerceptionAdapter.perceive(s, scope(), "ghost")
    assertEquals(Fact.UNKNOWN, f.sceneMember)
    assertEquals(Fact.UNKNOWN, f.conscious)
  }

  @Test fun activeButNotInPartyIsUnknown() {
    val s = state(mapOf("stranger" to actor("stranger")), party = listOf("cao_minh"))
    val f = NativePerceptionAdapter.perceive(s, scope(), "stranger")
    assertEquals(Fact.UNKNOWN, f.sceneMember)
  }

  @Test fun adapterFactsDrivePolicyEligibility() {
    val s = state(mapOf(
      "cao_minh" to actor("cao_minh"),
      "luc_tram" to actor("luc_tram", CharacterPresence.SEPARATED)),
      party = listOf("cao_minh", "luc_tram"))
    val sc = scope()
    val facts = listOf(
      NativePerceptionAdapter.perceive(s, sc, "cao_minh"),
      NativePerceptionAdapter.perceive(s, sc, "luc_tram"))
    val eligible = CompanionExposurePolicy.eligible(
      Event(sc, Publication.PERCEPTIBLE, setOf(Channel.SEEN, Channel.HEARD)), facts)
    assertTrue("party membership grants no perception authority", eligible.isEmpty())
  }

  @Test fun sceneKeyPrefersWorldNodeId() {
    val s = state(mapOf("cao_minh" to actor("cao_minh")),
      world = mapOf("worldNodeId" to "node-7", "location" to "elsewhere"))
    assertEquals("node-7", NativePerceptionAdapter.sceneKeyOf(s))
  }

  @Test fun observationCandidateIsDeterministicAndImmutable() {
    val s = state(mapOf("cao_minh" to actor("cao_minh")))
    val sc = scope()
    // Policy-positive fixture only; not evidence from the incomplete adapter.
    val facts = listOf(CompanionExposurePolicy.NativeFacts(sc, "cao_minh",
      Fact.YES, Fact.YES, Fact.YES, Fact.YES, Fact.YES))
    val eligible = CompanionExposurePolicy.eligible(
      Event(sc, Publication.PERCEPTIBLE, setOf(Channel.SEEN)), facts).single()
    val projection = PublicEventProjection.project("WAIT_COMPLETED",
      org.json.JSONObject().put("actor", "cao_minh").put("minutes", 30).put("location","node-7").put("elapsedMinutes",90))!!
    val a = ObservationCandidate.fromEligible(eligible, projection)
    val b = ObservationCandidate.fromEligible(eligible, projection)
    assertEquals(a.observationId, b.observationId)
    assertEquals("cao_minh", a.ownerActorId)
    assertEquals(ObservationCandidate.AccessKind.SEEN, a.access)
    assertEquals(ObservationCandidate.Certainty.PLAUSIBLE, a.certainty)
    assertEquals("companion_exposure.v1", a.policyVersion)
  }
}
