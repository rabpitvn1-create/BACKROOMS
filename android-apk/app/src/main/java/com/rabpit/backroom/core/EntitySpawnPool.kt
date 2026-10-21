package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Core-authoritative, level-scoped Entity encounters; NPC eligibility follows the baseline.
 *
 * Entity encounter: 35% total on d10000 per eligible turn, independent of pool size.
 * Resident pools use exact level IDs from the project Entity Codex (ENT-1/2/3/5).
 * Nodes without a resident roster use the owner-approved roaming pool.
 * Existing rarity weights only choose the species AFTER the encounter succeeds.
 *
 * NPC first-meeting rates (d10000, success if roll <= threshold):
 * - Lucia: 5000 (50%) — Level 0 EXECUTE, once-only
 * - Catherine: 500 (5%) — all physical actions across every node, once-only
 * - Janet: 500 (5%) — all physical actions across every node, once-only
 * - Nguyễn Quốc Nam: 500 (5%) — all physical actions across every node, once-only
 * - Luc Tram: 25 (0.25%) — Level 4+ physical, once-only
 * - Iris/Syvial reunion: 25 (0.25%) each — physical, continuity SEPARATED/LOST/UNKNOWN
 * - An Nhien: 25 (0.25%) — physical, once-only
 * - Temporary ambient NPC generation is archived and never triggered.
 *
 * Eligibility (from baseline MainActivity.makeGameplayRolls @ 202737c2):
 * - Meta turns (status/inventory/help): NO rolls at all
 * - Entity roll: action kind EXECUTE AND flags.entityEncountersAllowed (default true)
 * - NPC rolls: `physical` inferred from ACTION TEXT verbs (not action kind).
 *   Dialogue-only EXECUTE (e.g. "nói chuyện", "hỏi") is NOT physical → no NPC spawns.
 * - Combat/pending encounters: NO rolls
 *
 * Core rolls (not AI). GM only narrates. See spawn-baseline.md for full reference.
 */
object EntitySpawnPool {
  // Relative species weights; encounter probability is a separate 35% gate.
  val COMMON = listOf(
    "926", "927", "LuckyBear", "Brownies", "hound", "clump", "duller", "deathmoth", "hostile_faceling",
    "false_puddle", "paintings", "smiler", "skin-stealer", "predatory_window",
    "biological_pipeline", "wretch", "cable_mimic", "hotel_corpse_lure"
  )
  // Lifeform rarity group.
  val LIFEFORM = listOf("blackroot_sentinel", "sinew_strider", "hollow_grasper")
  // Rare group.
  val RARE = listOf("the_beast_of_level_5", "slenderman", "diep_minh", "luc_tram_hac_hoa")
  // Killer group.
  val KILLERS = listOf("jeff_the_killer", "jane_the_killer")

  const val COMMON_BP = 250
  const val LIFEFORM_BP = 200
  const val RARE_BP = 100
  const val KILLER_BP = 50
  const val TOTAL_BP = 3500

  // Project Entity Codex R4: ENT-1A..G, ENT-2A..F, ENT-3A..D, ENT-5A..E.
  // Exact IDs: sublevels/areas do not silently inherit their parent's residents.
  val RESIDENTS_BY_LEVEL: Map<String, List<String>> = mapOf(
    "1" to listOf("hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle", "paintings"),
    "2" to listOf("clump", "hound", "smiler", "skin-stealer", "predatory_window", "biological_pipeline"),
    "3" to listOf("deathmoth", "wretch", "skin-stealer", "cable_mimic"),
    "5" to listOf("the_beast_of_level_5", "predatory_window", "skin-stealer", "hound", "hotel_corpse_lure")
  )

  // Jeff/Jane are roaming. Owner also assigned entities without a confirmed
  // current habitat to this gameplay pool on 2026-10-16; not a new lore claim.
  val ROAMING = listOf("jeff_the_killer", "jane_the_killer", "slenderman", "diep_minh",
    "blackroot_sentinel", "sinew_strider", "hollow_grasper", "926", "927", "LuckyBear", "Brownies", "luc_tram_hac_hoa")

  fun residentsForLevel(levelId: String): List<String> = RESIDENTS_BY_LEVEL[levelId].orEmpty()

  fun poolForLevel(levelId: String): List<String> =
    residentsForLevel(levelId).ifEmpty { ROAMING }

  private fun weight(entity: String): Int = when (entity) {
    in COMMON -> COMMON_BP
    in LIFEFORM -> LIFEFORM_BP
    in RARE -> RARE_BP
    in KILLERS -> KILLER_BP
    else -> error("Unknown spawn Entity: $entity")
  }

