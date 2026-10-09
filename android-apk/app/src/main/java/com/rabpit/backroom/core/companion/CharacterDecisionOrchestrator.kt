package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.DecisionPreflight.DecisionBinding
import com.rabpit.backroom.core.companion.DecisionPreflight.NativeScope
import com.rabpit.backroom.core.companion.DecisionPreflight.Proposal
import org.json.JSONObject

/**
 * Isolated decision protocol, not production provider/audit/storage integration.
 * Local schema checks do not replace semantic/canon audits. No RNG is consumed:
 * native action capture owns the ordered tape after a decision is locked.
 */
internal object CharacterDecisionOrchestrator {
  interface DecisionProvider {
    data class CallResult(val proposalJson: String?, val error: String?)
    fun propose(packet: ActorContextBuilder.Packet, binding: DecisionBinding,
      repairHint: String? = null): CallResult
  }
  fun interface DecisionAuditor {
    /** Required existing semantic/canon audit adapter; null means approved. */
    fun audit(packet: ActorContextBuilder.Packet, binding: DecisionBinding, proposalJson: String): String?
  }
  /** Production adapter must persist atomically; test fakes are not durability proof. */
  interface DecisionLedger {
    fun get(bindingDigest: String): Decided?
    fun put(decided: Decided): Boolean
  }
  data class Decided(
    val binding: DecisionBinding,
    val intent: DecisionPreflight.Intent,
    val targetId: String?, val itemId: String?,
    val providerCalls: Int,
    val utterance: String? = null
  )
  data class Trace(val actorId: String, val bindingDigest: String?, val intentName: String?,
    val providerCalls: Int, val repairs: Int, val outcome: String)
  sealed class Outcome {
    data class DecidedOutcome(val decided: Decided, val trace: Trace): Outcome()
    data class Rejected(val reason: String, val trace: Trace): Outcome()
  }
  data class Input(val packet: ActorContextBuilder.Packet, val scope: NativeScope,
    val proposal: Proposal, val provider: DecisionProvider, val ledger: DecisionLedger,
    val auditor: DecisionAuditor? = null)

  fun decide(input: Input): Outcome {
    val packet=input.packet
    if (packet.actorId != input.proposal.actorId || packet.actorId != input.scope.actorId)
      return reject(input,null,"identity_mismatch",0,0)
    if (packet.slotId != input.scope.slotId || packet.pins.personaRevision != input.scope.canonRevision ||
        packet.pins.ruleVersion != input.scope.ruleVersion ||
        packet.pins.policyVersion != CompanionExposurePolicy.VERSION)
      return reject(input,null,"context_binding_mismatch",0,0)
    val preflight=DecisionPreflight.preflight(input.proposal,input.scope)
    if (preflight is DecisionPreflight.Result.Rejected)
      return reject(input,null,"preflight_"+preflight.reason,0,0)
    val binding=(preflight as DecisionPreflight.Result.Approved).binding
    val auditor=input.auditor ?: return reject(input,binding,"audit_adapter_missing",0,0)
    val locked=try { input.ledger.get(binding.proposalDigest) } catch (_: Exception) {
      return reject(input,binding,"ledger_read_failed",0,0)
    }
    if (locked != null) return replay(input,binding,locked,0,0,"locked_replay")
    var hint: String?=null
    for (attempt in 0..1) {
      val result=try { input.provider.propose(packet,binding,hint) } catch (_: Exception) {
        return reject(input,binding,"provider_exception",attempt+1,attempt)
      }
      if (result.error != null)
        return reject(input,binding,safeReason(result.error,"provider_error"),attempt+1,attempt)
      val raw=result.proposalJson ?: return reject(input,binding,"proposal_missing",attempt+1,attempt)
      val failure=audit(raw,binding,input.scope)
      if (failure != null) {
        if (failure.hard || attempt==1) return reject(input,binding,failure.reason,attempt+1,attempt)
        hint=failure.reason; continue
      }
      val semantic=try { auditor.audit(packet,binding,raw) } catch (_: Exception) {
        return reject(input,binding,"audit_exception",attempt+1,attempt)
      }
      if (semantic != null)
        return reject(input,binding,safeReason(semantic,"semantic_audit_failed"),attempt+1,attempt)
      val json=JSONObject(raw)
      val decided=Decided(binding,binding.intent,binding.targetId,binding.itemId,attempt+1,
        optionalString(json,"utterance"))
      val inserted=try { input.ledger.put(decided) } catch (_: Exception) {
        return reject(input,binding,"ledger_write_failed",attempt+1,attempt)
      }
      if (inserted) return replay(input,binding,decided,attempt+1,attempt,"decided")
      val winner=try { input.ledger.get(binding.proposalDigest) } catch (_: Exception) { null }
      if (winner==null) return reject(input,binding,"ledger_race_missing",attempt+1,attempt)
      return replay(input,binding,winner,attempt+1,attempt,"locked_race")
    }
    return reject(input,binding,"repair_exhausted",2,1)
  }

