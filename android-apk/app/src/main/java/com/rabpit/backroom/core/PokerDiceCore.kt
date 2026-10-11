package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Backported 1.1.99 Poker Dice + Core progression adapted to the current Kotlin GameState.
 *
 * Core progression is save-persistent metadata, independent from canon. Equipment remains the
 * current version's authority. Core stats multiply the already-normalized gameplay projection.
 * A Poker Dice session is bound to one encounter + one direct combat command so reload cannot
 * create free rerolls or bypass the active combat round.
 */
object PokerDiceCore {
  const val DICE_COUNT = 5
  const val MAX_REROLLS = 3
  const val BASE_STAT = 5
  const val MAX_STAT = 999
  const val ENTITY_VICTORY_CORE = 2
  const val DIRECT_COMBAT_ACTION = "Cả Party cùng tấn công"

  private const val PREFIX = "poker."
  private const val DICE = PREFIX + "dice."
  private const val REROLL_BASE_WEIGHT = 12
  private const val HELD_FACE_WEIGHT_BONUS = 6
  private const val HELD_SEQUENCE_WEIGHT_BONUS = 18
  private val STAT_KEYS = listOf("STR", "DEF", "SKL", "VIT")

  data class UpgradeResult(
    val state: GameState,
    val characterId: String,
    val stat: String,
    val value: Int,
    val cost: Int,
    val coreRemaining: Int
  )

  data class DiceSession(
    val encounterId: String,
    val action: String,
    val values: List<Int>,
    val held: List<Boolean>,
    val rerollsUsed: Int,
    val finalized: Boolean,
    val hand: String,
    val seed: Long,
    val sequence: Int
  )

  fun coreCount(state: GameState): Int = CharacterProgressionCore.coreCount(state)

  fun coreStat(state: GameState, characterId: String, stat: String): Int =
    CharacterProgressionCore.baseStat(state, characterId, stat)

  fun statPercent(state: GameState, characterId: String, stat: String): Int =
    CharacterProgressionCore.statPercent(state, characterId, stat)

  fun scaleByPercent(value: Int, percent: Int): Int = CharacterStatCore.scaleByPercent(value, percent)

  fun upgradeCost(currentStat: Int): Int = CharacterProgressionCore.upgradeCost(currentStat)

  fun grantCore(state: GameState, amount: Int): GameState = CharacterProgressionCore.grantCore(state, amount)

  fun upgrade(state: GameState, rawCharacterId: String, rawStat: String): UpgradeResult {
    val result = CharacterProgressionCore.upgrade(state, rawCharacterId, rawStat)
    return UpgradeResult(result.state, result.characterId, result.stat, result.value, result.cost, result.coreRemaining)
  }

  fun statsJson(state: GameState, characterId: String): JSONObject {
    val id = characterId.trim().lowercase(Locale.ROOT)
    val effective = CharacterStatCore.effective(state, id)
    val stats = JSONObject()
    STAT_KEYS.forEach { stat ->
      val base = coreStat(state, id, stat)
      val eff = when (stat) { "STR" -> effective.str; "DEF" -> effective.def; "SKL" -> effective.skl; else -> effective.vit }
      stats.put(stat, JSONObject()
        .put("base", base)
        .put("effective", eff)
        .put("multiplierPercent", statPercent(state, id, stat))
        .put("nextCoreCost", if (base >= MAX_STAT) JSONObject.NULL else upgradeCost(base)))
    }
    return JSONObject()
      .put("schema", CharacterProgressionCore.SCHEMA)
      .put("sharedCore", coreCount(state))
      .put("characterId", id)
      .put("stats", stats)
  }

  fun prepare(state: GameState, rawAction: String, encounterId: String): GameState {
    val action = normalizeAction(rawAction)
    require(action.isNotEmpty()) { "Combat action trống." }
    require(encounterId.isNotBlank()) { "Combat encounter chưa sẵn sàng." }
    val existing = session(state)
    if (existing != null && existing.encounterId == encounterId) {
      if (normalizeAction(existing.action) != action) {
        throw IllegalStateException(
          "Poker Dice đang khóa hành động " + existing.action + ". Hãy hoàn tất hand hiện tại."
        )
      }
      return state
    }

    var next = clearDice(state)
    val seed = stableSeed(encounterId, action, state.turn.currentTurnId)
    val values = MutableList(DICE_COUNT) { 0 }
    var sequence = 0
    for (i in 0 until DICE_COUNT) {
      values[i] = 1 + deterministicRoll(seed, sequence++, i, 6)
    }
    val held = autoHold(values, List(DICE_COUNT) { false })
    return writeSession(next, DiceSession(
      encounterId, rawAction.trim(), values, held, 0, false, classify(values), seed, sequence
    ))
  }

