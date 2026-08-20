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
  val lastReferencedItemId: String? = null
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
  private val negative = Regex("(?:^|\\s)(?:không|đừng|chớ|chưa)\\s+(?:nhặt|lấy|bỏ|cất|đưa|dùng|trang bị|quét|scan|copy|sao chép|hoàn nguyên)", RegexOption.IGNORE_CASE)
  private val memory = Regex("(?:nhớ|hồi tưởng|lần trước|đã từng|giả sử|nếu như|ước gì|có lẽ)", RegexOption.IGNORE_CASE)
  private val observation = Regex("(?:nhìn|thấy|quan sát|nghe).{0,40}(?:nhặt|lấy|bỏ|cất|đưa|dùng)", RegexOption.IGNORE_CASE)
  private val quote = Regex("[\"“”][^\"“”]*(?:nhặt|lấy|bỏ|cất|đưa|dùng|quét|copy|hoàn nguyên)[^\"“”]*[\"“”]", RegexOption.IGNORE_CASE)
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
    Rule(GameIntent.OMNIVAULT_COPY, Regex("(?:sao chép|copy|nhân bản)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.OMNIVAULT_RESTORE, Regex("(?:hoàn nguyên|restore|khôi phục vật)(?:\\s|$)", RegexOption.IGNORE_CASE)),
    Rule(GameIntent.TRANSFER_ITEM, Regex("(?:đưa|trao|chuyển).*(?:cho|sang)\\s+\\p{L}+", setOf(RegexOption.IGNORE_CASE))),
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
  private val words = mapOf("một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "năm" to 5)
  override fun resolve(clause: String): Int {
    Regex("\\b(\\d+)\\b").find(clause)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
    return words.entries.firstOrNull { Regex("\\b${it.key}\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause) }?.value ?: 1
  }
}

class DefaultItemResolver : ItemResolver {
  private val noise = Regex("\\b(?:kai|iris|syvial|nhặt|lượm|cầm|lấy|rút|triệu hồi|bỏ|cất|lưu|đưa|trao|chuyển|cho|sang|dùng|sử dụng|uống|ăn|trang bị|đeo|mặc|tháo|cởi|quét|scan|copy|sao chép|nhân bản|hoàn nguyên|restore|khỏi|ra|từ|vào|trong|nhẫn|omnivault|kho|rồi|một|hai|ba|bốn|năm|\\d+)\\b", RegexOption.IGNORE_CASE)
  override fun resolve(clause: String, context: GameContext): Pair<String, String>? {
    context.itemAliases.entries.firstOrNull { clause.contains(it.key, true) }?.let { return it.value to it.key }
    if (Regex("\\b(?:nó|vật đó|cái đó)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) {
      context.lastReferencedItemId?.let { id ->
        val known = context.state.inventories.values.asSequence().mapNotNull { it.items[id] }.firstOrNull()
          ?: context.state.omnivault.storedItems[id]
        return id to (known?.name ?: id)
      }
    }
    val name = clause.replace(noise, " ").replace(Regex("[^\\p{L}\\p{N}_ -]+"), " ").replace(Regex("\\s+"), " ").trim()
    if (name.isBlank()) return null
    val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }
    return id to name
  }
}

class DefaultContainerResolver : ContainerResolver {
  override fun resolve(clause: String): String? = when {
    Regex("(?:nhẫn|omnivault)", RegexOption.IGNORE_CASE).containsMatchIn(clause) -> "omnivault"
    Regex("(?:inventory|túi đồ|kho đồ)", RegexOption.IGNORE_CASE).containsMatchIn(clause) -> "inventory"
    else -> null
  }
}
