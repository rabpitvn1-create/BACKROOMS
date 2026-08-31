package com.rabpit.backroom.core

import java.security.MessageDigest

data class PipelineResult(
  val intentResult: IntentResult,
  val commands: List<GameCommand>,
  val unresolved: List<IntentCandidate>
)

class CommandResolver(
  private val actorResolver: ActorResolver = DefaultActorResolver(),
  private val targetResolver: TargetResolver = DefaultTargetResolver(),
  private val itemResolver: ItemResolver = DefaultItemResolver(),
  private val quantityResolver: QuantityResolver = DefaultQuantityResolver()
) {
  private data class CopyTemplateRef(val item: Pair<String, String>, val templateId: String?, val templateSlot: Int?)

  fun resolveSequence(candidates: List<IntentCandidate>, turnId: String, context: GameContext): List<GameCommand?> {
    var resolutionContext = context
    return candidates.mapIndexed { index, candidate ->
      val command = resolve(candidate, index, turnId, resolutionContext)
      val itemId = when (command) {
        is ItemCommand -> command.itemId
        is OmnivaultCommand -> command.itemId
        else -> null
      }
      if (itemId != null) resolutionContext = resolutionContext.copy(lastReferencedItemId = itemId)
      command
    }
  }

  fun resolve(candidate: IntentCandidate, index: Int, turnId: String, context: GameContext): GameCommand? {
    val actor = actorResolver.resolve(candidate.clause, context) ?: return null
    val target = targetResolver.resolve(candidate.clause, context)
    val commandId = stableCommandId(turnId, index, candidate.clause)
    val item = itemResolver.resolve(candidate.clause, context)
    val rawQuantity = quantityResolver.resolve(candidate.clause)
    val quantity = resolvedQuantity(candidate, actor, item, rawQuantity, context)
    val source = candidate.source
    return when (candidate.intent) {
      GameIntent.PICKUP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.PICKUP, it, quantity) }
      GameIntent.DROP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.DROP, it, quantity) }
      GameIntent.USE_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.USE, it, quantity) }
      GameIntent.TRANSFER_ITEM -> item?.let {
        val parties = resolveTransferParties(candidate.clause, context, actor, target)
        itemCommand(commandId, turnId, parties.first, parties.second, source, ItemCommand.Operation.TRANSFER, it, quantity)
      }
      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, resolveEquipmentSlot(candidate.clause, actor, it, context, false)) }
      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, resolveEquipmentSlot(candidate.clause, actor, it, context, true)) }
      GameIntent.OMNIVAULT_STORE -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.STORE, it, quantity) }
      GameIntent.OMNIVAULT_WITHDRAW -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.WITHDRAW, it, quantity) }
      GameIntent.OMNIVAULT_SCAN -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.SCAN, it, quantity) }
      GameIntent.OMNIVAULT_COPY -> resolveCopyTemplate(candidate.clause, item, context)?.let { ref ->
        vaultCommand(
          commandId, turnId, actor, source, OmnivaultCommand.Operation.COPY, ref.item, quantity,
          templateId = ref.templateId,
          templateSlot = ref.templateSlot,
          targetTotal = copyTargetTotal(candidate.clause, quantity)
        )
      }
      GameIntent.OMNIVAULT_RESTORE -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.RESTORE, it, quantity) }
      GameIntent.PARTY_JOIN_REQUEST -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.ADD) }
      GameIntent.PARTY_REMOVE -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.REMOVE) }
      GameIntent.PARTY_FOLLOW -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.FOLLOW) }
      GameIntent.PARTY_SEPARATE -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.SEPARATE) }
      GameIntent.INVENTORY_QUERY -> QueryCommand(commandId, turnId, actor, source = source, type = QueryCommand.Type.INVENTORY)
      GameIntent.OMNIVAULT_QUERY -> QueryCommand(commandId, turnId, actor, source = source, type = QueryCommand.Type.OMNIVAULT)
      GameIntent.PARTY_QUERY -> QueryCommand(commandId, turnId, actor, source = source, type = QueryCommand.Type.PARTY)
      GameIntent.CHARACTER_QUERY -> QueryCommand(commandId, turnId, actor, target, source, QueryCommand.Type.CHARACTER)
      GameIntent.STATUS_QUERY -> QueryCommand(commandId, turnId, actor, target, source, QueryCommand.Type.STATUS)
      else -> null
    }
  }

  private fun resolvedQuantity(candidate: IntentCandidate, actor: String, item: Pair<String, String>?, rawQuantity: Int, context: GameContext): Int = rawQuantity

  private fun copyTargetTotal(clause: String, rawQuantity: Int): Int? =
    if (Regex("(?:thành|tổng\\s+cộng|đủ)\\s+(?:\\d+|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|một\\s+trăm)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) rawQuantity else null

  private fun resolveCopyTemplate(clause: String, item: Pair<String, String>?, context: GameContext): CopyTemplateRef? {
    val explicitSlot = Regex("(?:slot|ô|mẫu)\\s*([1-3])\\b", RegexOption.IGNORE_CASE)
      .find(clause)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (explicitSlot != null) {
      val slot = context.state.omnivault.scanSlots.firstOrNull { it.slot == explicitSlot } ?: return item?.let { CopyTemplateRef(it, null, explicitSlot) }
      return CopyTemplateRef(slot.sourceItemId to slot.templateItem.name, ItemIdentity.templateId(slot), slot.slot)
    }
    if (item == null) {
      val only = context.state.omnivault.scanSlots.singleOrNull() ?: return null
      return CopyTemplateRef(only.sourceItemId to only.templateItem.name, ItemIdentity.templateId(only), only.slot)
    }
    val matches = context.state.omnivault.scanSlots.filter { slot ->
      slot.sourceItemId == item.first || slot.templateItem.itemId == item.first || slot.templateItem.archetypeId == item.first ||
        slot.templateItem.name.equals(item.second, true)
    }
    return if (matches.size == 1) {
      val slot = matches.single()
      CopyTemplateRef(slot.sourceItemId to slot.templateItem.name, ItemIdentity.templateId(slot), slot.slot)
    } else CopyTemplateRef(item, null, null)
  }

  private fun resolveTransferParties(
    clause: String,
    context: GameContext,
    defaultActor: String,
    defaultTarget: String?
  ): Pair<String, String?> {
    val verb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE).find(clause) ?: return defaultActor to defaultTarget
    val mentions = context.actorAliases.entries.mapNotNull { (alias, id) ->
      Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE).find(clause)?.let { Triple(it.range.first, it.range.last, id) }
    }.sortedBy { it.first }
    val actor = mentions.lastOrNull { it.second < verb.range.first }?.third ?: defaultActor
    val explicitTargetStart = Regex("(?:cho|sang)\\s+", RegexOption.IGNORE_CASE).find(clause, verb.range.last + 1)?.range?.last
    val target = if (explicitTargetStart != null) {
      mentions.firstOrNull { it.first > explicitTargetStart && it.third != actor }?.third
        ?: mentions.firstOrNull { it.first > explicitTargetStart }?.third
    } else {
      mentions.firstOrNull { it.first > verb.range.last && it.third != actor }?.third
        ?: mentions.firstOrNull { it.first > verb.range.last }?.third
    }
    return actor to (target ?: defaultTarget)
  }

  private fun resolveEquipmentSlot(
    clause: String,
    actor: String,
    item: Pair<String, String>,
    context: GameContext,
    unequip: Boolean
  ): String {
    val existing = context.state.equipment[actor]?.slots?.entries?.firstOrNull { it.value == item.first }?.key
    if (unequip && existing != null) return existing
    val owned = context.state.inventories[actor]?.items?.get(item.first)
    owned?.metadata?.get("equipmentSlot")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    owned?.metadata?.get("slot")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    KaiStartingEquipment.slotFor(item.first, item.second)?.let { return it }
    val lower = clause.lowercase()
    return when {
      lower.contains("nhẫn") || lower.contains("ring") -> "ring"
      lower.contains("giáp") || lower.contains("armor") || lower.contains("áo") || lower.contains("mặc") -> "armor"
      else -> existing ?: "weapon"
    }
  }

  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =
    ItemCommand(id, turn, actor, target, source, operation, item.first, item.second, quantity, slot)

  private fun vaultCommand(
    id: String,
    turn: String,
    actor: String,
    source: CommandSource,
    operation: OmnivaultCommand.Operation,
    item: Pair<String, String>,
    quantity: Int,
    templateId: String? = null,
    templateSlot: Int? = null,
    targetTotal: Int? = null
  ) = OmnivaultCommand(
    id, turn, actor,
    source = source,
    operation = operation,
    itemId = item.first,
    itemName = item.second,
    quantity = quantity,
    timestampEpochMs = System.currentTimeMillis(),
    templateId = templateId,
    templateSlot = templateSlot,
    targetTotal = targetTotal
  )

  private fun stableCommandId(turnId: String, index: Int, clause: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$turnId|$index|${clause.trim().lowercase()}".toByteArray())
    return "$turnId:${digest.take(8).joinToString("") { "%02x".format(it) }}"
  }
}

