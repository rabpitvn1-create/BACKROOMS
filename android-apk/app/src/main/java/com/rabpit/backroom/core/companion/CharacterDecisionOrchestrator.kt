package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.DecisionPreflight.DecisionBinding
import com.rabpit.backroom.core.companion.DecisionPreflight.NativeScope
import com.rabpit.backroom.core.companion.DecisionPreflight.Proposal
import org.json.JSONObject

/**
 * A1b isolated audited character-decision orchestration (issue #511).
 *
 * Takes a companion actor's private packet (#509) and proposed intent, runs the
 * native preflight (#510), calls the provider pool through a narrow seam, audits
 * the returned proposal, and locks the decision+RNG tuple — all before any
 * outcome-bearing response.
 *
 * Hard rules:
 * - A provider proposal NEVER becomes authoritative state, brain content, or an
 *   utterance directly. It is an audited candidate; only the locked [Decided]
 *   record is consumable downstream.
 * - Native preflight + semantic/canon/local audits are mandatory. Exactly one
 *   repair attempt is allowed, and the repaired proposal is fully re-audited.
 *   Audits are never reduced to hit a call-count target.
 * - RNG is drawn once per binding digest and locked in the [DecisionLedger].
 *   Retry or alias with the same digest returns the locked record — never
 *   rerolls, never calls the provider again.
 * - An outcome-bearing response is produced only AFTER the durable reservation.
 * - Provider errors or unsupported output fail closed: no fabricated reaction,
 *   goal, or event is ever invented.
 * - The [Trace] is privacy-safe: digests, ids, and reason codes only — never
 *   packet contents or proposal text.
 *
 * No UI. No blanket action execution. Pure orchestration over injected seams
 * (provider, RNG, ledger) so tests run without network or Android.
 */
internal object CharacterDecisionOrchestrator {

  /** Narrow provider seam. Implementations wrap the real provider pool. */
  interface DecisionProvider {
    data class CallResult(val proposalJson: String?, val error: String?)
    fun propose(packet: ActorContextBuilder.Packet, binding: DecisionBinding): CallResult
  }

  fun interface RngSource { fun nextLong(): Long }

  /** Durable reservation. First put wins; later puts return false. */
  interface DecisionLedger {
    fun get(bindingDigest: String): Decided?
    fun put(decided: Decided): Boolean
  }

  /** Audited, locked decision — the only outcome-bearing artifact. */
  data class Decided(
    val binding: DecisionBinding,
    val intent: DecisionPreflight.Intent,
    val targetId: String?,
    val itemId: String?,
    val rngValue: Long,
    val providerCalls: Int
  )

  /** Privacy-safe trace: no packet contents, no proposal text. */
  data class Trace(
    val actorId: String,
    val bindingDigest: String?,
    val intentName: String?,
    val providerCalls: Int,
    val repairs: Int,
    val outcome: String
  )

  sealed class Outcome {
    data class DecidedOutcome(val decided: Decided, val trace: Trace) : Outcome()
    data class Rejected(val reason: String, val trace: Trace) : Outcome()
  }

  data class Input(
    val packet: ActorContextBuilder.Packet,
    val scope: NativeScope,
    val proposal: Proposal,
    val provider: DecisionProvider,
    val rng: RngSource,
    val ledger: DecisionLedger
  )

  fun decide(input: Input): Outcome {
    val packet = input.packet
    // Packet/scope/proposal must agree on actor identity.
    if (packet.actorId != input.proposal.actorId || packet.actorId != input.scope.actorId) {
      return reject(input, null, "identity_mismatch", 0, 0)
    }
    // 1. Native preflight first; rejection means zero provider calls.
    val preflight = DecisionPreflight.preflight(input.proposal, input.scope)
    if (preflight is DecisionPreflight.Result.Rejected) {
      return reject(input, null, "preflight_" + preflight.reason, 0, 0)
    }
    val binding = (preflight as DecisionPreflight.Result.Approved).binding
    // 2. Retry/alias: locked record wins — no reroll, no provider call.
    input.ledger.get(binding.proposalDigest)?.let { locked ->
      return Outcome.DecidedOutcome(locked, Trace(
        packet.actorId, binding.proposalDigest, binding.intent.name,
        providerCalls = 0, repairs = 0, outcome = "locked_replay"))
    }
    // 3. Provider call + audit; exactly one repair with full re-audit.
    var calls = 0
    var repairs = 0
    val attempt: (repairHint: String?) -> DecisionProvider.CallResult = { hint ->
      calls++
      input.provider.propose(packet, binding)
    }
    val first = attempt(null)
    val firstAudit = first.proposalJson?.let { audit(it, binding, input.scope) }
    if (first.error == null && firstAudit == null) {
      return lock(input, binding, first.proposalJson!!, calls, repairs)
    }
    val hardFailure = first.error != null || firstAudit?.hard == true
    if (hardFailure) {
      // Provider error or hard audit failure: fail closed, never fabricate.
      return reject(input, binding, first.error ?: firstAudit!!.reason, calls, repairs)
    }
    // 4. One repair, fully re-audited (audits never reduced for call count).
    repairs++
    val second = attempt(firstAudit!!.reason)
    val secondAudit = second.proposalJson?.let { audit(it, binding, input.scope) }
    if (second.error == null && secondAudit == null) {
      return lock(input, binding, second.proposalJson!!, calls, repairs)
    }
    return reject(input, binding, second.error ?: secondAudit!!.reason, calls, repairs)
  }

