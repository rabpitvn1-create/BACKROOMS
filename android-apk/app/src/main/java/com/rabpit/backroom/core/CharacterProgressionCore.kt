package com.rabpit.backroom.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CharacterProgressionCore {
  const val SCHEMA = "core_stats_v2"
  const val BASE_STAT = 5
  const val MAX_STAT = 999
  const val BASE_MAX_HP = 50
  const val CORE_KEY = "characterProgression.coreResource"
  private val STAT_KEYS = setOf("STR", "DEX", "AGI", "VIT", "EVA")

  data class Upgrade(
    val state: GameState,
    val characterId: String,
    val stat: String,
    val value: Int,
    val cost: Int,
    val coreRemaining: Int
  )

  fun coreCount(state: GameState): Int =
    state.metadata[CORE_KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

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

  fun upgradeCost(currentStat: Int): Int =
    1 + (currentStat.coerceIn(BASE_STAT, MAX_STAT) - BASE_STAT) / 2

  fun grantCore(state: GameState, amount: Int): GameState {
    if (amount <= 0) return normalize(state)
    val next = min(Int.MAX_VALUE.toLong(), coreCount(state).toLong() + amount.toLong()).toInt()
    return normalize(state.copy(metadata = sanitizeMetadata(state.metadata) + (CORE_KEY to next.toString())))
  }

  fun upgrade(state: GameState, rawCharacterId: String, rawStat: String): Upgrade {
    val normalized = normalize(state)
    val id = rawCharacterId.trim().lowercase(Locale.ROOT)
    require(id.isNotEmpty() && normalized.characters.containsKey(id)) { "Nhân vật không tồn tại." }
    val stat = normalizeStat(rawStat)
    require(stat in STAT_KEYS) { "Chỉ có thể nâng STR, DEX, AGI, VIT hoặc EVA." }
    val character = normalized.characters.getValue(id)
    val current = baseStat(normalized, id, stat)
    check(current < MAX_STAT) { "Chỉ số đã đạt giới hạn." }
    val cost = upgradeCost(current)
    val core = coreCount(normalized)
    check(core >= cost) { "Không đủ Core. Cần $cost Core." }

    val oldMax = CharacterStatCore.effective(normalized, id).maxHp
    val p = character.statProfile
    val nextProfile = when (stat) {
      "STR" -> p.copy(str = current + 1)
      "DEX" -> p.copy(dex = current + 1)
      "AGI" -> p.copy(agi = current + 1)
      "VIT" -> p.copy(vit = current + 1)
      else -> p.copy(eva = current + 1)
    }
    var next = normalized.copy(
      characters = normalized.characters + (id to character.copy(statProfile = nextProfile)),
      metadata = sanitizeMetadata(normalized.metadata) + (CORE_KEY to (core - cost).toString())
    )
    if (stat == "VIT" && character.vitalState.currentHp > 0) {
      val newMax = CharacterStatCore.effective(next, id).maxHp
      next = setCurrentHp(next, id, character.vitalState.currentHp + max(0, newMax - oldMax))
    }
    return Upgrade(next, id, stat, current + 1, cost, core - cost)
  }

  fun normalize(input: GameState): GameState {
    val currentCore = coreCount(input)
    var state = input.copy(metadata = sanitizeMetadata(input.metadata) + (CORE_KEY to currentCore.toString()))
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
      val provisional = state.copy(characters = state.characters + (id to c.copy(statProfile = canonical)))
      val maxHp = CharacterStatCore.effective(provisional, id).maxHp
      val hp = c.vitalState.currentHp.coerceIn(0, maxHp)
      chars[id] = c.copy(
        statProfile = canonical,
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
      chars[id] = c.copy(statProfile = a.statProfile, vitalState = a.vitalState)
    }
    return cand.copy(
      characters = chars,
      metadata = sanitizeMetadata(cand.metadata) + (CORE_KEY to coreCount(auth).toString())
    )
  }

  fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> =
    metadata.filterKeys { key ->
      !key.startsWith("poker.core.") &&
      !key.startsWith("characterRpg") &&
      !key.startsWith("baseStats") &&
      !key.startsWith("explorer") &&
      key != "level" && key != "exp" && key != "EXP"
    }

  fun normalizeStat(raw: String): String {
    val s = raw.trim().uppercase(Locale.ROOT)
    return if (s in STAT_KEYS) s else ""
  }

  private fun setCurrentHp(state: GameState, id: String, hp: Int): GameState {
    val c = state.characters[id] ?: return state
    val maxHp = CharacterStatCore.effective(state, id).maxHp
    val value = hp.coerceIn(0, maxHp)
    return state.copy(characters = state.characters + (id to c.copy(
      vitalState = c.vitalState.copy(
        currentHp = value,
        condition = CharacterStatEngine.conditionFor(value, maxHp, c.vitalState.condition, c.presence)
      )
    )))
  }
}

object CharacterStatCore {
  const val DAI_DAO_MA_TON_STAT_BONUS_PERCENT = 10
  const val CRITICAL_DAMAGE_PERCENT = 400
  const val BASIS_POINTS_PER_PERCENT = 100

  fun isCaoMinh(id: String): Boolean = id.equals(KAI_ID, true) || id.equals("cao_minh", true)

  private fun passiveBonus(base: Int, id: String): Int =
    if (isCaoMinh(id)) (base.coerceIn(CharacterProgressionCore.BASE_STAT, CharacterProgressionCore.MAX_STAT) *
      DAI_DAO_MA_TON_STAT_BONUS_PERCENT + 50) / 100 else 0

  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    fun value(raw: Int): Int = (raw.coerceIn(5, 999) + passiveBonus(raw, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val str = value(p.str)
    val dex = value(p.dex)
    val agi = value(p.agi)
    val vit = value(p.vit)
    val eva = value(p.eva)
    val def = defense(vit)
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
    val c = state.characters[KAI_ID] ?: return state
    if (c.presence == CharacterPresence.DEAD || c.vitalState.currentHp <= 0) return state
    val maxHp = CharacterStatCore.effective(state, KAI_ID).maxHp
    val heal = max(1, (maxHp * HEAL_PERCENT + 50) / 100)
    val hp = min(maxHp, c.vitalState.currentHp + heal)
    val nextStack = stacks(state) + 1
    return state.copy(
      characters = state.characters + (KAI_ID to c.copy(
        vitalState = c.vitalState.copy(currentHp = hp, condition = CharacterStatEngine.conditionFor(hp, maxHp, c.vitalState.condition, c.presence))
      )),
      metadata = state.metadata + (STACK_KEY to nextStack.toString())
    )
  }
}