  init {
    val roster = COMMON + LIFEFORM + RARE + KILLERS
    val residents = RESIDENTS_BY_LEVEL.values.flatten().toSet()
    check(roster.size == roster.toSet().size)
    check(residents.intersect(ROAMING.toSet()).isEmpty())
    check(residents + ROAMING.toSet() == roster.toSet())
    check(RESIDENTS_BY_LEVEL.values.all { it.isNotEmpty() && it.size == it.toSet().size })
    check(ROAMING.isNotEmpty() && ROAMING.size == ROAMING.toSet().size)
  }

  /** Map the successful part of d10000 into the local weighted species pool.
   * Reusing this ticket preserves the single encounter roll and NPC RNG order.
   * Pool size and rarity cannot increase/decrease the 3,500 successful tickets.
   */
  fun pickForSuccessfulRoll(levelId: String, roll: Int): String {
    require(roll in 1..TOTAL_BP) { "roll must be 1..$TOTAL_BP, got $roll" }
    val pool = poolForLevel(levelId)
    val totalWeight = pool.sumOf { weight(it) }
    var ticket = (roll - 1) * totalWeight / TOTAL_BP
    for (entity in pool) {
      ticket -= weight(entity)
      if (ticket < 0) return entity
    }
    error("Unreachable Entity selection")
  }

  /** One d10000 roll per eligible turn: exactly 35%, including roaming fallback. */
  fun rollEntity(levelId: String, nextInt: (Int) -> Int): String? {
    val roll = nextInt(10000) + 1
    return if (roll <= TOTAL_BP) pickForSuccessfulRoll(levelId, roll) else null
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

  // Whole Vietnamese words only: "điều tra" and "điểm" must not match "đi".
  private fun wordPattern(terms: List<String>): Regex {
    val alternatives = terms.joinToString("|") { Regex.escape(it) }
    return Regex("(?iu)(?<![\\p{L}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{N}])")
  }
  private val PHYSICAL_PATTERN = wordPattern(PHYSICAL_VERBS)
  private val SCENE_PATTERN = wordPattern(listOf(
    "khám phá", "khảo sát", "thăm dò", "dò xét", "điều tra", "thần thức",
    "quan sát", "nhìn quanh", "nhìn vào", "tìm đường", "đi", "đi qua",
    "bước", "chạy", "leo", "tiến", "lùi", "bò", "vượt", "tìm", "lục", "quét", "scan"
  ))

  // Asking or talking *about* exploration is not the same as investigating.
  private val DIALOGUE_OPENING = Regex(
    "(?iu)^\\s*(?:cao\\s+minh\\s+)?(?:nói(?:\\s+chuyện)?|hỏi|trả\\s+lời|đáp|chào|gọi|trò\\s+chuyện|thì\\s+thầm)(?![\\p{L}\\p{N}])"
  )

  // "Thả thần thức đi điều tra" sends perception outward; Cao Minh does not walk.
  // Remove that remote-sensing clause before checking tangible movement. Remaining
  // verbs still count, e.g. "... rồi bước tới" is genuinely physical.
  private val REMOTE_RECON = Regex(
    "(?iu)(?<![\\p{L}\\p{N}])(?:thả|phóng|triển\\s+khai|dùng|mở\\s+rộng)\\s+thần\\s+thức\\s+đi\\s+(?:điều\\s+tra|thăm\\s+dò|khảo\\s+sát|dò\\s+xét|quét)(?![\\p{L}\\p{N}])"
  )
  @JvmStatic fun isPhysical(actionText: String): Boolean =
    PHYSICAL_PATTERN.containsMatchIn(REMOTE_RECON.replace(actionText, " "))

  /** Visual intent within EXECUTE, never a new action kind or spawn pipeline. */
  @JvmStatic fun isSceneFacing(actionText: String): Boolean =
    !DIALOGUE_OPENING.containsMatchIn(actionText) && SCENE_PATTERN.containsMatchIn(actionText)
}

/**
 * NPC first-meeting spawn table. All thresholds are d10000 (success if roll <= threshold).
 */
object NpcSpawnTable {
  const val LUCIA_THRESHOLD = 5000       // 50%
  const val LUC_TRAM_THRESHOLD = 25     // 0.25%
  const val CATHERINE_THRESHOLD = 500    // 5%: every parent Level, sublevel and AREA
  const val JANET_THRESHOLD = 500        // 5%: disjoint from Entity and Catherine tickets
  const val NAM_THRESHOLD = 500          // 5%: [5000, 5499] separate from all above
  const val IRIS_THRESHOLD = 25         // 0.25%
  const val SYVIAL_THRESHOLD = 25       // 0.25%
  const val AN_NHIEN_THRESHOLD = 25     // 0.25% (baseline value; kept for reference only)
  // LEGACY (owner-confirmed 2026-10-14): An Nhien is NOT USED in the current game.
  // Canon files, data and table references are kept intact — but auto-spawn is OFF.
  // Do NOT re-enable without the owner's explicit request.
  const val AN_NHIEN_LEGACY_ENABLED = false
  // Archived legacy crowd-NPC probability. No gameplay path rolls it anymore.
  const val AMBIENT_THRESHOLD = 3000

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
   * - Lucia: EXECUTE kind only at Level 0 (not text-based)
   * - Legacy random ambient NPCs are disabled; only scripted NPCs and Canon first meetings remain.
   *
   * @param state current game state
   * @param kind action kind — only EXECUTE is eligible for Core spawns
   * @param actionText raw action text for physical verb detection
   * @param level current level number
   * @param nextInt function returning 0..9999 (injectable for tests)
   */
  fun rollNpcs(state: GameState, kind: ActionKind, actionText: String, level: Int,
    nextInt: (Int) -> Int): List<NpcEncounter> = rollNpcsWithPhysical(state, kind, actionText, level, nextInt, null)

