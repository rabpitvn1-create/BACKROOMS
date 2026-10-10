package com.rabpit.backroom.core.companion

import org.json.JSONObject

/**
 * First actor-owned decision boundary for INTERACT.
 *
 * Player text is merely evidence/advice. Only the isolated actor's provider
 * may propose an intent; native capabilities and targets decide whether it is
 * even eligible. The result is NOT a committed action or public narration.
 * The production host must next call decideAuthoritative() with native slot
 * verification, semantic audit and durable reservation.
 */
internal object CompanionActorIntentGateway {
  data class Selected(val proposal: DecisionPreflight.Proposal,
                      val binding: DecisionPreflight.DecisionBinding)

  sealed class Result {
    data class Accepted(val selected: Selected) : Result()
    data class Rejected(val reason: String) : Result()
  }

  private fun reject(reason: String) = Result.Rejected(reason)
  private val allowedKeys = setOf("intent", "targetId", "itemId", "utterance")

  /** The provider cannot select actor identity, scene, capabilities, revision or pins. */
  fun select(
    raw: String,
    scope: DecisionPreflight.NativeScope,
    interaction: DecisionPreflight.InteractionIdentity
  ): Result {
    if (!interaction.valid()) return reject("interaction_invalid")
    if (raw.toByteArray(Charsets.UTF_8).size > 4096 || !Charsets.UTF_8.newEncoder().canEncode(raw))
      return reject("proposal_size_invalid")
    val json = try { JSONObject(raw) } catch (_: Exception) { return reject("proposal_malformed") }
    if (json.keys().asSequence().any { it !in allowedKeys }) return reject("proposal_unknown_field")
    val rawIntent = json.opt("intent")
    if (rawIntent !is String || rawIntent.isEmpty()) return reject("intent_invalid")
    val intent = DecisionPreflight.Intent.entries.firstOrNull { it.name == rawIntent }
      ?: return reject("intent_unsupported")
    fun optionalId(key: String): String? {
      val value = json.opt(key)
      if (value == null || value == JSONObject.NULL) return null
      if (value !is String || value.isBlank() ||
          !value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) return ""
      return value
    }
    val utterance = json.opt("utterance")
    if (utterance != null && utterance != JSONObject.NULL &&
        (utterance !is String || intent != DecisionPreflight.Intent.TALK ||
          utterance.isBlank() || utterance.length > 500 ||
          utterance.any { it.isISOControl() && it != '\n' && it != '\t' }))
      return reject("utterance_invalid")
    val target = optionalId("targetId")
    val item = optionalId("itemId")
    if (target == "" || item == "") return reject("target_or_item_invalid")
    // Only native, currently observed facts may grant an intent and target.
    // Do not let free-text persuasion forge a route or capability.
    val candidate = DecisionPreflight.Proposal(
      intent = intent,
      targetId = target,
      itemId = item,
      slotId = scope.slotId,
      slotRevision = scope.slotRevision,
      actorId = scope.actorId,
      canonRevision = scope.canonRevision,
      ruleVersion = scope.ruleVersion
    )
    return when (val verified = DecisionPreflight.preflight(candidate, scope, interaction)) {
      is DecisionPreflight.Result.Rejected -> reject("preflight_" + verified.reason)
      is DecisionPreflight.Result.Approved -> Result.Accepted(Selected(candidate, verified.binding))
    }
  }

  /** A production adapter supplies a measured provider, a real native verifier,
   * audited actor-private context, and a durable reservation writer. None may be
   * replaced with a WebView/GM boolean.
   */
  fun decideAuthoritative(
    rawActorProposal: String,
    packet: ActorContextBuilder.Packet,
    scope: DecisionPreflight.NativeScope,
    interaction: DecisionPreflight.InteractionIdentity,
    exactPlayerInput: String,
    provider: CharacterDecisionOrchestrator.DecisionProvider,
    ledger: CharacterDecisionOrchestrator.DecisionLedger,
    auditor: CharacterDecisionOrchestrator.TypedDecisionAuditor,
    nativeVerifier: CharacterDecisionOrchestrator.NativeInteractionVerifier,
    reservationGate: CharacterDecisionOrchestrator.ReservationGate
  ): CharacterDecisionOrchestrator.Outcome {
    val selected = select(rawActorProposal, scope, interaction)
    if (selected !is Result.Accepted) {
      val reason = (selected as Result.Rejected).reason
      return CharacterDecisionOrchestrator.Outcome.Rejected(
        reason, CharacterDecisionOrchestrator.Trace(scope.actorId,null,null,0,0,"rejected",0))
    }
    val input = CharacterDecisionOrchestrator.Input(
      packet = packet,
      scope = scope,
      proposal = selected.selected.proposal,
      provider = provider,
      ledger = ledger,
      interaction = interaction,
      reservationGate = reservationGate,
      exactPlayerInput = exactPlayerInput,
      nativeVerifier = nativeVerifier,
      typedAuditor = auditor
    )
    return CharacterDecisionOrchestrator.decideAuthoritative(input)
  }
}
