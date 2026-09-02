package com.rabpit.backroom.core

enum class IntentConfidence { HIGH, MEDIUM, LOW }

enum class GameIntent {
  PICKUP_ITEM, DROP_ITEM, USE_ITEM, TRANSFER_ITEM, STORE_ITEM, WITHDRAW_ITEM,
  EQUIP_ITEM, UNEQUIP_ITEM, INVENTORY_QUERY,
  OMNIVAULT_STORE, OMNIVAULT_WITHDRAW, OMNIVAULT_SCAN, OMNIVAULT_COPY,
  OMNIVAULT_RESTORE, OMNIVAULT_QUERY,
  PARTY_JOIN_REQUEST, PARTY_REMOVE, PARTY_FOLLOW, PARTY_SEPARATE, PARTY_QUERY,
  CHARACTER_QUERY, STATUS_QUERY, UNKNOWN, NO_ACTION
}

data class GameContext(
  val state: GameState,
  val actorAliases: Map<String, String> = mapOf("kai" to KAI_ID),
  val itemAliases: Map<String, String> = emptyMap(),
  val lastReferencedItemId: String? = state.metadata["lastReferencedItemId"]
)

data class IntentCandidate(
  val clause: String,
  val intent: GameIntent,
  val confidence: IntentConfidence,
  val score: Float,
  val source: CommandSource,
  val reason: String? = null
)

data class IntentResult(
  val candidates: List<IntentCandidate>,
  val requiresFallback: Boolean = false
)

interface IntentInterpreter {
  suspend fun interpret(input: String, context: GameContext): IntentResult
}

object ClauseSplitter {
  private val connectors = Regex("\\s+(?:rồi|sau đó|rồi sau đó|và rồi|tiếp theo)\\s+", RegexOption.IGNORE_CASE)
  fun split(input: String): List<String> = input.split(connectors).map(String::trim).filter(String::isNotEmpty)
}

object CommandSafety {
  private val negative = Regex("(?:^|\\s)(?:không|đừng|chớ|chưa)\\s+(?:nhặt|lấy|bỏ|cất|đưa|dùng|trang bị|quét|scan|copy|sao chép|hoàn nguyên|tạo thêm|tạo ra thêm)", RegexOption.IGNORE_CASE)
  private val memory = Regex("(?:nhớ|hồi tưởng|lần trước|đã từng|giả sử|nếu như|ước gì|có lẽ)", RegexOption.IGNORE_CASE)
  private val observation = Regex("(?:nhìn|thấy|quan sát|nghe).{0,40}(?:nhặt|lấy|bỏ|cất|đưa|dùng)", RegexOption.IGNORE_CASE)
  private val quote = Regex("[\"“”][^\"“”]*(?:nhặt|lấy|bỏ|cất|đưa|dùng|quét|copy|hoàn nguyên|tạo thêm)[^\"“”]*[\"“”]", RegexOption.IGNORE_CASE)
  private val hypotheticalQuestion = Regex("^(?:nếu|liệu|có nên|có thể).*[?？]?$", RegexOption.IGNORE_CASE)

  fun rejectionReason(text: String): String? = when {
    negative.containsMatchIn(text) -> "negated_action"
    memory.containsMatchIn(text) -> "memory_or_hypothetical"
    observation.containsMatchIn(text) -> "observed_narrative"
    quote.containsMatchIn(text) -> "quoted_action"
    hypotheticalQuestion.matches(text.trim()) -> "question_or_hypothetical"
    else -> null
  }
}

class RuleIntentInterpreter : IntentInterpreter {
  private data class Rule(val intent: GameIntent, val regex: Regex)

