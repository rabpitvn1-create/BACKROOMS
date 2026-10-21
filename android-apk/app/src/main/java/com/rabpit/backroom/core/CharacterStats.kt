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

/** Per-character progression; EXP is progress toward the next level, not lifetime EXP. */
data class CharacterProgression(val level: Int = 0, val exp: Int = 0)

data class CharacterStatProfile(
  val baseMaxHp: Int = CharacterProgressionCore.BASE_MAX_HP,
  val str: Int = CharacterProgressionCore.BASE_STAT,
  val dex: Int = CharacterProgressionCore.BASE_STAT,
  val agi: Int = CharacterProgressionCore.BASE_STAT,
  val vit: Int = CharacterProgressionCore.BASE_STAT,
  val eva: Int = CharacterProgressionCore.BASE_STAT,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regen: HpRegenRule = HpRegenRule(),
  val combatRole: String = "UNSPECIFIED",
  val statSource: StatSource = StatSource.GAMEPLAY_NORMALIZED,
  val schema: String = CharacterProgressionCore.SCHEMA
)

data class CharacterVitalState(
  val currentHp: Int = CharacterProgressionCore.BASE_MAX_HP,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null
)

data class EffectiveCharacterStats(
  val maxHp: Int,
  val str: Int,
  val dex: Int,
  val agi: Int,
  val vit: Int,
  val eva: Int,
  val def: Int,
  val criticalChancePercent: Int,
  val evasionPercent: Int,
  val resCriticalPercent: Int,
  val resEvasionPercent: Int,
  val criticalDamagePercent: Int = CharacterStatCore.CRITICAL_DAMAGE_PERCENT,
  // Basis points retain 0.01% precision for fractional per-stat bonuses.
  val accuracyBasisPoints: Int = 5800,
  val criticalChanceBasisPoints: Int = 0,
  val evasionBasisPoints: Int = 0,
  val resCriticalBasisPoints: Int = 0,
  val resEvasionBasisPoints: Int = 0,
  val attackPercent: Int = 100,
  val skillDamagePercent: Int = 100,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regenPerCompletedTurn: Int = 0
)

object CharacterStatProfiles {
  private fun role(id: String): String = when (id.trim().lowercase()) {
    "cao_minh" -> "VẠN GIỚI MA TÔN / MA ĐẠO KIẾM TU"
    "iris" -> "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN"
    "syvial" -> "HIGH-SPEED SWORDSMAN / ASSAULT / COUNTER / EXECUTION"
    "lucia" -> "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER"
    LUC_TRAM_ID -> "THIÊN KIẾM MÔN / CHÍNH ĐẠO KIẾM TU"
    CATHERINE_ID -> "VICTORIAN GUNSLINGER / OCCULT MARKSMAN / COUNTERSHOT"
    JANET_ID -> "MELEE BRAWLER / BAYOU BATTER / PROTECTIVE FOLLOWER"
    NGUYEN_QUOC_NAM_ID -> "MEDICAL LECTURER / DEFENSIVE MARKSMAN / FIELD FIRST AID"
    HUA_TIEU_LAN_ID -> "SAIGON CHOLON RIFLE MARKSWOMAN / COVER / COUNTERSHOT"
    TRAC_LAM_ID -> "SRU-03 CAPTAIN / TACTICAL MARKSMAN / ANOMALY RESPONSE"
    "an-nhien", "an_nhien", "annhien" -> "PROTECTED FOLLOWER / NON-COMBAT"
    else -> "UNSPECIFIED"
  }

