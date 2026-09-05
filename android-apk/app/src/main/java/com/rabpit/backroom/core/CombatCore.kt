package com.rabpit.backroom.core

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Deterministic combat foundation for the automatic rotation system.
 *
 * v1 intentionally has no skills. Every actor falls back to Basic Attack, while the data model
 * already exposes status/effect and timeline primitives for the later skill layer.
 */
enum class CombatEffectType { BLEED, STUN, POISON }

enum class CombatOutcome { VICTORY, DEFEAT, NO_ENCOUNTER, SAFETY_ABORT }

data class CombatEffect(
  val type: CombatEffectType,
  val remainingTurns: Int
)

data class CombatStats(
  val hpStat: Int = CombatRules.BASE_STAT,
  val defend: Int = CombatRules.BASE_STAT,
  val agi: Int = CombatRules.BASE_STAT,
  val crit: Int = CombatRules.BASE_STAT,
  val currentHp: Int = CombatRules.BASE_HP,
  val survival: Int = 0,
  val survivalTarget: Int = CombatRules.FIRST_SURVIVAL_TARGET
) {
  val maxHp: Int get() = CombatRules.maxHp(hpStat)
  val defensePoints: Int get() = CombatRules.defensePoints(defend)
  val evasionChance: Double get() = CombatRules.evasionChance(agi)
  val criticalChance: Double get() = CombatRules.criticalChance(crit)
}

data class CombatantState(
  val id: String,
  val name: String,
  val isEntity: Boolean,
  val stats: CombatStats,
  val baseDamage: Int,
  val effects: Map<CombatEffectType, CombatEffect> = emptyMap()
)

data class CombatTimelineEvent(
  val kind: String,
  val actorId: String? = null,
  val targetId: String? = null,
  val enemyId: String? = null,
  val text: String
)

data class CombatResolution(
  val encounterId: String,
  val outcome: CombatOutcome,
  val level: Int,
  val entityQueue: List<String>,
  val party: List<CombatantState>,
  val timeline: List<CombatTimelineEvent>,
  val defeatedEntities: List<String>
)

interface CombatRandom {
  fun nextDouble(): Double
}

class DefaultCombatRandom(private val random: Random = Random.Default) : CombatRandom {
  override fun nextDouble(): Double = random.nextDouble()
}

object CombatRules {
  const val BASE_STAT = 5
  const val BASE_HP = 50
  const val FIRST_SURVIVAL_TARGET = 10
  const val SURVIVAL_TARGET_STEP = 5
  const val BASE_EVASION = 0.10
  const val EVASION_PER_AGI_POINT = 0.001
  const val MAX_EVASION = 0.35
  const val BASE_CRIT = 0.05
  const val CRIT_PER_POINT = 0.005
  const val MAX_CRIT = 0.50
  const val CRIT_MULTIPLIER = 3
  const val DEFENSE_PER_POINT = 3
  const val BLEED_DAMAGE = 3
  const val BLEED_TURNS = 3
  const val POISON_TURNS = 3
  const val POISON_MAX_HP_PERCENT = 0.03
  const val STUN_TURNS = 1

  fun maxHp(hpStat: Int): Int = BASE_HP + (hpStat.coerceAtLeast(BASE_STAT) - BASE_STAT)

  fun defensePoints(defend: Int): Int = defend.coerceAtLeast(0) * DEFENSE_PER_POINT

  fun evasionChance(agi: Int): Double =
    (BASE_EVASION + (agi.coerceAtLeast(BASE_STAT) - BASE_STAT) * EVASION_PER_AGI_POINT)
      .coerceAtMost(MAX_EVASION)

  fun criticalChance(crit: Int): Double =
    (BASE_CRIT + (crit.coerceAtLeast(BASE_STAT) - BASE_STAT) * CRIT_PER_POINT)
      .coerceAtMost(MAX_CRIT)

  fun finalDamage(rawDamage: Int, defend: Int): Int {
    if (rawDamage <= 0) return 0
    val reduced = rawDamage * 100.0 / (100.0 + defensePoints(defend))
    return reduced.roundToInt().coerceAtLeast(1)
  }

  fun poisonDamage(maxHp: Int): Int = (maxHp * POISON_MAX_HP_PERCENT).roundToInt().coerceAtLeast(1)
}

