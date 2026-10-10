package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

/** Contract rejects caller-shaped oversized/duplicate evidence before reducers. */
class BrainContractValidationTest {
  private fun claim(ids: List<String>) = BrainContracts.Claim(
    "claim-1","node-7",BrainContracts.Predicates.AT_LOCATION,"safe",
    BrainContracts.Claim.Polarity.POSITIVE,"luc_tram",ids)
  private fun promise(digest: String) = BrainContracts.Promise(
    "promise-1","cao_minh","luc_tram","escort","luc_tram",
    BrainContracts.Predicates.AT_LOCATION,
    mapOf("actorRef" to "luc_tram","locationRef" to "node-8"),20,digest)
  @Test fun duplicateObservationReferencesAreRejected() {
    try { claim(listOf("observation-1","observation-1")); fail("accepted duplicate") }
    catch(e: IllegalArgumentException) { assertEquals("claim_contract_invalid",e.message) }
  }
  @Test fun boundedClaimEvidenceAndNoUnprovenContradiction() {
    try { claim((1..65).map { "observation-$it" }); fail("accepted unbounded evidence") }
    catch(e: IllegalArgumentException) { assertEquals("claim_contract_invalid",e.message) }
    assertFalse(BrainContracts.ContradictionComparator.contradicts(
      claim(emptyList()),claim(emptyList()).copy(polarity=BrainContracts.Claim.Polarity.NEGATIVE)))
  }
  @Test fun termsNeedPinnedDigestAndKnownPredicate() {
    try { promise("not-a-digest"); fail("accepted arbitrary terms") }
    catch(e: IllegalArgumentException) { assertEquals("promise_contract_invalid",e.message) }
    try { promise("a".repeat(64)).copy(completionPredicateId="model/guess"); fail("unknown predicate") }
    catch(e: IllegalArgumentException) { assertEquals("promise_unknown_predicate",e.message) }
  }
}
