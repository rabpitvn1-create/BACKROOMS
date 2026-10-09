package com.rabpit.backroom.core.companion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContextAndBudgetRegressionTest {
  private fun memory(slot: String = "s") = MemoryRetrieval.MemoryView("m",slot,"cao_minh","o","t",1,
    "summary","topic",EpisodicMemory.Salience.ORDINARY,null,"scene",emptySet())
  private fun input() = ActorContextBuilder.Input("s","cao_minh",
    CompanionPersonaFixture.load("cao_minh"),
    BrainContracts.BrainState("cao_minh",slotId="s"),listOf(memory()),emptyList())
  private fun rejects(reason: String, action: () -> Unit) {
    try { action(); fail("expected $reason") } catch (e: IllegalArgumentException) { assertEquals(reason,e.message) }
  }
  @Test fun zeroAndTooSmallBudgetsNeverAdmitFirstMemory() {
    for (limit in listOf(0,1,70)) {
      val packet=MemoryRetrieval.retrieve(listOf(memory()),MemoryRetrieval.Query("s","cao_minh",maxChars=limit))
      assertTrue(packet.entries.isEmpty()); assertTrue(packet.truncated)
    }
  }
  @Test fun negativeBudgetRejected() {
    rejects("memory_budget_invalid") { MemoryRetrieval.retrieve(listOf(memory()),MemoryRetrieval.Query("s","cao_minh",maxChars=-1)) }
  }
  @Test fun sameActorFromAnotherSlotCannotEnterContext() {
    rejects("context_memory_not_owned") { ActorContextBuilder.build(input().copy(memories=listOf(memory("other")))) }
  }
  @Test fun brainFromAnotherSlotRejected() {
    rejects("context_slot_mismatch") { ActorContextBuilder.build(input().copy(brain=BrainContracts.BrainState("cao_minh",slotId="other"))) }
  }
  @Test fun rawGmJsonCannotBeRelabeledAsPublic() {
    val json=JSONObject().put("source","a").put("target","b").put("WRITER_SECRET","hidden")
    val evidence=ActorContextBuilder.SceneEvidence("s","cao_minh","WORLD_TRANSITION",json)
    rejects("context_projection_unclassified") { ActorContextBuilder.build(input().copy(sceneEvidence=listOf(evidence))) }
  }
  @Test fun anotherActorsEvidenceRejected() {
    val evidence=ActorContextBuilder.SceneEvidence("s","luc_tram","WORLD_TRANSITION",JSONObject().put("source","a").put("target","b"))
    rejects("context_evidence_not_owned") { ActorContextBuilder.build(input().copy(sceneEvidence=listOf(evidence))) }
  }
  @Test fun uncommittedEventsAreNotProjected() {
    val view=InteractUiContract.project(InteractUiContract.ProjectionInput(
      listOf(InteractUiContract.PublicEventView("e","hidden outcome")),"r","t","TALK",false,"alias",InteractUiContract.PendingState.PENDING))
    assertTrue(view.publicEvents.isEmpty()); assertNull(view.receipt); assertNotNull(view.pending)
  }
}
