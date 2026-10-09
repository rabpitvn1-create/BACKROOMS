package com.rabpit.backroom.core.companion

/**
 * P2a typed brain contracts (issue #505).
 *
 * Maps Character Brain Rule Table V1 to concrete native event/claim/predicate IDs.
 * Versions, bounds and actor refs are pinned here. No trust scalar, no ethics,
 * no canon additions — mechanics only.
 *
 * Reducer implementations live in #506 (belief BR01/BR02), #507 (goal/promise/
 * relationship GR01-GR03/RR01), #508 (mood MR01/MR02). This file defines the
 * shared types, the predicate registry, the salience map, and the contradiction
 * comparator.
 */
internal object BrainContracts {
  const val RULE_VERSION = "rule_table.v1"
  const val CONTRACT_VERSION = "brain_contracts.v1"

  /** Epistemic stance of a claim. UNKNOWN is a stance, never an observation certainty. */
  enum class Stance { UNKNOWN, SUSPECTED, BELIEVED, DISPUTED, KNOWN }

  /**
   * A bounded native-validated claim record (Rule Table §2).
   * polarity distinguishes "X is safe" (POSITIVE) from "X is not safe" (NEGATIVE)
   * for the contradiction comparator.
   */
  data class Claim(
    val claimId: String,
    val subjectRef: String,
    val predicateId: String,
    val objectRef: String,
    val polarity: Polarity,
    val speakerRef: String?,
    val sourceObservationIds: List<String>
  ) {
    enum class Polarity { POSITIVE, NEGATIVE }
    /** Typed proposition identity for duplicate/contradiction detection. */
    fun propositionKey(): String = "$subjectRef|$predicateId|$objectRef"
  }

  /**
   * Supported promise completion predicates (Rule Table §3).
   * V1 supports ONLY these pinned predicates; an unknown predicate is rejected,
   * never stored as free-text executable semantics.
   */
  object Predicates {
    const val AT_LOCATION = "predicate.v1/at_location"
    const val ITEM_IN_INVENTORY = "predicate.v1/item_in_inventory"
    const val TURN_REACHED = "predicate.v1/turn_reached"
    const val THREAT_OBSERVED = "predicate.v1/threat_observed"
    val ALL: Set<String> = setOf(AT_LOCATION, ITEM_IN_INVENTORY, TURN_REACHED, THREAT_OBSERVED)
    fun requireKnown(predicateId: String) {
      require(predicateId in ALL) { "promise_unknown_predicate" }
    }
  }

  /**
   * A promise proposal. Accepted only after native actor/scene/capability checks
   * plus audits approve the structured PROMISE_ACCEPTED event — never from
   * player/GM prose ("you promised" is not acceptance, RG02).
   */
  data class Promise(
    val promiseId: String,
    val promisorActorId: String,
    val beneficiaryActorId: String,
    val scope: String,
    val targetRef: String,
    val completionPredicateId: String,
    val predicateArgs: Map<String, String>,
    val deadlineTurn: Long?,
    val termsDigest: String
  ) {
    init {
      Predicates.requireKnown(completionPredicateId)
      require(promisorActorId != beneficiaryActorId) { "promise_self_beneficiary" }
    }
  }

  /**
   * Reviewed immediate-threat salience map (Rule Table MR01).
   * v1: exactly one IMPORTANT entry. ORDINARY emits no mood change.
   * Unknown event types map to ORDINARY (no delta rights).
   */
  object SalienceMap {
    const val VERSION = "threat_salience.v1"
    private val important = setOf("COMBAT_STARTED")
    fun salienceOf(eventType: String): EpisodicMemory.Salience =
      if (eventType in important) EpisodicMemory.Salience.IMPORTANT
      else EpisodicMemory.Salience.ORDINARY
  }

  /**
   * Reviewed contradiction comparator (BR02).
   * Two claims contradict iff same typed proposition, opposite polarity, both
   * from validated actor-owned observations. Comparison outside this contract
   * emits no delta.
   */
  object ContradictionComparator {
    fun contradicts(a: Claim, b: Claim): Boolean =
      a.propositionKey() == b.propositionKey() && a.polarity != b.polarity
  }

  // ---- Working brain state (reducers #506–#508 own the transitions) ----

  data class Belief(
    val beliefId: String,
    val claim: Claim,
    val stance: Stance,
    val evidenceObservationIds: List<String>
  )

  data class Goal(
    val goalId: String,
    val promise: Promise,
    val status: GoalStatus
  ) {
    enum class GoalStatus { ACTIVE, DONE, ABANDONED }
  }

  data class RelationshipAppraisal(
    val promiseId: String,
    val otherActorId: String,
    val appraisal: Appraisal
  ) {
    enum class Appraisal { FULFILLED, BREACHED }
  }

  data class MoodState(
    val mood: Mood,
    val cause: String?,
    val expiryTurn: Long?
  ) {
    enum class Mood { UNSET, WORRIED }
  }

  data class BrainState(
    val actorId: String,
    val beliefs: List<Belief> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val appraisals: List<RelationshipAppraisal> = emptyList(),
    val mood: MoodState = MoodState(MoodState.Mood.UNSET, null, null),
    val ruleVersion: String = RULE_VERSION,
    val slotId: String = ""
  )

  /** Typed delta emitted by a reducer; persisted with the application identity. */
  data class BrainDelta(
    val ruleId: String,
    val ruleVersion: String,
    val targetKind: TargetKind,
    val targetId: String,
    val evidenceObservationIds: List<String>
  ) {
    enum class TargetKind { BELIEF, GOAL, APPRAISAL, MOOD }
  }
}
