from pathlib import Path

# ============================================================================
# patch-exit-discovery-engine.py — EXIT AUTHORITY v1
#
# Moves exit discovery off keyword parsing and onto typed action kinds.
#
# Scope (deliberately small):
#   1. New pure engine: core/ExitDiscoveryEngine.kt (kind authority, canonical
#      thresholds, atomic traversal validation, legacy migration). v2: the engine
#      knows NO level count (no MAX_LEVEL, no 0..6 clamps, no sourceLevel+1).
#      Exit targets come from the injected WorldRouteResolver; exits are keyed by
#      sourceNodeId/targetNodeId ("level-N" in v1), numeric levels survive only as
#      nullable compatibility/display fields. The temporary LinearWorldRouteResolver
#      encodes current linear content (boundary at 6) as ITS configuration — not a
#      domain invariant — and is replaced by the real content-registry resolver
#      (cf. WorldProgressionCore design) when it lands.
#   2. New unit tests: ExitDiscoveryEngineTest.kt (regression tests incl. route-graph
#      cases: level-6 with route, level-999 via registry, no_route, non-linear hub,
#      multi-route selection, strict deserialization).
#   3. MainActivity.java glue (this patch):
#      - TraverseExitCommand ("traverse_exit", exact match) dispatches FIRST, before
#        every other interception (item blocks, combat, ActionRuntime) and before
#        the generic dice pipeline, so a traverse turn can never roll
#        entityEncounter / hazard / loot (the live chain lets SEARCH, EXECUTE and
#        EXPLORE all roll entityEncounter via entityEncounterAction).
#      - makeGameplayRolls: exitProbe/exitIntent keyword gate replaced by an
#        ExitDiscoveryEngine evaluation (kind-gated). anNhienRead is SEARCH-only
#        and rolls only when discovery is genuinely eligible (RNG-free
#        precondition check); bonuses preserved (+200 following, +2000 on read).
#      - Per-turn normalizeExitRecordTurnState: corrupt / stale-node /
#        stale-revision records are discarded (audited), never blocking discovery.
#      - canTransition: AI-proposed level changes are never accepted on this path.
#        Level transitions are exclusively owned by TraverseExitCommand.
#      - Flag gates: AI-proposed mutations to levelExit / exitProgress /
#        exitCandidate / confirmedExit (ops path) and to levelExit / exitProgress /
#        exitCandidate / confirmedExit / exitChanceThreshold (candidate-merge path)
#        are stripped unconditionally.
#      - Legacy confirmedExit migrates one-way to a DISCOVERED record, guarded by a
#        confirmedExitMigrated marker so a later consume can never resurrect it.
#        After migration no gameplay code reads the legacy key.
#      - Legacy threshold readers (exitThresholdAndroid, exitIntent keyword list)
#        are removed.
#      - Engine imports: top-level Kotlin types are imported (the data classes are
#        package-level, not nested in the ExitDiscoveryEngine object).
#
# v3 fixes (integration review):
#   - Java glue now references top-level Kotlin types correctly (was: nested).
#   - confirmedExitMigrated marker stops legacy resurrection after consume.
#   - normalizeExitRecordTurnState discards stale/corrupt records each turn.
#   - Traverse dispatch moved before the processCombat intercept.
#   - ExitRecord.fromMap is strict (fail closed on any missing/invalid field).
#   - anNhienRead: SEARCH-only, rolls only when discovery is eligible.
#   - Thresholds stay at the legacy 0.1% default (BALANCE_TODO); the 1%/1.5%
#     uplift is a separate balance PR, not part of this architecture refactor.
#   - WorldRouteResolver supports multiple outbound routes per node (routeId);
#     discovery picks one via the injected roller on success.
#
# v4 fixes (review round 2):
#   - AI-mutation gates now strip engine-owned progression/tombstone/audit keys
#     (levelTurns, confirmedExitMigrated, lastExitDiscard, commandId) in BOTH the
#     ops path and the sanitized candidate path. The model can neither forge nor
#     clear them.
#   - Turn order is now normalize -> migrate (was: migrate -> normalize). A stale
#     levelExit can no longer cause the legacy confirmedExit to miss its tombstone
#     and resurrect later.
#   - Balance fully separated: PROGRESS_BONUS_PER_TURN = 0. The architecture PR
#     keeps a flat 0.1%; any per-turn escalation is a separate balance PR.
#   - ExitRecord.fromMap now validates semantics, not just presence/type: rejects
#     blank ids/location, negative turn, out-of-range threshold/roll, and unknown
#     discoveredBy values.
#   - Corrected the "idempotent by commandId" comment: idempotency comes from
#     atomic validate-then-consume (record removed in the same commit); commandId
#     is audit-only and never checked.
#
# v6 fixes (review round 3):
#   - Ops-path gate rejects non-JSONObject exploration outright (continue); the
#     candidate path drops non-object exploration instead of merging. A scalar or
#     array can no longer replace the whole exploration root and wipe engine keys.
#   - normalizeExitRecordTurnState treats present-but-non-object levelExit as
#     corrupt_record (discard + audit) instead of silently skipping.
#   - Unbindable legacy migration still sets confirmedExitMigrated: one-way, no
#     later authority, even on bind failure.
#
# Explicitly OUT of scope (step 2): the "Di qua loi thoat" UI button, the freeform
# -> traverse intent mapping, and prompt text changes. UI contract for step 2:
# submit actionKind=EXECUTE (any kind works), action="traverse_exit" exactly.
#
# Test mapping:
#   - Spec tests 1-7, 9(partial), 10(partial), 11 -> ExitDiscoveryEngineTest.kt
#     (JUnit, injected IntRoller; production keeps SecureRandom GAME_RNG).
#   - Spec test 8 (AI mutation cannot touch authority) -> CI markers below
#     (gate code presence assertions; the JVM cannot meaningfully unit-test the
#     Android JSONObject layer).
#   - Spec test 9 (traverse skips generic dice) -> structural: the dispatch block
#     precedes beginAction/makeGameplayRolls; asserted by markers below.
#
# Chain position: LAST (after patch-combat-93-snapshot.py). All anchors are read
# from the post-chain MainActivity.java.
#
# MANUAL workflow edits required in the same PR (GitHub App cannot edit
# .github/workflows/*, and patch scripts cannot commit YAML changes):
#   1. Append patch-exit-discovery-engine.py to the scripts list (last).
#   2. Replace marker ('return exitFound && progressionReady(before);', java)
#      with ('EXIT_AUTHORITY_V1', java) and add the new markers (see WORKFLOW_EDIT.md).
# ============================================================================

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
ENGINE_KT = CORE / "ExitDiscoveryEngine.kt"
ENGINE_TEST_KT = ROOT / "app/src/test/java/com/rabpit/backroom/core/ExitDiscoveryEngineTest.kt"

