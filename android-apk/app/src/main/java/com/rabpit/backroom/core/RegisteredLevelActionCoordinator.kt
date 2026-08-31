package com.rabpit.backroom.core

data class RegisteredLevelActionResult(
  val state: GameState,
  val handled: Boolean,
  val reply: String? = null,
  val progressed: Boolean = false,
  val escaped: Boolean = false,
  val error: String? = null
)

/**
 * Connects a registered data-driven Level to the existing persistent ActionRuntime/TurnCoordinator.
 * SEARCH and EXPLORE are authoritative to the registered Level. EXECUTE is claimed only when its
 * free-form text matches an action locked into the current LevelInstance, so generated puzzles can
 * vary per New Game while unrelated inventory/party commands still use the ordinary pipeline.
 */
object RegisteredLevelActionCoordinator {
  fun applyStarted(
    state: GameState,
    registry: LevelRegistry,
    kind: ActionKind,
    input: String,
    levelId: String?,
    runSeed: String
  ): RegisteredLevelActionResult {
    val requestedId = levelId?.trim().orEmpty()
    if (requestedId.isEmpty() || !registry.contains(requestedId)) {
      return RegisteredLevelActionResult(state, handled = false)
    }

    val definition = registry.require(requestedId)
    var working = if (state.levelInstance?.levelId == requestedId) {
      state
    } else {
      GenericLevelRuntime.install(state, registry, requestedId, runSeed)
    }

    if (kind == ActionKind.EXECUTE) {
      val actions = working.levelInstance?.actions?.takeIf { it.isNotEmpty() } ?: definition.actions
      if (matchingActions(actions, input).isEmpty()) {
        return RegisteredLevelActionResult(state, handled = false)
      }
    }

    val active = ActionRuntime.activeSession(working)
      ?: return RegisteredLevelActionResult(working, handled = true, error = "action_session_missing")
    if (active.kind != kind || active.input != input) {
      return RegisteredLevelActionResult(working, handled = true, error = "action_session_mismatch")
    }

    val pending = TurnCoordinator.createPending(working, active.turnId, input)
    if (pending.error != null) {
      return RegisteredLevelActionResult(working, handled = true, error = pending.error)
    }

    val outcome = GenericLevelRuntime.apply(pending.state, registry, kind, input)
    working = outcome.state

    val minutes = active.plannedMinutes ?: TimeCostPolicy.estimateMinutes(input)
    val advanced = ActionRuntime.advance(working, active.sessionId, "registered-level", minutes)
    if (!advanced.applied && !advanced.duplicate) {
      return RegisteredLevelActionResult(working, handled = true, error = advanced.error ?: "action_time_rejected")
    }
    working = if (advanced.duplicate) working else advanced.state

    val committed = TurnCoordinator.commit(working, emptyList())
    if (committed.error != null) {
      return RegisteredLevelActionResult(working, handled = true, error = committed.error)
    }
    working = committed.state

    if (kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
      val depth = active.searchDepth ?: SearchDepth.NORMAL
      val coverage = ActionRuntime.markSearchCoverage(
        working,
        active.sessionId,
        setOf("depth:${depth.name.lowercase()}", "registered-level:$requestedId")
      )
      if (coverage.applied) working = coverage.state
    }

    val completed = ActionRuntime.complete(working, active.sessionId)
    if (!completed.applied) {
      return RegisteredLevelActionResult(working, handled = true, error = completed.error ?: "action_complete_failed")
    }

    return RegisteredLevelActionResult(
      state = completed.state,
      handled = true,
      reply = outcome.reply,
      progressed = outcome.progressed,
      escaped = outcome.escaped
    )
  }

  fun matchingActions(definition: LevelDefinition, input: String): List<String> =
    matchingActions(definition.actions, input)

  fun matchingActions(actions: Map<String, LevelActionRule>, input: String): List<String> {
    val text = input.lowercase()
    return actions.values
      .filter { rule -> rule.matchGroups.all { group -> group.any { token -> token.lowercase() in text } } }
      .map { it.id }
      .sorted()
  }
}