object CombatProgression {
  private const val HP_KEY = "combat.hpStat"
  private const val DEFEND_KEY = "combat.defend"
  private const val AGI_KEY = "combat.agi"
  private const val CRIT_KEY = "combat.crit"
  private const val CURRENT_HP_KEY = "combat.currentHp"
  private const val SURVIVAL_KEY = "combat.survival"
  private const val SURVIVAL_TARGET_KEY = "combat.survivalTarget"

  fun read(character: CharacterState): CombatStats {
    val metadata = character.metadata
    val hpStat = metadata.intOrDefault(HP_KEY, CombatRules.BASE_STAT).coerceAtLeast(CombatRules.BASE_STAT)
    val defend = metadata.intOrDefault(DEFEND_KEY, CombatRules.BASE_STAT).coerceAtLeast(CombatRules.BASE_STAT)
    val agi = metadata.intOrDefault(AGI_KEY, CombatRules.BASE_STAT).coerceAtLeast(CombatRules.BASE_STAT)
    val crit = metadata.intOrDefault(CRIT_KEY, CombatRules.BASE_STAT).coerceAtLeast(CombatRules.BASE_STAT)
    val maxHp = CombatRules.maxHp(hpStat)
    return CombatStats(
      hpStat = hpStat,
      defend = defend,
      agi = agi,
      crit = crit,
      currentHp = metadata.intOrDefault(CURRENT_HP_KEY, maxHp).coerceIn(0, maxHp),
      survival = metadata.intOrDefault(SURVIVAL_KEY, 0).coerceAtLeast(0),
      survivalTarget = metadata.intOrDefault(SURVIVAL_TARGET_KEY, CombatRules.FIRST_SURVIVAL_TARGET)
        .coerceAtLeast(CombatRules.FIRST_SURVIVAL_TARGET)
    )
  }

  fun write(character: CharacterState, stats: CombatStats): CharacterState {
    val safe = stats.copy(currentHp = stats.currentHp.coerceIn(0, stats.maxHp))
    return character.copy(
      healthState = "${safe.currentHp}/${safe.maxHp}",
      metadata = character.metadata + mapOf(
        HP_KEY to safe.hpStat.toString(),
        DEFEND_KEY to safe.defend.toString(),
        AGI_KEY to safe.agi.toString(),
        CRIT_KEY to safe.crit.toString(),
        CURRENT_HP_KEY to safe.currentHp.toString(),
        SURVIVAL_KEY to safe.survival.toString(),
        SURVIVAL_TARGET_KEY to safe.survivalTarget.toString()
      )
    )
  }

  fun growthPerCompletion(characterId: String): Int = when (characterId.lowercase()) {
    "kai" -> 4
    "iris", "syvial" -> 3
    else -> 2
  }

  fun awardEntityKill(characterId: String, stats: CombatStats): CombatStats {
    var current = stats
    var progress = current.survival + 1
    var target = current.survivalTarget
    var hpStat = current.hpStat
    var defend = current.defend
    var agi = current.agi
    var crit = current.crit
    var currentHp = current.currentHp
    val growth = growthPerCompletion(characterId)

    while (progress >= target) {
      progress -= target
      target += CombatRules.SURVIVAL_TARGET_STEP
      hpStat += growth
      defend += growth
      agi += growth
      crit += growth
      currentHp += growth
    }

    val maxHp = CombatRules.maxHp(hpStat)
    return CombatStats(
      hpStat = hpStat,
      defend = defend,
      agi = agi,
      crit = crit,
      currentHp = currentHp.coerceAtMost(maxHp),
      survival = progress,
      survivalTarget = target
    )
  }

  private fun Map<String, String>.intOrDefault(key: String, fallback: Int): Int = this[key]?.toIntOrNull() ?: fallback
}

object CombatEffects {
  fun apply(
    effects: Map<CombatEffectType, CombatEffect>,
    type: CombatEffectType,
    durationTurns: Int
  ): Map<CombatEffectType, CombatEffect> {
    if (durationTurns <= 0) return effects
    val current = effects[type]?.remainingTurns ?: 0
    return effects + (type to CombatEffect(type, maxOf(current, durationTurns)))
  }