  fun setHold(state: GameState, index: Int, held: Boolean): GameState {
    val s = requireSession(state)
    check(!s.finalized) { "Poker Dice hand đã được chốt." }
    require(index in 0 until DICE_COUNT) { "Die index không hợp lệ." }
    val flags = s.held.toMutableList()
    flags[index] = held
    return writeSession(state, s.copy(held = flags))
  }

  fun reroll(state: GameState): GameState {
    val s = requireSession(state)
    check(!s.finalized) { "Poker Dice hand đã được chốt." }
    check(s.rerollsUsed < MAX_REROLLS) { "Đã dùng hết 3 lần ROLL." }
    check(s.held.any { !it }) { "Cả 5 viên đã HOLD." }

    val values = s.values.toMutableList()
    var sequence = s.sequence
    val weighted = s.held.any { it }
    for (slot in 0 until DICE_COUNT) {
      if (s.held[slot]) continue
      values[slot] = if (weighted) {
        val weights = rerollWeights(values, s.held, slot)
        weightedFace(deterministicRoll(s.seed, sequence++, slot, weights.sum()), weights)
      } else {
        1 + deterministicRoll(s.seed, sequence++, slot, 6)
      }
    }
    val held = autoHold(values, s.held)
    return writeSession(state, s.copy(
      values = values,
      held = held,
      rerollsUsed = s.rerollsUsed + 1,
      hand = classify(values),
      sequence = sequence
    ))
  }

  fun finish(state: GameState): GameState {
    val s = requireSession(state)
    if (s.finalized) return state
    return writeSession(state, s.copy(finalized = true, hand = classify(s.values)))
  }

  fun clearDice(state: GameState): GameState =
    state.copy(metadata = state.metadata.filterKeys { !it.startsWith(DICE) })

  fun isFinalizedFor(state: GameState, rawAction: String, encounterId: String): Boolean {
    val s = session(state) ?: return false
    return s.finalized && s.encounterId == encounterId
      && normalizeAction(s.action) == normalizeAction(rawAction)
  }

  fun diceJson(state: GameState): JSONObject? = session(state)?.let { s ->
    JSONObject()
      .put("action", s.action)
      .put("values", JSONArray(s.values))
      .put("held", JSONArray(s.held))
      .put("rerollsUsed", s.rerollsUsed)
      .put("maxRerolls", MAX_REROLLS)
      .put("finalized", s.finalized)
      .put("hand", s.hand)
  }

  fun attackPercent(state: GameState): Int = when (session(state)?.takeIf { it.finalized }?.hand) {
    "ONE PAIR" -> 125
    "TWO PAIR" -> 100
    "THREE OF A KIND" -> 125
    "STRAIGHT" -> 150
    "FULL HOUSE" -> 175
    "FOUR OF A KIND" -> 200
    "SSF" -> 225
    "FSF" -> 250
    else -> 100
  }

  fun accuracyBonus(state: GameState): Int = when (session(state)?.takeIf { it.finalized }?.hand) {
    "TWO PAIR" -> 2
    "THREE OF A KIND" -> 4
    "STRAIGHT" -> 7
    "FULL HOUSE" -> 10
    "FOUR OF A KIND" -> 13
    "SSF" -> 15
    "FSF" -> 18
    else -> 0
  }

  fun defenseBonus(state: GameState): Int = when (session(state)?.takeIf { it.finalized }?.hand) {
    "ONE PAIR" -> 2
    "TWO PAIR" -> 8
    "THREE OF A KIND" -> 5
    "STRAIGHT" -> 8
    "FULL HOUSE" -> 10
    "FOUR OF A KIND" -> 12
    "SSF" -> 15
    "FSF" -> 18
    else -> 0
  }

  fun escapeBonus(state: GameState): Int = when (session(state)?.takeIf { it.finalized }?.hand) {
    "ONE PAIR" -> 2
    "TWO PAIR" -> 8
    "THREE OF A KIND" -> 4
    "STRAIGHT" -> 10
    "FULL HOUSE" -> 12
    "FOUR OF A KIND" -> 15
    "SSF" -> 18
    "FSF" -> 20
    else -> 0
  }

  fun classify(values: List<Int>): String {
    if (values.size != DICE_COUNT || values.any { it !in 1..6 }) return "NO HAND"
    if (values.distinct().size == 1) return "FSF"
    if (values == listOf(1, 2, 3, 4, 5) || values == listOf(5, 4, 3, 2, 1)) return "SSF"
    if (values == listOf(2, 3, 4, 5, 6) || values == listOf(6, 5, 4, 3, 2)) return "STRAIGHT"

    val counts = IntArray(7)
    values.forEach { counts[it]++ }
    val four = counts.any { it == 4 }
    val triple = counts.any { it == 3 }
    val pairs = counts.count { it == 2 }
    return when {
      four -> "FOUR OF A KIND"
      triple && pairs == 1 -> "FULL HOUSE"
      triple -> "THREE OF A KIND"
      pairs >= 2 -> "TWO PAIR"
      pairs == 1 -> "ONE PAIR"
      else -> "NO HAND"
    }
  }