ENGINE_KT_SOURCE = r'''package com.rabpit.backroom.core

/**
 * ExitDiscoveryEngine — the single authority for exit discovery and traversal validation.
 *
 * Authority model (kind-based, never text-based):
 * - Only the typed SEARCH / EXPLORE actions can discover an exit. Freeform text is never
 *   parsed for discovery; it may only express intent to traverse an already-discovered exit.
 * - Discovery thresholds are computed purely from canonical progression state
 *   (action kind, level turns, explicit follower bonuses). Legacy narrative keys
 *   (exitProgress, exitChanceThreshold, confirmedExit) confer zero authority.
 * - Traversal is an atomic system command (TraverseExitCommand), validated here and
 *   applied by the caller. There is no persisted TRAVERSING state: a traverse either
 *   fully commits or is rejected, so there is nothing to recover after a crash.
 *
 * Level-graph model:
 * - The engine does NOT know how many levels exist, and MUST NOT (no MAX_LEVEL,
 *   no 0..6 clamps, no sourceLevel+1 arithmetic). Levels 0-6 are initial content,
 *   not a domain boundary: future content may have hundreds of levels, sublevels
 *   ("37-A"), hubs, or non-linear routes.
 * - Exit targets come exclusively from the injected [WorldRouteResolver], which may
 *   expose MULTIPLE outbound routes per node (each with a stable [ExitRoute.routeId]).
 *   Exits are identified by [ExitRecord.sourceNodeId] / [targetNodeId]; numeric level
 *   numbers survive only as nullable compatibility/display fields.
 *
 * The engine is pure: randomness flows through the injected [IntRoller], routing
 * through the injected [WorldRouteResolver], and every other input is explicit.
 * Production passes a SecureRandom-backed roller and the temporary linear resolver;
 * unit tests inject scripted doubles. The engine never touches Android APIs or
 * org.json, so it stays unit-testable on the JVM.
 */
fun interface IntRoller {
  /** Returns a value in [0, bound), mirroring SecureRandom.nextInt(bound). */
  fun nextInt(bound: Int): Int
}

enum class ExitStatus { NONE, DISCOVERED }

/**
 * Route authority for exit traversal. Answers one question: from this node, which
 * outbound exit routes exist? Topology knowledge (linear chain, hub, sublevels,
 * content boundaries) lives here — never in the engine. Multiple routes per node
 * are supported; each carries a stable routeId.
 */
fun interface WorldRouteResolver {
  /** All outbound exit routes from [sourceNodeId]; empty when the node has none. */
  fun resolveExitRoutes(sourceNodeId: String): List<ExitRoute>
}

data class ExitRoute(
  /** Stable route identity, e.g. "level-2:main", "hub:sewer". */
  val routeId: String,
  val targetNodeId: String,
  /** Null when the target node has no numeric level (e.g. sublevels like "37-A"). */
  val targetLevelNumber: Int?,
)

/**
 * TEMPORARY v1 resolver: current content is a linear "level-N" chain. The content
 * boundary ([maxContentLevel]) is configuration of this throwaway resolver — it is
 * NOT a domain invariant and must move into the content registry when the real
 * [WorldRouteResolver] lands. The engine itself remains unbounded (see tests).
 */
class LinearWorldRouteResolver @JvmOverloads constructor(private val maxContentLevel: Int = 6) : WorldRouteResolver {
  override fun resolveExitRoutes(sourceNodeId: String): List<ExitRoute> {
    val n = sourceNodeId.removePrefix("level-").toIntOrNull() ?: return emptyList()
    if (n < 0 || n >= maxContentLevel) return emptyList()
    return listOf(ExitRoute(routeId = "level-$n:main", targetNodeId = "level-${n + 1}", targetLevelNumber = n + 1))
  }
}

/**
 * Canonical discovery input. Built by the caller from authoritative state — the engine
 * never reads freeform text, narrative flags, or legacy exit keys.
 */
data class ExitDiscoveryInput(
  val actionKind: String,
  val status: ExitStatus,
  val levelTurns: Int,
  val combatActive: Boolean,
  /** Canonical location identifier. Blank means unknown (fail closed: ineligible). */
  val locationKey: String,
  val worldRevision: String,
  /** Authoritative node identity, e.g. "level-2". */
  val sourceNodeId: String,
  /** Compatibility/display only. Null when the node has no numeric level. */
  val sourceLevelNumber: Int?,
  /** Explicit follower/system bonuses, derived from canonical state only. */
  val bonusThreshold: Int = 0,
)

/** RNG-free precondition check. Lets callers gate dependent rolls (e.g. follower
 *  bonuses) on real eligibility without consuming randomness. */
data class DiscoveryEligibility(
  /** True when the engine engaged (kind is SEARCH/EXPLORE). */
  val evaluated: Boolean,
  /** True when every precondition passed. */
  val eligible: Boolean,
  /** "ok" when eligible, else the machine-readable reason. */
  val reason: String,
  /** Resolved outbound routes; empty unless eligible. */
  val routes: List<ExitRoute>,
)

data class ExitDiscoveryOutcome(
  /** True when the engine engaged (kind is SEARCH/EXPLORE). */
  val evaluated: Boolean,
  /** True when every precondition passed and a roll was actually performed. */
  val eligible: Boolean,
  val success: Boolean,
  val threshold: Int,
  /** Null unless a roll was performed. */
  val roll: Int?,
  /** The selected outbound route on success, null otherwise. */
  val route: ExitRoute?,
  /** Machine-readable reason: ok | discovered | kind_not_eligible | already_discovered |
   *  no_route | progression_not_ready | combat_active | location_unknown | not_found */
  val reason: String,
)

/**
 * Authoritative exit record, schema v1. Serialized under flags.exploration.levelExit.
 * Exactly one record may exist per node; discovery never rolls while one exists.
 */
data class ExitRecord(
  val v: Int = 1,
  val status: ExitStatus = ExitStatus.DISCOVERED,
  val exitId: String,
  val sourceNodeId: String,
  val targetNodeId: String,
  /** Which of the node's outbound routes this exit is. */
  val routeId: String,
  val sourceLevelNumber: Int?,
  val targetLevelNumber: Int?,
  val locationKey: String,
  val worldRevision: String,
  val discoveredAtTurn: Int,
  /** SEARCH | EXPLORE | LEGACY */
  val discoveredBy: String,
  /** Minimal provenance: threshold used and roll value. Failed probes are diagnostics,
   *  not domain data, and are never persisted here. */
  val threshold: Int,
  val roll: Int,
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "v" to v,
    "status" to status.name,
    "exitId" to exitId,
    "sourceNodeId" to sourceNodeId,
    "targetNodeId" to targetNodeId,
    "routeId" to routeId,
    "sourceLevelNumber" to sourceLevelNumber,
    "targetLevelNumber" to targetLevelNumber,
    "locationKey" to locationKey,
    "worldRevision" to worldRevision,
    "discoveredAtTurn" to discoveredAtTurn,
    "discoveredBy" to discoveredBy,
    "threshold" to threshold,
    "roll" to roll,
  )

  companion object {
    /**
     * Strict deserialization: rejects on any missing field, wrong type, unknown
     * schema version, OR semantically impossible value (blank ids, negative turn,
     * out-of-range threshold/roll, unknown discoverer). The engine could never
     * have written such a record, so it must not be trusted. Callers must discard
     * rejected records rather than letting them block fresh discovery.
     */
    fun fromMap(map: Map<String, *>): ExitRecord? {
      return try {
        if ((map["v"] as? Number)?.toInt() != 1) return null
        val status = when ((map["status"] as? String)?.uppercase()) {
          "DISCOVERED" -> ExitStatus.DISCOVERED
          else -> return null
        }
        val exitId = map["exitId"] as? String ?: return null
        val sourceNodeId = map["sourceNodeId"] as? String ?: return null
        val targetNodeId = map["targetNodeId"] as? String ?: return null
        val routeId = map["routeId"] as? String ?: return null
        val locationKey = map["locationKey"] as? String ?: return null
        val worldRevision = map["worldRevision"] as? String ?: return null
        val discoveredAtTurn = (map["discoveredAtTurn"] as? Number)?.toInt() ?: return null
        val discoveredBy = map["discoveredBy"] as? String ?: return null
        val threshold = (map["threshold"] as? Number)?.toInt() ?: return null
        val roll = (map["roll"] as? Number)?.toInt() ?: return null
        // Semantic validation.
        if (exitId.isBlank() || sourceNodeId.isBlank() || targetNodeId.isBlank() ||
            routeId.isBlank() || locationKey.isBlank() || worldRevision.isBlank()) return null
        if (discoveredAtTurn < 0) return null
        if (threshold !in 0..ExitDiscoveryEngine.ROLL_MAX) return null
        if (roll !in 0..ExitDiscoveryEngine.ROLL_MAX) return null
        if (discoveredBy.uppercase() !in setOf("SEARCH", "EXPLORE", "LEGACY")) return null
        ExitRecord(
          v = 1,
          status = status,
          exitId = exitId,
          sourceNodeId = sourceNodeId,
          targetNodeId = targetNodeId,
          routeId = routeId,
          // Nullable display numbers: a missing key and an explicit null both mean
          // "no numeric level" (e.g. sublevels). Every other field is required.
          sourceLevelNumber = (map["sourceLevelNumber"] as? Number)?.toInt(),
          targetLevelNumber = (map["targetLevelNumber"] as? Number)?.toInt(),
          locationKey = locationKey,
          worldRevision = worldRevision,
          discoveredAtTurn = discoveredAtTurn,
          discoveredBy = discoveredBy.uppercase(),
          threshold = threshold,
          roll = roll,
        )
      } catch (ignored: Exception) {
        null
      }
    }
  }
}

data class TraverseInput(
  val record: ExitRecord?,
  /** Authoritative node identity of the actor's current position, e.g. "level-2". */
  val currentNodeId: String,
  val currentLocationKey: String,
  val worldRevision: String,
  val combatActive: Boolean,
)

sealed interface TraverseValidation {
  data class Ok(val targetNodeId: String, val targetLevelNumber: Int?) : TraverseValidation
  data class Rejected(val reason: String) : TraverseValidation
}

object ExitDiscoveryEngine {
  const val ROLL_MAX = 10000
  const val MIN_LEVEL_TURNS = 6

  /** Reserved exact-match command text for TraverseExitCommand (UI contract, step 2). */
  const val TRAVERSE_COMMAND = "traverse_exit"

  // ---------------------------------------------------------------------------
  // BALANCE_TODO: provisional values preserving the legacy default (0.1% FLAT).
  // The architecture PR keeps a fixed chance; any per-turn escalation or the
  // 1%/1.5% uplift is a separate, explicit balance PR — do not tune here.
  // ---------------------------------------------------------------------------
  const val SEARCH_BASE_THRESHOLD = 10
  const val EXPLORE_BASE_THRESHOLD = 10
  const val PROGRESS_BONUS_PER_TURN = 0
  const val PROGRESS_BONUS_CAP = 0

  fun isTraverseCommand(actionKind: String?, action: String?): Boolean =
    action?.trim() == TRAVERSE_COMMAND

  /**
   * Pure threshold function. Inputs are canonical progression state only — there is
   * deliberately no parameter for exitProgress / exitChanceThreshold / confirmedExit.
   */
  fun thresholdFor(actionKind: String, levelTurns: Int, bonusThreshold: Int): Int {
    val base = when (actionKind.trim().uppercase()) {
      "EXPLORE" -> EXPLORE_BASE_THRESHOLD
      "SEARCH" -> SEARCH_BASE_THRESHOLD
      else -> 0
    }
    if (base == 0) return 0
    val progress = PROGRESS_BONUS_PER_TURN * maxOf(0, levelTurns - MIN_LEVEL_TURNS)
    return minOf(ROLL_MAX, base + minOf(progress, PROGRESS_BONUS_CAP) + maxOf(0, bonusThreshold))
  }

  /** RNG-free precondition check. Consumes no randomness. */
  fun checkEligibility(
    input: ExitDiscoveryInput,
    resolver: WorldRouteResolver = LinearWorldRouteResolver(),
  ): DiscoveryEligibility {
    val kind = input.actionKind.trim().uppercase()
    if (kind != "SEARCH" && kind != "EXPLORE") {
      return DiscoveryEligibility(false, false, "kind_not_eligible", emptyList())
    }
    if (input.status != ExitStatus.NONE) {
      // Single authoritative exit per node: never reroll, never stack.
      return DiscoveryEligibility(true, false, "already_discovered", emptyList())
    }
    // No outbound route, no exit to discover. The resolver — not a MAX_LEVEL constant —
    // owns knowledge of the graph boundary.
    val routes = resolver.resolveExitRoutes(input.sourceNodeId)
    if (routes.isEmpty()) {
      return DiscoveryEligibility(true, false, "no_route", emptyList())
    }
    if (input.levelTurns < MIN_LEVEL_TURNS) {
      return DiscoveryEligibility(true, false, "progression_not_ready", routes)
    }
    if (input.combatActive) {
      return DiscoveryEligibility(true, false, "combat_active", routes)
    }
    if (input.locationKey.isBlank()) {
      return DiscoveryEligibility(true, false, "location_unknown", routes)
    }
    return DiscoveryEligibility(true, true, "ok", routes)
  }

  fun evaluate(
    input: ExitDiscoveryInput,
    roller: IntRoller,
    resolver: WorldRouteResolver = LinearWorldRouteResolver(),
  ): ExitDiscoveryOutcome {
    val pre = checkEligibility(input, resolver)
    if (!pre.eligible) {
      return ExitDiscoveryOutcome(pre.evaluated, false, false, 0, null, null, pre.reason)
    }
    val kind = input.actionKind.trim().uppercase()
    val threshold = thresholdFor(kind, input.levelTurns, input.bonusThreshold)
    val roll = roller.nextInt(ROLL_MAX) + 1
    val success = roll <= threshold
    // On success, the discovered exit is one of the node's outbound routes, picked
    // uniformly via the injected roller (scripted in tests).
    val route = if (success) pre.routes[roller.nextInt(pre.routes.size)] else null
    return ExitDiscoveryOutcome(true, true, success, threshold, roll, route, if (success) "discovered" else "not_found")
  }

  /** Builds the authoritative record on success. Returns null when there is no discovery. */
  fun buildRecord(input: ExitDiscoveryInput, outcome: ExitDiscoveryOutcome, turn: Int): ExitRecord? {
    val route = outcome.route ?: return null
    if (!outcome.success) return null
    val kind = input.actionKind.trim().uppercase()
    return ExitRecord(
      exitId = "exit:${input.sourceNodeId}:t$turn:${kind.lowercase()}",
      sourceNodeId = input.sourceNodeId,
      targetNodeId = route.targetNodeId,
      routeId = route.routeId,
      sourceLevelNumber = input.sourceLevelNumber,
      targetLevelNumber = route.targetLevelNumber,
      locationKey = input.locationKey.trim(),
      worldRevision = input.worldRevision,
      discoveredAtTurn = turn,
      discoveredBy = kind,
      threshold = outcome.threshold,
      roll = outcome.roll ?: 0,
    )
  }

  fun validateTraverse(input: TraverseInput): TraverseValidation {
    val record = input.record ?: return TraverseValidation.Rejected("no_exit")
    if (record.status != ExitStatus.DISCOVERED) return TraverseValidation.Rejected("exit_not_discovered")
    if (input.combatActive) return TraverseValidation.Rejected("combat_active")
    if (input.currentNodeId.trim() != record.sourceNodeId) return TraverseValidation.Rejected("stale_node")
    if (input.worldRevision != record.worldRevision) return TraverseValidation.Rejected("stale_revision")
    // Anti-teleport: the exit was discovered at a concrete location; traversing from
    // anywhere else is rejected. Return to the recorded location to use it.
    if (input.currentLocationKey.trim() != record.locationKey) {
      return TraverseValidation.Rejected("wrong_location")
    }
    return TraverseValidation.Ok(record.targetNodeId, record.targetLevelNumber)
  }

  /**
   * One-way, idempotent migration of the legacy AI-narrative exit flag.
   * Fail closed: an unbindable legacy exit (blank location, no outbound route) is
   * dropped instead of migrated — the player re-discovers through the clean path.
   * Migration is deterministic (first route wins; no RNG consumed). After migration
   * no gameplay code may read the legacy key again.
   */
  fun migrateLegacy(
    confirmedExit: String,
    sourceNodeId: String,
    sourceLevelNumber: Int?,
    locationKey: String,
    worldRevision: String,
    turn: Int,
    resolver: WorldRouteResolver = LinearWorldRouteResolver(),
  ): ExitRecord? {
    if (confirmedExit.trim().isEmpty()) return null
    if (locationKey.isBlank()) return null
    val route = resolver.resolveExitRoutes(sourceNodeId).firstOrNull() ?: return null
    return ExitRecord(
      exitId = "exit:$sourceNodeId:t$turn:legacy",
      sourceNodeId = sourceNodeId,
      targetNodeId = route.targetNodeId,
      routeId = route.routeId,
      sourceLevelNumber = sourceLevelNumber,
      targetLevelNumber = route.targetLevelNumber,
      locationKey = locationKey.trim(),
      worldRevision = worldRevision,
      discoveredAtTurn = turn,
      discoveredBy = "LEGACY",
      threshold = ROLL_MAX,
      roll = 0,
    )
  }
}'''

