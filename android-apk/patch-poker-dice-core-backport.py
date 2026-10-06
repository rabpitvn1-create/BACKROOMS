from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = CORE / "GameCoreFacade.kt"
COMBAT = CORE / "CombatRuntime.kt"
EQUIPMENT = CORE / "CharacterEquipmentSystem.kt"
POKER = CORE / "PokerDiceCore.kt"
TEST = TESTS / "PokerDiceCoreBackportTest.kt"


def one(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


POKER.write_text(r'''package com.rabpit.backroom.core

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
 * A Poker Dice session is bound to one encounter + one selected combat action so reload or action
 * switching cannot create free rerolls.
 */
object PokerDiceCore {
  const val DICE_COUNT = 5
  const val MAX_REROLLS = 3
  const val BASE_STAT = 5
  const val MAX_STAT = 999
  const val ENTITY_VICTORY_CORE = 2

  private const val PREFIX = "poker."
  private const val CORE_COUNT = PREFIX + "core.count"
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

  fun coreCount(state: GameState): Int =
    state.metadata[CORE_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

  fun coreStat(state: GameState, characterId: String, stat: String): Int {
    val key = normalizeStat(stat)
    if (key.isEmpty()) return BASE_STAT
    return state.metadata[statKey(characterId, key)]?.toIntOrNull()?.coerceIn(BASE_STAT, MAX_STAT)
      ?: BASE_STAT
  }

  fun statPercent(state: GameState, characterId: String, stat: String): Int =
    100 + 10 * (coreStat(state, characterId, stat) - BASE_STAT)

  fun scaleByPercent(value: Int, percent: Int): Int {
    val scaled = max(0L, value.toLong()) * max(0, percent).toLong()
    return min(Int.MAX_VALUE.toLong(), (scaled + 50L) / 100L).toInt()
  }

  fun upgradeCost(currentStat: Int): Int {
    val normalized = currentStat.coerceIn(BASE_STAT, MAX_STAT)
    return 1 + (normalized - BASE_STAT) / 2
  }

  fun grantCore(state: GameState, amount: Int): GameState {
    if (amount <= 0) return state
    val current = coreCount(state)
    val next = min(Int.MAX_VALUE.toLong(), current.toLong() + amount.toLong()).toInt()
    return withMetadata(state, CORE_COUNT, next.toString())
  }

  fun upgrade(state: GameState, rawCharacterId: String, rawStat: String): UpgradeResult {
    val id = rawCharacterId.trim().lowercase(Locale.ROOT)
    require(id.isNotEmpty() && state.characters.containsKey(id)) { "Nhân vật không tồn tại." }
    val stat = normalizeStat(rawStat)
    require(stat.isNotEmpty()) { "Chỉ có thể nâng STR, DEF, SKL hoặc VIT." }
    val current = coreStat(state, id, stat)
    check(current < MAX_STAT) { "Chỉ số đã đạt giới hạn." }
    val cost = upgradeCost(current)
    val available = coreCount(state)
    check(available >= cost) { "Không đủ Core. Cần " + cost + " Core." }

    val oldMax = CharacterStatEngine.effective(state, id).maxHp
    val oldHp = state.characters[id]?.vitalState?.currentHp ?: oldMax
    var next = withMetadata(state, statKey(id, stat), (current + 1).toString())
    next = withMetadata(next, CORE_COUNT, (available - cost).toString())

    if (stat == "VIT" && oldHp > 0) {
      val newMax = CharacterStatEngine.effective(next, id).maxHp
      next = CharacterStatEngine.setCurrentHp(next, id, oldHp + max(0, newMax - oldMax))
    }
    return UpgradeResult(next, id, stat, current + 1, cost, available - cost)
  }

  fun statsJson(state: GameState, characterId: String): JSONObject {
    val id = characterId.trim().lowercase(Locale.ROOT)
    val stats = JSONObject()
    STAT_KEYS.forEach { stat ->
      val value = coreStat(state, id, stat)
      stats.put(stat, JSONObject()
        .put("value", value)
        .put("multiplierPercent", statPercent(state, id, stat))
        .put("nextCoreCost", if (value >= MAX_STAT) JSONObject.NULL else upgradeCost(value)))
    }
    return JSONObject()
      .put("schema", "poker_core_v1")
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

  private fun statKey(characterId: String, stat: String): String {
    val id = characterId.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]+"), "_")
    return PREFIX + "core.stat." + id + "." + stat
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
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# Adapt the current CharacterStatEngine instead of replacing current equipment.
# Core base 5 = exactly 100%, preserving every existing 1.1.63.0.1 stat/test.
# STR -> physical/weapon output, DEF -> DF, SKL -> CRIT, VIT -> HP + AGI.
# ---------------------------------------------------------------------------
equipment = EQUIPMENT.read_text(encoding="utf-8")
start = equipment.find("object CharacterStatEngine {")
effective_start = equipment.find("  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {", start)
fallback_start = equipment.find("\n  private fun fallback(characterId: String): EffectiveCharacterStats {", effective_start)
if start < 0 or effective_start < 0 or fallback_start < 0:
    raise RuntimeError("CharacterStatEngine effective() boundaries missing")
new_effective = r'''  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val character = state.characters[characterId] ?: return fallback(characterId)
    val definitions = state.equipment[character.equipmentId]?.slots.orEmpty().values
      .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }
    val hp = definitions.sumOf { it.bonuses.hp }
    val str = definitions.sumOf { it.bonuses.str }
    val df = definitions.sumOf { it.bonuses.df }
    val agi = definitions.sumOf { it.bonuses.agi }
    val crit = definitions.sumOf { it.bonuses.crit }
    val vitPercent = PokerDiceCore.statPercent(state, characterId, "VIT")
    return EffectiveCharacterStats(
      maxHp = PokerDiceCore.scaleByPercent((character.statProfile.baseMaxHp + hp).coerceAtLeast(1), vitPercent).coerceAtLeast(1),
      equipmentHp = hp,
      str = PokerDiceCore.scaleByPercent(character.statProfile.str + str, PokerDiceCore.statPercent(state, characterId, "STR")).coerceAtLeast(1),
      df = PokerDiceCore.scaleByPercent(character.statProfile.df + df, PokerDiceCore.statPercent(state, characterId, "DEF")).coerceAtLeast(1),
      agi = PokerDiceCore.scaleByPercent(character.statProfile.agi + agi, vitPercent).coerceAtLeast(1),
      crit = PokerDiceCore.scaleByPercent(character.statProfile.crit + crit, PokerDiceCore.statPercent(state, characterId, "SKL")).coerceAtLeast(0),
      energy = character.statProfile.energy,
      regenPerCompletedTurn = if (character.statProfile.regen.enabled) character.statProfile.regen.amountPerCompletedTurn else 0
    )
  }
