package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

class ExecutorBindingBoundaryTest {
  private fun decided(intent:DecisionPreflight.Intent,target:String?)=CharacterDecisionOrchestrator.Decided(
    DecisionPreflight.DecisionBinding("d".repeat(64),"cao_minh","slot",1,intent,target,null,"R17",BrainContracts.RULE_VERSION,
      DecisionPreflight.InteractionIdentity("turn","a".repeat(64),"c".repeat(64),"b".repeat(64)),"scene"),intent,target,null,1)
  private fun facts(target:String?=null)=MoveSearchInspectExecutor.SenseNativeFacts("cao_minh","scene",target,true,true,false,false)
  @Test fun forgedDecidedIntentCannotOverrideLockedBinding() {
    val forged=decided(DecisionPreflight.Intent.WAIT,null).copy(intent=DecisionPreflight.Intent.SEARCH)
    val result=MoveSearchInspectExecutor.executeSearch(forged,facts(),"turn","obs",1) as MoveSearchInspectExecutor.SearchResult.NotSearched
    assertEquals("decision_binding_mismatch",result.reason)
  }
  @Test fun searchRejectsWrongTurnAndWrongSceneBeforeCreatingObservation() {
    val d=decided(DecisionPreflight.Intent.SEARCH,null)
    val wrongTurn=MoveSearchInspectExecutor.executeSearch(d,facts(),"other","obs",1) as MoveSearchInspectExecutor.SearchResult.NotSearched
    assertEquals("turn_mismatch",wrongTurn.reason)
    val wrongScene=MoveSearchInspectExecutor.executeSearch(d,facts().copy(sceneId="other"),"turn","obs",1) as MoveSearchInspectExecutor.SearchResult.NotSearched
    assertEquals("scene_mismatch",wrongScene.reason)
  }
  @Test fun targetedSearchCannotSkipTargetPresenceOrReach() {
    val d=decided(DecisionPreflight.Intent.SEARCH,"crate")
    val absent=MoveSearchInspectExecutor.executeSearch(d,facts("crate"),"turn","obs",1) as MoveSearchInspectExecutor.SearchResult.NotSearched
    assertEquals("target_absent",absent.reason)
    val unreachable=MoveSearchInspectExecutor.executeSearch(d,facts("crate").copy(targetPresent=true),"turn","obs",1) as MoveSearchInspectExecutor.SearchResult.NotSearched
    assertEquals("target_unreachable",unreachable.reason)
  }
}
