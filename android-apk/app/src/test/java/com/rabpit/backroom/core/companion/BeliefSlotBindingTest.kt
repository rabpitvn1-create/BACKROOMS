package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

/** Pure reducer inputs must not cross actor/slot before native ledger publication. */
class BeliefSlotBindingTest {
  private val prior = BrainContracts.BrainState("cao_minh",slotId="a".repeat(32))
  private val claim = BrainContracts.Claim("c","room",BrainContracts.Predicates.AT_LOCATION,"safe",
    BrainContracts.Claim.Polarity.POSITIVE,"luc_tram",listOf("o"))
  @Test fun toldCannotCrossSlot() {
    try { BeliefReducer.reduceTold(BeliefReducer.ToldInput(prior,claim,"o","cao_minh","b".repeat(32))); fail("cross slot accepted") }
    catch(e: IllegalArgumentException) { assertEquals("belief_cross_slot",e.message) }
  }
  @Test fun contradictionCannotCrossSlot() {
    try { BeliefReducer.reduceContradiction(BeliefReducer.ContradictionInput(
      prior,claim,"o","cao_minh","b".repeat(32))); fail("cross slot accepted") }
    catch(e: IllegalArgumentException) { assertEquals("belief_cross_slot",e.message) }
  }
  @Test fun selfReportedToldIsNotAListenerObservation() {
    val self=claim.copy(speakerRef="cao_minh")
    try { BeliefReducer.reduceTold(BeliefReducer.ToldInput(prior,self,"o","cao_minh")); fail("self TOLD accepted") }
    catch(e: IllegalArgumentException) { assertEquals("belief_self_told_invalid",e.message) }
  }
  @Test fun nativeSchemaUsesEvidenceAndImmutableManifests() {
    val statements=BrainDeltaSchema.createStatements()
    assertTrue(statements.any { it.contains("source_observation_id") && it.contains("BR01") })
    assertTrue(statements.any { it.contains("immutable_brain_delta") })
    assertTrue(statements.any { it.contains("immutable_brain_manifest") })
  }
}
