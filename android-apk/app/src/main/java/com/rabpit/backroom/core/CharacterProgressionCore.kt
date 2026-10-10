package com.rabpit.backroom.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CharacterProgressionCore {
  const val SCHEMA = "core_stats_v1"
  const val BASE_STAT = 5
  const val MAX_STAT = 999
  const val BASE_MAX_HP = 50
  const val CORE_KEY = "characterProgression.coreResource"
  private const val LEGACY_CORE_KEY = "poker.core.count"
  private val STAT_KEYS = setOf("STR", "DEF", "SKL", "VIT")

  data class Upgrade(
    val state: GameState,
    val characterId: String,
    val stat: String,
    val value: Int,
    val cost: Int,
    val coreRemaining: Int
  )

  fun coreCount(state: GameState): Int =
    (state.metadata[CORE_KEY] ?: state.metadata[LEGACY_CORE_KEY])
      ?.toIntOrNull()?.coerceAtLeast(0) ?: 0

  fun baseStat(state: GameState, characterId: String, rawStat: String): Int {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    return when (normalizeStat(rawStat)) {
      "STR" -> p.str
      "DEF" -> p.def
      "SKL" -> p.skl
      "VIT" -> p.vit
      else -> BASE_STAT
    }.coerceIn(BASE_STAT, MAX_STAT)
  }

  fun statPercent(stat: Int): Int = 100 + 10 * (stat.coerceIn(BASE_STAT, MAX_STAT) - BASE_STAT)
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
    require(stat in STAT_KEYS) { "Chỉ có thể nâng STR, DEF, SKL hoặc VIT." }
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
      "DEF" -> p.copy(def = current + 1)
      "SKL" -> p.copy(skl = current + 1)
      else -> p.copy(vit = current + 1)
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
    val migratedCore = coreCount(input)
    var state = input.copy(metadata = sanitizeMetadata(input.metadata) + (CORE_KEY to migratedCore.toString()))
    val chars = state.characters.toMutableMap()
    state.characters.forEach { (id, c) ->
      val raw = c.statProfile
      val canonical = if (raw.schema == SCHEMA) {
        raw.copy(
          baseMaxHp = BASE_MAX_HP,
          str = raw.str.coerceIn(BASE_STAT, MAX_STAT),
          def = raw.def.coerceIn(BASE_STAT, MAX_STAT),
          skl = raw.skl.coerceIn(BASE_STAT, MAX_STAT),
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
  const val CRITICAL_DAMAGE_PERCENT = 150

  fun isCaoMinh(id: String): Boolean = id.equals(KAI_ID, true) || id.equals("cao_minh", true)

  private fun passiveBonus(base: Int, id: String): Int =
    if (isCaoMinh(id)) (base.coerceIn(CharacterProgressionCore.BASE_STAT, CharacterProgressionCore.MAX_STAT) *
      DAI_DAO_MA_TON_STAT_BONUS_PERCENT + 50) / 100 else 0

  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    val str = (p.str.coerceIn(5, 999) + passiveBonus(p.str, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val def = (p.def.coerceIn(5, 999) + passiveBonus(p.def, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val skl = (p.skl.coerceIn(5, 999) + passiveBonus(p.skl, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val vit = (p.vit.coerceIn(5, 999) + passiveBonus(p.vit, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val maxHp = scaleByPercent(CharacterProgressionCore.BASE_MAX_HP, CharacterProgressionCore.statPercent(vit))
    return EffectiveCharacterStats(
      maxHp = maxHp,
      str = str, def = def, skl = skl, vit = vit,
      criticalChancePercent = criticalChance(skl),
      evasionPercent = evasion(vit),
      resCriticalPercent = criticalResistance(def),
      resEvasionPercent = evasionResistance(skl),
      criticalDamagePercent = CRITICAL_DAMAGE_PERCENT,
      energy = p.energy,
      regenPerCompletedTurn = 0
    )
  }

  fun criticalChance(skl: Int): Int = (5 + (skl.coerceAtLeast(5) - 5) * 2).coerceAtMost(50)
  fun evasion(vit: Int): Int = ((vit.coerceAtLeast(5) - 5) * 2).coerceAtMost(35)
  fun criticalResistance(def: Int): Int = ((def.coerceAtLeast(5) - 5) * 2).coerceAtMost(50)
  fun evasionResistance(skl: Int): Int = ((skl.coerceAtLeast(5) - 5) * 2).coerceAtMost(50)

  fun scaleByPercent(value: Int, percent: Int): Int {
    val scaled = max(0L, value.toLong()) * max(0, percent).toLong()
    return min(Int.MAX_VALUE.toLong(), (scaled + 50L) / 100L).toInt()
  }

  fun basicDamage(baseDamage: Int, str: Int, handPercent: Int = 100, attackPercent: Int = 100): Int {
    var out = scaleByPercent(baseDamage, CharacterProgressionCore.statPercent(str))
    out = scaleByPercent(out, handPercent)
    return scaleByPercent(out, attackPercent)
  }

  fun skillDamage(baseDamage: Int, skillPercent: Int, skl: Int, handPercent: Int = 100): Int {
    var out = scaleByPercent(baseDamage, skillPercent)
    out = scaleByPercent(out, CharacterProgressionCore.statPercent(skl))
    return scaleByPercent(out, handPercent)
  }

  fun defendedIncomingDamage(rawDamage: Int, def: Int): Int {
    if (rawDamage <= 0) return 0
    val percent = CharacterProgressionCore.statPercent(def).coerceAtLeast(1)
    return max(1, ((rawDamage.toLong() * 100L + percent / 2L) / percent).toInt())
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
