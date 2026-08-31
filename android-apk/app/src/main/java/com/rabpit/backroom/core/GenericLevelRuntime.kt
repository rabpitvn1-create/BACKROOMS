package com.rabpit.backroom.core

data class LevelActionOutcome(
  val state: GameState,
  val reply: String,
  val progressed: Boolean,
  val escaped: Boolean = false,
  val evidenceIds: Set<String> = emptySet()
)

/**
 * Generic deterministic runtime for any registered Level instance.
 * Canon lives in LevelDefinition; generated topology, evidence, actions and prose live in the
 * locked LevelInstance so every New Game can carry a different validated puzzle.
 */
object GenericLevelRuntime {
  fun install(state: GameState, registry: LevelRegistry, levelId: String, seed: String): GameState {
    if (state.levelInstance?.levelId == levelId) return state
    val definition = registry.require(levelId)
    val level = GenericLevelGenerator.generate(definition, seed)
    require(BlueprintValidator.validate(level, definition).valid) { "invalid_generated_level:$levelId" }
    return sync(state, definition, level)
  }

  fun apply(state: GameState, registry: LevelRegistry, kind: ActionKind, input: String): LevelActionOutcome {
    val stored = state.levelInstance
      ?: return LevelActionOutcome(state, "Level instance chưa được khởi tạo.", progressed = false)
    val definition = registry.get(stored.levelId)
      ?: return LevelActionOutcome(state, "Không tìm thấy Level definition cho ${stored.levelId}.", progressed = false)
    val hydrated = hydrateLegacyInstance(stored, definition)
    val level = reconcileDiscoveredFacts(hydrated, definition)
    val workingState = if (level == stored) state else state.copy(levelInstance = level)
    if (level.completed) return LevelActionOutcome(workingState, "Lối chuyển Level đã được mở.", progressed = false, escaped = true)

    return when (kind) {
      ActionKind.SEARCH -> search(workingState, definition, level)
      ActionKind.EXPLORE -> explore(workingState, definition, level)
      ActionKind.EXECUTE -> execute(workingState, definition, level, input)
    }
  }

  private fun search(state: GameState, definition: LevelDefinition, level: LevelInstanceState): LevelActionOutcome {
    val searchKey = "searched:${level.currentZoneId}:${level.revision}"
    if (level.environment[searchKey] == "true") {
      return LevelActionOutcome(
        state,
        reply(level, definition, "search:exhausted") ?: "Kai kiểm tra lại khu vực nhưng điều kiện chưa thay đổi; không có dấu vết mới.",
        progressed = false
      )
    }

    val eligible = level.evidence.values
      .filter { !it.discovered && it.zoneId == level.currentZoneId && EvidenceSource.SEARCH in it.sources }
      .firstOrNull { conditionsMet(level, it.discoverConditions) }

    val searched = level.copy(environment = level.environment + (searchKey to "true"))
    if (eligible == null) {
      return LevelActionOutcome(
        sync(state, definition, searched),
        reply(level, definition, "search:empty") ?: "Không có thêm chi tiết đáng kể trong trạng thái hiện tại.",
        progressed = false
      )
    }

    val discovered = discover(searched, eligible.id, definition)
    return LevelActionOutcome(
      sync(state, definition, discovered),
      evidenceReply(discovered, definition, eligible.id),
      progressed = true,
      evidenceIds = setOf(eligible.id)
    )
  }

  private fun explore(state: GameState, definition: LevelDefinition, level: LevelInstanceState): LevelActionOutcome {
    val step = level.environment["exploreStep"]?.toIntOrNull() ?: 0
    val nextZone = level.exploreRoute.getOrNull(step) ?: chooseConnectedZone(level)
      ?: return LevelActionOutcome(
        state,
        reply(level, definition, "explore:exhausted") ?: "Các tuyến có thể tiếp cận từ đây đã được khảo sát; tiếp tục lặp lại không tạo tiến triển mới.",
        progressed = false
      )

    if (nextZone !in level.zones) {
      return LevelActionOutcome(state, "Level instance tham chiếu một vùng không tồn tại.", progressed = false)
    }

    val visitsKey = "visits:$nextZone"
    val visits = (level.environment[visitsKey]?.toIntOrNull() ?: 0) + 1
    val nextStep = if (level.exploreRoute.getOrNull(step) == nextZone) step + 1 else step
    var next = level.copy(
      currentZoneId = nextZone,
      environment = level.environment + mapOf(
        "exploreStep" to nextStep.toString(),
        visitsKey to visits.toString()
      )
    )
    next = commitRevision(next, "move", nextZone, "visit:$visits")

    val revealed = revealEligibleNonSearchEvidence(next, definition)
    next = revealed.first
    val evidenceIds = revealed.second
    val zoneName = next.zones.getValue(nextZone).name
    val detail = evidenceIds.joinToString(" ") { evidenceReply(next, definition, it) }
    val moved = reply(next, definition, "explore:moved")?.replace("{zone}", zoneName) ?: "Kai đi sâu hơn vào $zoneName."
    val resultReply = if (detail.isBlank()) moved else "$moved $detail"

    return LevelActionOutcome(sync(state, definition, next), resultReply, progressed = true, evidenceIds = evidenceIds)
  }

