package com.rabpit.backroom.core

enum class ActionKind { SEARCH, EXECUTE, EXPLORE }
enum class SearchDepth { QUICK, NORMAL, THOROUGH }
enum class ActionPhase { ACTIVE, INTERRUPTED, COMPLETED }

data class ActionSessionSnapshot(
  val sessionId: String,
  val turnId: String,
  val actorId: String,
  val kind: ActionKind,
  val phase: ActionPhase,
  val input: String,
  val locationKey: String?,
  val startedAtSubjectiveMinute: Long,
  val elapsedMinutes: Int,
  val plannedMinutes: Int?,
  val searchDepth: SearchDepth?,
  val appliedCheckpointIds: Set<String>
)

data class ActionRuntimeResult(
  val state: GameState,
  val session: ActionSessionSnapshot? = null,
  val applied: Boolean,
  val duplicate: Boolean = false,
  val error: String? = null
)

/**
 * Persistent action-session layer shared by Search, Execute and Explore.
 *
 * The runtime intentionally stores its small control state inside GameState metadata/world maps so
 * the existing save codec remains backward compatible. Time is advanced only through TimeEngine,
 * which keeps hunger/thirst/awake counters synchronized with partial action progress.
 *
 * A UI button must never mutate world state directly. It starts an Action Session, then checkpoints
 * real elapsed progress. Interrupt/complete returns control to the player without rolling back time
 * or already-recorded search coverage.
 */
object ActionRuntime {
  private const val PREFIX = "actionRuntime."
  private const val COVERAGE_PREFIX = "searchCoverage:"

