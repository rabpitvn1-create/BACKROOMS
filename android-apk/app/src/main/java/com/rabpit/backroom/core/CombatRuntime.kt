package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Authoritative, save-persistent combat state stored in GameState.metadata. */
object CombatRuntime {
  internal var lootRng: LootRng = LootRng { bound -> kotlin.random.Random.nextInt(bound) }
  private const val PREFIX = "combat."
  // COMBAT_VIETNAMESE_NARRATION_V1
  internal fun localizeCombatNarration(text: String): String = text
    .replace("PARTY ACTION TẤN CÔNG:", "HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG:")
    .replace("PARTY ACTION NÉ TRÁNH:", "HÀNH ĐỘNG CỦA ĐỘI - NÉ TRÁNH:")
    .replace("PARTY ACTION BỎ CHẠY:", "HÀNH ĐỘNG CỦA ĐỘI - BỎ CHẠY:")
    .replace("cùng khai triển đòn đánh trong một combat turn.", "cùng khai triển đòn đánh trong một lượt tấn công.")
    .replace("cùng thực hiện trong một combat turn.", "cùng thực hiện trong một lượt chiến đấu.")
    .replace("cùng rút khỏi encounter trong một combat turn.", "cùng rút khỏi giao tranh trong một lượt chiến đấu.")
    .replace("vulnerable Blink/Blind/Stun", "dễ bị ảnh hưởng bởi Blink/Blind/__KEEP_STUN__")
    .replace("Stun không proc", "hiệu ứng Choáng không kích hoạt")
    .replace("Stun", "Choáng")
    .replace("__KEEP_STUN__", "Stun")
    .replace("Bleeding", "Chảy máu")
    .replace("CD ", "hồi chiêu còn ")
    .replace("tỷ lệ proc", "tỷ lệ kích hoạt")
    .replace("proc hiện tại", "kích hoạt hiện tại")
    .replace("% proc", "% tỷ lệ kích hoạt")
    .replace("proc", "kích hoạt")
    .replace("Weapon DMG", "sát thương vũ khí")
    .replace("Base DMG", "sát thương cơ bản")
    .replace("DMG", "sát thương")
    .replace(" damage", " sát thương")
    .replace(" Evasion", " Né tránh")
    .replace("Accuracy ", "Độ chính xác ")
    .replace("(CRITICAL)", "(CHÍ MẠNG)")
    .replace("Entity turn", "lượt của Entity")
    .replace("combat turn", "lượt chiến đấu")
    .replace(" turn", " lượt")
    .replace("encounter", "giao tranh")
    .replace("Party", "đội")
    .replace("Armor", "Giáp")
    .replace("buff", "hiệu ứng tăng cường")
    .replace("Forced Blink", "Blink cưỡng bức")
    .replace("blinkCounter", "bộ đếm chớp mắt")
    .replace("State=", "Trạng thái=")
    .replace("first UNOBSERVED strike", "đòn đầu khi không bị quan sát")
    .replace("UNOBSERVED", "không bị quan sát")
    .replace("OBSERVED", "được quan sát")
    .replace("ACTIVE", "đang hoạt động")
    .replace("Execution hợp lệ", "Kết liễu hợp lệ")
    .replace("narration", "lời tường thuật")
    .replace("shell", "viên đạn")
    .replace("; ", " • ")

  private const val ENTITY_HP_BONUS = 30
  private const val ENTITY_EVASION_PERCENT = 17
  private const val ENTITY_REGEN_PER_TURN = 1
  private const val JEFF_KEY = "jeff_the_killer"
  private const val JANE_KEY = "jane_the_killer"
  private const val UNIQUE_KILLER_MAX_HP = 947

  private const val JEFF_GO_TO_SLEEP_NORMAL_PERCENT = 12
  private const val JEFF_GO_TO_SLEEP_LOW_HP_PERCENT = 17
  private const val JEFF_GO_TO_SLEEP_COOLDOWN = 3
  private const val JEFF_SILENT_STALKER_DAMAGE_PERCENT = 140
  private const val JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS = 10
  private const val JEFF_SILENT_STALKER_COOLDOWN = 5
  private const val JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY = 20
  private const val JEFF_NO_SAFE_ROUTE_DURATION_TURNS = 3
  private const val JEFF_NO_SAFE_ROUTE_RETALIATION_PERCENT = 7
  private const val JEFF_NO_SAFE_ROUTE_COOLDOWN = 7

  private const val JANE_DONT_WAKE_UP_HIT_PERCENT = 6
  private const val JANE_DONT_WAKE_UP_SECOND_HIT_ACCURACY = 70
  private const val JANE_DONT_WAKE_UP_COOLDOWN = 3
  private const val JANE_BLEED_PERCENT = 2
  private const val JANE_BLEED_TURNS = 2
  private const val JANE_HUNTER_MARK_ACCURACY_BONUS = 15
  private const val JANE_HUNTER_MARK_ESCAPE_PENALTY = 15
  private const val JANE_HUNTER_MARK_DURATION_TURNS = 4
  private const val JANE_HUNTER_MARK_COOLDOWN = 6
  private const val JANE_VENGEFUL_TRIGGER_PERCENT = 20
  private const val JANE_VENGEFUL_PROC_PERCENT = 35
  private const val JANE_VENGEFUL_DAMAGE_PERCENT = 8
  private const val JANE_VENGEFUL_COOLDOWN = 4
  private const val KAI_DEVIL_WITHIN_KEY = "kai_the_devil_within"
  private const val KAI_DEVIL_WITHIN_MAX_HP = 5678
  private const val KAI_DEVIL_WITHIN_STAT_PERCENT = 70
  private const val KAI_DEVIL_WITHIN_SPARDA_PROC_PERCENT = 20
  private const val KAI_DEVIL_WITHIN_SPARDA_COOLDOWN = 3
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_PROC_PERCENT = 10
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_COOLDOWN = 3
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS = 13
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_DAMAGE_PERCENT = 70
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_PROC_PERCENT = 10
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_COOLDOWN = 3
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT = 3
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS = 3
  private const val KAI_DEVIL_WITHIN_GUNSLINGER_PROC_PERCENT = 10
  private const val KAI_DEVIL_WITHIN_GUNSLINGER_COOLDOWN = 5
  private const val KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT = 5
  private const val KAI_DEVIL_WITHIN_REGEN_PERCENT = 5
  private const val KAI_DEVIL_WITHIN_STUN_TARGET_KEY = "combat.kaiDevilWithin.stunTarget"
  private const val KAI_DEVIL_WITHIN_STUN_UNTIL_KEY = "combat.kaiDevilWithin.stunUntilEvent"
  private const val KAI_DEVIL_WITHIN_BLEED_TARGET_KEY = "combat.kaiDevilWithin.bleedTarget"
  private const val KAI_DEVIL_WITHIN_BLEED_TURNS_KEY = "combat.kaiDevilWithin.bleedTurns"
  private const val VIOLET_WARDEN_KEY = "violet_warden"
  private const val VIOLET_WARDEN_MAX_HP = 3319
  private const val VIOLET_WARDEN_ATTACK_PERCENT = 11
  private const val VIOLET_WARDEN_REGEN_PER_TURN = 33
  private const val VIOLET_WARDEN_BLOCK_PERCENT = 60
  private const val VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT = 30
  private const val VIOLET_WARDEN_CONTROL_INTERVAL_TURNS = 3
  private const val VIOLET_WARDEN_CONTROL_DAMAGE_PERCENT = 6
  private const val VIOLET_WARDEN_CONTROL_STUN_PERCENT = 50
  private const val VIOLET_WARDEN_ULTIMATE_INTERVAL_TURNS = 5
  private const val VIOLET_WARDEN_ULTIMATE_DAMAGE_PERCENT = 8
  private const val VIOLET_WARDEN_RIPOSTE_DAMAGE_PERCENT = 13
  private const val VIOLET_WARDEN_DUEL_TARGET_KEY = "combat.violetWardenDuelTargetId"
  private const val VIOLET_WARDEN_RIPOSTE_READY_KEY = "combat.violetWardenRiposteReady"
  private const val VIOLET_WARDEN_STATUS_PREFIX = "violet_warden:"
  private const val VIOLET_WARDEN_STUN_TARGET_KEY = "combat.violetWardenStunTargetId"
  private const val VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY = "combat.violetWardenStunUntilEvent"