ENGINE_TEST_SOURCE = r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression suite for the exit authority redesign (v3: route-based, no level-count
 * assumptions anywhere in the engine; strict record deserialization).
 *
 * Kind-based discovery (SEARCH/EXPLORE only), engine-owned thresholds, atomic traversal.
 * Routing comes from the injected [WorldRouteResolver] (multiple outbound routes per
 * node supported); the engine never knows how many levels exist. Production RNG
 * (SecureRandom) is never touched here: every test injects a scripted [IntRoller].
 * Java-layer concerns (AI mutation gates, traverse dispatch ordering, SEARCH-only
 * follower gating) are covered by CI contract markers in
 * patch-exit-discovery-engine.py.
 */
class ExitDiscoveryEngineTest {

  private val okRoller = IntRoller { 0 } // roll = 1: always succeeds when eligible
  private val failRoller = IntRoller { 9999 } // roll = 10000: always fails when eligible
  private val explodingRoller = IntRoller { throw AssertionError("RNG must not be called") }
  private val explodingResolver = WorldRouteResolver { throw AssertionError("resolver must not be called") }

  /** Temporary v1 content resolver: linear "level-N" chain, boundary at 6. */
  private val linear = LinearWorldRouteResolver()
  /** A registry-style resolver: routes exist wherever the registry says so. */
  private val registry = WorldRouteResolver { id ->
    when (id) {
      "level-6" -> listOf(ExitRoute("level-6:main", "level-7", 7))
      "level-999" -> listOf(ExitRoute("level-999:main", "level-1000", 1000))
      "hub" -> listOf(ExitRoute("hub:sewer", "level-37-A", null))
      "crossroads" -> listOf(
        ExitRoute("crossroads:north", "level-10", 10),
        ExitRoute("crossroads:down", "level-11", 11),
      )
      else -> emptyList()
    }
  }
  private val noRoute = WorldRouteResolver { emptyList() }
  /** Proves the engine itself is unbounded: routes everywhere, no level arithmetic. */
  private val openLinear = WorldRouteResolver { id -> listOf(ExitRoute("$id:main", "$id-next", null)) }

  private fun input(
    kind: String = "SEARCH",
    status: ExitStatus = ExitStatus.NONE,
    levelTurns: Int = 6,
    combatActive: Boolean = false,
    locationKey: String = "Parking A",
    worldRevision: String = "r1",
    sourceNodeId: String = "level-2",
    sourceLevelNumber: Int? = 2,
    bonusThreshold: Int = 0,
  ) = ExitDiscoveryInput(
    kind, status, levelTurns, combatActive, locationKey, worldRevision,
    sourceNodeId, sourceLevelNumber, bonusThreshold,
  )

  private fun discoveredRecord(
    sourceNodeId: String = "level-2",
    targetNodeId: String = "level-3",
    routeId: String = "level-2:main",
    targetLevelNumber: Int? = 3,
    locationKey: String = "Khu A",
  ) = ExitRecord(
    exitId = "exit:level-2:t9:search",
    sourceNodeId = sourceNodeId,
    targetNodeId = targetNodeId,
    routeId = routeId,
    sourceLevelNumber = 2,
    targetLevelNumber = targetLevelNumber,
    locationKey = locationKey,
    worldRevision = "r1",
    discoveredAtTurn = 9,
    discoveredBy = "SEARCH",
    threshold = 10,
    roll = 5,
  )

  private fun traverseInput(
    record: ExitRecord? = discoveredRecord(),
    currentNodeId: String = "level-2",
    currentLocationKey: String = "Khu A",
    worldRevision: String = "r1",
    combatActive: Boolean = false,
  ) = TraverseInput(record, currentNodeId, currentLocationKey, worldRevision, combatActive)

