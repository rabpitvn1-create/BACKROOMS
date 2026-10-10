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
  fun resolveSequence(candidates: List<IntentCandidate>, turnId: String, context: GameContext): List<GameCommand?> {
    var resolutionContext = context
    return candidates.mapIndexed { index, candidate ->
      val command = resolve(candidate, index, turnId, resolutionContext)
      val itemId = when (command) {
        is ItemCommand -> command.itemId
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
      GameIntent.PICKUP_ITEM, GameIntent.DROP_ITEM, GameIntent.USE_ITEM,
      GameIntent.TRANSFER_ITEM, GameIntent.EQUIP_ITEM, GameIntent.UNEQUIP_ITEM -> null
      GameIntent.PARTY_JOIN_REQUEST -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.ADD) }
      GameIntent.PARTY_REMOVE -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.REMOVE) }
      GameIntent.PARTY_FOLLOW -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.FOLLOW) }
      GameIntent.PARTY_SEPARATE -> target?.let { PartyCommand(commandId, turnId, actor, it, source, PartyCommand.Operation.SEPARATE) }
      GameIntent.INVENTORY_QUERY -> QueryCommand(commandId, turnId, actor, source = source, type = QueryCommand.Type.INVENTORY)
      GameIntent.PARTY_QUERY -> QueryCommand(commandId, turnId, actor, source = source, type = QueryCommand.Type.PARTY)
      GameIntent.CHARACTER_QUERY -> QueryCommand(commandId, turnId, actor, target, source, QueryCommand.Type.CHARACTER)
      GameIntent.STATUS_QUERY -> QueryCommand(commandId, turnId, actor, target, source, QueryCommand.Type.STATUS)
      else -> null
    }
  }

  private fun resolvedQuantity(candidate: IntentCandidate, actor: String, item: Pair<String, String>?, rawQuantity: Int, context: GameContext): Int = rawQuantity

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
