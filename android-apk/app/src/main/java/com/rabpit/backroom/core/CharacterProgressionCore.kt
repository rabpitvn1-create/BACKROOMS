package com.rabpit.backroom.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Authoritative per-character Level and EXP progression, unrelated to Backrooms world Level. */
object CharacterProgressionCore {
  const val SCHEMA = "core_stats_v2"
  const val BASE_STAT = 5
  const val MAX_STAT = 999
  const val BASE_MAX_HP = 50
  // Each new level can add up to 6 to one stat. This bound prevents any roll exceeding MAX_STAT.
  const val MAX_LEVEL = (MAX_STAT - BASE_STAT) / 6
  private val STAT_KEYS = setOf("STR", "DEX", "AGI", "VIT", "EVA")

  data class ExpAward(val state: GameState, val characterId: String, val awardedExp: Int, val levelsGained: Int)

  fun baseStat(state: GameState, characterId: String, rawStat: String): Int {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    return when (normalizeStat(rawStat)) {
      "STR" -> p.str
      "DEX" -> p.dex
      "AGI" -> p.agi
      "VIT" -> p.vit
      "EVA" -> p.eva
      else -> BASE_STAT
    }.coerceIn(BASE_STAT, MAX_STAT)
  }

  fun statPercent(stat: Int): Int = 100 + 4 * (stat.coerceIn(BASE_STAT, MAX_STAT) - BASE_STAT)
  fun statPercent(state: GameState, characterId: String, stat: String): Int =
    statPercent(baseStat(state, characterId, stat))

  /** EXP needed for exactly one advancement from `level` to `level + 1`. */
  fun expRequired(level: Int): Int {
    require(level in 0 until MAX_LEVEL) { "Character Level đã đạt giới hạn." }
    return 10 + 8 * level
  }

  /** System-only reward primitive: callers must verify a real event and avoid replay before awarding. */
  fun grantExp(state: GameState, rawCharacterId: String, amount: Int, random: Random = Random.Default): ExpAward {
    require(amount >= 0) { "EXP thưởng phải không âm." }
    val normalized = normalize(state)
    val id = rawCharacterId.trim().lowercase(Locale.ROOT)
    val original = normalized.characters[id] ?: throw IllegalArgumentException("Nhân vật không tồn tại.")
    if (amount == 0 || original.progression.level == MAX_LEVEL) return ExpAward(normalized, id, 0, 0)

    var character = original
    var xp = character.progression.exp.toLong() + amount.toLong()
    var level = character.progression.level
    var gained = 0
    while (level < MAX_LEVEL && xp >= expRequired(level).toLong()) {
      xp -= expRequired(level).toLong()
      val bonus = IntArray(5) { 1 }
      repeat(5) { bonus[random.nextInt(5)]++ }
      val p = character.statProfile
      val values = intArrayOf(p.str, p.dex, p.agi, p.vit, p.eva)
      for (i in values.indices) check(values[i] + bonus[i] <= MAX_STAT) {
        "STAT chạm giới hạn; không thể phân phối đủ 10 điểm."
      }
      val before = CharacterStatCore.effective(normalized.copy(characters = normalized.characters + (id to character)), id).maxHp
      val updatedProfile = p.copy(
        str = values[0] + bonus[0], dex = values[1] + bonus[1], agi = values[2] + bonus[2],
        vit = values[3] + bonus[3], eva = values[4] + bonus[4]
      )
      val candidate = character.copy(statProfile = updatedProfile)
      val after = CharacterStatCore.effective(normalized.copy(characters = normalized.characters + (id to candidate)), id).maxHp
      val hp = if (character.vitalState.currentHp > 0)
        min(after, character.vitalState.currentHp.toLong().plus((after - before).toLong()).toInt())
      else character.vitalState.currentHp
      character = candidate.copy(vitalState = character.vitalState.copy(
        currentHp = hp, condition = CharacterStatEngine.conditionFor(hp, after, character.vitalState.condition, character.presence)
      ))
      level++
      gained++
    }
    if (level == MAX_LEVEL) xp = 0L
    character = character.copy(progression = CharacterProgression(level, xp.toInt()))
    return ExpAward(normalized.copy(characters = normalized.characters + (id to character)), id, amount, gained)
  }