  // 1. SEARCH/EXPLORE without keywords are eligible after the gate; EXECUTE never discovers.
  @Test fun searchAndExploreEligibleAfterGateWithoutKeywords() {
    for (kind in listOf("SEARCH", "EXPLORE")) {
      val outcome = ExitDiscoveryEngine.evaluate(input(kind = kind), okRoller, linear)
      assertTrue("kind=$kind evaluated", outcome.evaluated)
      assertTrue("kind=$kind eligible", outcome.eligible)
      assertTrue("kind=$kind success", outcome.success)
      assertEquals("discovered", outcome.reason)
      assertEquals(ExitRoute("level-2:main", "level-3", 3), outcome.route)
      val record = ExitDiscoveryEngine.buildRecord(input(kind = kind), outcome, turn = 9)
      assertNotNull(record)
      assertEquals(ExitStatus.DISCOVERED, record!!.status)
      assertEquals("level-2", record.sourceNodeId)
      assertEquals("level-3", record.targetNodeId)
      assertEquals("level-2:main", record.routeId)
      assertEquals(2, record.sourceLevelNumber)
      assertEquals(3, record.targetLevelNumber)
      assertEquals("Parking A", record.locationKey)
      assertEquals(kind, record.discoveredBy)
    }
  }

  @Test fun executeWithExitWordsNeverDiscovers() {
    // The engine never sees text at all; kind alone decides. Neither RNG nor resolver runs.
    val outcome = ExitDiscoveryEngine.evaluate(input(kind = "EXECUTE"), explodingRoller, explodingResolver)
    assertFalse(outcome.evaluated)
    assertFalse(outcome.eligible)
    assertFalse(outcome.success)
    assertEquals("kind_not_eligible", outcome.reason)
  }

  // 2. Legacy narrative keys cannot influence the threshold: the function is pure over
  // canonical inputs, and there is simply no parameter for them.
  @Test fun legacyKeysCannotInfluenceThreshold() {
    val a = ExitDiscoveryEngine.thresholdFor("SEARCH", 6, 0)
    val b = ExitDiscoveryEngine.thresholdFor("SEARCH", 6, 0)
    assertEquals(a, b)
    assertEquals(0, ExitDiscoveryEngine.thresholdFor("EXECUTE", 6, 0))
    // Follower bonus is an explicit canonical input, not narrative text.
    assertEquals(a + 200, ExitDiscoveryEngine.thresholdFor("SEARCH", 6, 200))
  }

  @Test fun balanceConstantsAreProvisionalLegacyDefaults() {
    // BALANCE_TODO: arch refactor preserves the legacy 0.1% FLAT default; the uplift is a
    // separate balance PR. This test pins the provisional values so the uplift is explicit.
    assertEquals(10, ExitDiscoveryEngine.SEARCH_BASE_THRESHOLD)
    assertEquals(10, ExitDiscoveryEngine.EXPLORE_BASE_THRESHOLD)
    assertEquals(0, ExitDiscoveryEngine.PROGRESS_BONUS_PER_TURN)
  }

  @Test fun thresholdIsFlatNoPerTurnEscalation() {
    // Architecture PR: fixed chance. Per-turn escalation is balance tuning, separate PR.
    assertEquals(10, ExitDiscoveryEngine.thresholdFor("SEARCH", 6, 0))
    assertEquals(10, ExitDiscoveryEngine.thresholdFor("SEARCH", 100, 0))
    assertEquals(10, ExitDiscoveryEngine.thresholdFor("EXPLORE", 50, 0))
    // Explicit follower bonuses still apply (canonical input, not balance tuning).
    assertEquals(210, ExitDiscoveryEngine.thresholdFor("SEARCH", 100, 200))
  }

  // 3. One discovery per node: neither the resolver nor the RNG is consulted again.
  @Test fun discoveredExitIsNeverRerolled() {
    val outcome = ExitDiscoveryEngine.evaluate(input(status = ExitStatus.DISCOVERED), explodingRoller, explodingResolver)
    assertTrue(outcome.evaluated)
    assertFalse(outcome.eligible)
    assertNull(outcome.roll)
    assertEquals("already_discovered", outcome.reason)
  }

  // 4. Legacy confirmedExit migrates once, then loses all authority.
  @Test fun legacyConfirmedExitMigrates() {
    val record = ExitDiscoveryEngine.migrateLegacy("cửa trắng cuối hành lang", "level-2", 2, "Parking A", "r1", 9, linear)
    assertNotNull(record)
    assertEquals(ExitStatus.DISCOVERED, record!!.status)
    assertEquals("level-2", record.sourceNodeId)
    assertEquals("level-3", record.targetNodeId)
    assertEquals("level-2:main", record.routeId)
    assertEquals("LEGACY", record.discoveredBy)
    assertEquals("Parking A", record.locationKey)
    assertNull(ExitDiscoveryEngine.migrateLegacy("   ", "level-2", 2, "Parking A", "r1", 9, linear))
  }

  @Test fun legacyMigrationIsPureFunction() {
    val a = ExitDiscoveryEngine.migrateLegacy("exit", "level-2", 2, "A", "r1", 9, linear)
    val b = ExitDiscoveryEngine.migrateLegacy("exit", "level-2", 2, "A", "r1", 9, linear)
    assertEquals(a, b)
  }

  @Test fun legacyMigrationFailsClosedOnUnbindableExit() {
    assertNull(ExitDiscoveryEngine.migrateLegacy("exit", "level-2", 2, "   ", "r1", 9, linear))
    assertNull(ExitDiscoveryEngine.migrateLegacy("exit", "level-2", 2, "A", "r1", 9, noRoute))
  }

  @Test fun legacyMigrationPicksFirstRouteDeterministically() {
    // No RNG in migration: the first route wins, always.
    val record = ExitDiscoveryEngine.migrateLegacy("exit", "crossroads", null, "A", "r1", 9, registry)
    assertEquals("crossroads:north", record!!.routeId)
    assertEquals("level-10", record.targetNodeId)
  }

  // 5. Anti-teleport: an exit discovered at A cannot be traversed from B.
  @Test fun traverseFromWrongLocationRejected() {
    val result = ExitDiscoveryEngine.validateTraverse(traverseInput(currentLocationKey = "Khu B"))
    assertTrue(result is TraverseValidation.Rejected)
    assertEquals("wrong_location", (result as TraverseValidation.Rejected).reason)
  }

  @Test fun traverseAfterReturningToLocationAllowed() {
    val result = ExitDiscoveryEngine.validateTraverse(traverseInput(currentLocationKey = "Khu A"))
    assertTrue(result is TraverseValidation.Ok)
    val ok = result as TraverseValidation.Ok
    assertEquals("level-3", ok.targetNodeId)
    assertEquals(3, ok.targetLevelNumber)
  }

  // 6. Stale records fail closed (node identity, not numeric level).
  @Test fun traverseRejectsStaleRevision() {
    val result = ExitDiscoveryEngine.validateTraverse(traverseInput(worldRevision = "r2"))
    assertEquals("stale_revision", (result as TraverseValidation.Rejected).reason)
  }

  @Test fun traverseRejectsStaleNode() {
    val result = ExitDiscoveryEngine.validateTraverse(traverseInput(currentNodeId = "level-3"))
    assertEquals("stale_node", (result as TraverseValidation.Rejected).reason)
  }

  @Test fun traverseWithNoExitRejected() {
    val result = ExitDiscoveryEngine.validateTraverse(traverseInput(record = null))
    assertEquals("no_exit", (result as TraverseValidation.Rejected).reason)
  }

  @Test fun corruptRecordDeserializesToNull() {
    assertNull(ExitRecord.fromMap(mapOf("status" to "BOGUS", "exitId" to "x")))
    assertNull(ExitRecord.fromMap(mapOf("exitId" to "x")))
    assertNull(ExitRecord.fromMap(mapOf("status" to "DISCOVERED"))) // missing node ids
    // Strict: every non-nullable field is required, unknown schema versions rejected.
    val missingThreshold = discoveredRecord().toMap().toMutableMap().apply { remove("threshold") }
    assertNull(ExitRecord.fromMap(missingThreshold))
    val missingRevision = discoveredRecord().toMap().toMutableMap().apply { remove("worldRevision") }
    assertNull(ExitRecord.fromMap(missingRevision))
    val futureVersion = discoveredRecord().toMap().toMutableMap().apply { put("v", 2) }
    assertNull(ExitRecord.fromMap(futureVersion))
    // Semantic validation: values the engine could never have written are rejected.
    val blankNode = discoveredRecord().toMap().toMutableMap().apply { put("sourceNodeId", "  ") }
    assertNull(ExitRecord.fromMap(blankNode))
    val blankRoute = discoveredRecord().toMap().toMutableMap().apply { put("routeId", "") }
    assertNull(ExitRecord.fromMap(blankRoute))
    val blankLocation = discoveredRecord().toMap().toMutableMap().apply { put("locationKey", "") }
    assertNull(ExitRecord.fromMap(blankLocation))
    val negativeTurn = discoveredRecord().toMap().toMutableMap().apply { put("discoveredAtTurn", -1) }
    assertNull(ExitRecord.fromMap(negativeTurn))
    val badThreshold = discoveredRecord().toMap().toMutableMap().apply { put("threshold", 10001) }
    assertNull(ExitRecord.fromMap(badThreshold))
    val badRoll = discoveredRecord().toMap().toMutableMap().apply { put("roll", -1) }
    assertNull(ExitRecord.fromMap(badRoll))
    val unknownDiscoverer = discoveredRecord().toMap().toMutableMap().apply { put("discoveredBy", "GEMINI") }
    assertNull(ExitRecord.fromMap(unknownDiscoverer))
    val roundTripped = ExitRecord.fromMap(discoveredRecord().toMap())
    assertEquals(discoveredRecord(), roundTripped)
    // Nullable display numbers survive the round trip, including nulls.
    val sub = discoveredRecord(targetNodeId = "level-37-A", targetLevelNumber = null)
    assertEquals(sub, ExitRecord.fromMap(sub.toMap()))
  }

