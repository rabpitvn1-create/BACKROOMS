package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

/** Protocol regressions only. Fakes do not qualify native storage/provider integration. */
class DecisionInteractionBoundaryTest {
  private val exact = "abcdefghijklmnop"
  private fun packet(): ActorContextBuilder.Packet {
    val persona = CompanionPersonaFixture.load("cao_minh")
    return ActorContextBuilder.Packet("slot-1", "cao_minh",
      ActorContextBuilder.CanonRefs(persona.traitRefs, persona.ethicalRefs, persona.voiceRefs),
      ActorContextBuilder.BrainView(emptyList(), emptyList(), "UNSET"), emptyList(), emptyList(),
      ActorContextBuilder.Pins(persona.sourceRevision, persona.sourceSha256,
        BrainContracts.RULE_VERSION, CompanionExposurePolicy.VERSION), false)
  }
  private fun scope() = DecisionPreflight.NativeScope("slot-1", 4, "cao_minh", "node-1",
    setOf("cao_minh"), setOf("cap.talk"), emptySet(), setOf("cao_minh"), "R17", BrainContracts.RULE_VERSION)
  private fun proposal() = DecisionPreflight.Proposal(DecisionPreflight.Intent.TALK, "cao_minh",
    null, "slot-1", 4, "cao_minh", "R17", BrainContracts.RULE_VERSION)
  private fun identity(p: ActorContextBuilder.Packet) = DecisionPreflight.InteractionIdentity("turn-1",
    CompanionPendingTurn.Request.fromPlayerInput("slot-1", "request-1", 4, "cao_minh", exact).inputDigest,
    ActorContextBuilder.digest(p), CompanionDigests.sha256("native-snapshot"))
  private class Ledger : CharacterDecisionOrchestrator.DecisionLedger {
    var row: CharacterDecisionOrchestrator.Decided? = null
    override fun get(bindingDigest: String) = row?.takeIf { it.binding.proposalDigest == bindingDigest }
    override fun put(decided: CharacterDecisionOrchestrator.Decided): Boolean {
      if (row != null) return false
      row = decided; return true
    }
  }
  private fun input(ledger: Ledger = Ledger(), provider: CharacterDecisionOrchestrator.DecisionProvider,
                    gate: CharacterDecisionOrchestrator.ReservationGate? = null): CharacterDecisionOrchestrator.Input {
    val p = packet()
    return CharacterDecisionOrchestrator.Input(p, scope(), proposal(), provider, ledger,
      CharacterDecisionOrchestrator.DecisionAuditor { _, _, _ -> null }, identity(p), gate, exact,
      CharacterDecisionOrchestrator.NativeInteractionVerifier { _, _, _, _ -> true })
  }
  private class Provider(private val result: CharacterDecisionOrchestrator.DecisionProvider.CallResult) :
    CharacterDecisionOrchestrator.DecisionProvider {
    var calls = 0
    override fun propose(packet: ActorContextBuilder.Packet, binding: DecisionPreflight.DecisionBinding,
                         repairHint: String?): CharacterDecisionOrchestrator.DecisionProvider.CallResult {
      calls++; return result
    }
  }
  private fun provider(attempts: Int? = 3) = Provider(CharacterDecisionOrchestrator.DecisionProvider.CallResult(
    """{"intent":"TALK","targetId":"cao_minh","utterance":"hello"}""", null, attempts))

  @Test fun interactionTupleSeparatesTurnsInputsContextAndSnapshot() {
    val original = identity(packet())
    fun digest(i: DecisionPreflight.InteractionIdentity) =
      (DecisionPreflight.preflight(proposal(), scope(), i) as DecisionPreflight.Result.Approved).binding.proposalDigest
    val base = digest(original)
    for (other in listOf(original.copy(turnId = "turn-2"),
      original.copy(inputDigest = CompanionDigests.sha256("different-input")),
      original.copy(contextDigest = CompanionDigests.sha256("different-context")),
      original.copy(sourceSnapshotDigest = CompanionDigests.sha256("different-snapshot"))))
      assertNotEquals(base, digest(other))
  }

  @Test fun missingReservationAdapterStopsBeforeProvider() {
    val p = provider()
    val out = CharacterDecisionOrchestrator.decideAuthoritative(input(provider = p)) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("reservation_adapter_missing", out.reason); assertEquals(0, p.calls)
  }

