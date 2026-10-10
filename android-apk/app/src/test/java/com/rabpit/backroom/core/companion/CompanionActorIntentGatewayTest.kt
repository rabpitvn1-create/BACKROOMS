package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

class CompanionActorIntentGatewayTest {
  private val scope = DecisionPreflight.NativeScope(
    slotId = "slot-1",
    slotRevision = 3,
    actorId = "cao_minh",
    sceneId = "level-0",
    presentActorIds = setOf("cao_minh"),
    capabilities = setOf("cap.search", "cap.wait"),
    inventoryItemIds = emptySet(),
    legalTargetIds = setOf("level-0"),
    canonRevision = "R17",
    ruleVersion = BrainContracts.RULE_VERSION
  )
  private val interaction = DecisionPreflight.InteractionIdentity(
    "turn-4", "a".repeat(64), "b".repeat(64), "c".repeat(64))

  @Test fun actorCanSelectWaitOrSearchWithoutAnyPlayerIssuedActionKind() {
    val wait = CompanionActorIntentGateway.select("""{"intent":"WAIT"}""", scope, interaction)
    val search = CompanionActorIntentGateway.select("""{"intent":"SEARCH"}""", scope, interaction)
    assertTrue(wait is CompanionActorIntentGateway.Result.Accepted)
    assertTrue(search is CompanionActorIntentGateway.Result.Accepted)
    val first = (wait as CompanionActorIntentGateway.Result.Accepted).selected.proposal
    val second = (search as CompanionActorIntentGateway.Result.Accepted).selected.proposal
    assertEquals("cao_minh", first.actorId)
    assertEquals("cao_minh", second.actorId)
    assertEquals(DecisionPreflight.Intent.WAIT, first.intent)
    assertEquals(DecisionPreflight.Intent.SEARCH, second.intent)
    assertNotEquals(first.intent, second.intent)
  }

  @Test fun forgedCapabilitiesActorIdentityOrMovementFailBeforeAnyCommit() {
    val forged = listOf(
      """{"intent":"WAIT","actorId":"player"}""",
      """{"intent":"MOVE","targetId":"level-13"}""",
      """{"intent":"COMBAT_ACTION","targetId":"level-0"}""",
      """{"intent":"TALK","targetId":"cao_minh"}""",
      """{"intent":"SEARCH","capabilities":["cap.move"]}""",
      """{"intent":"WAIT","targetId":"level-0"}""",
      """{"intent":"SEARCH","targetId":42}""",
      """{"intent":"EXECUTE"}""",
      """{"intent":"MOVE","targetId":"../level-1"}"""
    )
    forged.forEach { raw ->
      val result = CompanionActorIntentGateway.select(raw, scope, interaction)
      assertTrue("Accepted unsafe actor proposal: $raw", result is CompanionActorIntentGateway.Result.Rejected)
    }
  }

  @Test fun lockedProposalUsesNativeSceneRevisionAndCanonPins() {
    val result = CompanionActorIntentGateway.select("""{"intent":"SEARCH","targetId":"level-0"}""",
      scope, interaction)
    assertTrue(result is CompanionActorIntentGateway.Result.Accepted)
    val selected = (result as CompanionActorIntentGateway.Result.Accepted).selected
    assertEquals(scope.slotId, selected.proposal.slotId)
    assertEquals(scope.slotRevision, selected.proposal.slotRevision)
    assertEquals(scope.canonRevision, selected.proposal.canonRevision)
    assertEquals(interaction, selected.binding.interaction)
  }
}
