package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class EntityEncounterPolicyTest {
  @Test fun everyRandomThresholdUsesSeventyPercentWithIntegerFloor() {
    assertEquals(listOf(1263, 1400, 1505, 1505, 1267, 1540, 1263),
      listOf(1805, 2000, 2150, 2150, 1810, 2200, 1805).map(EntityEncounterPolicy::scaledThreshold))
    assertEquals(210, EntityEncounterPolicy.scaledThreshold(300))
    assertEquals(700, EntityEncounterPolicy.scaledThreshold(1000))
    assertEquals(350, EntityEncounterPolicy.scaledThreshold(500))
    assertEquals(140, EntityEncounterPolicy.scaledThreshold(200))
  }

  @Test fun proceduralConstraintIsTheOnlyLevelInputToEligibility() {
    assertFalse(EntityEncounterPolicy.randomEncounterAllowed(ProceduralGenerationConstraints(allowEntities = false)))
    assertTrue(EntityEncounterPolicy.randomEncounterAllowed(ProceduralGenerationConstraints(allowEntities = true)))
    assertTrue(EntityEncounterPolicy.randomEncounterAllowed(null))
  }

  @Test fun arbitraryLevelIdsDoNotEnterEncounterPolicy() {
    val definition = LevelDefinition(
      id = "level:future/alpha", name = "Future", initialZoneId = "z",
      zones = mapOf("z" to ZoneState("z", "Zone")),
      escapeBlueprint = EscapeBlueprintState("hidden", emptySet(), emptyList()), evidence = emptyMap(),
      generationConstraints = ProceduralGenerationConstraints(allowEntities = false)
    )
    assertFalse(EntityEncounterPolicy.randomEncounterAllowed(definition.generationConstraints))
  }
}