  fun normalize(input: GameState): GameState {
    // Drop obsolete Core currency; only the five stored primary stats are authoritative.
    val state = input.copy(metadata = sanitizeMetadata(input.metadata))
    val chars = state.characters.toMutableMap()
    state.characters.forEach { (id, c) ->
      val raw = c.statProfile
      val canonical = if (raw.schema == SCHEMA) {
        raw.copy(
          baseMaxHp = BASE_MAX_HP,
          str = raw.str.coerceIn(BASE_STAT, MAX_STAT),
          dex = raw.dex.coerceIn(BASE_STAT, MAX_STAT),
          agi = raw.agi.coerceIn(BASE_STAT, MAX_STAT),
          eva = raw.eva.coerceIn(BASE_STAT, MAX_STAT),
          vit = raw.vit.coerceIn(BASE_STAT, MAX_STAT),
          regen = HpRegenRule(),
          schema = SCHEMA
        )
      } else CharacterStatProfiles.forId(id)
      val level = c.progression.level.coerceIn(0, MAX_LEVEL)
      val exp = if (level == MAX_LEVEL) 0 else c.progression.exp.coerceIn(0, expRequired(level) - 1)
      val provisional = state.copy(characters = state.characters + (id to c.copy(statProfile = canonical)))
      val maxHp = CharacterStatCore.effective(provisional, id).maxHp
      val hp = c.vitalState.currentHp.coerceIn(0, maxHp)
      chars[id] = c.copy(
        statProfile = canonical, progression = CharacterProgression(level, exp),
        vitalState = c.vitalState.copy(
          currentHp = hp,
          condition = CharacterStatEngine.conditionFor(hp, maxHp, c.vitalState.condition, c.presence)
        )
      )
    }
    return state.copy(characters = chars)
  }

  fun protectFromCandidate(authoritative: GameState, candidate: GameState): GameState {
    val auth = normalize(authoritative)
    val cand = normalize(candidate)
    val chars = cand.characters.toMutableMap()
    auth.characters.forEach { (id, a) ->
      val c = chars[id] ?: return@forEach
      chars[id] = c.copy(statProfile = a.statProfile, vitalState = a.vitalState, progression = a.progression)
    }
    return cand.copy(characters = chars, metadata = sanitizeMetadata(cand.metadata))
  }

  fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> =
    metadata.filterKeys { key ->
      !key.startsWith("poker.core.") &&
      !key.startsWith("characterProgression.core") &&
      !key.startsWith("characterRpg") &&
      !key.startsWith("baseStats") &&
      !key.startsWith("explorer") &&
      key != "level" && key != "exp" && key != "EXP"
    }

  fun normalizeStat(raw: String): String {
    val s = raw.trim().uppercase(Locale.ROOT)
    return if (s in STAT_KEYS) s else ""
  }
}

object CharacterStatCore {
  const val DAI_DAO_MA_TON_STAT_BONUS_PERCENT = 10
  const val CRITICAL_DAMAGE_PERCENT = 400
  const val BASIS_POINTS_PER_PERCENT = 100

  fun isCaoMinh(id: String): Boolean = id.equals(CAO_MINH_ID, true) || id.equals("cao_minh", true)

  private fun passiveBonus(base: Int, id: String): Int =
    if (isCaoMinh(id)) (base.coerceIn(CharacterProgressionCore.BASE_STAT, CharacterProgressionCore.MAX_STAT) *
      DAI_DAO_MA_TON_STAT_BONUS_PERCENT + 50) / 100 else 0

