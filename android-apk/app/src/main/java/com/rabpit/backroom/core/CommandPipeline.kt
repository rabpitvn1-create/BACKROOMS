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
  fun resolve(candidate: IntentCandidate, index: Int, turnId: String, context: GameContext): GameCommand? {
    val actor = actorResolver.resolve(candidate.clause, context) ?: return null
    val target = targetResolver.resolve(candidate.clause, context)
    val commandId = stableCommandId(turnId, index, candidate.clause)
    val item = itemResolver.resolve(candidate.clause, context)
    val quantity = quantityResolver.resolve(candidate.clause)
    val source = candidate.source
    return when (candidate.intent) {
      GameIntent.PICKUP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.PICKUP, it, quantity) }
      GameIntent.DROP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.DROP, it, quantity) }
      GameIntent.USE_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.USE, it, quantity) }
      GameIntent.TRANSFER_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.TRANSFER, it, quantity) }
      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, "weapon") }
      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, "weapon") }
      GameIntent.OMNIVAULT_STORE -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.STORE, it, quantity) }
      GameIntent.OMNIVAULT_WITHDRAW -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.WITHDRAW, it, quantity) }
      GameIntent.OMNIVAULT_SCAN -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.SCAN, it, quantity) }
      GameIntent.OMNIVAULT_COPY -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.COPY, it, quantity) }
      GameIntent.OMNIVAULT_RESTORE -> item?.let {
        val restored = restoreResult(candidate.clause)
        OmnivaultCommand(
          commandId, turnId, actor, source = source, operation = OmnivaultCommand.Operation.RESTORE,
          itemId = it.first, itemName = it.second, quantity = quantity, timestampEpochMs = System.currentTimeMillis(),
          restoreResultItemId = restored?.first, restoreResultName = restored?.second
        )
      }
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

  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =
    ItemCommand(id, turn, actor, target, source, operation, item.first, item.second, quantity, slot)

  private fun vaultCommand(id: String, turn: String, actor: String, source: CommandSource, operation: OmnivaultCommand.Operation, item: Pair<String, String>, quantity: Int) =
    OmnivaultCommand(id, turn, actor, source = source, operation = operation, itemId = item.first, itemName = item.second, quantity = quantity, timestampEpochMs = System.currentTimeMillis())

  private fun restoreResult(clause: String): Pair<String, String>? {
    val match = Regex("(?:nhận được|biến thành|trở thành|thành)\\s+(.+)$", RegexOption.IGNORE_CASE).find(clause) ?: return null
    val name = match.groupValues[1].replace(Regex("[.,;:!?]+$"), "").trim()
    if (name.isBlank()) return null
    return canonicalItemId(name) to name
  }

  private fun canonicalItemId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }

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
