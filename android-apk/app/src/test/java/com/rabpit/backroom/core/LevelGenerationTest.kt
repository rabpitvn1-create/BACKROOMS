package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LevelGenerationTest {
  @Test fun definitionRoundTripPreservesCanonAndGenerationConstraints() {
    val original = definition()
    val decoded = LevelDefinitionJson.decode(LevelDefinitionJson.encode(original).toString())

    assertEquals(original, decoded)
    assertEquals(setOf("concrete"), decoded.canonProfile.environmentTags)
    assertTrue(decoded.generationConstraints.proceduralEscapeBlueprint)
  }

  @Test fun committedCandidateIsLockedSanitizedAndFingerprintable() {
    val definition = definition()
    val dirtyEvidence = candidate().evidence.mapValues { (_, item) -> item.copy(discovered = true, discoveredAtRevision = 99) }
    val generated = LevelInstanceGenerator.commitCandidate(
      definition,
      "seed-42",
      candidate().copy(evidence = dirtyEvidence),
      "test-generator-v1"
    )

    assertTrue(generated.escapeBlueprint.locked)
    assertEquals("seed-42", generated.runSeed)
    assertEquals("test-generator-v1", generated.generatorVersion)
    assertNotNull(generated.generationFingerprint)
    assertTrue(generated.evidence.values.none { it.discovered })
    assertTrue(generated.discoveredFacts.isEmpty())
    assertTrue(generated.completedActions.isEmpty())
    assertTrue(generated.mutations.isEmpty())
  }

  @Test fun generatedPuzzleMayUseDifferentActionRulesThanDefinitionFixture() {
    val definition = definition()
    val generatedAction = LevelActionRule(
      id = "generated_exit",
      matchGroups = listOf(setOf("rời", "thoát")),
      conditions = setOf("zone:exit"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
      reply = "Generated exit works."
    )
    val generatedCandidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("generated-solution", setOf("F"), listOf("generated_exit"), locked = false),
      actions = mapOf(generatedAction.id to generatedAction)
    )

    val level = LevelInstanceGenerator.commitCandidate(definition, "seed-new-puzzle", generatedCandidate, "candidate-test")
    val registry = LevelRegistry.from(listOf(definition))
    var state = GameState.initial().copy(levelInstance = level)
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    val result = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "Thoát khỏi đây")

    assertTrue(result.progressed)
    assertTrue(result.escaped)
    assertEquals(listOf("generated_exit"), result.state.levelInstance?.completedActions)
  }

  @Test fun forbiddenPhenomenonIsRejectedBeforeCommit() {
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(
        definition(),
        "seed-bad",
        candidate().copy(phenomena = setOf("telepathic_level")),
        "candidate-test"
      )
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("forbidden_phenomenon:telepathic_level"))
  }

  @Test fun forbiddenCanonClaimIsRejectedBeforeCommit() {
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(
        definition(),
        "seed-bad-claim",
        candidate().copy(canonClaims = setOf("backrooms_confirmed_conscious")),
        "candidate-test"
      )
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("forbidden_canon_claim:backrooms_confirmed_conscious"))
  }

  @Test fun circularRequiredFactDependencyIsRejectedAsSoftlock() {
    val looping = candidate().copy(
      evidence = mapOf(
        "a" to EvidenceState("a", setOf("F"), setOf(EvidenceSource.SEARCH), "entry", setOf("fact:F")),
        "b" to EvidenceState("b", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry", setOf("fact:F"))
      )
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-loop", looping, "candidate-test")
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_fact_unreachable:F"))
  }

  @Test fun candidateJsonRoundTripContainsOnlyGenerationPayload() {
    val original = candidate()
    val raw = LevelGenerationCandidateJson.encode(original).toString()
    val decoded = LevelGenerationCandidateJson.decode(raw)

    assertEquals(original, decoded)
    assertFalse(raw.contains("completedActions"))
    assertFalse(raw.contains("discoveredFacts"))
    assertFalse(raw.contains("mutations"))
  }

  @Test fun oldLevelInstanceJsonWithoutGeneratedRuntimeFieldsStillDecodes() {
    val current = LevelInstanceGenerator.fromDefinition(definition(), "legacy-seed")
    val json = LevelInstanceJson.encode(current)
    json.remove("actions")
    json.remove("exploreRoute")
    json.remove("replies")
    json.remove("environmentTags")
    json.remove("phenomena")
    json.remove("canonClaims")
    json.remove("generatorVersion")
    json.remove("generationSchemaVersion")
    json.remove("generationFingerprint")

    val decoded = LevelInstanceJson.decode(json)

    assertEquals("legacy", decoded.generatorVersion)
    assertTrue(decoded.actions.isEmpty())
    assertTrue(BlueprintValidator.validate(decoded).valid)
  }

  private fun definition(): LevelDefinition {
    val zones = linkedMapOf(
      "entry" to ZoneState("entry", "Concrete Entry", setOf("exit"), setOf("entry", "parking")),
      "exit" to ZoneState("exit", "Service Exit", emptySet(), setOf("escape", "parking"))
    )
    val evidence = mapOf(
      "search-f" to EvidenceState("search-f", setOf("F"), setOf(EvidenceSource.SEARCH), "entry"),
      "anomaly-f" to EvidenceState("anomaly-f", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry")
    )
    val action = LevelActionRule(
      id = "fixture_exit",
      matchGroups = listOf(setOf("mở", "vào")),
      conditions = setOf("zone:exit"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
      reply = "Fixture exit works."
    )
    return LevelDefinition(
      id = "test.1",
      name = "Test Parking Level",
      initialZoneId = "entry",
      zones = zones,
      environment = mapOf("power" to "on"),
      escapeBlueprint = EscapeBlueprintState("fixture", setOf("F"), listOf("fixture_exit"), locked = true),
      evidence = evidence,
      exploreRoute = listOf("exit"),
      actions = mapOf(action.id to action),
      canonProfile = LevelCanonProfile(
        environmentTags = setOf("concrete"),
        requiredZoneTags = setOf("entry", "escape"),
        allowedPhenomena = setOf("blackout"),
        forbiddenClaims = setOf("backrooms_confirmed_conscious")
      ),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 2,
        maxZones = 4,
        minEvidencePerRequiredFact = 2,
        minEvidenceSourceTypesPerRequiredFact = 2,
        maxRequiredActions = 4,
        proceduralTopology = true,
        proceduralLandmarks = true,
        proceduralEvidencePlacement = true,
        proceduralEscapeBlueprint = true
      )
    )
  }

  private fun candidate(): LevelGenerationCandidate {
    val definition = definition()
    return LevelGenerationCandidate(
      initialZoneId = definition.initialZoneId,
      zones = definition.zones,
      landmarks = definition.landmarks,
      environment = definition.environment,
      environmentTags = definition.canonProfile.environmentTags,
      phenomena = setOf("blackout"),
      escapeBlueprint = definition.escapeBlueprint.copy(locked = false),
      evidence = definition.evidence,
      npcKnowledge = definition.npcKnowledge,
      exploreRoute = definition.exploreRoute,
      actions = definition.actions,
      replies = definition.replies
    )
  }
}
