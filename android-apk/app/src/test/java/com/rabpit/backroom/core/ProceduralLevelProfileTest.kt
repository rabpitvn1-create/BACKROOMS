package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ProceduralLevelProfileTest {
  @Test fun catalogIdentityCompilesOpaqueFutureIdIntoValidFallbackDefinition() {
    val catalog = LevelCatalog.from(listOf(
      LevelCatalogEntry("742", name = "Main 742", kind = LevelKind.MAIN, parentMainLevel = 742, campaignId = "future", campaignOrder = 1000),
      LevelCatalogEntry("742.13", parentId = "742", name = "Future Sublevel", kind = LevelKind.SUBLEVEL, parentMainLevel = 742, campaignId = "future", campaignOrder = 2000)
    ))
    val profile = profile("742.13")

    val definition = ProceduralLevelProfileCompiler.compile(profile, catalog)
    val validation = LevelDefinitionValidator.validate(definition)

    assertTrue(validation.errors.joinToString("\n"), validation.valid)
    assertEquals("742.13", definition.id)
    assertEquals("742", definition.parentId)
    assertEquals("Future Sublevel", definition.name)
    assertEquals("procedural-profile", definition.metadata["definitionSource"])
    assertTrue(definition.zones.values.any { "entry" in it.tags })
    assertTrue(definition.zones.values.any { "escape" in it.tags })
  }

  @Test fun generationRequestExposesCanonButNotCompiledFallbackPuzzle() {
    val catalog = LevelCatalog.from(listOf(
      LevelCatalogEntry("999.alpha", name = "Alpha", kind = LevelKind.SPECIAL, campaignId = "future", campaignOrder = 1000)
    ))
    val definition = ProceduralLevelProfileCompiler.compile(profile("999.alpha"), catalog)

    val request = LevelGenerationRequestFactory.build(definition, "seed-alpha").toString()

    assertTrue(request.contains("future_environment"))
    assertTrue(request.contains("future_transition"))
    assertFalse(request.contains("PROFILE_EXIT_PATTERN_CONFIRMED"))
    assertFalse(request.contains("follow_profile_transition"))
    assertFalse(request.contains("profile-fallback:"))
  }

  @Test fun compiledFallbackStillRequiresEvidenceThenExecute() {
    val catalog = LevelCatalog.from(listOf(
      LevelCatalogEntry("future", name = "Future", kind = LevelKind.MAIN, campaignId = "future", campaignOrder = 1000)
    ))
    val definition = ProceduralLevelProfileCompiler.compile(profile("future"), catalog)
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "future", "fallback-seed")

    val firstExplore = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "khám phá")
    state = firstExplore.state
    assertTrue(firstExplore.progressed)
    assertFalse(state.levelInstance!!.completed)
    assertFalse("PROFILE_EXIT_PATTERN_CONFIRMED" in state.levelInstance!!.discoveredFacts)

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "khám phá").state
    val search = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "tìm kiếm")
    state = search.state
    assertTrue(search.progressed)
    assertTrue("PROFILE_EXIT_PATTERN_CONFIRMED" in state.levelInstance!!.discoveredFacts)

    while (state.levelInstance!!.currentZoneId != "profile_transition") {
      val explore = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "khám phá")
      assertFalse(explore.escaped)
      state = explore.state
    }

    val finish = GenericLevelRuntime.apply(
      state,
      registry,
      ActionKind.EXECUTE,
      "đi tiếp theo dấu hiệu chuyển vùng"
    )
    assertTrue(finish.progressed)
    assertTrue(finish.escaped)
  }

  @Test fun unknownCatalogProfileAndImpossibleSourceDiversityFailClosed() {
    val catalog = LevelCatalog.from(listOf(
      LevelCatalogEntry("known", name = "Known", kind = LevelKind.MAIN)
    ))
    val unknown = profile("missing")
    assertFalse(ProceduralLevelProfileValidator.validate(unknown, catalog).valid)

    val impossible = profile("known").copy(
      generationConstraints = profile("known").generationConstraints.copy(
        allowSurvivors = false,
        minEvidenceSourceTypesPerRequiredFact = 4
      )
    )
    val validation = ProceduralLevelProfileValidator.validate(impossible, catalog)
    assertFalse(validation.valid)
    assertTrue("profile_generation_source_diversity_unreachable" in validation.errors)
  }

  @Test fun duplicateProfileDocumentsFailClosed() {
    val catalog = LevelCatalog.from(listOf(
      LevelCatalogEntry("known", name = "Known", kind = LevelKind.MAIN)
    ))
    val raw = """{
      "schemaVersion":1,
      "id":"known",
      "canonProfile":{"environmentTags":["future_environment"],"requiredZoneTags":["entry","escape"],"transitionTags":["future_transition"]},
      "generationConstraints":{"minZones":4,"maxZones":8,"minEvidencePerRequiredFact":2,"minEvidenceSourceTypesPerRequiredFact":2,"maxRequiredActions":4,"allowSurvivors":true,"allowEntities":true,"proceduralTopology":true,"proceduralLandmarks":true,"proceduralEvidencePlacement":true,"proceduralEscapeBlueprint":true}
    }""".trimIndent()

    try {
      ProceduralLevelProfileLoader.load(
        listOf(
          ProceduralLevelProfileDocument("level_profiles/a.json", raw),
          ProceduralLevelProfileDocument("level_profiles/b.json", raw)
        ),
        catalog
      )
      fail("Expected duplicate profile to fail closed")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("duplicate_procedural_level_profile:known"))
    }
  }

  @Test fun packagedLevelTwoProfileIsCatalogBackedAndCanonBounded() {
    val catalogFile = locate("level_catalog/backrooms-0-6.json")
    val profileFile = locate("level_profiles/2.json")
    val catalog = LevelCatalogLoader.load(listOf(LevelCatalogDocument(catalogFile.path, catalogFile.readText(Charsets.UTF_8))))
    val profile = ProceduralLevelProfileJson.decode(profileFile.readText(Charsets.UTF_8))
    val definition = ProceduralLevelProfileCompiler.compile(profile, catalog)

    assertEquals("2", definition.id)
    assertEquals("Pipe Dreams", definition.name)
    assertTrue("utility_tunnel_network" in definition.canonProfile.environmentTags)
    assertTrue("seismic_instability" in definition.canonProfile.environmentTags)
    assertTrue("earthquake" in definition.canonProfile.allowedPhenomena)
    assertTrue("high_voltage_hum" in definition.canonProfile.transitionTags)
    assertEquals("never_drink_directly", definition.canonProfile.metadata["pipeWaterRule"])
    assertEquals("3", definition.canonProfile.metadata["transitionTarget"])
    assertTrue(LevelDefinitionValidator.validate(definition).valid)
  }

  private fun profile(id: String) = ProceduralLevelProfile(
    id = id,
    canonProfile = LevelCanonProfile(
      environmentTags = setOf("future_environment"),
      requiredZoneTags = setOf("entry", "escape"),
      transitionTags = setOf("future_transition"),
      forbiddenClaims = setOf("backrooms_confirmed_conscious")
    ),
    generationConstraints = ProceduralGenerationConstraints(
      minZones = 4,
      maxZones = 8,
      minEvidencePerRequiredFact = 2,
      minEvidenceSourceTypesPerRequiredFact = 2,
      maxRequiredActions = 4,
      allowSurvivors = true,
      allowEntities = true,
      proceduralTopology = true,
      proceduralLandmarks = true,
      proceduralEvidencePlacement = true,
      proceduralEscapeBlueprint = true
    )
  )

  private fun locate(relative: String): File {
    val candidates = listOf(
      File("src/main/assets/$relative"),
      File("app/src/main/assets/$relative"),
      File("android-apk/app/src/main/assets/$relative")
    )
    return candidates.firstOrNull(File::isFile)
      ?: error("Cannot locate packaged $relative from ${File(".").absolutePath}")
  }
}