'''
equipment = equipment[:effective_start] + new_effective + equipment[fallback_start:]

weapon_start = equipment.find("  fun weaponDamage(state: GameState, characterId: String): Int {", start)
weapon_end = equipment.find("\n  }\n}", weapon_start)
if weapon_start < 0 or weapon_end < 0:
    raise RuntimeError("CharacterStatEngine weaponDamage() boundary missing")
weapon_end += len("\n  }")
new_weapon = r'''  fun weaponDamage(state: GameState, characterId: String): Int {
    val weaponId = state.equipment[characterId]?.slots?.get(EquipmentSlot.WEAPON.key)
    val base = weaponId?.let { EquipmentCatalog.definition(it)?.weapon?.dmg } ?: 18
    val coreScaled = PokerDiceCore.scaleByPercent(base, PokerDiceCore.statPercent(state, characterId, "STR"))
    return PokerDiceCore.scaleByPercent(coreScaled, PokerDiceCore.attackPercent(state)).coerceAtLeast(1)
  }'''
equipment = equipment[:weapon_start] + new_weapon + equipment[weapon_end:]
for marker in (
    'PokerDiceCore.statPercent(state, characterId, "STR")',
    'PokerDiceCore.statPercent(state, characterId, "DEF")',
    'PokerDiceCore.statPercent(state, characterId, "SKL")',
    'PokerDiceCore.statPercent(state, characterId, "VIT")',
    'PokerDiceCore.attackPercent(state)',
):
    if marker not in equipment:
        raise RuntimeError("Core stat integration missing: " + marker)
EQUIPMENT.write_text(equipment, encoding="utf-8")


# ---------------------------------------------------------------------------
# Current Pressure Combat remains authoritative. Poker hand augments the existing
# Attack / Evade / Flee mechanics; current Party attacks, skills and ultimates remain.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
combat = one(
    combat,
    '          escapeProgress = min(100, c.escapeProgress + if (goodCounter) 18 else 10),',
    '          escapeProgress = min(100, c.escapeProgress + (if (goodCounter) 18 else 10) + PokerDiceCore.escapeBonus(state)),',
    "Poker Dice evade escape bonus",
)
combat = one(
    combat,
    '        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }',
    '        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 } + PokerDiceCore.escapeBonus(state)',
    "Poker Dice flee bonus",
)
hit_pattern = re.compile(r'val hitChance = \(58 \+ rangeBonus \+ c\.opening \* 11 \+ c\.momentum \* 6\)\.coerceIn\(20, 96\)')
combat, count = hit_pattern.subn(
    'val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6 + PokerDiceCore.accuracyBonus(state)).coerceIn(20, 99)',
    combat,
    count=1,
)
if count != 1:
    raise RuntimeError(f"Poker Dice attack accuracy anchor expected 1, found {count}")
defense_pattern = re.compile(
    r'(\s+val defense = when \(intent\) \{ Intent\.EVADE -> 34; Intent\.GUARD -> 30; Intent\.MOVE -> 18; Intent\.READ -> 12; else -> 0 \} \+\n'
    r'\s+when \(c\.cover\) \{ Cover\.HARD -> 22; Cover\.PARTIAL -> 10; Cover\.EXPOSED -> 0 \} \+ max\(0, c\.momentum\) \* 4)(?!\s*\+\s*PokerDiceCore\.defenseBonus)'
)
combat, count = defense_pattern.subn(r'\1 +\n      PokerDiceCore.defenseBonus(state)', combat, count=1)
if count != 1:
    raise RuntimeError(f"Poker Dice defense bonus: expected exactly 1 match, found {count}")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# GameCoreFacade: shared Core, persistent Poker Dice and combat commit gating.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
facade_methods = r'''
  fun coreStats(legacyStateJson: String, characterId: String): String {
    val state = loadOrMigrate(JSONObject(legacyStateJson))
    return PokerDiceCore.statsJson(state, characterId).toString()
  }

  fun processCoreUpgrade(legacyStateJson: String, characterId: String, stat: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) != null) {
      return response(false, syncLegacy(legacy, current, incrementTurn = false),
        "combat_active", "core_upgrade_locked", "Không thể nâng Core khi combat đang hoạt động.")
    }
    return try {
      val upgraded = PokerDiceCore.upgrade(current, characterId, stat)
      repository.save(upgraded.state)
      val projected = syncLegacy(legacy, upgraded.state, incrementTurn = false)
      response(true, projected, null, "core_upgrade_committed",
        upgraded.stat + " +" + "1 · -" + upgraded.cost + " Core · còn " + upgraded.coreRemaining + " Core.")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "core_upgrade_failed", "core_upgrade_rejected")
    }
  }

  fun prepareCombatDice(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val combat = CombatRuntime.active(current)
      ?: return response(false, syncLegacy(legacy, current, incrementTurn = false),
        "combat_inactive", "combat_dice_inactive")
    return try {
      val next = PokerDiceCore.prepare(current, action, combat.encounterId)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_prepared")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_prepare_failed", "combat_dice_rejected")
    }
  }

  fun setCombatDiceHold(legacyStateJson: String, dieIndex: Int, held: Boolean): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.setHold(current, dieIndex, held)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_hold")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_hold_failed", "combat_dice_rejected")
    }
  }

  fun rerollCombatDice(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.reroll(current)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_rolled")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_roll_failed", "combat_dice_rejected")
    }
  }

  fun finishCombatDice(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.finish(current)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_finalized")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_finish_failed", "combat_dice_rejected")
    }
  }

