package com.rabpit.backroom.core

/** Deterministic text item actions for already recruited Nam/Lan; never creates ownership. */
object NamedFollowerItemActions {
  data class Result(
    val state: GameState,
    val handled: Boolean = true,
    val error: String? = null,
    val reply: String,
    val changedItems: Set<String> = emptySet(),
    val changedOwners: Set<String> = emptySet()
  )

  private val allowed = setOf(GameIntent.EQUIP_ITEM, GameIntent.UNEQUIP_ITEM,
    GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM, GameIntent.USE_ITEM)
  private val verb = Regex("(?:đưa|trao|chuyển|vứt|thả|bỏ xuống|sử dụng|dùng|uống|ăn|trang bị|đeo|mặc|cầm làm vũ khí|tháo|cởi|bỏ trang bị)", RegexOption.IGNORE_CASE)

  @JvmStatic fun tryExecute(state: GameState, action: String, turnId: String): Result? {
    val aliases = aliases(state)
    val involvesNamed = ClauseSplitter.split(action).any { clause ->
      val prefix = verb.find(clause)?.range?.first?.let { clause.substring(0, it) } ?: clause
      val marker = Regex("(?:^|\\s)(?:cho|sang|của)(?:\\s|$)", RegexOption.IGNORE_CASE).find(clause)
      val target = marker?.let { clause.substring(it.range.last + 1) }.orEmpty()
      (mentions(prefix, aliases) + mentions(target, aliases)).any { it.second in VietnameseFollowersCanon.ids }
    }
    if (!involvesNamed) return null
    val context = GameContext(state, aliases, itemAliases(state))
    val interpreted = RuleIntentInterpreter().interpretSync(action, context)
    if (interpreted.candidates.none { it.intent in allowed }) return null
    fun reject(reason: String) = Result(state, error = reason,
      reply = "Không thể thực hiện thao tác trang bị/vật phẩm này: $reason.")
    if (interpreted.candidates.any { it.confidence != IntentConfidence.HIGH || it.intent !in allowed }) {
      return reject("named_item_action_ambiguous")
    }
    if (CombatRuntime.active(state) != null) return reject("combat_active")
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return reject(pending.error)
    var staged = pending.state
    var lastActor = CAO_MINH_ID
    val commands = mutableListOf<GameCommand>()
    val owners = mutableSetOf<String>()
    val changed = mutableSetOf<String>()
    for ((index, candidate) in interpreted.candidates.withIndex()) {
      val registry = itemAliases(staged)
      val knownResolver = object : ItemResolver {
        override fun resolve(clause: String, context: GameContext): Pair<String, String>? {
          val known = registry.entries.sortedByDescending { it.key.length }
            .firstOrNull { containsTerm(clause, it.key) }
          if (known != null) return known.value to staged.inventories.values
            .firstNotNullOfOrNull { it.items[known.value]?.name }.orEmpty()
          if (Regex("(?:nó|vật đó|món đó|cái đó|thứ đó)", RegexOption.IGNORE_CASE).containsMatchIn(clause)) {
            val id = staged.metadata["lastReferencedItemId"] ?: return null
            val item = staged.inventories.values.firstNotNullOfOrNull { it.items[id] } ?: return null
            return id to item.name
          }
          return null
        }
      }
      val actorResolver = object : ActorResolver {
        override fun resolve(clause: String, context: GameContext): String {
          val prefix = verb.find(clause)?.range?.first?.let { clause.substring(0, it) } ?: clause
          return mentions(prefix, aliases).lastOrNull()?.second ?: lastActor
        }
      }
      val targetResolver = object : TargetResolver {
        override fun resolve(clause: String, context: GameContext): String? {
          val marker = Regex("(?:^|\\s)(?:cho|sang|của)(?:\\s|$)", RegexOption.IGNORE_CASE).find(clause)
            ?: return null
          return mentions(clause.substring(marker.range.last + 1), aliases).firstOrNull()?.second
        }
      }
      val quantityResolver = object : QuantityResolver {
        override fun resolve(clause: String): Int {
          var text = clause
          registry.keys.sortedByDescending(String::length).forEach { name ->
            text = text.replace(name, "", ignoreCase = true)
          }
          return DefaultQuantityResolver().resolve(text)
        }
      }
      val clauseContext = GameContext(staged, aliases, registry)
      val resolver = CommandResolver(actorResolver, targetResolver, knownResolver, quantityResolver)
      var command = resolver.resolve(candidate, index, turnId, clauseContext) as? ItemCommand
        ?: return reject("named_item_unknown")
      if (command.operation in setOf(ItemCommand.Operation.EQUIP, ItemCommand.Operation.UNEQUIP, ItemCommand.Operation.USE) &&
        command.targetId in VietnameseFollowersCanon.ids) command = command.copy(actorId = command.targetId!!, targetId = null)
      if (command.actorId !in VietnameseFollowersCanon.ids && command.targetId !in VietnameseFollowersCanon.ids) {
        return reject("named_item_actor_required")
      }
      val participants = setOfNotNull(command.actorId, command.targetId)
      for (id in participants) {
        val actor = staged.characters[id] ?: return reject("target_unknown")
        if ((id in VietnameseFollowersCanon.ids && actor.metadata["encountered"] != "true") || id !in staged.party.memberIds ||
          actor.presence != CharacterPresence.ACTIVE || actor.vitalState.currentHp <= 0 ||
          actor.vitalState.condition == CharacterCondition.DEAD) return reject("named_follower_unavailable")
      }
      var quantityText = candidate.clause
      registry.keys.sortedByDescending(String::length).forEach { quantityText = quantityText.replace(it, "", ignoreCase = true) }
      val explicitQuantity = Regex("(?<![\\p{L}\\p{N}])[-+]?\\d+(?![\\p{L}\\p{N}])").find(quantityText)?.value
      if (explicitQuantity != null && (explicitQuantity.toIntOrNull()?.let { it > 0 } != true)) {
        return reject("quantity_must_be_positive")
      }
      if (command.operation == ItemCommand.Operation.EQUIP) {
        val slot = EquipmentCatalog.definition(command.itemId)?.primarySlot?.key
        command = command.copy(slot = slot ?: command.slot)
      }
      val step = StateReducer.execute(staged, command)
      if (!step.applied) return reject(step.validation.reason ?: "named_item_unchanged")
      staged = step.state
      commands += command
      owners += participants
      changed += command.itemId
      lastActor = command.actorId
    }
    commands += TimeAdvanceCommand("$turnId:NAMED_ITEMS:TIME", turnId, CAO_MINH_ID,
      source = CommandSource.SYSTEM, minutes = TimeCostPolicy.estimateMinutes(action), reason = "named_follower_item_action")
    val committed = TurnCoordinator.commit(pending.state, commands)
    if (committed.error != null) return reject(committed.error)
    for (id in owners) {
      val before = state.inventories[id]?.items.orEmpty()
      val after = committed.state.inventories[id]?.items.orEmpty()
      changed += (before.keys + after.keys).filter { before[it] != after[it] }
    }
    return Result(committed.state, reply = "Trang bị và vật phẩm đã được Core cập nhật.",
      changedItems = changed, changedOwners = owners)
  }

