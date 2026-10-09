package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent

/**
 * A2a native TALK executor + verified speech events (issue #512).
 *
 * Executes a locked TALK decision (#511) against NATIVE communication facts.
 * TALK costs 1 minute per the action contract (ExitStreak ordinary action).
 *
 * Authority rules:
 * - Speaker/listener/scene/reach/consciousness/audibility must be NATIVE-PROVEN
 *   ([TalkNativeFacts]); inference never substitutes. Only actual communication
 *   creates a TOLD observation (which feeds BR01).
 * - A failed TALK (unheard, unreachable, unconscious listener) is still a TALK
 *   attempt — never rewritten as a technical NONE. Refusal/negotiation spoken
 *   aloud are real TALK.
 * - The utterance is the speaker's claim, distinct from world truth: the TOLD
 *   belief it creates starts UNKNOWN (#506). A promise request inside speech is
 *   NOT an accepted promise — that requires the separate PROMISE_ACCEPTED flow
 *   (#507).
 * - Capability/semantic/canon checks from #510/#511 still apply; the utterance
 *   itself passes the canon firewall (no lock refs).
 * - Output is a complete ordered bundle: tape entry + speech event + TOLD
 *   observation. The atomic Core commit (stage/events/observations/brain/receipt
 *   in one transaction) is the #517 boundary; this executor produces the exact
 *   bundle that commit consumes. Repair keeps locked action/target/timing/tape.
 *
 * Pure Kotlin: no Android, no I/O, no provider.
 */
internal object TalkExecutor {
  /** Native-proven communication facts. Every field must come from native evidence. */
  data class TalkNativeFacts(
    val speakerId: String,
    val listenerId: String,
    val sceneId: String,
    val speakerConscious: Boolean,
    val speakerAudible: Boolean,
    val listenerPresent: Boolean,
    val listenerConscious: Boolean,
    val sameScene: Boolean,
    val inReach: Boolean
  )

  data class TalkInput(
    val decided: Decided,
    /** Audited provider utterance text (canon-firewalled). */
    val utterance: String,
    val facts: TalkNativeFacts,
    val turnId: String,
    val observationId: String,
    val tapeSequence: Long
  )

  data class SpeechEvent(
    val eventId: String,
    val turnId: String,
    val speakerId: String,
    val listenerId: String,
    val sceneId: String,
    val utteranceDigest: String,
    /** The utterance claim as spoken — NOT world truth. */
    val utterance: String
  )

  /** TOLD observation candidate feeding BR01 (stance starts UNKNOWN). */
  data class ToldObservation(
    val observationId: String,
    val turnId: String,
    val speakerId: String,
    val listenerId: String,
    val claimDigest: String
  )

  data class TapeEntry(
    val sequence: Long,
    val turnId: String,
    val actorId: String,
    val actionType: String,
    /** TALK is an ordinary 1-minute action per contract. */
    val durationMinutes: Int,
    val eventId: String
  )

  data class SpokenBundle(
    val event: SpeechEvent,
    val told: ToldObservation,
    val tape: TapeEntry
  )

  sealed class TalkResult {
    data class Spoken(val bundle: SpokenBundle) : TalkResult()
    /** Failed TALK stays TALK (attempt), never rewritten as NONE. */
    data class NotSpoken(val reason: String) : TalkResult()
  }

  fun execute(input: TalkInput): TalkResult {
    val decided = input.decided
    if (decided.intent != Intent.TALK) return TalkResult.NotSpoken("intent_not_talk")
    if (decided.binding.actorId != input.facts.speakerId) return TalkResult.NotSpoken("speaker_mismatch")
    if (decided.utterance != input.utterance) return TalkResult.NotSpoken("utterance_not_locked")
    if (decided.targetId != input.facts.listenerId)
      return TalkResult.NotSpoken("listener_mismatch")
    if (input.utterance.isBlank()) return TalkResult.NotSpoken("utterance_empty")
    // Canon firewall on the utterance itself.
    if (input.utterance.contains("CAO-LOCK") || input.utterance.contains("knowledgeLockRefs"))
      return TalkResult.NotSpoken("utterance_canon_leak")
    val f = input.facts
    // Native communication preconditions — every one must be proven.
    if (!f.speakerConscious) return TalkResult.NotSpoken("speaker_unconscious")
    if (!f.speakerAudible) return TalkResult.NotSpoken("speaker_inaudible")
    if (!f.listenerPresent) return TalkResult.NotSpoken("listener_absent")
    if (!f.listenerConscious) return TalkResult.NotSpoken("listener_unconscious")
    if (!f.sameScene) return TalkResult.NotSpoken("scene_mismatch")
    if (!f.inReach) return TalkResult.NotSpoken("not_in_reach")
    // Actual communication: emit the ordered bundle.
    val utteranceDigest = CompanionDigests.sha256(input.utterance)
    val eventId = "speech-" + CompanionDigests.sha256(CompanionWaitCapture.canonical(org.json.JSONArray(listOf(decided.binding.slotId,input.turnId,f.speakerId,f.listenerId,f.sceneId,utteranceDigest)))).take(16)
    val event = SpeechEvent(
      eventId = eventId, turnId = input.turnId,
      speakerId = f.speakerId, listenerId = f.listenerId, sceneId = f.sceneId,
      utteranceDigest = utteranceDigest, utterance = input.utterance)
    val told = ToldObservation(
      observationId = input.observationId, turnId = input.turnId,
      speakerId = f.speakerId, listenerId = f.listenerId,
      claimDigest = utteranceDigest)
    val tape = TapeEntry(
      sequence = input.tapeSequence, turnId = input.turnId,
      actorId = f.speakerId, actionType = "TALK",
      durationMinutes = 1, eventId = eventId)
    return TalkResult.Spoken(SpokenBundle(event, told, tape))
  }
}
