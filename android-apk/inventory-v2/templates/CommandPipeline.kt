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
    val defaultActor = actorResolver.resolve(candidate.clause, context) ?: KAI_ID
    val target = targetResolver.resolve(candidate.clause, context)
    val item = itemResolver.resolve(candidate.clause, context)
    val quantity = quantityResolver.resolve(candidate.clause)
    val commandId = stableCommandId(turnId, index, candidate.clause)
    val source = candidate.source

    return when (candidate.intent) {
      GameIntent.DISCARD_ITEM -> item?.let {
        val actual = resolveInventoryItem(defaultActor, it, context) ?: return null
        ItemCommand(commandId, turnId, defaultActor, source = source, operation = ItemCommand.Operation.DISCARD,
          itemId = actual.first, itemName = actual.second, quantity = quantity)
      }
      GameIntent.USE_ITEM -> item?.let {
        val actual = resolveInventoryItem(defaultActor, it, context) ?: return null
        ItemCommand(commandId, turnId, defaultActor, source = source, operation = ItemCommand.Operation.USE,
          itemId = actual.first, itemName = actual.second, quantity = quantity)
      }
      GameIntent.TRANSFER_ITEM -> item?.let {
        val participants = transferParticipants(candidate.clause, target, context) ?: return null
        val actual = resolveInventoryItem(participants.first, it, context) ?: return null
        ItemCommand(commandId, turnId, participants.first, participants.second, source, ItemCommand.Operation.TRANSFER,
          actual.first, actual.second, quantity)
      }
      GameIntent.GIVE_AND_USE_ITEM -> item?.let {
        val recipient = target ?: return null
        val actual = resolveInventoryItem(KAI_ID, it, context) ?: return null
        GiveAndUseItemCommand(commandId, turnId, KAI_ID, recipient, source, actual.first, actual.second, quantity)
      }
      GameIntent.REQUEST_ITEM -> item?.let {
        val owner = target ?: context.state.party.memberIds.firstOrNull { it != KAI_ID && ownsDefinition(context.state, it, it = it, resolved = item) }
          ?: return null
        val actual = resolveInventoryItem(owner, it, context) ?: return null
        ItemCommand(commandId, turnId, owner, KAI_ID, source, ItemCommand.Operation.TRANSFER,
          actual.first, actual.second, quantity)
      }
      GameIntent.EQUIP_ITEM -> item?.let {
        val actual = resolveInventoryItem(defaultActor, it, context) ?: return null
        ItemCommand(commandId, turnId, defaultActor, source = source, operation = ItemCommand.Operation.EQUIP,
          itemId = actual.first, itemName = actual.second, quantity = 1)
      }
      GameIntent.UNEQUIP_ITEM -> item?.let {
        ItemCommand(commandId, turnId, defaultActor, source = source, operation = ItemCommand.Operation.UNEQUIP,
          itemId = it.first, itemName = it.second, quantity = 1)
      }
      GameIntent.OMNIVAULT_STORE -> item?.let {
        val actual = resolveInventoryItem(KAI_ID, it, context) ?: return null
        vaultCommand(commandId, turnId, source, OmnivaultCommand.Operation.STORE, actual, quantity)
      }
      GameIntent.OMNIVAULT_WITHDRAW -> item?.let {
        val actual = resolveStoredItem(it, context) ?: return null
        vaultCommand(commandId, turnId, source, OmnivaultCommand.Operation.WITHDRAW, actual, quantity)
      }
      GameIntent.OMNIVAULT_RESTORE -> item?.let {
        val equipmentItem = resolveEquippedItem(it, context) ?: return null
        vaultCommand(commandId, turnId, source, OmnivaultCommand.Operation.RESTORE, equipmentItem, 1)
      }
      GameIntent.INVENTORY_QUERY -> QueryCommand(commandId, turnId, defaultActor, target, source, QueryCommand.Type.INVENTORY)
      GameIntent.OMNIVAULT_QUERY -> QueryCommand(commandId, turnId, KAI_ID, source = source, type = QueryCommand.Type.OMNIVAULT)
      GameIntent.PARTY_JOIN_REQUEST -> target?.let { PartyCommand(commandId, turnId, KAI_ID, it, source, PartyCommand.Operation.ADD) }
      GameIntent.PARTY_REMOVE -> target?.let { PartyCommand(commandId, turnId, KAI_ID, it, source, PartyCommand.Operation.REMOVE) }
      GameIntent.PARTY_FOLLOW -> target?.let { PartyCommand(commandId, turnId, KAI_ID, it, source, PartyCommand.Operation.FOLLOW) }
      GameIntent.PARTY_SEPARATE -> target?.let { PartyCommand(commandId, turnId, KAI_ID, it, source, PartyCommand.Operation.SEPARATE) }
      GameIntent.PARTY_QUERY -> QueryCommand(commandId, turnId, KAI_ID, source = source, type = QueryCommand.Type.PARTY)
      GameIntent.CHARACTER_QUERY -> QueryCommand(commandId, turnId, KAI_ID, target, source, QueryCommand.Type.CHARACTER)
      GameIntent.STATUS_QUERY -> QueryCommand(commandId, turnId, KAI_ID, target, source, QueryCommand.Type.STATUS)
      GameIntent.NO_ACTION, GameIntent.UNKNOWN -> null
    }
  }

  private fun transferParticipants(clause: String, defaultTarget: String?, context: GameContext): Pair<String, String>? {
    val lower = clause.lowercase()
    val nonKai = defaultTarget
    if (nonKai != null && Regex("(?:cho|sang)\\s+kai\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
      return nonKai to KAI_ID
    }
    return KAI_ID to (nonKai ?: return null)
  }

  private fun resolveInventoryItem(ownerId: String, resolved: Pair<String, String>, context: GameContext): Pair<String, String>? {
    val inventory = context.state.inventories[ownerId]?.items?.values.orEmpty()
    val stack = inventory.firstOrNull {
      it.itemId == resolved.first || ItemDefinitionMetadata.definitionId(it) == resolved.first || it.name.equals(resolved.second, true)
    } ?: return null
    return stack.itemId to stack.name
  }

  private fun resolveStoredItem(resolved: Pair<String, String>, context: GameContext): Pair<String, String>? {
    val stack = context.state.omnivault.storedItems.values.firstOrNull {
      it.itemId == resolved.first || ItemDefinitionMetadata.definitionId(it) == resolved.first || it.name.equals(resolved.second, true)
    } ?: return null
    return stack.itemId to stack.name
  }

  private fun resolveEquippedItem(resolved: Pair<String, String>, context: GameContext): Pair<String, String>? {
    val equipment = context.state.equipment[KAI_ID]?.slots.orEmpty()
    val itemId = equipment.values.firstOrNull { id ->
      id == resolved.first || KaiStartingEquipment.displayName(id)?.equals(resolved.second, true) == true
    } ?: return null
    return itemId to (KaiStartingEquipment.displayName(itemId) ?: resolved.second)
  }

  private fun ownsDefinition(state: GameState, ownerId: String, it: String, resolved: Pair<String, String>?): Boolean {
    if (resolved == null) return false
    return state.inventories[ownerId]?.items?.values.orEmpty().any { stack ->
      stack.itemId == resolved.first || ItemDefinitionMetadata.definitionId(stack) == resolved.first || stack.name.equals(resolved.second, true)
    }
  }

  private fun vaultCommand(
    id: String,
    turn: String,
    source: CommandSource,
    operation: OmnivaultCommand.Operation,
    item: Pair<String, String>,
    quantity: Int
  ) = OmnivaultCommand(
    id, turn, KAI_ID, source = source, operation = operation,
    itemId = item.first, itemName = item.second, quantity = quantity,
    timestampEpochMs = System.currentTimeMillis()
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
