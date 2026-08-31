package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LevelMainProfilesBatchTest {
  @Test fun packagedLevelThreeThroughSixProfilesCompileFromCatalog() {
    val catalogFile = locate("level_catalog/backrooms-0-6.json")
    val catalog = LevelCatalogLoader.load(listOf(
      LevelCatalogDocument(catalogFile.path, catalogFile.readText(Charsets.UTF_8))
    ))

    val expectedNames = mapOf(
      "3" to "Electrical Station",
      "4" to "The Abandoned Office",
      "5" to "Terror Hotel",
      "6" to "Lights Out"
    )

    expectedNames.forEach { (id, name) ->
      val profile = ProceduralLevelProfileJson.decode(locate("level_profiles/$id.json").readText(Charsets.UTF_8))
      val definition = ProceduralLevelProfileCompiler.compile(profile, catalog)
      val validation = LevelDefinitionValidator.validate(definition)

      assertTrue("$id: ${validation.errors.joinToString("\n")}", validation.valid)
      assertEquals(id, definition.id)
      assertEquals(name, definition.name)
      assertEquals("procedural-profile", definition.metadata["definitionSource"])
      assertTrue(definition.generationConstraints.proceduralTopology)
      assertTrue(definition.generationConstraints.proceduralEvidencePlacement)
      assertTrue(definition.generationConstraints.proceduralEscapeBlueprint)
      assertTrue("backrooms_confirmed_conscious" in definition.canonProfile.forbiddenClaims)

      val request = LevelGenerationRequestFactory.build(definition, "profile-$id-test").toString()
      assertFalse(request.contains("PROFILE_EXIT_PATTERN_CONFIRMED"))
      assertFalse(request.contains("follow_profile_transition"))
      assertFalse(request.contains("profile-fallback:"))
    }
  }

  @Test fun levelThreeProfileKeepsElectricalUncertaintyAndForwardTransitions() {
    val profile = loadProfile("3")
    assertTrue("industrial_corridor_network" in profile.canonProfile.environmentTags)
    assertTrue("local_blackout" in profile.canonProfile.allowedPhenomena)
    assertTrue("subzero_utility_tunnels" in profile.canonProfile.allowedPhenomena)
    assertTrue("level3_confirmed_power_source" in profile.canonProfile.forbiddenClaims)
    assertTrue("level3_confirmed_powers_other_levels" in profile.canonProfile.forbiddenClaims)
    assertEquals("4", profile.canonProfile.metadata["primaryTransitionTarget"])
    assertEquals("6", profile.canonProfile.metadata["alternateForwardTransitionTarget"])
  }

  @Test fun levelFourProfileIsRelativeOasisNotAbsoluteSafety() {
    val profile = loadProfile("4")
    assertTrue("relatively_stable_geometry" in profile.canonProfile.environmentTags)
    assertTrue("boundary_incursion" in profile.canonProfile.allowedPhenomena)
    assertTrue("level4_confirmed_absolutely_safe" in profile.canonProfile.forbiddenClaims)
    assertTrue("level4_confirmed_native_hostile_population" in profile.canonProfile.forbiddenClaims)
    assertEquals("none_confirmed", profile.canonProfile.metadata["residentEntities"])
    assertEquals("small_survivor_groups_only", profile.canonProfile.metadata["civilizationScale"])
  }

  @Test fun levelFiveProfileDoesNotCollapseMentalEffectsIntoSanityMeter() {
    val profile = loadProfile("5")
    assertTrue("strong_non_euclidean_geometry" in profile.canonProfile.environmentTags)
    assertTrue("paranoia" in profile.canonProfile.allowedPhenomena)
    assertTrue("whispering" in profile.canonProfile.allowedPhenomena)
    assertTrue("sanity_meter_is_canon" in profile.canonProfile.forbiddenClaims)
    assertTrue("all_mental_effects_confirmed_level_caused" in profile.canonProfile.forbiddenClaims)
    assertEquals("open_multifactor", profile.canonProfile.metadata["mentalEffectCause"])
  }

  @Test fun levelSixProfileUsesDarkTundraAndLeavesObelisksOpen() {
    val profile = loadProfile("6")
    assertTrue("dark_open_tundra" in profile.canonProfile.environmentTags)
    assertTrue("near_absolute_darkness" in profile.canonProfile.environmentTags)
    assertTrue("level6_confirmed_indoor_corridor_maze" in profile.canonProfile.forbiddenClaims)
    assertTrue("obelisk_confirmed_gateway" in profile.canonProfile.forbiddenClaims)
    assertTrue("resident_entity_presence_confirmed" in profile.canonProfile.forbiddenClaims)
    assertEquals("OPEN", profile.canonProfile.metadata["obeliskFunction"])
    assertEquals("no_method_guaranteed", profile.canonProfile.metadata["transitionRule"])
  }

  private fun loadProfile(id: String): ProceduralLevelProfile =
    ProceduralLevelProfileJson.decode(locate("level_profiles/$id.json").readText(Charsets.UTF_8))

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
