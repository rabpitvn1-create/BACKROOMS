package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EvidenceQuorumRuntimeTest {
  @Test fun requiredFactNeedsIndependentEvidenceBeforeExecuteUnlocks() {
    val definition = definition()
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, definition.id, "quorum-seed")

    val first = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm")
    state = first.state
    assertTrue(first.progressed)
    assertEquals(setOf("search-fact"), first.evidenceIds)
    assertFalse("EXIT_FACT" in state.levelInstance!!.discoveredFacts)

    val blocked = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "mở lối thoát")
    assertFalse(blocked.progressed)
    assertFalse(blocked.escaped)

    val second = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá")
    state = second.state
    assertTrue(second.progressed)
    assertEquals(setOf("anomaly-fact"), second.evidenceIds)
    assertTrue("EXIT_FACT" in state.levelInstance!!.discoveredFacts)

    val escaped = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "mở lối thoát")
    assertTrue(escaped.progressed)
    assertTrue(escaped.escaped)
  }

  @Test fun staleRequiredFactIsRemovedWhenItsEvidenceQuorumIsMissing() {
    val definition = definition()
    val registry = LevelRegistry.from(listOf(definition))
    val installed = GenericLevelRuntime.install(GameState.initial(), registry, definition.id, "stale-seed")
    val level = installed.levelInstance!!
    val stale = installed.copy(levelInstance = level.copy(discoveredFacts = setOf("EXIT_FACT")))

    val result = GenericLevelRuntime.apply(stale, registry, ActionKind.EXECUTE, "mở lối thoát")

    assertFalse(result.progressed)
    assertFalse("EXIT_FACT" in result.state.levelInstance!!.discoveredFacts)
  }

  private fun definition(): LevelDefinition {
    val zones = linkedMapOf(
      "entry" to ZoneState("entry", "Entry", setOf("exit"), setOf("entry")),
      "exit" to ZoneState("exit", "Exit", setOf("entry"), setOf("escape"))
    )
    val evidence = linkedMapOf(
      "search-fact" to EvidenceState(
        id = "search-fact",
        supports = setOf("EXIT_FACT"),
        sources = setOf(EvidenceSource.SEARCH),
        zoneId = "entry"
      ),
      "anomaly-fact" to EvidenceState(
        id = "anomaly-fact",
        supports = setOf("EXIT_FACT"),
        sources = setOf(EvidenceSource.ANOMALY),
        zoneId = "exit",
        discoverConditions = setOf("visit:exit:1")
      )
    )
    val exit = LevelActionRule(
      id = "open_exit",
      matchGroups = listOf(setOf("mở"), setOf("thoát")),
      conditions = setOf("fact:EXIT_FACT"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
      reply = "Exit opens."
    )
    return LevelDefinition(
      id = "test.quorum",
      name = "Evidence Quorum Test",
      initialZoneId = "entry",
      zones = zones,
      escapeBlueprint = EscapeBlueprintState(
        solutionId = "quorum-solution",
        requiredFacts = setOf("EXIT_FACT"),
        requiredActions = listOf("open_exit")
      ),
      evidence = evidence,
      exploreRoute = listOf("exit"),
      actions = mapOf(exit.id to exit),
      canonProfile = LevelCanonProfile(requiredZoneTags = setOf("entry", "escape")),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 2,
        maxZones = 4,
        minEvidencePerRequiredFact = 2,
        minEvidenceSourceTypesPerRequiredFact = 2,
        maxRequiredActions = 2
      )
    )
  }
}