  private fun execute(state: GameState, definition: LevelDefinition, level: LevelInstanceState, input: String): LevelActionOutcome {
    val actionId = canonicalAction(level.actions, input)
      ?: return LevelActionOutcome(
        state,
        reply(level, definition, "execute:unresolved") ?: "Hành động đó không làm thay đổi quy luật đang chi phối khu vực này.",
        progressed = false
      )

    val expectedIndex = level.completedActions.size
    val expected = level.escapeBlueprint.requiredActions.getOrNull(expectedIndex)
      ?: return LevelActionOutcome(state, "Không còn bước Escape nào chưa hoàn thành trong blueprint đã khóa.", progressed = false)
    if (actionId != expected) {
      return LevelActionOutcome(
        state,
        reply(level, definition, "execute:no_progress") ?: "Kai thực hiện thử nghiệm, nhưng trạng thái thế giới không tạo ra tiến triển mới.",
        progressed = false
      )
    }

    val rule = level.actions[actionId]
      ?: return LevelActionOutcome(state, "Level instance thiếu action rule đã khóa: $actionId.", progressed = false)
    if (!conditionsMet(level, rule.conditions)) {
      return LevelActionOutcome(
        state,
        reply(level, definition, "execute:conditions_missing") ?: "Giả thuyết có thể có ý nghĩa, nhưng điều kiện hoặc vị trí hiện tại chưa đúng.",
        progressed = false
      )
    }

    var next = level.copy(completedActions = level.completedActions + actionId)
    rule.effects.forEach { effect -> next = applyEffect(next, effect) }
    next = commitRevision(next, "execute", actionId, actionId)

    val revealed = revealEligibleNonSearchEvidence(next, definition)
    next = revealed.first
    val evidenceIds = revealed.second
    val resultReply = listOfNotNull(
      rule.reply,
      evidenceIds.takeIf { it.isNotEmpty() }?.joinToString(" ") { evidenceReply(next, definition, it) }
    ).joinToString(" ").ifBlank { "Hành động làm trạng thái Level thay đổi." }

    return LevelActionOutcome(
      sync(state, definition, next),
      resultReply,
      progressed = true,
      escaped = next.completed,
      evidenceIds = evidenceIds
    )
  }

  private fun hydrateLegacyInstance(level: LevelInstanceState, definition: LevelDefinition): LevelInstanceState {
    if (level.generatorVersion != "legacy") return level
    return level.copy(
      environmentTags = if (level.environmentTags.isEmpty()) definition.canonProfile.environmentTags else level.environmentTags,
      exploreRoute = if (level.exploreRoute.isEmpty()) definition.exploreRoute else level.exploreRoute,
      actions = if (level.actions.isEmpty()) definition.actions else level.actions,
      replies = if (level.replies.isEmpty()) definition.replies else level.replies,
      generatorVersion = "legacy-definition-hydrated"
    )
  }

  private fun chooseConnectedZone(level: LevelInstanceState): String? {
    val candidates = level.zones[level.currentZoneId]?.connections.orEmpty()
    return candidates.minWithOrNull(compareBy<String> { level.environment["visits:$it"]?.toIntOrNull() ?: 0 }.thenBy { it })
  }

  private fun canonicalAction(actions: Map<String, LevelActionRule>, input: String): String? {
    val text = input.lowercase()
    val matches = actions.values.filter { rule ->
      rule.matchGroups.all { group -> group.any { token -> token.lowercase() in text } }
    }
    return matches.singleOrNull()?.id
  }

  private fun conditionsMet(level: LevelInstanceState, conditions: Set<String>): Boolean = conditions.all { condition ->
    when {
      condition.startsWith("visit:") -> {
        val parts = condition.split(':')
        val required = parts.getOrNull(2)?.toIntOrNull() ?: return@all false
        (level.environment["visits:${parts.getOrNull(1).orEmpty()}"]?.toIntOrNull() ?: 0) >= required
      }
      condition.startsWith("env:") -> {
        val body = condition.substringAfter("env:")
        val key = body.substringBefore('=')
        val value = body.substringAfter('=', missingDelimiterValue = "")
        level.environment[key] == value
      }
      condition.startsWith("zone:") -> level.currentZoneId == condition.substringAfter("zone:")
      condition.startsWith("action:") -> condition.substringAfter("action:") in level.completedActions
      condition.startsWith("fact:") -> condition.substringAfter("fact:") in level.discoveredFacts
      else -> false
    }
  }