  fun activeSession(state: GameState): ActionSessionSnapshot? {
    val m = state.metadata
    val id = m["${PREFIX}sessionId"] ?: return null
    val turnId = m["${PREFIX}turnId"] ?: return null
    val actorId = m["${PREFIX}actorId"] ?: return null
    val kind = enumValueOrNull<ActionKind>(m["${PREFIX}kind"]) ?: return null
    val phase = enumValueOrNull<ActionPhase>(m["${PREFIX}phase"]) ?: ActionPhase.ACTIVE
    val input = m["${PREFIX}input"].orEmpty()
    val locationKey = m["${PREFIX}locationKey"]?.takeIf { it.isNotBlank() }
    val started = m["${PREFIX}startedAt"]?.toLongOrNull() ?: state.time.elapsedSubjectiveMinutes
    val elapsed = m["${PREFIX}elapsed"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val planned = m["${PREFIX}planned"]?.toIntOrNull()?.takeIf { it > 0 }
    val depth = enumValueOrNull<SearchDepth>(m["${PREFIX}searchDepth"])
    val checkpoints = decodeSet(m["${PREFIX}checkpoints"])
    return ActionSessionSnapshot(id, turnId, actorId, kind, phase, input, locationKey, started, elapsed, planned, depth, checkpoints)
  }

  fun start(
    state: GameState,
    sessionId: String,
    turnId: String,
    actorId: String,
    kind: ActionKind,
    input: String,
    locationKey: String? = state.world["location"],
    plannedMinutes: Int? = null,
    searchDepth: SearchDepth? = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
  ): ActionRuntimeResult {
    if (sessionId.isBlank()) return ActionRuntimeResult(state, applied = false, error = "action_session_id_required")
    if (turnId.isBlank()) return ActionRuntimeResult(state, applied = false, error = "action_turn_id_required")
    if (actorId !in state.characters) return ActionRuntimeResult(state, applied = false, error = "action_actor_unknown")
    if (activeSession(state) != null) return ActionRuntimeResult(state, applied = false, error = "action_session_already_active")
    if (plannedMinutes != null && plannedMinutes <= 0) return ActionRuntimeResult(state, applied = false, error = "action_planned_minutes_invalid")
    if (kind != ActionKind.SEARCH && searchDepth != null) return ActionRuntimeResult(state, applied = false, error = "search_depth_non_search_action")

    val metadata = state.metadata + mapOf(
      "${PREFIX}sessionId" to sessionId,
      "${PREFIX}turnId" to turnId,
      "${PREFIX}actorId" to actorId,
      "${PREFIX}kind" to kind.name,
      "${PREFIX}phase" to ActionPhase.ACTIVE.name,
      "${PREFIX}input" to input,
      "${PREFIX}locationKey" to locationKey.orEmpty(),
      "${PREFIX}startedAt" to state.time.elapsedSubjectiveMinutes.toString(),
      "${PREFIX}elapsed" to "0",
      "${PREFIX}planned" to (plannedMinutes?.toString().orEmpty()),
      "${PREFIX}searchDepth" to (searchDepth?.name.orEmpty()),
      "${PREFIX}checkpoints" to ""
    )
    val next = state.copy(metadata = metadata)
    return ActionRuntimeResult(next, activeSession(next), applied = true)
  }

  /**
   * Commits a real slice of elapsed action time. checkpointId makes retries idempotent.
   * A later interrupt therefore preserves the exact elapsed portion instead of charging zero or the
   * whole originally planned action.
   */
  fun advance(state: GameState, sessionId: String, checkpointId: String, minutes: Int): ActionRuntimeResult {
    val session = activeSession(state) ?: return ActionRuntimeResult(state, applied = false, error = "action_session_missing")
    if (session.sessionId != sessionId) return ActionRuntimeResult(state, session, applied = false, error = "action_session_mismatch")
    if (session.phase != ActionPhase.ACTIVE) return ActionRuntimeResult(state, session, applied = false, error = "action_session_not_active")
    if (checkpointId.isBlank()) return ActionRuntimeResult(state, session, applied = false, error = "action_checkpoint_id_required")
    if (minutes <= 0) return ActionRuntimeResult(state, session, applied = false, error = "action_checkpoint_minutes_invalid")
    if (checkpointId in session.appliedCheckpointIds) return ActionRuntimeResult(state, session, applied = false, duplicate = true)
    val nextElapsed = session.elapsedMinutes.toLong() + minutes.toLong()
    if (nextElapsed > Int.MAX_VALUE) return ActionRuntimeResult(state, session, applied = false, error = "action_elapsed_overflow")
    if (session.plannedMinutes != null && nextElapsed > session.plannedMinutes) {
      return ActionRuntimeResult(state, session, applied = false, error = "action_checkpoint_exceeds_plan")
    }

    val timeResult = TimeEngine.execute(
      state,
      TimeAdvanceCommand(
        commandId = "ACTION:${session.sessionId}:$checkpointId:TIME",
        turnId = session.turnId,
        actorId = session.actorId,
        source = CommandSource.SYSTEM,
        minutes = minutes,
        reason = "action_${session.kind.name.lowercase()}"
      )
    )
    if (!timeResult.applied) return ActionRuntimeResult(state, session, applied = false, error = timeResult.validation.reason ?: "action_time_rejected")

    val checkpoints = session.appliedCheckpointIds + checkpointId
    val metadata = timeResult.state.metadata + mapOf(
      "${PREFIX}elapsed" to nextElapsed.toString(),
      "${PREFIX}checkpoints" to encodeSet(checkpoints)
    )
    val next = timeResult.state.copy(metadata = metadata)
    return ActionRuntimeResult(next, activeSession(next), applied = true)
  }

  fun markSearchCoverage(
    state: GameState,
    sessionId: String,
    scopes: Set<String>,
    worldRevision: String = state.world["worldRevision"] ?: "default"
  ): ActionRuntimeResult {
    val session = activeSession(state) ?: return ActionRuntimeResult(state, applied = false, error = "action_session_missing")
    if (session.sessionId != sessionId) return ActionRuntimeResult(state, session, applied = false, error = "action_session_mismatch")
    if (session.kind != ActionKind.SEARCH) return ActionRuntimeResult(state, session, applied = false, error = "search_coverage_requires_search")
    val location = session.locationKey?.takeIf { it.isNotBlank() }
      ?: return ActionRuntimeResult(state, session, applied = false, error = "search_location_missing")
    val normalized = scopes.map(String::trim).filter(String::isNotEmpty).toSet()
    if (normalized.isEmpty()) return ActionRuntimeResult(state, session, applied = false, error = "search_coverage_scope_required")
    val key = coverageKey(location, worldRevision)
    val merged = decodeSet(state.world[key]) + normalized
    val next = state.copy(world = state.world + (key to encodeSet(merged)))
    return ActionRuntimeResult(next, activeSession(next), applied = true)
  }

  fun searchCoverage(state: GameState, locationKey: String, worldRevision: String = state.world["worldRevision"] ?: "default"): Set<String> =
    decodeSet(state.world[coverageKey(locationKey, worldRevision)])

  fun interrupt(state: GameState, sessionId: String, reason: String): ActionRuntimeResult =
    finish(state, sessionId, ActionPhase.INTERRUPTED, reason.ifBlank { "interrupted" })

  fun complete(state: GameState, sessionId: String): ActionRuntimeResult =
    finish(state, sessionId, ActionPhase.COMPLETED, "completed")

  private fun finish(state: GameState, sessionId: String, phase: ActionPhase, reason: String): ActionRuntimeResult {
    val session = activeSession(state) ?: return ActionRuntimeResult(state, applied = false, error = "action_session_missing")
    if (session.sessionId != sessionId) return ActionRuntimeResult(state, session, applied = false, error = "action_session_mismatch")
    val cleared = state.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    cleared["lastAction.sessionId"] = session.sessionId
    cleared["lastAction.turnId"] = session.turnId
    cleared["lastAction.actorId"] = session.actorId
    cleared["lastAction.kind"] = session.kind.name
    cleared["lastAction.phase"] = phase.name
    cleared["lastAction.elapsedMinutes"] = session.elapsedMinutes.toString()
    cleared["lastAction.reason"] = reason
    var next = state.copy(metadata = cleared)
    if (phase == ActionPhase.COMPLETED && session.kind == ActionKind.SEARCH) {
      next = LevelLootEngine.onSearchCompleted(next, session.sessionId, session.locationKey)
    }
    return ActionRuntimeResult(next, session.copy(phase = phase), applied = true)
  }

  private fun coverageKey(location: String, revision: String): String =
    "$COVERAGE_PREFIX${location.trim()}:${revision.trim()}"

  private fun encodeSet(values: Set<String>): String = values.sorted().joinToString("|") { it.replace("|", "_") }
  private fun decodeSet(value: String?): Set<String> = value.orEmpty().split('|').map(String::trim).filter(String::isNotEmpty).toSet()

  private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
    raw?.takeIf { it.isNotBlank() }?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
}
