package com.rabpit.backroom.core

/**
 * Gameplay-normalized character numbers. These values are balance data, not canon claims.
 * Character Codex remains authoritative for identity, role, abilities, and equipment behavior.
 */
enum class StatSource { GAMEPLAY_NORMALIZED, GAMEPLAY_FALLBACK }
enum class EnergyMode { INFINITE, FINITE, NOT_APPLICABLE }
enum class CharacterCondition { HEALTHY, HURT, WOUNDED, CRITICAL, DEFEATED, DEAD }

data class EnergyProfile(
  val mode: EnergyMode = EnergyMode.NOT_APPLICABLE,
  val max: Int? = null
) {
  companion object {
    fun infinite() = EnergyProfile(EnergyMode.INFINITE, null)
    fun finite(max: Int) = EnergyProfile(EnergyMode.FINITE, max.coerceAtLeast(0))
    fun notApplicable() = EnergyProfile(EnergyMode.NOT_APPLICABLE, null)
  }
}

data class HpRegenRule(
  val amountPerCompletedTurn: Int = 0,
  val sourceId: String? = null,
  val enabled: Boolean = false,
  val intervalCompletedTurns: Int = 1
)

data class CharacterStatProfile(
  val baseMaxHp: Int = 100,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regen: HpRegenRule = HpRegenRule(),
  val str: Int = 10,
  val df: Int = 10,
  val agi: Int = 10,
  val crit: Int = 10,
  val combatRole: String = "UNSPECIFIED",
  val statSource: StatSource = StatSource.GAMEPLAY_FALLBACK
)

data class CharacterVitalState(
  val currentHp: Int = 100,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null,
  val completedTurnsSinceRegen: Int = 0
)

/** Derived-only view. Equipment integration comes in the later equipment step. */
data class EffectiveCharacterStats(
  val maxHp: Int,
  val equipmentHp: Int = 0,
  val str: Int,
  val df: Int,
  val agi: Int,
  val crit: Int,
  val energy: EnergyProfile,
  val regenPerCompletedTurn: Int
)

object CharacterStatProfiles {
  private val kai = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.infinite(),
    regen = HpRegenRule(4, "kai:passive-regeneration", true),
    str = 82,
    df = 78,
    agi = 92,
    crit = 95,
    combatRole = "COMMANDER / SUPREME MARKSMAN / HIGH-MOBILITY COMBATANT",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val iris = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.infinite(),
    regen = HpRegenRule(4, "iris:passive-regeneration", true),
    str = 58,
    df = 60,
    agi = 84,
    crit = 90,
    combatRole = "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val syvial = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.infinite(),
    regen = HpRegenRule(4, "syvial:passive-regeneration", true),
    str = 94,
    df = 84,
    agi = 96,
    crit = 88,
    combatRole = "HIGH-SPEED SWORDSMAN / ASSAULT / COUNTER / EXECUTION",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val lucia = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.notApplicable(),
    regen = HpRegenRule(2, "lucia:three-turn-regeneration", true, 3),
    str = 7,
    df = 7,
    agi = 8,
    crit = 7,
    combatRole = "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val anNhien = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.notApplicable(),
    regen = HpRegenRule(),
    str = 10,
    df = 10,
    agi = 10,
    crit = 0,
    combatRole = "PROTECTED FOLLOWER / NON-COMBAT",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val fallback = CharacterStatProfile()

  fun forId(characterId: String): CharacterStatProfile = when (characterId.trim().lowercase()) {
    "kai" -> kai
    "iris" -> iris
    "syvial" -> syvial
    "an-nhien", "an_nhien", "annhien" -> anNhien
    "lucia", "luc", "lucia-luc" -> lucia
    else -> fallback
  }

  fun initialVitals(characterId: String): CharacterVitalState =
    CharacterVitalState(currentHp = forId(characterId).baseMaxHp)
}