  private data class AuditFailure(val reason: String, val hard: Boolean)

  /**
   * Semantic/canon/local audit of a provider proposal against the bound scope.
   * Returns null when clean. Hard failures skip repair (fail closed immediately).
   */
  private fun audit(proposalJson: String, binding: DecisionBinding, scope: NativeScope): AuditFailure? {
    val json = try { JSONObject(proposalJson) } catch (_: Exception) {
      return AuditFailure("proposal_malformed", hard = true)
    }
    // Canon firewall: knowledge-lock refs or whole-codex markers must never appear.
    val raw = proposalJson
    if (raw.contains("CAO-LOCK") || raw.contains("knowledgeLockRefs"))
      return AuditFailure("canon_lock_leak", hard = true)
    // Semantic: intent must match the bound intent; no capability invention.
    val intentName = json.optString("intent", "")
    if (intentName != binding.intent.name)
      return AuditFailure("intent_mismatch", hard = false)
    val claimedCaps = json.optJSONArray("capabilities")?.let { arr ->
      (0 until arr.length()).map { arr.optString(it) }
    } ?: emptyList()
    val forged = claimedCaps.filter { it !in scope.capabilities }
    if (forged.isNotEmpty()) return AuditFailure("capability_forged", hard = true)
    // Local: target/item must stay within the bound scope.
    val target = json.optString("targetId", null)?.ifBlank { null }
    if (target != binding.targetId) {
      if (target != null && target !in scope.legalTargetIds)
        return AuditFailure("target_unbound", hard = false)
      if (target != binding.targetId && binding.targetId != null)
        return AuditFailure("target_mismatch", hard = false)
    }
    val item = json.optString("itemId", null)?.ifBlank { null }
    if (item != binding.itemId) {
      if (item != null && item !in scope.inventoryItemIds)
        return AuditFailure("item_unbound", hard = false)
      if (binding.intent == DecisionPreflight.Intent.USE_ITEM)
        return AuditFailure("item_mismatch", hard = false)
    }
    return null
  }

  private fun lock(
    input: Input, binding: DecisionBinding, proposalJson: String, calls: Int, repairs: Int
  ): Outcome {
    val json = JSONObject(proposalJson)
    val decided = Decided(
      binding = binding,
      intent = binding.intent,
      targetId = json.optString("targetId", null)?.ifBlank { null },
      itemId = json.optString("itemId", null)?.ifBlank { null },
      rngValue = input.rng.nextLong(),
      providerCalls = calls)
    // Durable reservation BEFORE any outcome-bearing response. First put wins.
    if (!input.ledger.put(decided)) {
      val locked = input.ledger.get(binding.proposalDigest)!!
      return Outcome.DecidedOutcome(locked, Trace(
        input.packet.actorId, binding.proposalDigest, binding.intent.name,
        providerCalls = calls, repairs = repairs, outcome = "locked_race"))
    }
    return Outcome.DecidedOutcome(decided, Trace(
      input.packet.actorId, binding.proposalDigest, binding.intent.name,
      providerCalls = calls, repairs = repairs, outcome = "decided"))
  }

  private fun reject(
    input: Input, binding: DecisionBinding?, reason: String, calls: Int, repairs: Int
  ): Outcome.Rejected {
    // Never fabricate: rejection carries no proposal, no reaction, no event.
    return Outcome.Rejected(reason, Trace(
      actorId = input.packet.actorId,
      bindingDigest = binding?.proposalDigest,
      intentName = binding?.intent?.name ?: input.proposal.intent.name,
      providerCalls = calls, repairs = repairs, outcome = "rejected"))
  }
}