  private fun itemAliases(state: GameState): Map<String, String> = linkedMapOf<String, String>().apply {
    state.inventories.values.forEach { inventory -> inventory.items.values.forEach { item ->
      put(item.name, item.itemId); put(item.itemId, item.itemId)
    } }
  }

  private fun aliases(state: GameState): Map<String, String> = linkedMapOf<String, String>().apply {
    state.characters.values.forEach { c -> put(c.name, c.id); put(c.id, c.id) }
    listOf("Nguyễn Quốc Nam", "Quốc Nam", "quoc nam", "nguyen quoc nam", "Nam", NGUYEN_QUOC_NAM_ID)
      .forEach { put(it, NGUYEN_QUOC_NAM_ID) }
    listOf("Hứa Tiểu Lan", "Tiểu Lan", "tieu lan", "hua tieu lan", HUA_TIEU_LAN_ID)
      .forEach { put(it, HUA_TIEU_LAN_ID) }
    put("cao minh", CAO_MINH_ID)
  }

  private fun mentions(text: String, aliases: Map<String, String>): List<Pair<Int, String>> = aliases.entries
    .flatMap { (name, id) -> if (name.isBlank()) emptyList() else
      Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        .findAll(text).map { it.range.first to id }.toList() }
    .distinct().sortedBy { it.first }

  private fun containsTerm(text: String, term: String): Boolean = term.isNotBlank() &&
    Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
}
