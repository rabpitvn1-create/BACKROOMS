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

  @Test fun directActionSelfCycleIsRejected() {
    val action = LevelActionRule(
      id = "act_a",
      matchGroups = listOf(setOf("a")),
      conditions = setOf("action:act_a"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    val generated = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("self-cycle", setOf("F"), listOf("act_a"), locked = false),
      actions = mapOf(action.id to action)
    )

    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-self-cycle", generated, "candidate-test")
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun requiredActionCannotDependOnLaterRequiredAction() {
    val first = LevelActionRule(
      id = "act_a",
      matchGroups = listOf(setOf("a")),
      conditions = setOf("action:act_b"),
      effects = listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "gate", "open"))
    )
    val second = LevelActionRule(
      id = "act_b",
      matchGroups = listOf(setOf("b")),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    val generated = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("forward-dependency", setOf("F"), listOf("act_a", "act_b"), locked = false),
      actions = mapOf(first.id to first, second.id to second)
    )

    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-forward", generated, "candidate-test")
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun impossibleEnvironmentPreconditionIsRejected() {
    val action = LevelActionRule(
      id = "act_a",
      matchGroups = listOf(setOf("a")),
      conditions = setOf("env:magic=true"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    val generated = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("impossible-env", setOf("F"), listOf("act_a"), locked = false),
      actions = mapOf(action.id to action)
    )

    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-impossible-env", generated, "candidate-test")
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun earlierRequiredActionMaySatisfyLaterEnvironmentPrecondition() {
    val first = LevelActionRule(
      id = "act_a",
      matchGroups = listOf(setOf("a")),
      effects = listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "breaker", "off"))
    )
    val second = LevelActionRule(
      id = "act_b",
      matchGroups = listOf(setOf("b")),
      conditions = setOf("env:breaker=off"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    val generated = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("env-chain", setOf("F"), listOf("act_a", "act_b"), locked = false),
      actions = mapOf(first.id to first, second.id to second)
    )

    val level = LevelInstanceGenerator.commitCandidate(definition(), "seed-env-chain", generated, "candidate-test")

    assertEquals(listOf("act_a", "act_b"), level.escapeBlueprint.requiredActions)
  }

  @Test fun unreachableEvidenceZoneCannotSatisfyFactQuorum() {
    val generated = candidate().copy(
      zones = candidate().zones + ("isolated" to ZoneState("isolated", "Isolated", emptySet(), setOf("utility"))),
      evidence = mapOf(
        "reachable" to EvidenceState("reachable", setOf("F"), setOf(EvidenceSource.SEARCH), "entry"),
        "isolated" to EvidenceState("isolated", setOf("F"), setOf(EvidenceSource.ANOMALY), "isolated")
      )
    )

    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-isolated", generated, "candidate-test")
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_fact_unreachable:F"))
  }

  @Test fun moveEffectCanRevealLaterFactAndKeepPuzzleSolvable() {
    val zones = linkedMapOf(
      "entry" to ZoneState("entry", "Entry", setOf("exit"), setOf("entry", "parking")),
      "hidden" to ZoneState("hidden", "Hidden", emptySet(), setOf("parking")),
      "exit" to ZoneState("exit", "Exit", emptySet(), setOf("escape", "parking"))
    )
    val first = LevelActionRule(
      id = "unlock",
      matchGroups = listOf(setOf("unlock")),
      conditions = setOf("fact:F"),
      effects = listOf(LevelEffect(LevelEffectType.MOVE_TO_ZONE, zoneId = "hidden"))
    )
    val second = LevelActionRule(
      id = "escape",
      matchGroups = listOf(setOf("escape")),
      conditions = setOf("fact:G"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    val evidence = mapOf(
      "f-search" to EvidenceState("f-search", setOf("F"), setOf(EvidenceSource.SEARCH), "entry"),
      "f-anomaly" to EvidenceState("f-anomaly", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry"),
      "g-search" to EvidenceState("g-search", setOf("G"), setOf(EvidenceSource.SEARCH), "hidden"),
      "g-anomaly" to EvidenceState("g-anomaly", setOf("G"), setOf(EvidenceSource.ANOMALY), "hidden")
    )
    val generated = candidate().copy(
      initialZoneId = "entry",
      zones = zones,
      escapeBlueprint = EscapeBlueprintState("move-chain", setOf("F", "G"), listOf("unlock", "escape"), locked = false),
      evidence = evidence,
      exploreRoute = listOf("exit"),
      actions = mapOf(first.id to first, second.id to second)
    )

    val level = LevelInstanceGenerator.commitCandidate(definition(), "seed-move-chain", generated, "candidate-test")

    assertTrue(BlueprintValidator.validate(level, definition()).valid)
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