'''
anchor = "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n"
if "fun prepareCombatDice(legacyStateJson: String, action: String)" not in facade:
    if anchor not in facade:
        raise RuntimeError("GameCoreFacade currentCoreState anchor missing")
    facade = facade.replace(anchor, facade_methods + anchor, 1)

# Include dice state + Core count in the client-safe legacy projection.
projection_old = '    CombatRuntime.toJson(state)?.let { output.put("combat", it) } ?: output.remove("combat")\n'
projection_new = '''    CombatRuntime.toJson(state)?.let { combat ->
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      output.put("combat", combat)
    } ?: output.remove("combat")
    output.put("coreSystem", PokerDiceCore.statsJson(state, KAI_ID))
'''
facade = one(facade, projection_old, projection_new, "Poker Dice legacy projection")

method_start = facade.find("  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {\n")
if method_start < 0:
    raise RuntimeError("processCombat missing")
method_end = facade.find("\n  fun ", method_start + 1)
if method_end < 0:
    method_end = facade.find("\n  private fun ", method_start + 1)
if method_end < 0:
    raise RuntimeError("processCombat method end missing")
method = facade[method_start:method_end]

inactive = '    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")\n'
gate = '''    val activeCombat = CombatRuntime.active(current)
      ?: return response(false, legacy, null, "combat_inactive")
    if (!PokerDiceCore.isFinalizedFor(current, action, activeCombat.encounterId)) {
      return response(true, syncLegacy(legacy, current, incrementTurn = false),
        "combat_dice_required", "combat_dice_required",
        "Hãy hoàn tất Poker Dice trước khi giải quyết hành động combat.")
    }
'''
method = one(method, inactive, gate, "combat Poker Dice gate")

next_anchor = "    var next = resolution.state\n"
next_new = '''    var next = resolution.state
    val coreReward = if (resolution.entityDestroyed) PokerDiceCore.ENTITY_VICTORY_CORE else 0
    if (coreReward > 0) next = PokerDiceCore.grantCore(next, coreReward)
    next = PokerDiceCore.clearDice(next)
'''
method = one(method, next_anchor, next_new, "combat Core reward + dice clear")

if "val combatReply =" not in method:
    output_anchor = "    val output = syncLegacy(legacy, next, incrementTurn = true)\n"
    output_new = '''    val output = syncLegacy(legacy, next, incrementTurn = true)
    val combatReply = if (coreReward > 0)
      resolution.reply + " +" + coreReward + " Core."
    else resolution.reply