  internal fun rollNpcsWithPhysical(
    state: GameState,
    kind: ActionKind,
    actionText: String,
    level: Int,
    nextInt: (Int) -> Int,
    physicalOverride: Boolean? = null,
    catherineTicket: Int? = null
  ): List<NpcEncounter> {
    val encounters = mutableListOf<NpcEncounter>()
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()
    val partyMemberIds = state.party.memberIds
    // Guard the NPC table itself, not just its usual SpawnMechanism caller.
    if (kind != ActionKind.EXECUTE) return emptyList()
    // Baseline: physical from action TEXT verbs, not action kind (MainActivity:1359 @ 202737c2)
    val isPhysical = physicalOverride ?: PhysicalActionDetector.isPhysical(actionText)

    // Lucia: 50% on Level 0 EXECUTE, once-only
    if (level == 0 && LUCIA_ID !in partyMemberIds && !flagSpawned(flags, "lucia")) {
      if (nextInt(10000) + 1 <= LUCIA_THRESHOLD) {
        encounters += NpcEncounter(LUCIA_ID, "lucia")
      }
    }

    // Reserve d10000 tickets [3500, 3999] for Catherine, disjoint from the
    // Entity window [0, 3499]. Both use the same Entity ticket when available.
    // No dropped NPC or Entity event, preserving 5% and 35% unconditionally.
    // First-meeting flags prevent duplicate appearances.
    if (isPhysical && CATHERINE_ID !in partyMemberIds &&
        !flagSpawned(flags, "catherine") &&
        state.characters[CATHERINE_ID]?.metadata?.get("encountered") != "true") {
      val ticket = catherineTicket ?: nextInt(10000)
      if (ticket in EntitySpawnPool.TOTAL_BP until (EntitySpawnPool.TOTAL_BP + CATHERINE_THRESHOLD)) {
        encounters += NpcEncounter(CATHERINE_ID, "catherine")
      }
    }

    // Janet owns [4000, 4499]: no overlap with Entity [0, 3499] or
    // Catherine [3500, 3999]. No extra random draw when Entity ticket exists.
    if (isPhysical && JANET_ID !in partyMemberIds &&
        !flagSpawned(flags, "janet") &&
        state.characters[JANET_ID]?.metadata?.get("encountered") != "true") {
      val ticket = catherineTicket ?: nextInt(10000)
      if (ticket in (EntitySpawnPool.TOTAL_BP + CATHERINE_THRESHOLD) until
          (EntitySpawnPool.TOTAL_BP + CATHERINE_THRESHOLD + JANET_THRESHOLD)) {
        encounters += NpcEncounter(JANET_ID, "janet")
      }
    }

    // Nguyễn Quốc Nam: [5000, 5499], preserving the 35/5/5/5 windows.
    // Only the first valid physical encounter is eligible; family is not spawnable.
    if (isPhysical && NGUYEN_QUOC_NAM_ID !in partyMemberIds &&
        !flagSpawned(flags, "nguyenQuocNam") &&
        state.characters[NGUYEN_QUOC_NAM_ID]?.metadata?.get("encountered") != "true") {
      val ticket = catherineTicket ?: nextInt(10000)
      if (ticket in (EntitySpawnPool.TOTAL_BP + CATHERINE_THRESHOLD + JANET_THRESHOLD) until
          (EntitySpawnPool.TOTAL_BP + CATHERINE_THRESHOLD + JANET_THRESHOLD + NAM_THRESHOLD)) {
        encounters += NpcEncounter(NGUYEN_QUOC_NAM_ID, "nguyenQuocNam")
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
 * @param ambientNpcRolled true if the 30% ambient crowd-NPC roll succeeded this
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
   * 3. Entity: action kind EXECUTE AND
   *    flags.entityEncountersAllowed (default true)
   * 4. NPCs: physical from action TEXT (see PhysicalActionDetector)
   * 5. Ephemeral ambient NPC spawns are archived (no RNG calls).
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
    if (isMeta || inCombatOrPending || kind != ActionKind.EXECUTE) {
      return SpawnResult(null, emptyList())
    }

    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull() ?: JSONObject()

    // Gate 3: entity roll requires eligible action kind AND entityEncountersAllowed flag.
    // Baseline: `entityEncounterAction && entityAllowed` (MainActivity:1391 @ 202737c2).
    val entityEncounterAction = kind == ActionKind.EXECUTE
    val entityAllowed = flags.optBoolean("entityEncountersAllowed", true)
    var entityTicket: Int? = null
    val entityKey = if (entityEncounterAction && entityAllowed) {
      EntitySpawnPool.rollEntity(currentLevelId(state)) { bound ->
        nextInt(bound).also { entityTicket = it }
      }
    } else {
      null
    }

    // Gate 4: share the Entity ticket for Catherine, preserving old Entity rolls.
    // All other NPCs retain their original independent RNG checks.
    val level = currentLevelNumber(state)
    val npcEncounters = NpcSpawnTable.rollNpcsWithPhysical(
      state, kind, actionText, level, nextInt, catherineTicket = entityTicket)

    // Archived: free-roaming ephemeral crowd NPCs never spawn.
    // Canon first meetings and combat encounters remain unchanged.
    return SpawnResult(entityKey, npcEncounters)
  }

  /** Called only AFTER Core validates a typed scene target. No text classification. */
  fun rollSceneSpawns(state: GameState, kind: String, nextInt: (Int) -> Int): SpawnResult {
    if (kind !in setOf("MOVE", "SEARCH", "STEP", "FREE", "INTERACT", "TALK")) return SpawnResult(null, emptyList())
    if (CombatRuntime.active(state) != null || CombatRuntime.pending(state) != null)
      return SpawnResult(null, emptyList())
    val flags = JSONObject(state.world["flagsJson"] ?: "{}")
    var entityTicket: Int? = null
    val entity = if (flags.optBoolean("entityEncountersAllowed", true))
      EntitySpawnPool.rollEntity(currentLevelId(state)) { bound ->
        nextInt(bound).also { entityTicket = it }
      } else null
    val npcs = NpcSpawnTable.rollNpcsWithPhysical(state, ActionKind.EXECUTE, "",
      currentLevelNumber(state), nextInt, physicalOverride = true, catherineTicket = entityTicket)
    // The Entity and Catherine windows are disjoint, so neither gets dropped.
    return SpawnResult(entity, npcs)
  }

  /**
   * Convenience overload for programmatic callers with no action text.
   * Game turns use the full overload; empty text is not physical.
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
    next = clearAmbientNpcMarker(next)
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
    next = clearAmbientNpcMarker(next)
    return next
  }

  /** Metadata key holding deferred NPC encounters as a JSON array string. */
  const val PENDING_NPC_METADATA_KEY = "spawn.pendingNpcEncounters"

  /** Archived marker, retained only to discard lingering legacy state safely. */
  const val AMBIENT_NPC_METADATA_KEY = "spawn.ambientNpcRolled"

  /** Archived legacy event: never treat even a stale marker as a new NPC encounter. */
  fun ambientNpcRolled(@Suppress("UNUSED_PARAMETER") state: GameState): Boolean = false

  /** Discard a stale legacy marker without activating the old encounter flow. */
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
    CATHERINE_ID -> CatherineCanon.NAME
    JANET_ID -> JanetCanon.NAME
    NGUYEN_QUOC_NAM_ID -> NguyenQuocNamCanon.NAME
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

  private fun currentLevelId(state: GameState): String = runCatching {
    LevelRouteRegistry.requested(JSONObject(state.world["levelJson"] ?: "{}"))?.id ?: "0"
  }.getOrDefault("0")

  private fun currentLevelNumber(state: GameState): Int =
    LevelRouteRegistry.node(currentLevelId(state))?.parent ?: 0

  /**
   * Creates an NPC character from Canon if not yet in game state.
   * Used for first-meeting encounters (e.g. Lucia not in initial state).
   */
  private fun createNpcCharacter(characterId: String): CharacterState? {
    return when (characterId) {
      LUCIA_ID -> LuciaCanon.character()
      AN_NHIEN_ID -> AnNhienCanon.character()
      LUC_TRAM_ID -> LucTramCanon.character()
      CATHERINE_ID -> CatherineCanon.character()
      JANET_ID -> JanetCanon.character()
      NGUYEN_QUOC_NAM_ID -> NguyenQuocNamCanon.character()
      IRIS_ID -> SpecialFollowersCanon.irisCharacter()
      SYVIAL_ID -> SpecialFollowersCanon.syvialCharacter()
      else -> null
    }
  }
}
