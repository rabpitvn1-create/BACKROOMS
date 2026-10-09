package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.BrainContracts.Claim
import com.rabpit.backroom.core.companion.BrainContracts.Promise
import com.rabpit.backroom.core.companion.BrainContracts.Stance
import org.junit.Assert.*
import org.junit.Test

/**
 * P2a expected fixtures (issue #505), written BEFORE reducer code.
 *
 * Status labels:
 * - RED: asserts Rule Table behavior; fails with NotImplementedError until the
 *   owning reducer issue lands (#506 belief, #507 goal, #508 mood).
 * - GREEN: contract-level check, passes now.
 * - SPEC: documented expectation needing the commit path; executed in #508.
 */
class P2aFixturesTest {
  private fun claim(polarity: Claim.Polarity = Claim.Polarity.POSITIVE) = Claim(
    claimId = "claim-1", subjectRef = "node-7",
    predicateId = BrainContracts.Predicates.AT_LOCATION, objectRef = "safe",
    polarity = polarity, speakerRef = "luc_tram", sourceObservationIds = listOf("obs-1"))

  private fun promise() = Promise(
    promiseId = "prom-1", promisorActorId = "cao_minh", beneficiaryActorId = "luc_tram",
    scope = "escort", targetRef = "luc_tram",
    completionPredicateId = BrainContracts.Predicates.AT_LOCATION,
    predicateArgs = mapOf("actorRef" to "luc_tram", "locationRef" to "node-8"),
    deadlineTurn = 20, termsDigest = "d".repeat(64))

  private fun brain() = BrainState(actorId = "cao_minh")

  // ---- RB: belief (RED until #506) ----

  @Test fun RB01_firstToldCreatesUnknownBelief() {
    val r = BeliefReducer.reduceTold(
      BeliefReducer.ToldInput(brain(), claim(), "obs-1", "cao_minh"))
    assertEquals(1, r.state.beliefs.size)
    assertEquals(Stance.UNKNOWN, r.state.beliefs.single().stance)
    assertEquals("luc_tram", r.state.beliefs.single().claim.speakerRef)
  }

  @Test fun RB02_duplicateToldIsIdempotent() {
    val once = BeliefReducer.reduceTold(
      BeliefReducer.ToldInput(brain(), claim(), "obs-1", "cao_minh"))
    val twice = BeliefReducer.reduceTold(
      BeliefReducer.ToldInput(once.state, claim(), "obs-1", "cao_minh"))
    assertEquals(once.state.beliefs.size, twice.state.beliefs.size)
  }

  @Test fun RB03_otherActorEvidenceRejected() {
    try {
      BeliefReducer.reduceTold(
        BeliefReducer.ToldInput(brain(), claim(), "obs-1", "luc_tram"))
      fail("expected reject")
    } catch (e: IllegalArgumentException) { /* expected */ }
  }

  @Test fun RB04_noKnownPromotionWithoutRule() {
    val r = BeliefReducer.reduceTold(
      BeliefReducer.ToldInput(brain(), claim(), "obs-1", "cao_minh"))
    assertNotEquals(Stance.KNOWN, r.state.beliefs.single().stance)
  }

  @Test fun RB05_conflictingClaimsDisputed() {
    val first = BeliefReducer.reduceTold(
      BeliefReducer.ToldInput(brain(), claim(Claim.Polarity.POSITIVE), "obs-1", "cao_minh"))
    val r = BeliefReducer.reduceContradiction(BeliefReducer.ContradictionInput(
      first.state, claim(Claim.Polarity.NEGATIVE), "obs-2", "cao_minh"))
    assertEquals(Stance.DISPUTED, r.state.beliefs.single().stance)
    assertEquals(2, r.state.beliefs.single().evidenceObservationIds.size)
  }

  // ---- RG: goal/promise (RED until #507) ----

  @Test fun RG01_acceptedPromiseCreatesActiveGoal() {
    val r = GoalReducer.reduceAccept(GoalReducer.AcceptInput(brain(), promise(), "event-1"))
    assertEquals(1, r.state.goals.size)
    assertEquals(BrainContracts.Goal.GoalStatus.ACTIVE, r.state.goals.single().status)
  }

  @Test fun RG02_proseIsNotAcceptance() {
    // No PROMISE_ACCEPTED event -> no goal. The reducer has no prose path;
    // this fixture asserts the type system offers none.
    assertTrue(GoalReducer.AcceptInput::class.java.declaredFields
      .none { it.name == "prose" || it.name == "utterance" })
  }

  @Test fun RG03_fulfilledPredicateResolvesDoneOnce() {
    val accepted = GoalReducer.reduceAccept(GoalReducer.AcceptInput(brain(), promise(), "event-1"))
    val outcome = GoalReducer.PredicateOutcome(
      BrainContracts.Predicates.AT_LOCATION,
      mapOf("actorRef" to "luc_tram", "locationRef" to "node-8"),
      satisfied = true, breached = false, evidenceEventId = "event-2")
    val done = GoalReducer.reduceOutcome(
      GoalReducer.OutcomeInput(accepted.state, "prom-1", outcome))
    assertEquals(BrainContracts.Goal.GoalStatus.DONE, done.state.goals.single().status)
    val again = GoalReducer.reduceOutcome(
      GoalReducer.OutcomeInput(done.state, "prom-1", outcome))
    assertEquals(BrainContracts.Goal.GoalStatus.DONE, again.state.goals.single().status)
    assertTrue(again.deltas.isEmpty())  // no new delta on replay
  }