class IntentPipeline(
  private val rules: RuleIntentInterpreter,
  private val localModel: IntentInterpreter,
  private val gemini: IntentInterpreter,
  private val resolver: CommandResolver = CommandResolver()
) {
  suspend fun interpret(input: String, turnId: String, context: GameContext): PipelineResult {
    val ruleResult = rules.interpret(input, context)
    val finalCandidates = mutableListOf<IntentCandidate>()
    for (candidate in ruleResult.candidates) {
      if (candidate.confidence == IntentConfidence.HIGH || candidate.intent == GameIntent.NO_ACTION) {
        finalCandidates += candidate
        continue
      }
      val local = localModel.interpret(candidate.clause, context).candidates.singleOrNull()
      if (local != null && local.confidence == IntentConfidence.HIGH) {
        finalCandidates += local
        continue
      }
      val remote = gemini.interpret(candidate.clause, context).candidates.singleOrNull()
      finalCandidates += remote ?: candidate
    }
    val result = IntentResult(finalCandidates, finalCandidates.any { it.intent == GameIntent.UNKNOWN })
    val resolved = finalCandidates.mapIndexed { index, candidate -> candidate to resolver.resolve(candidate, index, turnId, context) }
    val commands = resolved.mapNotNull { it.second }
    val unresolved = resolved.filter { (candidate, command) -> candidate.intent != GameIntent.NO_ACTION && command == null }.map { it.first }
    return PipelineResult(result, commands, unresolved)
  }
}
