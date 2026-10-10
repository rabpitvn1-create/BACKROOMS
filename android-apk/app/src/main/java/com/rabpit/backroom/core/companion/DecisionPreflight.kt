package com.rabpit.backroom.core.companion

/**
 * A1a native decision preflight validator (issue #510).
 *
 * Validates a companion actor's proposed decision intent against native state
 * BEFORE any RNG, provider call, or mutation. Validator-only: no I/O, no
 * provider, no mutation.
 *
 * Authority rules:
 * - Closed intent set per Action/Exit V1; unknown intents fail closed.
 * - Default timing/targets are native-authorized only; keyword parsing of the
 *   proposal text never grants authority.
 * - INTERACT from the player is a suggestion to the companion pipeline, never a
 *   command; this validator additionally refuses to decide FOR Cao Minh
 *   (direct control of the protagonist is outside the companion pipeline).
 * - Capabilities come from the native capability set, never from prose claims
 *   inside the proposal ("prose-forged capabilities" are rejected).
 * - Unsupported or ambiguous actor/intent/target fails closed.
 *
 * An approved decision binds the exact input bytes, identity, and native scope
 * into a [DecisionBinding] digest. The binding is the only thing downstream
 * stages (RNG tape, provider) may consume.
 */
internal object DecisionPreflight {
  /** Closed intent set (Action/Exit V1). */
  enum class Intent { TALK, MOVE, SEARCH, INSPECT, USE_ITEM, COMBAT_ACTION, WAIT, NONE }

  /** Native-authorized capability required per intent. */
  private val requiredCapability = mapOf(
    Intent.TALK to "cap.talk",
    Intent.MOVE to "cap.move",
    Intent.SEARCH to "cap.search",
    Intent.INSPECT to "cap.inspect",
    Intent.USE_ITEM to "cap.use_item",
    Intent.COMBAT_ACTION to "cap.combat",
    Intent.WAIT to "cap.wait"
    // NONE needs no capability.
  )

  /** Native adapter attests these values; constructing hashes alone is not native authority.
   * Input digest preserves exact original UTF-8 bytes. Java trim/codepoint count
   * is used only for minimum-input eligibility, never to collapse distinct requests.
   */
  data class InteractionIdentity(
    val turnId: String,
    val inputDigest: String,
    val contextDigest: String,
    val sourceSnapshotDigest: String
  ) {
    fun valid(): Boolean = identifier(turnId) &&
      listOf(inputDigest, contextDigest, sourceSnapshotDigest).all { it.matches(Regex("[0-9a-f]{64}")) }
  }

