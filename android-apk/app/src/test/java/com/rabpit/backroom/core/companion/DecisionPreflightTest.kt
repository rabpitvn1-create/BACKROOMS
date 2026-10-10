package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.DecisionPreflight.NativeScope
import com.rabpit.backroom.core.companion.DecisionPreflight.Proposal
import com.rabpit.backroom.core.companion.DecisionPreflight.Result
import org.junit.Assert.*
import org.junit.Test

/**
 * A1a native decision preflight tests (issue #510).
 *
 * Validator-only: no provider, no RNG, no mutation. Every rejection fails
 * closed with a typed reason.
 */
class DecisionPreflightTest {
  private fun scope() = NativeScope(
    slotId = "slot-1", slotRevision = 42, actorId = "luc_tram", sceneId = "node-7",
    presentActorIds = setOf("luc_tram", "cao_minh"),
    capabilities = setOf("cap.talk", "cap.move", "cap.search", "cap.inspect",
      "cap.use_item", "cap.combat", "cap.wait"),
    inventoryItemIds = setOf("torch"),
    legalTargetIds = setOf("cao_minh", "node-8", "crate"),
    canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION)

  private fun proposal(intent: Intent = Intent.TALK) = Proposal(
    intent = intent, targetId = "cao_minh", itemId = null,
    slotId = "slot-1", slotRevision = 42, actorId = "luc_tram",
    canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION)

  private fun rejectedReason(p: Proposal, s: NativeScope = scope()): String {
    val r = DecisionPreflight.preflight(p, s)
    assertTrue("expected Rejected, got $r", r is Result.Rejected)
    return (r as Result.Rejected).reason
  }

  @Test fun happyPath_talkApprovedWithBinding() {
    val r = DecisionPreflight.preflight(proposal(), scope())
    assertTrue(r is Result.Approved)
    val b = (r as Result.Approved).binding
    assertEquals("luc_tram", b.actorId)
    assertEquals(42L, b.slotRevision)
    assertEquals(Intent.TALK, b.intent)
    assertEquals(64, b.proposalDigest.length)  // sha256 hex
  }

  @Test fun staleRevision_rejected() {
    assertEquals("revision_stale",
      rejectedReason(proposal().copy(slotRevision = 41)))
  }

  @Test fun crossSlot_rejected() {
    assertEquals("slot_mismatch",
      rejectedReason(proposal().copy(slotId = "slot-2")))
  }

  @Test fun caoMinhAutonomousCandidateIsValidated() {
    val s = scope().copy(actorId = "cao_minh",
      presentActorIds = setOf("luc_tram", "cao_minh"))
    assertTrue(DecisionPreflight.preflight(proposal().copy(actorId = "cao_minh"), s) is Result.Approved)
  }

  @Test fun proseForgedCapability_rejected() {
    val s = scope().copy(capabilities = setOf("cap.talk"))  // no cap.move natively
    assertEquals("capability_missing",
      rejectedReason(proposal(Intent.MOVE).copy(targetId = "node-8"), s))
  }

  @Test fun ambiguousTarget_rejected() {
    assertEquals("target_ambiguous",
      rejectedReason(proposal().copy(targetId = null)))
    assertEquals("target_ambiguous",
      rejectedReason(proposal().copy(targetId = "  ")))
  }

  @Test fun illegalTarget_rejected() {
    assertEquals("target_unreachable",
      rejectedReason(proposal().copy(targetId = "void-99")))
  }

  @Test fun pinsMismatch_rejected() {
    assertEquals("pins_mismatch",
      rejectedReason(proposal().copy(canonRevision = "R16")))
    assertEquals("pins_mismatch",
      rejectedReason(proposal().copy(ruleVersion = "rule_table.v0")))
  }

  @Test fun useItem_missingItem_rejected() {
    assertEquals("item_missing",
      rejectedReason(proposal(Intent.USE_ITEM).copy(itemId = "rope", targetId = null)))
    assertEquals("item_ambiguous",
      rejectedReason(proposal(Intent.USE_ITEM).copy(itemId = null, targetId = null)))
  }

  @Test fun useItem_nativeItem_approved() {
    val r = DecisionPreflight.preflight(
      proposal(Intent.USE_ITEM).copy(itemId = "torch", targetId = null), scope())
    assertTrue(r is Result.Approved)
  }

  @Test fun wait_none_noTarget() {
    assertTrue(DecisionPreflight.preflight(
      proposal(Intent.WAIT).copy(targetId = null), scope()) is Result.Approved)
    assertEquals("target_unexpected",
      rejectedReason(proposal(Intent.WAIT).copy(targetId = "node-8")))
  }

  @Test fun binding_deterministic() {
    val a = DecisionPreflight.preflight(proposal(), scope()) as Result.Approved
    val b = DecisionPreflight.preflight(proposal(), scope()) as Result.Approved
    assertEquals(a.binding.proposalDigest, b.binding.proposalDigest)
    val c = DecisionPreflight.preflight(
      proposal().copy(slotRevision = 43),
      scope().copy(slotRevision = 43)) as Result.Approved
    assertNotEquals(a.binding.proposalDigest, c.binding.proposalDigest)
  }
}