  @Test fun missingOrDeniedNativeProofStopsBeforeProvider() {
    val p = provider(); val i = input(provider = p, gate = CharacterDecisionOrchestrator.ReservationGate { true })
    val missing = CharacterDecisionOrchestrator.decideAuthoritative(i.copy(nativeVerifier = null)) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("native_interaction_adapter_missing", missing.reason)
    val denied = CharacterDecisionOrchestrator.decideAuthoritative(i.copy(nativeVerifier =
      CharacterDecisionOrchestrator.NativeInteractionVerifier { _, _, _, _ -> false })) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("native_interaction_unverified", denied.reason); assertEquals(0, p.calls)
  }

  @Test fun failedReservationWithholdsSpeechAndRecoveredRetryDoesNotCallProvider() {
    val p = provider(); val ledger = Ledger(); var durable = false
    val i = input(ledger, p, CharacterDecisionOrchestrator.ReservationGate { durable })
    val first = CharacterDecisionOrchestrator.decideAuthoritative(i) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("reservation_not_durable", first.reason)
    assertFalse(first.toString().contains("hello"))
    durable = true
    val retry = CharacterDecisionOrchestrator.decideAuthoritative(i) as CharacterDecisionOrchestrator.Outcome.DecidedOutcome
    assertEquals("hello", retry.decided.utterance); assertEquals(1, p.calls)
    assertEquals(0, retry.trace.providerCalls); assertEquals(0, retry.trace.httpAttempts)
  }

  @Test fun changedExactInputOrPacketRejectedBeforeProvider() {
    val p = provider(); val i = input(provider = p, gate = CharacterDecisionOrchestrator.ReservationGate { true })
    val changed = CharacterDecisionOrchestrator.decideAuthoritative(i.copy(exactPlayerInput = " " + exact)) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("interaction_input_mismatch", changed.reason)
    val other = CharacterDecisionOrchestrator.decideAuthoritative(i.copy(interaction = i.interaction!!.copy(
      contextDigest = CompanionDigests.sha256("wrong-context")))) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("context_digest_mismatch", other.reason); assertEquals(0, p.calls)
  }

  @Test fun measuredFallbackDispatchesRemainDistinctFromLogicalCalls() {
    val p = provider(7)
    val out = CharacterDecisionOrchestrator.decideAuthoritative(input(provider = p,
      gate = CharacterDecisionOrchestrator.ReservationGate { true })) as CharacterDecisionOrchestrator.Outcome.DecidedOutcome
    assertEquals(1, out.trace.providerCalls); assertEquals(7, out.trace.httpAttempts)
    assertEquals(7, out.decided.httpAttempts)
  }

  @Test fun unmeasuredAttemptsStayUnknownInsteadOfInventingOne() {
    val p = provider(null)
    val out = CharacterDecisionOrchestrator.decide(input(provider = p)) as CharacterDecisionOrchestrator.Outcome.DecidedOutcome
    assertNull(out.trace.httpAttempts)
  }

  @Test fun oversizedCapabilityListRejectedBeforeAudit() {
    val raw = """{"intent":"TALK","targetId":"cao_minh","capabilities":${org.json.JSONArray(List(257) { "cap.talk" })}}"""
    val p = Provider(CharacterDecisionOrchestrator.DecisionProvider.CallResult(raw, null, 1))
    val out = CharacterDecisionOrchestrator.decide(input(provider = p)) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("capability_bound", out.reason); assertEquals(1, out.trace.httpAttempts)
  }
  @Test fun typedRepairReauditsSameBindingAndUsesOnlyOneRepair() {
    val bindings = mutableListOf<DecisionPreflight.DecisionBinding>()
    val hints = mutableListOf<String?>(); var calls = 0; var audits = 0
    val p = object : CharacterDecisionOrchestrator.DecisionProvider {
      override fun propose(packet: ActorContextBuilder.Packet, binding: DecisionPreflight.DecisionBinding,
        repairHint: String?): CharacterDecisionOrchestrator.DecisionProvider.CallResult {
        calls++; bindings.add(binding); hints.add(repairHint)
        return CharacterDecisionOrchestrator.DecisionProvider.CallResult(
          """{"intent":"TALK","targetId":"cao_minh","utterance":"hello"}""", null, 2)
      }
    }
    val i = input(provider = p, gate = CharacterDecisionOrchestrator.ReservationGate { true }).copy(
      auditor = null, typedAuditor = CharacterDecisionOrchestrator.TypedDecisionAuditor { _, binding, _ ->
        bindings.add(binding); audits++
        if (audits == 1) CharacterDecisionOrchestrator.AuditVerdict(CharacterDecisionOrchestrator.AuditDisposition.REPAIRABLE,"voice_issue")
        else CharacterDecisionOrchestrator.AuditVerdict(CharacterDecisionOrchestrator.AuditDisposition.PASS)
      })
    val out = CharacterDecisionOrchestrator.decideAuthoritative(i) as CharacterDecisionOrchestrator.Outcome.DecidedOutcome
    assertEquals(2,calls); assertEquals(2,audits); assertEquals(listOf(null,"voice_issue"),hints)
    assertEquals(1,bindings.distinct().size); assertEquals(1,out.trace.repairs); assertEquals(4,out.trace.httpAttempts)
  }

