package com.rabpit.backroom.core

import org.json.JSONArray
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
  private const val WAVE_KEYS = "combat.waveKeys"
  private const val WAVE_INDEX = "combat.waveIndex"
  private const val ENTITY_HP_BONUS = 30
  private const val ENTITY_EVASION_PERCENT = 25
  private const val ENTITY_REGEN_PER_TURN = 1
  private const val CAO_MINH_ULTIMATE_SHOTS = 24
  private const val CAO_MINH_ULTIMATE_ACCURACY_PERCENT = 200
  private const val CAO_MINH_ULTIMATE_DAMAGE_PER_SHOT = 10
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
  private const val LUCIA_BLEED_TURNS_KEY = "combat.luciaBleedTurns"
  private const val LUCIA_BLEED_PERCENT_KEY = "combat.luciaBleedPercent"
  private const val LUCIA_POISON_TURNS_KEY = "combat.luciaPoisonTurns"
  private const val LUCIA_POISON_PERCENT_KEY = "combat.luciaPoisonPercent"
  private const val LUCIA_ARMOR_BREAK_TURNS_KEY = "combat.luciaArmorBreakTurns"
  private const val LUCIA_ARMOR_BREAK_PERCENT_KEY = "combat.luciaArmorBreakPercent"
  private const val LUCIA_ULTIMATE_SHOTS = 60
  private data class LuciaProc(val name: String, val chance: Int, val damageBonus: Int,
                               val status: FeedbackStatus, val turns: Int, val value: Int)
  private data class LuciaTick(val key: String, val turns: Int, val percent: Int,
                               val status: FeedbackStatus)
  private val luciaProcs = listOf(
    LuciaProc("Toxic Burst", 52, 25, FeedbackStatus.POISON, 2, 3),
    LuciaProc("Armor-Piercing Burst", 55, 20, FeedbackStatus.ARMOR, 2, 10),
    LuciaProc("Concussive Burst", 46, 15, FeedbackStatus.STUN, 1, 0),
    LuciaProc("Rending Burst", 49, 20, FeedbackStatus.BLEED, 2, 3),
    LuciaProc("Corrosive Burst", 51, 20, FeedbackStatus.POISON, 2, 4)
  )
  private const val CAO_MINH_FOUR_SLASH_CHANCE_PERCENT = 30
  private const val CAO_MINH_FOUR_SLASH_DAMAGE_PERCENT = 170
  private const val CAO_MINH_FOUR_SLASH_BLEED_TURNS = 3
  private const val CAO_MINH_FOUR_SLASH_BLEED_MAX_HP_PERCENT = 5
  private const val CAO_MINH_STUN_SLASH_CHANCE_PERCENT = 20
  private const val CAO_MINH_STUN_SLASH_DAMAGE_PERCENT = 130
  private const val CAO_MINH_SWORD_DASH_CHANCE_PERCENT = 20
  private const val CAO_MINH_SWORD_DASH_DAMAGE_PERCENT = 147
  private const val CAO_MINH_QUICK_STEP_CHANCE_PERCENT = 30
  private const val CAO_MINH_QUICK_STEP_EVASION_BONUS_PERCENT = 50
  private const val CAO_MINH_QUICK_STEP_DURATION_TURNS = 3
  private const val CAO_MINH_BLEED_TURNS_KEY = "combat.caoMinhBleedTurns"
  private const val CAO_MINH_QUICK_STEP_TURNS_KEY = "combat.caoMinhQuickStepTurns"
  private const val IRIS_ANALYZED_TURNS_KEY = "combat.irisAnalyzedTurns"
  private const val IRIS_ARMOR_BREAK_TURNS_KEY = "combat.irisArmorBreakTurns"
  private const val IRIS_EXPOSED_TURNS_KEY = "combat.irisExposedTurns"
  private const val SYVIAL_BLEED_TURNS_KEY = "combat.syvialBleedTurns"
  private const val SYVIAL_DEVIL_TRIGGER_KEY = "combat.syvialDevilTrigger"
  private const val SYVIAL_DISORIENT_TURNS_KEY = "combat.syvialDisorientTurns"
  private const val AN_NHIEN_ULTIMATE_INTERVAL_TURNS = 5

  enum class Phase { PENDING_ENCOUNTER_CONFIRMATION, ACTIVE, RESOLVED }
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
    val actorId: String = CAO_MINH_ID,
    val phase: String = "actor",
    val skill: String = "đánh thường",
    val sourceName: String = "",
    val entityName: String = "",
    val remainingHp: Int? = null,
    val maxHp: Int? = null,
    val outcome: String = "hit"
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("target", target); put("actorId", actorId)
      put("outcome", outcome); put("text", if (outcome == "miss") "MISS" else "-$amount HP")
      put("critical", critical); put("flash", outcome == "hit"); put("phase", phase)
      put("skill", skill); put("sourceName", sourceName); put("entityName", entityName)
      remainingHp?.let { put("remainingHp", it) }
      maxHp?.let { put("maxHp", it) }
      status?.let { put("status", it.label) }
    }
  }

  /** Transient presentation cues; indices refer to the separate damage event stream. */
  data class TurnCue(
    val kind: String,
    val eventIndex: Int,
    val actorId: String = "",
    val actorName: String = "",
    val waveNumber: Int,
    val waveCount: Int,
    val entityName: String,
    val entityKey: String,
    val logIndex: Int = 0,
    val details: List<String> = emptyList()
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("kind", kind); put("eventIndex", eventIndex)
      put("actorId", actorId); put("actorName", actorName)
      put("waveNumber", waveNumber); put("waveCount", waveCount)
      put("entityName", entityName); put("entityKey", entityKey)
      put("details", JSONArray(details))
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
    val feedback: List<DamageFeedback> = emptyList(),
    val turnCues: List<TurnCue> = emptyList()
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

  /** Single source of truth for Milestone final Entity eligibility. */
  fun supportsEntityKey(key: String): Boolean = key in profiles

  /** Gemini receives actual combat-supported Entity keys, not a separate lore-only roster. */
  fun supportedEntityKeys(): List<String> = profiles.keys.sorted()

  fun entityName(key: String): String = profiles.getValue(key).displayName

  fun active(state: GameState): Snapshot? = decode(state)?.takeIf { it.phase == Phase.ACTIVE }

  fun pending(state: GameState): Snapshot? = decode(state)?.takeIf {
    it.phase == Phase.PENDING_ENCOUNTER_CONFIRMATION
  }

  /** Reserve one Core encounter without entering Combat or touching character HP. */
  fun prepareEncounter(state: GameState, entityKey: String, encounterId: String, boss: Boolean): GameState {
    require(active(state) == null && pending(state) == null) { "encounter_already_reserved" }
    require(supportsEntityKey(entityKey) && encounterId.isNotBlank()) { "encounter_invalid" }
    val prepared = createEncounter(state, listOf(entityKey), Phase.PENDING_ENCOUNTER_CONFIRMATION, encounterId)
    if (!boss) return prepared
    val fight = requireNotNull(pending(prepared))
    val hp = (fight.entityMaxHp.toLong() * StoryMilestonePlan.FINAL_HP_MULTIPLIER)
      .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return prepared.copy(metadata = prepared.metadata + mapOf(
      "combat.entityHp" to hp.toString(), "combat.entityMaxHp" to hp.toString(),
      "combat.milestoneBoss" to encounterId
    ))
  }

  /** A repeated click is a no-op; identity never comes from narrator/client Entity keys. */
  fun confirmEncounter(state: GameState, encounterId: String): GameState {
    val reserved = pending(state)
    if (reserved == null) {
      require(active(state)?.encounterId == encounterId) { "encounter_confirmation_stale" }
      return state
    }
    require(reserved.encounterId == encounterId) { "encounter_confirmation_stale" }
    val flags = JSONObject(state.world["flagsJson"] ?: "{}").put("entityEncounterKey", reserved.entityKey)
    return state.copy(metadata = state.metadata + ("combat.phase" to Phase.ACTIVE.name),
      world = state.world + ("flagsJson" to flags.toString()))
  }

  fun pendingJson(state: GameState): JSONObject? = pending(state)?.let {
    JSONObject().put("phase", it.phase.name).put("encounterId", it.encounterId)
      .put("entityKey", it.entityKey).put("entityName", profiles.getValue(it.entityKey).displayName)
  }

  fun start(state: GameState, entityKey: String): GameState = startWaves(state, listOf(entityKey))

  /** The final Milestone Entity is a single verified Combat instance. */
  fun startMilestoneBoss(state: GameState, entityKey: String): GameState {
    require(active(state) == null) { "milestone_combat_already_active" }
    val started = start(state, entityKey)
    val fight = requireNotNull(active(started)) { "milestone_entity_unsupported" }
    val hp = (fight.entityMaxHp.toLong() * StoryMilestonePlan.FINAL_HP_MULTIPLIER)
      .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return started.copy(metadata = started.metadata + mapOf(
      "combat.entityHp" to hp.toString(),
      "combat.entityMaxHp" to hp.toString(),
      "combat.milestoneBoss" to fight.encounterId
    ))
  }

  /** Only explicitly composed encounters contain multiple Entities. */
  fun startWaves(state: GameState, entityKeys: List<String>): GameState {
    if (active(state) != null || pending(state) != null) return state
    return createEncounter(state, entityKeys, Phase.ACTIVE)
  }

  private fun createEncounter(state: GameState, entityKeys: List<String>, phase: Phase,
                              encounterId: String? = null): GameState {
    if (entityKeys.size !in 1..4 || entityKeys.any { it !in profiles }) return state
    val entityKey = entityKeys.first()
    val profile = profiles.getValue(entityKey)
    val effective = CharacterStatEngine.effective(state, CAO_MINH_ID)
    val playerMax = effective.maxHp
    val playerHp = state.characters[CAO_MINH_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    // Every encounter gets a persistent local serial; equal turns/time cannot duplicate rewards.
    val serial = (state.metadata["loot.encounterSerial"]?.toLongOrNull() ?: 0L) + 1L
    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes xor serial)
    val progressionRank = EntityPowerScaling.rankFor(state)
    val unscaledEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val enhancedEntityMaxHp = EntityPowerScaling.scale(unscaledEntityMaxHp, progressionRank)
    val snapshot = Snapshot(
      encounterId = encounterId ?: "$serial:${state.turn.currentTurnId}:$entityKey",
      entityKey = entityKey,
      entityName = profile.displayName,
      progressionRank = progressionRank,
      phase = phase,
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
    val metadata = state.metadata + mapOf(
      "loot.encounterSerial" to serial.toString(),
      WAVE_KEYS to entityKeys.joinToString("|"),
      WAVE_INDEX to "0"
    )
    val encoded = encode(state.copy(metadata = metadata), snapshot)
    return if (phase == Phase.PENDING_ENCOUNTER_CONFIRMATION) encoded.copy(characters = state.characters) else encoded
  }

  fun resolve(state: GameState, actionKind: String, action: String): Resolution {
    val current = active(state) ?: return Resolution(state, handled = false)
    val originalProfile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    var profile = if ((state.metadata["combat.caoMinhProc1"]?.toIntOrNull() ?: 0) > 0) originalProfile.copy(armor = originalProfile.armor * 90 / 100) else originalProfile
    val intent = classify(actionKind, action)
    val milestoneBoss = state.world["story.milestone.bossEncounter"] == current.encounterId
    require(!milestoneBoss || intent != Intent.ESCAPE) { "milestone_boss_escape_forbidden" }
    // One finalized Poker Dice hand authorizes Ultimates for the current Party action only.
    val ultimateHand = if (intent == Intent.ATTACK &&
      PokerDiceCore.isFinalizedFor(state, action, current.encounterId))
      PokerDiceCore.diceJson(state)?.optString("hand").orEmpty() else ""
    val ultimateReady = ultimateHand == "SSF" || ultimateHand == "FSF"
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
    val feedback = mutableListOf<DamageFeedback>()
    val turnCues = mutableListOf<TurnCue>()
    var resolvedState = state
    var currentActingId = CAO_MINH_ID

    fun entityDodges(actorId: String, salt: Int): Boolean {
      val attacker = CharacterStatEngine.effective(resolvedState, actorId)
      val chance = CharacterStatCore.effectiveChanceBasisPoints(
        ENTITY_EVASION_PERCENT * 100, attacker.resEvasionBasisPoints)
      return roll(c.copy(eventCounter = c.eventCounter + salt), 10000) < chance
    }

    fun actorCritical(actorId: String, salt: Int): Boolean {
      val attacker = CharacterStatEngine.effective(resolvedState, actorId)
      val passive = when {
        CharacterStatCore.isCaoMinh(actorId) ->
          CaoMinhCombatPassive.CRITICAL_PER_TURN_PERCENT.toLong() * CaoMinhCombatPassive.stacks(resolvedState) * 100L
        CAO_MINH_ID in PartyCombatHealth.actingMembers(resolvedState) ->
          CaoMinhCombatPassive.ALLY_CRITICAL_BONUS_PERCENT.toLong() * 100L
        else -> 0L
      }
      val chance = (attacker.criticalChanceBasisPoints.toLong() + passive).coerceIn(0L, 10000L).toInt()
      return roll(c.copy(eventCounter = c.eventCounter + salt), 10000) < chance
    }
    fun cue(kind: String, actorId: String = "", actorName: String = "") {
      val queue = waveKeys(resolvedState, c.entityKey)
      turnCues += TurnCue(kind, feedback.size, actorId, actorName,
        waveIndex(resolvedState, queue.size) + 1, queue.size, c.entityName, c.entityKey,
        logIndex = log.size)
    }
    fun presentedCues(): List<TurnCue> = turnCues.mapIndexed { index, turn ->
      val from = turn.logIndex.coerceIn(0, log.size)
      val to = turnCues.getOrNull(index + 1)?.logIndex?.coerceIn(from, log.size) ?: log.size
      turn.copy(details = log.subList(from, to).filter { it.isNotBlank() && !it.startsWith("PARTY TURN ") })
    }
    fun recordFeedback(target: String, before: Int, after: Int,
                       critical: Boolean = false, status: FeedbackStatus? = null,
                       phase: String = "actor", actorId: String = currentActingId,
                       skill: String = "đánh thường", outcome: String = "hit") {
      val amount = (before - after).coerceAtLeast(0)
      if (amount > 0) feedback += DamageFeedback(
        target, amount, critical, status, actorId = actorId, phase = phase, skill = skill,
        sourceName = if (target == "entity") resolvedState.characters[actorId]?.name ?: actorId else c.entityName,
        entityName = c.entityName,
        remainingHp = if (target == "entity") after else null,
        maxHp = if (target == "entity") c.entityMaxHp else null, outcome = outcome
      )
    }
    fun recordMiss(target: String, actorId: String = currentActingId,
                   phase: String = "actor", skill: String = "đánh thường") {
      feedback += DamageFeedback(target, 0, actorId = actorId, phase = phase,
        skill = skill, sourceName = if (target == "entity")
          resolvedState.characters[actorId]?.name ?: actorId else c.entityName,
        entityName = c.entityName, outcome = "miss")
    }
    var lifeformBleedTurns = state.metadata[LIFEFORM_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, LIFEFORM_BLEED_TURNS) ?: 0
    var lifeformPoisonTurns = state.metadata[LIFEFORM_POISON_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, LIFEFORM_POISON_TURNS) ?: 0
    var bleedTurns = state.metadata[CAO_MINH_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, CAO_MINH_FOUR_SLASH_BLEED_TURNS) ?: 0
    var quickStepTurns = state.metadata[CAO_MINH_QUICK_STEP_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, CAO_MINH_QUICK_STEP_DURATION_TURNS) ?: 0
    var entityStunnedThisTurn = false
    var luciaBleedTurns = state.metadata[LUCIA_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var luciaBleedPercent = state.metadata[LUCIA_BLEED_PERCENT_KEY]?.toIntOrNull()?.coerceIn(0, 25) ?: 0
    var luciaPoisonTurns = state.metadata[LUCIA_POISON_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var luciaPoisonPercent = state.metadata[LUCIA_POISON_PERCENT_KEY]?.toIntOrNull()?.coerceIn(0, 25) ?: 0
    var luciaArmorTurns = state.metadata[LUCIA_ARMOR_BREAK_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var luciaArmorPercent = state.metadata[LUCIA_ARMOR_BREAK_PERCENT_KEY]?.toIntOrNull()?.coerceIn(0, 75) ?: 0
    if (luciaArmorTurns > 0) profile = profile.copy(armor = profile.armor * (100 - luciaArmorPercent) / 100)
    // Status damage begins on the following turn; each effect stays on the current Entity only.
    for ((key, turns, percent, status) in listOf(
      LuciaTick(LUCIA_BLEED_TURNS_KEY, luciaBleedTurns, luciaBleedPercent, FeedbackStatus.BLEED),
      LuciaTick(LUCIA_POISON_TURNS_KEY, luciaPoisonTurns, luciaPoisonPercent, FeedbackStatus.POISON)
    )) {
      if (turns > 0 && percent > 0 && c.entityHp > 0) {
        val damage = max(1, c.entityMaxHp * percent / 100)
        val hp = max(0, c.entityHp - damage)
        recordFeedback("entity", c.entityHp, hp, status = status, actorId = LUCIA_ID,
          skill = if (status == FeedbackStatus.BLEED) "Rending Burst" else "Toxic/Corrosive Burst", outcome = "tick")
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        log += "Lucia M4A1 ${status.label}: -${damage} HP."
      }
      if (turns > 0) resolvedState = withCombatCounter(resolvedState, key, turns - 1)
    }
    luciaBleedTurns = (luciaBleedTurns - 1).coerceAtLeast(0)
    luciaPoisonTurns = (luciaPoisonTurns - 1).coerceAtLeast(0)
    if (luciaBleedTurns == 0) resolvedState = withCombatCounter(resolvedState, LUCIA_BLEED_PERCENT_KEY, 0)
    if (luciaPoisonTurns == 0) resolvedState = withCombatCounter(resolvedState, LUCIA_POISON_PERCENT_KEY, 0)
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
          recordFeedback("entity", c.entityHp, hp, status = feedbackStatus(spec[3]), skill = spec[0], outcome = "tick")
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
      recordFeedback("actor", c.playerHp, hp, status = FeedbackStatus.BLEED, phase = "entity", outcome = "tick")
      c = c.copy(playerHp = hp)
      lifeformBleedTurns = max(0, lifeformBleedTurns - 1)
      resolvedState = withLifeformCounter(resolvedState, LIFEFORM_BLEED_TURNS_KEY, lifeformBleedTurns)
      log += "Lifeform Bleed: Cao Minh -" + bleedDamage + " HP (" + LIFEFORM_BLEED_MAX_HP_PERCENT + "% Max HP); còn " + lifeformBleedTurns + " turn."
    }
    if (c.playerHp > 0 && lifeformPoisonTurns > 0) {
      val poisonDamage = percentDamage(c.playerMaxHp, LIFEFORM_POISON_MAX_HP_PERCENT)
      val hp = max(0, c.playerHp - poisonDamage)
      recordFeedback("actor", c.playerHp, hp, status = FeedbackStatus.POISON, phase = "entity", outcome = "tick")
      c = c.copy(playerHp = hp)
      lifeformPoisonTurns = max(0, lifeformPoisonTurns - 1)
      resolvedState = withLifeformCounter(resolvedState, LIFEFORM_POISON_TURNS_KEY, lifeformPoisonTurns)
      log += "Lifeform Poison: Cao Minh -" + poisonDamage + " HP (" + LIFEFORM_POISON_MAX_HP_PERCENT + "% Max HP); còn " + lifeformPoisonTurns + " turn."
    }

    var canCounterAttack = true
    var newWaveAwaitingAction = false
    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.
    val irisActive = activePartyCharacter(resolvedState, IRIS_ID) != null
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    val syvialActive = syvialCharacter != null
    val anNhienActive = activePartyCharacter(resolvedState, AN_NHIEN_ID) != null
    val lucTramActive = activePartyCharacter(resolvedState, LUC_TRAM_ID) != null


    fun promoteDefeatedWave(): Boolean {
      if (c.entityHp > 0) return false
      val keys = waveKeys(resolvedState, c.entityKey)
      if (waveIndex(resolvedState, keys.size) + 1 >= keys.size) return false
      val advanced = settleDefeated(resolvedState, c, "", emptyList(), leaderPassive = false)
      resolvedState = advanced.state
      c = active(resolvedState) ?: error("Wave handoff must preserve active combat")
      profile = profiles.getValue(c.entityKey)
      cue("wave")
      log += advanced.reply
      bleedTurns = 0
      syvialBleedTurns = 0
      irisAnalyzedTurns = 0
      irisArmorBreakTurns = 0
      irisExposedTurns = 0
      syvialDisorientTurns = 0
      companionEnemyAccuracyPenalty = 0
      entityStunnedThisTurn = false
      luciaBleedTurns = 0
      luciaPoisonTurns = 0
      luciaArmorTurns = 0
      luciaArmorPercent = 0
      canCounterAttack = false
      newWaveAwaitingAction = true
      return true
    }
    if (c.entityHp <= 0) promoteDefeatedWave()

    // Shared passives also run when the Party chooses a non-attack action.
    fun applySyvialPassive() {
      if (!syvialActive || c.entityHp <= 0) return
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
    }

    if (intent != Intent.ATTACK) cue("party", actorName = "Cả Party")
    when (intent) {
      Intent.READ -> {
        c = c.copy(
          telegraphRevealed = true,
          opening = min(3, c.opening + 1),
          momentum = min(3, c.momentum + 1)
        )
        log += "Cao Minh đọc được nhịp tấn công của ${c.entityName}; sơ hở tăng lên."
        if (lucTramActive) {
          c = c.copy(opening = min(3, c.opening + 1))
          log += "Thiên Kiếm Linh Tâm: Lục Trầm nhận biết quỹ đạo, tạo thêm một opening; không suy ra bản chất Entity."
        }
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
        if (lucTramActive) {
          c = c.copy(cover = Cover.HARD)
          log += "Vạn Kiếm Quy Tâm: Lục Trầm dùng kiếm khí che tuyến rút, Party có HARD cover."
        }
        if (CAO_MINH_ID in PartyCombatHealth.actingMembers(resolvedState) && c.entityHp > 0 &&
            roll(c.copy(eventCounter = c.eventCounter + 139), 100) < CAO_MINH_QUICK_STEP_CHANCE_PERCENT) {
          quickStepTurns = CAO_MINH_QUICK_STEP_DURATION_TURNS
          resolvedState = withCombatCounter(resolvedState, CAO_MINH_QUICK_STEP_TURNS_KEY, quickStepTurns)
          log += "Thiên Ma Bộ tự động kích hoạt: dịch chuyển ngắn liên tục, +${CAO_MINH_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${CAO_MINH_QUICK_STEP_DURATION_TURNS} turn."
        }
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
        if (lucTramActive) {
          c = c.copy(opening = min(3, c.opening + 1))
          log += "Vạn Kiếm Quy Tâm: Lục Trầm khóa hướng tiến công của đối thủ, thêm opening."
        }
      }
      Intent.ESCAPE -> {
        log += "PARTY ACTION BỎ CHẠY: ${activePartyNames(resolvedState)} cùng rút khỏi encounter trong một combat turn."
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 } + PokerDiceCore.escapeBonus(state)
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        log += "Cao Minh dồn ưu thế vào đường thoát (${c.escapeProgress}%)."
        if (anNhienActive && c.entityHp > 0 && c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0) {
          companionEnemyAccuracyPenalty += 20
          c = c.copy(escapeProgress = min(100, c.escapeProgress + 30))
          log += "Kế Hoạch Không Có Trong Kế Hoạch: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại."
        }
      }
      Intent.ATTACK -> {
        // Shared tactical opening at command start; Cao Minh may spend his own
        // opening before Lục Trầm acts, but that must not invalidate a planned Kiếm Vực.
        val openingAtPartyCommandStart = c.opening
        val partyOrder = PartyCombatHealth.actingMembers(resolvedState)
        log += "PARTY ACTION TẤN CÔNG: ${activePartyNames(resolvedState)} cùng khai triển đòn đánh trong một combat turn."
        for ((turnIndex, actorId) in partyOrder.withIndex()) {
          if (c.entityHp <= 0) break
          if (newWaveAwaitingAction) {
            canCounterAttack = true
            newWaveAwaitingAction = false
          }
          val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6 + PokerDiceCore.accuracyBonus(state)).coerceIn(20, 99)
          val actor = resolvedState.characters.getValue(actorId)
          currentActingId = actorId
          cue("actor", actorId, actor.name)
          log += "PARTY TURN ${turnIndex + 1}/${partyOrder.size}: ${actor.name}."
          when (actorId) {
            CAO_MINH_ID -> {
        val roll = roll(c, 100)
        val entityEvaded = entityDodges(CAO_MINH_ID, 13)
        if (roll < hitChance && !entityEvaded) {
          val variance = 2 + roll(c.copy(eventCounter = c.eventCounter + 17), 7)
          val effective = CharacterStatEngine.effective(state, CAO_MINH_ID)
          val weaponDamage = CharacterStatEngine.weaponDamage(state, CAO_MINH_ID)
          val critical = actorCritical(CAO_MINH_ID, 23)
          val rawBase = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val base = CharacterStatCore.basicDamage(rawBase, effective.str, PokerDiceCore.attackPercent(resolvedState), CaoMinhCombatPassive.attackPercent(resolvedState))
          val normalized = if (critical) CharacterStatCore.criticalDamage(base) else base
          // The ordinary hit limiter must not silently turn a 400% CRIT into a normal hit.
          val damage = if (critical) max(1, normalized - profile.armor)
            else min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100))
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
          recordMiss("entity")
          log += if (entityEvaded) "${c.entityName} né đòn (25% evasion) và giành lại áp lực." else "Đòn đánh trượt; ${c.entityName} giành lại áp lực."
        }
            // PARTY_ATTACK_GCO_GATE_V1
            if (intent == Intent.ATTACK && CAO_MINH_ID in PartyCombatHealth.actingMembers(resolvedState)) {
            if (c.entityHp > 0 && ultimateReady) {
              val damagePerSlash = CharacterStatEngine.skillBaseDamage(resolvedState, CAO_MINH_ID) * 115 / 100
              val totalDamage = CAO_MINH_ULTIMATE_SHOTS * damagePerSlash
              val hp = max(0, c.entityHp - totalDamage)
              recordFeedback("entity", c.entityHp, hp, skill = "Huyết Ma Nhị Thập Tứ Trảm")
              c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
              log += "Huyết Ma Nhị Thập Tứ Trảm kích hoạt với Poker Dice [$ultimateHand]: $CAO_MINH_ULTIMATE_SHOTS/" +
                "$CAO_MINH_ULTIMATE_SHOTS trảm trúng liên tiếp, Accuracy $CAO_MINH_ULTIMATE_ACCURACY_PERCENT%, bỏ qua toàn bộ hiệu ứng né; " +
                "mỗi trảm -$damagePerSlash HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."
            }
        
            }
        
            val isCaoMinhUltimateTurn = ultimateReady && CAO_MINH_ID in PartyCombatHealth.actingMembers(resolvedState)
            if (intent == Intent.ATTACK && CAO_MINH_ID in PartyCombatHealth.actingMembers(resolvedState) && c.entityHp > 0 && !isCaoMinhUltimateTurn) {
              procSpecs.forEachIndexed { index, spec ->
                if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 401 + index * 17), 100) < spec[1].toInt()) {
                  val damage = CharacterStatCore.scaleByPercent(
                    CharacterStatEngine.skillBaseDamage(resolvedState, CAO_MINH_ID), spec[2].toInt())
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = feedbackStatus(spec[3]), skill = spec[0])
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                  if (spec[3] == "stun") entityStunnedThisTurn = true
                  else resolvedState = withCombatCounter(resolvedState, "combat.caoMinhProc$index", 2)
                  log += "${spec[0]} kích hoạt: -$damage HP; ${spec[3]}."
                }
              }
            }
            if (c.entityHp > 0 && CAO_MINH_ID in PartyCombatHealth.actingMembers(resolvedState)) {
              val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, CAO_MINH_ID)
        
              if (intent == Intent.ATTACK && !isCaoMinhUltimateTurn && roll(c.copy(eventCounter = c.eventCounter + 101), 100) < CAO_MINH_FOUR_SLASH_CHANCE_PERCENT) {
                val damage = weaponSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, CAO_MINH_ID), CAO_MINH_FOUR_SLASH_DAMAGE_PERCENT, profile.armor)
                val hp = max(0, c.entityHp - damage)
                recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED, skill = "Huyết Ma Tứ Liên")
                c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 22))
                bleedTurns = CAO_MINH_FOUR_SLASH_BLEED_TURNS
                resolvedState = withCombatCounter(resolvedState, CAO_MINH_BLEED_TURNS_KEY, bleedTurns)
                log += "Huyết Ma Tứ Liên tự động kích hoạt: 4 trảm vào điểm nối hộ thể, ${CAO_MINH_FOUR_SLASH_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${CAO_MINH_FOUR_SLASH_BLEED_TURNS} turn, ${CAO_MINH_FOUR_SLASH_BLEED_MAX_HP_PERCENT}% Max HP/turn."
              }
        
              if (intent == Intent.ATTACK && !isCaoMinhUltimateTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < CAO_MINH_STUN_SLASH_CHANCE_PERCENT) {
                val damage = weaponSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, CAO_MINH_ID), CAO_MINH_STUN_SLASH_DAMAGE_PERCENT, profile.armor)
                val hp = max(0, c.entityHp - damage)
                recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.STUN, skill = "Ma Tâm Trấn Hồn")
                c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
                entityStunnedThisTurn = true
                log += "Ma Tâm Trấn Hồn tự động kích hoạt: Cao Minh bật lên cao, 4 đường ma kiếm vào cùng điểm trên ngực, ${CAO_MINH_STUN_SLASH_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."
              }
        
              if (intent == Intent.ATTACK && !isCaoMinhUltimateTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < CAO_MINH_SWORD_DASH_CHANCE_PERCENT) {
                val damage = weaponSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, CAO_MINH_ID), CAO_MINH_SWORD_DASH_DAMAGE_PERCENT, profile.armor)
                val hp = max(0, c.entityHp - damage)
                recordFeedback("entity", c.entityHp, hp, skill = "Huyết Ảnh Ma Độn")
                c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 16))
                log += "Huyết Ảnh Ma Độn tự động kích hoạt: Cao Minh ngự Huyết Ma Kiếm qua mục tiêu, dịch chuyển theo kiếm và chém nhanh đúng 2 trảm, ${CAO_MINH_SWORD_DASH_DAMAGE_PERCENT}% DMG = -$damage HP."
              }
        
              if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !isCaoMinhUltimateTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < CAO_MINH_QUICK_STEP_CHANCE_PERCENT) {
                quickStepTurns = CAO_MINH_QUICK_STEP_DURATION_TURNS
                resolvedState = withCombatCounter(resolvedState, CAO_MINH_QUICK_STEP_TURNS_KEY, quickStepTurns)
                log += "Thiên Ma Bộ tự động kích hoạt: dịch chuyển ngắn liên tục, +${CAO_MINH_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${CAO_MINH_QUICK_STEP_DURATION_TURNS} turn."
              }
            }
            }
            LUCIA_ID -> {
              // The finalized Poker Dice hand is authoritative. SSF/FSF are the
              // only triggers of Lucia's sixty-shot Ultimate, as in BACKROOMsV2.
              val lucia = activePartyCharacter(resolvedState, LUCIA_ID)
              if (lucia != null && c.entityHp > 0) {
                val baseDamage = CharacterStatCore.basicDamage(
                  LUCIA_M4A1_COMBAT_DAMAGE, CharacterStatEngine.effective(resolvedState, LUCIA_ID).str)
                val hand = PokerDiceCore.diceJson(state)?.takeIf { it.optBoolean("finalized", false) }
                  ?.optString("hand").orEmpty()
                val ultimate = PokerDiceCore.isFinalizedFor(state, action, c.encounterId) &&
                  hand in setOf("SSF", "FSF")
                if (ultimate) {
                  val perShot = max(1, (baseDamage.toLong() * 115L + 50L) / 100L).toInt()
                  val total = perShot.toLong() * LUCIA_ULTIMATE_SHOTS * if (hand == "FSF") 2L else 1L
                  val damage = min(c.entityHp.toLong(), total).toInt()
                  val hp = c.entityHp - damage
                  recordFeedback("entity", c.entityHp, hp, actorId = LUCIA_ID, skill = "Too Young To Die")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 30))
                  log += "Too Young To Die [$hand]: Lucia bắn đúng 60 phát M4A1, mỗi phát 100% current DMG + 15% bonus, " +
                    (if (hand == "FSF") "FSF x200%, " else "") + "-$damage HP; không roll Evasion/Critical lần nữa."
                } else {
                  val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
                  if (luciaRoll < hitChance && !entityDodges(LUCIA_ID, 97)) {
                    val critical = actorCritical(LUCIA_ID, 101)
                    val jointDamage = CharacterStatCore.scaleByPercent(baseDamage, 150)
                    val raw = if (critical) CharacterStatCore.criticalDamage(jointDamage) else jointDamage
                    val damage = min(c.entityHp, max(1, raw - profile.armor))
                    val hp = c.entityHp - damage
                    recordFeedback("entity", c.entityHp, hp, critical = critical, actorId = LUCIA_ID, skill = "M4A1 Joint Attack")
                    c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 22))
                    log += "Lucia \"Lục\" bắn hỗ trợ bằng M4A1 (Joint Attack 150% DMG): -$damage HP."
                    // Each Burst rolls independently after a successful ordinary attack;
                    // Ultimate never procs Burst. Damage and status match Lucia R01.
                    luciaProcs.forEachIndexed { index, proc ->
                      if (c.entityHp <= 0) return@forEachIndexed
                      if (roll(c.copy(eventCounter = c.eventCounter + 701 + index * 31), 100) >= proc.chance) return@forEachIndexed
                      val bonus = max(1, ((baseDamage.toLong() * proc.damageBonus + 50L) / 100L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                      val after = max(0, c.entityHp - bonus)
                      recordFeedback("entity", c.entityHp, after, status = proc.status, actorId = LUCIA_ID, skill = proc.name)
                      c = c.copy(entityHp = after, entityCondition = condition(after, c.entityMaxHp))
                      if (after > 0) when (proc.status) {
                        FeedbackStatus.BLEED -> {
                          luciaBleedTurns = if (luciaBleedTurns > 0) luciaBleedTurns else proc.turns
                          luciaBleedPercent = (luciaBleedPercent + proc.value).coerceAtMost(25)
                          resolvedState = withCombatCounter(resolvedState, LUCIA_BLEED_TURNS_KEY, luciaBleedTurns)
                          resolvedState = withCombatCounter(resolvedState, LUCIA_BLEED_PERCENT_KEY, luciaBleedPercent)
                        }
                        FeedbackStatus.POISON -> {
                          luciaPoisonTurns = if (luciaPoisonTurns > 0) luciaPoisonTurns else proc.turns
                          luciaPoisonPercent = (luciaPoisonPercent + proc.value).coerceAtMost(25)
                          resolvedState = withCombatCounter(resolvedState, LUCIA_POISON_TURNS_KEY, luciaPoisonTurns)
                          resolvedState = withCombatCounter(resolvedState, LUCIA_POISON_PERCENT_KEY, luciaPoisonPercent)
                        }
                        FeedbackStatus.ARMOR -> {
                          luciaArmorTurns = if (luciaArmorTurns > 0) luciaArmorTurns else proc.turns
                          luciaArmorPercent = (luciaArmorPercent + proc.value).coerceAtMost(75)
                          profile = profile.copy(armor = profile.armor * (100 - proc.value) / 100)
                          resolvedState = withCombatCounter(resolvedState, LUCIA_ARMOR_BREAK_TURNS_KEY, luciaArmorTurns)
                          resolvedState = withCombatCounter(resolvedState, LUCIA_ARMOR_BREAK_PERCENT_KEY, luciaArmorPercent)
                        }
                        FeedbackStatus.STUN -> entityStunnedThisTurn = true
                        else -> Unit
                      }
                      log += "${proc.name}: +${proc.damageBonus}% DMG (-$bonus HP), " +
                        "${proc.status.label} ${proc.turns} turn."
                    }
                  } else {
                    recordMiss("entity", LUCIA_ID)
                    log += "Lucia \"Lục\" cũng khai hỏa nhưng phát bắn không trúng mục tiêu; Burst không kích hoạt."
                  }
                }
              }
            }
            IRIS_ID -> {
        // PARTY_FOLLOWER_BASE_ATTACKS_V1: all actors resolve inside this one ATTACK event.
        val irisPartyAttack = activePartyCharacter(resolvedState, IRIS_ID)
        if (irisPartyAttack != null) {
          val irisHitRoll = roll(c.copy(eventCounter = c.eventCounter + 307), 100)
          val irisEntityEvaded = entityDodges(IRIS_ID, 311)
          if (irisHitRoll < hitChance && !irisEntityEvaded) {
            val critical = actorCritical(IRIS_ID, 349)
            val basic = CharacterStatEngine.skillBaseDamage(resolvedState, IRIS_ID)
            val potential = companionSkillDamage(
              if (critical) CharacterStatCore.criticalDamage(basic) else basic, 100, profile.armor)
            val damage = min(c.entityHp, potential)
            val hp = max(0, c.entityHp - damage)
            recordFeedback("entity", c.entityHp, hp, critical = critical, actorId = IRIS_ID)
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 10))
            log += "Iris thực hiện lệnh TẤN CÔNG bằng Ivory & Ebony: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            recordMiss("entity", IRIS_ID)
            log += "Iris thực hiện lệnh TẤN CÔNG nhưng loạt bắn không trúng mục tiêu."
          }
        }

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
              val irisUltimate = ultimateReady
              if (irisUltimate) {
                val damage = companionSkillDamage(irisWeapon, 300, irisArmor)
                val hp = max(0, c.entityHp - damage)
                recordFeedback("entity", c.entityHp, hp, skill = "ARGUS // Thousandfold Execution")
                c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 30))
                irisExposedTurns = 2
                resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
                log += "ARGUS // Thousandfold Execution: 12 phát luân phiên, 300% DMG = -$damage HP; Fully Exposed 2 turn."
              } else if (intent == Intent.ATTACK) {
                if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {
                  val percent = if (irisAnalyzedTurns > 0) 170 else 155
                  val damage = companionSkillDamage(irisWeapon, percent, irisArmor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, skill = "Twosome Time")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 14))
                  log += "Twosome Time tự động kích hoạt: 2 phát chéo góc, $percent% DMG = -$damage HP."
                }
                if (roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 20 && c.entityHp > 0) {
                  val damage = companionSkillDamage(irisWeapon, 145, irisArmor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, skill = "Rain Storm")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
                  log += "Rain Storm tự động kích hoạt: 6 phát khi đổi góc trên không, 145% DMG = -$damage HP."
                }
                if (roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 20 && c.entityHp > 0) {
                  val damage = companionSkillDamage(irisWeapon, 185, irisArmor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.ARMOR, skill = "Honeycomb Fire")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
                  irisArmorBreakTurns = 2
                  resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
                  log += "Honeycomb Fire tự động kích hoạt: 8 phát tập trung, 185% DMG = -$damage HP; Armor Break 20% trong 2 turn."
                }
                if (roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 25 && c.entityHp > 0) {
                  val chargedArmor = armorAfterIgnore(profile.armor, 35)
                  val damage = companionSkillDamage(irisWeapon, 175, chargedArmor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.ARMOR, skill = "Charged Shot")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 20))
                  log += "Charged Shot tự động kích hoạt: 175% DMG = -$damage HP, bỏ qua 35% Armor."
                }
              }
            }
            }
            SYVIAL_ID -> {
        val syvialPartyAttack = activePartyCharacter(resolvedState, SYVIAL_ID)
        if (syvialPartyAttack != null) {
          val syvialHitRoll = roll(c.copy(eventCounter = c.eventCounter + 317), 100)
          val syvialEntityEvaded = entityDodges(SYVIAL_ID, 331)
          if (syvialHitRoll < hitChance && !syvialEntityEvaded) {
            val critical = actorCritical(SYVIAL_ID, 353)
            val basic = CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID)
            val potential = companionSkillDamage(
              if (critical) CharacterStatCore.criticalDamage(basic) else basic, 100, profile.armor)
            val damage = min(c.entityHp, potential)
            val hp = max(0, c.entityHp - damage)
            recordFeedback("entity", c.entityHp, hp, critical = critical, actorId = SYVIAL_ID)
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 12))
            log += "Syvial thực hiện lệnh TẤN CÔNG bằng GodKiller: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            recordMiss("entity", SYVIAL_ID)
            log += "Syvial thực hiện lệnh TẤN CÔNG nhưng Entity tránh được nhát chém."
          }
        }

            if (syvialActive && c.entityHp > 0) {
              applySyvialPassive()
              val syvialWeapon = CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID)
              val dtMultiplier = if (syvialDevilTrigger) 125 else 100
              fun syvialDamage(percent: Int, armor: Int): Int = companionSkillDamage(syvialWeapon, (percent * dtMultiplier + 99) / 100, armor)
              val syvialUltimate = ultimateReady && syvialDevilTrigger
              if (syvialUltimate) {
                val damage = min(c.entityHp, 24 * 10)
                val hp = max(0, c.entityHp - damage)
                recordFeedback("entity", c.entityHp, hp, skill = "GodKiller Override // Twenty-Four Severance")
                c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
                log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x 10 HP = -$damage HP; bỏ qua Evasion."
              } else if (intent == Intent.ATTACK) {
                if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {
                  val damage = syvialDamage(175, armorAfterIgnore(profile.armor, 20))
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.ARMOR, skill = "Rift Sever")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                  log += "Rift Sever tự động kích hoạt: Spatial Shift + GodKiller, 175% DMG = -$damage HP."
                }
                if (roll(c.copy(eventCounter = c.eventCounter + 223), 100) < 20 && c.entityHp > 0) {
                  val damage = syvialDamage(190, profile.armor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED, skill = "Crimson Guillotine")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                  syvialBleedTurns = 3
                  resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
                  log += "Crimson Guillotine tự động kích hoạt: 190% DMG = -$damage HP; Bleeding 3 turn x 4% Max HP."
                }
                if (roll(c.copy(eventCounter = c.eventCounter + 239), 100) < 20 && c.entityHp > 0) {
                  val damage = syvialDamage(155, profile.armor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.STUN, skill = "Lucifer Breaker")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                  entityStunnedThisTurn = true
                  log += "Lucifer Breaker tự động kích hoạt: 155% DMG = -$damage HP; Entity bị Stun trong phản ứng hiện tại."
                }
                if (syvialDevilTrigger && roll(c.copy(eventCounter = c.eventCounter + 251), 100) < 20 && c.entityHp > 0) {
                  val damage = syvialDamage(210, profile.armor)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.DISORIENT, skill = "Spatial Dominion")
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                  syvialDisorientTurns = 2
                  resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
                  log += "Spatial Dominion tự động kích hoạt: 210% DMG = -$damage HP; Disoriented -25% Accuracy trong 2 turn."
                }
              }
            }
            }
            LUC_TRAM_ID -> {
              // Sword arts relying only on sword intent can be used without
              // asserting the OPEN post-revival status of the physical Tịch Quang.
              if (activePartyCharacter(resolvedState, LUC_TRAM_ID) != null) {
                val waveId = LucTramCanon.domainKey(c.encounterId,
                  waveIndex(resolvedState, waveKeys(resolvedState, c.entityKey).size))
                if (ultimateReady && LucTramCanon.canEstablishDomain(resolvedState, openingAtPartyCommandStart, waveId)) {
                  // Setup replaces her attack action, with no unearned bonus hit.
                  c = c.copy(opening = 3, cover = Cover.HARD)
                  resolvedState = resolvedState.copy(metadata = resolvedState.metadata +
                    ("combat.lucTramDomainWave" to waveId))
                  log += "Thiên Kiếm Định Giới: Lục Trầm dùng lượt dựng kiếm ấn, cover HARD và opening 3; Kiếm Vực không khóa hình học Backrooms."
                } else {
                  val advance = c.range != RangeBand.CLOSE && c.opening == 0
                  val technique = if (advance) "Bạch Hồng Quán Nhật" else "Nhất Tuyến Phá Vọng"
                  if (advance) {
                    c = c.copy(range = if (c.range == RangeBand.FAR) RangeBand.NEAR else RangeBand.CLOSE)
                    log += "Bạch Hồng Quán Nhật: Lục Trầm đột tiến một tầm, không vượt xuyên cấu trúc Level."
                  }
                  val rangeBonus = when (c.range) {
                    RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5
                  }
                  val chance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6 +
                    PokerDiceCore.accuracyBonus(state)).coerceIn(20, 99)
                  val hits = roll(c.copy(eventCounter = c.eventCounter + 373), 100) < chance &&
                    !entityDodges(LUC_TRAM_ID, 379)
                  if (hits) {
                    val critical = actorCritical(LUC_TRAM_ID, 383)
                    val basic = CharacterStatEngine.skillBaseDamage(resolvedState, LUC_TRAM_ID)
                    val armor = if (advance) profile.armor else armorAfterIgnore(profile.armor, 20)
                    val damage = min(c.entityHp, companionSkillDamage(
                      if (critical) CharacterStatCore.criticalDamage(basic) else basic, 100, armor))
                    val hp = max(0, c.entityHp - damage)
                    recordFeedback("entity", c.entityHp, hp, critical = critical, actorId = LUC_TRAM_ID, skill = technique)
                    c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                    log += "$technique: Lục Trầm gây -$damage HP (${c.entityHp}/${c.entityMaxHp})."
                  } else {
                    recordMiss("entity", LUC_TRAM_ID, skill = technique)
                    log += "$technique: đòn đánh không trúng hoặc bị né, không bỏ qua Accuracy/Evasion."
                  }
                }
              }
            }
            AN_NHIEN_ID -> {
        if (activePartyCharacter(resolvedState, AN_NHIEN_ID) != null) {
          log += "An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ: gây nhiễu/đánh lạc hướng, không dùng vũ khí và không gây damage."
        }
            if (anNhienActive && c.entityHp > 0) {
              if (intent == Intent.ATTACK && roll(c.copy(eventCounter = c.eventCounter + 269), 100) < 25) {
                companionEnemyAccuracyPenalty += 25
                log += "An Nhiên dùng Quăng Đại Cái Gì Đó: tiếng động lệch hướng khiến Entity -25 điểm % Accuracy trong phản ứng hiện tại."
              }
            }
            }
            else -> {
              if (actor.metadata["nonCombat"] == "true" || actor.metadata["canUseWeapons"] == "false") {
                log += "${actor.name} hỗ trợ, không dùng vũ khí."
              } else {
                val hitRoll = roll(c.copy(eventCounter = c.eventCounter + 409 + turnIndex * 19), 100)
                if (hitRoll < hitChance && !entityDodges(actorId, 467 + turnIndex * 19)) {
                  val critical = actorCritical(actorId, 491 + turnIndex * 19)
                  val basic = CharacterStatEngine.skillBaseDamage(resolvedState, actorId)
                  val potential = companionSkillDamage(
                    if (critical) CharacterStatCore.criticalDamage(basic) else basic, 100, profile.armor)
                  val damage = min(c.entityHp, potential)
                  val hp = max(0, c.entityHp - damage)
                  recordFeedback("entity", c.entityHp, hp, critical = critical, actorId = actorId)
                  c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
                  log += "${actor.name} tấn công: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
                } else {
                  recordMiss("entity", actorId)
                  log += "${actor.name} tấn công nhưng trượt."
                }
              }
            }
          }
          if (c.entityHp <= 0 && !promoteDefeatedWave()) break
        }
      }
      Intent.OTHER -> {
        c = c.copy(momentum = max(-3, c.momentum - 1))
        log += "Hành động không tạo được lợi thế chiến đấu rõ ràng."
      }
    }

    if (c.entityHp > 0 && bleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, CAO_MINH_FOUR_SLASH_BLEED_MAX_HP_PERCENT)
      val hp = max(0, c.entityHp - bleedDamage)
      bleedTurns = max(0, bleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, CAO_MINH_BLEED_TURNS_KEY, bleedTurns)
      recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED, actorId = CAO_MINH_ID, skill = "Huyết Ma Tứ Liên", outcome = "tick")
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Huyết Ma Tứ Liên gây -$bleedDamage HP (${CAO_MINH_FOUR_SLASH_BLEED_MAX_HP_PERCENT}% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $bleedTurns turn."
    }

    if (c.entityHp > 0 && syvialBleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, 4)
      val hp = max(0, c.entityHp - bleedDamage)
      syvialBleedTurns = max(0, syvialBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      recordFeedback("entity", c.entityHp, hp, status = FeedbackStatus.BLEED, actorId = SYVIAL_ID, skill = "Crimson Guillotine", outcome = "tick")
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Crimson Guillotine gây -$bleedDamage HP (4% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $syvialBleedTurns turn."
    }

    if (c.entityHp <= 0 && !promoteDefeatedWave()) {
      return settleDefeated(resolvedState, c, log.joinToString(" "), feedback.toList()).copy(turnCues = presentedCues())
    }
    if (!milestoneBoss && c.escapeProgress >= 100) {
      val persisted = afterActiveLeaderTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED)))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " Cao Minh cắt được truy đuổi và thoát khỏi encounter.", escaped = true, feedback = feedback.toList(), turnCues = presentedCues())
    }

    if (syvialDisorientTurns > 0) companionEnemyAccuracyPenalty += 25

    if (c.entityHp <= 0 && !promoteDefeatedWave()) {
      return settleDefeated(resolvedState, c, log.joinToString(" "), feedback.toList()).copy(turnCues = presentedCues())
    }

    if (intent != Intent.ATTACK && c.entityHp > 0) {
      if (irisActive && irisAnalyzedTurns <= 0) {
        irisAnalyzedTurns = 3
        resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
        log += "ARGUS Terrain Read: Iris đánh dấu mục tiêu Analyzed trong 3 turn."
      }
      applySyvialPassive()
    }

    // A replacement Entity is not allowed to counterattack for free.
    currentActingId = CAO_MINH_ID
    if (canCounterAttack) cue("entity")
    if (!canCounterAttack) {
      log += "${c.entityName} vừa xuất hiện và chờ lượt giao chiến tiếp theo."
    } else if (entityStunnedThisTurn) {
      log += "Ma Tâm Trấn Hồn: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
      val groupHpBefore = PartyCombatHealth.currentHp(resolvedState)
      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)
      resolvedState = pulse.state
      recordFeedback("actor", c.playerHp, pulse.caoMinhHp, phase = "entity")
      c = c.copy(playerHp = pulse.caoMinhHp, momentum = max(-3, c.momentum - 1))
      log += "Devils And Gold kích hoạt ở combat turn ${c.eventCounter}: toàn bộ nhân vật ACTIVE đang ra trận nhận ${DIEP_MINH_ULTIMATE_PERCENT}% Max HP. ${pulse.summary}."
      val groupHpAfter = PartyCombatHealth.currentHp(resolvedState)
      log += "${c.entityName} phản công: Party chịu -${(groupHpBefore - groupHpAfter).coerceAtLeast(0)} HP (${groupHpAfter}/${PartyCombatHealth.maxHp(resolvedState)})."
    } else {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4 +
      PokerDiceCore.defenseBonus(state)
      val quickStepEvasion = if (quickStepTurns > 0) CAO_MINH_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - companionEnemyAccuracyPenalty).coerceIn(0, 88)
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
        // Ordinary counterattacks drain the Party pool once. Snapshot.playerHp
        // remains Cao Minh's canonical HP for targeted/status-specific skills.
        // Commit pre-attack personal status ticks before sharing this hit.
        val beforeHit = CharacterStatEngine.setCurrentHp(resolvedState, CAO_MINH_ID, c.playerHp)
        val sharedHit = if (c.entityKey == DIEP_MINH_KEY) {
          // Diệp Minh's percentage-based attack is a special ability, not a normal physical hit.
          PartyCombatHealth.applySharedDamage(beforeHit, damage, c.eventCounter)
        } else {
          PartyCombatHealth.applyStatAdjustedDamage(
            beforeHit, damage, c.eventCounter,
            dodgeRoll = { index -> roll(c.copy(eventCounter = c.eventCounter + 601 + index * 41), 10000) },
            criticalRoll = { index -> roll(c.copy(eventCounter = c.eventCounter + 619 + index * 41), 10000) },
            leaderEvasionBonusPercent = quickStepEvasion
          )
        }
        resolvedState = sharedHit.state
        sharedHit.damageByMember.forEach { (memberId, loss) ->
          feedback += DamageFeedback("actor", loss, actorId = memberId, phase = "entity",
            critical = memberId in sharedHit.criticalIds)
        }
        sharedHit.dodgedIds.forEach { recordMiss("actor", it, phase = "entity") }
        c = c.copy(
          playerHp = resolvedState.characters[CAO_MINH_ID]?.vitalState?.currentHp ?: c.playerHp,
          momentum = max(-3, c.momentum - 1)
        )
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
        val partyHp = PartyCombatHealth.currentHp(resolvedState)
        val partyMaxHp = PartyCombatHealth.maxHp(resolvedState)
        val distribution = sharedHit.damageByMember.entries.joinToString("; ") { (id, loss) ->
          "${resolvedState.characters[id]?.name ?: id} -$loss"
        }
        val dodged = sharedHit.dodgedIds.joinToString(", ") { resolvedState.characters[it]?.name ?: it }
        val critical = sharedHit.criticalIds.joinToString(", ") { resolvedState.characters[it]?.name ?: it }
        log += "${c.entityName} phản công: Party chịu -${sharedHit.appliedDamage} HP " +
          "($partyHp/$partyMaxHp). $distribution." +
          (if (dodged.isNotEmpty()) " Né tránh: $dodged." else "") +
          (if (critical.isNotEmpty()) " CRIT 400%: $critical." else "")
      } else {
        recordMiss("actor", CAO_MINH_ID, phase = "entity")
        log += if (quickStepTurns > 0) {
          "Thiên Ma Bộ khiến ${c.entityName} hụt đòn; +${CAO_MINH_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Cao Minh."
        }
        if (intent == Intent.ATTACK && irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
          val damage = companionSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, IRIS_ID), 120, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, phase = "entity", actorId = IRIS_ID, skill = "Dead Angle")
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
        }
        if (lucTramActive && c.entityHp > 0 && LucTramCanon.hasConfirmedSword(resolvedState)) {
          // Entity missed its standard response, so counter without spending
          // a new turn. Reuses standard combat accuracy and Evasion gates.
          val chance = (58 + c.opening * 11 + c.momentum * 6 +
            PokerDiceCore.accuracyBonus(state)).coerceIn(20, 99)
          val hits = roll(c.copy(eventCounter = c.eventCounter + 397), 100) < chance &&
            !entityDodges(LUC_TRAM_ID, 401)
          if (hits) {
            val basic = CharacterStatEngine.skillBaseDamage(resolvedState, LUC_TRAM_ID)
            val damage = min(c.entityHp, companionSkillDamage(basic, 100, profile.armor))
            val hp = max(0, c.entityHp - damage)
            recordFeedback("entity", c.entityHp, hp, phase = "entity", actorId = LUC_TRAM_ID,
              skill = "Tịch Quang Phản Kiếm")
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
            log += "Tịch Quang Phản Kiếm: Lục Trầm dẫn lực, phản kiếm -$damage HP."
          } else {
            recordMiss("entity", LUC_TRAM_ID, phase = "entity", skill = "Tịch Quang Phản Kiếm")
            log += "Tịch Quang Phản Kiếm: Lục Trầm đổi trục nhưng phản kiếm không trúng."
          }
        }
        if (intent == Intent.ATTACK && syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {
          val damage = companionSkillDamage(CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID), if (syvialDevilTrigger) 157 else 125, profile.armor)
          val hp = max(0, c.entityHp - damage)
          recordFeedback("entity", c.entityHp, hp, phase = "entity", actorId = SYVIAL_ID, skill = "Counterphase")
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
        }
      }
    }

    if (c.entityHp <= 0 && !promoteDefeatedWave()) {
      return settleDefeated(resolvedState, c, log.joinToString(" "), feedback.toList()).copy(turnCues = presentedCues())
    }
    if (quickStepTurns > 0) {
      quickStepTurns = max(0, quickStepTurns - 1)
      resolvedState = withCombatCounter(resolvedState, CAO_MINH_QUICK_STEP_TURNS_KEY, quickStepTurns)
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
    if (luciaArmorTurns > 0) {
      luciaArmorTurns = max(0, luciaArmorTurns - 1)
      resolvedState = withCombatCounter(resolvedState, LUCIA_ARMOR_BREAK_TURNS_KEY, luciaArmorTurns)
      if (luciaArmorTurns == 0) resolvedState = withCombatCounter(resolvedState, LUCIA_ARMOR_BREAK_PERCENT_KEY, 0)
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
    val next = afterActiveLeaderTurn(encode(resolvedState, c))
    return Resolution(next, true, log.joinToString(" "), feedback = feedback.toList(), turnCues = presentedCues())
  }

  fun toJson(state: GameState): JSONObject? = decode(state)?.let { c -> JSONObject().apply {
    put("active", c.phase == Phase.ACTIVE)
    put("encounterId", c.encounterId)
    put("entityKey", c.entityKey)
    put("entityName", c.entityName)
    put("progressionRank", c.progressionRank)
    // The combat HUD displays the Party pool, but the persisted playerHp in
    // Snapshot remains Cao Minh's HP for legacy targeted combat mechanics.
    put("playerHp", PartyCombatHealth.currentHp(state))
    put("playerMaxHp", PartyCombatHealth.maxHp(state))
    put("partyMemberCount", PartyCombatHealth.actingMembers(state).size)
    put("entityHp", c.entityHp); put("entityMaxHp", c.entityMaxHp)
    val queue = waveKeys(state, c.entityKey)
    val wave = waveIndex(state, queue.size)
    put("entityInstanceId", "${c.encounterId}:wave:$wave")
    put("round", c.eventCounter + 1)
    put("waveNumber", wave + 1)
    put("waveCount", queue.size)
    put("remainingEntityCount", queue.size - wave)
    put("entityCondition", c.entityCondition.name)
    put("range", c.range.name); put("cover", c.cover.name)
    put("momentum", c.momentum); put("opening", c.opening)
    put("escapeProgress", c.escapeProgress); put("noise", c.noise)
    put("telegraph", if (c.telegraphRevealed) c.telegraph else "UNKNOWN")
    put("telegraphRevealed", c.telegraphRevealed)
  } }

  fun clear(state: GameState): GameState = clearCombatOnly(state)

  private fun waveKeys(state: GameState, currentKey: String): List<String> =
    state.metadata[WAVE_KEYS]?.split('|')?.takeIf { keys ->
      keys.size in 1..4 && keys.all { it in profiles }
    } ?: listOf(currentKey)

  private fun waveIndex(state: GameState, size: Int): Int =
    state.metadata[WAVE_INDEX]?.toIntOrNull()?.coerceIn(0, size - 1) ?: 0

  /** Entity-specific debuffs never cross the boundary into a different Entity. */
  private fun clearTargetEffects(state: GameState): GameState {
    val targetKeys = setOf(
      CAO_MINH_BLEED_TURNS_KEY, IRIS_ANALYZED_TURNS_KEY, IRIS_ARMOR_BREAK_TURNS_KEY,
      IRIS_EXPOSED_TURNS_KEY, SYVIAL_BLEED_TURNS_KEY, SYVIAL_DISORIENT_TURNS_KEY,
      LUCIA_BLEED_TURNS_KEY, LUCIA_BLEED_PERCENT_KEY, LUCIA_POISON_TURNS_KEY,
      LUCIA_POISON_PERCENT_KEY, LUCIA_ARMOR_BREAK_TURNS_KEY, LUCIA_ARMOR_BREAK_PERCENT_KEY
    )
    return state.copy(metadata = state.metadata.filterKeys { key ->
      key !in targetKeys && !key.startsWith("combat.caoMinhProc")
    })
  }

  /** Defeat settles one instance. Only the final wave ends the encounter. */
  private fun settleDefeated(
    state: GameState, c: Snapshot, log: String,
    feedback: List<DamageFeedback>, leaderPassive: Boolean = true
  ): Resolution {
    val queue = waveKeys(state, c.entityKey)
    val index = waveIndex(state, queue.size)
    val persisted = encode(state, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
    val withPassive = if (leaderPassive) afterActiveLeaderTurn(persisted) else persisted
    val instanceId = if (queue.size == 1) c.encounterId else "${c.encounterId}:wave:$index"
    val expMultiplier = if (state.metadata["combat.milestoneBoss"] == c.encounterId &&
      queue.size == 1) StoryMilestonePlan.FINAL_EXP_MULTIPLIER else 1
    val exp = EntityExpRewards.award(withPassive, instanceId, c.progressionRank, expMultiplier)
    val loot = OfflineEntityLoot.award(exp.state, instanceId, c.seed)
    val resultText = "$log ${c.entityName} đã bị tiêu diệt. ${exp.text} ${loot.text}".trim()
    if (index + 1 == queue.size) {
      return Resolution(clearCombatOnly(loot.state), true, resultText, entityDestroyed = true, feedback = feedback)
    }

    val nextIndex = index + 1
    val nextProfile = profiles.getValue(queue[nextIndex])
    val baseMax = if (nextProfile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else nextProfile.maxHp + ENTITY_HP_BONUS
    val maxHp = EntityPowerScaling.scale(baseMax, c.progressionRank)
    val nextSeed = mix(c.seed, nextIndex)
    val clean = clearTargetEffects(loot.state)
    val leaderHp = clean.characters[CAO_MINH_ID]?.vitalState?.currentHp ?: c.playerHp
    val next = c.copy(
      entityKey = nextProfile.key, entityName = nextProfile.displayName,
      entityHp = maxHp, entityMaxHp = maxHp, entityCondition = EntityCondition.HEALTHY,
      playerHp = leaderHp, phase = Phase.ACTIVE, opening = 0, noise = 0,
      telegraph = telegraphFor(nextProfile, nextSeed, c.eventCounter),
      telegraphRevealed = false, seed = nextSeed
    )
    val resumed = encode(clean.copy(metadata = clean.metadata + (WAVE_INDEX to nextIndex.toString())), next)
    return Resolution(
      resumed, true,
      "$resultText Đợt ${nextIndex + 1}/${queue.size}: ${nextProfile.displayName} xuất hiện.",
      feedback = feedback
    )
  }

  private fun afterActiveLeaderTurn(state: GameState): GameState =
    if (CAO_MINH_ID in PartyCombatHealth.actingMembers(state)) CaoMinhCombatPassive.afterCaoMinhTurn(state) else state

  private fun withCombatCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

  private fun weaponSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - armor)

  // PARTY_ACTIONS_V1: authoritative roster for one simultaneous Party command.
  private fun activePartyNames(state: GameState): String =
    PartyCombatHealth.actingMembers(state).mapNotNull { id ->
      state.characters[id]?.name
    }.joinToString(", ")

  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in PartyCombatHealth.actingMembers(state)) return null
    val character = state.characters[characterId] ?: return null
    return character.takeIf { it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0 }
  }

  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))

  private fun armorAfterIgnore(armor: Int, ignorePercent: Int): Int =
    max(0, armor - ((armor * ignorePercent + 99) / 100))

  private data class PartyPercentDamage(
    val state: GameState,
    val caoMinhHp: Int,
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
    val caoMinhMaxHp = CharacterStatEngine.effective(next, CAO_MINH_ID).maxHp
    val caoMinhHp = next.characters[CAO_MINH_ID]?.vitalState?.currentHp?.coerceIn(0, caoMinhMaxHp) ?: caoMinhMaxHp
    return PartyPercentDamage(
      state = next,
      caoMinhHp = caoMinhHp,
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
    return CharacterStatEngine.setCurrentHp(state.copy(metadata = metadata), CAO_MINH_ID, c.playerHp)
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
    val playerMax = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
    val playerHp = state.characters[CAO_MINH_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    return Snapshot(
      encounterId = m["${PREFIX}encounterId"].orEmpty(),
      entityKey = key,
      entityName = profile.displayName,
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