  fun consumeStun(effects: Map<CombatEffectType, CombatEffect>): Pair<Boolean, Map<CombatEffectType, CombatEffect>> {
    val stun = effects[CombatEffectType.STUN] ?: return false to effects
    val next = if (stun.remainingTurns <= 1) effects - CombatEffectType.STUN
    else effects + (CombatEffectType.STUN to stun.copy(remainingTurns = stun.remainingTurns - 1))
    return true to next
  }

  fun tickDamage(stats: CombatStats, effects: Map<CombatEffectType, CombatEffect>): Pair<CombatStats, Map<CombatEffectType, CombatEffect>> {
    var hp = stats.currentHp
    var next = effects
    effects[CombatEffectType.BLEED]?.let { bleed ->
      hp = (hp - CombatRules.BLEED_DAMAGE).coerceAtLeast(0)
      next = decrement(next, bleed)
    }
    effects[CombatEffectType.POISON]?.let { poison ->
      hp = (hp - CombatRules.poisonDamage(stats.maxHp)).coerceAtLeast(0)
      next = decrement(next, poison)
    }
    return stats.copy(currentHp = hp) to next
  }

  private fun decrement(
    effects: Map<CombatEffectType, CombatEffect>,
    effect: CombatEffect
  ): Map<CombatEffectType, CombatEffect> = if (effect.remainingTurns <= 1) {
    effects - effect.type
  } else {
    effects + (effect.type to effect.copy(remainingTurns = effect.remainingTurns - 1))
  }
}

object CombatProfiles {
  private val entityNames = mapOf(
    "ENTITY.HOUND" to "Hound",
    "ENTITY.CLUMP" to "Clump",
    "ENTITY.DULLER" to "Duller",
    "ENTITY.DEATHMOTH" to "Deathmoth",
    "ENTITY.HOSTILE_FACELING" to "Hostile Faceling",
    "ENTITY.FALSE_PUDDLE" to "False Puddle",
    "ENTITY.PAINTINGS" to "Paintings",
    "ENTITY.SMILER" to "Smiler",
    "ENTITY.SKIN_STEALER" to "Skin-Stealer",
    "ENTITY.PREDATORY_WINDOW" to "Predatory Window",
    "ENTITY.BIOLOGICAL_PIPELINE" to "Biological Pipeline",
    "ENTITY.WRETCH" to "Wretch",
    "ENTITY.CABLE_MIMIC" to "Cable Mimic",
    "ENTITY.BEAST_LEVEL_5" to "The Beast of Level 5",
    "ENTITY.HOTEL_CORPSE_LURE" to "Hotel Corpse Lure",
    "ENTITY.JEFF" to "Jeff the Killer",
    "ENTITY.JANE" to "Jane the Killer",
    "ENTITY.SLENDERMAN" to "Slenderman"
  )

  fun partyBaseDamage(characterId: String): Int = when (characterId.lowercase()) {
    "kai" -> 12
    "syvial" -> 13
    "iris" -> 11
    "lucia" -> 8
    else -> 7
  }

  fun entityName(entityId: String): String = entityNames[entityId] ?: entityId.substringAfter("ENTITY.")

  fun enemyGrowth(kaiStats: CombatStats, level: Int): Int {
    val survivalGrowth = (kaiStats.hpStat - CombatRules.BASE_STAT).coerceAtLeast(0)
    return floor(survivalGrowth * 0.5).toInt() + level.coerceAtLeast(0)
  }

  fun entity(entityId: String, kaiStats: CombatStats, level: Int): CombatantState {
    val growth = enemyGrowth(kaiStats, level)
    val stat = CombatRules.BASE_STAT + growth
    val hp = CombatRules.maxHp(stat)
    return CombatantState(
      id = entityId,
      name = entityName(entityId),
      isEntity = true,
      stats = CombatStats(
        hpStat = stat,
        defend = stat,
        agi = stat,
        crit = stat,
        currentHp = hp
      ),
      baseDamage = 8 + growth / 2
    )
  }
}

class AutoTurnCombatEngine(private val random: CombatRandom = DefaultCombatRandom()) {
  private data class MutableFighter(
    val id: String,
    val name: String,
    val isEntity: Boolean,
    var stats: CombatStats,
    val baseDamage: Int,
    var effects: Map<CombatEffectType, CombatEffect>
  ) {
    fun snapshot() = CombatantState(id, name, isEntity, stats, baseDamage, effects)
    fun alive(): Boolean = stats.currentHp > 0
  }

