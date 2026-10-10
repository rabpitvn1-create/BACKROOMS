package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

class BrainReducerRegressionTest {
  private fun brain()=BrainContracts.BrainState("cao_minh",slotId="s")
  private fun claim(speaker: String="luc_tram",polarity: BrainContracts.Claim.Polarity=BrainContracts.Claim.Polarity.POSITIVE,
    observations: List<String> = listOf("o"))=BrainContracts.Claim("c","scene",BrainContracts.Predicates.AT_LOCATION,
      "safe",polarity,speaker,observations)
  private fun accepted(): BrainContracts.BrainState {
    val p=BrainContracts.Promise("p","cao_minh","luc_tram","escort","luc_tram",BrainContracts.Predicates.AT_LOCATION,
      mapOf("actorRef" to "luc_tram","locationRef" to "room"),20,"d".repeat(64))
    return GoalReducer.reduceAccept(GoalReducer.AcceptInput(brain(),p,"accepted")).state
  }
  @Test fun unrelatedBreachCannotAbandonPromise() {
    val prior=accepted()
    val outcome=GoalReducer.PredicateOutcome(BrainContracts.Predicates.ITEM_IN_INVENTORY,
      mapOf("item" to "other"),false,true,"event")
    val result=GoalReducer.reduceOutcome(GoalReducer.OutcomeInput(prior,"p",outcome))
    assertEquals(prior,result.state); assertTrue(result.deltas.isEmpty())
  }
  @Test fun beforeDeadlineCannotCountAsDeadlineBreach() {
    val prior=accepted()
    val outcome=GoalReducer.PredicateOutcome(BrainContracts.Predicates.TURN_REACHED,
      mapOf("turnNumber" to "19"),false,true,"event")
    assertTrue(GoalReducer.reduceOutcome(GoalReducer.OutcomeInput(prior,"p",outcome)).deltas.isEmpty())
  }
  @Test fun unresolvedPromiseHasNoRelationshipAppraisal() {
    assertTrue(GoalReducer.reduceAppraisal(accepted(),"p",true).deltas.isEmpty())
  }
  @Test fun unlinkedContradictionEvidenceRejected() {
    val prior=BeliefReducer.reduceTold(BeliefReducer.ToldInput(brain(),claim(),"o","cao_minh")).state
    try {
      BeliefReducer.reduceContradiction(BeliefReducer.ContradictionInput(prior,
        claim(polarity=BrainContracts.Claim.Polarity.NEGATIVE),"other","cao_minh"))
      fail("expected rejection")
    } catch (e: IllegalArgumentException) { assertEquals("belief_evidence_unlinked",e.message) }
  }
  @Test fun contradictionDisputesAllMatchingSpeakers() {
    var prior=brain()
    for(speaker in listOf("luc_tram","witness")) prior=BeliefReducer.reduceTold(
      BeliefReducer.ToldInput(prior,claim(speaker),"o","cao_minh")).state
    val result=BeliefReducer.reduceContradiction(BeliefReducer.ContradictionInput(prior,
      claim(polarity=BrainContracts.Claim.Polarity.NEGATIVE,observations=listOf("new")),"new","cao_minh"))
    assertEquals(2,result.deltas.size)
    assertTrue(result.state.beliefs.all { it.stance==BrainContracts.Stance.DISPUTED })
  }
  @Test fun expiredThreatDoesNotRearmOnReplay() {
    val worried=MoodReducer.reduceDanger(MoodReducer.DangerInput(brain(),"COMBAT_STARTED","e",17,"cao_minh")).state
    val expired=MoodReducer.reduceExpire(worried,18).state
    val replay=MoodReducer.reduceDanger(MoodReducer.DangerInput(expired,"COMBAT_STARTED","e",17,"cao_minh"))
    assertEquals(expired,replay.state);assertTrue(replay.deltas.isEmpty())
  }
  @Test fun overflowTurnRejected() {
    try { MoodReducer.reduceDanger(MoodReducer.DangerInput(brain(),"COMBAT_STARTED","e",Long.MAX_VALUE,"cao_minh")); fail("expected rejection") }
    catch(e: IllegalArgumentException) { assertEquals("mood_turn_invalid",e.message) }
  }
}