  private fun identifier(value: String): Boolean =
    value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))

  /** Native state the proposal is checked against. */
  data class NativeScope(
    val slotId: String,
    val slotRevision: Long,
    /** The companion actor this pipeline decides for. */
    val actorId: String,
    val sceneId: String,
    val presentActorIds: Set<String>,
    /** Native-authorized capability IDs. */
    val capabilities: Set<String>,
    val inventoryItemIds: Set<String>,
    /** Native-authorized target IDs for this scene/turn. */
    val legalTargetIds: Set<String>,
    val canonRevision: String,
    val ruleVersion: String
  )

  /** A companion's proposed decision. */
  data class Proposal(
    val intent: Intent,
    val targetId: String?,
    val itemId: String?,
    val slotId: String,
    val slotRevision: Long,
    val actorId: String,
    val canonRevision: String,
    val ruleVersion: String
  )

  /** Exact bound input/identity/scope; the only artifact downstream may use. */
  data class DecisionBinding(
    val proposalDigest: String,
    val actorId: String,
    val slotId: String,
    val slotRevision: Long,
    val intent: Intent,
    val targetId: String?,
    val itemId: String?,
    val canonRevision: String,
    val ruleVersion: String,
    val interaction: InteractionIdentity? = null,
    val sceneId: String? = null
  )

  sealed class Result {
    data class Approved(val binding: DecisionBinding) : Result()
    data class Rejected(val reason: String) : Result()
  }

  fun preflight(proposal: Proposal, scope: NativeScope,
                interaction: InteractionIdentity? = null): Result {
    if (interaction != null && !interaction.valid()) return Result.Rejected("interaction_invalid")
    val ids = listOf(scope.slotId, scope.actorId, scope.sceneId, proposal.slotId, proposal.actorId,
      scope.canonRevision, proposal.canonRevision, scope.ruleVersion, proposal.ruleVersion)
    val sets = listOf(scope.presentActorIds, scope.capabilities, scope.inventoryItemIds, scope.legalTargetIds)
    if (ids.any { !identifier(it) } || sets.any { it.size > 256 || it.any { id -> !identifier(id) } } ||
        proposal.targetId?.let { !it.isBlank() && !identifier(it) } == true ||
        proposal.itemId?.let { !it.isBlank() && !identifier(it) } == true || scope.slotRevision == Long.MAX_VALUE)
      return Result.Rejected("scope_bound_invalid")
    // 1. Identity / freshness / pins — before any semantic check.
    if (proposal.slotId != scope.slotId) return Result.Rejected("slot_mismatch")
    if (proposal.slotRevision != scope.slotRevision) return Result.Rejected("revision_stale")
    if (proposal.actorId != scope.actorId) return Result.Rejected("actor_mismatch")
    // Cao Minh makes his own audited decision; receiving a suggestion is valid.
    if (scope.slotId.isBlank() || scope.sceneId.isBlank() || scope.slotRevision < 0)
      return Result.Rejected("scope_invalid")
    if (scope.ruleVersion != BrainContracts.RULE_VERSION)
      return Result.Rejected("rule_unsupported")
    if (proposal.actorId !in scope.presentActorIds) return Result.Rejected("actor_not_present")
    if (proposal.canonRevision != scope.canonRevision ||
      proposal.ruleVersion != scope.ruleVersion) return Result.Rejected("pins_mismatch")
    // 3. Native-authorized capability (never prose-claimed).
    val need = requiredCapability[proposal.intent]
    if (need != null && need !in scope.capabilities) return Result.Rejected("capability_missing")
    // 4. Target legality.
    when (proposal.intent) {
      Intent.TALK, Intent.MOVE, Intent.INSPECT, Intent.COMBAT_ACTION -> {
        val t = proposal.targetId
        if (t.isNullOrBlank()) return Result.Rejected("target_ambiguous")
        if (t !in scope.legalTargetIds) return Result.Rejected("target_unreachable")
      }
      Intent.SEARCH, Intent.USE_ITEM -> {
        val t = proposal.targetId
        if (!t.isNullOrBlank() && t !in scope.legalTargetIds)
          return Result.Rejected("target_unreachable")
      }
      Intent.WAIT, Intent.NONE -> {
        if (!proposal.targetId.isNullOrBlank()) return Result.Rejected("target_unexpected")
      }
    }
    // 5. Resources.
    if (proposal.intent == Intent.USE_ITEM) {
      val item = proposal.itemId
      if (item.isNullOrBlank()) return Result.Rejected("item_ambiguous")
      if (item !in scope.inventoryItemIds) return Result.Rejected("item_missing")
    }
    if (proposal.intent != Intent.USE_ITEM && proposal.itemId != null)
      return Result.Rejected("item_unexpected")
    // 6. Bind exact input/identity/scope.
    val fields = mutableListOf<Any?>( 
      proposal.actorId, proposal.slotId, proposal.slotRevision,
      proposal.intent.name, proposal.targetId, proposal.itemId,
      proposal.canonRevision, proposal.ruleVersion, scope.sceneId,
      org.json.JSONArray(scope.presentActorIds.sorted()),
      org.json.JSONArray(scope.capabilities.sorted()),
      org.json.JSONArray(scope.inventoryItemIds.sorted()),
      org.json.JSONArray(scope.legalTargetIds.sorted()))
    if (interaction != null) fields.add(org.json.JSONArray(listOf(interaction.turnId,
      interaction.inputDigest, interaction.contextDigest, interaction.sourceSnapshotDigest)))
    val canonical = CompanionWaitCapture.canonical(org.json.JSONArray(fields))
    val binding = DecisionBinding(
      proposalDigest = CompanionDigests.sha256(canonical),
      actorId = proposal.actorId, slotId = proposal.slotId,
      slotRevision = proposal.slotRevision, intent = proposal.intent,
      targetId = proposal.targetId, itemId = proposal.itemId,
      canonRevision = proposal.canonRevision, ruleVersion = proposal.ruleVersion, interaction = interaction, sceneId = scope.sceneId)
    return Result.Approved(binding)
  }
}
