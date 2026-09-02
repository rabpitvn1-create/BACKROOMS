package com.rabpit.backroom.core

data class RegisteredLevelActionResult(
  val state: GameState,
  val handled: Boolean,
  val reply: String? = null,
  val progressed: Boolean = false,
  val escaped: Boolean = false,
  val evidenceIds: Set<String> = emptySet(),
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
    catalog: LevelCatalog,
    kind: ActionKind,
    input: String,
    levelId: String?,
    runSeed: String,
    director: BackroomsDirector = BackroomsDirector.DETERMINISTIC
  ): RegisteredLevelActionResult {
    val requestedId = levelId?.trim().orEmpty()
    if (requestedId.isEmpty() || !registry.contains(requestedId)) {
      return RegisteredLevelActionResult(state, handled = false)
    }

    val definition = registry.require(requestedId)
    var working = if (state.levelInstance?.levelId == requestedId) {
      GenericLevelRuntime.install(state, registry, requestedId, runSeed)
    } else {
      val current = state.levelInstance
      val decision = ForwardProgressionPolicy.evaluate(catalog, current?.levelId, current?.completed ?: false, requestedId)
      if (!decision.allowed) {
        return RegisteredLevelActionResult(state, handled = true, error = decision.reason)
      }
      GenericLevelRuntime.install(state, registry, requestedId, runSeed)
    }

    var resolvedExecuteActionId: String? = null
    if (kind == ActionKind.EXECUTE) {
      val actions = working.levelInstance?.actions?.takeIf { it.isNotEmpty() } ?: definition.actions
      val legacyMatches = matchingActions(actions, input)
      resolvedExecuteActionId = when {
        legacyMatches.size == 1 -> legacyMatches.single()
        legacyMatches.size > 1 -> null
        else -> {
          val safeCandidates = actions.values.sortedBy { it.id }.mapIndexed { index, rule ->
            SemanticActionDescriptor("candidate-$index", rule.semanticDescriptions)
          }
          val mapping = SemanticActionMapper.resolve(input, safeCandidates)
          val index = mapping.candidateToken?.removePrefix("candidate-")?.toIntOrNull()
          index?.let { actions.values.sortedBy { rule -> rule.id }.getOrNull(it)?.id }
        }
      }
      if (resolvedExecuteActionId == null && !isNavigationAttempt(input)) {
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

    val outcome = GenericLevelRuntime.apply(
      pending.state, registry, kind, input, director,
      resolvedExecuteActionId = resolvedExecuteActionId
    )
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
      escaped = outcome.escaped,
      evidenceIds = outcome.evidenceIds
    )
  }

  fun matchingActions(definition: LevelDefinition, input: String): List<String> =
    matchingActions(definition.actions, input)

  // A movement command that does not match a legal action still belongs to the Level runtime.
  // Letting it fall through to the writer lets prose cross a boundary without a Core transition.
  private fun isNavigationAttempt(input: String): Boolean {
    val text = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim()
    return Regex("^(?:(?:kai(?: akechi)?(?:\\s+(?:va|cung)\\s+(?:lucia|iris|syvial))?|ban|toi|ca hai|ca nhom)\\s+)?(?:(?:se|thu|tiep tuc)\\s+)?(?:di|buoc|chay|tang toc|tien|re|quay lai|bam theo|theo hanh lang|vuot|roi khoi|sang level|qua level|no[ -]?clip)\\b")
      .containsMatchIn(text)
  }

  fun matchingActions(actions: Map<String, LevelActionRule>, input: String): List<String> {
    val text = input.lowercase()
    return actions.values
      .filter { rule -> rule.matchGroups.all { group -> group.any { token -> token.lowercase() in text } } }
      .map { it.id }
      .sorted()
  }
}
