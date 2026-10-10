package com.rabpit.backroom.core

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Authoritative, save-persistent combat state stored in GameState.metadata. */
object CombatRuntime {
  // LIFEFORM_TRIO_R02
  // ENTITY_POWER_SCALING_R01
  // Linear on the Level-0 base: full Levels are 100/110/120/130...%, never compounded.
  private const val PREFIX = "combat."
  private const val ENTITY_HP_BONUS = 30
  private const val ENTITY_EVASION_PERCENT = 25
  private const val ENTITY_REGEN_PER_TURN = 1
  private const val KAI_GUILTY_CROWN_INTERVAL_TURNS = 3
  private const val KAI_GUILTY_CROWN_SHOTS = 24
  private const val KAI_GUILTY_CROWN_ACCURACY_PERCENT = 200
  private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10
  private const val DIEP_MINH_KEY = "diep_minh"
  private const val DIEP_MINH_MAX_HP = 2999
  private const val DIEP_MINH_ATTACK_PERCENT = 10
  private const val DIEP_MINH_REGEN_PER_TURN = 30
  private const val DIEP_MINH_ULTIMATE_INTERVAL_TURNS = 5
  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
  private const val LIFEFORM_BASE_MAX_HP = 104
  private const val LIFEFORM_BASE_ATTACK = 20
  private const val LIFEFORM_BASE_ARMOR = 3
  private const val LIFEFORM_BASE_AGGRESSION = 10
  private const val LIFEFORM_BLEED_TURNS = 3
  private const val LIFEFORM_BLEED_MAX_HP_PERCENT = 3
  private const val LIFEFORM_POISON_TURNS = 3
  private const val LIFEFORM_POISON_MAX_HP_PERCENT = 2
  private const val LIFEFORM_BLEED_TURNS_KEY = "combat.lifeformBleedTurns"
  private const val LIFEFORM_POISON_TURNS_KEY = "combat.lifeformPoisonTurns"
  private const val LUCIA_M4A1_COMBAT_DAMAGE = 26
  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 30
  private const val KAI_LAST_REQUIEM_DAMAGE_PERCENT = 170
  private const val KAI_LAST_REQUIEM_BLEED_TURNS = 3
  private const val KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT = 5
  private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 20
  private const val KAI_SILENT_LULLABY_DAMAGE_PERCENT = 130
  private const val KAI_SALVATION_CHANCE_PERCENT = 20
  private const val KAI_SALVATION_DAMAGE_PERCENT = 147
  private const val KAI_QUICK_STEP_CHANCE_PERCENT = 30
  private const val KAI_QUICK_STEP_EVASION_BONUS_PERCENT = 50
  private const val KAI_QUICK_STEP_DURATION_TURNS = 3
  private const val KAI_BLEED_TURNS_KEY = "combat.kaiBleedTurns"
  private const val KAI_QUICK_STEP_TURNS_KEY = "combat.kaiQuickStepTurns"
  private const val IRIS_ANALYZED_TURNS_KEY = "combat.irisAnalyzedTurns"
  private const val IRIS_ARMOR_BREAK_TURNS_KEY = "combat.irisArmorBreakTurns"
  private const val IRIS_EXPOSED_TURNS_KEY = "combat.irisExposedTurns"
  private const val SYVIAL_BLEED_TURNS_KEY = "combat.syvialBleedTurns"
  private const val SYVIAL_DEVIL_TRIGGER_KEY = "combat.syvialDevilTrigger"
  private const val SYVIAL_DISORIENT_TURNS_KEY = "combat.syvialDisorientTurns"
  private const val IRIS_ULTIMATE_INTERVAL_TURNS = 4
  private const val SYVIAL_ULTIMATE_INTERVAL_TURNS = 3
  private const val AN_NHIEN_ULTIMATE_INTERVAL_TURNS = 5

  enum class Phase { ACTIVE, RESOLVED }
  enum class RangeBand { CLOSE, NEAR, FAR }
  enum class Cover { EXPOSED, PARTIAL, HARD }
  enum class EntityCondition { HEALTHY, HURT, WOUNDED, CRITICAL, DESTROYED }
  enum class Intent { READ, ATTACK, EVADE, MOVE, GUARD, ESCAPE, OTHER }

  data class Profile(
    val key: String,
    val displayName: String,
    val maxHp: Int,
    val attack: Int,
    val armor: Int,
    val aggression: Int
  )

  data class Snapshot(
    val encounterId: String,
    val entityKey: String,
    val entityName: String,
    val progressionRank: Long,
    val phase: Phase,
    val playerHp: Int,
    val playerMaxHp: Int,
    val entityHp: Int,
    val entityMaxHp: Int,
    val entityCondition: EntityCondition,
    val range: RangeBand,
    val cover: Cover,
    val momentum: Int,
    val opening: Int,
    val escapeProgress: Int,
    val noise: Int,
    val telegraph: String,
    val telegraphRevealed: Boolean,
    val eventCounter: Int,
    val seed: Long
  )

  // COMBAT_FEEDBACK_1193A: transient output; never persisted in GameState/save metadata.
  enum class FeedbackStatus(val label: String) {
    BLEED("Chảy máu"), POISON("Trúng độc"), STUN("Choáng"),
    ARMOR("Xuyên giáp"), DISORIENT("Mất phương hướng")
  }

