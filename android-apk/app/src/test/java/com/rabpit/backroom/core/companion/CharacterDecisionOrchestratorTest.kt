package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.ActorContextBuilder.Packet
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.DecisionLedger
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.DecisionProvider
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Input
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Outcome
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.DecisionPreflight.NativeScope
import com.rabpit.backroom.core.companion.DecisionPreflight.Proposal
import org.junit.Assert.*
import org.junit.Test

/**
 * A1b isolated audited decision orchestration tests (issue #511).
 *
 * Covers: happy path, preflight gate, one-repair re-audit, hard fail-closed,
 * provider-error no-fabrication, retry no-reroll, privacy-safe trace.
 * No network, no Android — all seams are fakes.
 */
class CharacterDecisionOrchestratorTest {
  private fun packet() = Packet(
    actorId = "luc_tram",
    canonRefs = ActorContextBuilder.CanonRefs(listOf("CAO-PER-01"), listOf("CAO-LIFE-02"), listOf()),
    brain = ActorContextBuilder.BrainView(emptyList(), emptyList(), "UNSET"),
    memories = emptyList(), sceneEvidence = emptyList(),
    pins = ActorContextBuilder.Pins("R17", "deadbeef",
      BrainContracts.RULE_VERSION, CompanionExposurePolicy.VERSION),
    truncated = false)

  private fun scope() = NativeScope(
    slotId = "slot-1", slotRevision = 42, actorId = "luc_tram", sceneId = "node-7",
    presentActorIds = setOf("luc_tram", "cao_minh"),
    capabilities = setOf("cap.talk", "cap.wait"),
    inventoryItemIds = emptySet(),
    legalTargetIds = setOf("cao_minh", "node-8"),
    canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION)

  private fun proposal() = Proposal(
    intent = Intent.TALK, targetId = "cao_minh", itemId = null,
    slotId = "slot-1", slotRevision = 42, actorId = "luc_tram",
    canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION)

  private class FakeProvider(val script: MutableList<DecisionProvider.CallResult>) : DecisionProvider {
    var calls = 0
    override fun propose(packet: Packet, binding: DecisionPreflight.DecisionBinding) =
      script[minOf(calls++, script.size - 1)]
  }

  private fun goodJson(target: String = "cao_minh") =
    """{"intent":"TALK","targetId":"$target"}"""

  private class MemLedger : DecisionLedger {
    private val map = mutableMapOf<String, Decided>()
    override fun get(bindingDigest: String) = map[bindingDigest]
    override fun put(decided: Decided): Boolean {
      if (map.containsKey(decided.binding.proposalDigest)) return false
      map[decided.binding.proposalDigest] = decided
      return true
    }
    fun size() = map.size
  }

  private fun input(provider: DecisionProvider, ledger: DecisionLedger = MemLedger(),
                    rngValues: MutableList<Long> = mutableListOf(777L, 888L)) = Input(
    packet = packet(), scope = scope(), proposal = proposal(),
    provider = provider, rng = CharacterDecisionOrchestrator.RngSource { rngValues.removeAt(0) },
    ledger = ledger)

  @Test fun happyPath_decidedAndLocked() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(goodJson(), null)))
    val out = CharacterDecisionOrchestrator.decide(input(provider, ledger))
    assertTrue(out is Outcome.DecidedOutcome)
    val decided = (out as Outcome.DecidedOutcome).decided
    assertEquals(777L, decided.rngValue)
    assertEquals(1, decided.providerCalls)
    assertEquals(1, ledger.size())
    assertEquals(1, out.trace.providerCalls)
    assertEquals("decided", out.trace.outcome)
  }

  @Test fun preflightReject_zeroProviderCalls() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(goodJson(), null)))
    val bad = input(provider, ledger).copy(proposal = proposal().copy(slotRevision = 41))
    val out = CharacterDecisionOrchestrator.decide(bad)
    assertTrue(out is Outcome.Rejected)
    assertEquals("preflight_revision_stale", (out as Outcome.Rejected).reason)
    assertEquals(0, provider.calls)
    assertEquals(0, ledger.size())
  }

  @Test fun softIssue_oneRepairWithReaudit() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(goodJson("node-8"), null),  // target_mismatch (soft)
      DecisionProvider.CallResult(goodJson(), null)))          // repair: clean
    val out = CharacterDecisionOrchestrator.decide(input(provider, ledger))
    assertTrue("got $out", out is Outcome.DecidedOutcome)
    val trace = (out as Outcome.DecidedOutcome).trace
    assertEquals(2, trace.providerCalls)
    assertEquals(1, trace.repairs)
    assertEquals("cao_minh", out.decided.targetId)
  }

  @Test fun hardIssue_failClosed_noRepair() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult("""{"intent":"TALK","targetId":"cao_minh","note":"CAO-LOCK-01"}""", null)))
    val out = CharacterDecisionOrchestrator.decide(input(provider, ledger))
    assertTrue(out is Outcome.Rejected)
    assertEquals("canon_lock_leak", (out as Outcome.Rejected).reason)
    assertEquals(1, provider.calls)  // hard: no repair attempt
    assertEquals(0, ledger.size())    // nothing fabricated or locked
  }

  @Test fun providerError_noFabrication() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(null, "timeout")))
    val out = CharacterDecisionOrchestrator.decide(input(provider, ledger))
    assertTrue(out is Outcome.Rejected)
    assertEquals("timeout", (out as Outcome.Rejected).reason)
    assertEquals(0, ledger.size())
  }

  @Test fun retry_noReroll_noProviderCall() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(goodJson(), null)))
    val inp = input(provider, ledger)
    val first = CharacterDecisionOrchestrator.decide(inp) as Outcome.DecidedOutcome
    val second = CharacterDecisionOrchestrator.decide(inp) as Outcome.DecidedOutcome
    assertEquals(first.decided.rngValue, second.decided.rngValue)  // 777, not 888
    assertEquals(777L, second.decided.rngValue)
    assertEquals(1, provider.calls)  // replay never re-calls
    assertEquals("locked_replay", second.trace.outcome)
    assertEquals(0, second.trace.providerCalls)
  }

  @Test fun trace_privacySafe() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(goodJson(), null)))
    val out = CharacterDecisionOrchestrator.decide(input(provider, ledger)) as Outcome.DecidedOutcome
    val traceStr = out.trace.toString()
    assertFalse(traceStr.contains("saw luc_tram"))
    assertFalse(traceStr.contains("CAO-PER-01"))
    assertTrue(traceStr.contains("luc_tram"))  // actor id is fine
  }

  @Test fun identityMismatch_rejected() {
    val ledger = MemLedger()
    val provider = FakeProvider(mutableListOf(
      DecisionProvider.CallResult(goodJson(), null)))
    val bad = input(provider, ledger).copy(proposal = proposal().copy(actorId = "stranger"))
    val out = CharacterDecisionOrchestrator.decide(bad)
    assertTrue(out is Outcome.Rejected)
    assertEquals("identity_mismatch", (out as Outcome.Rejected).reason)
    assertEquals(0, provider.calls)
  }
}
