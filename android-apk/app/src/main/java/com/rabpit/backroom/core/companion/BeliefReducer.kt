package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.Belief
import com.rabpit.backroom.core.companion.BrainContracts.BrainDelta
import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.Claim
import com.rabpit.backroom.core.companion.BrainContracts.Stance

/**
 * P2b deterministic belief reducer (issue #506): BR01 / BR02.
 *
 * - BR01: a valid TOLD observation (typed claim, known speaker/listener, already
 *   verified upstream) CREATES one belief with stance UNKNOWN. One instance per
 *   actor/claim/speaker identity (deterministic beliefId); re-application is
 *   idempotent. No truth upgrade: hearsay never becomes KNOWN here.
 * - BR02: later validated actor-owned evidence contradicting the same typed
 *   proposition (via the reviewed ContradictionComparator) UPDATES stance to
 *   DISPUTED and appends the new evidence. Both chains retained; no deletion,
 *   no KNOWN promotion. Comparison outside the predicate contract emits no delta.
 *
 * Application identity (slot, actor, rule version, rule ID, source observation,
 * target belief) makes retry/repetition a no-op. Cross-actor evidence is
 * rejected. Pure function: no I/O, no provider, no RNG.
 */
internal object BeliefReducer {
  data class ToldInput(
    val prior: BrainState,
    val claim: Claim,
    /** The validated TOLD observation's id; speaker/listener already verified. */
    val observationId: String,
    val actorId: String,
    val slotId: String = prior.slotId
  )

  data class ContradictionInput(
    val prior: BrainState,
    val newClaim: Claim,
    val newObservationId: String,
    val actorId: String,
    val slotId: String = prior.slotId
  )

  data class Result(val state: BrainState, val deltas: List<BrainDelta>)

  /** Deterministic belief identity: one instance per actor/claim/speaker. */
  internal fun beliefId(actorId: String, claim: Claim): String =
    CompanionDigests.sha256(
      listOf(actorId, claim.propositionKey(), claim.polarity.name,
        claim.speakerRef ?: "").joinToString("|")).take(32)

  /** BR01: valid TOLD observation -> CREATE belief UNKNOWN (idempotent). */
  fun reduceTold(input: ToldInput): Result {
    require(input.prior.ruleVersion == BrainContracts.RULE_VERSION) { "brain_rule_unsupported" }
    require(input.actorId == input.prior.actorId) { "belief_cross_actor" }
    require(input.slotId == input.prior.slotId) { "belief_cross_slot" }
    require(input.observationId.isNotBlank()) { "belief_evidence_missing" }
    require(input.claim.sourceObservationIds.contains(input.observationId)) {
      "belief_evidence_unlinked"
    }
    require(!input.claim.speakerRef.isNullOrBlank()) { "belief_speaker_missing" }
    require(input.claim.speakerRef != input.actorId) { "belief_self_told_invalid" }
    val id = beliefId(input.actorId, input.claim)
    val existing = input.prior.beliefs.find { it.beliefId == id }
    if (existing != null) {
      // Idempotent re-application: same identity, no new delta.
      return Result(input.prior, emptyList())
    }
    val belief = Belief(
      beliefId = id,
      claim = input.claim,
      // TOLD starts UNKNOWN; certainty of speaking never proves the proposition.
      stance = Stance.UNKNOWN,
      evidenceObservationIds = listOf(input.observationId)
    )
    val delta = BrainDelta(
      ruleId = "BR01", ruleVersion = BrainContracts.RULE_VERSION,
      targetKind = BrainDelta.TargetKind.BELIEF, targetId = id,
      evidenceObservationIds = listOf(input.observationId))
    return Result(input.prior.copy(beliefs = input.prior.beliefs + belief), listOf(delta))
  }

  /** BR02: validated contradicting evidence -> DISPUTED, append evidence. */
  fun reduceContradiction(input: ContradictionInput): Result {
    require(input.prior.ruleVersion == BrainContracts.RULE_VERSION) { "brain_rule_unsupported" }
    require(input.actorId == input.prior.actorId) { "belief_cross_actor" }
    require(input.slotId == input.prior.slotId) { "belief_cross_slot" }
    require(input.newObservationId.isNotBlank()) { "belief_evidence_missing" }
    require(input.newObservationId in input.newClaim.sourceObservationIds) { "belief_evidence_unlinked" }
    val deltas = arrayListOf<BrainDelta>()
    val beliefs = input.prior.beliefs.map { target ->
      if (!BrainContracts.ContradictionComparator.contradicts(target.claim,input.newClaim) ||
          input.newObservationId in target.evidenceObservationIds) target
      else {
        deltas.add(BrainDelta("BR02",BrainContracts.RULE_VERSION,BrainDelta.TargetKind.BELIEF,
          target.beliefId,listOf(input.newObservationId)))
        target.copy(stance=Stance.DISPUTED,evidenceObservationIds=target.evidenceObservationIds+input.newObservationId)
      }
    }
    return Result(if (deltas.isEmpty()) input.prior else input.prior.copy(beliefs=beliefs),deltas)
  }
}