  private fun applyEffect(level: LevelInstanceState, effect: LevelEffect): LevelInstanceState = when (effect.type) {
    LevelEffectType.SET_ENVIRONMENT -> level.copy(environment = level.environment + (effect.key.orEmpty() to effect.value.orEmpty()))
    LevelEffectType.MOVE_TO_ZONE -> level.copy(currentZoneId = effect.zoneId ?: level.currentZoneId)
    LevelEffectType.COMPLETE_LEVEL -> level.copy(completed = true)
  }

  private fun revealEligibleNonSearchEvidence(
    level: LevelInstanceState,
    definition: LevelDefinition
  ): Pair<LevelInstanceState, Set<String>> {
    var next = level
    val revealed = linkedSetOf<String>()
    level.evidence.values
      .filter { !it.discovered && it.zoneId == level.currentZoneId && EvidenceSource.SEARCH !in it.sources }
      .filter { conditionsMet(next, it.discoverConditions) }
      .forEach { evidence ->
        next = discover(next, evidence.id, definition)
        revealed += evidence.id
      }
    return next to revealed
  }

  private fun discover(level: LevelInstanceState, evidenceId: String, definition: LevelDefinition): LevelInstanceState {
    val current = level.evidence[evidenceId] ?: return level
    if (current.discovered) return level
    val updated = current.copy(discovered = true, discoveredAtRevision = level.revision)
    val withEvidence = level.copy(evidence = level.evidence + (evidenceId to updated))
    return reconcileDiscoveredFacts(withEvidence, definition)
  }

  /**
   * A required fact becomes authoritative only after the player has actually discovered the
   * evidence quorum promised by the generation contract. This keeps one lucky clue from silently
   * unlocking a puzzle that was validated as requiring multiple independent observations.
   */
  private fun reconcileDiscoveredFacts(level: LevelInstanceState, definition: LevelDefinition): LevelInstanceState {
    val requiredFacts = level.escapeBlueprint.requiredFacts
    val constraints = definition.generationConstraints
    val minEvidence = constraints.minEvidencePerRequiredFact.coerceAtLeast(1)
    val minSources = constraints.minEvidenceSourceTypesPerRequiredFact.coerceAtLeast(1)

    val earnedRequiredFacts = requiredFacts.filter { fact ->
      val supporting = level.evidence.values.filter { it.discovered && fact in it.supports }
      supporting.size >= minEvidence && supporting.flatMap { it.sources }.toSet().size >= minSources
    }.toSet()

    val discoveredNonRequiredFacts = level.evidence.values
      .filter { it.discovered }
      .flatMap { it.supports }
      .filterNot(requiredFacts::contains)
      .toSet()
    val preservedNonRequiredFacts = level.discoveredFacts - requiredFacts
    val reconciled = preservedNonRequiredFacts + discoveredNonRequiredFacts + earnedRequiredFacts
    return if (reconciled == level.discoveredFacts) level else level.copy(discoveredFacts = reconciled)
  }

  private fun commitRevision(level: LevelInstanceState, kind: String, target: String, value: String): LevelInstanceState {
    val revision = level.revision + 1
    return level.copy(
      revision = revision,
      mutations = level.mutations + WorldMutation(
        id = "${level.levelId}:$revision:${level.mutations.size + 1}",
        revision = revision,
        kind = kind,
        targetId = target,
        value = value
      )
    )
  }

  private fun sync(state: GameState, definition: LevelDefinition, level: LevelInstanceState): GameState {
    val zoneName = level.zones[level.currentZoneId]?.name ?: level.currentZoneId
    return state.copy(
      levelInstance = level,
      world = state.world + mapOf(
        "levelId" to definition.id,
        "location" to "Level ${definition.id} / $zoneName",
        "worldRevision" to "${definition.id}:${level.revision}"
      )
    )
  }

  private fun reply(level: LevelInstanceState, definition: LevelDefinition, key: String): String? =
    level.replies[key] ?: definition.replies[key]

  private fun evidenceReply(level: LevelInstanceState, definition: LevelDefinition, id: String): String =
    reply(level, definition, "evidence:$id") ?: "Kai nhận ra một chi tiết bất thường."
}
