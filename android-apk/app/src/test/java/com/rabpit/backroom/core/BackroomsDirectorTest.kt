package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class BackroomsDirectorTest {
  @Test fun policyCanChooseOnlyFromEngineEligibleEvidence() {
    val definition = definition()
    val level = instance(definition, visitCount = 1)
    val director = BackroomsDirector(BackroomsDirectorPolicy { DirectorEvidencePreference.SURVIVOR })
    val eligible = listOf(
      definition.evidence.getValue("survivor-f"),
      definition.evidence.getValue("anomaly-f")
    )

    val selected = director.selectEvidence(level, definition, ActionKind.EXPLORE, eligible)

    assertEquals(listOf("survivor-f"), selected.map { it.id })
    assertTrue(selected.all { it in eligible })
  }

  @Test fun unavailableModelPreferenceFallsBackToBehaviorAwareLegalSource() {
    val definition = definition()
    val level = instance(definition, visitCount = 2)
    val director = BackroomsDirector(BackroomsDirectorPolicy { DirectorEvidencePreference.SEARCH })
    val eligible = listOf(
      definition.evidence.getValue("environment-f"),
      definition.evidence.getValue("anomaly-f")
    )

    val selected = director.selectEvidence(level, definition, ActionKind.EXPLORE, eligible)

    assertEquals(listOf("environment-f"), selected.map { it.id })
  }

  @Test fun searchRemainsIncrementalEvenWhenSeveralCluesAreLegal() {
    val definition = definition()
    val level = instance(definition, visitCount = 1)
    val director = BackroomsDirector(BackroomsDirectorPolicy { DirectorEvidencePreference.SEARCH })
    val eligible = listOf(
      definition.evidence.getValue("search-f"),
      definition.evidence.getValue("search-g")
    )

    val selected = director.selectEvidence(level, definition, ActionKind.SEARCH, eligible)

    assertEquals(1, selected.size)
    assertTrue(selected.single().id in setOf("search-f", "search-g"))
  }

  @Test fun directorFeatureTextContainsBehaviorButNoLevelOrPuzzleIdentifiers() {
    val context = BackroomsDirectorContext(
      actionKind = ActionKind.EXPLORE,
      levelId = "742.13-secret-level",
      zoneId = "secret-exit-zone",
      zoneTags = setOf("loop", "memory_room"),
      visitCount = 3,
      revision = 9,
      recentMutationKind = "move",
      discoveredEvidenceCount = 2,
      discoveredSourceCounts = mapOf(EvidenceSource.SEARCH to 1),
      candidateSourceCounts = mapOf(EvidenceSource.ENVIRONMENT to 1, EvidenceSource.ANOMALY to 1)
    )

    val text = BackroomsDirectorFeatures.describe(context)

    assertTrue(text.contains("action_explore"))
    assertTrue(text.contains("visit_deep"))
    assertTrue(text.contains("candidate_environment"))
    assertTrue(text.contains("candidate_anomaly"))
    assertFalse(text.contains("742.13-secret-level"))
    assertFalse(text.contains("secret-exit-zone"))
  }

  @Test fun deterministicDirectorPreservesLevelZeroFallbackSolvePath() {
    val definition = loadLevelZero()
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "0", "director-level-zero-test")

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm").state

    val facts = state.levelInstance!!.discoveredFacts
    assertTrue("MARKERS_ARE_UNRELIABLE" in facts)
    assertTrue("CONCRETE_DRIFT_IS_TRANSITION" in facts)
    assertTrue("HUM_FADES_ALONG_TRANSITION" in facts)

    val follow = GenericLevelRuntime.apply(
      state, registry, ActionKind.EXECUTE, "đi theo hành lang có đèn rung và tiếng ù chồng lên nhau"
    )
    val finish = GenericLevelRuntime.apply(
      follow.state, registry, ActionKind.EXECUTE, "tiếp tục cho tới khi kiến trúc đổi hẳn"
    )
    assertTrue(follow.progressed)
    assertTrue(finish.escaped)
  }

  private fun definition(): LevelDefinition {
    val zones = mapOf(
      "entry" to ZoneState("entry", "Entry", emptySet(), setOf("entry", "escape"))
    )
    val evidence = mapOf(
      "search-f" to EvidenceState("search-f", setOf("F"), setOf(EvidenceSource.SEARCH), "entry"),
      "search-g" to EvidenceState("search-g", setOf("G"), setOf(EvidenceSource.SEARCH), "entry"),
      "environment-f" to EvidenceState("environment-f", setOf("F"), setOf(EvidenceSource.ENVIRONMENT), "entry"),
      "anomaly-f" to EvidenceState("anomaly-f", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry"),
      "survivor-f" to EvidenceState("survivor-f", setOf("F"), setOf(EvidenceSource.SURVIVOR), "entry")
    )
    val action = LevelActionRule(
      id = "exit",
      matchGroups = listOf(setOf("exit")),
      conditions = setOf("zone:entry"),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    return LevelDefinition(
      id = "director.test",
      name = "Director Test",
      initialZoneId = "entry",
      zones = zones,
      escapeBlueprint = EscapeBlueprintState("director-test", setOf("F"), listOf("exit")),
      evidence = evidence,
      actions = mapOf("exit" to action),
      canonProfile = LevelCanonProfile(requiredZoneTags = setOf("entry", "escape")),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 1,
        maxZones = 4,
        minEvidencePerRequiredFact = 2,
        minEvidenceSourceTypesPerRequiredFact = 2,
        maxRequiredActions = 4
      )
    )
  }

  private fun instance(definition: LevelDefinition, visitCount: Int): LevelInstanceState =
    GenericLevelGenerator.generate(definition, "director-seed").copy(
      environment = mapOf("visits:entry" to visitCount.toString())
    )

  private fun loadLevelZero(): LevelDefinition {
    val candidates = listOf(
      java.io.File("src/main/assets/levels/0.json"),
      java.io.File("app/src/main/assets/levels/0.json"),
      java.io.File("android-apk/app/src/main/assets/levels/0.json")
    )
    val file = candidates.firstOrNull(java.io.File::isFile)
      ?: error("Cannot locate packaged levels/0.json")
    return LevelDefinitionJson.decode(file.readText(Charsets.UTF_8))
  }
}