'''
    method = one(method, output_anchor, output_new, "combat Core reward reply")
    method = method.replace("    appendLog(output, action, resolution.reply)\n", "    appendLog(output, action, combatReply)\n", 1)
    method = method.replace(", resolution.reply)\n  }", ", combatReply)\n  }", 1)

for marker in (
    "PokerDiceCore.isFinalizedFor(current, action, activeCombat.encounterId)",
    "PokerDiceCore.ENTITY_VICTORY_CORE",
    "PokerDiceCore.clearDice(next)",
    "val combatReply =",
):
    if marker not in method:
        raise RuntimeError("combat Core integration missing: " + marker)

facade = facade[:method_start] + method + facade[method_end:]
FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android JS bridge: mutations run on the existing IO executor.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
bridge = r'''
    @JavascriptInterface public String coreStats(String stateJson, String characterId) {
      try {
        return requireGameCore().coreStats(stateJson, characterId);
      } catch (Exception error) {
        return "{}";
      }
    }

    @JavascriptInterface public void coreUpgrade(String stateJson, String characterId, String stat) {
      io.execute(() -> {
        try {
          emit("backroomCoreUpgrade", requireGameCore().processCoreUpgrade(stateJson, characterId, stat));
        } catch (Exception error) {
          emit("backroomCoreUpgrade", new JSONObject()
            .put("handled", false)
            .put("error", error.getMessage() == null ? "Không thể nâng Core." : error.getMessage())
            .toString());
        }
      });
    }

    @JavascriptInterface public void combatDicePrepare(String stateJson, String action) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().prepareCombatDice(stateJson, action)));
    }

    @JavascriptInterface public void combatDiceHold(String stateJson, int dieIndex, boolean held) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().setCombatDiceHold(stateJson, dieIndex, held)));
    }

    @JavascriptInterface public void combatDiceRoll(String stateJson) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().rerollCombatDice(stateJson)));
    }

    @JavascriptInterface public void combatDiceFinish(String stateJson) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().finishCombatDice(stateJson)));
    }

'''
helper = r'''
  private interface CombatDiceCall { String run() throws Exception; }

  private void emitCombatDiceResult(CombatDiceCall call) {
    try {
      JSONObject result = new JSONObject(call.run());
      if (!result.optBoolean("handled", false)) {
        emit("backroomCombatDiceError", result.optString("error", "Poker Dice không khả dụng."));
        return;
      }
      emit("backroomCombatDiceState", result.getJSONObject("state").toString());
    } catch (Exception error) {
      emit("backroomCombatDiceError",
        error.getMessage() == null ? "Không thể cập nhật Poker Dice." : error.getMessage());
    }
  }

