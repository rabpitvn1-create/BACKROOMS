package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.Claim
import com.rabpit.backroom.core.companion.BrainContracts.Stance
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.TalkExecutor.TalkInput
import com.rabpit.backroom.core.companion.TalkExecutor.TalkNativeFacts
import com.rabpit.backroom.core.companion.TalkExecutor.TalkResult
import org.junit.Assert.*
import org.junit.Test

/**
 * A2a native TALK executor tests (issue #512).
 *
 * Covers: happy path, native-fact failures (still TALK, never NONE), intent
 * binding, canon firewall, TOLD→BR01 integration, ordered tape.
 */
class TalkExecutorTest {
  private fun decided(intent: Intent = Intent.TALK, target: String? = "cao_minh") =
    Decided(
      binding = DecisionPreflight.DecisionBinding(
        proposalDigest = "d".repeat(64), actorId = "luc_tram", slotId = "slot-1",
        slotRevision = 42, intent = intent, targetId = target, itemId = null,
        canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION),
      intent = intent, targetId = target, itemId = null,
      providerCalls = 1)

  private fun facts() = TalkNativeFacts(
    speakerId = "luc_tram", listenerId = "cao_minh", sceneId = "node-7",
    speakerConscious = true, speakerAudible = true,
    listenerPresent = true, listenerConscious = true,
    sameScene = true, inReach = true)

  private fun input(f: TalkNativeFacts = facts(), utterance: String = "Đi theo tôi, an toàn.") =
    TalkInput(decided().copy(utterance=utterance), utterance, f, turnId = "turn-9",
      observationId = "obs-42", tapeSequence = 7L)

  @Test fun happyPath_spokenBundle() {
    val r = TalkExecutor.execute(input())
    assertTrue(r is TalkResult.Spoken)
    val b = (r as TalkResult.Spoken).bundle
    assertEquals("luc_tram", b.event.speakerId)
    assertEquals("cao_minh", b.event.listenerId)
    assertEquals("obs-42", b.told.observationId)
    assertEquals("luc_tram", b.told.speakerId)
    assertEquals(1, b.tape.durationMinutes)   // ordinary 1-minute action
    assertEquals(7L, b.tape.sequence)
    assertEquals("TALK", b.tape.actionType)
    assertEquals(b.event.eventId, b.tape.eventId)  // tape references the event
  }

  @Test fun listenerUnconscious_notSpoken_stillTalk() {
    val r = TalkExecutor.execute(input(facts().copy(listenerConscious = false)))
    assertTrue(r is TalkResult.NotSpoken)
    assertEquals("listener_unconscious", (r as TalkResult.NotSpoken).reason)
    // The intent is NOT rewritten to NONE: failure is a TALK attempt.
  }

  @Test fun notInReach_notSpoken() {
    val r = TalkExecutor.execute(input(facts().copy(inReach = false)))
    assertEquals("not_in_reach", (r as TalkResult.NotSpoken).reason)
  }

  @Test fun speakerInaudible_notSpoken() {
    val r = TalkExecutor.execute(input(facts().copy(speakerAudible = false)))
    assertEquals("speaker_inaudible", (r as TalkResult.NotSpoken).reason)
  }

  @Test fun wrongIntent_rejected() {
    val r = TalkExecutor.execute(input().copy(decided = decided(Intent.WAIT, null)))
    assertEquals("intent_not_talk", (r as TalkResult.NotSpoken).reason)
  }

  @Test fun listenerMismatch_rejected() {
    val r = TalkExecutor.execute(input(facts().copy(listenerId = "stranger")))
    assertEquals("listener_mismatch", (r as TalkResult.NotSpoken).reason)
  }

  @Test fun utteranceCanonLeak_rejected() {
    val r = TalkExecutor.execute(input(utterance = "Tin tôi đi CAO-LOCK-01"))
    assertEquals("utterance_canon_leak", (r as TalkResult.NotSpoken).reason)
  }

  @Test fun told_feedsBelief_unknown() {
    // Integration: the TOLD observation from a Spoken bundle feeds BR01.
    val bundle = (TalkExecutor.execute(input()) as TalkResult.Spoken).bundle
    val claim = Claim(
      claimId = "claim-talk-1", subjectRef = "node-8",
      predicateId = BrainContracts.Predicates.AT_LOCATION, objectRef = "safe",
      polarity = Claim.Polarity.POSITIVE, speakerRef = bundle.event.speakerId,
      sourceObservationIds = listOf(bundle.told.observationId))
    val brain = BrainContracts.BrainState(actorId = "cao_minh")
    val res = BeliefReducer.reduceTold(BeliefReducer.ToldInput(
      brain, claim, bundle.told.observationId, "cao_minh"))
    // Utterance is a claim, not world truth: stance starts UNKNOWN.
    assertEquals(Stance.UNKNOWN, res.state.beliefs.single().stance)
    assertEquals("luc_tram", res.state.beliefs.single().claim.speakerRef)
  }

  @Test fun tape_ordered() {
    val a = (TalkExecutor.execute(input().copy(tapeSequence = 7L)) as TalkResult.Spoken).bundle.tape
    val b = (TalkExecutor.execute(input().copy(tapeSequence = 8L)) as TalkResult.Spoken).bundle.tape
    assertTrue(b.sequence > a.sequence)
  }
  @Test fun rejectsChangedSpeechAndWrongSpeaker() {
    assertEquals("utterance_not_locked",(TalkExecutor.execute(input().copy(utterance="changed")) as TalkResult.NotSpoken).reason)
    assertEquals("speaker_mismatch",(TalkExecutor.execute(input(facts().copy(speakerId="other"))) as TalkResult.NotSpoken).reason)
  }
  @Test fun repeatedSpeechInDifferentTurnsHasDistinctIdentity() {
    val a=(TalkExecutor.execute(input()) as TalkResult.Spoken).bundle.event
    val b=(TalkExecutor.execute(input().copy(turnId="next")) as TalkResult.Spoken).bundle.event
    assertNotEquals(a.eventId,b.eventId)
  }

}
