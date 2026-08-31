package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class SemanticMapperPrivacyContractTest {
  @Test fun safeCandidateProjectionCannotCarryHiddenRuntimeFields() {
    val rule = LevelActionRule(
      id = "secret_action_id",
      matchGroups = listOf(setOf("secret")),
      conditions = setOf("fact:HIDDEN", "zone:hidden_zone"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
      semanticDescriptions = setOf("kiểm tra cánh cửa đã nhìn thấy")
    )
    val projected = listOf(rule).sortedBy { it.id }.mapIndexed { index, action ->
      SemanticActionDescriptor("candidate-$index", action.semanticDescriptions)
    }.single().toString()

    assertFalse(projected.contains(rule.id))
    assertFalse(projected.contains("HIDDEN"))
    assertFalse(projected.contains("hidden_zone"))
    assertFalse(projected.contains("COMPLETE_LEVEL"))
    assertFalse(projected.contains("requiredActions"))
    assertFalse(projected.contains("escapeBlueprint"))
  }

  @Test fun sanitizedGenerationRequestDoesNotContainFixtureSolution() {
    val definition = fixture()
    val request = LevelGenerationRequestFactory.build(definition, "privacy-seed").toString()

    assertFalse(request.contains("hidden-solution"))
    assertFalse(request.contains("secret_action_id"))
    assertFalse(request.contains("HIDDEN_FACT"))
    assertFalse(request.contains("secret evidence"))
    assertFalse(request.contains("requiredActions"))
    assertFalse(request.contains("requiredFacts"))
    assertFalse(request.contains("escapeBlueprint"))
    assertFalse(request.contains("COMPLETE_LEVEL"))
  }

  private fun fixture(): LevelDefinition {
    val zones = mapOf(
      "entry" to ZoneState("entry", "Entry", setOf("exit"), setOf("entry")),
      "exit" to ZoneState("exit", "Exit", emptySet(), setOf("escape"))
    )
    val action = LevelActionRule(
      "secret_action_id", listOf(setOf("mở")), effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    return LevelDefinition(
      id = "privacy", name = "Privacy", initialZoneId = "entry", zones = zones,
      escapeBlueprint = EscapeBlueprintState("hidden-solution", setOf("HIDDEN_FACT"), listOf(action.id)),
      evidence = mapOf("secret evidence" to EvidenceState("secret evidence", setOf("HIDDEN_FACT"), setOf(EvidenceSource.SEARCH), "entry")),
      exploreRoute = listOf("exit"), actions = mapOf(action.id to action)
    )
  }
}