  @Test fun RG04_wrongActorOrUnknownPredicateNoDelta() {
    val accepted = GoalReducer.reduceAccept(GoalReducer.AcceptInput(brain(), promise(), "event-1"))
    val wrong = GoalReducer.PredicateOutcome(
      BrainContracts.Predicates.AT_LOCATION, mapOf("actorRef" to "stranger"),
      satisfied = true, breached = false, evidenceEventId = "event-2")
    val r = GoalReducer.reduceOutcome(GoalReducer.OutcomeInput(accepted.state, "prom-1", wrong))
    assertEquals(BrainContracts.Goal.GoalStatus.ACTIVE, r.state.goals.single().status)
    assertTrue(r.deltas.isEmpty())
  }

  @Test fun RG05_breachAbandonsWithAppraisal() {
    val accepted = GoalReducer.reduceAccept(GoalReducer.AcceptInput(brain(), promise(), "event-1"))
    val breach = GoalReducer.PredicateOutcome(
      BrainContracts.Predicates.TURN_REACHED, mapOf("turnNumber" to "21"),
      satisfied = false, breached = true, evidenceEventId = "event-3")
    val r = GoalReducer.reduceOutcome(GoalReducer.OutcomeInput(accepted.state, "prom-1", breach))
    assertEquals(BrainContracts.Goal.GoalStatus.ABANDONED, r.state.goals.single().status)
    val appraised = GoalReducer.reduceAppraisal(r.state, "prom-1", breached = true)
    assertEquals(BrainContracts.RelationshipAppraisal.Appraisal.BREACHED,
      appraised.state.appraisals.single().appraisal)
  }

  // ---- RM: mood (RED until #508) ----

  @Test fun RM01_dangerAtTurn17WorriedUntil18() {
    val r = MoodReducer.reduceDanger(MoodReducer.DangerInput(
      brain(), "COMBAT_STARTED", "event-9", 17, "cao_minh"))
    assertEquals(BrainContracts.MoodState.Mood.WORRIED, r.state.mood.mood)
    assertEquals(18L, r.state.mood.expiryTurn)
  }

  @Test fun RM02_reloadAtSameTurnNoDecay() {
    val once = MoodReducer.reduceDanger(MoodReducer.DangerInput(
      brain(), "COMBAT_STARTED", "event-9", 17, "cao_minh"))
    val again = MoodReducer.reduceDanger(MoodReducer.DangerInput(
      once.state, "COMBAT_STARTED", "event-9", 17, "cao_minh"))
    assertEquals(again.state.mood.mood, once.state.mood.mood)
    assertEquals(again.state.mood.expiryTurn, once.state.mood.expiryTurn)
  }

  @Test fun RM03_expiryUnsetsMood() {
    val worried = MoodReducer.reduceDanger(MoodReducer.DangerInput(
      brain(), "COMBAT_STARTED", "event-9", 17, "cao_minh"))
    val r = MoodReducer.reduceExpire(worried.state, 18)
    assertEquals(BrainContracts.MoodState.Mood.UNSET, r.state.mood.mood)
  }

  @Test fun RM04_newDangerReplacesAtSameTurn() {
    val worried = MoodReducer.reduceDanger(MoodReducer.DangerInput(
      brain(), "COMBAT_STARTED", "event-9", 17, "cao_minh"))
    val r = MoodReducer.reduceDanger(MoodReducer.DangerInput(
      worried.state, "COMBAT_STARTED", "event-10", 18, "cao_minh"))
    assertEquals("event-10", r.state.mood.cause)
    assertEquals(19L, r.state.mood.expiryTurn)
  }

  // ---- RP: replay/projection (contract-level) ----

  @Test fun RP02_unknownPredicateRejectedAtContract() {
    try {
      BrainContracts.Predicates.requireKnown("predicate.v1/mind_read")
      fail("expected promise_unknown_predicate")
    } catch (e: IllegalArgumentException) {
      assertEquals("promise_unknown_predicate", e.message)
    }
  }

  @Test fun RP02_unknownPredicateRejectedInPromiseInit() {
    try {
      promise().copy(completionPredicateId = "predicate.v1/mind_read")
      fail("expected promise_unknown_predicate")
    } catch (e: IllegalArgumentException) {
      assertEquals("promise_unknown_predicate", e.message)
    }
  }

  @Test fun salienceMapUnknownIsOrdinary() {
    assertEquals(EpisodicMemory.Salience.ORDINARY,
      BrainContracts.SalienceMap.salienceOf("SOME_FUTURE_EVENT"))
    assertEquals(EpisodicMemory.Salience.IMPORTANT,
      BrainContracts.SalienceMap.salienceOf("COMBAT_STARTED"))
  }

  @Test fun contradictionComparatorContract() {
    val a = claim(Claim.Polarity.POSITIVE)
    val b = claim(Claim.Polarity.NEGATIVE)
    val c = claim(Claim.Polarity.POSITIVE).copy(objectRef = "dangerous")
    assertTrue(BrainContracts.ContradictionComparator.contradicts(a, b))
    assertFalse(BrainContracts.ContradictionComparator.contradicts(a, c))
    assertFalse(BrainContracts.ContradictionComparator.contradicts(a, a))
  }
}
