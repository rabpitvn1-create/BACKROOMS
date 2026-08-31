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

  @Test fun directActionSelfCycleRejected() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf("action:act_a"), listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("self-cycle", setOf("F"), listOf("act_a")),
      actions = mapOf("act_a" to actA)
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-self", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun twoActionCycleRejected() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf("action:act_b"), listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "env_a", "1")))
    val actB = LevelActionRule("act_b", listOf(setOf("b")), setOf("action:act_a"), listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("two-act-cycle", setOf("F"), listOf("act_a", "act_b")),
      actions = mapOf("act_a" to actA, "act_b" to actB)
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-two-act", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun requiredActionDependingOnLaterActionRejected() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf("action:act_b"), listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "k", "v")))
    val actB = LevelActionRule("act_b", listOf(setOf("b")), setOf(), listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("forward-dep", setOf("F"), listOf("act_a", "act_b")),
      actions = mapOf("act_a" to actA, "act_b" to actB)
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-forward", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun factToActionToSameFactCycleRejected() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf("fact:F"), listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "gate", "open")))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("fact-action-cycle", setOf("F"), listOf("act_a")),
      actions = mapOf("act_a" to actA),
      evidence = mapOf(
        "ev1" to EvidenceState("ev1", setOf("F"), setOf(EvidenceSource.SEARCH), "entry", setOf("env:gate=open")),
        "ev2" to EvidenceState("ev2", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry", setOf("env:gate=open"))
      )
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-fact-act", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_fact_unreachable:F"))
  }

  @Test fun twoFactTwoActionCycleRejected() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf("fact:F1"), listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "env1", "ok")))
    val actB = LevelActionRule("act_b", listOf(setOf("b")), setOf("fact:F2"), listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("two-fact-two-act", setOf("F1", "F2"), listOf("act_a", "act_b")),
      actions = mapOf("act_a" to actA, "act_b" to actB),
      evidence = mapOf(
        "ev1a" to EvidenceState("ev1a", setOf("F1"), setOf(EvidenceSource.SEARCH), "entry", setOf("fact:F2")),
        "ev1b" to EvidenceState("ev1b", setOf("F1"), setOf(EvidenceSource.ANOMALY), "entry", setOf("fact:F2")),
        "ev2a" to EvidenceState("ev2a", setOf("F2"), setOf(EvidenceSource.SEARCH), "entry", setOf("env:env1=ok")),
        "ev2b" to EvidenceState("ev2b", setOf("F2"), setOf(EvidenceSource.ANOMALY), "entry", setOf("env:env1=ok"))
      )
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-2f2a", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_fact_unreachable:"))
  }

  @Test fun impossibleEnvironmentPreconditionRejected() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf("env:magic=true"), listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("impossible-env", setOf("F"), listOf("act_a")),
      actions = mapOf("act_a" to actA)
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-impos-env", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_action_unreachable:act_a"))
  }

  @Test fun environmentPreconditionMadeTrueByEarlierRequiredActionAccepted() {
    val actA = LevelActionRule("act_a", listOf(setOf("a")), setOf(), listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, "breaker", "off")))
    val actB = LevelActionRule("act_b", listOf(setOf("b")), setOf("env:breaker=off"), listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("env-mutation-ok", setOf("F"), listOf("act_a", "act_b")),
      actions = mapOf("act_a" to actA, "act_b" to actB)
    )
    val level = LevelInstanceGenerator.commitCandidate(definition(), "seed-env-ok", candidate, "test")
    assertNotNull(level)
    assertEquals(listOf("act_a", "act_b"), level.escapeBlueprint.requiredActions)
  }

  @Test fun unreachableEvidenceZoneRejectedWhenItBlocksRequiredFact() {
    val isolatedZone = ZoneState("isolated", "Isolated Zone", emptySet(), setOf("isolated"))
    val candidate = candidate().copy(
      zones = candidate().zones + ("isolated" to isolatedZone),
      evidence = mapOf(
        "e1" to EvidenceState("e1", setOf("F"), setOf(EvidenceSource.SEARCH), "isolated"),
        "e2" to EvidenceState("e2", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry")
      )
    )
    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-unreachable-ev-zone", candidate, "test")
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("required_fact_unreachable:F"))
  }

  @Test fun validMultiStepPuzzleWithEvidenceQuorumAndEnvironmentMutationAndMovementAndCompletionAccepted() {
    val hiddenZone = ZoneState("hidden", "Hidden Room", setOf("exit"), setOf("utility"))
    val exitZone = ZoneState("exit", "Service Exit", emptySet(), setOf("escape"))
    val entryZone = ZoneState("entry", "Entry", setOf("exit"), setOf("entry"))

    val actA = LevelActionRule("act_unlock", listOf(setOf("mở")), setOf("fact:F_KEY"), listOf(
      LevelEffect(LevelEffectType.SET_ENVIRONMENT, "door_unlocked", "true"),
      LevelEffect(LevelEffectType.MOVE_TO_ZONE, "hidden")
    ))
    val actB = LevelActionRule("act_escape", listOf(setOf("thoát")), setOf("env:door_unlocked=true", "fact:F_PASSCODE"), listOf(
      LevelEffect(LevelEffectType.COMPLETE_LEVEL)
    ))

    val evidenceMap = mapOf(
      "e_key_search" to EvidenceState("e_key_search", setOf("F_KEY"), setOf(EvidenceSource.SEARCH), "entry"),
      "e_key_anomaly" to EvidenceState("e_key_anomaly", setOf("F_KEY"), setOf(EvidenceSource.ANOMALY), "entry"),
      "e_pass_search" to EvidenceState("e_pass_search", setOf("F_PASSCODE"), setOf(EvidenceSource.SEARCH), "hidden"),
      "e_pass_survivor" to EvidenceState("e_pass_survivor", setOf("F_PASSCODE"), setOf(EvidenceSource.SURVIVOR), "hidden")
    )

    val multiStepCandidate = candidate().copy(
      initialZoneId = "entry",
      zones = mapOf("entry" to entryZone, "hidden" to hiddenZone, "exit" to exitZone),
      escapeBlueprint = EscapeBlueprintState("multi-step", setOf("F_KEY", "F_PASSCODE"), listOf("act_unlock", "act_escape")),
      actions = mapOf("act_unlock" to actA, "act_escape" to actB),
      evidence = evidenceMap
    )

    val level = LevelInstanceGenerator.commitCandidate(definition(), "seed-multi-step", multiStepCandidate, "test")
    assertNotNull(level)
    assertTrue(BlueprintValidator.validate(level, definition()).valid)
  }

  @Test fun currentLevelZeroAndLevelOneProceduralDefinitionsStillValidate() {
    val level0Def = LevelDefinitionJson.decode(java.io.File("android-apk/app/src/main/assets/levels/0.json").readText())
    val level1Def = LevelDefinitionJson.decode(java.io.File("android-apk/app/src/main/assets/levels/1.json").readText())

    val val0 = LevelDefinitionValidator.validate(level0Def)
    assertTrue(val0.errors.joinToString(","), val0.valid)

    val val1 = LevelDefinitionValidator.validate(level1Def)
    assertTrue(val1.errors.joinToString(","), val1.valid)
  }

  @Test fun validatorIsDeterministic() {
    val level = LevelInstanceGenerator.fromDefinition(definition(), "seed-det")
    val res1 = BlueprintValidator.validate(level, definition())
    val res2 = BlueprintValidator.validate(level, definition())

    assertEquals(res1.valid, res2.valid)
    assertEquals(res1.errors, res2.errors)
  }

  @Test fun validatorHasBoundedSearchBudgetRegressionForPathologicalInput() {
    // Construct evidence chain with recursive dependencies to exercise budget check
    val evidenceList = (0 until 6000).associate { i ->
      "e_$i" to EvidenceState(
        id = "e_$i",
        supports = setOf("F_$i"),
        sources = setOf(EvidenceSource.SEARCH),
        zoneId = "entry",
        discoverConditions = if (i == 0) emptySet() else setOf("fact:F_${i - 1}")
      )
    }
    val candidate = candidate().copy(
      escapeBlueprint = EscapeBlueprintState("budget-test", setOf("F_5999"), listOf("fixture_exit")),
      evidence = evidenceList
    )

    val error = runCatching {
      LevelInstanceGenerator.commitCandidate(definition(), "seed-budget", candidate, "test")
    }.exceptionOrNull()

    assertNotNull(error)
    assertTrue(error!!.message.orEmpty().contains("validation_budget_exceeded") || error.message.orEmpty().contains("required_fact_unreachable"))
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