  // 7. Combat blocks both discovery and traversal.
  @Test fun combatBlocksDiscoveryAndTraversal() {
    val discovery = ExitDiscoveryEngine.evaluate(input(combatActive = true), explodingRoller, linear)
    assertEquals("combat_active", discovery.reason)
    val traverse = ExitDiscoveryEngine.validateTraverse(traverseInput(combatActive = true))
    assertEquals("combat_active", (traverse as TraverseValidation.Rejected).reason)
  }

  // 10. A valid traverse resolves the precomputed target exactly once...
  @Test fun validTraverseResolvesTargetLevel() {
    val first = ExitDiscoveryEngine.validateTraverse(traverseInput())
    assertTrue(first is TraverseValidation.Ok)
    // ...and after the caller consumes the record, a repeated command is rejected:
    // validate-then-consume is atomic, so double-submit can never double-transition.
    val second = ExitDiscoveryEngine.validateTraverse(traverseInput(record = null))
    assertTrue(second is TraverseValidation.Rejected)
  }

  // 11. Scripted rollers are deterministic.
  @Test fun deterministicWithFakeRoller() {
    val first = ExitDiscoveryEngine.evaluate(input(), IntRoller { 41 }, linear)
    val second = ExitDiscoveryEngine.evaluate(input(), IntRoller { 41 }, linear)
    assertEquals(first, second)
    assertEquals(42, first.roll)
  }

  // RNG-free eligibility: lets callers gate dependent rolls without consuming randomness.
  @Test fun checkEligibilityReasons() {
    assertEquals("kind_not_eligible", ExitDiscoveryEngine.checkEligibility(input(kind = "EXECUTE"), linear).reason)
    assertEquals("already_discovered", ExitDiscoveryEngine.checkEligibility(input(status = ExitStatus.DISCOVERED), linear).reason)
    assertEquals("no_route", ExitDiscoveryEngine.checkEligibility(input(), noRoute).reason)
    assertEquals("progression_not_ready", ExitDiscoveryEngine.checkEligibility(input(levelTurns = 5), linear).reason)
    assertEquals("combat_active", ExitDiscoveryEngine.checkEligibility(input(combatActive = true), linear).reason)
    assertEquals("location_unknown", ExitDiscoveryEngine.checkEligibility(input(locationKey = " "), linear).reason)
    val ok = ExitDiscoveryEngine.checkEligibility(input(), linear)
    assertTrue(ok.eligible)
    assertEquals("ok", ok.reason)
    assertEquals(listOf(ExitRoute("level-2:main", "level-3", 3)), ok.routes)
  }

  @Test fun checkEligibilityIsDeterministic() {
    // checkEligibility takes no roller at all: the follower-bonus gating path can never
    // consume randomness. Pure function of (input, resolver).
    val a = ExitDiscoveryEngine.checkEligibility(input(), linear)
    val b = ExitDiscoveryEngine.checkEligibility(input(), linear)
    assertEquals(a, b)
    assertTrue(a.routes.isNotEmpty())
  }

  // Route-graph tests: the engine owns no level-count assumptions.
  @Test fun levelSixDiscoversWhenOutboundRouteExists() {
    val outcome = ExitDiscoveryEngine.evaluate(
      input(sourceNodeId = "level-6", sourceLevelNumber = 6), okRoller, registry,
    )
    assertTrue(outcome.eligible)
    assertTrue(outcome.success)
    val record = ExitDiscoveryEngine.buildRecord(
      input(sourceNodeId = "level-6", sourceLevelNumber = 6), outcome, turn = 20,
    )
    assertEquals("level-7", record!!.targetNodeId)
    assertEquals(7, record.targetLevelNumber)
    assertEquals("level-6:main", record.routeId)
  }

  @Test fun highLevelNodeWorksWithRegistryRoute() {
    val outcome = ExitDiscoveryEngine.evaluate(
      input(sourceNodeId = "level-999", sourceLevelNumber = 999), okRoller, registry,
    )
    assertTrue(outcome.success)
    val record = ExitDiscoveryEngine.buildRecord(
      input(sourceNodeId = "level-999", sourceLevelNumber = 999), outcome, turn = 1,
    )
    assertEquals("level-1000", record!!.targetNodeId)
  }

  @Test fun noRouteMeansNoDiscovery() {
    val outcome = ExitDiscoveryEngine.evaluate(input(), explodingRoller, noRoute)
    assertTrue(outcome.evaluated)
    assertFalse(outcome.eligible)
    assertEquals("no_route", outcome.reason)
    assertNull(ExitDiscoveryEngine.buildRecord(input(), outcome, turn = 1))
  }

  @Test fun nonLinearRouteResolves() {
    val outcome = ExitDiscoveryEngine.evaluate(
      input(sourceNodeId = "hub", sourceLevelNumber = null), okRoller, registry,
    )
    assertTrue(outcome.success)
    val record = ExitDiscoveryEngine.buildRecord(
      input(sourceNodeId = "hub", sourceLevelNumber = null), outcome, turn = 3,
    )
    assertEquals("level-37-A", record!!.targetNodeId)
    assertEquals("hub:sewer", record.routeId)
    assertNull(record.targetLevelNumber) // sublevels need not have a number
    val traverse = ExitDiscoveryEngine.validateTraverse(
      traverseInput(
        record = record,
        currentNodeId = "hub",
        currentLocationKey = "Parking A",
      ),
    )
    assertTrue(traverse is TraverseValidation.Ok)
    assertEquals("level-37-A", (traverse as TraverseValidation.Ok).targetNodeId)
    assertNull(traverse.targetLevelNumber)
  }

  @Test fun multipleRoutesSelectViaRoller() {
    // Discovery roll first (scripted 0 -> roll 1, success), then route index.
    val pickSecond = ExitDiscoveryEngine.evaluate(
      input(sourceNodeId = "crossroads", sourceLevelNumber = null),
      IntRoller { if (it == 10000) 0 else 1 },
      registry,
    )
    assertTrue(pickSecond.success)
    assertEquals("crossroads:down", pickSecond.route!!.routeId)
    val record = ExitDiscoveryEngine.buildRecord(
      input(sourceNodeId = "crossroads", sourceLevelNumber = null), pickSecond, turn = 4,
    )
    assertEquals("crossroads:down", record!!.routeId)
    assertEquals("level-11", record.targetNodeId)

    val pickFirst = ExitDiscoveryEngine.evaluate(
      input(sourceNodeId = "crossroads", sourceLevelNumber = null),
      IntRoller { 0 },
      registry,
    )
    assertEquals("crossroads:north", pickFirst.route!!.routeId)
  }

  @Test fun engineItselfHasNoMaxLevel() {
    // The content boundary lives in the temporary resolver, not the engine: with an
    // unbounded resolver the engine discovers happily at level-6 and beyond.
    val outcome = ExitDiscoveryEngine.evaluate(
      input(sourceNodeId = "level-6", sourceLevelNumber = 6), okRoller, openLinear,
    )
    assertTrue(outcome.success)
    assertEquals("level-6-next", outcome.route!!.targetNodeId)
  }

  @Test fun tempLinearResolverKnowsContentBoundary() {
    // Temporary v1 content knowledge: linear chain, boundary at 6. This is the
    // resolver's configuration — not an engine invariant — and moves to the content
    // registry when the real WorldRouteResolver lands.
    assertEquals(listOf(ExitRoute("level-5:main", "level-6", 6)), LinearWorldRouteResolver().resolveExitRoutes("level-5"))
    assertTrue(LinearWorldRouteResolver().resolveExitRoutes("level-6").isEmpty())
    assertTrue(LinearWorldRouteResolver().resolveExitRoutes("hub").isEmpty())
  }

  // Gate + bounds.
  @Test fun progressionGateBlocksEarlyDiscovery() {
    val outcome = ExitDiscoveryEngine.evaluate(input(levelTurns = 5), explodingRoller, linear)
    assertEquals("progression_not_ready", outcome.reason)
    assertFalse(outcome.eligible)
  }

  @Test fun blankLocationIneligible() {
    val outcome = ExitDiscoveryEngine.evaluate(input(locationKey = "  "), explodingRoller, linear)
    assertEquals("location_unknown", outcome.reason)
  }

