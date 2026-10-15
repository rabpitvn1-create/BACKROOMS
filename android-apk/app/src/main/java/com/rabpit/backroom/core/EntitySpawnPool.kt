package com.rabpit.backroom.core

import org.json.JSONArray
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
 * - Ambient NPC: 1000 (10%) — ephemeral marker only (state.metadata),
 *   NEVER a CharacterState/Follower/Canon encounter. Entity and Canon NPCs
 *   take priority over ambient.
 *
 * Eligibility (from baseline MainActivity.makeGameplayRolls @ 202737c2):
 * - Meta turns (status/inventory/help): NO rolls at all
 * - Entity roll: action kind in (EXPLORE, SEARCH, EXECUTE) AND flags.entityEncountersAllowed (default true)
 * - NPC rolls: `physical` inferred from ACTION TEXT verbs (not action kind).
 *   Dialogue-only EXECUTE (e.g. "nói chuyện", "hỏi") is NOT physical → no NPC spawns.
 * - Combat/pending encounters: NO rolls
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
 * Physical action detection from baseline (MainActivity.java:1359 @ 202737c2).
 *
 * `physical` was inferred from the ACTION TEXT (not action kind) by matching
 * Vietnamese movement/interaction verbs. A dialogue-only EXECUTE like
 * "nói chuyện với Lucia" or "hỏi đường" contains none of these verbs → not physical.
 *
 * This is why NPC rolls (Lục Trầm, Iris/Syvial, An Nhiên) must use action text,
 * not just `kind == EXECUTE`.
 */
object PhysicalActionDetector {
  /**
   * Vietnamese verbs indicating physical movement or interaction.
   * Exact list from baseline: MainActivity.java line 1359 @ 202737c2.
   */
  val PHYSICAL_VERBS = listOf(
    "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm",
    "kiểm tra", "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo",
    "tiến", "lùi", "cúi", "nhìn vào", "bò", "nhảy", "đào", "tháo",
    "đập", "vượt", "đi qua"
  )

