package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RegisteredLevelActionCoordinatorTest {
  @Test fun arbitraryRegisteredLevelAutoInstallsAndOwnsSearch() {
    val definition = fixture("742.13")
    val registry = LevelRegistry.from(listOf(definition))
    val started = start(GameState.initial(), ActionKind.SEARCH, "Tìm kiếm")

    val result = RegisteredLevelActionCoordinator.applyStarted(
      started, registry, catalog("742.13"), ActionKind.SEARCH, "Tìm kiếm", "742.13", "run-742"
    )

    assertTrue(result.error.orEmpty(), result.handled)
    assertNull(result.error)
    assertEquals("742.13", result.state.levelInstance?.levelId)
    assertEquals("742.13:run-742", result.state.levelInstance?.generationId)
    assertEquals(setOf("search-clue"), result.evidenceIds)
    assertNull(ActionRuntime.activeSession(result.state))
    assertTrue("TURN_1" in result.state.turn.completedTurnIds)
  }

  @Test fun unrelatedExecuteIsLeftForExistingCommandPipeline() {
    val definition = fixture("347.2")
    val registry = LevelRegistry.from(listOf(definition))
    val started = start(GameState.initial(), ActionKind.EXECUTE, "Dùng băng gạc cho Lucia")

    val result = RegisteredLevelActionCoordinator.applyStarted(
      started, registry, catalog("347.2"), ActionKind.EXECUTE, "Dùng băng gạc cho Lucia", "347.2", "run-347"
    )

    assertFalse(result.handled)
    assertNull(result.state.levelInstance)
    assertNotNull(ActionRuntime.activeSession(result.state))
  }

  @Test fun recognizedExecuteIsResolvedWithoutGeminiReroll() {
    val definition = fixture("999.alpha")
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "999.alpha", "run-999")
    state = start(state, ActionKind.EXECUTE, "Mở cánh cửa thoát")

    val result = RegisteredLevelActionCoordinator.applyStarted(
      state, registry, catalog("999.alpha"), ActionKind.EXECUTE, "Mở cánh cửa thoát", "999.alpha", "ignored"
    )

    assertTrue(result.handled)
    assertTrue(result.progressed)
    assertTrue(result.escaped)
    assertTrue(result.state.levelInstance?.completed == true)
    assertNull(ActionRuntime.activeSession(result.state))
  }

  @Test fun semanticParaphraseResolvesWithoutLegacySubstringMatch() {
    val definition = fixture("semantic")
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "semantic", "run-semantic")
    state = start(state, ActionKind.EXECUTE, "Đi qua lối cửa thoát")

    val result = RegisteredLevelActionCoordinator.applyStarted(
      state, registry, catalog("semantic"), ActionKind.EXECUTE,
      "Đi qua lối cửa thoát", "semantic", "run-semantic"
    )

    assertTrue(result.handled)
    assertTrue(result.progressed)
    assertTrue(result.escaped)
  }

  @Test fun incompleteLevelAndModelSelectedTargetOutsideGraphAreRejected() {
    val definitions = listOf(fixture("from"), fixture("allowed"), fixture("hidden"))
    val registry = LevelRegistry.from(definitions)
    val catalog = LevelCatalog.from(listOf(
      graphEntry("from", 1000, listOf("allowed")),
      graphEntry("allowed", 2000),
      graphEntry("hidden", 3000)
    ))
    val installed = GenericLevelRuntime.install(GameState.initial(), registry, "from", "run")

    val incomplete = RegisteredLevelActionCoordinator.applyStarted(
      start(installed, ActionKind.SEARCH, "Tìm kiếm"), registry, catalog,
      ActionKind.SEARCH, "Tìm kiếm", "allowed", "run"
    )
    assertEquals("progression_current_level_incomplete:from", incomplete.error)

    val completed = installed.copy(levelInstance = installed.levelInstance?.copy(completed = true))
    val outsideGraph = RegisteredLevelActionCoordinator.applyStarted(
      start(completed, ActionKind.SEARCH, "Tìm kiếm"), registry, catalog,
      ActionKind.SEARCH, "Tìm kiếm", "hidden", "run"
    )
    assertEquals("progression_transition_not_declared:from:hidden", outsideGraph.error)
    assertEquals("from", outsideGraph.state.levelInstance?.levelId)
  }

  private fun start(state: GameState, kind: ActionKind, input: String): GameState {
    val started = ActionRuntime.start(
      state = state,
      sessionId = "TURN_1:${kind.name}:test",
      turnId = "TURN_1",
      actorId = KAI_ID,
      kind = kind,
      input = input,
      locationKey = state.world["location"] ?: "fixture",
      plannedMinutes = 1,
      searchDepth = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
    )
    assertTrue(started.error.orEmpty(), started.applied)
    return started.state
  }

  private fun fixture(id: String): LevelDefinition {
    val fact = "EXIT_FACT"
    val zones = linkedMapOf(
      "entry" to ZoneState("entry", "Entry", setOf("exit"), setOf("entry")),
      "exit" to ZoneState("exit", "Exit", emptySet(), setOf("escape"))
    )
    val evidence = listOf(
      EvidenceState("search-clue", setOf(fact), setOf(EvidenceSource.SEARCH), "entry"),
      EvidenceState("environment-clue", setOf(fact), setOf(EvidenceSource.ENVIRONMENT), "exit")
    ).associateBy { it.id }
    val action = LevelActionRule(
      id = "open_exit",
      matchGroups = listOf(setOf("mở"), setOf("cửa", "thoát")),
      conditions = setOf("zone:entry"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
      reply = "Cánh cửa mở.",
      semanticDescriptions = setOf("bước qua cánh cửa thoát")
    )
    return LevelDefinition(
      id = id,
      name = "Fixture $id",
      initialZoneId = "entry",
      zones = zones,
      escapeBlueprint = EscapeBlueprintState("fixture-exit", setOf(fact), listOf("open_exit"), locked = true),
      evidence = evidence,
      exploreRoute = listOf("exit"),
      actions = mapOf(action.id to action)
    )
  }

  private fun catalog(id: String): LevelCatalog = LevelCatalog.from(listOf(
    LevelCatalogEntry(id, name = "Fixture $id", kind = LevelKind.SPECIAL)
  ))

  private fun graphEntry(id: String, order: Long, targets: List<String> = emptyList()) = LevelCatalogEntry(
    id = id, name = id, kind = LevelKind.SPECIAL, campaignId = "test", campaignOrder = order,
    outgoingTransitions = targets.map(::LevelTransition)
  )
}