  @Test fun traverseCommandMatchesExactly() {
    assertTrue(ExitDiscoveryEngine.isTraverseCommand("EXECUTE", "traverse_exit"))
    assertTrue(ExitDiscoveryEngine.isTraverseCommand("EXECUTE", "  traverse_exit  "))
    assertFalse(ExitDiscoveryEngine.isTraverseCommand("EXECUTE", "đi qua lối thoát"))
    assertFalse(ExitDiscoveryEngine.isTraverseCommand("EXECUTE", "traverse_exit now"))
    assertFalse(ExitDiscoveryEngine.isTraverseCommand("EXECUTE", null))
  }
}
'''


def replace_once(source, old, new, label):
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 0. The engine and its tests are owned by this patch (single-file chain
#    convention). To change the engine, edit the embedded sources above.
# ---------------------------------------------------------------------------
ENGINE_KT.write_text(ENGINE_KT_SOURCE, encoding="utf-8")
ENGINE_TEST_KT.write_text(ENGINE_TEST_SOURCE, encoding="utf-8")
print("ExitDiscoveryEngine.kt + ExitDiscoveryEngineTest.kt written.")

main = MAIN.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1. Traverse dispatch FIRST: before every other interception (item blocks,
#    combat, ActionRuntime) and before the generic dice pipeline (no
#    entity/hazard/loot rolls on a traverse turn). A traverse turn is a system
#    command, not a gameplay action.
# ---------------------------------------------------------------------------
old_dispatch = '''          if (requireGameCore().blocksTextItemAction(action)) {\n            JSONObject blocked = new JSONObject(requireGameCore().processRule(stateJson, action));\n            emit(\"backroomTurn\", blocked.getJSONObject(\"state\").toString());\n            return;\n          }\n          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));\n          if (combatResult.optBoolean(\"handled\", false)) {\n            emit(\"backroomTurn\", combatResult.getJSONObject(\"state\").toString());\n            return;\n          }\n          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));\n          if (!actionStart.optBoolean(\"handled\", false)) {\n            throw new Exception(\"Action Runtime từ chối hành động: \" + actionStart.optString(\"error\", \"action_start_failed\"));\n          }\n          JSONObject localResult = new JSONObject(requireGameCore().processRule(stateJson, action));\n          if (localResult.optBoolean(\"handled\", false)) {\n            emit(\"backroomTurn\", localResult.getJSONObject(\"state\").toString());\n            return;\n          }\n          JSONObject before = new JSONObject(stateJson);\n          boolean meta = isMetaAction(action);\n          JSONObject rolls = makeGameplayRolls(before, actionKind, action, meta);\n'''
new_dispatch = '''          // EXIT_AUTHORITY_V1: TraverseExitCommand is a system command, not a gameplay action.\n          // It dispatches before every other interception (item blocks, combat, ActionRuntime)\n          // and before the generic dice pipeline, so a traverse turn can never roll\n          // entityEncounter/hazard/loot. Validation failure aborts the turn through the\n          // standard error path below.\n          boolean traverseTurn = isTraverseExitCommand(actionKind, action);\n          int traverseFromLevel = -1;\n          JSONObject traverseBase = null;\n          if (traverseTurn) {\n            JSONObject preTraverse = migrateLegacyExitTurnState(normalizeExitRecordTurnState(new JSONObject(stateJson)));\n            traverseFromLevel = currentLevel(preTraverse);\n            traverseBase = applyTraverseExitTurn(preTraverse);\n          }\n          if (!traverseTurn) {\n            if (requireGameCore().blocksTextItemAction(action)) {\n              JSONObject blocked = new JSONObject(requireGameCore().processRule(stateJson, action));\n              emit(\"backroomTurn\", blocked.getJSONObject(\"state\").toString());\n              return;\n            }\n            JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));\n            if (combatResult.optBoolean(\"handled\", false)) {\n              emit(\"backroomTurn\", combatResult.getJSONObject(\"state\").toString());\n              return;\n            }\n            JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));\n            if (!actionStart.optBoolean(\"handled\", false)) {\n              throw new Exception(\"Action Runtime từ chối hành động: \" + actionStart.optString(\"error\", \"action_start_failed\"));\n            }\n            JSONObject localResult = new JSONObject(requireGameCore().processRule(stateJson, action));\n            if (localResult.optBoolean(\"handled\", false)) {\n              emit(\"backroomTurn\", localResult.getJSONObject(\"state\").toString());\n              return;\n            }\n          }\n          JSONObject before = traverseTurn ? traverseBase : new JSONObject(stateJson);\n          boolean meta = isMetaAction(action);\n          if (!traverseTurn && !meta) before = migrateLegacyExitTurnState(normalizeExitRecordTurnState(before));\n          JSONObject rolls;\n          if (traverseTurn) {\n            rolls = new JSONObject().put(\"turn\", before.optInt(\"turn\", 1)).put(\"meta\", false)\n              .put(\"traverseExit\", new JSONObject().put(\"executed\", true)\n                .put(\"fromLevel\", traverseFromLevel).put(\"toLevel\", currentLevel(before)));\n          } else {\n            rolls = makeGameplayRolls(before, actionKind, action, meta);\n            if (!meta) before = applyExitDiscoveryOutcome(before, rolls);\n          }\n'''
main = replace_once(main, old_dispatch, new_dispatch, "traverse dispatch first")

# ---------------------------------------------------------------------------
# 2. makeGameplayRolls: remove the keyword gate; discovery becomes kind-based.
# ---------------------------------------------------------------------------
old_exitintent = '''    boolean exitIntent = containsAny(a, \"exit\", \"lối thoát\", \"thoát\", \"cửa trắng\", \"cánh cửa\", \"ngưỡng\", \"chuyển level\", \"sang level\", \"hành lang phía sau\", \"đường ra\");\n'''
new_exitintent = '''    // EXIT_AUTHORITY_V1: legacy keyword gate removed; discovery is kind-based (ExitDiscoveryEngine.checkEligibility).
'''
main = replace_once(main, old_exitintent, new_exitintent, "exitIntent keyword list removal")

old_exitblock = '''    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
    JSONObject anNhienRead = thresholdRoll("anNhienRead", 10000, 2000, anNhienFollowing && search && exitIntent, " Khoan, Để Tôi Đọc Cái Này");
    rolls.put("anNhienRead", anNhienRead);
    if (anNhienRead.optBoolean("success", false)) exitThreshold = Math.min(10000, exitThreshold + 2000);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search),
      anNhienRead.optBoolean("success", false) ? " discovery clue +2% An Nhiên +20% đọc dấu Exit" : (anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue"));
    rolls.put("exitProbe", exitProbe);
    // Compatibility alias for the older Android reducer. Both keys point to the exact same locked result; no reroll occurs.
    rolls.put("levelExit", new JSONObject(exitProbe.toString()).put("label", "levelExit"));
    return rolls;
'''
new_exitblock = '''    // EXIT_AUTHORITY_V1: exit discovery is owned by ExitDiscoveryEngine (kind authority).\n    // anNhienRead is SEARCH-only and rolls only when a discovery is genuinely eligible\n    // (the precondition check consumes no RNG). Bonuses preserved from the legacy\n    // exitIntent behavior: +200 while following, +2000 on a successful read.\n    DiscoveryEligibility discoveryPre = checkExitDiscoveryEligibility(state, actionKindNormalized);\n    boolean anNhienReadEligible = anNhienFollowing && \"SEARCH\".equals(actionKindNormalized)\n      && discoveryPre.getEligible();\n    JSONObject anNhienRead = thresholdRoll(\"anNhienRead\", 10000, 2000, anNhienReadEligible, \" Khoan, Để Tôi Đọc Cái Này\");\n    rolls.put(\"anNhienRead\", anNhienRead);\n    int exitBonus = (anNhienFollowing ? 200 : 0) + (anNhienRead.optBoolean(\"success\", false) ? 2000 : 0);\n    rolls.put(\"exitDiscovery\", evaluateExitDiscovery(state, actionKindNormalized, exitBonus));\n    return rolls;\n'''
main = replace_once(main, old_exitblock, new_exitblock, "engine discovery block")

# ---------------------------------------------------------------------------
# 3. Remove the legacy threshold reader; install the engine glue in its place.
# ---------------------------------------------------------------------------
old_threshold_fn = '''  private int exitThresholdAndroid(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) return 10;
    int explicit = flags.optInt("exitChanceThreshold", -1);
    if (explicit >= 0 && explicit <= 10000) return explicit;
    String progress = flags.optString("exitProgress", "");
    JSONObject exploration = flags.optJSONObject("exploration");
    if (progress.isEmpty() && exploration != null) progress = exploration.optString("exitProgress", "");
    String upper = progress.toUpperCase(java.util.Locale.ROOT);
    if (containsAny(upper, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE")) return 10000;
    if (containsAny(upper, "NEAR", "ALMOST", "VERY STRONG")) return 150;
    if (containsAny(upper, "STRONG", "CORRECT ROUTE")) return 100;
    if (containsAny(upper, "CLUE", "CANDIDATE", "OPENED", "OBSERVED", "TRACKED")) return 50;
    return 10;
  }
'''
new_glue = '''  // EXIT_AUTHORITY_V1: ExitDiscoveryEngine glue. The engine (core/ExitDiscoveryEngine.kt)
  // owns discovery + traversal validation. This layer only converts JSONObject state to the
  // engine's canonical input and applies the engine's decisions. Legacy narrative keys
  // (exitProgress/exitChanceThreshold/confirmedExit/exitCandidate) confer zero authority.
  private boolean isTraverseExitCommand(String actionKind, String action) {
    return ExitDiscoveryEngine.INSTANCE.isTraverseCommand(actionKind, action);  }

  private JSONObject readExitRecordJson(JSONObject state) {
    JSONObject exploration = state.optJSONObject("flags") != null
      ? state.optJSONObject("flags").optJSONObject("exploration") : null;
    if (exploration == null) return null;
    return exploration.optJSONObject("levelExit");
  }

  /** Strict: a record that fails deserialization is treated as absent (fail closed). */
  private ExitRecord readExitRecord(JSONObject state) {
    JSONObject recordJson = readExitRecordJson(state);
    if (recordJson == null) return null;
    java.util.Map<String, Object> map = new java.util.HashMap<>();
    java.util.Iterator<String> keys = recordJson.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      map.put(k, recordJson.opt(k));
    }
    return ExitRecord.Companion.fromMap(map);
  }

  private JSONObject exitRecordToJson(ExitRecord record) throws Exception {
    JSONObject json = new JSONObject();
    for (java.util.Map.Entry<String, Object> e : record.toMap().entrySet()) {
      json.put(e.getKey(), e.getValue());
    }
    return json;
  }

  /** Builds the canonical engine input from authoritative state. No text parsing. */
  private ExitDiscoveryInput buildDiscoveryInput(JSONObject state, String actionKindNormalized, int bonusThreshold) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    int levelNumber = currentLevel(state);
    // Node identity v1: "level-N". The numeric level survives only as a compatibility /
    // display field; the engine reasons about node ids and knows no level count.
    String sourceNodeId = "level-" + levelNumber;
    ExitRecord existing = readExitRecord(state);
    return new ExitDiscoveryInput(
      actionKindNormalized == null ? "" : actionKindNormalized,
      existing != null ? existing.getStatus() : ExitStatus.NONE,
      exploration != null ? Math.max(0, exploration.optInt("levelTurns", 0)) : 0,
      com.rabpit.backroom.core.CombatChoiceEngine.isActive(state),
      state.optString("location", "").trim(),
      state.optString("worldRevision", "default"),
      sourceNodeId,
      levelNumber,
      bonusThreshold);
  }

  /** RNG-free precondition check, so dependent rolls can gate on real eligibility. */
  private DiscoveryEligibility checkExitDiscoveryEligibility(JSONObject state, String actionKindNormalized) {
    return ExitDiscoveryEngine.INSTANCE.checkEligibility(
      buildDiscoveryInput(state, actionKindNormalized, 0),
      new LinearWorldRouteResolver());
  }

  private JSONObject evaluateExitDiscovery(JSONObject state, String actionKindNormalized, int bonusThreshold) throws Exception {
    ExitDiscoveryInput input = buildDiscoveryInput(state, actionKindNormalized, bonusThreshold);
    IntRoller roller = bound -> GAME_RNG.nextInt(bound);
    // Temporary v1 content resolver (linear chain). The real WorldRouteResolver replaces it.
    WorldRouteResolver resolver = new LinearWorldRouteResolver();
    int turn = state.optInt("turn", 1);
    ExitDiscoveryOutcome outcome = ExitDiscoveryEngine.INSTANCE.evaluate(input, roller, resolver);
    JSONObject json = new JSONObject()
      .put("evaluated", outcome.getEvaluated())
      .put("eligible", outcome.getEligible())
      .put("success", outcome.getSuccess())
      .put("threshold", outcome.getThreshold())
      .put("roll", outcome.getRoll() == null ? JSONObject.NULL : outcome.getRoll())
      .put("reason", outcome.getReason())
      .put("dice", "d" + ExitDiscoveryEngine.ROLL_MAX);
    if (outcome.getSuccess()) {
      ExitRecord record = ExitDiscoveryEngine.INSTANCE.buildRecord(input, outcome, turn);
      if (record != null) json.put("record", exitRecordToJson(record));
    }
    return json;
  }

  private JSONObject applyExitDiscoveryOutcome(JSONObject state, JSONObject rolls) throws Exception {
    JSONObject discovery = rolls.optJSONObject("exitDiscovery");
    if (discovery == null || !discovery.optBoolean("success", false)) return state;
    JSONObject recordJson = discovery.optJSONObject("record");
    if (recordJson == null) return state;
    JSONObject next = new JSONObject(state.toString());
    JSONObject flags = next.optJSONObject("flags");
    if (flags == null) { flags = new JSONObject(); next.put("flags", flags); }
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) { exploration = new JSONObject(); flags.put("exploration", exploration); }
    if (exploration.has("levelExit")) return state; // first discovery wins; never overwrite
    exploration.put("levelExit", recordJson);
    return next;
  }

  /**
   * One-way migration of the legacy AI-narrative exit flag. Runs at most once: after a
   * successful migration the confirmedExitMigrated marker is set, so a later consume
   * (or any other levelExit removal) can never resurrect the legacy exit. The legacy
   * string itself is preserved for projection/history; it confers no authority.
   */
  private JSONObject migrateLegacyExitTurnState(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    if (exploration == null) return state;
    if (exploration.has("levelExit") || exploration.optBoolean("confirmedExitMigrated", false)) return state;
    String confirmed = exploration.optString("confirmedExit", "").trim();
    if (confirmed.isEmpty()) return state;
    int levelNumber = currentLevel(state);
    ExitRecord record = ExitDiscoveryEngine.INSTANCE.migrateLegacy(
      confirmed,
      "level-" + levelNumber,
      levelNumber,
      state.optString("location", "").trim(),
      state.optString("worldRevision", "default"),
      state.optInt("turn", 1),
      new LinearWorldRouteResolver());
    if (record == null) {
      // One-way even on bind failure: a nonempty legacy value considered once can never
      // confer authority later. Tombstone it so it is not re-read next turn.
      JSONObject dropped = new JSONObject(state.toString());
      dropped.optJSONObject("flags").optJSONObject("exploration").put("confirmedExitMigrated", true);
      return dropped;
    }
    JSONObject next = new JSONObject(state.toString());
    JSONObject nextExploration = next.optJSONObject("flags").optJSONObject("exploration");
    nextExploration.put("levelExit", exitRecordToJson(record));
    nextExploration.put("confirmedExitMigrated", true);
    return next;
  }

  /**
   * Per-turn normalization: a levelExit record that is corrupt (fails strict
   * deserialization), bound to another node, or from another world revision is
   * discarded so it can neither block fresh discovery nor be traversed. Discards are
   * audited in exploration.lastExitDiscard.
   */
  private JSONObject normalizeExitRecordTurnState(JSONObject state) throws Exception {
    JSONObject exploration = state.optJSONObject("flags") != null
      ? state.optJSONObject("flags").optJSONObject("exploration") : null;
    if (exploration == null || !exploration.has("levelExit")) return state;
    // A present-but-non-object levelExit is corrupt: optJSONObject would return null
    // and silently skip, while has() would block discovery forever. Discard + audit.
    JSONObject recordJson = exploration.optJSONObject("levelExit");
    String reason;
    if (recordJson == null) {
      reason = "corrupt_record";
    } else {
      ExitRecord record = readExitRecord(state);
      String currentNodeId = "level-" + currentLevel(state);
      String revision = state.optString("worldRevision", "default");
      if (record == null) reason = "corrupt_record";
      else if (!record.getSourceNodeId().equals(currentNodeId)) reason = "stale_node";
      else if (!record.getWorldRevision().equals(revision)) reason = "stale_revision";
      else return state;
    }
    JSONObject next = new JSONObject(state.toString());
    JSONObject nextExploration = next.optJSONObject("flags").optJSONObject("exploration");
    nextExploration.remove("levelExit");
    nextExploration.put("lastExitDiscard", reason + "@turn-" + state.optInt("turn", 1));
    return next;
  }

  private JSONObject applyTraverseExitTurn(JSONObject before) throws Exception {
    TraverseInput input = new TraverseInput(
      readExitRecord(before),
      "level-" + currentLevel(before),
      before.optString("location", "").trim(),
      before.optString("worldRevision", "default"),
      com.rabpit.backroom.core.CombatChoiceEngine.isActive(before));
    TraverseValidation validation = ExitDiscoveryEngine.INSTANCE.validateTraverse(input);
    if (validation instanceof TraverseValidation.Rejected) {
      throw new Exception("Không thể đi qua lối thoát (" +
        ((TraverseValidation.Rejected) validation).getReason() + ").");
    }
    TraverseValidation.Ok ok = (TraverseValidation.Ok) validation;
    // Compat layer: the JSON state model is still numeric. A null targetLevelNumber means
    // the route points at a non-numeric node the v1 state model cannot represent yet.
    Integer targetLevelNumber = ok.getTargetLevelNumber();
    if (targetLevelNumber == null) {
      throw new Exception("Không thể đi qua lối thoát (route không tương thích).");
    }
    int targetLevel = targetLevelNumber;
    JSONObject state = new JSONObject(before.toString());
    state.put("location", "");
    JSONObject level = state.optJSONObject("level");
    if (level == null) { level = new JSONObject(); state.put("level", level); }
    level.put("number", targetLevel);
    level.put("name", "Level " + targetLevel + " - " + levelName(targetLevel));
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) { flags = new JSONObject(); state.put("flags", flags); }
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) { exploration = new JSONObject(); flags.put("exploration", exploration); }
    // Atomic consume: the exit is gone and progression resets in the same commit, so a
    // crash or double-submit can never double-transition: validate-then-consume is atomic,
    // so a repeated command finds no record (no_exit). commandId is audit-only, not checked.
    exploration.remove("levelExit");
    exploration.put("levelTurns", 0);
    exploration.put("transitionReady", false);
    exploration.put("exitReady", false);
    exploration.put("commandId", "traverse-exit-t" + before.optInt("turn", 1));
    JSONObject lastRolls = flags.optJSONObject("lastRolls");
    if (lastRolls == null) { lastRolls = new JSONObject(); flags.put("lastRolls", lastRolls); }
    lastRolls.put("traverseExit", new JSONObject()
      .put("fromLevel", currentLevel(before))
      .put("toLevel", targetLevel)
      .put("targetNodeId", ok.getTargetNodeId())
      .put("turn", before.optInt("turn", 1)));
    return state;
  }

'''
main = replace_once(main, old_threshold_fn, new_glue, "engine glue installation")

# ---------------------------------------------------------------------------
# 4. canTransition: AI-proposed level changes are never accepted on this path.
# ---------------------------------------------------------------------------
old_transition = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    boolean exitFound = (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
    return exitFound && progressionReady(before);
  }
'''
new_transition = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    // EXIT_AUTHORITY_V1: level transitions are exclusively owned by TraverseExitCommand
    // (ExitDiscoveryEngine). AI-proposed level changes are never accepted on this path.
    // Legacy authorities (confirmedExit text, exitProbe/levelExit rolls, progressionReady
    // flags) no longer confer transition authority.
    return false;
  }
'''
main = replace_once(main, old_transition, new_transition, "transition authority gate")

# ---------------------------------------------------------------------------
# 5. Imports for the engine glue (top-level Kotlin types; the engine object itself
#    is referenced as ExitDiscoveryEngine.INSTANCE).
# ---------------------------------------------------------------------------
old_imports = '''import com.rabpit.backroom.core.GameCoreFacade;
'''
new_imports = '''import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.DiscoveryEligibility;
import com.rabpit.backroom.core.ExitDiscoveryEngine;
import com.rabpit.backroom.core.ExitDiscoveryInput;
import com.rabpit.backroom.core.ExitDiscoveryOutcome;
import com.rabpit.backroom.core.ExitRecord;
import com.rabpit.backroom.core.ExitStatus;
import com.rabpit.backroom.core.IntRoller;
import com.rabpit.backroom.core.LinearWorldRouteResolver;
import com.rabpit.backroom.core.TraverseInput;
import com.rabpit.backroom.core.TraverseValidation;
import com.rabpit.backroom.core.WorldRouteResolver;
'''
main = replace_once(main, old_imports, new_imports, "engine imports")

# ---------------------------------------------------------------------------
# 6. Flag authority gate (ops path): strip engine-owned exit keys unconditionally.
# ---------------------------------------------------------------------------
old_gate = '''        if (root.equals("exploration") && value instanceof JSONObject) {
          JSONObject patchValue = new JSONObject(value.toString());
          JSONObject beforeExploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
          String beforeProgress = beforeExploration != null ? beforeExploration.optString("exitProgress", "") : "";
          String afterProgress = patchValue.optString("exitProgress", beforeProgress);
          boolean exitMutation = !afterProgress.equals(beforeProgress) || patchValue.has("exitCandidate");
          if (exitMutation && !rollSuccess(rolls, "levelExit")) continue;
          if (containsAny(afterProgress, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE") &&
              !containsAny(beforeProgress, "NEAR", "ALMOST", "VERY STRONG")) continue;
          value = patchValue;
        }
'''
new_gate = '''        if (root.equals("exploration")) {
          if (!(value instanceof JSONObject)) {
            // EXIT_AUTHORITY_V1: a non-object exploration would replace the whole root,
            // wiping the engine-owned record, progression, tombstone, and audit keys.
            // Reject the patch entry outright.
            continue;
          }
          JSONObject patchValue = new JSONObject(value.toString());
          // EXIT_AUTHORITY_V1: the authoritative exit record is engine-owned. The model may
          // never create, mutate, or escalate it. Legacy exit keys are read-only, and the
          // engine-owned progression counter (levelTurns), migration tombstone
          // (confirmedExitMigrated), discard audit (lastExitDiscard), and traverse audit
          // (commandId) are never AI-writable: strip them from every AI-proposed patch.
          patchValue.remove("levelExit");
          patchValue.remove("exitProgress");
          patchValue.remove("exitCandidate");
          patchValue.remove("confirmedExit");
          patchValue.remove("levelTurns");
          patchValue.remove("confirmedExitMigrated");
          patchValue.remove("lastExitDiscard");
          patchValue.remove("commandId");
          value = patchValue;
        }
'''
main = replace_once(main, old_gate, new_gate, "exploration flag gate")

# ---------------------------------------------------------------------------
# 7. sanitizedFlags (candidate-merge path): never merge engine-owned exit keys.
# ---------------------------------------------------------------------------
old_sanitized = '''    JSONObject patch = new JSONObject(proposed.toString());
    patch.remove("lastRolls");
    if (!transitionAccepted) patch.remove("currentLevel");
'''
new_sanitized = '''    JSONObject patch = new JSONObject(proposed.toString());
    patch.remove("lastRolls");
    patch.remove("exitChanceThreshold");
    if (!transitionAccepted) patch.remove("currentLevel");
    // EXIT_AUTHORITY_V1: a non-object exploration would replace the whole root.
    // Drop it rather than merging.
    if (patch.has("exploration") && patch.optJSONObject("exploration") == null) {
      patch.remove("exploration");
    }
    JSONObject patchExploration = patch.optJSONObject("exploration");
    if (patchExploration != null) {
      // EXIT_AUTHORITY_V1: engine-owned exit keys are never merged from AI candidates.
      // This includes the progression counter, migration tombstone, and audit keys —
      // the model may not forge, clear, or escalate any of them.
      patchExploration.remove("levelExit");
      patchExploration.remove("exitProgress");
      patchExploration.remove("exitCandidate");
      patchExploration.remove("confirmedExit");
      patchExploration.remove("levelTurns");
      patchExploration.remove("confirmedExitMigrated");
      patchExploration.remove("lastExitDiscard");
      patchExploration.remove("commandId");
    }
'''
main = replace_once(main, old_sanitized, new_sanitized, "sanitizedFlags exit strip")

# ---------------------------------------------------------------------------
# 8. Post-commit: a traverse turn already committed its transition; the AI phase
#    only narrates the arrival. AI-proposed level/title changes are discarded and
#    progression resets via traverseFromLevel.
# ---------------------------------------------------------------------------
old_postcommit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          int mentioned = mentionedLevel(state);
          if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
            newLevel = mentioned;
            state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
          }
          boolean levelChanged = oldLevel != newLevel;
          boolean transitionAccepted = !levelChanged || canTransition(before, rolls);
'''
new_postcommit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          int mentioned = mentionedLevel(state);
          if (traverseTurn) {
            // EXIT_AUTHORITY_V1: the traverse transition is already committed and validated.
            // The AI phase only narrates the arrival; any AI-proposed level/title change in
            // a traverse turn is discarded here.
            JSONObject traversedLevel = before.optJSONObject("level");
            if (traversedLevel != null) state.put("level", new JSONObject(traversedLevel.toString()));
            state.put("title", before.optString("title", state.optString("title", "")));
            newLevel = currentLevel(state);
          } else if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
            newLevel = mentioned;
            state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
          }
          boolean levelChanged = oldLevel != newLevel;
          boolean transitionAccepted = traverseTurn || !levelChanged || canTransition(before, rolls);
'''
main = replace_once(main, old_postcommit, new_postcommit, "traverse post-commit handling")

old_progress = '''            recordLevelProgress(state, oldLevel, newLevel);
'''
new_progress = '''            recordLevelProgress(state, traverseTurn && traverseFromLevel >= 0 ? traverseFromLevel : oldLevel, newLevel);
'''
main = replace_once(main, old_progress, new_progress, "traverse progression reset")

# ---------------------------------------------------------------------------
# 9. Contract markers: the new authority must exist; the old one must be gone.
# ---------------------------------------------------------------------------
for marker in (
    "EXIT_AUTHORITY_V1",
    "private boolean isTraverseExitCommand",
    "boolean traverseTurn = isTraverseExitCommand(actionKind, action);",
    "before every other interception",
    "traverseTurn || !levelChanged || canTransition(before, rolls)",
    "patchValue.remove(\"levelExit\")",
    "patch.remove(\"exitChanceThreshold\")",
    "patchValue.remove(\"levelTurns\")",
    "patchValue.remove(\"confirmedExitMigrated\")",
    "patchExploration.remove(\"levelTurns\")",
    "patchExploration.remove(\"confirmedExitMigrated\")",
    "!(value instanceof JSONObject)",
    "patch.remove(\"exploration\");",
    "migrateLegacyExitTurnState(normalizeExitRecordTurnState(before))",
    "rolls.put(\"exitDiscovery\", evaluateExitDiscovery(state, actionKindNormalized, exitBonus));",
    "private DiscoveryEligibility checkExitDiscoveryEligibility",
    "anNhienReadEligible",
    "SEARCH-only",
    "private JSONObject applyTraverseExitTurn",
    "private JSONObject normalizeExitRecordTurnState",
    "confirmedExitMigrated",
):
    if marker not in main:
        raise RuntimeError(f"Exit discovery contract missing: {marker}")
if "object ExitDiscoveryEngine" not in ENGINE_KT_SOURCE:
    raise RuntimeError("Exit discovery contract missing: engine source")
if "class ExitDiscoveryEngineTest" not in ENGINE_TEST_SOURCE:
    raise RuntimeError("Exit discovery contract missing: engine tests")

for forbidden in (
    'exitIntent && (physical || search)',
    'private int exitThresholdAndroid',
    'boolean exitIntent = containsAny(a, "exit"',
    'return exitFound && progressionReady(before);',
    'if (exitMutation && !rollSuccess(rolls, "levelExit")) continue;',
    'rolls.put("levelExit", new JSONObject(exitProbe.toString())',
):
    if forbidden in main:
        raise RuntimeError(f"Legacy exit authority survived: {forbidden}")

MAIN.write_text(main, encoding="utf-8")
print("Exit discovery v6 installed: kind authority, route-graph traversal, atomic traverse.")