  /** Sum of percentage stat bonuses from all equipped items. Bound gear cannot be unequipped. */
  private fun equipmentBonuses(state: GameState, characterId: String): EquipmentBonuses {
    val equipmentId = state.characters[characterId]?.equipmentId ?: characterId
    val itemIds = state.equipment[equipmentId]?.slots?.values ?: return EquipmentBonuses()
    var acc = EquipmentBonuses()
    for (itemId in itemIds) {
      val b = EquipmentCatalog.definition(itemId)?.bonuses ?: continue
      acc = acc.copy(
        agiPct = acc.agiPct + b.agiPct,
        dexPct = acc.dexPct + b.dexPct,
        vitPct = acc.vitPct + b.vitPct,
        strPct = acc.strPct + b.strPct,
        defPct = acc.defPct + b.defPct
      )
    }
    return acc
  }

  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    val equip = equipmentBonuses(state, characterId)
    fun value(raw: Int, pct: Int): Int {
      val base = (raw.coerceIn(5, 999) + passiveBonus(raw, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
      return ((base.toLong() * (100 + pct)) / 100).coerceAtMost(CharacterProgressionCore.MAX_STAT.toLong()).toInt()
    }
    val str = value(p.str, equip.strPct)
    val dex = value(p.dex, equip.dexPct)
    val agi = value(p.agi, equip.agiPct)
    val vit = value(p.vit, equip.vitPct)
    val eva = value(p.eva, 0)
    val def = ((defense(vit).toLong() * (100 + equip.defPct)) / 100).coerceAtMost(CharacterProgressionCore.MAX_STAT.toLong()).toInt()
    val critBp = criticalChanceBasisPoints(agi)
    val dodgeBp = evasionBasisPoints(eva)
    val critResBp = criticalResistanceBasisPoints(dex)
    val dodgeResBp = evasionResistanceBasisPoints(eva)
    val attack = CharacterProgressionCore.statPercent(str)
    return EffectiveCharacterStats(
      maxHp = maxHp(vit),
      str = str, dex = dex, agi = agi, vit = vit, eva = eva, def = def,
      criticalChancePercent = roundPercent(critBp),
      evasionPercent = roundPercent(dodgeBp),
      resCriticalPercent = roundPercent(critResBp),
      resEvasionPercent = roundPercent(dodgeResBp),
      criticalChanceBasisPoints = critBp,
      evasionBasisPoints = dodgeBp,
      resCriticalBasisPoints = critResBp,
      resEvasionBasisPoints = dodgeResBp,
      criticalDamagePercent = CRITICAL_DAMAGE_PERCENT,
      attackPercent = attack, skillDamagePercent = attack,
      energy = p.energy,
      regenPerCompletedTurn = 0
    )
  }

  fun maxHp(vit: Int): Int = CharacterProgressionCore.BASE_MAX_HP +
    5 * (vit.coerceIn(5, 999) - 5)
  fun defense(vit: Int): Int = (vit.coerceIn(5, 999) - 5)

  fun criticalChanceBasisPoints(agi: Int): Int =
    (500 + (agi.coerceIn(5, 999) - 5) * 45).coerceAtMost(3000)
  fun evasionBasisPoints(eva: Int): Int =
    (500 + (eva.coerceIn(5, 999) - 5) * 50).coerceAtMost(3500)
  fun criticalResistanceBasisPoints(dex: Int): Int =
    ((dex.coerceIn(5, 999) - 5) * 25).coerceAtMost(10000)
  fun evasionResistanceBasisPoints(eva: Int): Int =
    ((eva.coerceIn(5, 999) - 5) * 25).coerceAtMost(10000)

  private fun roundPercent(basisPoints: Int): Int = (basisPoints + 50) / BASIS_POINTS_PER_PERCENT
  fun criticalChance(agi: Int): Int = roundPercent(criticalChanceBasisPoints(agi))
  fun evasion(eva: Int): Int = roundPercent(evasionBasisPoints(eva))
  fun criticalResistance(dex: Int): Int = roundPercent(criticalResistanceBasisPoints(dex))
  fun evasionResistance(eva: Int): Int = roundPercent(evasionResistanceBasisPoints(eva))

  fun effectiveChanceBasisPoints(chance: Int, resistance: Int): Int =
    (chance - resistance).coerceIn(0, 10000)

  fun scaleByPercent(value: Int, percent: Int): Int {
    val scaled = max(0L, value.toLong()) * max(0, percent).toLong()
    return min(Int.MAX_VALUE.toLong(), (scaled + 50L) / 100L).toInt()
  }

  fun basicDamage(baseDamage: Int, str: Int, handPercent: Int = 100, attackPercent: Int = 100): Int {
    var out = scaleByPercent(baseDamage, CharacterProgressionCore.statPercent(str))
    out = scaleByPercent(out, handPercent)
    return scaleByPercent(out, attackPercent)
  }

  // Both ATK and Skill DMG come from STR; apply its multiplier exactly once.
  fun skillDamage(baseDamage: Int, skillPercent: Int, str: Int, handPercent: Int = 100): Int {
    var out = scaleByPercent(baseDamage, skillPercent)
    out = scaleByPercent(out, CharacterProgressionCore.statPercent(str))
    return scaleByPercent(out, handPercent)
  }

  fun defendedIncomingDamage(rawDamage: Int, def: Int): Int {
    if (rawDamage <= 0) return 0
    val safeDef = def.coerceAtLeast(0)
    return max(1, ((rawDamage.toLong() * 100L + (100L + safeDef) / 2L) /
      (100L + safeDef)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
  }

  fun criticalDamage(damage: Int): Int = scaleByPercent(damage, CRITICAL_DAMAGE_PERCENT)
  fun effectiveChance(chance: Int, resistance: Int): Int = (chance - resistance).coerceIn(0, 100)
}

object CaoMinhCombatPassive {
  private const val STACK_KEY = "combat.daiDaoMaTonStacks"
  const val HEAL_PERCENT = 10
  const val ATTACK_PER_TURN_PERCENT = 20
  const val CRITICAL_PER_TURN_PERCENT = 20
  const val ALLY_CRITICAL_BONUS_PERCENT = 50
  const val MAX_COMBAT_CRITICAL_PERCENT = 100

  fun stacks(state: GameState): Int = state.metadata[STACK_KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
  fun attackPercent(state: GameState): Int = 100 + ATTACK_PER_TURN_PERCENT * stacks(state)
  fun criticalChance(state: GameState, base: Int): Int =
    (base + CRITICAL_PER_TURN_PERCENT * stacks(state)).coerceIn(0, MAX_COMBAT_CRITICAL_PERCENT)
  fun allyCriticalChance(base: Int): Int = (base + ALLY_CRITICAL_BONUS_PERCENT).coerceIn(0, MAX_COMBAT_CRITICAL_PERCENT)

  fun afterCaoMinhTurn(state: GameState): GameState {
    val c = state.characters[CAO_MINH_ID] ?: return state
    if (c.presence == CharacterPresence.DEAD || c.vitalState.currentHp <= 0) return state
    val maxHp = CharacterStatCore.effective(state, CAO_MINH_ID).maxHp
    val heal = max(1, (maxHp * HEAL_PERCENT + 50) / 100)
    val hp = min(maxHp, c.vitalState.currentHp + heal)
    val nextStack = stacks(state) + 1
    return state.copy(
      characters = state.characters + (CAO_MINH_ID to c.copy(
        vitalState = c.vitalState.copy(currentHp = hp, condition = CharacterStatEngine.conditionFor(hp, maxHp, c.vitalState.condition, c.presence))
      )),
      metadata = state.metadata + (STACK_KEY to nextStack.toString())
    )
  }
}
