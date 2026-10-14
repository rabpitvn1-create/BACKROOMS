package com.rabpit.backroom.core

import org.json.JSONObject

/**
 * Core-authoritative spawn mechanism restored from pre-Milestone baseline (b7929e00~1).
 *
 * Entity pool: 45% total on d10000 per eligible turn.
 * - 14 common @ 2.5% each (250bp) = 35%
 * - 3 lifeforms @ 2% each (200bp) = 6%
 * - 3 rare @ 1% each (100bp) = 3%
 * - 2 killers @ 0.5% each (50bp) = 1%
 *
 * NPC first-meeting rates (d10000, success if roll <= threshold):
 * - Lucia: 5000 (50%) — Level 0 EXPLORE, once-only
 * - Luc Tram: 25 (0.25%) — Level 4+ physical, once-only
 * - Iris/Syvial reunion: 25 (0.25%) each — physical, continuity SEPARATED/LOST/UNKNOWN
 * - An Nhien: 25 (0.25%) — physical, once-only
 * - Survivor: 200 (2%) — survivorAllowed (default true)
 *
 * Core rolls (not AI). GM only narrates. See spawn-baseline.md for full reference.
 */
object EntitySpawnPool {
  // 14 common entities @ 2.5% each
  val COMMON = listOf(
    "hound", "clump", "duller", "deathmoth", "hostile_faceling",
    "false_puddle", "paintings", "smiler", "skin-stealer", "predatory_window",
    "biological_pipeline", "wretch", "cable_mimic", "hotel_corpse_lure"
  )
  // 3 lifeforms @ 2% each
  val LIFEFORM = listOf("blackroot_sentinel", "sinew_strider", "hollow_grasper")
  // 3 rare @ 1% each
  val RARE = listOf("the_beast_of_level_5", "slenderman", "diep_minh")
  // 2 killers @ 0.5% each
  val KILLERS = listOf("jeff_the_killer", "jane_the_killer")

  const val COMMON_BP = 250
  const val LIFEFORM_BP = 200
  const val RARE_BP = 100
  const val KILLER_BP = 50
  const val TOTAL_BP = 4500

  init {
    val total = COMMON.size * COMMON_BP + LIFEFORM.size * LIFEFORM_BP +
      RARE.size * RARE_BP + KILLERS.size * KILLER_BP
    check(total == TOTAL_BP) {
      "EntitySpawnPool weights must total $TOTAL_BP bp (45%), got $total"
    }
  }

  /**
   * Selects an entity for a successful roll (1..4500).
   * Maps ticket = roll - 1 through weighted groups.
   */
  fun pickForSuccessfulRoll(roll: Int): String {
    require(roll in 1..TOTAL_BP) { "roll must be 1..$TOTAL_BP, got $roll" }
    var ticket = roll - 1
    if (ticket < 3500) return COMMON[ticket / 250]
    ticket -= 3500
    if (ticket < 600) return LIFEFORM[ticket / 200]
    ticket -= 600
    if (ticket < 300) return RARE[ticket / 100]
    ticket -= 300
    return KILLERS[ticket / 50]
  }

  /**
   * Rolls d10000 for entity encounter. Returns entity key on success (45%), null otherwise.
   * @param nextInt function returning 0..9999 (injectable for tests)
   */
  fun rollEntity(nextInt: (Int) -> Int): String? {
    val roll = nextInt(10000) + 1  // 1..10000
    return if (roll <= TOTAL_BP) pickForSuccessfulRoll(roll) else null
  }
}

/**
 * NPC first-meeting spawn table. All thresholds are d10000 (success if roll <= threshold).
 */
object NpcSpawnTable {
  const val LUCIA_THRESHOLD = 5000       // 50%
  const val LUC_TRAM_THRESHOLD = 25     // 0.25%
  const val IRIS_THRESHOLD = 25         // 0.25%
  const val SYVIAL_THRESHOLD = 25       // 0.25%
  const val AN_NHIEN_THRESHOLD = 25     // 0.25%
  const val SURVIVOR_THRESHOLD = 200    // 2%

