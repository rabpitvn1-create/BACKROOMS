package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LevelGenerationRequestTest {
  @Test fun requestExposesCanonBoundsButNotFixturePuzzle() {
    val definition = LevelDefinition(
      id = "742.13",
      parentId = "742",
      name = "Future Sublevel",
      initialZoneId = "fixture_secret_zone",
      zones = emptyMap(),
      escapeBlueprint = EscapeBlueprintState(
        solutionId = "FIXTURE_SECRET_SOLUTION",
        requiredFacts = setOf("FIXTURE_SECRET_FACT"),
        requiredActions = listOf("FIXTURE_SECRET_ACTION")
      ),
      evidence = emptyMap(),
      actions = mapOf(
        "FIXTURE_SECRET_ACTION" to LevelActionRule(
          id = "FIXTURE_SECRET_ACTION",
          matchGroups = listOf(setOf("secret"))
        )
      ),
      canonProfile = LevelCanonProfile(
        environmentTags = setOf("industrial"),
        allowedPhenomena = setOf("blackout"),
        forbiddenClaims = setOf("backrooms_confirmed_conscious")
      ),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 5,
        maxZones = 18,
        proceduralTopology = true,
        proceduralEvidencePlacement = true,
        proceduralEscapeBlueprint = true
      )
    )

    val request = LevelGenerationRequestFactory.build(definition, "run-seed-123")
    val raw = request.toString()

    assertEquals("742.13", request.getString("levelId"))
    assertEquals("run-seed-123", request.getString("runSeed"))
    assertEquals(5, request.getJSONObject("generationConstraints").getInt("minZones"))
    assertTrue(request.getJSONObject("canonProfile").getJSONArray("environmentTags").toString().contains("industrial"))
    assertTrue(request.getJSONObject("rules").getBoolean("runtimeProgressFieldsForbidden"))
    assertFalse(raw.contains("FIXTURE_SECRET_SOLUTION"))
    assertFalse(raw.contains("FIXTURE_SECRET_FACT"))
    assertFalse(raw.contains("FIXTURE_SECRET_ACTION"))
    assertFalse(raw.contains("fixture_secret_zone"))
    assertFalse(raw.contains("completedActions"))
    assertFalse(raw.contains("discoveredFacts"))
  }
}
