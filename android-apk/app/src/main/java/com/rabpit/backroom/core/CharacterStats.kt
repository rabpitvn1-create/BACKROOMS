package com.rabpit.backroom.core

enum class StatSource { GAMEPLAY_NORMALIZED, GAMEPLAY_FALLBACK }
enum class EnergyMode { INFINITE, FINITE, NOT_APPLICABLE }
enum class CharacterCondition { HEALTHY, HURT, WOUNDED, CRITICAL, DEFEATED, DEAD }

data class EnergyProfile(val mode: EnergyMode = EnergyMode.NOT_APPLICABLE, val max: Int? = null) {
  companion object {
    fun infinite() = EnergyProfile(EnergyMode.INFINITE, null)
    fun finite(max: Int) = EnergyProfile(EnergyMode.FINITE, max.coerceAtLeast(0))
    fun notApplicable() = EnergyProfile(EnergyMode.NOT_APPLICABLE, null)
  }
}

data class HpRegenRule(
  val amountPerCompletedTurn: Int = 0,
  val sourceId: String? = null,
  val enabled: Boolean = false
)

data class CharacterStatProfile(
  val baseMaxHp: Int = CharacterProgressionCore.BASE_MAX_HP,
  val str: Int = CharacterProgressionCore.BASE_STAT,
  val def: Int = CharacterProgressionCore.BASE_STAT,
  val skl: Int = CharacterProgressionCore.BASE_STAT,
  val vit: Int = CharacterProgressionCore.BASE_STAT,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regen: HpRegenRule = HpRegenRule(),
  val combatRole: String = "UNSPECIFIED",
  val statSource: StatSource = StatSource.GAMEPLAY_NORMALIZED,
  val schema: String = CharacterProgressionCore.SCHEMA
) {
}

data class CharacterVitalState(
  val currentHp: Int = CharacterProgressionCore.BASE_MAX_HP,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null
)

data class EffectiveCharacterStats(
  val maxHp: Int,
  val str: Int,
  val def: Int,
  val skl: Int,
  val vit: Int,
  val criticalChancePercent: Int,
  val evasionPercent: Int,
  val resCriticalPercent: Int,
  val resEvasionPercent: Int,
  val criticalDamagePercent: Int = CharacterStatCore.CRITICAL_DAMAGE_PERCENT,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regenPerCompletedTurn: Int = 0
) {
}

object CharacterStatProfiles {
  private fun role(id: String): String = when (id.trim().lowercase()) {
    "cao_minh", "kai" -> "VẠN GIỚI MA TÔN / MA ĐẠO KIẾM TU"
    "iris" -> "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN"
    "syvial" -> "HIGH-SPEED SWORDSMAN / ASSAULT / COUNTER / EXECUTION"
    "lucia" -> "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER"
    "an-nhien", "an_nhien", "annhien" -> "PROTECTED FOLLOWER / NON-COMBAT"
    else -> "UNSPECIFIED"
  }

  private fun energy(id: String): EnergyProfile = when (id.trim().lowercase()) {
    "cao_minh", "kai", "iris", "syvial" -> EnergyProfile.infinite()
    else -> EnergyProfile.notApplicable()
  }

  fun forId(characterId: String): CharacterStatProfile =
    CharacterStatProfile(combatRole = role(characterId), energy = energy(characterId))

  fun initialVitals(characterId: String): CharacterVitalState =
    CharacterVitalState(currentHp = CharacterProgressionCore.BASE_MAX_HP)
}
