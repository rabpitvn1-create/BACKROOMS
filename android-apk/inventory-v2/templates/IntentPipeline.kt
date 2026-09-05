package com.rabpit.backroom.core

enum class IntentConfidence { HIGH, MEDIUM, LOW }

enum class GameIntent {
  DISCARD_ITEM, USE_ITEM, TRANSFER_ITEM, GIVE_AND_USE_ITEM, REQUEST_ITEM,
  EQUIP_ITEM, UNEQUIP_ITEM, INVENTORY_QUERY,
  OMNIVAULT_STORE, OMNIVAULT_WITHDRAW, OMNIVAULT_RESTORE, OMNIVAULT_QUERY,
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

data class IntentResult(val candidates: List<IntentCandidate>, val requiresFallback: Boolean = false)

interface IntentInterpreter {
  suspend fun interpret(input: String, context: GameContext): IntentResult
}

object ClauseSplitter {
  private val connectors = Regex("\\s+(?:rồi|sau đó|rồi sau đó|và rồi|tiếp theo)\\s+", RegexOption.IGNORE_CASE)
  fun split(input: String): List<String> = input.split(connectors).map(String::trim).filter(String::isNotEmpty)
}

object CommandSafety {
  private val negative = Regex("(?:^|\\s)(?:không|đừng|chớ|chưa)\\s+(?:vứt|bỏ|đưa|trao|chuyển|xin|dùng|sử dụng|uống|ăn|trang bị|tháo|cất|rút|hoàn nguyên)", RegexOption.IGNORE_CASE)
  private val memory = Regex("(?:nhớ|hồi tưởng|lần trước|đã từng|giả sử|nếu như|ước gì|có lẽ)", RegexOption.IGNORE_CASE)
  private val quote = Regex("[\"“”][^\"“”]*(?:vứt|đưa|xin|dùng|cất|rút|hoàn nguyên)[^\"“”]*[\"“”]", RegexOption.IGNORE_CASE)
  private val hypotheticalQuestion = Regex("^(?:nếu|liệu|có nên|có thể).*[?？]?$", RegexOption.IGNORE_CASE)
  private val worldPickup = Regex("(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy\\s+lên|pick\\s+up)(?:\\s|$)", RegexOption.IGNORE_CASE)
  private val retiredCreation = Regex("(?:quét|scan|sao chép|copy|nhân bản|tạo thêm|tạo ra thêm|nhân thêm).*(?:omnivault|nhẫn|vật|đồ|item)|(?:omnivault|nhẫn).*(?:quét|scan|sao chép|copy|nhân bản|tạo thêm|tạo ra)", RegexOption.IGNORE_CASE)

  fun rejectionReason(text: String): String? = when {
    worldPickup.containsMatchIn(text) -> "world_item_unavailable"
    retiredCreation.containsMatchIn(text) -> "omnivault_creation_removed"
    negative.containsMatchIn(text) -> "negated_action"
    memory.containsMatchIn(text) -> "memory_or_hypothetical"
    quote.containsMatchIn(text) -> "quoted_action"
    hypotheticalQuestion.matches(text.trim()) -> "question_or_hypothetical"
    else -> null
  }
}

class RuleIntentInterpreter : IntentInterpreter {
  private data class Rule(val intent: GameIntent, val regex: Regex)