  /**
   * Returns true if the action text contains any physical verb (case-insensitive).
   * Matches baseline `containsAny(a, ...)` where `a = lower(action)`.
   */
  fun isPhysical(actionText: String): Boolean {
    val lower = actionText.lowercase()
    return PHYSICAL_VERBS.any { verb -> lower.contains(verb.lowercase()) }
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
  const val AN_NHIEN_THRESHOLD = 25     // 0.25% (baseline value; kept for reference only)
  // LEGACY (owner-confirmed 2026-10-14): An Nhien is NOT USED in the current game.
  // Canon files, data and table references are kept intact — but auto-spawn is OFF.
  // Do NOT re-enable without the owner's explicit request.
  const val AN_NHIEN_LEGACY_ENABLED = false
  // Ambient crowd NPC: 10% on d10000. Ephemeral only — rolled in
  // SpawnMechanism.rollSpawns (NOT via the NpcEncounter pipeline) and surfaced
  // as state.metadata["spawn.ambientNpcRolled"]. Replaces the old Survivor 2%
  // branch (removed 2026-10-15) to avoid two overlapping crowd-spawn types.
  const val AMBIENT_THRESHOLD = 1000    // 10%

  /** Result of NPC spawn rolls: character ID -> encounter flag key */
  data class NpcEncounter(val characterId: String, val flagKey: String)

  private fun flagSpawned(flags: JSONObject, key: String): Boolean {
    val record = flags.optJSONObject(key) ?: return false
    return record.optBoolean("spawned", false) || record.optBoolean("present", false)
  }

  private fun reunionEligible(flags: JSONObject, characters: Map<String, CharacterState>, id: String, key: String): Boolean {
    // Reunions are not first spawns: spawned remains true for once-only history.
    // Only a Core-confirmed separated character with no current presence may return.
    val character = characters[id] ?: return false
    if (character.metadata["encountered"] != "true" ||
        character.presence !in setOf(CharacterPresence.SEPARATED, CharacterPresence.MISSING)) return false
    val record = flags.optJSONObject(key) ?: return false
    if (!record.optBoolean("encountered", false) ||
        record.optBoolean("present", false)) return false
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
   * Baseline eligibility (@ 202737c2 MainActivity.makeGameplayRolls):
   * - `physical` is inferred from ACTION TEXT verbs, NOT action kind.
   *   A dialogue-only EXECUTE ("nói chuyện", "hỏi") is NOT physical → no NPC rolls.
   * - Lucia: EXPLORE kind only (not text-based)
   * - Ambient NPC: rolled in SpawnMechanism.rollSpawns (not here) — 10%,
   *   entity and Canon NPCs take priority, never a Canon encounter.
   *
   * @param state current game state
   * @param kind action kind (EXPLORE/EXECUTE/SEARCH) — used for Lucia EXPLORE check only
   * @param actionText raw action text for physical verb detection
   * @param level current level number
   * @param nextInt function returning 0..9999 (injectable for tests)
   */
  fun rollNpcs(
    state: GameState,
    kind: ActionKind,
    actionText: String,
    level: Int,
    nextInt: (Int) -> Int
  ): List<NpcEncounter> {
    val encounters = mutableListOf<NpcEncounter>()
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()
    val partyMemberIds = state.party.memberIds
    val isExplore = kind == ActionKind.EXPLORE
    // Baseline: physical from action TEXT verbs, not action kind (MainActivity:1359 @ 202737c2)
    val isPhysical = PhysicalActionDetector.isPhysical(actionText)

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

    // LEGACY — auto-spawn disabled (owner-confirmed 2026-10-14). Canon/data kept intact.
    // Do NOT re-enable without the owner's explicit request.
    if (AN_NHIEN_LEGACY_ENABLED && isPhysical && AN_NHIEN_ID !in partyMemberIds && !anNhienEncountered(flags, state.characters)) {
      if (nextInt(10000) + 1 <= AN_NHIEN_THRESHOLD) {
        encounters += NpcEncounter(AN_NHIEN_ID, "anNhien")
      }
    }

    return encounters
  }
}

/**
 * Result of a Core-authoritative spawn roll.
 *
 * @param ambientNpcRolled true if the 10% ambient crowd-NPC roll succeeded this
 * turn. Ephemeral: surfaced via state.metadata["spawn.ambientNpcRolled"], NEVER
 * through the NpcEncounter pipeline (no flags, no CharacterState, no Canon).
 */
data class SpawnResult(
  val entityKey: String?,
  val npcEncounters: List<NpcSpawnTable.NpcEncounter>,
  val ambientNpcRolled: Boolean = false
)

/**
 * Core-authoritative spawn rolls. Called by GameCoreFacade during turn processing.
 * GM only narrates results; Core decides.
 */
object SpawnMechanism {
  /**
   * Rolls for entity and NPC spawns.
   *
   * Eligibility gates (from baseline @ 202737c2):
   * 1. Meta turns: NO rolls (status/inventory/help don't spawn)
   * 2. Combat/pending: NO rolls (caller must check CombatRuntime.pending)
   * 3. Entity: action kind in (EXPLORE, SEARCH, EXECUTE) AND
   *    flags.entityEncountersAllowed (default true)
   * 4. NPCs: physical from action TEXT (see PhysicalActionDetector)
   * 5. Ambient NPC: 10% on eligible EXPLORE turns — only if NO entity rolled AND no
   *    Canon NPC encounters. Meta/combat already returned above. The "currently
   *    in ambient dialogue" check is RAM-side (MainActivity), not Core state.
   *
   * @param state current game state
   * @param kind action kind
   * @param actionText raw action text for physical verb detection
   * @param isMeta true for meta-only turns (status/inventory/help) — skips all rolls
   * @param inCombatOrPending true if combat or encounter confirmation pending — skips all rolls
   * @param nextInt function returning 0..9999 (use SecureRandom in production, seeded Random in tests)
   */
  fun rollSpawns(
    state: GameState,
    kind: ActionKind,
    actionText: String,
    isMeta: Boolean = false,
    inCombatOrPending: Boolean = false,
    nextInt: (Int) -> Int
  ): SpawnResult {
    // Gate 1+2: meta turns and combat/pending → no spawns at all (baseline: `if (meta) return rolls`)
    if (isMeta || inCombatOrPending) {
      return SpawnResult(null, emptyList())
    }

    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()

    // Gate 3: entity roll requires eligible action kind AND entityEncountersAllowed flag.
    // Baseline: `entityEncounterAction && entityAllowed` (MainActivity:1391 @ 202737c2).
    // Note: the `!milestoneHasPlan()` gate is obsolete — Milestone is gone.
    val entityEncounterAction = kind == ActionKind.EXPLORE ||
      kind == ActionKind.SEARCH ||
      kind == ActionKind.EXECUTE
    val entityAllowed = flags.optBoolean("entityEncountersAllowed", true)
    val entityKey = if (entityEncounterAction && entityAllowed) {
      EntitySpawnPool.rollEntity(nextInt)
    } else {
      null
    }

    // Gate 4: NPCs gated by physical (from action text), level, and once-only flags.
    val level = currentLevelNumber(state)
    val npcEncounters = NpcSpawnTable.rollNpcs(state, kind, actionText, level, nextInt)

    // Gate 5: Ambient crowd NPC — 10% on d10000, ephemeral only.
    // Priority: entity and Canon NPCs take precedence over ambient.
    val ambientNpcRolled = kind == ActionKind.EXPLORE &&
      entityKey == null && npcEncounters.isEmpty() &&
      nextInt(10000) + 1 <= NpcSpawnTable.AMBIENT_THRESHOLD

    return SpawnResult(entityKey, npcEncounters, ambientNpcRolled)
  }

  /**
   * Backward-compatible overload for callers that don't yet pass action text.
   * Defaults to empty action text (not physical) — prefer the full overload.
   * TODO(Agents 1/2): Update GameCoreFacade.beginAction to pass the action text.
   */
  fun rollSpawns(state: GameState, kind: ActionKind, nextInt: (Int) -> Int): SpawnResult {
    return rollSpawns(state, kind, "", isMeta = false, inCombatOrPending = false, nextInt)
  }

  /**
   * Applies spawn results to state: sets flags.entityEncounterKey and NPC encounter metadata.
   * Returns updated state. NPC encounters set character.metadata["encountered"]="true"
   * so recruitFollower() can succeed (Core-authoritative, not Milestone).
   *
   * NOTE: this is the immediate-apply variant (kept for direct-apply callers/tests).
   * The production turn pipeline uses the two-phase [prepareSpawns] /
   * [commitPendingNpcEncounters] API below so a failed/aborted turn does not consume
   * the once-only NPC first meeting.
   */
  fun applySpawns(state: GameState, result: SpawnResult): GameState {
    var next = applyEntitySpawn(state, result.entityKey)
    next = applyNpcEncounters(next, result.npcEncounters)
    next = applyAmbientMarker(next, result.ambientNpcRolled)
    return next
  }

  /**
   * Two-phase variant for the production turn pipeline (P0-2 NPC timing fix).
   * Entity spawns apply immediately (one-shot flags.entityEncounterKey; the caller
   * reserves the combat encounter). NPC encounters are DEFERRED: stored as pending
   * in state metadata WITHOUT marking encountered/spawned/present.
   *
   * - [commitPendingNpcEncounters]: call after processValidatedCandidate succeeds.
   * - [discardPendingNpcEncounters]: call on abort/failure; once-only NOT consumed.
   */
  fun prepareSpawns(state: GameState, result: SpawnResult): GameState {
    var next = applyEntitySpawn(state, result.entityKey)
    next = prepareNpcEncounters(next, result.npcEncounters)
    next = applyAmbientMarker(next, result.ambientNpcRolled)
    return next
  }

  /** Metadata key holding deferred NPC encounters as a JSON array string. */
  const val PENDING_NPC_METADATA_KEY = "spawn.pendingNpcEncounters"

  /**
   * Metadata key for the ephemeral ambient-NPC roll marker.
   * Value "true" (string, per GameState.metadata: Map<String, String> convention) =
   * an ambient NPC was rolled this turn. Consumed by MainActivity (Gemini
   * generation + RAM cache), cleared after use. NEVER creates a CharacterState,
   * Follower, or Canon encounter — it bypasses applyNpcEncounters entirely.
   */
  const val AMBIENT_NPC_METADATA_KEY = "spawn.ambientNpcRolled"

  /**
   * Sets the ephemeral ambient-NPC marker. No-op when the roll did not succeed.
   */
  private fun applyAmbientMarker(state: GameState, rolled: Boolean): GameState {
    val cleared = state.metadata - AMBIENT_NPC_METADATA_KEY
    return state.copy(metadata = if (rolled) cleared + (AMBIENT_NPC_METADATA_KEY to "true") else cleared)
  }

  /** Reads the ephemeral ambient-NPC marker (true if rolled this turn). */
  fun ambientNpcRolled(state: GameState): Boolean =
    state.metadata[AMBIENT_NPC_METADATA_KEY] == "true"

  /**
   * Clears the ephemeral ambient-NPC marker after MainActivity consumes it
   * (Gemini success/failure, or scene end). Safe to call when absent.
   */
  fun clearAmbientNpcMarker(state: GameState): GameState {
    if (!state.metadata.containsKey(AMBIENT_NPC_METADATA_KEY)) return state
    return state.copy(metadata = state.metadata - AMBIENT_NPC_METADATA_KEY)
  }

  /**
   * Phase 1: store NPC encounters as pending WITHOUT touching flags/characters.
   * Safe to call with an empty list (clears any stale pending).
   */
  fun prepareNpcEncounters(state: GameState, encounters: List<NpcSpawnTable.NpcEncounter>): GameState {
    if (encounters.isEmpty()) return discardPendingNpcEncounters(state)
    val arr = JSONArray()
    encounters.forEach { e ->
      arr.put(JSONObject().put("characterId", e.characterId).put("flagKey", e.flagKey))
    }
    return state.copy(metadata = state.metadata + (PENDING_NPC_METADATA_KEY to arr.toString()))
  }

  /** Reads deferred NPC encounters (empty if none). Never throws. */
  fun pendingNpcEncounters(state: GameState): List<NpcSpawnTable.NpcEncounter> {
    val raw = state.metadata[PENDING_NPC_METADATA_KEY] ?: return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        NpcSpawnTable.NpcEncounter(o.getString("characterId"), o.getString("flagKey"))
      }
    }.getOrDefault(emptyList())
  }