  fun resolve(
    encounterId: String,
    partyInput: List<CombatantState>,
    entityIds: List<String>,
    level: Int
  ): CombatResolution {
    val queue = entityIds.distinct().filter { it.isNotBlank() }
    if (queue.isEmpty()) {
      return CombatResolution(encounterId, CombatOutcome.NO_ENCOUNTER, level, emptyList(), partyInput, emptyList(), emptyList())
    }

    val orderedParty = partyInput
      .filterNot { it.isEntity }
      .sortedWith(compareBy<CombatantState> { if (it.id.equals(KAI_ID, true)) 0 else 1 }.thenBy { partyInput.indexOf(it) })
      .map { MutableFighter(it.id, it.name, false, it.stats, it.baseDamage, it.effects) }
      .toMutableList()
    val kai = orderedParty.firstOrNull { it.id.equals(KAI_ID, true) }
      ?: return CombatResolution(encounterId, CombatOutcome.DEFEAT, level, queue, partyInput, emptyList(), emptyList())

    val timeline = mutableListOf<CombatTimelineEvent>()
    val defeated = mutableListOf<String>()
    var enemyIndex = 0
    var enemy = CombatProfiles.entity(queue[enemyIndex], kai.stats, level).toMutable()
    var partyCursor = 0
    var actions = 0

    timeline += CombatTimelineEvent("ENTITY_ENTER", enemyId = enemy.id, text = "${enemy.name} bước vào chiến đấu.")

    while (enemyIndex < queue.size && orderedParty.any { it.alive() } && actions < 10_000) {
      val actorIndex = nextLivingPartyIndex(orderedParty, partyCursor) ?: break
      val actor = orderedParty[actorIndex]
      timeline += CombatTimelineEvent("FOCUS", actorId = actor.id, enemyId = enemy.id, text = actor.name)

      val (stunned, actorEffects) = CombatEffects.consumeStun(actor.effects)
      actor.effects = actorEffects
      if (stunned) {
        timeline += CombatTimelineEvent("STATUS", actorId = actor.id, enemyId = enemy.id, text = "${actor.name} đang [Choáng] → bỏ lượt.")
      } else {
        attack(actor, enemy, timeline)
      }
      tickDots(actor, timeline, enemy.id)
      actions++

      if (!enemy.alive()) {
        rewardKill(orderedParty)
        defeated += enemy.id
        timeline += CombatTimelineEvent("ENTITY_DOWN", actorId = actor.id, enemyId = enemy.id, text = "${enemy.name} bị tiêu diệt.")
        enemyIndex++
        partyCursor = (actorIndex + 1) % orderedParty.size
        if (enemyIndex >= queue.size) break
        enemy = CombatProfiles.entity(queue[enemyIndex], orderedParty.first { it.id.equals(KAI_ID, true) }.stats, level).toMutable()
        timeline += CombatTimelineEvent("ENTITY_ENTER", enemyId = enemy.id, text = "${enemy.name} bước vào chiến đấu.")
        continue
      }

      if (actor.alive()) {
        val (enemyStunned, enemyEffects) = CombatEffects.consumeStun(enemy.effects)
        enemy.effects = enemyEffects
        if (enemyStunned) {
          timeline += CombatTimelineEvent("STATUS", actorId = enemy.id, targetId = actor.id, enemyId = enemy.id, text = "${enemy.name} đang [Choáng] → bỏ lượt.")
        } else {
          attack(enemy, actor, timeline)
        }
        tickDots(enemy, timeline, enemy.id)
        actions++
      }

      if (!enemy.alive()) {
        rewardKill(orderedParty)
        defeated += enemy.id
        timeline += CombatTimelineEvent("ENTITY_DOWN", actorId = actor.id, enemyId = enemy.id, text = "${enemy.name} bị tiêu diệt.")
        enemyIndex++
        if (enemyIndex < queue.size) {
          enemy = CombatProfiles.entity(queue[enemyIndex], orderedParty.first { it.id.equals(KAI_ID, true) }.stats, level).toMutable()
          timeline += CombatTimelineEvent("ENTITY_ENTER", enemyId = enemy.id, text = "${enemy.name} bước vào chiến đấu.")
        }
      }

      partyCursor = (actorIndex + 1) % orderedParty.size
    }

    val outcome = when {
      actions >= 10_000 -> CombatOutcome.SAFETY_ABORT
      enemyIndex >= queue.size -> CombatOutcome.VICTORY
      orderedParty.none { it.alive() } -> CombatOutcome.DEFEAT
      else -> CombatOutcome.DEFEAT
    }
    timeline += CombatTimelineEvent(
      "COMBAT_END",
      text = when (outcome) {
        CombatOutcome.VICTORY -> "Toàn bộ Entity trong lượt chạm trán đã bị tiêu diệt."
        CombatOutcome.SAFETY_ABORT -> "Combat dừng bởi giới hạn an toàn của engine."
        CombatOutcome.NO_ENCOUNTER -> "Không có Entity để chiến đấu."
        CombatOutcome.DEFEAT -> "Party không còn thành viên có thể tiếp tục chiến đấu."
      }
    )

    return CombatResolution(encounterId, outcome, level, queue, orderedParty.map { it.snapshot() }, timeline, defeated)
  }

