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
  // LITERT_DIALOGUE_GUARD_R01:
  // Questions about weapons/ammunition and requests that are still speech must never become
  // authoritative inventory mutations merely because they contain words such as "dùng" or "sử dụng".
  private val nonMutatingItemInquiry = Regex(
    """(?:^|\s)(?:hỏi|muốn\s+biết|xin\s+hỏi|thắc\s+mắc).{0,140}(?:súng|đạn|vũ\s+khí|trang\s+bị|dùng|sử\s+dụng|xin|cho)|""" +
      """(?:súng|đạn|vũ\s+khí|trang\s+bị).{0,100}(?:gì|nào|bao\s+nhiêu|có\s+giống|giống.*không|thế\s+nào)""",
    RegexOption.IGNORE_CASE
  )
  private val companionTacticalCommand = Regex(
    """(?:^|\s)(?:yêu\s+cầu|bảo|cho)?\s*(?:lucia|iris|syvial|an\s+nhiên).{0,60}""" +
      """(?:trinh\s+sát|do\s+thám|thăm\s+dò|quan\s+sát|canh|giữ\s+vị\s+trí|kiểm\s+tra|theo\s+dõi)""",
    RegexOption.IGNORE_CASE
  )

  fun rejectionReason(text: String): String? = when {
    nonMutatingItemInquiry.containsMatchIn(text) -> "non_mutating_item_inquiry"
    companionTacticalCommand.containsMatchIn(text) -> "companion_tactical_command"
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
    Rule(GameIntent.TRANSFER_ITEM, Regex("(?i:đưa|trao|chuyển)(?:.*(?i:cho|sang)\\s+\\p{L}+|\\s+\\p{Lu}\\p{L}+)" ) ),
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

class DefaultActorResolver : ActorResolver {
  override fun resolve(clause: String, context: GameContext): String? =
    context.actorAliases.entries.firstOrNull { clause.contains(it.key, true) }?.value ?: KAI_ID
}

class DefaultTargetResolver : TargetResolver {
  override fun resolve(clause: String, context: GameContext): String? = context.actorAliases.entries
    .firstOrNull { it.value != KAI_ID && Regex("\\b${Regex.escape(it.key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause) }?.value
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
  private val resultTail = Regex("\\s+(?:và\\s+)?(?:nhận được|biến thành|trở thành|thành)\\s+.+$", RegexOption.IGNORE_CASE)
  private val noise = Regex("\\b(?:kai|iris|syvial|nhặt|lượm|cầm|lấy|rút|triệu hồi|bỏ|cất|lưu|đưa|trao|chuyển|cho|sang|dùng|sử dụng|uống|ăn|trang bị|đeo|mặc|tháo|cởi|quét|scan|copy|sao chép|nhân bản|tạo thêm|tạo ra thêm|nhân thêm|hoàn nguyên|restore|khỏi|ra|từ|vào|trong|nhẫn|omnivault|kho|rồi|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|trăm|\\d+)\\b", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): Pair<String, String>? {
    if (pronoun.containsMatchIn(clause)) {
      context.lastReferencedItemId?.let { id -> return knownPair(id, context) }
    }

    val sourceClause = clause.replace(resultTail, " ")
    context.itemAliases.entries.firstOrNull { sourceClause.contains(it.key, true) }?.let { return it.value to it.key }

    val normalizedClause = normalize(sourceClause)
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

    val name = sourceClause.replace(noise, " ").replace(Regex("[^\\p{L}\\p{N}_ -]+"), " ").replace(Regex("\\s+"), " ").trim()
    if (name.isBlank()) return null
    val id = canonicalId(name)
    return id to name
  }

  private fun knownPair(id: String, context: GameContext): Pair<String, String> {
    val known = context.state.inventories.values.asSequence().mapNotNull { it.items[id] }.firstOrNull()
      ?: context.state.omnivault.storedItems[id]
      ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == id }?.templateItem
    return id to (known?.name ?: id)
  }

  private fun normalize(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()

  private fun canonicalId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }
}

class DefaultContainerResolver : ContainerResolver {
  override fun resolve(clause: String): String? = when {
    Regex("(?:nhẫn|omnivault)", RegexOption.IGNORE_CASE).containsMatchIn(clause) -> "omnivault"
    Regex("(?:inventory|túi đồ|kho đồ)", RegexOption.IGNORE_CASE).containsMatchIn(clause) -> "inventory"
    else -> null
  }
}