'''
if "private interface CombatDiceCall" not in main:
    emit_anchor = "  private class GameBridge {\n"
    if emit_anchor not in main:
        raise RuntimeError("MainActivity GameBridge anchor missing")
    main = main.replace(emit_anchor, helper + emit_anchor, 1)

if "@JavascriptInterface public void combatDicePrepare" not in main:
    req_anchor = "    @JavascriptInterface public void requestSnapshot(String stateJson) {\n"
    if req_anchor not in main:
        raise RuntimeError("MainActivity requestSnapshot bridge anchor missing")
    main = main.replace(req_anchor, bridge + req_anchor, 1)

for marker in (
    "@JavascriptInterface public String coreStats",
    "@JavascriptInterface public void coreUpgrade",
    "@JavascriptInterface public void combatDicePrepare",
    "@JavascriptInterface public void combatDiceHold",
    "@JavascriptInterface public void combatDiceRoll",
    "@JavascriptInterface public void combatDiceFinish",
    'emit("backroomCombatDiceState"',
):
    if marker not in main:
        raise RuntimeError("Android Poker/Core bridge missing: " + marker)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Current 7px UI: Action bar stays unchanged. A combat button opens a Poker Dice
# bottom sheet; Character Detail receives a compact Core Stats panel.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
submit_start = html.find("  function submitCombat(action){")
submit_end = html.find("\n  function interceptCombatClick", submit_start)
if submit_start < 0 or submit_end < 0:
    raise RuntimeError("final combat action submit method missing")
submit_method = r'''  function submitCombat(action){
    if(!combatActive())return false;
    if(typeof busy!=='undefined'&&busy)return true;
    if(typeof window.openCombatDiceForAction==='function'){
      window.openCombatDiceForAction(action);
      return true;
    }
    if(!window.Android||typeof window.Android.submitAction!=='function'){var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android action bridge.';return true;}
    if(typeof busy!=='undefined')busy=true;
    pending(action);
    var status=document.getElementById('status');if(status)status.textContent='Đang xử lý hành động chiến đấu…';
    renderCombatActionBar();
    window.Android.submitAction(JSON.stringify(state),'EXECUTE',action);
    return true;
  }'''
html = html[:submit_start] + submit_method + html[submit_end:]

ui = r'''
<style id="pokerCoreUiStyle">
/* POKER_DICE_CORE_BACKPORT_R01 */
.poker-dice-modal[hidden]{display:none}.poker-dice-modal{position:fixed;inset:0;z-index:145;display:flex;align-items:flex-end;justify-content:center}.poker-dice-backdrop{position:absolute;inset:0;background:#000c}
.poker-dice-sheet{position:relative;width:min(100%,680px);max-height:min(calc(var(--app-height,100dvh) - 10px),760px);overflow:auto;background-color:#39341e;background-image:linear-gradient(145deg,rgba(15,17,13,.64),rgba(12,15,13,.82) 55%,rgba(15,17,13,.68)),url('dice/level0-wallpaper.svg');background-size:auto,64px 96px;border:1px solid #8b8052;border-bottom:0;border-radius:7px 7px 0 0;padding:14px 14px calc(14px + env(safe-area-inset-bottom));box-shadow:0 -24px 60px #000d}
.poker-dice-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.poker-dice-kicker{font-size:9px;font-weight:800;letter-spacing:.16em;color:#b9ad77}.poker-dice-head h2{margin:3px 0 0;font-size:15px;letter-spacing:.08em;color:#fff7d7}.poker-dice-close{width:42px;height:42px;padding:0;border-radius:7px!important}
.poker-dice-meta{margin-top:8px;color:#c5c3a8;font-size:11px}.poker-dice-row{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:4px;margin-top:12px}
.poker-die{position:relative;min-width:0;aspect-ratio:1/1.18;padding:4px;border:1px solid transparent!important;border-radius:7px!important;background:transparent!important;display:grid;place-items:center}.poker-die img{width:100%;height:100%;object-fit:contain;filter:drop-shadow(0 4px 4px #0008)}.poker-die.held img{filter:drop-shadow(0 0 2px #9be1bc) drop-shadow(0 0 8px #9be1bc99) drop-shadow(0 4px 4px #0008)}.poker-die.held:after{content:"HOLD";position:absolute;bottom:2px;left:50%;transform:translateX(-50%);font-size:8px;font-weight:800;letter-spacing:.08em;color:#9be1bc;text-shadow:0 0 7px #9be1bc88}
.poker-die.rolling img{visibility:hidden}.poker-die.rolling:before{content:"";position:absolute;inset:5%;background:url('dice/roll-3d.png') 0 0/2400% 100% no-repeat;animation:poker-die-roll .68s steps(23,end) infinite}@keyframes poker-die-roll{to{background-position:100% 0}}@media(prefers-reduced-motion:reduce){.poker-die.rolling:before{display:none}.poker-die.rolling img{visibility:visible}}
.poker-dice-hand{min-height:26px;margin-top:10px;text-align:center;font-size:17px;font-weight:800;letter-spacing:.06em;color:#e6cf77;text-shadow:0 1px 2px #000}.poker-dice-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}.poker-dice-roll,.poker-dice-finish{min-height:48px;border-radius:7px!important;font-weight:800;letter-spacing:.1em}.poker-dice-roll{border-color:#4f9479!important;background:#14251f!important;color:#dff7ec}.poker-dice-finish{border-color:#8b6047!important;background:#281c18!important;color:#f2ddd0}
.core-progression-panel{margin-top:10px;border:1px solid #3b3524;background:linear-gradient(145deg,#171813,#0b0e10);padding:10px;border-radius:7px}.core-progression-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:7px}.core-progression-head b{font-size:10px;letter-spacing:.12em;color:#d5c67d}.core-balance{font-size:12px;font-weight:800;color:#f6c85f}.core-progression-row{display:grid;grid-template-columns:42px minmax(0,1fr) auto;gap:7px;align-items:center;padding:6px 0;border-top:1px solid #25291f}.core-progression-row:first-of-type{border-top:0}.core-progression-stat{font-weight:900;color:#eef1f3}.core-progression-value{min-width:0;color:#aeb8bf;font-size:11px}.core-progression-upgrade{min-width:72px;padding:7px 8px!important;border-radius:7px!important;font-size:10px}
</style>
<div class="poker-dice-modal" id="pokerDiceModal" hidden aria-hidden="true">
  <div class="poker-dice-backdrop"></div>
  <section class="poker-dice-sheet" role="dialog" aria-modal="true" aria-labelledby="pokerDiceTitle">
    <div class="poker-dice-head"><div><div class="poker-dice-kicker">COMBAT DICE</div><h2 id="pokerDiceTitle">POKER DICE</h2></div><button type="button" class="poker-dice-close" id="pokerDiceClose" aria-label="Đóng">×</button></div>
    <div class="poker-dice-meta" id="pokerDiceMeta"></div>
    <div class="poker-dice-row" id="pokerDiceRow"></div>
    <div class="poker-dice-hand" id="pokerDiceHand"></div>
    <div class="poker-dice-actions"><button type="button" class="poker-dice-roll" id="pokerDiceRoll">ROLL</button><button type="button" class="poker-dice-finish" id="pokerDiceFinish">FINISH</button></div>
  </section>
</div>
<script>
(function(){
  "use strict";
  if(window.__pokerCoreUiInstalled)return;window.__pokerCoreUiInstalled=true;
  var modal=document.getElementById("pokerDiceModal"),row=document.getElementById("pokerDiceRow"),meta=document.getElementById("pokerDiceMeta"),hand=document.getElementById("pokerDiceHand");
  var roll=document.getElementById("pokerDiceRoll"),finish=document.getElementById("pokerDiceFinish"),close=document.getElementById("pokerDiceClose");
  var pendingAction="",submitAfterFinalize=false;window.__combatDiceBusy=false;

  function dice(){return state&&state.combat&&state.combat.diceState?state.combat.diceState:null}
  function show(){modal.hidden=false;modal.setAttribute("aria-hidden","false");document.body.classList.add("poker-dice-open")}
  function hide(){modal.hidden=true;modal.setAttribute("aria-hidden","true");document.body.classList.remove("poker-dice-open")}
  function setBusy(value){window.__combatDiceBusy=!!value;if(typeof busy!=="undefined")busy=!!value||!modal.hidden;if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar()}
  function renderDice(){
    var d=dice();if(!d){row.textContent="";hand.textContent="";meta.textContent="";return}
    pendingAction=String(d.action||pendingAction||"");
    document.getElementById("pokerDiceTitle").textContent=pendingAction||"POKER DICE";
    meta.textContent="ROLL "+String(d.rerollsUsed||0)+"/"+String(d.maxRerolls||3)+" · chạm xúc xắc để HOLD";
    row.textContent="";
    var values=Array.isArray(d.values)?d.values:[],held=Array.isArray(d.held)?d.held:[];
    for(var i=0;i<5;i++){(function(index){
      var button=document.createElement("button");button.type="button";button.className="poker-die"+(held[index]?" held":"")+(window.__combatDiceBusy&&window.__combatDiceRolling&&!held[index]?" rolling":"");button.disabled=window.__combatDiceBusy||d.finalized===true;
      var img=document.createElement("img"),value=Number(values[index])||1;img.src="file:///android_asset/dice/die-"+value+".png";img.alt="D"+String(value);button.appendChild(img);
      button.addEventListener("click",function(){if(window.__combatDiceBusy||!window.Android||typeof Android.combatDiceHold!=="function")return;setBusy(true);Android.combatDiceHold(JSON.stringify(state),index,!held[index])});row.appendChild(button);
    })(i)}
    hand.textContent=d.hand?("["+String(d.hand)+"]"):"";
    var allHeld=held.length===5&&held.every(function(x){return x===true});
    roll.disabled=window.__combatDiceBusy||d.finalized===true||Number(d.rerollsUsed)>=Number(d.maxRerolls||3)||allHeld;
    finish.disabled=window.__combatDiceBusy||d.finalized===true;
  }

  window.openCombatDiceForAction=function(action){
    if(window.__combatDiceBusy)return;
    if(!window.Android||typeof Android.combatDicePrepare!=="function"){if(statusEl)statusEl.textContent="Không tìm thấy Poker Dice bridge.";return}
    pendingAction=String(action||"").trim();if(!pendingAction)return;
    show();setBusy(true);if(statusEl)statusEl.textContent="Đang chuẩn bị Poker Dice…";
    Android.combatDicePrepare(JSON.stringify(state),pendingAction);
  };

  roll.addEventListener("click",function(){
    if(window.__combatDiceBusy||!window.Android||typeof Android.combatDiceRoll!=="function")return;
    window.__combatDiceRolling=true;setBusy(true);renderDice();Android.combatDiceRoll(JSON.stringify(state));
  });
  finish.addEventListener("click",function(){
    if(window.__combatDiceBusy||!window.Android||typeof Android.combatDiceFinish!=="function")return;
    submitAfterFinalize=true;setBusy(true);Android.combatDiceFinish(JSON.stringify(state));
  });
  close.addEventListener("click",function(){
    if(window.__combatDiceBusy)return;hide();if(typeof busy!=="undefined")busy=false;if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar();
  });

  window.backroomCombatDiceState=function(json){
    try{
      state=JSON.parse(json);window.__combatDiceRolling=false;window.__combatDiceBusy=false;show();renderDice();
      var d=dice();
      if(submitAfterFinalize&&d&&d.finalized===true){
        submitAfterFinalize=false;hide();if(typeof busy!=="undefined")busy=true;
        if(typeof appendMacroPending==="function")appendMacroPending(pendingAction);
        if(statusEl)statusEl.textContent="Poker Dice đã chốt. Đang giải quyết combat…";
        Android.submitAction(JSON.stringify(state),"EXECUTE",pendingAction);
        return;
      }
      if(typeof busy!=="undefined")busy=true;
      if(statusEl)statusEl.textContent="Poker Dice · "+String(d&&d.hand||"");
      if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar();
    }catch(_){window.backroomCombatDiceError("Poker Dice state không hợp lệ.")}
  };
  window.backroomCombatDiceError=function(message){
    window.__combatDiceBusy=false;window.__combatDiceRolling=false;submitAfterFinalize=false;
    if(typeof busy!=="undefined")busy=false;if(statusEl)statusEl.textContent=String(message||"Poker Dice lỗi.");renderDice();if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar();
  };

  function selectedCharacter(){
    var view=document.getElementById("characterInventoryView"),id=view&&view.dataset.characterId||"kai";
    var members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[];
    return members.find(function(m){return String(m&&m.id||"")===String(id)})||members[0]||{id:id};
  }
  function readCoreStats(id){
    try{return window.Android&&typeof Android.coreStats==="function"?JSON.parse(Android.coreStats(JSON.stringify(state),id)):null}catch(_){return null}
  }
  function renderCorePanel(){
    var host=document.getElementById("characterStatusList");if(!host)return;
    var member=selectedCharacter(),id=String(member&&member.id||"kai"),data=readCoreStats(id);if(!data||!data.stats)return;
    var panel=document.getElementById("coreProgressionPanel");if(!panel){panel=document.createElement("section");panel.id="coreProgressionPanel";panel.className="core-progression-panel";host.appendChild(panel)}
    panel.textContent="";
    var head=document.createElement("div");head.className="core-progression-head";head.innerHTML="<b>CORE STATS</b><span class='core-balance'>Core "+Number(data.sharedCore||0)+"</span>";panel.appendChild(head);
    ["STR","DEF","SKL","VIT"].forEach(function(key){
      var s=data.stats[key]||{},line=document.createElement("div");line.className="core-progression-row";
      var name=document.createElement("span");name.className="core-progression-stat";name.textContent=key;
      var value=document.createElement("span");value.className="core-progression-value";value.textContent=String(s.value||5)+" · ×"+((Number(s.multiplierPercent||100)/100).toFixed(1))+" · next "+(s.nextCoreCost==null?"MAX":s.nextCoreCost+" Core");
      var button=document.createElement("button");button.type="button";button.className="core-progression-upgrade";button.textContent="+1 ("+(s.nextCoreCost==null?"—":s.nextCoreCost)+")";
      button.disabled=!!window.__coreUpgradeBusy||!!window.__combatBusy||(typeof busy!=="undefined"&&!!busy)||s.nextCoreCost==null||Number(data.sharedCore||0)<Number(s.nextCoreCost||0);
      button.addEventListener("click",function(){
        if(button.disabled||!window.Android||typeof Android.coreUpgrade!=="function")return;
        window.__coreUpgradeBusy=true;renderCorePanel();Android.coreUpgrade(JSON.stringify(state),id,key);
      });
      line.appendChild(name);line.appendChild(value);line.appendChild(button);panel.appendChild(line);
    });
  }
  window.backroomCoreUpgrade=function(payload){
    window.__coreUpgradeBusy=false;
    try{var result=JSON.parse(payload);if(result&&result.state)state=result.state;if(statusEl)statusEl.textContent=result&&result.handled?String(result.reply||"Đã nâng Core."):String(result&&result.error||"Không thể nâng Core.");}
    catch(_){if(statusEl)statusEl.textContent="Core upgrade trả dữ liệu không hợp lệ."}
    if(typeof window.render==="function")window.render();else renderCorePanel();
  };

  var previousRender=window.render;if(typeof previousRender==="function")window.render=function(){var result=previousRender.apply(this,arguments);renderCorePanel();return result};
  var previousTurn=window.backroomTurn;if(typeof previousTurn==="function")window.backroomTurn=function(json){var result=previousTurn.call(this,json);hide();window.__combatDiceBusy=false;window.__combatDiceRolling=false;submitAfterFinalize=false;if(typeof busy!=="undefined")busy=false;renderCorePanel();return result};
  renderCorePanel();
})();
</script>
'''
if "POKER_DICE_CORE_BACKPORT_R01" not in html:
    if "</body>" not in html:
        raise RuntimeError("index body closing tag missing")
    html = html.replace("</body>", ui + "\n</body>", 1)

for marker in (
    "POKER_DICE_CORE_BACKPORT_R01",
    "openCombatDiceForAction",
    'id="pokerDiceModal"',
    "dice/roll-3d.png",
    "CORE STATS",
    "Android.coreUpgrade",
    "Android.combatDicePrepare",
    "Android.combatDiceRoll",
    "Android.combatDiceFinish",
    "border-radius:7px",
):
    if marker not in html:
        raise RuntimeError("Poker/Core UI contract missing: " + marker)
INDEX.write_text(html, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage for authoritative dice persistence + Core economy.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PokerDiceCoreBackportTest {
  @Test fun handClassificationMatchesBackportedPokerRules() {
    assertEquals("FSF", PokerDiceCore.classify(listOf(4,4,4,4,4)))
    assertEquals("SSF", PokerDiceCore.classify(listOf(1,2,3,4,5)))
    assertEquals("SSF", PokerDiceCore.classify(listOf(5,4,3,2,1)))
    assertEquals("STRAIGHT", PokerDiceCore.classify(listOf(2,3,4,5,6)))
    assertEquals("FOUR OF A KIND", PokerDiceCore.classify(listOf(6,6,6,6,2)))
    assertEquals("FULL HOUSE", PokerDiceCore.classify(listOf(3,3,3,5,5)))
    assertEquals("THREE OF A KIND", PokerDiceCore.classify(listOf(2,2,2,4,6)))
    assertEquals("TWO PAIR", PokerDiceCore.classify(listOf(1,1,5,5,3)))
    assertEquals("ONE PAIR", PokerDiceCore.classify(listOf(1,1,3,4,6)))
    assertEquals("NO HAND", PokerDiceCore.classify(listOf(1,3,4,5,6)))
  }

  @Test fun diceSessionPersistsHoldsAndCapsRerolls() {
    var state = PokerDiceCore.prepare(GameState.initial(), "Tấn công", "E1")
    val initial = PokerDiceCore.diceJson(state)!!
    assertEquals(5, initial.getJSONArray("values").length())
    repeat(3) { state = PokerDiceCore.reroll(state) }
    val after = PokerDiceCore.diceJson(state)!!
    assertEquals(3, after.getInt("rerollsUsed"))
    try {
      PokerDiceCore.reroll(state)
      fail("fourth reroll must be rejected")
    } catch (_: IllegalStateException) {}
  }

  @Test fun changingActionCannotResetActiveDiceSession() {
    val state = PokerDiceCore.prepare(GameState.initial(), "Tấn công", "E1")
    try {
      PokerDiceCore.prepare(state, "Bỏ chạy", "E1")
      fail("action switching must not reset dice")
    } catch (_: IllegalStateException) {}
  }

  @Test fun finalizedHandIsBoundToSelectedActionAndEncounter() {
    var state = PokerDiceCore.prepare(GameState.initial(), "Tấn công", "E1")
    state = PokerDiceCore.finish(state)
    assertTrue(PokerDiceCore.isFinalizedFor(state, "Tấn công", "E1"))
    assertFalse(PokerDiceCore.isFinalizedFor(state, "Bỏ chạy", "E1"))
    assertFalse(PokerDiceCore.isFinalizedFor(state, "Tấn công", "E2"))
  }

  @Test fun coreDefaultsDoNotChangeCurrentStatsAndUpgradeUsesSharedResource() {
    var state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val before = CharacterStatEngine.effective(state, KAI_ID)
    assertEquals(5, PokerDiceCore.coreStat(state, KAI_ID, "STR"))
    assertEquals(0, PokerDiceCore.coreCount(state))
    assertEquals(140, before.maxHp)
    assertEquals(107, before.str)
    assertEquals(109, before.df)
    assertEquals(112, before.agi)
    assertEquals(109, before.crit)

    state = PokerDiceCore.grantCore(state, 4)
    val upgraded = PokerDiceCore.upgrade(state, KAI_ID, "STR")
    assertEquals(6, PokerDiceCore.coreStat(upgraded.state, KAI_ID, "STR"))
    assertEquals(3, PokerDiceCore.coreCount(upgraded.state))
    assertEquals(118, CharacterStatEngine.effective(upgraded.state, KAI_ID).str)
  }

  @Test fun vitUpgradePreservesMissingHpWhileIncreasingMaxHp() {
    var state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 100)
    state = PokerDiceCore.grantCore(state, 2)
    val result = PokerDiceCore.upgrade(state, KAI_ID, "VIT")
    val effective = CharacterStatEngine.effective(result.state, KAI_ID)
    assertEquals(154, effective.maxHp)
    assertEquals(114, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }
}
''', encoding="utf-8")

for path, markers in {
    POKER: ["object PokerDiceCore", "MAX_REROLLS = 3", "ENTITY_VICTORY_CORE = 2", "FOUR OF A KIND", "HELD_SEQUENCE_WEIGHT_BONUS"],
    FACADE: ["fun prepareCombatDice", "fun processCoreUpgrade", "PokerDiceCore.isFinalizedFor"],
    MAIN: ["combatDicePrepare", "coreUpgrade", "backroomCombatDiceState"],
    INDEX: ["POKER_DICE_CORE_BACKPORT_R01", "CORE STATS", "pokerDiceModal"],
}.items():
    source = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise RuntimeError(f"final Poker/Core marker missing in {path.name}: {marker}")

print("Poker Dice + Character Core backport installed: persistent 5D6 combat, shared Core progression, current stat/equipment integration and 7px UI.")