  /** Result of NPC spawn rolls: character ID -> encounter flag key */
  data class NpcEncounter(val characterId: String, val flagKey: String)

  private fun flagSpawned(flags: JSONObject, key: String): Boolean {
    val record = flags.optJSONObject(key) ?: return false
    return record.optBoolean("spawned", false) || record.optBoolean("present", false)
  }

  private fun reunionEligible(flags: JSONObject, characters: Map<String, CharacterState>, id: String, key: String): Boolean {
    // Character must exist in world
    characters[id] ?: return false
    val record = flags.optJSONObject(key)
    // Must have a record (exists in world narrative)
    if (record == null) return false
    // Already spawned = not eligible (party check done by caller)
    if (flagSpawned(flags, key)) return false
    // Explicitly marked not eligible
    if (!record.optBoolean("reunionEligible", true)) return false
    // Continuity must allow reunion: SEPARATED, LOST, UNKNOWN, or unset
    val continuity = record.optString("continuity", "")
    return continuity in setOf("", "SEPARATED", "LOST", "UNKNOWN")
  }

  private fun anNhienEncountered(flags: JSONObject, characters: Map<String, CharacterState>): Boolean {
    // In party check is done by caller via party.memberIds; here check flags
    val record = flags.optJSONObject("anNhien") ?: return false
    return record.optBoolean("encountered", false)
  }

  private fun lucTramAlreadyMet(flags: JSONObject, partyMemberIds: List<String>): Boolean {
    if (LUC_TRAM_ID in partyMemberIds) return true
    if (flagSpawned(flags, "lucTram")) return true
    val record = flags.optJSONObject("lucTram") ?: return false
    return record.optBoolean("follower", false) || record.optBoolean("joinPending", false)
  }

  /**
   * Rolls for NPC encounters. Returns list of encounters (character ID + flag key).
   *
   * @param state current game state
   * @param kind action kind (EXPLORE/EXECUTE/SEARCH)
   * @param level current level number
   * @param nextInt function returning 0..9999 (injectable for tests)
   */
  fun rollNpcs(
    state: GameState,
    kind: ActionKind,
    level: Int,
    nextInt: (Int) -> Int
  ): List<NpcEncounter> {
    val encounters = mutableListOf<NpcEncounter>()
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()
    val partyMemberIds = state.party.memberIds
    val isExplore = kind == ActionKind.EXPLORE
    val isPhysical = kind == ActionKind.EXPLORE || kind == ActionKind.EXECUTE

    // Lucia: 50% on Level 0 EXPLORE, once-only
    if (isExplore && level == 0 && LUCIA_ID !in partyMemberIds && !flagSpawned(flags, "lucia")) {
      if (nextInt(10000) + 1 <= LUCIA_THRESHOLD) {
        encounters += NpcEncounter(LUCIA_ID, "lucia")
      }
    }

    // Luc Tram: 0.25% on Level 4+ physical, once-only
    if (isPhysical && level >= 4 && !lucTramAlreadyMet(flags, partyMemberIds)) {
      if (nextInt(10000) + 1 <= LUC_TRAM_THRESHOLD) {
        encounters += NpcEncounter(LUC_TRAM_ID, "lucTram")
      }
    }

    // Iris reunion: 0.25% physical, continuity-gated
    if (isPhysical && IRIS_ID !in partyMemberIds &&
        reunionEligible(flags, state.characters, IRIS_ID, "iris")) {
      if (nextInt(10000) + 1 <= IRIS_THRESHOLD) {
        encounters += NpcEncounter(IRIS_ID, "iris")
      }
    }

    // Syvial reunion: 0.25% physical, continuity-gated
    if (isPhysical && SYVIAL_ID !in partyMemberIds &&
        reunionEligible(flags, state.characters, SYVIAL_ID, "syvial")) {
      if (nextInt(10000) + 1 <= SYVIAL_THRESHOLD) {
        encounters += NpcEncounter(SYVIAL_ID, "syvial")
      }
    }

    // An Nhien: 0.25% physical, once-only
    if (isPhysical && AN_NHIEN_ID !in partyMemberIds && !anNhienEncountered(flags, state.characters)) {
      if (nextInt(10000) + 1 <= AN_NHIEN_THRESHOLD) {
        encounters += NpcEncounter(AN_NHIEN_ID, "anNhien")
      }
    }

    // Survivor: 2% (survivorAllowed default true)
    val survivorAllowed = flags.optBoolean("survivorEncountersAllowed", true)
    if (survivorAllowed) {
      if (nextInt(10000) + 1 <= SURVIVOR_THRESHOLD) {
        encounters += NpcEncounter("survivor", "survivor")
      }
    }

    return encounters
  }
}