  private val rules = listOf(
    Rule(GameIntent.OMNIVAULT_WITHDRAW, Regex("(?:lấy|rút|triệu hồi).*(?:khỏi|ra khỏi|từ)\\s+(?:nhẫn|omnivault|kho)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_STORE, Regex("(?:cất|bỏ|lưu).*(?:vào|trong)\\s+(?:nhẫn|omnivault|kho)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_SCAN, Regex("(?:quét|scan)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_COPY, Regex("(?:sao chép|copy|nhân bản|tạo thêm|tạo ra thêm|nhân thêm)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_RESTORE, Regex("(?:hoàn nguyên|restore|khôi phục vật)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.TRANSFER_ITEM, Regex("(?i:đưa|trao|chuyển)(?:.*(?i:cho|sang)\\s+\\p{L}+|\\s+\\p{Lu}\\p{L}+)") ),
    Rule(GameIntent.PICKUP_ITEM, Regex("(?:^|\\s)(?:nhặt|lượm|cầm lên|lấy lên)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.DROP_ITEM, Regex("(?:^|\\s)(?:vứt|thả|bỏ xuống)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.USE_ITEM, Regex("(?:^|\\s)(?:dùng|sử dụng|uống|ăn)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.EQUIP_ITEM, Regex("(?:trang bị|đeo|mặc|cầm làm vũ khí)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.UNEQUIP_ITEM, Regex("(?:tháo|cởi|bỏ trang bị)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.PARTY_JOIN_REQUEST, Regex("(?:vào|gia nhập|tham gia)\\s+(?:party|đội|nhóm)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.PARTY_REMOVE, Regex("(?:rời|đuổi khỏi|loại khỏi)\\s+(?:party|đội|nhóm)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.INVENTORY_QUERY, Regex("(?:inventory|kho đồ|túi đồ).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.PARTY_QUERY, Regex("(?:party|đội hình|nhóm).*(?:ai|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:party|đội hình)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.STATUS_QUERY, Regex("(?:status|trạng thái|tình trạng).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:status|trạng thái)", RegexOption.IGNORE_CASE))
  )

  fun interpretSync(input: String, context: GameContext): IntentResult {
    val candidates = ClauseSplitter.split(input).map { clause ->
      val rejection = CommandSafety.rejectionReason(clause)
      if (rejection != null) IntentCandidate(clause, GameIntent.NO_ACTION, IntentConfidence.HIGH, 1f, CommandSource.RULE, rejection)
      else {
        val matches = rules.filter { it.regex.containsMatchIn(clause) }
        when (matches.size) {
          0 -> IntentCandidate(clause, GameIntent.UNKNOWN, IntentConfidence.LOW, 0f, CommandSource.RULE, "no_rule")
          1 -> IntentCandidate(clause, matches.first().intent, IntentConfidence.HIGH, .99f, CommandSource.RULE)
          else -> IntentCandidate(clause, matches.first().intent, IntentConfidence.MEDIUM, .65f, CommandSource.RULE, "multiple_rules")
        }
      }
    }
    return IntentResult(candidates, candidates.any { it.confidence != IntentConfidence.HIGH && it.intent != GameIntent.NO_ACTION })
  }

  override suspend fun interpret(input: String, context: GameContext): IntentResult = interpretSync(input, context)
}

interface ActorResolver { fun resolve(clause: String, context: GameContext): String? }
interface TargetResolver { fun resolve(clause: String, context: GameContext): String? }
interface ItemResolver { fun resolve(clause: String, context: GameContext): Pair<String, String>? }
interface QuantityResolver { fun resolve(clause: String): Int }
interface ContainerResolver { fun resolve(clause: String): String? }
interface ReferenceResolver { fun resolve(clause: String, context: GameContext): String? }

private data class ResolverAlias(val text: String, val ids: Set<String>)

private fun resolverCharacterAliases(context: GameContext): List<ResolverAlias> {
  val candidates = linkedMapOf<String, MutableSet<String>>()

  fun register(raw: String, id: String) {
    if (id !in context.state.characters) return
    val alias = raw.trim().lowercase().replace(Regex("\\s+"), " ")
    if (alias.isBlank()) return
    candidates.getOrPut(alias) { linkedSetOf() }.add(id)
  }

  context.state.characters.forEach { (id, character) ->
    register(id, id)
    register(character.name, id)
    val firstName = character.name.trim().substringBefore(' ').lowercase()
    if (firstName.length >= 3) register(firstName, id)
    character.metadata["aliases"].orEmpty()
      .split(',', ';', '|')
      .map(String::trim)
      .filter(String::isNotBlank)
      .forEach { register(it, id) }
  }
  context.actorAliases.forEach { (alias, id) -> register(alias, id) }

  return candidates.entries
    .map { (alias, ids) -> ResolverAlias(alias, ids.toSet()) }
    .sortedByDescending { it.text.length }
}

private fun resolverAliasRegex(alias: String): Regex = Regex(
  "(?<![\\p{L}\\p{N}_])${Regex.escape(alias)}(?![\\p{L}\\p{N}_])",
  RegexOption.IGNORE_CASE
)

private data class ResolverMention(val start: Int, val end: Int, val ids: Set<String>)

private fun resolverMentions(clause: String, context: GameContext): List<ResolverMention> =
  resolverCharacterAliases(context).mapNotNull { alias ->
    resolverAliasRegex(alias.text).find(clause)?.let {
      ResolverMention(it.range.first, it.range.last, alias.ids)
    }
  }.sortedWith(compareBy<ResolverMention> { it.start }.thenByDescending { it.end - it.start })

class DefaultActorResolver : ActorResolver {
  private val actionVerb = Regex(
    "(?<![\\p{L}\\p{N}_])(?:nhặt|lượm|cầm\\s+lên|lấy\\s+lên|vứt|thả|bỏ\\s+xuống|đưa|trao|chuyển|dùng|sử\\s+dụng|uống|ăn|trang\\s+bị|đeo|mặc|tháo|cởi)(?![\\p{L}\\p{N}_])",
    RegexOption.IGNORE_CASE
  )
  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)
  private val useVerb = Regex("(?:dùng|sử\\s+dụng|uống|ăn)", RegexOption.IGNORE_CASE)
  private val recipientAfterVerb = Regex("(?:cho|lên)\\s+", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
    val mentions = resolverMentions(clause, context)
    val action = actionVerb.find(clause)
    if (action != null) {
      // "Kai cho Lucia ăn ...": the source is before "cho", not the name nearest "ăn".
      // Resolve both sides exactly so unknown/ambiguous recipients cannot turn into self-use.
      val giving = Regex("(?<![\\p{L}\\p{N}_])cho\\s+", RegexOption.IGNORE_CASE).find(clause)
      if (useVerb.matches(action.value) && giving != null && giving.range.last < action.range.first) {
        val aliases = resolverCharacterAliases(context)
        val recipientText = clause.substring(giving.range.last + 1, action.range.first)
          .trim().replace(Regex("\\s+"), " ")
        val recipientIds = aliases.filter { it.text.equals(recipientText, true) }.flatMap { it.ids }.toSet()
        if (recipientIds.size != 1) return null
        val giverText = clause.substring(0, giving.range.first).trim().replace(Regex("\\s+"), " ")
        if (giverText.isEmpty() || giverText.equals("tôi", true) || giverText.equals("bạn", true)) return KAI_ID
        return aliases.filter { it.text.equals(giverText, true) }.flatMap { it.ids }.toSet().singleOrNull()
      }
      val actorMention = mentions
        .filter { it.end < action.range.first }
        .maxWithOrNull(compareBy<ResolverMention> { it.end }.thenBy { it.start })
      if (actorMention != null) return actorMention.ids.singleOrNull()

      // First-person transfer/use commands without an explicit source belong to Kai.
      if (transferVerb.containsMatchIn(action.value)) return KAI_ID
      if (useVerb.containsMatchIn(action.value) && recipientAfterVerb.find(clause, action.range.last + 1) != null) return KAI_ID
    }

    val firstMention = mentions.firstOrNull() ?: return KAI_ID
    return firstMention.ids.singleOrNull()
  }
}

class DefaultTargetResolver : TargetResolver {
  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
    val aliases = resolverCharacterAliases(context)

    // Explicit "cho/sang <character>" recipient syntax has highest authority.
    // If that alias belongs to more than one character, resolution fails closed.
    val recipient = aliases.mapNotNull { alias ->
      val regex = Regex(
        "(?:cho|sang)\\s+${Regex.escape(alias.text)}(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
      )
      regex.find(clause)?.let { Triple(it.range.first, alias.text.length, alias) }
    }.minWithOrNull(
      compareBy<Triple<Int, Int, ResolverAlias>> { it.first }.thenByDescending { it.second }
    )
    if (recipient != null) return recipient.third.ids.singleOrNull()

    val transfer = transferVerb.find(clause)
    if (transfer != null) {
      // Also support "Kai đưa Iris hai chai nước" without a "cho" connector.
      return resolverMentions(clause, context)
        .filter { it.start > transfer.range.last }
        .minWithOrNull(compareBy<ResolverMention> { it.start }.thenByDescending { it.end - it.start })
        ?.ids
        ?.singleOrNull()
    }

    return resolverMentions(clause, context)
      .firstOrNull { mention -> mention.ids.singleOrNull()?.let { it != KAI_ID } == true }
      ?.ids
      ?.singleOrNull()
  }
}


class DefaultQuantityResolver : QuantityResolver {
  private val words = mapOf("mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4, "nam" to 5, "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9, "muoi" to 10)
  override fun resolve(clause: String): Int {
    val quantityClause = ItemCatalog.withoutOfficialMentions(clause)
    Regex("\\b(\\d+)\\b").find(quantityClause)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
    if (Regex("\\bmot\\s+tram\\b", RegexOption.IGNORE_CASE).containsMatchIn(quantityClause)) return 100
    return words.entries.firstOrNull { Regex("\\b${it.key}\\b", RegexOption.IGNORE_CASE).containsMatchIn(quantityClause) }?.value ?: 1
  }
}

class DefaultItemResolver : ItemResolver {
  private fun withoutCharacterAliases(clause: String, context: GameContext): String {
    var result = clause
    resolverCharacterAliases(context).forEach { alias -> result = resolverAliasRegex(alias.text).replace(result, " ") }
    return result.replace(Regex("\\s+"), " ").trim()
  }

  private val pronoun = Regex("\\b(?:nó|vật đó|cái đó|món đó|thứ đó)\\b", RegexOption.IGNORE_CASE)
  private val resultTail = Regex("\\s+(?:và\\s+)?(?:nhận được|biến thành|trở thành|thành)\\s+.+$", RegexOption.IGNORE_CASE)
  private val noise = Regex("\\b(?:kai|iris|syvial|nhặt|được|lượm|cầm|lấy|rút|triệu hồi|bỏ|cất|lưu|đưa|trao|chuyển|cho|sang|dùng|sử dụng|uống|ăn|trang bị|đeo|mặc|tháo|cởi|quét|scan|copy|sao chép|nhân bản|tạo thêm|tạo ra thêm|nhân thêm|hoàn nguyên|restore|khỏi|ra|từ|vào|trong|nhẫn|omnivault|kho|rồi|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|trăm|\\d+)\\b", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): Pair<String, String>? {
    if (pronoun.containsMatchIn(clause)) {
      context.lastReferencedItemId?.let { id -> return knownPair(id, context) }
    }

    val sourceClause = clause.replace(resultTail, " ")
    val itemClause = withoutCharacterAliases(sourceClause, context)
    ItemCatalog.officialMention(itemClause)?.let { item -> return item.id to item.name }
    context.itemAliases.entries.firstOrNull { itemClause.contains(it.key, true) }?.let {
      val official = ItemCatalog.resolveOfficial(it.value, it.key)
      return (official?.id ?: ItemCatalog.identityId(it.value, it.key)) to (official?.name ?: it.key)
    }

    val normalizedClause = normalize(itemClause)
    val knownItems = (
      context.state.inventories.values.flatMap { it.items.values } +
      context.state.omnivault.storedItems.values +
      context.state.omnivault.scanSlots.map { it.templateItem }
    ).distinctBy { it.itemId }
    val clauseTokens = normalizedClause.split(' ').filter(String::isNotBlank)
    val fuzzy = knownItems.mapNotNull { stack ->
      val normalizedName = normalize(stack.name)
      val tokens = normalizedName.split(' ').filter(String::isNotBlank)
      val overlap = tokens.count { token -> clauseTokens.contains(token) }
      val strongPrefix = normalizedName.startsWith(normalizedClause) || normalizedClause.contains(normalizedName)
      val score = when {
        strongPrefix && normalizedClause.length >= 4 -> 100 + normalizedName.length
        tokens.size >= 2 && overlap >= 2 -> overlap * 10 + tokens.size
        else -> 0
      }
      if (score > 0) Triple(score, stack.itemId, stack.name) else null
    }.maxByOrNull { it.first }
    if (fuzzy != null) return fuzzy.second to fuzzy.third

    val name = itemClause.replace(noise, " ").replace(Regex("[^\\p{L}\\p{N}_ -]+"), " ").replace(Regex("\\s+"), " ").trim()
    // ITEM_REFERENCE_FALLBACK_FINAL_R02
    if (name.isBlank()) {
      val rawRemembered = context.lastReferencedItemId ?: return null
      val rememberedId = ItemCatalog.identityId(rawRemembered, rawRemembered)
      val known = context.state.inventories.values.asSequence().mapNotNull { it.items[rememberedId] }.firstOrNull()
        ?: context.state.omnivault.storedItems[rememberedId]
        ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == rememberedId }?.templateItem
        ?: return null
      return rememberedId to known.name
    }
    if (name.isBlank()) return context.lastReferencedItemId?.let { knownPair(it, context) }
    val official = ItemCatalog.resolveOfficial(null, name)
    return (official?.id ?: ItemCatalog.identityId(name = name)) to (official?.name ?: name)
  }

  private fun knownPair(id: String, context: GameContext): Pair<String, String> {
    val canonicalId = ItemCatalog.identityId(id, id)
    val known = context.state.inventories.values.asSequence().mapNotNull { it.items[canonicalId] }.firstOrNull()
      ?: context.state.omnivault.storedItems[canonicalId]
      ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == canonicalId }?.templateItem
    return canonicalId to (known?.name ?: canonicalId)
  }

  private fun normalize(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()

}

class DefaultContainerResolver : ContainerResolver {
  override fun resolve(clause: String): String? = when {
    Regex("(?:nhẫn|omnivault)", RegexOption.IGNORE_CASE).containsMatchIn(clause) -> "omnivault"
    Regex("(?:inventory|túi đồ|kho đồ)", RegexOption.IGNORE_CASE).containsMatchIn(clause) -> "inventory"
    else -> null
  }
}