  private fun session(state: GameState): DiceSession? {
    val encounter = state.metadata[DICE + "encounter"]?.trim().orEmpty()
    val action = state.metadata[DICE + "action"]?.trim().orEmpty()
    if (encounter.isEmpty() || action.isEmpty()) return null
    val values = parseInts(state.metadata[DICE + "values"], DICE_COUNT)
    val held = parseHeld(state.metadata[DICE + "held"], DICE_COUNT)
    if (values.size != DICE_COUNT || held.size != DICE_COUNT) return null
    return DiceSession(
      encounter,
      action,
      values,
      held,
      state.metadata[DICE + "rerolls"]?.toIntOrNull()?.coerceIn(0, MAX_REROLLS) ?: 0,
      state.metadata[DICE + "finalized"] == "true",
      state.metadata[DICE + "hand"] ?: classify(values),
      state.metadata[DICE + "seed"]?.toLongOrNull() ?: stableSeed(encounter, action, state.turn.currentTurnId),
      state.metadata[DICE + "sequence"]?.toIntOrNull()?.coerceAtLeast(0) ?: DICE_COUNT
    )
  }

  private fun requireSession(state: GameState): DiceSession =
    session(state) ?: throw IllegalStateException("Poker Dice chưa được chuẩn bị.")

  private fun writeSession(state: GameState, s: DiceSession): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[DICE + "encounter"] = s.encounterId
    metadata[DICE + "action"] = s.action
    metadata[DICE + "values"] = s.values.joinToString(",")
    metadata[DICE + "held"] = s.held.joinToString(",") { if (it) "1" else "0" }
    metadata[DICE + "rerolls"] = s.rerollsUsed.toString()
    metadata[DICE + "finalized"] = s.finalized.toString()
    metadata[DICE + "hand"] = s.hand
    metadata[DICE + "seed"] = s.seed.toString()
    metadata[DICE + "sequence"] = s.sequence.toString()
    return state.copy(metadata = metadata)
  }

  private fun rerollWeights(values: List<Int>, held: List<Boolean>, slot: Int): IntArray {
    val weights = IntArray(6) { REROLL_BASE_WEIGHT }
    for (i in 0 until DICE_COUNT) {
      if (!held[i]) continue
      val face = values[i]
      if (face in 1..6) weights[face - 1] += HELD_FACE_WEIGHT_BONUS
    }
    if (held.count { it } < 2 || held[slot]) return weights

    val orderedStraights = listOf(
      listOf(1, 2, 3, 4, 5),
      listOf(5, 4, 3, 2, 1),
      listOf(2, 3, 4, 5, 6),
      listOf(6, 5, 4, 3, 2)
    )
    orderedStraights.forEach { target ->
      val compatible = (0 until DICE_COUNT).all { i -> !held[i] || values[i] == target[i] }
      if (compatible) weights[target[slot] - 1] += HELD_SEQUENCE_WEIGHT_BONUS
    }
    return weights
  }

  private fun weightedFace(roll: Int, weights: IntArray): Int {
    var cursor = max(0, roll)
    for (face in weights.indices) {
      val weight = max(0, weights[face])
      if (cursor < weight) return face + 1
      cursor -= weight
    }
    return 6
  }

  private fun autoHold(values: List<Int>, existing: List<Boolean>): List<Boolean> {
    val counts = IntArray(7)
    values.forEach { if (it in 1..6) counts[it]++ }
    return List(DICE_COUNT) { i -> existing.getOrElse(i) { false } || counts[values[i]] >= 2 }
  }

  private fun deterministicRoll(seed: Long, sequence: Int, slot: Int, bound: Int): Int {
    val mixed = seed * 1_103_515_245L + (sequence + 1L) * 12_345L + (slot + 1L) * 2_654_435_761L
    return Math.floorMod(mixed xor (mixed ushr 17), max(1, bound).toLong()).toInt()
  }

  private fun stableSeed(encounterId: String, action: String, turnId: String): Long =
    encounterId.hashCode().toLong() * 31L + action.hashCode().toLong() * 17L + turnId.hashCode().toLong()

  private fun parseInts(raw: String?, expected: Int): List<Int> {
    val values = raw?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
    return if (values.size == expected) values else emptyList()
  }

  private fun parseHeld(raw: String?, expected: Int): List<Boolean> {
    val values = raw?.split(",")?.map { it == "1" || it.equals("true", true) }.orEmpty()
    return if (values.size == expected) values else emptyList()
  }


  private fun normalizeStat(raw: String): String {
    val key = raw.trim().uppercase(Locale.ROOT)
    return if (key in STAT_KEYS) key else ""
  }

  private fun normalizeAction(raw: String): String =
    raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

  private fun withMetadata(state: GameState, key: String, value: String): GameState =
    state.copy(metadata = state.metadata + (key to value))
}