  // Starting-only balance for the four recently introduced mortal Followers.
  // The other characters retain their shared 5-stat / 50-HP baseline.
  private val lowStatFollowers = mapOf(
    CATHERINE_ID to CharacterStatProfile(
      baseMaxHp = 26, str = 1, dex = 2, agi = 2, vit = 1, eva = 2,
      combatRole = role(CATHERINE_ID)
    ),
    JANET_ID to CharacterStatProfile(
      baseMaxHp = 30, str = 2, dex = 1, agi = 2, vit = 2, eva = 1,
      combatRole = role(JANET_ID)
    ),
    NGUYEN_QUOC_NAM_ID to CharacterStatProfile(
      baseMaxHp = 28, str = 1, dex = 2, agi = 1, vit = 2, eva = 1,
      combatRole = role(NGUYEN_QUOC_NAM_ID)
    ),
    HUA_TIEU_LAN_ID to CharacterStatProfile(
      baseMaxHp = 25, str = 1, dex = 2, agi = 1, vit = 1, eva = 2,
      combatRole = role(HUA_TIEU_LAN_ID)
    ),
    TRAC_LAM_ID to CharacterStatProfile(
      baseMaxHp = 30, str = 1, dex = 2, agi = 2, vit = 2, eva = 1,
      combatRole = role(TRAC_LAM_ID)
    )
  )

  fun followerStartingBalance(characterId: String): CharacterStatProfile? =
    lowStatFollowers[characterId.trim().lowercase()]

  fun minimumStat(characterId: String): Int =
    if (followerStartingBalance(characterId) != null) 1 else CharacterProgressionCore.BASE_STAT

  /**
   * Rebalance the four Followers already present in a CURRENT-version run.
   * Their previously persisted baseline was 5 per stat / 50 HP even at Level 0.
   * Retain earned stat points, EXP, encounter history and the fraction of HP remaining.
   * baseMaxHp acts as an idempotent marker; new runs already have the new baseline.
   * This does not accept or migrate an obsolete save schema.
   */
  fun rebalanceCurrentFollowers(input: GameState): GameState {
    var state = input
    for ((id, seed) in lowStatFollowers) {
      val actor = state.characters[id] ?: continue
      val old = actor.statProfile
      if (old.schema != CharacterProgressionCore.SCHEMA ||
          old.baseMaxHp != CharacterProgressionCore.BASE_MAX_HP) continue

      fun adjusted(initial: Int, previous: Int): Int =
        (initial.toLong() + (previous.toLong() - CharacterProgressionCore.BASE_STAT).coerceAtLeast(0L))
          .coerceAtMost(CharacterProgressionCore.MAX_STAT.toLong()).toInt()

      val oldMaxHp = CharacterStatCore.maxHp(CharacterStatCore.effective(state, id).vit)
        .coerceAtLeast(1)
      val nextProfile = old.copy(
        baseMaxHp = seed.baseMaxHp,
        str = adjusted(seed.str, old.str),
        dex = adjusted(seed.dex, old.dex),
        agi = adjusted(seed.agi, old.agi),
        vit = adjusted(seed.vit, old.vit),
        eva = adjusted(seed.eva, old.eva)
      )
      val updated = actor.copy(statProfile = nextProfile)
      val withProfile = state.copy(characters = state.characters + (id to updated))
      val newMaxHp = CharacterStatCore.effective(withProfile, id).maxHp
      val previousHp = actor.vitalState.currentHp.coerceIn(0, oldMaxHp)
      val nextHp = if (previousHp == 0) 0 else
        ((previousHp.toLong() * newMaxHp + oldMaxHp - 1) / oldMaxHp)
          .coerceIn(1L, newMaxHp.toLong()).toInt()
      state = withProfile.copy(characters = withProfile.characters + (id to updated.copy(
        vitalState = actor.vitalState.copy(
          currentHp = nextHp,
          condition = CharacterStatEngine.conditionFor(
            nextHp, newMaxHp, actor.vitalState.condition, actor.presence
          )
        )
      )))
    }
    return state
  }

  private fun energy(id: String): EnergyProfile = when (id.trim().lowercase()) {
    "cao_minh", "iris", "syvial" -> EnergyProfile.infinite()
    else -> EnergyProfile.notApplicable()
  }

  fun forId(characterId: String): CharacterStatProfile =
    followerStartingBalance(characterId) ?: CharacterStatProfile(
      combatRole = role(characterId), energy = energy(characterId)
    )

  fun initialVitals(characterId: String): CharacterVitalState =
    CharacterVitalState(currentHp = forId(characterId).baseMaxHp)
}