  private const val JEFF_NO_SAFE_UNTIL_KEY = "combat.jeff.noSafeRouteUntil"
  private const val JEFF_NO_SAFE_RETALIATE_TURN_KEY = "combat.jeff.noSafeRouteRetaliateTurn"
  private const val JANE_MARK_TARGET_KEY = "combat.jane.hunterMarkTarget"
  private const val JANE_MARK_UNTIL_KEY = "combat.jane.hunterMarkUntil"
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
  private const val MONSTER_X_KEY = "monster_x"
  private const val MONSTER_X_MAX_HP = 3456
  private const val MONSTER_X_ATTACK_PERCENT = 6
  private const val MONSTER_X_REGEN_PER_TURN = 50
  private const val MONSTER_X_BLEED_INTERVAL_TURNS = 3
  private const val MONSTER_X_BLEED_PROC_PERCENT = 50
  private const val MONSTER_X_BLEED_MAX_HP_PERCENT = 3
  private const val MONSTER_X_BLEED_DURATION_TURNS = 5
  private const val MONSTER_X_STUN_INTERVAL_TURNS = 2
  private const val MONSTER_X_STUN_GATE_PERCENT = 30
  private const val MONSTER_X_STUN_PROC_PERCENT = 20
  private const val MONSTER_X_BLEED_TURNS_KEY = "combat.monsterXBleedTurns"
  private const val MONSTER_X_STUN_TURNS_KEY = "combat.monsterXStunTurns"
  private const val JOHN_DOE_KEY = "john_doe"
  private const val JOHN_DOE_MAX_HP = 1234
  private const val JOHN_DOE_ATTACK_PERCENT = 6
  private const val JOHN_DOE_REGEN_PER_TURN = 30
  private const val JOHN_DOE_POISON_INTERVAL_TURNS = 3
  private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50
  private const val JOHN_DOE_POISON_DAMAGE_PERCENT = 4
  private const val JOHN_DOE_POISONED_PREFIX = "combat.johnDoePoisoned."
  private const val JOHN_DOE_STUN_INTERVAL_TURNS = 2
  private const val JOHN_DOE_STUN_GATE_PERCENT = 30
  private const val JOHN_DOE_STUN_PROC_PERCENT = 20
  private const val JOHN_DOE_STUN_TURNS_KEY = "combat.johnDoeStunTurns"
  private const val SCP_173_KEY = "scp_173"
  private const val SCP_173_MAX_HP = 1730
  private const val SCP_173_REGEN_PER_TURN = 0
  private const val SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT = 20
  private const val SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT = 25
  private const val SCP_173_UNOBSERVED_ACTION_SPEED_PERCENT = 150
  private const val SCP_173_OBSERVED_ACTION_SPEED_PERCENT = 100
  private const val SCP_173_BLINK_THRESHOLD = 3
  private const val SCP_173_FIRST_UNOBSERVED_BONUS_PERCENT = 5
  private const val SCP_173_SNAP_STRIKE_PERCENT = 10
  private const val SCP_173_SNAP_STRIKE_STUN_PERCENT = 25
  private const val SCP_173_CONCRETE_RUSH_PERCENT = 16
  private const val SCP_173_CONCRETE_RUSH_VULNERABLE_PERCENT = 20
  private const val SCP_173_CONCRETE_RUSH_STUN_PERCENT = 35
  private const val SCP_173_CONCRETE_RUSH_COOLDOWN = 2
  private const val SCP_173_NECK_SNAP_PERCENT = 30
  private const val SCP_173_NECK_SNAP_COOLDOWN = 4
  private const val SCP_173_EXECUTION_THRESHOLD_PERCENT = 15
  private const val SCP_173_BLINK_PRESSURE_COOLDOWN = 3
  private const val SCP_173_FORCED_BLINK_PERCENT = 30
  private const val SCP_173_STATE_KEY = "combat.scp173.state"
  private const val SCP_173_ACTION_SPEED_KEY = "combat.scp173.actionSpeedPercent"
  private const val SCP_173_FIRST_STRIKE_PENDING_KEY = "combat.scp173.firstUnobservedStrikePending"
  private const val SCP_173_CONCRETE_RUSH_CD_KEY = "combat.scp173.cooldown.concreteRush"
  private const val SCP_173_NECK_SNAP_CD_KEY = "combat.scp173.cooldown.neckSnap"
  private const val SCP_173_BLINK_PRESSURE_CD_KEY = "combat.scp173.cooldown.blinkPressure"
  private const val SCP_173_BLINK_COUNTER_PREFIX = "combat.scp173.blinkCounter."
  private const val SCP_173_STATUS_PREFIX = "scp173:"
  private const val SCP_173_STATUS_EXPIRES_EVENT_KEY = "expiresEvent"
  private const val LUCIA_M4A1_COMBAT_DAMAGE = 26
  private const val LUCIA_FULL_AUTO_ROUNDS = 30
  private const val LUCIA_FULL_AUTO_BONUS_DAMAGE = 30
  private const val LUCIA_FULL_AUTO_CHANCE_PERCENT = 20
  private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = 2
  private const val LUCIA_TOO_YOUNG_TO_DIE_ROUNDS = 60
  private const val LUCIA_TOO_YOUNG_TO_DIE_BASE_CHANCE_PERCENT = 15
  private const val LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_STEP_PERCENT = 3
  private const val LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_BONUS_PERCENT = 5
  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 38
  private const val KAI_LAST_REQUIEM_DAMAGE_PERCENT = 170
  private const val KAI_LAST_REQUIEM_BLEED_TURNS = 3
  private const val KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT = 5
  private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 27
  private const val KAI_SILENT_LULLABY_DAMAGE_PERCENT = 130
  private const val KAI_SALVATION_CHANCE_PERCENT = 26
  private const val KAI_SALVATION_DAMAGE_PERCENT = 147
  private const val KAI_QUICK_STEP_CHANCE_PERCENT = 35
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
  private const val DEVIL_TRIGGER_KAI_ACTIVE_KEY = "passive.devilTrigger.kai.activeTurns"
  private const val DEVIL_TRIGGER_KAI_COOLDOWN_KEY = "passive.devilTrigger.kai.cooldownTurns"
  private const val DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY = "passive.devilTrigger.syvial.activeTurns"
  private const val DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY = "passive.devilTrigger.syvial.cooldownTurns"

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

  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false
  )

  private val profiles = listOf(
    Profile("hound", "Hound", 470, 15, 2, 8),
    Profile("clump", "Clump", 366, 17, 5, 7),
    Profile("duller", "Duller", 369, 14, 3, 6),
    Profile("deathmoth", "Deathmoth", 450, 13, 1, 7),
    Profile("hostile_faceling", "Hostile Faceling", 394, 14, 2, 7),
    Profile("false_puddle", "False Puddle", 317, 16, 4, 5),
    Profile("paintings", "Paintings", 382, 12, 1, 5),
    Profile("smiler", "Smiler", 280, 18, 2, 9),
    Profile("skin-stealer", "Skin-Stealer", 341, 18, 4, 8),
    Profile("predatory_window", "Predatory Window", 446, 17, 6, 6),
    Profile("biological_pipeline", "Biological Pipeline", 317, 18, 7, 7),
    Profile("wretch", "Wretch", 464, 16, 2, 8),
    Profile("cable_mimic", "Cable Mimic", 400, 17, 5, 8),
    Profile("the_beast_of_level_5", "The Beast of Level 5", 285, 22, 8, 9),
    Profile("hotel_corpse_lure", "Hotel Corpse Lure", 283, 18, 5, 7),
    Profile("jeff_the_killer", "Jeff the Killer", UNIQUE_KILLER_MAX_HP, 20, 4, 9),
    Profile("jane_the_killer", "Jane the Killer", UNIQUE_KILLER_MAX_HP, 20, 4, 9),
    Profile("slenderman", "Slenderman", 437, 23, 8, 10),
    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9),
    Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9),
    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9),
    Profile(SCP_173_KEY, "SCP-173", SCP_173_MAX_HP, 0, 9, 10),
    Profile(VIOLET_WARDEN_KEY, "The Violet Warden", VIOLET_WARDEN_MAX_HP, 0, 9, 9),
    Profile(KAI_DEVIL_WITHIN_KEY, "Kai - The Devil Within", KAI_DEVIL_WITHIN_MAX_HP, 0, 0, 0)
  ).associateBy { it.key }

  fun active(state: GameState): Snapshot? = decode(state)?.takeIf { it.phase == Phase.ACTIVE }

  fun start(state: GameState, entityKey: String): GameState {
    if (active(state) != null) return state
    val profile = profiles[entityKey] ?: return state
    val effective = CharacterStatEngine.effective(state, KAI_ID)
    val playerMax = effective.maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_MAX_HP; KAI_DEVIL_WITHIN_KEY -> KAI_DEVIL_WITHIN_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS }
    val enhancedEntityMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY) 200 else 0
    val snapshot = Snapshot(
      encounterId = "${state.turn.currentTurnId}:${entityKey}:${abs(seed)}",
      entityKey = entityKey,
      entityName = profile.displayName,
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
    var started = encode(state, snapshot)
    started.party.memberIds.filter { it != KAI_ID }.distinct().forEach { companionId ->
      val companion = started.characters[companionId] ?: return@forEach
      if (companion.presence == CharacterPresence.ACTIVE && companion.vitalState.currentHp > 0) {
        val blessingHp = CharacterStatEngine.devilBlessingHpBonus(started, companionId)
        started = CharacterStatEngine.setCurrentHp(started, companionId, companion.vitalState.currentHp + blessingHp)
      }
    }
    return if (entityKey == SCP_173_KEY) scp173InitializeEncounter(started) else started
  }

  fun resolve(state: GameState, actionKind: String, action: String): Resolution {
    val current = active(state) ?: return Resolution(state, handled = false)
    val baseProfile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    val profile = if (current.entityKey == KAI_DEVIL_WITHIN_KEY) {
      val kaiStats = CharacterStatEngine.effective(state, KAI_ID)
      baseProfile.copy(
        attack = max(1, CharacterStatEngine.weaponDamage(state, KAI_ID) * KAI_DEVIL_WITHIN_STAT_PERCENT / 100),
        armor = max(0, kaiStats.df * KAI_DEVIL_WITHIN_STAT_PERCENT / 100),
        aggression = max(1, kaiStats.agi * KAI_DEVIL_WITHIN_STAT_PERCENT / 100)
      )
    } else baseProfile
    val scp173NextEvent = current.eventCounter + 1
    val scp173PreparedState = if (current.entityKey == SCP_173_KEY) scp173PrepareTurn(state, scp173NextEvent) else state
    val scp173ObservedNow = current.entityKey == SCP_173_KEY && scp173Observed(scp173PreparedState)
    val requestedIntent = classify(actionKind, action)
    val monsterXStunTurns = state.metadata[MONSTER_X_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val monsterXPartyStunned = current.entityKey == MONSTER_X_KEY && monsterXStunTurns > 0
    val johnDoeStunTurns = state.metadata[JOHN_DOE_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val johnDoeTargetStunned = current.entityKey == JOHN_DOE_KEY && johnDoeStunTurns > 0
    val scp173TargetStunned = current.entityKey == SCP_173_KEY && scp173CharacterHasStatus(scp173PreparedState, KAI_ID, "STUN")
    val devilWithinKaiStunned = current.entityKey == KAI_DEVIL_WITHIN_KEY && kaiDevilWithinActionLocked(state, KAI_ID)
    val intent = if (monsterXPartyStunned || johnDoeTargetStunned || scp173TargetStunned || devilWithinKaiStunned) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
    val violetWardenKaiActionLocked = current.entityKey == VIOLET_WARDEN_KEY && violetWardenActionLocked(state, KAI_ID)
    var resolvedState = scp173PreparedState
    val devilWithinTurn = current.entityKey == KAI_DEVIL_WITHIN_KEY
    val devilWithinSpardaActive = devilWithinTurn &&
      killerSkillReady(resolvedState, "kai_devil_within.spardas_son", c.eventCounter) &&
      roll(c.copy(eventCounter = c.eventCounter + 1701), 100) < KAI_DEVIL_WITHIN_SPARDA_PROC_PERCENT
    if (devilWithinSpardaActive) {
      resolvedState = useKillerSkill(resolvedState, "kai_devil_within.spardas_son", c.eventCounter, KAI_DEVIL_WITHIN_SPARDA_COOLDOWN)
      log += "Sparda's Son kích hoạt: mọi đòn của Party trừ Kai chỉ còn 50% damage; Kai - The Devil Within nhận thêm một hành động trong Entity turn."
    }
    val devilWithinHpAtTurnStart = c.entityHp
    var devilWithinKaiDamage = 0
    var devilWithinSpardaRestored = 0
    fun applyDevilWithinSpardaReduction() {
      if (!devilWithinSpardaActive) return
      val rawOutgoingDamage = max(0, devilWithinHpAtTurnStart - c.entityHp + devilWithinSpardaRestored)
      val nonKaiDamage = max(0, rawOutgoingDamage - devilWithinKaiDamage)
      val desiredRestoration = (nonKaiDamage + 1) / 2
      val additionalRestoration = max(0, desiredRestoration - devilWithinSpardaRestored)
      if (additionalRestoration > 0) {
        val hp = min(c.entityMaxHp, c.entityHp + additionalRestoration)
        val applied = hp - c.entityHp
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        devilWithinSpardaRestored += applied
        log += "Sparda's Son triệt tiêu $applied damage từ các thành viên không phải Kai."
      }
    }
    val devilWithinHpBeforeKaiAction = c.entityHp
    var monsterXBleedTurns = state.metadata[MONSTER_X_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, MONSTER_X_BLEED_DURATION_TURNS) ?: 0
    if (monsterXPartyStunned) {
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_STUN_TURNS_KEY, 0)
      log += "Monster X Stun: toàn bộ Party mất lượt hành động hiện tại."
    }
    if (johnDoeTargetStunned) {
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_STUN_TURNS_KEY, 0)
      log += "John Doe Stun: mục tiêu bị Stun và không thể thực hiện hành động trong lượt hiện tại."
    }
    if (scp173TargetStunned) {
      log += "SCP-173 Stun: mục tiêu đang bị Stun 1 lượt; hành động hiện tại bị chặn và mục tiêu không thể duy trì quan sát."
    }
    var bleedTurns = state.metadata[KAI_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_LAST_REQUIEM_BLEED_TURNS) ?: 0
    var quickStepTurns = state.metadata[KAI_QUICK_STEP_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_QUICK_STEP_DURATION_TURNS) ?: 0
    var entityStunnedThisTurn = false
    var companionEnemyAccuracyPenalty = 0
    var irisAnalyzedTurns = state.metadata[IRIS_ANALYZED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var irisArmorBreakTurns = state.metadata[IRIS_ARMOR_BREAK_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var irisExposedTurns = state.metadata[IRIS_EXPOSED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialBleedTurns = state.metadata[SYVIAL_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var syvialDisorientTurns = state.metadata[SYVIAL_DISORIENT_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialDevilTrigger = false
    val kaiEligibleForDevilTrigger = resolvedState.characters[KAI_ID]?.let {
      it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0
    } == true
    val kaiDevilTriggerTurn = if (kaiEligibleForDevilTrigger) DevilTriggerPassive.beginTurn(
      devilTriggerState(resolvedState, KAI_ID),
      roll(c.copy(eventCounter = c.eventCounter + 401), 100)
    ) else null
    val kaiDevilTriggerActive = kaiDevilTriggerTurn?.activeThisTurn == true
    if (kaiDevilTriggerTurn != null) {
      resolvedState = withDevilTriggerState(resolvedState, KAI_ID, kaiDevilTriggerTurn.stateAtStart)
      if (kaiDevilTriggerTurn.triggeredThisTurn) log += "DEVIL TRIGGER — Sparda Core kích hoạt trong 3 turn."
      if (kaiDevilTriggerActive) {
        val healed = healCharacterForDevilTrigger(resolvedState, KAI_ID)
        resolvedState = healed.first
        val kaiMaxHp = CharacterStatEngine.effective(resolvedState, KAI_ID).maxHp
        val kaiHp = resolvedState.characters[KAI_ID]?.vitalState?.currentHp ?: c.playerHp
        c = c.copy(playerHp = kaiHp, playerMaxHp = kaiMaxHp)
        log += "DEVIL TRIGGER — Sparda Core hồi Kai +${healed.second} HP (5% Max HP; $kaiHp/$kaiMaxHp)."
      }
    }

    // Kai's probabilistic Passive is exclusive. Syvial keeps her separate canon STATE:
    // HP <= 50% or a Diệp Minh encounter, with no duration or cooldown.
    val syvialDevilTriggerTurn: DevilTriggerTurn? = null
    resolvedState = resolvedState.copy(metadata = resolvedState.metadata -
      setOf(DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY, DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY))
    val syvialTriggerCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    if (syvialTriggerCharacter != null) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
      val syvialHp = syvialTriggerCharacter.vitalState.currentHp.coerceIn(0, syvialMaxHp)
      val wasActive = resolvedState.metadata[SYVIAL_DEVIL_TRIGGER_KEY].equals("true", true)
      syvialDevilTrigger = wasActive || syvialHp * 2 <= syvialMaxHp || c.entityKey == DIEP_MINH_KEY
      if (syvialDevilTrigger) {
        resolvedState = resolvedState.copy(metadata = resolvedState.metadata + (SYVIAL_DEVIL_TRIGGER_KEY to "true"))
        if (!wasActive) log += "Syvial kích hoạt Devil Trigger theo Lucifer Core."
      }
      val regenPercent = if (syvialDevilTrigger) 4 else 2
      if (syvialHp > 0 && syvialHp < syvialMaxHp) {
        val heal = percentDamage(syvialMaxHp, regenPercent)
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, syvialHp + heal)
        val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvialHp
        log += "Lucifer Core hồi Syvial +${after - syvialHp} HP ($after/$syvialMaxHp)."
      }
    }

    when (intent) {
      Intent.READ -> {
        c = c.copy(
          telegraphRevealed = true,
          opening = min(3, c.opening + 1),
          momentum = min(3, c.momentum + 1)
        )
        log += "Kai đọc được nhịp tấn công của ${c.entityName}; sơ hở tăng lên."
      }
      Intent.EVADE -> {
        log += "PARTY ACTION NÉ TRÁNH: ${activePartyNames(resolvedState)} cùng thực hiện trong một combat turn."
        val goodCounter = c.telegraph in setOf("LUNGE", "GRAB", "RUSH")
        c = c.copy(
          range = if (c.range == RangeBand.CLOSE) RangeBand.NEAR else c.range,
          momentum = (c.momentum + if (goodCounter) 2 else 1).coerceIn(-3, 3),
          opening = min(3, c.opening + if (goodCounter) 2 else 1),
          escapeProgress = min(100, c.escapeProgress + if (goodCounter) 18 else 10),
          cover = if (c.cover == Cover.EXPOSED) Cover.PARTIAL else c.cover
        )
        log += if (goodCounter) "Kai né đúng telegraph, cướp thế chủ động." else "Kai đổi góc và giảm áp lực trực diện."
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
        log += "Kai tái định vị, kéo giãn khoảng cách và tìm vật che chắn."
      }
      Intent.GUARD -> {
        c = c.copy(cover = Cover.HARD, momentum = min(3, c.momentum + 1), opening = min(3, c.opening + 1))
        log += "Kai khóa tư thế phòng thủ và ép ${c.entityName} phải lộ hướng tấn công."
      }
      Intent.ESCAPE -> {
        log += "PARTY ACTION BỎ CHẠY: ${activePartyNames(resolvedState)} cùng rút khỏi encounter trong một combat turn."
        val baseGain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
        var escapePenalty = 0

        if (c.entityKey == JEFF_KEY) {
          val activeUntil = resolvedState.metadata[JEFF_NO_SAFE_UNTIL_KEY]?.toIntOrNull() ?: -1
          var noSafeRouteActive = activeUntil >= c.eventCounter
          if (!noSafeRouteActive && killerSkillReady(resolvedState, "jeff.no_safe_route", c.eventCounter)) {
            noSafeRouteActive = true
            resolvedState = withCombatCounter(resolvedState, JEFF_NO_SAFE_UNTIL_KEY, c.eventCounter + JEFF_NO_SAFE_ROUTE_DURATION_TURNS - 1)
            resolvedState = useKillerSkill(resolvedState, "jeff.no_safe_route", c.eventCounter, JEFF_NO_SAFE_ROUTE_COOLDOWN)
            log += "No Safe Route: Jeff khóa tuyến rút trong $JEFF_NO_SAFE_ROUTE_DURATION_TURNS turn; -$JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY điểm Escape."
          }
          if (noSafeRouteActive) escapePenalty += JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY
        }

        if (c.entityKey == JANE_KEY) {
          val markTarget = resolvedState.metadata[JANE_MARK_TARGET_KEY].orEmpty()
          val markUntil = resolvedState.metadata[JANE_MARK_UNTIL_KEY]?.toIntOrNull() ?: -1
          if (markTarget == KAI_ID && markUntil >= c.eventCounter) {
            escapePenalty += JANE_HUNTER_MARK_ESCAPE_PENALTY
            log += "Hunter's Mark: Kai chịu -$JANE_HUNTER_MARK_ESCAPE_PENALTY điểm Escape."
          }
        }

        val gain = max(0, baseGain - escapePenalty)
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        if (c.entityKey == JEFF_KEY && escapePenalty >= JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY && c.escapeProgress < 100) {
          resolvedState = withCombatCounter(resolvedState, JEFF_NO_SAFE_RETALIATE_TURN_KEY, c.eventCounter)
        }
        log += "Kai dồn ưu thế vào đường thoát (${c.escapeProgress}%)."
      }
      Intent.ATTACK -> {
        log += "PARTY ACTION TẤN CÔNG: ${activePartyNames(resolvedState)} cùng khai triển đòn đánh trong một combat turn."
        val roll = roll(c, 100)
        val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 13), 100)
        val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT
        // VIOLET_WARDEN_KAI_STUN_GATE_V1
        if (violetWardenKaiActionLocked) {
          log += "Violet Warden STUN: Kai mất lượt hành động cá nhân; các thành viên ACTIVE khác vẫn tiếp tục lệnh TẤN CÔNG."
        } else if (roll < hitChance && !entityEvaded) {
          val variance = 2 + roll(c.copy(eventCounter = c.eventCounter + 17), 7)
          val effective = CharacterStatEngine.effective(state, KAI_ID)
          val weaponDamage = CharacterStatEngine.weaponDamage(state, KAI_ID)
          val critChance = CombatStatMath.critChancePercent(effective.crit)
          val critical = roll(c.copy(eventCounter = c.eventCounter + 23), 100) < critChance
          val base = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val normalized = if (critical) base * 3 / 2 else base
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((DevilTriggerPassive.damage(min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100)), kaiDevilTriggerActive)), scp173ObservedNow) else (DevilTriggerPassive.damage(min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100)), kaiDevilTriggerActive))
          val hp = max(0, c.entityHp - damage)
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
          log += if (entityEvaded) "${c.entityName} né đòn (17% evasion) và giành lại áp lực." else "Đòn đánh trượt; ${c.entityName} giành lại áp lực."
        }
        if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiAction - c.entityHp)
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
            val potential = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 100, profile.armor)
            val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((min(c.entityHp, potential)), scp173ObservedNow) else (min(c.entityHp, potential))
            val hp = max(0, c.entityHp - damage)
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
            val potential = DevilTriggerPassive.damage(companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 100, profile.armor), syvialDevilTrigger)
            val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((min(c.entityHp, potential)), scp173ObservedNow) else (min(c.entityHp, potential))
            val hp = max(0, c.entityHp - damage)
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

    val devilWithinHpBeforeKaiBleed = c.entityHp
    if (c.entityHp > 0 && bleedTurns > 0) {
      val bleedDamage = DevilTriggerPassive.damage(percentDamage(c.entityMaxHp, KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT), kaiDevilTriggerActive)
      val hp = max(0, c.entityHp - bleedDamage)
      bleedTurns = max(0, bleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ The Last Requiem gây -$bleedDamage HP (${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $bleedTurns turn."
    }

    if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiBleed - c.entityHp)

    if (c.entityHp > 0 && syvialBleedTurns > 0) {
      val bleedDamage = DevilTriggerPassive.damage(percentDamage(c.entityMaxHp, 4), syvialDevilTrigger)
      val hp = max(0, c.entityHp - bleedDamage)
      syvialBleedTurns = max(0, syvialBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Crimson Guillotine gây -$bleedDamage HP (4% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $syvialBleedTurns turn."
    }

    applyDevilWithinSpardaReduction()
    if (c.entityHp <= 0) {
      val persisted = encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn), c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = EntityLootEngine.onDefeat(clearCombatOnly(persisted), c.encounterId, lootRng)
      return Resolution(cleared, true, localizeCombatNarration(log.joinToString(" ")) + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }
    if (c.escapeProgress >= 100) {
      val persisted = encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn), c.copy(phase = Phase.RESOLVED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, localizeCombatNarration(log.joinToString(" ")) + " Kai cắt được truy đuổi và thoát khỏi encounter.", escaped = true)
    }

    // PARTY_ATTACK_GCO_GATE_V1
    if (intent == Intent.ATTACK && !violetWardenKaiActionLocked) {
    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0) {
      // Baseline inactive contract remains KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 24 * 10.
      val perShotDamage = DevilTriggerPassive.damage(KAI_GUILTY_CROWN_DAMAGE_PER_SHOT, kaiDevilTriggerActive)
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * perShotDamage
      val appliedTotalDamage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(totalDamage, scp173ObservedNow) else totalDamage
      val hpBeforeGuiltyCrown = c.entityHp
      val hp = max(0, c.entityHp - appliedTotalDamage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      if (devilWithinTurn) devilWithinKaiDamage += max(0, hpBeforeGuiltyCrown - hp)
      log += "Guilty Crown Override tự động kích hoạt ở combat turn ${c.eventCounter}: $KAI_GUILTY_CROWN_SHOTS/" +
        "$KAI_GUILTY_CROWN_SHOTS phát trúng liên tiếp, Accuracy $KAI_GUILTY_CROWN_ACCURACY_PERCENT%, bỏ qua toàn bộ hiệu ứng né; " +
        "mỗi phát -$perShotDamage HP, " +
        (if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP" else "tổng -$totalDamage HP") +
        " (${c.entityHp}/${c.entityMaxHp})."
      applyDevilWithinSpardaReduction()
    if (c.entityHp <= 0) {
        val persisted = encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn), c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
        val cleared = clearCombatOnly(persisted)
        return Resolution(cleared, true, localizeCombatNarration(log.joinToString(" ")) + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
      }
    }

    }

    val isGuiltyCrownTurn = intent == Intent.ATTACK && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0 && !violetWardenKaiActionLocked
    val devilWithinHpBeforeKaiSkills = c.entityHp
    if (c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)

      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && !violetWardenKaiActionLocked && roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {
        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive)), scp173ObservedNow) else (DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive))
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 22))
        bleedTurns = KAI_LAST_REQUIEM_BLEED_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
        log += "The Last Requiem tự động kích hoạt: Kai ghì SRU-SG bằng hai tay và khai hỏa 4 shell quỷ lực theo nhịp giật kiểm soát, đặt chùm đạn cắt qua các điểm neo vận động ở vai; ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."
      }

      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && !violetWardenKaiActionLocked && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {
        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive)), scp173ObservedNow) else (DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive))
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
        entityStunnedThisTurn = true
        log += "Silent Lullaby tự động kích hoạt: Kai bật lên cao, hạ nòng SRU-SG và khai hỏa 4 shell quỷ lực theo nhịp giật kiểm soát vào cùng vùng trọng yếu trên ngực; ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."
      }

      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && !violetWardenKaiActionLocked && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {
        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive)), scp173ObservedNow) else (DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive))
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 16))
        log += "Salvation tự động kích hoạt: Kai bứt tốc qua góc chết, ghì SRU-SG bằng hai tay ở cự ly gần và khai hỏa nhanh 2 shell quỷ lực, ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."
      }

      if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn && !violetWardenKaiActionLocked && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {
        quickStepTurns = KAI_QUICK_STEP_DURATION_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
        log += "Quick Step tự động kích hoạt: Kai đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU-SG ở tư thế sẵn bắn, +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${KAI_QUICK_STEP_DURATION_TURNS} turn."
      }
    }

    if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiSkills - c.entityHp)

    applyDevilWithinSpardaReduction()
    if (c.entityHp <= 0) {
      val persisted = encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn), c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, localizeCombatNarration(log.joinToString(" ")) + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
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
      val irisWeapon = CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID)
      val irisArmor = when {
        irisExposedTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        irisArmorBreakTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        else -> profile.armor
      }
      val irisUltimate = intent == Intent.ATTACK && c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0
      if (irisUltimate) {
        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((companionSkillDamage(irisWeapon, 300, irisArmor)), scp173ObservedNow) else (companionSkillDamage(irisWeapon, 300, irisArmor))
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 30))
        irisExposedTurns = 2
        resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
        log += "ARGUS // Thousandfold Execution: 12 phát luân phiên, 300% DMG = -$damage HP; Fully Exposed 2 turn."
      } else if (intent == Intent.ATTACK) {
        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {
          val percent = if (irisAnalyzedTurns > 0) 170 else 155
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((companionSkillDamage(irisWeapon, percent, irisArmor)), scp173ObservedNow) else (companionSkillDamage(irisWeapon, percent, irisArmor))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 14))
          log += "Twosome Time tự động kích hoạt: 2 phát chéo góc, $percent% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 20 && c.entityHp > 0) {
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((companionSkillDamage(irisWeapon, 145, irisArmor)), scp173ObservedNow) else (companionSkillDamage(irisWeapon, 145, irisArmor))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
          log += "Rain Storm tự động kích hoạt: 6 phát khi đổi góc trên không, 145% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 20 && c.entityHp > 0) {
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((companionSkillDamage(irisWeapon, 185, irisArmor)), scp173ObservedNow) else (companionSkillDamage(irisWeapon, 185, irisArmor))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
          irisArmorBreakTurns = 2
          resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
          log += "Honeycomb Fire tự động kích hoạt: 8 phát tập trung, 185% DMG = -$damage HP; Armor Break 20% trong 2 turn."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 25 && c.entityHp > 0) {
          val chargedArmor = armorAfterIgnore(profile.armor, 35)
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((companionSkillDamage(irisWeapon, 175, chargedArmor)), scp173ObservedNow) else (companionSkillDamage(irisWeapon, 175, chargedArmor))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 20))
          log += "Charged Shot tự động kích hoạt: 175% DMG = -$damage HP, bỏ qua 35% Armor."
        }
      }
    }

    if (syvialActive && c.entityHp > 0) {
      val syvialWeapon = CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID)
      val dtMultiplier = if (syvialDevilTrigger) 500 else 100
      fun syvialDamage(percent: Int, armor: Int): Int = companionSkillDamage(syvialWeapon, (percent * dtMultiplier + 99) / 100, armor)
      val syvialUltimate = intent == Intent.ATTACK && syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0
      if (syvialUltimate) {
        val damagePerHit = DevilTriggerPassive.damage(10, syvialDevilTrigger)
        val blessedDamage = min(c.entityHp, (24 * damagePerHit * 105 + 99) / 100)
        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(blessedDamage, scp173ObservedNow) else blessedDamage
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
        log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x $damagePerHit HP = -$damage HP; bỏ qua Evasion."
      } else if (intent == Intent.ATTACK) {
        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((syvialDamage(175, armorAfterIgnore(profile.armor, 20))), scp173ObservedNow) else (syvialDamage(175, armorAfterIgnore(profile.armor, 20)))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Rift Sever tự động kích hoạt: Spatial Shift + GodKiller, 175% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 223), 100) < 20 && c.entityHp > 0) {
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((syvialDamage(190, profile.armor)), scp173ObservedNow) else (syvialDamage(190, profile.armor))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialBleedTurns = 3
          resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
          log += "Crimson Guillotine tự động kích hoạt: 190% DMG = -$damage HP; Bleeding 3 turn x 4% Max HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 239), 100) < 20 && c.entityHp > 0) {
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((syvialDamage(155, profile.armor)), scp173ObservedNow) else (syvialDamage(155, profile.armor))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          entityStunnedThisTurn = true
          log += "Lucifer Breaker tự động kích hoạt: 155% DMG = -$damage HP; Entity bị Stun trong phản ứng hiện tại."
        }
        if (syvialDevilTrigger && roll(c.copy(eventCounter = c.eventCounter + 251), 100) < 20 && c.entityHp > 0) {
          val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((syvialDamage(210, profile.armor)), scp173ObservedNow) else (syvialDamage(210, profile.armor))
          val hp = max(0, c.entityHp - damage)
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

    // LUCIA_FULL_AUTO_BURST_V1: every second ATTACK turn gets one 20% proc check.
    // A successful proc expends a 30-round burst as one skill event. Entity Evasion gates the
    // burst once, matching the existing companion-skill resolution model rather than rolling a
    // second hidden combat turn for every bullet.
    val luciaFullAutoActive = activePartyCharacter(resolvedState, LUCIA_ID) != null
    val luciaFullAutoEligible = intent == Intent.ATTACK &&
      c.eventCounter % LUCIA_FULL_AUTO_INTERVAL_TURNS == 0
    if (luciaFullAutoActive && luciaFullAutoEligible && c.entityHp > 0) {
      val luciaFullAutoProc = roll(c.copy(eventCounter = c.eventCounter + 601), 100)
      if (luciaFullAutoProc < LUCIA_FULL_AUTO_CHANCE_PERCENT) {
        val luciaFullAutoEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 607), 100)
        if (luciaFullAutoEvasionRoll >= ENTITY_EVASION_PERCENT) {
          val luciaBaseDamage = max(
            LUCIA_M4A1_COMBAT_DAMAGE,
            CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID)
          )
          val luciaRawPerBullet = LUCIA_FULL_AUTO_BONUS_DAMAGE + luciaBaseDamage
          val luciaPerBulletAfterArmor = max(1, luciaRawPerBullet - profile.armor)
          val luciaRawBurstDamage = (LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor * 105 + 99) / 100
          val luciaResolvedBurstDamage = if (c.entityKey == SCP_173_KEY) {
            scp173DirectDamage(luciaRawBurstDamage, scp173ObservedNow)
          } else {
            luciaRawBurstDamage
          }
          val luciaBurstDamage = min(c.entityHp, luciaResolvedBurstDamage)
          val luciaBurstHp = max(0, c.entityHp - luciaBurstDamage)
          c = c.copy(
            entityHp = luciaBurstHp,
            entityCondition = condition(luciaBurstHp, c.entityMaxHp),
            noise = min(100, c.noise + 45)
          )
          log += "Lucia \"Lục\" tự kích hoạt M4A1 Full Auto Burst: " +
            "$LUCIA_FULL_AUTO_ROUNDS viên, mỗi viên $LUCIA_FULL_AUTO_BONUS_DAMAGE + Base DMG ($luciaBaseDamage) trước Armor; " +
            "tổng -$luciaBurstDamage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          log += "${c.entityName} né M4A1 Full Auto Burst của Lucia (${ENTITY_EVASION_PERCENT}% Evasion)."
        }
      }
    }

    // VIOLET_WARDEN_BLOCK_V1: one directional guard resolution for the Party ATTACK package.
    if (c.entityKey == VIOLET_WARDEN_KEY && intent == Intent.ATTACK) {
      val damageTaken = max(0, current.entityHp - c.entityHp)
      if (damageTaken > 0 && roll(c.copy(eventCounter = c.eventCounter + 1201), 100) < VIOLET_WARDEN_BLOCK_PERCENT) {
        val restored = max(1, (damageTaken * VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT + 99) / 100)
        val blockedHp = min(c.entityMaxHp, c.entityHp + restored)
        val actualRestored = blockedHp - c.entityHp
        c = c.copy(entityHp = blockedHp, entityCondition = condition(blockedHp, c.entityMaxHp))
        resolvedState = violetWardenMetadata(resolvedState, VIOLET_WARDEN_RIPOSTE_READY_KEY, "true")
        log += "Violet Guard: The Violet Warden Block thành công; giảm $VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT% gói direct ATTACK (-$actualRestored damage được triệt tiêu) và chuẩn bị Violet Riposte."
      }
    }

    // LUCIA_TOO_YOUNG_TO_DIE_V1: independent AUTO check every combat turn.
    // The 60-round magazine resolves as one skill event and therefore uses one shared Entity
    // Evasion gate, matching Lucia's existing full-auto resolution contract.
    val luciaTooYoungCharacter = activePartyCharacter(resolvedState, LUCIA_ID)
    if (luciaTooYoungCharacter != null && c.entityHp > 0) {
      val luciaTooYoungMaxHp = CharacterStatEngine.effective(resolvedState, LUCIA_ID).maxHp
      val luciaTooYoungCurrentHp = luciaTooYoungCharacter.vitalState.currentHp.coerceIn(0, luciaTooYoungMaxHp)
      val luciaTooYoungChance = luciaTooYoungToDieTriggerChancePercent(luciaTooYoungCurrentHp, luciaTooYoungMaxHp)
      val luciaTooYoungProc = roll(c.copy(eventCounter = c.eventCounter + 641), 100)
      if (luciaTooYoungProc < luciaTooYoungChance) {
        val luciaTooYoungEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 647), 100)
        if (luciaTooYoungEvasionRoll >= ENTITY_EVASION_PERCENT) {
          val luciaBaseDamage = max(
            LUCIA_M4A1_COMBAT_DAMAGE,
            CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID)
          )
          val luciaRawPerBullet = (luciaBaseDamage * 105 + 99) / 100
          val luciaPerBulletDamage = companionSkillDamage(luciaBaseDamage, 105, profile.armor)
          val luciaRawBurstDamage = LUCIA_TOO_YOUNG_TO_DIE_ROUNDS * luciaPerBulletDamage
          val luciaResolvedBurstDamage = if (c.entityKey == SCP_173_KEY) {
            scp173DirectDamage(luciaRawBurstDamage, scp173ObservedNow)
          } else {
            luciaRawBurstDamage
          }
          val luciaBurstDamage = min(c.entityHp, luciaResolvedBurstDamage)
          val luciaBurstHp = max(0, c.entityHp - luciaBurstDamage)
          c = c.copy(
            entityHp = luciaBurstHp,
            entityCondition = condition(luciaBurstHp, c.entityMaxHp),
            noise = min(100, c.noise + 60)
          )
          log += "Lucia \"Lục\" tự kích hoạt Too Young To Die: " +
            "$LUCIA_TOO_YOUNG_TO_DIE_ROUNDS viên liên tục, mỗi viên Base DMG +5% = $luciaRawPerBullet trước Armor/buff ngoài kỹ năng; " +
            "tỷ lệ proc hiện tại $luciaTooYoungChance%; tổng -$luciaBurstDamage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          log += "${c.entityName} né Too Young To Die của Lucia (${ENTITY_EVASION_PERCENT}% Evasion)."
        }
      }
    }

    applyDevilWithinSpardaReduction()
    if (c.entityHp <= 0) {
      val persisted = encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn), c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, localizeCombatNarration(log.joinToString(" ")) + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }

    if (c.entityKey == JOHN_DOE_KEY && johnDoePoisonedIds(resolvedState).isNotEmpty()) {
      val poison = damageJohnDoePoisoned(resolvedState, JOHN_DOE_POISON_DAMAGE_PERCENT)
      resolvedState = poison.state
      c = c.copy(playerHp = poison.kaiHp)
      log += "Poison John Doe: từng mục tiêu đang bị ảnh hưởng mất ${JOHN_DOE_POISON_DAMAGE_PERCENT}% Max HP riêng biệt. ${poison.summary}."
    }

    if (c.entityKey == MONSTER_X_KEY && monsterXBleedTurns > 0) {
      val bleed = damageActivePartyByPercent(resolvedState, MONSTER_X_BLEED_MAX_HP_PERCENT)
      resolvedState = bleed.state
      c = c.copy(playerHp = bleed.kaiHp)
      monsterXBleedTurns = max(0, monsterXBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_BLEED_TURNS_KEY, monsterXBleedTurns)
      log += "Monster X Bleeding: toàn bộ nhân vật ACTIVE -${MONSTER_X_BLEED_MAX_HP_PERCENT}% Max HP; còn $monsterXBleedTurns lượt. ${bleed.summary}."
    }

    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.
    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == SCP_173_KEY) {
      if (scp173Observed(resolvedState)) {
        val pressureCooldown = scp173Cooldown(resolvedState, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN)
        if (pressureCooldown <= 0) {
          var pressureState = resolvedState
          val targets = scp173LivePartyIds(pressureState)
            .sortedWith(compareByDescending<String> { scp173BlinkCounter(pressureState, it) }.thenBy { it })
            .take(2)
          val details = mutableListOf<String>()
          targets.forEachIndexed { index, characterId ->
            val character = pressureState.characters[characterId] ?: return@forEachIndexed
            val advanced = scp173BlinkCounter(pressureState, characterId) + 1
            val thresholdBlink = advanced >= SCP_173_BLINK_THRESHOLD
            val forcedBlink = roll(c.copy(eventCounter = c.eventCounter + 733 + index * 17), 100) < SCP_173_FORCED_BLINK_PERCENT
            if (thresholdBlink || forcedBlink) {
              pressureState = scp173WithBlinkCounter(pressureState, characterId, 0)
              pressureState = scp173ApplyTransientStatus(pressureState, characterId, "BLINK", c.eventCounter + 1)
              details += "${character.name} Blink"
            } else {
              pressureState = scp173WithBlinkCounter(pressureState, characterId, advanced)
              details += "${character.name} blinkCounter=$advanced"
            }
          }
          pressureState = scp173WithCooldown(pressureState, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN)
          pressureState = scp173RecomputeObservation(pressureState)
          resolvedState = pressureState
          log += "Blink Pressure: tăng blinkCounter +1 cho ${targets.size} mục tiêu, Forced Blink ${SCP_173_FORCED_BLINK_PERCENT}%; ${if (details.isEmpty()) "không có mục tiêu sống hợp lệ" else details.joinToString("; ")}. State=${resolvedState.metadata[SCP_173_STATE_KEY]}."
        } else {
          log += "DON'T BLINK: SCP-173 đang OBSERVED, bất động hoàn toàn và không thể di chuyển, áp sát hay tấn công; Blink Pressure còn CD $pressureCooldown."
        }
      } else {
        val scp173ActionTargets = entityCombatActionTargets(resolvedState)
        val targetId = scp173TargetId(resolvedState)
        if (targetId == null) {
          log += "SCP-173 ở UNOBSERVED nhưng không còn mục tiêu ACTIVE hợp lệ."
        } else {
          val target = resolvedState.characters.getValue(targetId)
          val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
          val firstStrike = resolvedState.metadata[SCP_173_FIRST_STRIKE_PENDING_KEY].equals("true", true)
          val bonusPercent = if (firstStrike) SCP_173_FIRST_UNOBSERVED_BONUS_PERCENT else 0
          val vulnerable = scp173CharacterHasStatus(resolvedState, targetId, "BLINK", "BLIND", "STUN")
          val neckCooldown = scp173Cooldown(resolvedState, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN)
          val rushCooldown = scp173Cooldown(resolvedState, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN)

          if (c.range == RangeBand.CLOSE && neckCooldown <= 0) {
            val execution = before * 100 <= targetMaxHp * SCP_173_EXECUTION_THRESHOLD_PERCENT
            val requested = percentDamage(targetMaxHp, SCP_173_NECK_SNAP_PERCENT + bonusPercent)
            val damage = if (execution) before else min(before, requested)
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
            resolvedState = scp173WithCooldown(resolvedState, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN)
            resolvedState = scp173ConsumeFirstStrike(resolvedState)
            if (targetId == KAI_ID) c = c.copy(playerHp = scp173KaiHp(resolvedState), momentum = max(-3, c.momentum - 1))
            log += if (execution) {
              "Neck Snap: ${target.name} ở ${before}/${targetMaxHp} HP (<=${SCP_173_EXECUTION_THRESHOLD_PERCENT}%), Execution hợp lệ; HP được cập nhật về 0 trước narration."
            } else {
              "Neck Snap: ${target.name} -$damage HP (${SCP_173_NECK_SNAP_PERCENT}% Max HP${if (firstStrike) " + ${SCP_173_FIRST_UNOBSERVED_BONUS_PERCENT}% first UNOBSERVED strike" else ""}); CD ${SCP_173_NECK_SNAP_COOLDOWN}."
            }
          } else if (rushCooldown <= 0) {
            val basePercent = if (vulnerable) SCP_173_CONCRETE_RUSH_VULNERABLE_PERCENT else SCP_173_CONCRETE_RUSH_PERCENT
            val totalPercent = basePercent + bonusPercent
            val damage = min(before, percentDamage(targetMaxHp, totalPercent))
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
            resolvedState = scp173WithCooldown(resolvedState, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN)
            resolvedState = scp173ConsumeFirstStrike(resolvedState)
            val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
            var stunned = false
            if (after > 0 && roll(c.copy(eventCounter = c.eventCounter + 769), 100) < SCP_173_CONCRETE_RUSH_STUN_PERCENT) {
              resolvedState = scp173ApplyTransientStatus(resolvedState, targetId, "STUN", c.eventCounter + 1)
              stunned = true
            }
            c = c.copy(range = RangeBand.CLOSE, playerHp = if (targetId == KAI_ID) scp173KaiHp(resolvedState) else c.playerHp, momentum = max(-3, c.momentum - 1))
            log += "Concrete Rush: ${target.name} -$damage HP ($totalPercent% Max HP${if (vulnerable) ", vulnerable Blink/Blind/Stun" else ""}); CD ${SCP_173_CONCRETE_RUSH_COOLDOWN}; ${if (stunned) "Stun 1 lượt (${SCP_173_CONCRETE_RUSH_STUN_PERCENT}% proc)" else "Stun không proc"}."
          } else {
            val totalPercent = SCP_173_SNAP_STRIKE_PERCENT + bonusPercent
            val damage = min(before, percentDamage(targetMaxHp, totalPercent))
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
            resolvedState = scp173ConsumeFirstStrike(resolvedState)
            val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
            var stunned = false
            if (after > 0 && roll(c.copy(eventCounter = c.eventCounter + 797), 100) < SCP_173_SNAP_STRIKE_STUN_PERCENT) {
              resolvedState = scp173ApplyTransientStatus(resolvedState, targetId, "STUN", c.eventCounter + 1)
              stunned = true
            }
            c = c.copy(
              range = if (c.range == RangeBand.FAR) RangeBand.NEAR else c.range,
              playerHp = if (targetId == KAI_ID) scp173KaiHp(resolvedState) else c.playerHp,
              momentum = max(-3, c.momentum - 1)
            )
            log += "Snap Strike: ${target.name} -$damage HP ($totalPercent% Max HP); ${if (stunned) "Stun 1 lượt (${SCP_173_SNAP_STRIKE_STUN_PERCENT}% proc)" else "Stun không proc"}."
          }

          log += "ENTITY ACTION 1/${scp173ActionTargets.size} -> ${target.name}: SCP-173 primary UNOBSERVED action resolved."
          val scp173ExtraTargets = scp173ActionTargets.filter { it != targetId }
          scp173ExtraTargets.forEachIndexed { extraIndex, extraTargetId ->
            val extraTarget = resolvedState.characters[extraTargetId] ?: return@forEachIndexed
            val extraMaxHp = CharacterStatEngine.effective(resolvedState, extraTargetId).maxHp
            val extraBefore = extraTarget.vitalState.currentHp.coerceIn(0, extraMaxHp)
            if (extraBefore <= 0) return@forEachIndexed
            val extraDamage = min(extraBefore, percentDamage(extraMaxHp, SCP_173_SNAP_STRIKE_PERCENT))
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, extraTargetId, extraBefore - extraDamage)
            val extraAfter = resolvedState.characters[extraTargetId]?.vitalState?.currentHp ?: max(0, extraBefore - extraDamage)
            var extraStunned = false
            if (extraAfter > 0 && roll(c.copy(eventCounter = c.eventCounter + 823 + extraIndex * 19), 100) < SCP_173_SNAP_STRIKE_STUN_PERCENT) {
              resolvedState = scp173ApplyTransientStatus(resolvedState, extraTargetId, "STUN", c.eventCounter + 1)
              extraStunned = true
            }
            log += "ENTITY ACTION ${extraIndex + 2}/${scp173ActionTargets.size} -> ${extraTarget.name}: HIT. SCP-173 Snap Strike -$extraDamage HP (${SCP_173_SNAP_STRIKE_PERCENT}% Max HP); ${if (extraStunned) "Stun 1 lượt" else "Stun không proc"}."
          }
          log += "ENTITY ACTION BUDGET: SCP-173 UNOBSERVED = ${scp173ActionTargets.size}; mỗi combatant nhận tối đa một direct action trong Entity turn."
        }
      }
    } else if (c.entityKey == KAI_DEVIL_WITHIN_KEY) {
      val targets = entityCombatActionTargets(resolvedState)
      val bleedTargetId = resolvedState.metadata[KAI_DEVIL_WITHIN_BLEED_TARGET_KEY].orEmpty()
      var bleedTurnsLeft = resolvedState.metadata[KAI_DEVIL_WITHIN_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS) ?: 0
      if (bleedTargetId.isNotBlank() && bleedTurnsLeft > 0) {
        val target = resolvedState.characters[bleedTargetId]
        if (target != null && target.vitalState.currentHp > 0) {
          val maxHp = CharacterStatEngine.effective(resolvedState, bleedTargetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, maxHp)
          val damage = min(before, percentDamage(maxHp, KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT))
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, bleedTargetId, before - damage)
          bleedTurnsLeft--
          resolvedState = withCombatCounter(resolvedState, KAI_DEVIL_WITHIN_BLEED_TURNS_KEY, bleedTurnsLeft)
          log += "Dead Silence Bleeding: ${target.name} -$damage HP (${KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT}% Max HP); còn $bleedTurnsLeft lượt."
        }
      }

      if (targets.isEmpty()) {
        log += "Kai - The Devil Within không còn mục tiêu chiến đấu hợp lệ."
      } else {
        val kaiStats = CharacterStatEngine.effective(resolvedState, KAI_ID)
        val baseDamage = max(1, CharacterStatEngine.weaponDamage(resolvedState, KAI_ID) * KAI_DEVIL_WITHIN_STAT_PERCENT / 100)
        val critChance = max(0, CombatStatMath.critChancePercent(kaiStats.crit) * KAI_DEVIL_WITHIN_STAT_PERCENT / 100)
        val primaryId = targets[(c.eventCounter + targets.size) % targets.size]

        fun hitTarget(targetId: String, requested: Int, label: String, seedOffset: Int) {
          val target = resolvedState.characters[targetId] ?: return
          val maxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, maxHp)
          if (before <= 0) return
          val critical = roll(c.copy(eventCounter = c.eventCounter + seedOffset), 100) < critChance
          val damage = min(before, if (critical) requested * 3 / 2 else requested)
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
          val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
          if (targetId == KAI_ID) c = c.copy(playerHp = after, playerMaxHp = maxHp)
          log += "$label: ${target.name} -$damage HP${if (critical) " (CRITICAL)" else ""} ($after/$maxHp)."
        }

        val redRosary = killerSkillReady(resolvedState, "kai_devil_within.red_rosary", c.eventCounter) &&
          roll(c.copy(eventCounter = c.eventCounter + 1721), 100) < KAI_DEVIL_WITHIN_RED_ROSARY_PROC_PERCENT
        val deadSilence = !redRosary && killerSkillReady(resolvedState, "kai_devil_within.dead_silence", c.eventCounter) &&
          roll(c.copy(eventCounter = c.eventCounter + 1741), 100) < KAI_DEVIL_WITHIN_DEAD_SILENCE_PROC_PERCENT
        val gunslinger = !redRosary && !deadSilence && killerSkillReady(resolvedState, "kai_devil_within.gunslinger", c.eventCounter) &&
          roll(c.copy(eventCounter = c.eventCounter + 1753), 100) < KAI_DEVIL_WITHIN_GUNSLINGER_PROC_PERCENT

        when {
          redRosary -> {
            val perRound = max(1, baseDamage * KAI_DEVIL_WITHIN_RED_ROSARY_DAMAGE_PERCENT / 100)
            hitTarget(primaryId, perRound * KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS, "Red Rosary — ${KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS} viên x 0.7 Base DMG", 1777)
            resolvedState = withCombatText(resolvedState, KAI_DEVIL_WITHIN_STUN_TARGET_KEY, primaryId)
            resolvedState = withCombatCounter(resolvedState, KAI_DEVIL_WITHIN_STUN_UNTIL_KEY, c.eventCounter + 1)
            resolvedState = useKillerSkill(resolvedState, "kai_devil_within.red_rosary", c.eventCounter, KAI_DEVIL_WITHIN_RED_ROSARY_COOLDOWN)
            log += "Red Rosary gây STUN 1 lượt cho ${resolvedState.characters[primaryId]?.name ?: primaryId}."
          }
          deadSilence -> {
            hitTarget(primaryId, baseDamage, "Dead Silence bắn trúng điểm yếu", 1789)
            resolvedState = withCombatText(resolvedState, KAI_DEVIL_WITHIN_BLEED_TARGET_KEY, primaryId)
            resolvedState = withCombatCounter(resolvedState, KAI_DEVIL_WITHIN_BLEED_TURNS_KEY, KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS)
            resolvedState = useKillerSkill(resolvedState, "kai_devil_within.dead_silence", c.eventCounter, KAI_DEVIL_WITHIN_DEAD_SILENCE_COOLDOWN)
            log += "Dead Silence gây Bleeding ${KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS} lượt x ${KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT}% Max HP."
          }
          gunslinger -> {
            targets.forEachIndexed { index, targetId ->
              val maxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
              hitTarget(targetId, percentDamage(maxHp, KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT), "Gunslinger", 1801 + index * 17)
            }
            resolvedState = useKillerSkill(resolvedState, "kai_devil_within.gunslinger", c.eventCounter, KAI_DEVIL_WITHIN_GUNSLINGER_COOLDOWN)
            log += "Gunslinger quét toàn bộ mục tiêu, mỗi mục tiêu chịu ${KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT}% Max HP."
          }
          else -> hitTarget(primaryId, baseDamage, "Kai - The Devil Within tấn công", 1831)
        }

        if (devilWithinSpardaActive) {
          val extraTargets = entityCombatActionTargets(resolvedState)
          val extraId = extraTargets.firstOrNull()
          if (extraId != null) hitTarget(extraId, baseDamage, "Sparda's Son — hành động thêm", 1861)
        }
      }

      if (c.entityHp > 0 && c.eventCounter % 2 == 0) {
        val heal = percentDamage(c.entityMaxHp, KAI_DEVIL_WITHIN_REGEN_PERCENT)
        val hp = min(c.entityMaxHp, c.entityHp + heal)
        log += "Nội tại Quỷ giới hồi +${hp - c.entityHp} HP (${KAI_DEVIL_WITHIN_REGEN_PERCENT}% Max HP; $hp/${c.entityMaxHp})."
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      }
    } else if (c.entityKey == VIOLET_WARDEN_KEY) {
      val duelTargetId = violetWardenDuelTarget(resolvedState)
      if (duelTargetId == null) {
        log += "The Violet Warden không còn Duel Target ACTIVE hợp lệ."
      } else {
        if (resolvedState.metadata[VIOLET_WARDEN_DUEL_TARGET_KEY] != duelTargetId) {
          resolvedState = violetWardenMetadata(resolvedState, VIOLET_WARDEN_DUEL_TARGET_KEY, duelTargetId)
          val locked = resolvedState.characters[duelTargetId]?.name ?: duelTargetId
          log += "Duelist's Decree: The Violet Warden khóa $locked làm Duel Target."
        }
        val target = resolvedState.characters[duelTargetId]
        if (target != null) {
          val targetMaxHp = CharacterStatEngine.effective(resolvedState, duelTargetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
          if (before > 0) {
            val personalEvasion = (when {
              duelTargetId == KAI_ID -> {
                val quickStep = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
                quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)
              }
              duelTargetId == SYVIAL_ID && syvialDevilTrigger -> 20
              else -> 0
            }) + CharacterStatEngine.devilBlessingEvasionBonus(resolvedState, duelTargetId)
            val partyDefense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
              when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
            val enemyChance = (profile.aggression * 8 - partyDefense + max(0, -c.momentum) * 7 - companionEnemyAccuracyPenalty - personalEvasion).coerceIn(0, 88)
            val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 1301), 100)
            if (incomingRoll < enemyChance) {
              val ultimateTurn = c.eventCounter % VIOLET_WARDEN_ULTIMATE_INTERVAL_TURNS == 0
              val controlTurn = !ultimateTurn && c.eventCounter % VIOLET_WARDEN_CONTROL_INTERVAL_TURNS == 0
              val riposteReady = resolvedState.metadata[VIOLET_WARDEN_RIPOSTE_READY_KEY].equals("true", true)
              val percent = when {
                ultimateTurn -> VIOLET_WARDEN_ULTIMATE_DAMAGE_PERCENT
                controlTurn -> VIOLET_WARDEN_CONTROL_DAMAGE_PERCENT
                riposteReady -> VIOLET_WARDEN_RIPOSTE_DAMAGE_PERCENT
                else -> VIOLET_WARDEN_ATTACK_PERCENT
              }
              val damage = min(before, percentDamage(targetMaxHp, percent))
              resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, duelTargetId, before - damage)
              val after = resolvedState.characters[duelTargetId]?.vitalState?.currentHp ?: max(0, before - damage)
              if (!ultimateTurn && !controlTurn && riposteReady) {
                resolvedState = violetWardenMetadata(resolvedState, VIOLET_WARDEN_RIPOSTE_READY_KEY, null)
              }
              if (ultimateTurn && after > 0) {
                resolvedState = violetWardenApplyStun(resolvedState, duelTargetId, c.eventCounter)
                log += "King's Sentence: ${target.name} -$damage HP ($percent% Max HP), STUN 1 turn."
              } else if (controlTurn && after > 0) {
                val stun = roll(c.copy(eventCounter = c.eventCounter + 1327), 100) < VIOLET_WARDEN_CONTROL_STUN_PERCENT
                if (stun) resolvedState = violetWardenApplyStun(resolvedState, duelTargetId, c.eventCounter)
                log += "Pommel Break: ${target.name} -$damage HP ($percent% Max HP); ${if (stun) "STUN 1 turn" else "Stun không proc"}."
              } else if (riposteReady) {
                log += "Violet Riposte: ${target.name} -$damage HP ($percent% Max HP)."
              } else {
                log += "Violet Judgment: ${target.name} -$damage HP ($percent% Max HP)."
              }
              if (duelTargetId == KAI_ID) c = c.copy(playerHp = after, playerMaxHp = targetMaxHp)
              c = c.copy(momentum = max(-3, c.momentum - 1))
            } else {
              log += "${target.name} tránh/đỡ được đòn khóa mục tiêu của The Violet Warden."
            }
          }
        }
      }
    } else if (c.entityKey != SCP_173_KEY &&
        !(c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0)) {
      val entityTargets = entityCombatActionTargets(resolvedState)

      // Jane bleed ticks once per combat turn before her direct actions.
      if (c.entityKey == JANE_KEY) {
        entityTargets.forEach { bleedTargetId ->
          val turns = resolvedState.metadata[janeBleedKey(bleedTargetId)]?.toIntOrNull()?.coerceIn(0, JANE_BLEED_TURNS) ?: 0
          if (turns > 0) {
            val target = resolvedState.characters[bleedTargetId] ?: return@forEach
            val targetMaxHp = CharacterStatEngine.effective(resolvedState, bleedTargetId).maxHp
            val beforeBleed = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
            if (beforeBleed > 0) {
              val bleedDamage = min(beforeBleed, percentDamage(targetMaxHp, JANE_BLEED_PERCENT))
              resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, bleedTargetId, beforeBleed - bleedDamage)
              resolvedState = withCombatCounter(resolvedState, janeBleedKey(bleedTargetId), turns - 1)
              val afterBleed = resolvedState.characters[bleedTargetId]?.vitalState?.currentHp ?: max(0, beforeBleed - bleedDamage)
              log += "Bleed: ${target.name} -$bleedDamage HP ($JANE_BLEED_PERCENT% Max HP); còn ${turns - 1} turn ($afterBleed/$targetMaxHp)."
            } else {
              resolvedState = withCombatCounter(resolvedState, janeBleedKey(bleedTargetId), 0)
            }
          }
        }
      }

      // No Safe Route punishes an escape attempt only when the reduced escape did not resolve.
      if (c.entityKey == JEFF_KEY &&
          resolvedState.metadata[JEFF_NO_SAFE_RETALIATE_TURN_KEY]?.toIntOrNull() == c.eventCounter) {
        val targetId = if (KAI_ID in entityTargets) KAI_ID else entityTargets.firstOrNull()
        if (targetId != null) {
          val target = resolvedState.characters[targetId]
          if (target != null) {
            val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
            val beforeRetaliation = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
            if (beforeRetaliation > 0) {
              val damage = min(beforeRetaliation, percentDamage(targetMaxHp, JEFF_NO_SAFE_ROUTE_RETALIATION_PERCENT))
              resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, beforeRetaliation - damage)
              val afterRetaliation = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, beforeRetaliation - damage)
              log += "No Safe Route retaliation: ${target.name} -$damage HP ($JEFF_NO_SAFE_ROUTE_RETALIATION_PERCENT% Max HP; $afterRetaliation/$targetMaxHp)."
            }
          }
        }
        resolvedState = withCombatCounter(resolvedState, JEFF_NO_SAFE_RETALIATE_TURN_KEY, 0)
      }

      var hunterMarkTargetId = resolvedState.metadata[JANE_MARK_TARGET_KEY].orEmpty()
      var hunterMarkUntil = resolvedState.metadata[JANE_MARK_UNTIL_KEY]?.toIntOrNull() ?: -1
      if (c.entityKey == JANE_KEY &&
          hunterMarkUntil < c.eventCounter &&
          killerSkillReady(resolvedState, "jane.hunters_mark", c.eventCounter)) {
        hunterMarkTargetId = if (KAI_ID in entityTargets) KAI_ID else entityTargets.firstOrNull().orEmpty()
        if (hunterMarkTargetId.isNotBlank()) {
          hunterMarkUntil = c.eventCounter + JANE_HUNTER_MARK_DURATION_TURNS - 1
          resolvedState = withCombatText(resolvedState, JANE_MARK_TARGET_KEY, hunterMarkTargetId)
          resolvedState = withCombatCounter(resolvedState, JANE_MARK_UNTIL_KEY, hunterMarkUntil)
          resolvedState = useKillerSkill(resolvedState, "jane.hunters_mark", c.eventCounter, JANE_HUNTER_MARK_COOLDOWN)
          val marked = resolvedState.characters[hunterMarkTargetId]?.name ?: hunterMarkTargetId
          log += "Hunter's Mark: Jane đánh dấu $marked trong $JANE_HUNTER_MARK_DURATION_TURNS turn; +$JANE_HUNTER_MARK_ACCURACY_BONUS% Accuracy và -$JANE_HUNTER_MARK_ESCAPE_PENALTY điểm Escape."
        }
      }

      if (c.entityKey == JANE_KEY && entityTargets.isNotEmpty()) {
        val damageTakenThisTurn = max(0, current.entityHp - c.entityHp)
        val triggerThreshold = percentDamage(c.entityMaxHp, JANE_VENGEFUL_TRIGGER_PERCENT)
        if (damageTakenThisTurn >= triggerThreshold &&
            killerSkillReady(resolvedState, "jane.vengeful_reflex", c.eventCounter)) {
          resolvedState = useKillerSkill(resolvedState, "jane.vengeful_reflex", c.eventCounter, JANE_VENGEFUL_COOLDOWN)
          if (roll(c.copy(eventCounter = c.eventCounter + 947), 100) < JANE_VENGEFUL_PROC_PERCENT) {
            val targetId = if (KAI_ID in entityTargets) KAI_ID else entityTargets.first()
            val target = resolvedState.characters[targetId]
            if (target != null) {
              val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
              val beforeCounter = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
              if (beforeCounter > 0) {
                val damage = min(beforeCounter, percentDamage(targetMaxHp, JANE_VENGEFUL_DAMAGE_PERCENT))
                resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, beforeCounter - damage)
                val afterCounter = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, beforeCounter - damage)
                log += "Vengeful Reflex: Jane phản kích ${target.name} -$damage HP ($JANE_VENGEFUL_DAMAGE_PERCENT% Max HP; $afterCounter/$targetMaxHp)."
              }
            }
          } else {
            log += "Vengeful Reflex: điều kiện phản kích đạt nhưng proc $JANE_VENGEFUL_PROC_PERCENT% không thành công."
          }
        }
      }

      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."
      val partyDefense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      var landedActions = 0
      var missedIris = false
      var missedSyvial = false

      entityTargets.forEachIndexed { actionIndex, targetId ->
        val target = resolvedState.characters[targetId] ?: return@forEachIndexed
        val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
        val before = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
        if (before <= 0) return@forEachIndexed

        val personalEvasion = (when {
          targetId == KAI_ID -> {
            val quickStep = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
            quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)
          }
          targetId == SYVIAL_ID && syvialDevilTrigger -> 20
          else -> 0
        }) + CharacterStatEngine.devilBlessingEvasionBonus(resolvedState, targetId)
        val hunterMarked = c.entityKey == JANE_KEY &&
          targetId == hunterMarkTargetId && hunterMarkUntil >= c.eventCounter
        val jeffSilentStalker = c.entityKey == JEFF_KEY && actionIndex == 0 &&
          killerSkillReady(resolvedState, "jeff.silent_stalker", c.eventCounter)
        val jeffGoToSleep = c.entityKey == JEFF_KEY && actionIndex == 0 &&
          killerSkillReady(resolvedState, "jeff.go_to_sleep", c.eventCounter)
        val janeDontWakeUp = c.entityKey == JANE_KEY && actionIndex == 0 &&
          killerSkillReady(resolvedState, "jane.dont_wake_up", c.eventCounter)

        if (jeffSilentStalker) {
          resolvedState = useKillerSkill(resolvedState, "jeff.silent_stalker", c.eventCounter, JEFF_SILENT_STALKER_COOLDOWN)
        }
        if (jeffGoToSleep) {
          resolvedState = useKillerSkill(resolvedState, "jeff.go_to_sleep", c.eventCounter, JEFF_GO_TO_SLEEP_COOLDOWN)
        }
        if (janeDontWakeUp) {
          resolvedState = useKillerSkill(resolvedState, "jane.dont_wake_up", c.eventCounter, JANE_DONT_WAKE_UP_COOLDOWN)
        }

        val killerAccuracyBonus =
          (if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0) +
          (if (jeffSilentStalker && entityTargets.size == 1) JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS else 0)
        val enemyChance = (profile.aggression * 8 - partyDefense + max(0, -c.momentum) * 7 -
          companionEnemyAccuracyPenalty - personalEvasion + killerAccuracyBonus).coerceIn(0, 95)
        val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31 + actionIndex * 53), 100)

        if (incomingRoll < enemyChance) {
          var requestedDamage = when (c.entityKey) {
            DIEP_MINH_KEY -> percentDamage(targetMaxHp, DIEP_MINH_ATTACK_PERCENT)
            MONSTER_X_KEY -> percentDamage(targetMaxHp, MONSTER_X_ATTACK_PERCENT)
            JOHN_DOE_KEY -> percentDamage(targetMaxHp, JOHN_DOE_ATTACK_PERCENT)
            JEFF_KEY -> if (actionIndex == 0 && jeffGoToSleep) {
              percentDamage(targetMaxHp, if (before * 2 < targetMaxHp) JEFF_GO_TO_SLEEP_LOW_HP_PERCENT else JEFF_GO_TO_SLEEP_NORMAL_PERCENT)
            } else {
              val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47 + actionIndex * 59), 7) -
                when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
              if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
            }
            JANE_KEY -> if (actionIndex == 0 && janeDontWakeUp) {
              percentDamage(targetMaxHp, JANE_DONT_WAKE_UP_HIT_PERCENT)
            } else {
              val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47 + actionIndex * 59), 7) -
                when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
              if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
            }
            else -> {
              val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47 + actionIndex * 59), 7) -
                when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
              if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
            }
          }
          var killerSkillDetail = ""
          if (c.entityKey == JEFF_KEY && actionIndex == 0 && jeffGoToSleep) {
            val basePercent = if (before * 2 < targetMaxHp) JEFF_GO_TO_SLEEP_LOW_HP_PERCENT else JEFF_GO_TO_SLEEP_NORMAL_PERCENT
            killerSkillDetail = "Go to Sleep: $basePercent% Max HP"
          }
          if (c.entityKey == JEFF_KEY && actionIndex == 0 && jeffSilentStalker) {
            requestedDamage = max(1, (requestedDamage * JEFF_SILENT_STALKER_DAMAGE_PERCENT + 99) / 100)
            killerSkillDetail = (if (killerSkillDetail.isBlank()) "" else "$killerSkillDetail; ") +
              "Silent Stalker: damage x1.40${if (entityTargets.size == 1) ", +$JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS% Accuracy khi mục tiêu đi một mình" else ""}"
          }
          if (c.entityKey == JANE_KEY && actionIndex == 0 && janeDontWakeUp) {
            val secondHitChance = (JANE_DONT_WAKE_UP_SECOND_HIT_ACCURACY +
              if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0).coerceAtMost(95)
            val secondHit = roll(c.copy(eventCounter = c.eventCounter + 1091), 100) < secondHitChance
            if (secondHit) {
              requestedDamage += percentDamage(targetMaxHp, JANE_DONT_WAKE_UP_HIT_PERCENT)
              resolvedState = withCombatCounter(resolvedState, janeBleedKey(targetId), JANE_BLEED_TURNS)
              killerSkillDetail = "Don't Wake Up: 2 hit x $JANE_DONT_WAKE_UP_HIT_PERCENT% Max HP; Bleed $JANE_BLEED_PERCENT% Max HP/turn x $JANE_BLEED_TURNS"
            } else {
              killerSkillDetail = "Don't Wake Up: hit đầu $JANE_DONT_WAKE_UP_HIT_PERCENT% Max HP; hit hai ($secondHitChance% Accuracy) trượt"
            }
          }

          val damage = min(before, requestedDamage)
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
          val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
          landedActions += 1

          val legacyHit = when {
            targetId == KAI_ID && c.entityKey == DIEP_MINH_KEY ->
              "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            targetId == KAI_ID && c.entityKey == MONSTER_X_KEY ->
              "Monster X tấn công: Kai -$damage HP (${MONSTER_X_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            targetId == KAI_ID && c.entityKey == JOHN_DOE_KEY ->
              "John Doe tấn công: Kai -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            targetId == KAI_ID ->
              "${c.entityName} phản công: Kai -$damage HP ($after/$targetMaxHp)."
            c.entityKey == DIEP_MINH_KEY ->
              "Diệp Minh tấn công ${target.name}: -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            c.entityKey == MONSTER_X_KEY ->
              "Monster X tấn công ${target.name}: -$damage HP (${MONSTER_X_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            c.entityKey == JOHN_DOE_KEY ->
              "John Doe tấn công ${target.name}: -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            else -> "${c.entityName} tấn công ${target.name}: -$damage HP ($after/$targetMaxHp)."
          }
          if (killerSkillDetail.isNotBlank()) log += killerSkillDetail
          log += "ENTITY ACTION ${actionIndex + 1}/${entityTargets.size} -> ${target.name}: HIT. $legacyHit"
        } else {
          if (targetId == IRIS_ID) missedIris = true
          if (targetId == SYVIAL_ID) missedSyvial = true
          val missDetail = when {
            targetId == KAI_ID && quickStepTurns > 0 ->
              "Quick Step khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
            targetId == KAI_ID -> "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
            else -> "${c.entityName} hụt direct action vào ${target.name}."
          }
          log += "ENTITY ACTION ${actionIndex + 1}/${entityTargets.size} -> ${target.name}: MISS. $missDetail"
        }
      }

      if (landedActions > 0) c = c.copy(momentum = max(-3, c.momentum - 1))
      val kaiMaxHpAfter = CharacterStatEngine.effective(resolvedState, KAI_ID).maxHp
      val kaiHpAfter = resolvedState.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, kaiMaxHpAfter) ?: c.playerHp
      c = c.copy(playerHp = kaiHpAfter, playerMaxHp = kaiMaxHpAfter)

      // Counters are now personal: each companion may counter only if the direct
      // action aimed at that companion missed. They still trigger at most once
      // each per Entity turn and do not create extra Entity actions.
      if (missedIris && irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
        val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 120, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
      }
      if (missedSyvial && syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {
        val baseDamage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 125, profile.armor)
        val damage = DevilTriggerPassive.damage(baseDamage, syvialDevilTrigger)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
      }
    } else if (c.entityKey == JOHN_DOE_KEY) {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty).coerceIn(0, 88)
      if (incomingRoll < enemyChance) {
        val damage = percentDamage(c.playerMaxHp, JOHN_DOE_ATTACK_PERCENT)
        val before = resolvedState.characters[KAI_ID]?.vitalState?.currentHp ?: c.playerHp
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, KAI_ID, before - damage)
        val hp = resolvedState.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, c.playerMaxHp) ?: max(0, before - damage)
        c = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
        log += "John Doe tấn công: Kai -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
      } else {
        log += "John Doe không xuyên được thế phòng thủ/di chuyển của Kai."
      }
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
      // Baseline pulse compatibility: damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)
      val devilTriggerEvaders = listOfNotNull(
        KAI_ID.takeIf { kaiDevilTriggerActive },
        SYVIAL_ID.takeIf { syvialDevilTrigger }
      ).toSet()
      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT, devilTriggerEvaders)
      resolvedState = pulse.state
      c = c.copy(playerHp = pulse.kaiHp, momentum = max(-3, c.momentum - 1))
      log += "Devils And Gold kích hoạt ở combat turn ${c.eventCounter}: toàn bộ nhân vật ACTIVE đang ra trận nhận ${DIEP_MINH_ULTIMATE_PERCENT}% Max HP. ${pulse.summary}."
    } else {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty - kaiDevilTriggerEvasion).coerceIn(0, 88)
      if (incomingRoll < enemyChance) {
        val damage = if (c.entityKey == DIEP_MINH_KEY) {
          percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)
        } else if (c.entityKey == MONSTER_X_KEY) {
          percentDamage(c.playerMaxHp, MONSTER_X_ATTACK_PERCENT)
        } else {
          val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
          if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
        }
        val hp = max(0, c.playerHp - damage)
        c = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
        log += if (c.entityKey == DIEP_MINH_KEY) {
          "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else if (c.entityKey == MONSTER_X_KEY) {
          "Monster X tấn công: Kai -$damage HP (${MONSTER_X_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else {
          "${c.entityName} phản công: Kai -$damage HP (${c.playerHp}/${c.playerMaxHp})."
        }
      } else {
        log += if (quickStepTurns > 0) {
          "Quick Step khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
        }
        if (intent == Intent.ATTACK && irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 120, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
        }
        if (intent == Intent.ATTACK && syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {
          val baseDamage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 125, profile.armor)
          val damage = DevilTriggerPassive.damage(baseDamage, syvialDevilTrigger)
          val hp = max(0, c.entityHp - damage)
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

    if (c.entityKey == MONSTER_X_KEY && c.eventCounter % MONSTER_X_BLEED_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 401), 100) < MONSTER_X_BLEED_PROC_PERCENT) {
      monsterXBleedTurns = MONSTER_X_BLEED_DURATION_TURNS
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_BLEED_TURNS_KEY, monsterXBleedTurns)
      log += "Monster X gây Bleeding cho toàn bộ Party: ${MONSTER_X_BLEED_DURATION_TURNS} lượt, ${MONSTER_X_BLEED_MAX_HP_PERCENT}% Max HP/lượt."
    }
    if (c.entityKey == MONSTER_X_KEY && c.eventCounter % MONSTER_X_STUN_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 419), 100) < MONSTER_X_STUN_GATE_PERCENT &&
        roll(c.copy(eventCounter = c.eventCounter + 431), 100) < MONSTER_X_STUN_PROC_PERCENT) {
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_STUN_TURNS_KEY, 1)
      log += "Monster X chuẩn bị Stun: cổng ${MONSTER_X_STUN_GATE_PERCENT}% và proc ${MONSTER_X_STUN_PROC_PERCENT}% thành công; Party sẽ mất 1 lượt kế tiếp."
    }

    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_POISON_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 607), 100) < JOHN_DOE_POISON_CHANCE_PERCENT) {
      val affected = johnDoePoisonedIds(resolvedState) + johnDoeActivePartyIds(resolvedState)
      resolvedState = withJohnDoePoisoned(resolvedState, affected)
      log += "John Doe gây Poison sau ${JOHN_DOE_POISON_INTERVAL_TURNS} lượt: proc ${JOHN_DOE_POISON_CHANCE_PERCENT}% thành công; đánh dấu riêng ${affected.size} thành viên ACTIVE."
    }
    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_STUN_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 619), 100) < JOHN_DOE_STUN_GATE_PERCENT &&
        roll(c.copy(eventCounter = c.eventCounter + 631), 100) < JOHN_DOE_STUN_PROC_PERCENT) {
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_STUN_TURNS_KEY, 1)
      log += "John Doe Stun check: cổng ${JOHN_DOE_STUN_GATE_PERCENT}% và proc ${JOHN_DOE_STUN_PROC_PERCENT}% cùng thành công; mục tiêu bị Stun 1 lượt kế tiếp."
    }

    val entityHpBeforeRegen = c.entityHp
    val entityRegen = when (c.entityKey) { DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN; MONSTER_X_KEY -> MONSTER_X_REGEN_PER_TURN; JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN; SCP_173_KEY -> SCP_173_REGEN_PER_TURN; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN }
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
    val next = encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn), c)
    return Resolution(next, true, localizeCombatNarration(log.joinToString(" ")))
  }

  fun toJson(state: GameState): JSONObject? = decode(state)?.let { c -> JSONObject().apply {
    put("active", c.phase == Phase.ACTIVE)
    put("encounterId", c.encounterId)
    put("entityKey", c.entityKey)
    put("entityName", c.entityName)
    put("playerHp", c.playerHp); put("playerMaxHp", c.playerMaxHp)
    put("entityHp", c.entityHp); put("entityMaxHp", c.entityMaxHp)
    put("entityCondition", c.entityCondition.name)
    put("range", c.range.name); put("cover", c.cover.name)
    put("momentum", c.momentum); put("opening", c.opening)
    put("escapeProgress", c.escapeProgress); put("noise", c.noise)
    put("telegraph", if (c.telegraphRevealed) c.telegraph else "UNKNOWN")
    put("telegraphRevealed", c.telegraphRevealed)
    if (c.entityKey == VIOLET_WARDEN_KEY) {
      put("entityType", "Unique Former-Human Entity Boss")
      put("combatRole", "Control / Single Target / Counter")
      put("weapon", "Violet Judgment")
      put("blockPercent", VIOLET_WARDEN_BLOCK_PERCENT)
      put("blockReductionPercent", VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT)
      put("duelTargetId", state.metadata[VIOLET_WARDEN_DUEL_TARGET_KEY] ?: "")
      put("riposteReady", state.metadata[VIOLET_WARDEN_RIPOSTE_READY_KEY].equals("true", true))
      put("originEra", "15th century")
      put("stunTargetId", state.metadata[VIOLET_WARDEN_STUN_TARGET_KEY] ?: "")
      put("stunUntilEvent", state.metadata[VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY]?.toIntOrNull() ?: 0)
    }
    if (c.entityKey == SCP_173_KEY) {
      put("entityType", "Hostile Entity / Concrete Anomaly")
      put("passive", "DON'T BLINK")
      put("observationState", state.metadata[SCP_173_STATE_KEY] ?: "OBSERVED")
      put("actionSpeedPercent", state.metadata[SCP_173_ACTION_SPEED_KEY]?.toIntOrNull() ?: SCP_173_OBSERVED_ACTION_SPEED_PERCENT)
      put("firstUnobservedStrikePending", state.metadata[SCP_173_FIRST_STRIKE_PENDING_KEY].equals("true", true))
      put("observedDamageReductionPercent", SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT)
      put("physicalDamageReductionPercent", SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT)
      put("stunMaxTurns", 1)
      put("immunities", JSONArray(listOf("POISON", "FEAR", "KNOCKBACK")))
      put("blinkCounters", JSONObject().apply { scp173LivePartyIds(state).forEach { characterId -> put(characterId, scp173BlinkCounter(state, characterId)) } })
      put("cooldowns", JSONObject().apply {
        put("concreteRush", scp173Cooldown(state, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN))
        put("neckSnap", scp173Cooldown(state, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN))
        put("blinkPressure", scp173Cooldown(state, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN))
      })
    }
  } }

  fun clear(state: GameState): GameState = clearCombatOnly(state)

  private fun withCombatCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

  private fun weaponSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - armor)

  private fun devilTriggerState(state: GameState, characterId: String): DevilTriggerState {
    val activeKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_ACTIVE_KEY else DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY
    val cooldownKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_COOLDOWN_KEY else DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY
    return DevilTriggerState(
      activeTurns = state.metadata[activeKey]?.toIntOrNull() ?: 0,
      cooldownTurns = state.metadata[cooldownKey]?.toIntOrNull() ?: 0
    )
  }

  private fun withDevilTriggerState(state: GameState, characterId: String, value: DevilTriggerState): GameState {
    val activeKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_ACTIVE_KEY else DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY
    val cooldownKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_COOLDOWN_KEY else DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY
    val metadata = state.metadata.toMutableMap()
    if (value.activeTurns > 0) metadata[activeKey] = value.activeTurns.toString() else metadata.remove(activeKey)
    if (value.cooldownTurns > 0) metadata[cooldownKey] = value.cooldownTurns.toString() else metadata.remove(cooldownKey)
    return state.copy(metadata = metadata)
  }

  private fun finishDevilTriggerTurns(
    state: GameState,
    kaiTurn: DevilTriggerTurn?,
    syvialTurn: DevilTriggerTurn?
  ): GameState {
    var next = state
    if (kaiTurn != null) next = withDevilTriggerState(next, KAI_ID, DevilTriggerPassive.endTurn(kaiTurn))
    if (syvialTurn != null) next = withDevilTriggerState(next, SYVIAL_ID, DevilTriggerPassive.endTurn(syvialTurn))
    return next
  }

  private fun healCharacterForDevilTrigger(state: GameState, characterId: String): Pair<GameState, Int> {
    val character = state.characters[characterId] ?: return state to 0
    if (character.vitalState.currentHp <= 0) return state to 0
    val maxHp = CharacterStatEngine.effective(state, characterId).maxHp
    val before = character.vitalState.currentHp
    val next = CharacterStatEngine.setCurrentHp(state, characterId, before + DevilTriggerPassive.healAmount(maxHp))
    val after = next.characters[characterId]?.vitalState?.currentHp ?: before
    return next to max(0, after - before)
  }

  // PARTY_ACTIONS_V1: authoritative roster for one simultaneous Party command.
  private fun activePartyNames(state: GameState): String =
    state.party.memberIds.distinct().mapNotNull { id ->
      state.characters[id]?.takeIf { character ->
        character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
      }?.name
    }.joinToString(", ")

  internal fun luciaTooYoungToDieTriggerChancePercent(currentHp: Int, maxHp: Int): Int {
    val safeMaxHp = max(1, maxHp)
    val hp = currentHp.coerceIn(0, safeMaxHp)
    val hpPercent = (hp * 100) / safeMaxHp
    val percentLostBelowHalf = max(0, 50 - hpPercent)
    val lowHpSteps = percentLostBelowHalf / LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_STEP_PERCENT
    return min(
      100,
      LUCIA_TOO_YOUNG_TO_DIE_BASE_CHANCE_PERCENT +
        lowHpSteps * LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_BONUS_PERCENT
    )
  }

  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in state.party.memberIds) return null
    if (violetWardenActionLocked(state, characterId) || kaiDevilWithinActionLocked(state, characterId)) return null
    val character = state.characters[characterId] ?: return null
    return character.takeIf { it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0 }
  }

  private fun kaiDevilWithinActionLocked(state: GameState, characterId: String): Boolean {
    if (state.metadata["combat.entityKey"] != KAI_DEVIL_WITHIN_KEY) return false
    if (state.metadata[KAI_DEVIL_WITHIN_STUN_TARGET_KEY] != characterId) return false
    val currentEvent = state.metadata["combat.eventCounter"]?.toIntOrNull() ?: 0
    return currentEvent < (state.metadata[KAI_DEVIL_WITHIN_STUN_UNTIL_KEY]?.toIntOrNull() ?: 0)
  }

  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int {
    val resolved = max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))
    return max(1, (resolved * 105 + 99) / 100)
  }

  private fun armorAfterIgnore(armor: Int, ignorePercent: Int): Int =
    max(0, armor - ((armor * ignorePercent + 99) / 100))

  private data class PartyPercentDamage(
    val state: GameState,
    val kaiHp: Int,
    val summary: String
  )

  private fun percentDamage(maxHp: Int, percent: Int): Int =
    max(1, (maxHp * percent + 99) / 100)

  // JEFF_JANE_SKILLS_V1: save-persistent cooldown/status helpers.
  private fun killerCooldownKey(skill: String): String = "${PREFIX}killer.$skill.nextReadyTurn"

  private fun killerSkillReady(state: GameState, skill: String, eventCounter: Int): Boolean =
    eventCounter >= (state.metadata[killerCooldownKey(skill)]?.toIntOrNull() ?: 0)

  private fun useKillerSkill(state: GameState, skill: String, eventCounter: Int, cooldownTurns: Int): GameState =
    withCombatCounter(state, killerCooldownKey(skill), eventCounter + cooldownTurns + 1)

  private fun withCombatText(state: GameState, key: String, value: String): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value.isBlank()) metadata.remove(key) else metadata[key] = value
    return state.copy(metadata = metadata)
  }

  private fun janeBleedKey(characterId: String): String = "${PREFIX}jane.bleed.$characterId"

  // VIOLET_WARDEN_V1: unique duel/control helpers.
  private fun entityEvasionPercent(entityKey: String): Int =
    if (entityKey == VIOLET_WARDEN_KEY) 0 else ENTITY_EVASION_PERCENT

  private fun violetWardenMetadata(state: GameState, key: String, value: String?): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value.isNullOrBlank()) metadata.remove(key) else metadata[key] = value
    return state.copy(metadata = metadata)
  }

  private fun violetWardenActionLocked(state: GameState, characterId: String): Boolean {
    if (state.metadata["combat.entityKey"] != VIOLET_WARDEN_KEY) return false
    if (state.metadata[VIOLET_WARDEN_STUN_TARGET_KEY] != characterId) return false
    val untilEvent = state.metadata[VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY]?.toIntOrNull() ?: return false
    val nextEvent = (state.metadata["combat.eventCounter"]?.toIntOrNull() ?: 0) + 1
    return nextEvent <= untilEvent
  }

  private fun violetWardenDuelTarget(state: GameState): String? {
    val candidates = entityCombatActionTargets(state)
    val locked = state.metadata[VIOLET_WARDEN_DUEL_TARGET_KEY].orEmpty()
    if (locked in candidates) return locked
    return candidates.maxWithOrNull(compareBy<String> { CharacterStatEngine.weaponDamage(state, it) }.thenBy { it })
  }

  private fun violetWardenApplyStun(state: GameState, characterId: String, eventCounter: Int): GameState {
    if (characterId !in state.characters) return state
    var scheduled = violetWardenMetadata(state, VIOLET_WARDEN_STUN_TARGET_KEY, characterId)
    scheduled = violetWardenMetadata(scheduled, VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY, (eventCounter + 1).toString())
    val id = VIOLET_WARDEN_STATUS_PREFIX + "stun:" + characterId
    val effect = StatusEffect(
      id = id,
      type = "STUN",
      source = VIOLET_WARDEN_KEY,
      startTurnId = state.turn.currentTurnId,
      durationTurns = 1,
      persistent = false,
      metadata = mapOf("combatEvent" to eventCounter.toString())
    )
    val operation = if (id in scheduled.statuses) StatusCommand.Operation.UPDATE else StatusCommand.Operation.APPLY
    val result = StatusEngine.execute(scheduled, StatusCommand(
      commandId = "VIOLET_WARDEN:STUN:$characterId:$eventCounter",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = operation,
      effect = effect,
      statusId = id
    ))
    return if (result.applied) result.state else scheduled
  }

  // ENTITY_PARTY_ACTION_BUDGET_V1: direct Entity targets only.
  private fun entityCombatActionTargets(state: GameState): List<String> =
    state.party.memberIds.distinct().filter { characterId ->
      val character = state.characters[characterId]
      character != null &&
        character.presence == CharacterPresence.ACTIVE &&
        character.vitalState.currentHp > 0 &&
        characterId != AN_NHIEN_ID &&
        !character.statProfile.combatRole.uppercase().contains("NON-COMBAT")
    }

  private fun scp173LivePartyIds(state: GameState): List<String> =
    state.party.memberIds.distinct().filter { characterId ->
      val character = state.characters[characterId]
      character != null && character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
    }

  private fun scp173BlinkCounter(state: GameState, characterId: String): Int =
    state.metadata[SCP_173_BLINK_COUNTER_PREFIX + characterId]?.toIntOrNull()?.coerceIn(0, SCP_173_BLINK_THRESHOLD - 1) ?: 0

  private fun scp173WithBlinkCounter(state: GameState, characterId: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[SCP_173_BLINK_COUNTER_PREFIX + characterId] = value.coerceIn(0, SCP_173_BLINK_THRESHOLD - 1).toString()
    return state.copy(metadata = metadata)
  }

  private fun scp173StatusMatches(effect: StatusEffect, vararg tokens: String): Boolean {
    val type = effect.type.trim().uppercase().replace('-', '_').replace(' ', '_')
    val id = effect.id.trim().uppercase().replace('-', '_').replace(' ', '_')
    return tokens.any { token -> type.contains(token) || id.contains(token) }
  }

  private fun scp173CharacterHasStatus(state: GameState, characterId: String, vararg tokens: String): Boolean {
    val character = state.characters[characterId] ?: return false
    return character.statusIds.asSequence().mapNotNull(state.statuses::get).any { scp173StatusMatches(it, *tokens) }
  }

  private fun scp173VisionBlocked(state: GameState, characterId: String): Boolean {
    val character = state.characters[characterId] ?: return true
    if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return true
    if (scp173CharacterHasStatus(state, characterId,
        "BLINK", "BLIND", "STUN", "UNCONSCIOUS", "KNOCKED_OUT", "NO_LINE_OF_SIGHT", "LINE_OF_SIGHT_LOST", "VISION_LOST", "VISION_BLOCKED")) return true
    val metadata = character.metadata.mapKeys { it.key.trim().lowercase() }
    if (metadata["blind"].equals("true", true) || metadata["stunned"].equals("true", true) ||
        metadata["unconscious"].equals("true", true) || metadata["knockedout"].equals("true", true)) return true
    val los = metadata["lineofsight"]?.trim()?.lowercase()
    return los in setOf("false", "lost", "blocked", "none")
  }

  private fun scp173Observed(state: GameState): Boolean =
    state.metadata[SCP_173_STATE_KEY] == "OBSERVED"

  private fun scp173WithObservation(state: GameState, observed: Boolean): GameState {
    val metadata = state.metadata.toMutableMap()
    val previous = metadata[SCP_173_STATE_KEY]
    val next = if (observed) "OBSERVED" else "UNOBSERVED"
    val wasPending = metadata[SCP_173_FIRST_STRIKE_PENDING_KEY].equals("true", true)
    val firstStrikePending = when {
      observed -> false
      previous != "UNOBSERVED" -> true
      else -> wasPending
    }
    metadata[SCP_173_STATE_KEY] = next
    metadata[SCP_173_ACTION_SPEED_KEY] = (if (observed) SCP_173_OBSERVED_ACTION_SPEED_PERCENT else SCP_173_UNOBSERVED_ACTION_SPEED_PERCENT).toString()
    metadata[SCP_173_FIRST_STRIKE_PENDING_KEY] = firstStrikePending.toString()
    metadata["combat.scp173.immunity.poison"] = "true"
    metadata["combat.scp173.immunity.fear"] = "true"
    metadata["combat.scp173.immunity.knockback"] = "true"
    metadata["combat.scp173.stunMaxTurns"] = "1"
    return state.copy(metadata = metadata)
  }

  private fun scp173RecomputeObservation(state: GameState): GameState =
    scp173WithObservation(state, scp173LivePartyIds(state).any { !scp173VisionBlocked(state, it) })

  private fun scp173ApplyTransientStatus(state: GameState, characterId: String, type: String, expiresEvent: Int): GameState {
    if (characterId !in state.characters) return state
    val id = SCP_173_STATUS_PREFIX + type.lowercase() + ":" + characterId
    val effect = StatusEffect(
      id = id,
      type = type,
      source = SCP_173_KEY,
      startTurnId = state.turn.currentTurnId,
      durationTurns = 1,
      persistent = false,
      metadata = mapOf(SCP_173_STATUS_EXPIRES_EVENT_KEY to expiresEvent.toString())
    )
    val operation = if (id in state.statuses) StatusCommand.Operation.UPDATE else StatusCommand.Operation.APPLY
    val result = StatusEngine.execute(state, StatusCommand(
      commandId = "SCP173:STATUS:$type:$characterId:$expiresEvent",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = operation,
      effect = effect,
      statusId = id
    ))
    return if (result.applied) result.state else state
  }

  private fun scp173RemoveStatus(state: GameState, characterId: String, statusId: String): GameState {
    if (statusId !in state.statuses) return state
    val result = StatusEngine.execute(state, StatusCommand(
      commandId = "SCP173:STATUS:REMOVE:$statusId",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = StatusCommand.Operation.REMOVE,
      statusId = statusId
    ))
    return if (result.applied) result.state else state
  }

  private fun scp173CleanupExpiredStatuses(state: GameState, nextEvent: Int): GameState {
    var next = state
    val expired = state.characters.values.flatMap { character ->
      character.statusIds.mapNotNull { statusId ->
        val effect = state.statuses[statusId] ?: return@mapNotNull null
        val expires = effect.metadata[SCP_173_STATUS_EXPIRES_EVENT_KEY]?.toIntOrNull() ?: return@mapNotNull null
        if (effect.source == SCP_173_KEY && effect.id.startsWith(SCP_173_STATUS_PREFIX) && expires < nextEvent) character.id to effect.id else null
      }
    }
    expired.forEach { (characterId, statusId) -> next = scp173RemoveStatus(next, characterId, statusId) }
    return next
  }

  private fun scp173RemoveAllTransientStatuses(state: GameState): GameState {
    var next = state
    val owned = state.characters.values.flatMap { character ->
      character.statusIds.mapNotNull { statusId ->
        val effect = state.statuses[statusId]
        if (effect != null && effect.source == SCP_173_KEY && effect.id.startsWith(SCP_173_STATUS_PREFIX)) character.id to effect.id else null
      }
    }
    owned.forEach { (characterId, statusId) -> next = scp173RemoveStatus(next, characterId, statusId) }
    return next
  }

  private fun scp173Cooldown(state: GameState, key: String, maximum: Int): Int =
    state.metadata[key]?.toIntOrNull()?.coerceIn(0, maximum) ?: 0

  private fun scp173WithCooldown(state: GameState, key: String, value: Int): GameState =
    withCombatCounter(state, key, max(0, value))

  private fun scp173TickCooldowns(state: GameState, nextEvent: Int): GameState {
    val speedTick = if (!scp173Observed(state) && nextEvent % 2 == 0) 2 else 1
    var next = state
    val values = listOf(
      Triple(SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN, scp173Cooldown(next, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN)),
      Triple(SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN, scp173Cooldown(next, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN)),
      Triple(SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN, scp173Cooldown(next, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN))
    )
    values.forEach { (key, _, value) -> next = scp173WithCooldown(next, key, max(0, value - speedTick)) }
    return next
  }

  private fun scp173PrepareTurn(state: GameState, nextEvent: Int): GameState {
    var next = scp173CleanupExpiredStatuses(state, nextEvent)
    next = scp173TickCooldowns(next, nextEvent)
    scp173LivePartyIds(next).forEach { characterId ->
      if (scp173VisionBlocked(next, characterId)) return@forEach
      val advanced = scp173BlinkCounter(next, characterId) + 1
      if (advanced >= SCP_173_BLINK_THRESHOLD) {
        next = scp173WithBlinkCounter(next, characterId, 0)
        next = scp173ApplyTransientStatus(next, characterId, "BLINK", nextEvent)
      } else {
        next = scp173WithBlinkCounter(next, characterId, advanced)
      }
    }
    return scp173RecomputeObservation(next)
  }

  private fun scp173InitializeEncounter(state: GameState): GameState {
    var next = scp173RemoveAllTransientStatuses(state)
    scp173LivePartyIds(next).forEach { characterId -> next = scp173WithBlinkCounter(next, characterId, 0) }
    next = scp173WithCooldown(next, SCP_173_CONCRETE_RUSH_CD_KEY, 0)
    next = scp173WithCooldown(next, SCP_173_NECK_SNAP_CD_KEY, 0)
    next = scp173WithCooldown(next, SCP_173_BLINK_PRESSURE_CD_KEY, 0)
    return scp173RecomputeObservation(next)
  }

  private fun scp173ConsumeFirstStrike(state: GameState): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[SCP_173_FIRST_STRIKE_PENDING_KEY] = "false"
    return state.copy(metadata = metadata)
  }

  private fun scp173DirectDamage(rawDamage: Int, observed: Boolean): Int {
    if (rawDamage <= 0) return 0
    var adjusted = max(1, rawDamage * (100 - SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT) / 100)
    if (observed) adjusted = max(1, adjusted * (100 - SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT) / 100)
    return adjusted
  }

  private fun scp173TargetId(state: GameState): String? {
    val live = entityCombatActionTargets(state)
    return if (KAI_ID in live) KAI_ID else live.firstOrNull()
  }

  private fun scp173KaiHp(state: GameState): Int {
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    return state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, maxHp) ?: maxHp
  }

  private fun johnDoePoisonedIds(state: GameState): Set<String> =
    state.metadata.entries.asSequence()
      .filter { (key, value) -> key.startsWith(JOHN_DOE_POISONED_PREFIX) && value == "true" }
      .map { (key, _) -> key.removePrefix(JOHN_DOE_POISONED_PREFIX) }
      .filter { it.isNotBlank() }
      .toSet()

  private fun johnDoeActivePartyIds(state: GameState): Set<String> =
    state.party.memberIds.distinct().filter { characterId ->
      val character = state.characters[characterId]
      character != null && character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
    }.toSet()

  private fun withJohnDoePoisoned(state: GameState, characterIds: Set<String>): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(JOHN_DOE_POISONED_PREFIX) }.toMutableMap()
    characterIds.forEach { characterId -> metadata[JOHN_DOE_POISONED_PREFIX + characterId] = "true" }
    return state.copy(metadata = metadata)
  }

  private fun damageJohnDoePoisoned(state: GameState, percent: Int): PartyPercentDamage {
    var next = state
    val lines = mutableListOf<String>()
    johnDoePoisonedIds(state).forEach { characterId ->
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
      summary = if (lines.isEmpty()) "không có mục tiêu Poison ACTIVE hợp lệ" else lines.joinToString("; ")
    )
  }

  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {
    return damageActivePartyByPercent(state, percent, emptySet())
  }

  private fun damageActivePartyByPercent(state: GameState, percent: Int, evadingCharacterIds: Set<String>): PartyPercentDamage {
    var next = state
    val lines = mutableListOf<String>()
    state.party.memberIds.distinct().forEach { characterId ->
      val character = next.characters[characterId] ?: return@forEach
      if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return@forEach
      if (characterId in evadingCharacterIds) {
        lines += "${character.name} né hoàn toàn nhờ +100% Evasion của DEVIL TRIGGER"
        return@forEach
      }
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

  private fun encode(state: GameState, c: Snapshot): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata["${PREFIX}encounterId"] = c.encounterId
    metadata["${PREFIX}entityKey"] = c.entityKey
    metadata["${PREFIX}entityName"] = c.entityName
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
    val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_MAX_HP; KAI_DEVIL_WITHIN_KEY -> KAI_DEVIL_WITHIN_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS }
    val canonicalMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY) 200 else 0
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
    val defeated = state.metadata["${PREFIX}entityCondition"] == EntityCondition.DESTROYED.name
    val defeatId = state.metadata["${PREFIX}encounterId"].orEmpty()
    val lootResolvedState = if (defeated && defeatId.isNotBlank()) EntityLootEngine.onDefeat(state, defeatId, lootRng) else state
    val metadata = lootResolvedState.metadata.filterKeys { !it.startsWith(PREFIX) }
    var next = scp173RemoveAllTransientStatuses(lootResolvedState.copy(metadata = metadata))
    next.party.memberIds.filter { it != KAI_ID }.distinct().forEach { companionId ->
      val hp = next.characters[companionId]?.vitalState?.currentHp ?: return@forEach
      next = CharacterStatEngine.setCurrentHp(next, companionId, hp)
    }
    return next
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