  private val rules = listOf(
    Rule(GameIntent.GIVE_AND_USE_ITEM, Regex("(?:đưa|trao|chuyển|giao).*(?:cho|sang).*(?:dùng|sử dụng|uống|ăn)|(?:đưa|trao).*(?:dùng|uống|ăn)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.REQUEST_ITEM, Regex("(?:xin|yêu cầu).*(?:đưa|trao|chuyển|cho)|(?:xin|lấy).*(?:từ|của)\\s+\\p{L}+", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_WITHDRAW, Regex("(?:lấy|rút).*(?:ra khỏi|khỏi|từ)\\s+(?:nhẫn|omnivault|kho)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_STORE, Regex("(?:cất|lưu|đưa).*(?:vào|trong)\\s+(?:nhẫn|omnivault|kho)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_RESTORE, Regex("(?:hoàn nguyên|restore|khôi phục).*(?:trang bị|vũ khí|giáp|đồ|vật)?", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.TRANSFER_ITEM, Regex("(?:đưa|trao|chuyển|giao).*(?:cho|sang)\\s+\\p{L}+", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.DISCARD_ITEM, Regex("(?:^|\\s)(?:vứt|quăng|ném|loại bỏ|bỏ đi)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.USE_ITEM, Regex("(?:^|\\s)(?:dùng|sử dụng|uống|ăn|kích hoạt)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.EQUIP_ITEM, Regex("(?:trang bị|đeo|mặc|cầm làm vũ khí|gắn vào ô trang bị)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.UNEQUIP_ITEM, Regex("(?:tháo|cởi|bỏ trang bị|gỡ khỏi ô trang bị)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.PARTY_JOIN_REQUEST, Regex("(?:vào|gia nhập|tham gia)\\s+(?:party|đội|nhóm)|(?:mời|cho).*(?:gia nhập|tham gia)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.PARTY_REMOVE, Regex("(?:rời|đuổi khỏi|loại khỏi)\\s+(?:party|đội|nhóm)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.INVENTORY_QUERY, Regex("(?:inventory|kho đồ|túi đồ|hành trang).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra|mở).*(?:inventory|kho đồ|túi đồ|hành trang)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_QUERY, Regex("(?:xem|kiểm tra|mở).*(?:omnivault|nhẫn vạn tàng)|(?:omnivault|nhẫn vạn tàng).*(?:gì|xem|kiểm tra)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.PARTY_QUERY, Regex("(?:party|đội hình|nhóm).*(?:ai|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:party|đội hình)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.STATUS_QUERY, Regex("(?:status|trạng thái|tình trạng).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:status|trạng thái)", RegexOption.IGNORE_CASE))
  )

  fun interpretSync(input: String, context: GameContext): IntentResult {
    val candidates = ClauseSplitter.split(input).map { clause ->
      val rejection = CommandSafety.rejectionReason(clause)
      if (rejection != null) {
        IntentCandidate(clause, GameIntent.NO_ACTION, IntentConfidence.HIGH, 1f, CommandSource.RULE, rejection)
      } else {
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

class DefaultActorResolver : ActorResolver {
  override fun resolve(clause: String, context: GameContext): String? {
    val found = context.actorAliases.entries.mapNotNull { entry ->
      val match = Regex("\\b${Regex.escape(entry.key)}\\b", RegexOption.IGNORE_CASE).find(clause) ?: return@mapNotNull null
      Triple(match.range.first, entry.key.length, entry.value)
    }.minWithOrNull(compareBy<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })
    return found?.third ?: KAI_ID
  }
}

class DefaultTargetResolver : TargetResolver {
  override fun resolve(clause: String, context: GameContext): String? = context.actorAliases.entries
    .filter { it.value != KAI_ID }
    .mapNotNull { entry ->
      val match = Regex("\\b${Regex.escape(entry.key)}\\b", RegexOption.IGNORE_CASE).find(clause) ?: return@mapNotNull null
      Triple(match.range.first, entry.key.length, entry.value)
    }
    .minWithOrNull(compareBy<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })?.third
}

class DefaultQuantityResolver : QuantityResolver {
  private val words = mapOf("một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "năm" to 5, "sáu" to 6, "bảy" to 7, "tám" to 8, "chín" to 9, "mười" to 10)
  override fun resolve(clause: String): Int {
    Regex("\\b(\\d+)\\b").find(clause)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
    if (Regex("\\bmột\\s+trăm\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return 100
    return words.entries.firstOrNull { Regex("\\b${it.key}\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause) }?.value ?: 1
  }
}

class DefaultItemResolver : ItemResolver {
  private val pronoun = Regex("\\b(?:nó|vật đó|cái đó|món đó|thứ đó)\\b", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): Pair<String, String>? {
    if (pronoun.containsMatchIn(clause)) {
      context.lastReferencedItemId?.let { return knownPair(it, context) }
    }
    val byAlias = context.itemAliases.entries
      .filter { clause.contains(it.key, ignoreCase = true) }
      .maxByOrNull { it.key.length }
    if (byAlias != null) return knownPair(byAlias.value, context) ?: (byAlias.value to byAlias.key)

    val normalizedClause = normalize(clause)
    val known = knownItems(context)
    val clauseTokens = normalizedClause.split(' ').filter(String::isNotBlank)
    val fuzzy = known.mapNotNull { stack ->
      val normalizedName = normalize(stack.name)
      val tokens = normalizedName.split(' ').filter(String::isNotBlank)
      val overlap = tokens.count(clauseTokens::contains)
      val score = when {
        normalizedClause.contains(normalizedName) && normalizedName.length >= 4 -> 100 + normalizedName.length
        tokens.size >= 2 && overlap >= 2 -> overlap * 10 + tokens.size
        else -> 0
      }
      if (score > 0) Triple(score, stack.itemId, stack.name) else null
    }.maxByOrNull { it.first }
    return fuzzy?.let { it.second to it.third }
  }

  private fun knownItems(context: GameContext): List<ItemStack> =
    (context.state.inventories.values.flatMap { it.items.values } + context.state.omnivault.storedItems.values)
      .distinctBy { it.itemId }

  private fun knownPair(idOrDefinition: String, context: GameContext): Pair<String, String>? {
    val known = knownItems(context).firstOrNull { it.itemId == idOrDefinition || ItemDefinitionMetadata.definitionId(it) == idOrDefinition }
    return known?.let { it.itemId to it.name }
  }

  private fun normalize(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
}
