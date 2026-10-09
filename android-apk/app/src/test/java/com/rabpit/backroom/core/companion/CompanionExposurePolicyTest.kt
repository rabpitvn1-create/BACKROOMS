package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Scope
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Fact
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Channel
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Publication
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Event
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.NativeFacts
import com.rabpit.backroom.core.companion.CompanionExposurePolicy.Eligible
import org.junit.Assert.*
import org.junit.Test

/** Synthetic native evidence tests. They do not certify a runtime perception adapter. */
class CompanionExposurePolicyTest {
  private fun scope() = Scope("a".repeat(32), "turn-17", 17, "event-17-0", "present-scene")
  private fun event(s: Scope = scope(), channels: Set<Channel> = setOf(Channel.SEEN, Channel.HEARD)) =
    Event(s, Publication.PERCEPTIBLE, channels)
  private fun facts(s: Scope = scope(), actor: String = "cao_minh") =
    NativeFacts(s, actor, Fact.YES, Fact.YES, Fact.YES, Fact.YES, Fact.YES)
  private fun rejects(reason: String, operation: () -> Unit) {
    try { operation(); fail("expected " + reason) } catch (e: IllegalArgumentException) {
      assertEquals(reason, e.message)
    }
  }

  @Test fun visibleAndAudibleActorGetsOnlyDeclaredChannels() {
    val s = scope()
    val both = CompanionExposurePolicy.eligible(event(s), listOf(facts(s)))
    assertEquals(listOf(Channel.SEEN, Channel.HEARD), both.map { it.channel })
    assertTrue(both.all { it.scope == s && it.actorId == "cao_minh" &&
      it.policyVersion == CompanionExposurePolicy.VERSION })
    assertEquals(listOf(Channel.HEARD), CompanionExposurePolicy.eligible(
      event(s, setOf(Channel.HEARD)), listOf(facts(s))).map { it.channel })
  }

  @Test fun missingSceneReachOrConsciousnessNeverGrantsObservation() {
    val f = facts()
    for (status in listOf(Fact.NO, Fact.UNKNOWN)) {
      for (bad in listOf(f.copy(sceneMember = status), f.copy(inReach = status), f.copy(conscious = status))) {
        assertTrue(CompanionExposurePolicy.eligible(event(), listOf(bad)).isEmpty())
      }
    }
  }

  @Test fun sensesAreIndependentAndUnknownDoesNotPass() {
    for (status in listOf(Fact.NO, Fact.UNKNOWN)) {
      assertEquals(listOf(Channel.HEARD), CompanionExposurePolicy.eligible(
        event(), listOf(facts().copy(visible = status))).map { it.channel })
      assertEquals(listOf(Channel.SEEN), CompanionExposurePolicy.eligible(
        event(), listOf(facts().copy(audible = status))).map { it.channel })
    }
    assertTrue(CompanionExposurePolicy.eligible(event(),
      listOf(facts().copy(visible = Fact.UNKNOWN, audible = Fact.UNKNOWN))).isEmpty())
  }

  @Test fun absentActorsAreNotAddedAndOneActorCannotBorrowAnotherSense() {
    val result = CompanionExposurePolicy.eligible(event(), listOf(
      facts(actor = "cao_minh").copy(audible = Fact.NO),
      facts(actor = "luc_tram").copy(visible = Fact.NO)))
    assertEquals(listOf("cao_minh:SEEN", "luc_tram:HEARD"),
      result.map { it.actorId + ":" + it.channel })
    assertTrue(CompanionExposurePolicy.eligible(event(), emptyList()).isEmpty())
  }

  @Test fun gmSecretsAndUnclassifiedEventsNeverGrantObservation() {
    for (publication in listOf(Publication.PRIVATE_GM, Publication.UNCLASSIFIED)) {
      assertTrue(CompanionExposurePolicy.eligible(Event(scope(), publication, emptySet()),
        listOf(facts())).isEmpty())
      rejects("private_channels_invalid") { Event(scope(), publication, setOf(Channel.SEEN)) }
    }
    assertTrue(CompanionExposurePolicy.eligible(event(channels = emptySet()), listOf(facts())).isEmpty())
  }

  @Test fun everyScopeComponentMustMatchExactly() {
    val s = scope()
    for (other in listOf(s.copy(slotId = "b".repeat(32)), s.copy(turnId = "turn-18"),
      s.copy(revision = 18), s.copy(eventId = "event-17-1"), s.copy(sceneId = "other-scene"))) {
      rejects("evidence_scope_mismatch") { CompanionExposurePolicy.eligible(event(s), listOf(facts(other))) }
    }
  }

  @Test fun duplicateActorEvidenceRejectsInsteadOfMerging() {
    val f = facts()
    rejects("duplicate_actor_evidence") {
      CompanionExposurePolicy.eligible(event(), listOf(f.copy(visible = Fact.NO), f.copy(audible = Fact.NO)))
    }
    // Reject even when the event itself is private; malformed evidence is not silently accepted.
    rejects("duplicate_actor_evidence") {
      CompanionExposurePolicy.eligible(Event(scope(), Publication.PRIVATE_GM, emptySet()), listOf(f, f))
    }
  }

  @Test fun actorInputOrderCannotChangeEligibilityOrder() {
    val a = facts(actor = "cao_minh")
    val b = facts(actor = "luc_tram")
    assertEquals(CompanionExposurePolicy.eligible(event(), listOf(a, b)),
      CompanionExposurePolicy.eligible(event(), listOf(b, a)))
  }

  @Test fun eventChannelsAreCopiedAndResultCannotBeMutated() {
    val channels = mutableSetOf(Channel.SEEN)
    val e = event(channels = channels)
    channels.clear()
    assertEquals(setOf(Channel.SEEN), e.channels)
    val result = CompanionExposurePolicy.eligible(e, listOf(facts()))
    try { (result as MutableList<Eligible>).clear(); fail("mutable result") }
    catch (_: UnsupportedOperationException) { }
    try { (e.channels as MutableSet<Channel>).clear(); fail("mutable channels") }
    catch (_: UnsupportedOperationException) { }
  }

  @Test fun evidenceCountAndIdentifiersAreBounded() {
    rejects("exposure_bound") {
      CompanionExposurePolicy.eligible(event(), (0..64).map { facts(actor = "actor-" + it) })
    }
    rejects("actor_invalid") { facts(actor = "secret/actor") }
    rejects("actor_invalid") { facts(actor = "x".repeat(161)) }
    rejects("slot_invalid") { scope().copy(slotId = "A".repeat(32)) }
    rejects("revision_invalid") { scope().copy(revision = 0) }
    rejects("event_invalid") { scope().copy(eventId = "") }
    rejects("turn_invalid") { scope().copy(turnId = "turn\n17") }
    rejects("scene_invalid") { scope().copy(sceneId = "界".repeat(86)) }
    rejects("scene_invalid") { scope().copy(sceneId = "scene\u0000") }
    rejects("scene_invalid") { scope().copy(sceneId = "\uD800") }
    rejects("scene_invalid") { scope().copy(sceneId = "\uDC00") }
    assertEquals("phòng-😀", scope().copy(sceneId = "phòng-😀").sceneId)
  }
}