  /**
   * Phase 2a: commit deferred NPC encounters — marks encountered/spawned/present
   * and character metadata, then clears the pending list. Idempotent when empty.
   */
  fun commitPendingNpcEncounters(state: GameState): GameState {
    val pending = pendingNpcEncounters(state)
    if (pending.isEmpty()) return state
    val applied = applyNpcEncounters(state, pending)
    return discardPendingNpcEncounters(applied)
  }

  /**
   * Phase 2b: discard deferred NPC encounters WITHOUT marking encountered.
   * The once-only first meeting is NOT consumed — a later turn may roll it again.
   */
  fun discardPendingNpcEncounters(state: GameState): GameState {
    if (!state.metadata.containsKey(PENDING_NPC_METADATA_KEY)) return state
    return state.copy(metadata = state.metadata - PENDING_NPC_METADATA_KEY)
  }

  /**
   * Human-readable NPC name for the GM narration event. No Core secrets — only the
   * character id, flag key and display name are exposed to the prompt.
   */
  fun npcDisplayName(characterId: String): String = when (characterId) {
    LUCIA_ID -> runCatching { LuciaCanon.character().name }.getOrDefault("Lucia")
    AN_NHIEN_ID -> runCatching { AnNhienCanon.character().name }.getOrDefault("An Nhiên")
    LUC_TRAM_ID -> runCatching { LucTramCanon.character().name }.getOrDefault("Lục Trầm")
    IRIS_ID -> runCatching { SpecialFollowersCanon.irisCharacter().name }.getOrDefault("Iris")
    SYVIAL_ID -> runCatching { SpecialFollowersCanon.syvialCharacter().name }.getOrDefault("Syvial")
    "survivor" -> "Người sống sót"
    else -> characterId
  }

  private fun applyEntitySpawn(state: GameState, entityKey: String?): GameState {
    if (entityKey == null) return state
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()
    flags.put("entityEncounterKey", entityKey)
    return state.copy(world = state.world + ("flagsJson" to flags.toString()))
  }

  private fun applyNpcEncounters(state: GameState, encounters: List<NpcSpawnTable.NpcEncounter>): GameState {
    if (encounters.isEmpty()) return state
    var next = state
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()

    // NPCs: set encounter flags and character metadata
    for (encounter in encounters) {
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
          "encounterFlagKey" to encounter.flagKey,
          "encounterId" to "spawn:${System.currentTimeMillis()}"
        )
        next = next.copy(
          characters = next.characters + (encounter.characterId to character.copy(
            presence = CharacterPresence.ACTIVE,
            metadata = updatedMetadata
          ))
        )
      }
    }

    next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
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