  @Test fun typedHardAndInvalidPrivateCodesNeverRepairOrLock() {
    for (code in listOf("knowledge_leak", "private packet secret")) {
      val p = provider(); val ledger = Ledger()
      val i = input(ledger,p).copy(auditor = null,
        typedAuditor = CharacterDecisionOrchestrator.TypedDecisionAuditor { _, _, _ ->
          CharacterDecisionOrchestrator.AuditVerdict(CharacterDecisionOrchestrator.AuditDisposition.HARD,code)
        })
      val out = CharacterDecisionOrchestrator.decide(i) as CharacterDecisionOrchestrator.Outcome.Rejected
      assertEquals(if(code == "knowledge_leak") code else "typed_audit_invalid",out.reason)
      assertFalse(out.toString().contains("private packet secret")); assertEquals(1,p.calls); assertNull(ledger.row)
    }
  }

  @Test fun secondRepairableAuditExhaustsSharedBudget() {
    val p = provider(); val ledger = Ledger(); var audits = 0
    val i = input(ledger,p).copy(auditor = null,
      typedAuditor = CharacterDecisionOrchestrator.TypedDecisionAuditor { _, _, _ ->
        audits++; CharacterDecisionOrchestrator.AuditVerdict(CharacterDecisionOrchestrator.AuditDisposition.REPAIRABLE,"voice_issue")
      })
    val out = CharacterDecisionOrchestrator.decide(i) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("voice_issue",out.reason); assertEquals(2,p.calls); assertEquals(2,audits); assertNull(ledger.row)
  }

  @Test fun legacySemanticErrorStillFailsClosedWhenTypedAuditorWouldPass() {
    val p = provider(); val ledger = Ledger(); var typedCalls = 0
    val i = input(ledger,p).copy(auditor = CharacterDecisionOrchestrator.DecisionAuditor { _, _, _ -> "legacy_denied" },
      typedAuditor = CharacterDecisionOrchestrator.TypedDecisionAuditor { _, _, _ ->
        typedCalls++; CharacterDecisionOrchestrator.AuditVerdict(CharacterDecisionOrchestrator.AuditDisposition.PASS)
      })
    val out = CharacterDecisionOrchestrator.decide(i) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("legacy_denied",out.reason); assertEquals(1,p.calls); assertEquals(0,typedCalls); assertNull(ledger.row)
  }

  @Test fun nativeStateRaceAfterProviderRejectsBeforeLedgerOrReservation() {
    var nativeCurrent = true; var reservations = 0; val ledger = Ledger()
    val p = object : CharacterDecisionOrchestrator.DecisionProvider {
      override fun propose(packet: ActorContextBuilder.Packet,binding: DecisionPreflight.DecisionBinding,
        repairHint: String?): CharacterDecisionOrchestrator.DecisionProvider.CallResult {
        nativeCurrent = false
        return CharacterDecisionOrchestrator.DecisionProvider.CallResult(
          """{"intent":"TALK","targetId":"cao_minh","utterance":"hello"}""",null,1)
      }
    }
    val i = input(ledger,p,CharacterDecisionOrchestrator.ReservationGate { reservations++; true }).copy(
      nativeVerifier = CharacterDecisionOrchestrator.NativeInteractionVerifier { _, _, _, _ -> nativeCurrent })
    val out = CharacterDecisionOrchestrator.decideAuthoritative(i) as CharacterDecisionOrchestrator.Outcome.Rejected
    assertEquals("native_interaction_unverified",out.reason); assertNull(ledger.row); assertEquals(0,reservations)
  }

}
