package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class WorldDirectorTest {
  @Test fun modelProposalCannotBypassEntityConstraint() {
    val definition = definition(allowEntities = false, proceduralTopology = true)
    val state = state(definition)
    val director = WorldDirector(WorldDirectorPolicy { WorldPressureProposal.ENTITY_PRESSURE })

    val decision = director.propose(state, definition, ActionKind.EXPLORE)

    assertEquals(WorldPressureProposal.ENTITY_PRESSURE, decision.proposed)
    assertEquals(WorldPressureProposal.NONE, decision.accepted)
    assertEquals("core_rejected_illegal_proposal", decision.reason)
  }

  @Test fun mazePressureRequiresConnectedLocalGraphWithAlternativeRoute() {
    val safeDefinition = definition(allowEntities = true, proceduralTopology = true)
    val safeState = state(safeDefinition)
    val director = WorldDirector(WorldDirectorPolicy { WorldPressureProposal.MAZE_PRESSURE })

    val accepted = director.propose(safeState, safeDefinition, ActionKind.EXPLORE)
    assertEquals(WorldPressureProposal.MAZE_PRESSURE, accepted.accepted)

    val disconnected = safeDefinition.copy(
      zones = safeDefinition.zones + ("isolated" to ZoneState("isolated", "Isolated"))
    )
    val rejected = director.propose(state(disconnected), disconnected, ActionKind.EXPLORE)
    assertEquals(WorldPressureProposal.NONE, rejected.accepted)
    assertEquals("core_rejected_illegal_proposal", rejected.reason)
  }

  @Test fun proposalIsReadOnlyAndCannotManufactureInventoryOrTopology() {
    val definition = definition(allowEntities = true, proceduralTopology = true)
    val before = state(definition)
    val inventoryBefore = before.inventories
    val topologyBefore = before.levelInstance!!.zones
    val director = WorldDirector(WorldDirectorPolicy { WorldPressureProposal.ITEM_OPPORTUNITY })

    val decision = director.propose(before, definition, ActionKind.SEARCH)

    assertEquals(WorldPressureProposal.ITEM_OPPORTUNITY, decision.accepted)
    assertEquals(inventoryBefore, before.inventories)
    assertEquals(topologyBefore, before.levelInstance!!.zones)
  }

  @Test fun modelFeaturesExcludeHiddenNavigationAndPuzzleTerms() {
    val context = WorldDirectorContext(
      actionKind = ActionKind.EXPLORE,
      safeZoneTags = WorldDirectorFeatures.safeZoneTags(
        setOf("loop", "dark", "escape", "level_transition", "secret_solution", "blueprint_gate")
      ),
      visitCount = 3,
      revision = 8,
      recentMutationKind = "move",
      discoveredEvidenceCount = 2,
      legalProposals = setOf(
        WorldPressureProposal.NONE,
        WorldPressureProposal.MAZE_PRESSURE,
        WorldPressureProposal.ENTITY_PRESSURE
      )
    )

    val features = WorldDirectorFeatures.describe(context)

    assertTrue(features.contains("zone_loop"))
    assertTrue(features.contains("zone_dark"))
    assertTrue(features.contains("candidate_maze_pressure"))
    assertTrue(features.contains("candidate_entity_pressure"))
    for (hidden in listOf("escape", "transition", "secret", "solution", "blueprint", "required")) {
      assertFalse(hidden, features.contains(hidden))
    }
  }

  @Test fun executeCannotAskDirectorToCreateWorldPressure() {
    val definition = definition(allowEntities = true, proceduralTopology = true)
    val state = state(definition)
    val director = WorldDirector(WorldDirectorPolicy { WorldPressureProposal.MAZE_PRESSURE })

    val decision = director.propose(state, definition, ActionKind.EXECUTE)

    assertEquals(WorldPressureProposal.NONE, decision.accepted)
  }

  private fun definition(allowEntities: Boolean, proceduralTopology: Boolean): LevelDefinition {
    val zones = mapOf(
      "a" to ZoneState("a", "A", setOf("b", "c"), setOf("loop", "entry")),
      "b" to ZoneState("b", "B", setOf("a", "c"), setOf("dark")),
      "c" to ZoneState("c", "C", setOf("a", "b"), setOf("escape", "level_transition"))
    )
    val evidence = mapOf(
      "f-search" to EvidenceState("f-search", setOf("F"), setOf(EvidenceSource.SEARCH), "a"),
      "f-environment" to EvidenceState("f-environment", setOf("F"), setOf(EvidenceSource.ENVIRONMENT), "b")
    )
    val exit = LevelActionRule(
      id = "exit",
      matchGroups = listOf(setOf("exit")),
      conditions = setOf("zone:c", "fact:F"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    return LevelDefinition(
      id = "world-director-test",
      name = "World Director Test",
      initialZoneId = "a",
      zones = zones,
      escapeBlueprint = EscapeBlueprintState("hidden-solution", setOf("F"), listOf("exit")),
      evidence = evidence,
      exploreRoute = listOf("b", "c"),
      actions = mapOf("exit" to exit),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 3,
        maxZones = 8,
        minEvidencePerRequiredFact = 2,
        minEvidenceSourceTypesPerRequiredFact = 2,
        maxRequiredActions = 4,
        allowEntities = allowEntities,
        proceduralTopology = proceduralTopology
      )
    )
  }

  private fun state(definition: LevelDefinition): GameState {
    val level = GenericLevelGenerator.generate(definition, "world-director-seed").copy(
      currentZoneId = "a",
      environment = mapOf("visits:a" to "3")
    )
    return GameState.initial().copy(levelInstance = level)
  }
}