  private fun CombatantState.toMutable() = MutableFighter(id, name, isEntity, stats, baseDamage, effects)

  private fun nextLivingPartyIndex(party: List<MutableFighter>, start: Int): Int? {
    if (party.isEmpty()) return null
    repeat(party.size) { offset ->
      val index = (start + offset) % party.size
      if (party[index].alive()) return index
    }
    return null
  }

  private fun attack(attacker: MutableFighter, target: MutableFighter, timeline: MutableList<CombatTimelineEvent>) {
    if (!attacker.alive() || !target.alive()) return
    if (random.nextDouble() < target.stats.evasionChance) {
      timeline += CombatTimelineEvent(
        "EVADE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = if (attacker.isEntity) attacker.id else if (target.isEntity) target.id else null,
        text = "${attacker.name} dùng [Tấn công] lên ${target.name} → ${target.name} né tránh thành công."
      )
      return
    }

    val critical = random.nextDouble() < attacker.stats.criticalChance
    val raw = attacker.baseDamage * if (critical) CombatRules.CRIT_MULTIPLIER else 1
    val damage = CombatRules.finalDamage(raw, target.stats.defend)
    target.stats = target.stats.copy(currentHp = (target.stats.currentHp - damage).coerceAtLeast(0))
    val criticalText = if (critical) " CRITICAL!" else ""
    timeline += CombatTimelineEvent(
      "ATTACK",
      actorId = attacker.id,
      targetId = target.id,
      enemyId = if (attacker.isEntity) attacker.id else if (target.isEntity) target.id else null,
      text = "${attacker.name} dùng [Tấn công] lên ${target.name} →$criticalText ${target.name} -$damage HP."
    )
  }

  private fun tickDots(fighter: MutableFighter, timeline: MutableList<CombatTimelineEvent>, enemyId: String?) {
    if (!fighter.alive()) return
    val before = fighter.stats.currentHp
    val hadBleed = fighter.effects.containsKey(CombatEffectType.BLEED)
    val hadPoison = fighter.effects.containsKey(CombatEffectType.POISON)
    val (stats, effects) = CombatEffects.tickDamage(fighter.stats, fighter.effects)
    fighter.stats = stats
    fighter.effects = effects
    if (hadBleed) {
      val damage = minOf(CombatRules.BLEED_DAMAGE, before)
      timeline += CombatTimelineEvent("STATUS", actorId = fighter.id, enemyId = enemyId, text = "${fighter.name} chịu [Chảy máu] → -$damage HP.")
    }
    if (hadPoison && fighter.stats.currentHp < before) {
      val poison = minOf(CombatRules.poisonDamage(fighter.stats.maxHp), maxOf(0, before - (if (hadBleed) CombatRules.BLEED_DAMAGE else 0)))
      timeline += CombatTimelineEvent("STATUS", actorId = fighter.id, enemyId = enemyId, text = "${fighter.name} chịu [Trúng Độc] → -$poison HP.")
    }
  }

  private fun rewardKill(party: List<MutableFighter>) {
    party.filter { it.alive() }.forEach { fighter ->
      fighter.stats = CombatProgression.awardEntityKill(fighter.id, fighter.stats)
    }
  }
}