  private fun replay(input: Input, binding: DecisionBinding, locked: Decided,
    calls: Int, repairs: Int, code: String): Outcome {
    if (locked.binding != binding || locked.intent != binding.intent ||
        locked.targetId != binding.targetId || locked.itemId != binding.itemId)
      return reject(input,binding,"ledger_binding_mismatch",calls,repairs)
    return Outcome.DecidedOutcome(locked,Trace(binding.actorId,binding.proposalDigest,binding.intent.name,calls,repairs,code))
  }
  private data class AuditFailure(val reason: String,val hard: Boolean)
  private fun optionalString(json: JSONObject,key: String): String? =
    json.opt(key).let { if (it==null || it==JSONObject.NULL) null else it as String }
  private fun audit(raw: String,binding: DecisionBinding,scope: NativeScope): AuditFailure? {
    if (raw.length>65536) return AuditFailure("proposal_bound",true)
    val json=try { JSONObject(raw) } catch (_: Exception) { return AuditFailure("proposal_malformed",true) }
    if (raw.contains("CAO-LOCK") || raw.contains("knowledgeLockRefs"))
      return AuditFailure("canon_lock_leak",true)
    val allowed=setOf("intent","targetId","itemId","capabilities","utterance")
    if (json.keys().asSequence().any { it !in allowed }) return AuditFailure("proposal_unknown_field",true)
    for (key in listOf("intent","targetId","itemId","utterance")) {
      val value=json.opt(key)
      if (value!=null && value!=JSONObject.NULL && value !is String)
        return AuditFailure("proposal_field_type",true)
    }
    if (json.opt("intent") != binding.intent.name) return AuditFailure("intent_mismatch",false)
    if (optionalString(json,"targetId") != binding.targetId) return AuditFailure("target_mismatch",false)
    if (optionalString(json,"itemId") != binding.itemId) return AuditFailure("item_mismatch",false)
    val caps=json.opt("capabilities")
    if (caps!=null && caps !is org.json.JSONArray) return AuditFailure("capability_type",true)
    if (caps is org.json.JSONArray) for (i in 0 until caps.length()) {
      val value=caps.opt(i)
      if (value !is String || value !in scope.capabilities) return AuditFailure("capability_forged",true)
    }
    val speech=optionalString(json,"utterance")
    if (speech!=null && (binding.intent!=DecisionPreflight.Intent.TALK || speech.isBlank() || speech.length>500))
      return AuditFailure("utterance_invalid",true)
    return null
  }
  private fun safeReason(reason: String,fallback: String): String =
    if (reason.matches(Regex("[a-z][a-z0-9_]{0,63}"))) reason else fallback
  private fun reject(input: Input,binding: DecisionBinding?,reason: String,calls: Int,repairs: Int)=
    Outcome.Rejected(reason,Trace(input.packet.actorId,binding?.proposalDigest,
      binding?.intent?.name ?: input.proposal.intent.name,calls,repairs,"rejected"))
}