  data class DamageFeedback(
    val target: String,
    val amount: Int,
    val critical: Boolean = false,
    val status: FeedbackStatus? = null,
    val actorId: String = KAI_ID,
    val phase: String = "actor"
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("target", target); put("actorId", actorId); put("text", "-$amount HP")
      put("critical", critical); put("flash", true); put("phase", phase)
      status?.let { put("status", it.label) }
    }
  }

  private fun feedbackStatus(code: String): FeedbackStatus? = when (code) {
    "bleed" -> FeedbackStatus.BLEED
    "poison" -> FeedbackStatus.POISON
    "stun" -> FeedbackStatus.STUN
    "armorBreak" -> FeedbackStatus.ARMOR
    "disorient" -> FeedbackStatus.DISORIENT
    else -> null
  }

  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false,
    val feedback: List<DamageFeedback> = emptyList()
  )

  private val profiles = listOf(
    Profile("hound", "Hound", 80, 15, 2, 8),
    Profile("clump", "Clump", 105, 17, 5, 7),
    Profile("duller", "Duller", 90, 14, 3, 6),
    Profile("deathmoth", "Deathmoth", 65, 13, 1, 7),
    Profile("hostile_faceling", "Hostile Faceling", 75, 14, 2, 7),
    Profile("false_puddle", "False Puddle", 95, 16, 4, 5),
    Profile("paintings", "Paintings", 70, 12, 1, 5),
    Profile("smiler", "Smiler", 85, 18, 2, 9),
    Profile("skin-stealer", "Skin-Stealer", 100, 18, 4, 8),
    Profile("predatory_window", "Predatory Window", 115, 17, 6, 6),
    Profile("biological_pipeline", "Biological Pipeline", 120, 18, 7, 7),
    Profile("wretch", "Wretch", 85, 16, 2, 8),
    Profile("cable_mimic", "Cable Mimic", 100, 17, 5, 8),
    Profile("the_beast_of_level_5", "The Beast of Level 5", 145, 22, 8, 9),
    Profile("hotel_corpse_lure", "Hotel Corpse Lure", 110, 18, 5, 7),
    Profile("jeff_the_killer", "Jeff the Killer", 120, 20, 4, 9),
    Profile("jane_the_killer", "Jane the Killer", 120, 20, 4, 9),
    Profile("slenderman", "Slenderman", 160, 23, 8, 10),
    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9),
    Profile("blackroot_sentinel", "Blackroot Sentinel", LIFEFORM_BASE_MAX_HP, LIFEFORM_BASE_ATTACK, LIFEFORM_BASE_ARMOR, LIFEFORM_BASE_AGGRESSION),
    Profile("sinew_strider", "Sinew Strider", LIFEFORM_BASE_MAX_HP, LIFEFORM_BASE_ATTACK, LIFEFORM_BASE_ARMOR, LIFEFORM_BASE_AGGRESSION),
    Profile("hollow_grasper", "Hollow Grasper", LIFEFORM_BASE_MAX_HP, LIFEFORM_BASE_ATTACK, LIFEFORM_BASE_ARMOR, LIFEFORM_BASE_AGGRESSION),
  ).associateBy { it.key }

  fun active(state: GameState): Snapshot? = decode(state)?.takeIf { it.phase == Phase.ACTIVE }

  fun start(state: GameState, entityKey: String): GameState {
    if (active(state) != null) return state
    val profile = profiles[entityKey] ?: return state
    val effective = CharacterStatEngine.effective(state, KAI_ID)
    val playerMax = effective.maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    // Every encounter gets a persistent local serial; equal turns/time cannot duplicate rewards.
    val serial = (state.metadata["loot.encounterSerial"]?.toLongOrNull() ?: 0L) + 1L
    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes xor serial)
    val progressionRank = EntityPowerScaling.rankFor(state)
    val unscaledEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val enhancedEntityMaxHp = EntityPowerScaling.scale(unscaledEntityMaxHp, progressionRank)
    val snapshot = Snapshot(
      encounterId = "$serial:${state.turn.currentTurnId}:$entityKey",
      entityKey = entityKey,
      entityName = profile.displayName,
      progressionRank = progressionRank,
      phase = Phase.ACTIVE,
      playerHp = playerHp,
      playerMaxHp = playerMax,
      entityHp = enhancedEntityMaxHp,
      entityMaxHp = enhancedEntityMaxHp,
      entityCondition = EntityCondition.HEALTHY,
      range = RangeBand.NEAR,
      cover = Cover.EXPOSED,
      momentum = 0,
      opening = 0,
      escapeProgress = 0,
      noise = 0,
      telegraph = telegraphFor(profile, seed, 0),
      telegraphRevealed = false,
      eventCounter = 0,
      seed = seed
    )
    return encode(state.copy(metadata = state.metadata + ("loot.encounterSerial" to serial.toString())), snapshot)
  }

  fun resolve(state: GameState, actionKind: String, action: String): Resolution {
    val current = active(state) ?: return Resolution(state, handled = false)
    val originalProfile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    val profile = if ((state.metadata["combat.caoMinhProc1"]?.toIntOrNull() ?: 0) > 0) originalProfile.copy(armor = originalProfile.armor * 90 / 100) else originalProfile
    val intent = classify(actionKind, action)
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
    val feedback = mutableListOf<DamageFeedback>()
    fun recordFeedback(target: String, before: Int, after: Int,
                       critical: Boolean = false, status: FeedbackStatus? = null, phase: String = "actor") {
      val amount = (before - after).coerceAtLeast(0)
      if (amount > 0) feedback += DamageFeedback(target, amount, critical, status, phase = phase)
    }
    var resolvedState = state
    var lifeformBleedTurns = state.metadata[LIFEFORM_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, LIFEFORM_BLEED_TURNS) ?: 0
    var lifeformPoisonTurns = state.metadata[LIFEFORM_POISON_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, LIFEFORM_POISON_TURNS) ?: 0
    var bleedTurns = state.metadata[KAI_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_LAST_REQUIEM_BLEED_TURNS) ?: 0
    var quickStepTurns = state.metadata[KAI_QUICK_STEP_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_QUICK_STEP_DURATION_TURNS) ?: 0
    var entityStunnedThisTurn = false
    val procSpecs = listOf(
      listOf("Huyết Sát Kiếm Ấn", "50", "25", "bleed", "3"),
      listOf("Phá Giáp Ma Kiếm", "48", "20", "armorBreak", "10"),
      listOf("Ma Tâm Chấn", "45", "15", "stun", "0"),
      listOf("Huyết Độc Ma Khí", "47", "20", "poison", "3"),
      listOf("Huyết Liệt Ma Ấn", "51", "20", "bleed", "4")
    )
    // Persistent per-skill durations: refresh, never stack the same proc twice.
    procSpecs.forEachIndexed { index, spec ->
      val key = "combat.caoMinhProc$index"
      val turns = state.metadata[key]?.toIntOrNull() ?: 0
      if (turns > 0) {
        if (spec[3] == "bleed" || spec[3] == "poison") {
          val damage = c.entityMaxHp * spec[4].toInt() / 100
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = feedbackStatus(spec[3]))
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "${spec[0]}: -$damage HP (${spec[3]})."
        }
        resolvedState = withCombatCounter(resolvedState, key, turns - 1)
      }
    }
    var companionEnemyAccuracyPenalty = 0
    var irisAnalyzedTurns = state.metadata[IRIS_ANALYZED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var irisArmorBreakTurns = state.metadata[IRIS_ARMOR_BREAK_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var irisExposedTurns = state.metadata[IRIS_EXPOSED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialBleedTurns = state.metadata[SYVIAL_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var syvialDisorientTurns = state.metadata[SYVIAL_DISORIENT_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialDevilTrigger = state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.equals("true", ignoreCase = true) == true

    if (c.playerHp > 0 && lifeformBleedTurns > 0) {
      val bleedDamage = percentDamage(c.playerMaxHp, LIFEFORM_BLEED_MAX_HP_PERCENT)
      val hp = max(0, c.playerHp - bleedDamage)
      recordFeedback("actor", c.playerHp, hp, status = FeedbackStatus.BLEED, phase = "entity")
      c = c.copy(playerHp = hp)
      lifeformBleedTurns = max(0, lifeformBleedTurns - 1)
      resolvedState = withLifeformCounter(resolvedState, LIFEFORM_BLEED_TURNS_KEY, lifeformBleedTurns)
      log += "Lifeform Bleed: Kai -" + bleedDamage + " HP (" + LIFEFORM_BLEED_MAX_HP_PERCENT + "% Max HP); còn " + lifeformBleedTurns + " turn."
    }
    if (c.playerHp > 0 && lifeformPoisonTurns > 0) {
      val poisonDamage = percentDamage(c.playerMaxHp, LIFEFORM_POISON_MAX_HP_PERCENT)
      val hp = max(0, c.playerHp - poisonDamage)
      recordFeedback("actor", c.playerHp, hp, status = FeedbackStatus.POISON, phase = "entity")
      c = c.copy(playerHp = hp)
      lifeformPoisonTurns = max(0, lifeformPoisonTurns - 1)
      resolvedState = withLifeformCounter(resolvedState, LIFEFORM_POISON_TURNS_KEY, lifeformPoisonTurns)
      log += "Lifeform Poison: Kai -" + poisonDamage + " HP (" + LIFEFORM_POISON_MAX_HP_PERCENT + "% Max HP); còn " + lifeformPoisonTurns + " turn."
    }

    when (intent) {
      Intent.READ -> {
        c = c.copy(
          telegraphRevealed = true,
          opening = min(3, c.opening + 1),
          momentum = min(3, c.momentum + 1)
        )
        log += "Cao Minh đọc được nhịp tấn công của ${c.entityName}; sơ hở tăng lên."
      }
      Intent.EVADE -> {
        log += "PARTY ACTION NÉ TRÁNH: ${activePartyNames(resolvedState)} cùng thực hiện trong một combat turn."
        val goodCounter = c.telegraph in setOf("LUNGE", "GRAB", "RUSH")
        c = c.copy(
          range = if (c.range == RangeBand.CLOSE) RangeBand.NEAR else c.range,
          momentum = (c.momentum + if (goodCounter) 2 else 1).coerceIn(-3, 3),
          opening = min(3, c.opening + if (goodCounter) 2 else 1),
          escapeProgress = min(100, c.escapeProgress + (if (goodCounter) 18 else 10) + PokerDiceCore.escapeBonus(state)),
          cover = if (c.cover == Cover.EXPOSED) Cover.PARTIAL else c.cover
        )
        log += if (goodCounter) "Cao Minh né đúng telegraph, cướp thế chủ động." else "Cao Minh đổi góc và giảm áp lực trực diện."
      }
      Intent.MOVE -> {
        val nextRange = when (c.range) {
          RangeBand.CLOSE -> RangeBand.NEAR
          RangeBand.NEAR -> RangeBand.FAR
          RangeBand.FAR -> RangeBand.FAR
        }
        c = c.copy(
          range = nextRange,
          cover = if (c.cover == Cover.EXPOSED) Cover.PARTIAL else Cover.HARD,
          escapeProgress = min(100, c.escapeProgress + 15),
          momentum = min(3, c.momentum + 1)
        )
        log += "Cao Minh tái định vị, kéo giãn khoảng cách và tìm vật che chắn."
      }
      Intent.GUARD -> {
        c = c.copy(cover = Cover.HARD, momentum = min(3, c.momentum + 1), opening = min(3, c.opening + 1))
        log += "Cao Minh khóa tư thế phòng thủ và ép ${c.entityName} phải lộ hướng tấn công."
      }
      Intent.ESCAPE -> {
        log += "PARTY ACTION BỎ CHẠY: ${activePartyNames(resolvedState)} cùng rút khỏi encounter trong một combat turn."
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 } + PokerDiceCore.escapeBonus(state)
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        log += "Cao Minh dồn ưu thế vào đường thoát (${c.escapeProgress}%)."
      }
      Intent.ATTACK -> {
        log += "PARTY ACTION TẤN CÔNG: ${activePartyNames(resolvedState)} cùng khai triển đòn đánh trong một combat turn."
        val roll = roll(c, 100)
        val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6 + PokerDiceCore.accuracyBonus(state)).coerceIn(20, 99)
        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 13), 100)
        val entityEvaded = evasionRoll < (ENTITY_EVASION_PERCENT - CharacterStatEngine.effective(resolvedState, KAI_ID).resEvasionPercent).coerceAtLeast(0)
        if (roll < hitChance && !entityEvaded) {
          val variance = 2 + roll(c.copy(eventCounter = c.eventCounter + 17), 7)
          val effective = CharacterStatEngine.effective(state, KAI_ID)
          val weaponDamage = CharacterStatEngine.weaponDamage(state, KAI_ID)
          val critChance = CaoMinhCombatPassive.criticalChance(resolvedState, effective.criticalChancePercent)
          val critical = roll(c.copy(eventCounter = c.eventCounter + 23), 100) < critChance
          val rawBase = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val base = CharacterStatCore.basicDamage(rawBase, effective.str, PokerDiceCore.attackPercent(resolvedState), CaoMinhCombatPassive.attackPercent(resolvedState))
          val normalized = if (critical) CharacterStatCore.criticalDamage(base) else base
          val damage = min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100))
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, critical = critical)
          c = c.copy(
            entityHp = hp,
            entityCondition = condition(hp, c.entityMaxHp),
            momentum = min(3, c.momentum + 1),
            opening = max(0, c.opening - 1),
            noise = min(100, c.noise + 35)
          )
          log += "Đòn đánh trúng ${c.entityName}: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          c = c.copy(momentum = max(-3, c.momentum - 1), opening = max(0, c.opening - 1), noise = min(100, c.noise + 28))
          log += if (entityEvaded) "${c.entityName} né đòn (25% evasion) và giành lại áp lực." else "Đòn đánh trượt; ${c.entityName} giành lại áp lực."
        }
        // LUCIA_JOINT_ATTACK: process the follower when the player explicitly orders both attackers.
        val jointOrder = true // PARTY_ACTIONS_V1: every ATTACK intent includes every ACTIVE Party member.
        val lucia = resolvedState.characters[LUCIA_ID]
        val luciaActive = LUCIA_ID in resolvedState.party.memberIds &&
          lucia?.presence == CharacterPresence.ACTIVE && (lucia.vitalState.currentHp > 0)
        if (jointOrder && luciaActive) {
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < hitChance && !luciaEntityEvaded) {
            val luciaPotentialDamage = max(1, LUCIA_M4A1_COMBAT_DAMAGE - profile.armor)
            val luciaDamage = min(c.entityHp, luciaPotentialDamage)
            val luciaHp = max(0, c.entityHp - luciaDamage)
            recordFeedback("entity", c.entityHp, luciaHp)
            c = c.copy(
              entityHp = luciaHp,
              entityCondition = condition(luciaHp, c.entityMaxHp),
              noise = min(100, c.noise + 22)
            )
            log += "Lucia \"Lục\" bắn hỗ trợ bằng M4A1: -$luciaDamage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Lucia \"Lục\" cũng khai hỏa nhưng phát bắn không trúng mục tiêu."
          }
        }
        // PARTY_FOLLOWER_BASE_ATTACKS_V1: all actors resolve inside this one ATTACK event.
        val irisPartyAttack = activePartyCharacter(resolvedState, IRIS_ID)
        if (irisPartyAttack != null) {
          val irisHitRoll = roll(c.copy(eventCounter = c.eventCounter + 307), 100)
          val irisEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 311), 100)
          if (irisHitRoll < hitChance && irisEvasionRoll >= ENTITY_EVASION_PERCENT) {
            val potential = companionSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, IRIS_ID), 100, profile.armor)
            val damage = min(c.entityHp, potential)
            val hp = max(0, c.entityHp - damage)
            recordFeedback("entity", c.entityHp, hp)
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 10))
            log += "Iris thực hiện lệnh TẤN CÔNG bằng Ivory & Ebony: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Iris thực hiện lệnh TẤN CÔNG nhưng loạt bắn không trúng mục tiêu."
          }
        }

        val syvialPartyAttack = activePartyCharacter(resolvedState, SYVIAL_ID)
        if (syvialPartyAttack != null) {
          val syvialHitRoll = roll(c.copy(eventCounter = c.eventCounter + 317), 100)
          val syvialEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 331), 100)
          if (syvialHitRoll < hitChance && syvialEvasionRoll >= ENTITY_EVASION_PERCENT) {
            val potential = companionSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID), 100, profile.armor)
            val damage = min(c.entityHp, potential)
            val hp = max(0, c.entityHp - damage)
            recordFeedback("entity", c.entityHp, hp)
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 12))
            log += "Syvial thực hiện lệnh TẤN CÔNG bằng GodKiller: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Syvial thực hiện lệnh TẤN CÔNG nhưng Entity tránh được nhát chém."
          }
        }

        if (activePartyCharacter(resolvedState, AN_NHIEN_ID) != null) {
          log += "An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ: gây nhiễu/đánh lạc hướng, không dùng vũ khí và không gây damage."
        }
      }
      Intent.OTHER -> {
        c = c.copy(momentum = max(-3, c.momentum - 1))
        log += "Hành động không tạo được lợi thế chiến đấu rõ ràng."
      }
    }

    if (c.entityHp > 0 && bleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT)
      val hp = max(0, c.entityHp - bleedDamage)
      bleedTurns = max(0, bleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
      recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Huyết Ma Tứ Liên gây -$bleedDamage HP (${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $bleedTurns turn."
    }

    if (c.entityHp > 0 && syvialBleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, 4)
      val hp = max(0, c.entityHp - bleedDamage)
      syvialBleedTurns = max(0, syvialBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Crimson Guillotine gây -$bleedDamage HP (4% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $syvialBleedTurns turn."
    }

    if (c.entityHp <= 0) {
      val persisted = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED)))
      val cleared = clearCombatOnly(persisted)
      val loot = OfflineEntityLoot.award(cleared, c.encounterId, c.seed)
      return Resolution(loot.state, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt. " + loot.text, entityDestroyed = true, feedback = feedback.toList())
    }
    if (c.escapeProgress >= 100) {
      val persisted = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED)))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " Cao Minh cắt được truy đuổi và thoát khỏi encounter.", escaped = true, feedback = feedback.toList())
    }

    // PARTY_ATTACK_GCO_GATE_V1
    if (intent == Intent.ATTACK) {
    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0) {
      val damagePerSlash = CharacterStatEngine.skillBaseDamage(resolvedState, KAI_ID) * 115 / 100
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * damagePerSlash
      val hp = max(0, c.entityHp - totalDamage)
      recordFeedback("entity", c.entityHp, hp)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Huyết Ma Nhị Thập Tứ Trảm tự động kích hoạt ở combat turn ${c.eventCounter}: $KAI_GUILTY_CROWN_SHOTS/" +
        "$KAI_GUILTY_CROWN_SHOTS trảm trúng liên tiếp, Accuracy $KAI_GUILTY_CROWN_ACCURACY_PERCENT%, bỏ qua toàn bộ hiệu ứng né; " +
        "mỗi trảm -$damagePerSlash HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."
      if (c.entityHp <= 0) {
        val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
        val cleared = clearCombatOnly(persisted)
        val loot = OfflineEntityLoot.award(cleared, c.encounterId, c.seed)
      return Resolution(loot.state, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt. " + loot.text, entityDestroyed = true, feedback = feedback.toList())
      }
    }

    }

    val isGuiltyCrownTurn = intent == Intent.ATTACK && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0
    if (intent == Intent.ATTACK && c.entityHp > 0 && !isGuiltyCrownTurn) {
      procSpecs.forEachIndexed { index, spec ->
        if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 401 + index * 17), 100) < spec[1].toInt()) {
          val damage = CharacterStatCore.scaleByPercent(CharacterStatEngine.weaponDamage(resolvedState, KAI_ID), CaoMinhCombatPassive.attackPercent(resolvedState)) * spec[2].toInt() / 100
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = feedbackStatus(spec[3]))
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          if (spec[3] == "stun") entityStunnedThisTurn = true
          else resolvedState = withCombatCounter(resolvedState, "combat.caoMinhProc$index", 2)
          log += "${spec[0]} kích hoạt: -$damage HP; ${spec[3]}."
        }
      }
    }
    if (c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)

      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 22))
        bleedTurns = KAI_LAST_REQUIEM_BLEED_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
        log += "Huyết Ma Tứ Liên tự động kích hoạt: 4 trảm vào điểm nối hộ thể, ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."
      }

      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.STUN)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
        entityStunnedThisTurn = true
        log += "Ma Tâm Trấn Hồn tự động kích hoạt: Cao Minh bật lên cao, 4 đường ma kiếm vào cùng điểm trên ngực, ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."
      }

      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        recordFeedback("entity", c.entityHp, hp)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 16))
        log += "Huyết Ảnh Ma Độn tự động kích hoạt: Cao Minh ngự Huyết Ma Kiếm qua mục tiêu, dịch chuyển theo kiếm và chém nhanh đúng 2 trảm, ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."
      }

      if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {
        quickStepTurns = KAI_QUICK_STEP_DURATION_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
        log += "Thiên Ma Bộ tự động kích hoạt: dịch chuyển ngắn liên tục, +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${KAI_QUICK_STEP_DURATION_TURNS} turn."
      }
    }

    if (c.entityHp <= 0) {
      val persisted = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED)))
      val cleared = clearCombatOnly(persisted)
      val loot = OfflineEntityLoot.award(cleared, c.encounterId, c.seed)
      return Resolution(loot.state, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt. " + loot.text, entityDestroyed = true, feedback = feedback.toList())
    }

    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.
    val irisActive = activePartyCharacter(resolvedState, IRIS_ID) != null
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    val syvialActive = syvialCharacter != null
    val anNhienActive = activePartyCharacter(resolvedState, AN_NHIEN_ID) != null

    if (irisActive && c.entityHp > 0) {
      if (irisAnalyzedTurns <= 0) {
        irisAnalyzedTurns = 3
        resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
        log += "ARGUS Terrain Read: Iris đánh dấu mục tiêu Analyzed trong 3 turn."
      }
      val irisWeapon = CharacterStatEngine.skillBaseDamage(resolvedState, IRIS_ID)
      val irisArmor = when {
        irisExposedTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        irisArmorBreakTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        else -> profile.armor
      }
      val irisUltimate = intent == Intent.ATTACK && c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0
      if (irisUltimate) {
        val damage = companionSkillDamage(irisWeapon, 300, irisArmor)
        val hp = max(0, c.entityHp - damage)
        recordFeedback("entity", c.entityHp, hp)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 30))
        irisExposedTurns = 2
        resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
        log += "ARGUS // Thousandfold Execution: 12 phát luân phiên, 300% DMG = -$damage HP; Fully Exposed 2 turn."
      } else if (intent == Intent.ATTACK) {
        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {
          val percent = if (irisAnalyzedTurns > 0) 170 else 155
          val damage = companionSkillDamage(irisWeapon, percent, irisArmor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 14))
          log += "Twosome Time tự động kích hoạt: 2 phát chéo góc, $percent% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 20 && c.entityHp > 0) {
          val damage = companionSkillDamage(irisWeapon, 145, irisArmor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
          log += "Rain Storm tự động kích hoạt: 6 phát khi đổi góc trên không, 145% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 20 && c.entityHp > 0) {
          val damage = companionSkillDamage(irisWeapon, 185, irisArmor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.ARMOR)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
          irisArmorBreakTurns = 2
          resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
          log += "Honeycomb Fire tự động kích hoạt: 8 phát tập trung, 185% DMG = -$damage HP; Armor Break 20% trong 2 turn."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 25 && c.entityHp > 0) {
          val chargedArmor = armorAfterIgnore(profile.armor, 35)
          val damage = companionSkillDamage(irisWeapon, 175, chargedArmor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.ARMOR)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 20))
          log += "Charged Shot tự động kích hoạt: 175% DMG = -$damage HP, bỏ qua 35% Armor."
        }
      }
    }

    if (syvialActive && c.entityHp > 0) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
      val syvialHp = syvialCharacter!!.vitalState.currentHp
      if (!syvialDevilTrigger && (syvialHp * 2 <= syvialMaxHp || c.entityKey == DIEP_MINH_KEY)) {
        syvialDevilTrigger = true
        val metadata = resolvedState.metadata.toMutableMap()
        metadata[SYVIAL_DEVIL_TRIGGER_KEY] = "true"
        resolvedState = resolvedState.copy(metadata = metadata)
        log += "Syvial kích hoạt Devil Trigger."
      }
      val regenPercent = if (syvialDevilTrigger) 4 else 2
      if (syvialHp > 0 && syvialHp < syvialMaxHp) {
        val heal = percentDamage(syvialMaxHp, regenPercent)
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, syvialHp + heal)
        val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvialHp
        log += "Lucifer Core hồi Syvial +${after - syvialHp} HP ($after/$syvialMaxHp)."
      }
      val syvialWeapon = CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID)
      val dtMultiplier = if (syvialDevilTrigger) 125 else 100
      fun syvialDamage(percent: Int, armor: Int): Int = companionSkillDamage(syvialWeapon, (percent * dtMultiplier + 99) / 100, armor)
      val syvialUltimate = intent == Intent.ATTACK && syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0
      if (syvialUltimate) {
        val damage = min(c.entityHp, 24 * 10)
        val hp = max(0, c.entityHp - damage)
        recordFeedback("entity", c.entityHp, hp)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
        log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x 10 HP = -$damage HP; bỏ qua Evasion."
      } else if (intent == Intent.ATTACK) {
        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {
          val damage = syvialDamage(175, armorAfterIgnore(profile.armor, 20))
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.ARMOR)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Rift Sever tự động kích hoạt: Spatial Shift + GodKiller, 175% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 223), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(190, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialBleedTurns = 3
          resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
          log += "Crimson Guillotine tự động kích hoạt: 190% DMG = -$damage HP; Bleeding 3 turn x 4% Max HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 239), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(155, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.STUN)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          entityStunnedThisTurn = true
          log += "Lucifer Breaker tự động kích hoạt: 155% DMG = -$damage HP; Entity bị Stun trong phản ứng hiện tại."
        }
        if (syvialDevilTrigger && roll(c.copy(eventCounter = c.eventCounter + 251), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(210, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.DISORIENT)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialDisorientTurns = 2
          resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
          log += "Spatial Dominion tự động kích hoạt: 210% DMG = -$damage HP; Disoriented -25% Accuracy trong 2 turn."
        }
      }
    }

    if (anNhienActive && c.entityHp > 0) {
      if (intent == Intent.ATTACK && roll(c.copy(eventCounter = c.eventCounter + 269), 100) < 25) {
        companionEnemyAccuracyPenalty += 25
        log += "An Nhiên dùng Quăng Đại Cái Gì Đó: tiếng động lệch hướng khiến Entity -25 điểm % Accuracy trong phản ứng hiện tại."
      }
      if (intent == Intent.ESCAPE && c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0) {
        companionEnemyAccuracyPenalty += 20
        c = c.copy(escapeProgress = min(100, c.escapeProgress + 30))
        log += "Kế Hoạch Không Có Trong Kế Hoạch: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại."
      }
    }

    if (syvialDisorientTurns > 0) companionEnemyAccuracyPenalty += 25

    if (c.entityHp <= 0) {
      val persisted = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED)))
      val cleared = clearCombatOnly(persisted)
      val loot = OfflineEntityLoot.award(cleared, c.encounterId, c.seed)
      return Resolution(loot.state, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt. " + loot.text, entityDestroyed = true, feedback = feedback.toList())
    }

    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.
    if (entityStunnedThisTurn) {
      log += "Ma Tâm Trấn Hồn: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)
      resolvedState = pulse.state
      recordFeedback("actor", c.playerHp, pulse.kaiHp, phase = "entity")
      c = c.copy(playerHp = pulse.kaiHp, momentum = max(-3, c.momentum - 1))
      log += "Devils And Gold kích hoạt ở combat turn ${c.eventCounter}: toàn bộ nhân vật ACTIVE đang ra trận nhận ${DIEP_MINH_ULTIMATE_PERCENT}% Max HP. ${pulse.summary}."
    } else {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4 +
      PokerDiceCore.defenseBonus(state)
      val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty).coerceIn(0, 88)
      if (incomingRoll < enemyChance) {
        val lifeformSkill = rollLifeformSkill(c)
        val damage = if (c.entityKey == DIEP_MINH_KEY) {
          EntityPowerScaling.scale(percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT), c.progressionRank)
        } else {
          run {
            val basicDamage = max(1, EntityPowerScaling.scale(profile.attack, c.progressionRank) + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
            if (lifeformSkill == null) basicDamage
            else CharacterStatCore.scaleByPercent(basicDamage, lifeformSkill.basicAttackPercent)
          }
        }
        val hp = max(0, c.playerHp - damage)
        recordFeedback("actor", c.playerHp, hp, phase = "entity")
        c = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
        if (lifeformSkill != null) {
          if (c.playerHp > 0 && lifeformSkill.bleed) {
            lifeformBleedTurns = LIFEFORM_BLEED_TURNS
            resolvedState = withLifeformCounter(resolvedState, LIFEFORM_BLEED_TURNS_KEY, lifeformBleedTurns)
          }
          if (c.playerHp > 0 && lifeformSkill.poison) {
            lifeformPoisonTurns = LIFEFORM_POISON_TURNS
            resolvedState = withLifeformCounter(resolvedState, LIFEFORM_POISON_TURNS_KEY, lifeformPoisonTurns)
          }
          val effects = when {
            lifeformSkill.bleed && lifeformSkill.poison -> "Bleed + Poison"
            lifeformSkill.bleed -> "Bleed"
            else -> "Poison"
          }
          log += lifeformSkill.name + " proc: " + lifeformSkill.basicAttackPercent + "% Basic Attack; " + effects + "."
        }
        log += if (c.entityKey == DIEP_MINH_KEY) {
          "Diệp Minh phản công: Cao Minh -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else {
          "${c.entityName} phản công: Cao Minh -$damage HP (${c.playerHp}/${c.playerMaxHp})."
        }
      } else {
        log += if (quickStepTurns > 0) {
          "Thiên Ma Bộ khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Cao Minh."
        }
        if (intent == Intent.ATTACK && irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
          val damage = companionSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, IRIS_ID), 120, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, phase = "entity")
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
        }
        if (intent == Intent.ATTACK && syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {
          val damage = companionSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID), if (syvialDevilTrigger) 157 else 125, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, phase = "entity")
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
        }
      }
    }

    if (quickStepTurns > 0) {
      quickStepTurns = max(0, quickStepTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
    }

    if (irisAnalyzedTurns > 0) {
      irisAnalyzedTurns = max(0, irisAnalyzedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
    }
    if (irisArmorBreakTurns > 0) {
      irisArmorBreakTurns = max(0, irisArmorBreakTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
    }
    if (irisExposedTurns > 0) {
      irisExposedTurns = max(0, irisExposedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
    }
    if (syvialDisorientTurns > 0) {
      syvialDisorientTurns = max(0, syvialDisorientTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
    }

    val entityHpBeforeRegen = c.entityHp
    val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN
    val entityHpAfterRegen = min(c.entityMaxHp, c.entityHp + entityRegen)
    if (entityHpAfterRegen > entityHpBeforeRegen) {
      c = c.copy(entityHp = entityHpAfterRegen, entityCondition = condition(entityHpAfterRegen, c.entityMaxHp))
      log += "${c.entityName} hồi +$entityRegen HP (${c.entityHp}/${c.entityMaxHp})."
    }

    c = c.copy(
      telegraph = telegraphFor(profile, c.seed, c.eventCounter),
      telegraphRevealed = false,
      opening = max(0, c.opening - if (intent == Intent.READ) 0 else 1)
    )
    val next = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c))
    return Resolution(next, true, log.joinToString(" "), feedback = feedback.toList())
  }

  fun toJson(state: GameState): JSONObject? = decode(state)?.let { c -> JSONObject().apply {
    put("active", c.phase == Phase.ACTIVE)
    put("encounterId", c.encounterId)
    put("entityKey", c.entityKey)
    put("entityName", c.entityName)
    put("progressionRank", c.progressionRank)
    put("playerHp", c.playerHp); put("playerMaxHp", c.playerMaxHp)
    put("entityHp", c.entityHp); put("entityMaxHp", c.entityMaxHp)
    put("entityCondition", c.entityCondition.name)
    put("range", c.range.name); put("cover", c.cover.name)
    put("momentum", c.momentum); put("opening", c.opening)
    put("escapeProgress", c.escapeProgress); put("noise", c.noise)
    put("telegraph", if (c.telegraphRevealed) c.telegraph else "UNKNOWN")
    put("telegraphRevealed", c.telegraphRevealed)
  } }

  fun clear(state: GameState): GameState = clearCombatOnly(state)

  private fun withCombatCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

  private fun weaponSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - armor)

  // PARTY_ACTIONS_V1: authoritative roster for one simultaneous Party command.
  private fun activePartyNames(state: GameState): String =
    state.party.memberIds.distinct().mapNotNull { id ->
      state.characters[id]?.takeIf { character ->
        character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
      }?.name
    }.joinToString(", ")

  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in state.party.memberIds) return null
    val character = state.characters[characterId] ?: return null
    return character.takeIf { it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0 }
  }

  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))

  private fun armorAfterIgnore(armor: Int, ignorePercent: Int): Int =
    max(0, armor - ((armor * ignorePercent + 99) / 100))

  private data class PartyPercentDamage(
    val state: GameState,
    val kaiHp: Int,
    val summary: String
  )

  private fun percentDamage(maxHp: Int, percent: Int): Int =
    max(1, (maxHp * percent + 99) / 100)

  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {
    var next = state
    val lines = mutableListOf<String>()
    state.party.memberIds.distinct().forEach { characterId ->
      val character = next.characters[characterId] ?: return@forEach
      if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return@forEach
      val maxHp = CharacterStatEngine.effective(next, characterId).maxHp
      val damage = percentDamage(maxHp, percent)
      val before = character.vitalState.currentHp
      next = CharacterStatEngine.setCurrentHp(next, characterId, before - damage)
      val after = next.characters[characterId]?.vitalState?.currentHp ?: max(0, before - damage)
      lines += "${character.name} -$damage HP ($after/$maxHp)"
    }
    val kaiMaxHp = CharacterStatEngine.effective(next, KAI_ID).maxHp
    val kaiHp = next.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, kaiMaxHp) ?: kaiMaxHp
    return PartyPercentDamage(
      state = next,
      kaiHp = kaiHp,
      summary = if (lines.isEmpty()) "không có nhân vật ACTIVE hợp lệ để nhận sát thương" else lines.joinToString("; ")
    )
  }

  // LIFEFORM_BASIC_ATTACK_PERCENT_R03
  private data class LifeformSkillSpec(
    val name: String,
    val procPercent: Int,
    val basicAttackPercent: Int,
    val bleed: Boolean,
    val poison: Boolean
  )

  private fun lifeformSkills(entityKey: String): List<LifeformSkillSpec> = when (entityKey) {
    "blackroot_sentinel" -> listOf(
      LifeformSkillSpec("Thorned Hemorrhage", 25, 115, bleed = true, poison = false),
      LifeformSkillSpec("Blight Sap", 20, 125, bleed = false, poison = true),
      LifeformSkillSpec("Crimson Mycotoxin", 10, 140, bleed = true, poison = true)
    )
    "sinew_strider" -> listOf(
      LifeformSkillSpec("Tendon Ripper", 25, 115, bleed = true, poison = false),
      LifeformSkillSpec("Septic Thread", 20, 125, bleed = false, poison = true),
      LifeformSkillSpec("Venomous Flay", 10, 140, bleed = true, poison = true)
    )
    "hollow_grasper" -> listOf(
      LifeformSkillSpec("Hollow Laceration", 25, 115, bleed = true, poison = false),
      LifeformSkillSpec("Carrion Toxin", 20, 125, bleed = false, poison = true),
      LifeformSkillSpec("Necrotic Clutch", 10, 140, bleed = true, poison = true)
    )
    else -> emptyList()
  }

  private fun rollLifeformSkill(c: Snapshot): LifeformSkillSpec? {
    val specs = lifeformSkills(c.entityKey)
    if (specs.isEmpty()) return null
    val procRoll = roll(c.copy(eventCounter = c.eventCounter + 211), 100)
    var cursor = 0
    for (spec in specs) {
      cursor += spec.procPercent
      if (procRoll < cursor) return spec
    }
    return null
  }

  private fun withLifeformCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

  private fun encode(state: GameState, c: Snapshot): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata["${PREFIX}encounterId"] = c.encounterId
    metadata["${PREFIX}entityKey"] = c.entityKey
    metadata["${PREFIX}entityName"] = c.entityName
    metadata["${PREFIX}progressionRank"] = c.progressionRank.toString()
    metadata["${PREFIX}phase"] = c.phase.name
    metadata["${PREFIX}entityHp"] = c.entityHp.toString()
    metadata["${PREFIX}entityMaxHp"] = c.entityMaxHp.toString()
    metadata["${PREFIX}entityCondition"] = c.entityCondition.name
    metadata["${PREFIX}range"] = c.range.name
    metadata["${PREFIX}cover"] = c.cover.name
    metadata["${PREFIX}momentum"] = c.momentum.toString()
    metadata["${PREFIX}opening"] = c.opening.toString()
    metadata["${PREFIX}escapeProgress"] = c.escapeProgress.toString()
    metadata["${PREFIX}noise"] = c.noise.toString()
    metadata["${PREFIX}telegraph"] = c.telegraph
    metadata["${PREFIX}telegraphRevealed"] = c.telegraphRevealed.toString()
    metadata["${PREFIX}eventCounter"] = c.eventCounter.toString()
    metadata["${PREFIX}seed"] = c.seed.toString()
    return CharacterStatEngine.setCurrentHp(state.copy(metadata = metadata), KAI_ID, c.playerHp)
  }

  private fun decode(state: GameState): Snapshot? {
    val m = state.metadata
    val key = m["${PREFIX}entityKey"]?.takeIf { it.isNotBlank() } ?: return null
    val profile = profiles[key] ?: return null
    val progressionRank = m["${PREFIX}progressionRank"]?.toLongOrNull()?.let(EntityPowerScaling::requireRank) ?: 0L
    val unscaledCanonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val canonicalMaxHp = EntityPowerScaling.scale(unscaledCanonicalMaxHp, progressionRank)
    val storedMaxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: canonicalMaxHp
    val maxHp = max(storedMaxHp, canonicalMaxHp)
    val storedHp = m["${PREFIX}entityHp"]?.toIntOrNull()?.coerceIn(0, storedMaxHp) ?: storedMaxHp
    val hp = if (storedMaxHp < canonicalMaxHp) min(maxHp, storedHp + (canonicalMaxHp - storedMaxHp)) else storedHp.coerceIn(0, maxHp)
    val playerMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    return Snapshot(
      encounterId = m["${PREFIX}encounterId"].orEmpty(),
      entityKey = key,
      entityName = m["${PREFIX}entityName"] ?: profile.displayName,
      progressionRank = progressionRank,
      phase = enumOr(Phase.ACTIVE, m["${PREFIX}phase"]),
      playerHp = playerHp,
      playerMaxHp = playerMax,
      entityHp = hp,
      entityMaxHp = maxHp,
      entityCondition = condition(hp, maxHp),
      range = enumOr(RangeBand.NEAR, m["${PREFIX}range"]),
      cover = enumOr(Cover.EXPOSED, m["${PREFIX}cover"]),
      momentum = m["${PREFIX}momentum"]?.toIntOrNull()?.coerceIn(-3, 3) ?: 0,
      opening = m["${PREFIX}opening"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0,
      escapeProgress = m["${PREFIX}escapeProgress"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
      noise = m["${PREFIX}noise"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
      telegraph = m["${PREFIX}telegraph"] ?: telegraphFor(profile, stableSeed(key, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes), 0),
      telegraphRevealed = m["${PREFIX}telegraphRevealed"].toBoolean(),
      eventCounter = m["${PREFIX}eventCounter"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
      seed = m["${PREFIX}seed"]?.toLongOrNull() ?: stableSeed(key, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    )
  }

  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return state.copy(metadata = metadata)
  }

  private fun classify(actionKind: String, raw: String): Intent {
    val text = raw.lowercase()
    if (containsAny(text, "bắn", "đánh", "chém", "đâm", "tấn công", "shoot", "attack", "fire")) return Intent.ATTACK
    if (containsAny(text, "né", "lách", "dodge", "evade", "tránh")) return Intent.EVADE
    if (containsAny(text, "chạy thoát", "bỏ chạy", "thoát", "escape", "flee")) return Intent.ESCAPE
    if (containsAny(text, "thủ", "đỡ", "chặn", "guard", "block", "cover")) return Intent.GUARD
    if (actionKind.equals("SEARCH", true) || containsAny(text, "quan sát", "đọc", "nhìn kỹ", "theo dõi", "observe", "read")) return Intent.READ
    if (actionKind.equals("EXPLORE", true) || containsAny(text, "lùi", "tiến", "di chuyển", "núp", "vòng", "move", "reposition")) return Intent.MOVE
    return Intent.OTHER
  }

  private fun containsAny(text: String, vararg needles: String) = needles.any(text::contains)

  private fun condition(hp: Int, maxHp: Int): EntityCondition {
    if (hp <= 0) return EntityCondition.DESTROYED
    val ratio = hp.toDouble() / maxHp.toDouble()
    return when {
      ratio > .75 -> EntityCondition.HEALTHY
      ratio > .50 -> EntityCondition.HURT
      ratio > .25 -> EntityCondition.WOUNDED
      else -> EntityCondition.CRITICAL
    }
  }

  private fun telegraphFor(profile: Profile, seed: Long, counter: Int): String {
    val options = when {
      profile.key == "smiler" -> listOf("STALK", "RUSH", "VANISH")
      profile.key == "cable_mimic" -> listOf("GRAB", "LUNGE", "FLANK")
      profile.key == "slenderman" -> listOf("STALK", "GRAB", "RUSH")
      else -> listOf("LUNGE", "GRAB", "RUSH", "FLANK")
    }
    return options[positiveMod(mix(seed, counter), options.size)]
  }

  private fun roll(c: Snapshot, bound: Int): Int = positiveMod(mix(c.seed, c.eventCounter), bound)
  private fun stableSeed(entityKey: String, turnId: String, time: Long): Long = mix(entityKey.hashCode().toLong() * 31L + turnId.hashCode(), time.toInt())
  private fun mix(seed: Long, counter: Int): Long {
    var x = seed xor (counter.toLong() * -7046029254386353131L)
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    return x xor (x ushr 31)
  }
  private fun positiveMod(value: Long, bound: Int): Int = ((value and Long.MAX_VALUE) % bound.toLong()).toInt()
  private inline fun <reified T : Enum<T>> enumOr(fallback: T, raw: String?): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