/**
 * Result of a Core-authoritative spawn roll.
 */
data class SpawnResult(
  val entityKey: String?,
  val npcEncounters: List<NpcSpawnTable.NpcEncounter>
)

/**
 * Core-authoritative spawn rolls. Called by GameCoreFacade during turn processing.
 * GM only narrates results; Core decides.
 */
object SpawnMechanism {
  /**
   * Rolls for entity and NPC spawns.
   *
   * @param state current game state
   * @param kind action kind
   * @param nextInt function returning 0..9999 (use SecureRandom in production, seeded Random in tests)
   */
  fun rollSpawns(state: GameState, kind: ActionKind, nextInt: (Int) -> Int): SpawnResult {
    // Entity: all gameplay actions eligible (SEARCH, EXECUTE, EXPLORE)
    val entityKey = EntitySpawnPool.rollEntity(nextInt)

    // NPCs: gated by action type, level, and once-only flags
    val level = currentLevelNumber(state)
    val npcEncounters = NpcSpawnTable.rollNpcs(state, kind, level, nextInt)

    return SpawnResult(entityKey, npcEncounters)
  }

  /**
   * Applies spawn results to state: sets flags.entityEncounterKey and NPC encounter metadata.
   * Returns updated state. NPC encounters set character.metadata["encountered"]="true"
   * so recruitFollower() can succeed (Core-authoritative, not Milestone).
   */
  fun applySpawns(state: GameState, result: SpawnResult): GameState {
    var next = state
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()

    // Entity: set one-shot encounter key
    if (result.entityKey != null) {
      flags.put("entityEncounterKey", result.entityKey)
    }

    // NPCs: set encounter flags and character metadata
    for (encounter in result.npcEncounters) {
      val key = encounter.flagKey
      val record = flags.optJSONObject(key) ?: JSONObject()
      // Mark as encountered/spawned (once-only protection for future rolls)
      record.put("encountered", true)
      record.put("spawned", true)
      record.put("present", true)
      flags.put(key, record)

      // Set character metadata so recruitFollower() works (Core-authoritative).
      // Create the character from Canon if not yet in state (e.g. Lucia).
      val character = next.characters[encounter.characterId] ?: createNpcCharacter(encounter.characterId)
      if (character != null) {
        val updatedMetadata = character.metadata + mapOf(
          "encountered" to "true",
          "encounterId" to "spawn:${System.currentTimeMillis()}"
        )
        next = next.copy(
          characters = next.characters + (encounter.characterId to character.copy(metadata = updatedMetadata))
        )
      }
    }

    if (result.entityKey != null || result.npcEncounters.isNotEmpty()) {
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    return next
  }

  private fun currentLevelNumber(state: GameState): Int {
    val levelJson = state.world["levelJson"] ?: return 0
    return try {
      LevelRouteRegistry.requested(JSONObject(levelJson)).parent
    } catch (e: Exception) {
      0
    }
  }

  /**
   * Creates an NPC character from Canon if not yet in game state.
   * Used for first-meeting encounters (e.g. Lucia not in initial state).
   */
  private fun createNpcCharacter(characterId: String): CharacterState? {
    return when (characterId) {
      LUCIA_ID -> LuciaCanon.character()
      AN_NHIEN_ID -> AnNhienCanon.character()
      LUC_TRAM_ID -> LucTramCanon.character()
      IRIS_ID -> SpecialFollowersCanon.irisCharacter()
      SYVIAL_ID -> SpecialFollowersCanon.syvialCharacter()
      else -> null
    }
  }
}
