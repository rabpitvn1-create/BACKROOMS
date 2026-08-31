package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ProceduralLevelProfileInheritanceTest {
  @Test fun sublevelInheritsParentCanonAndOverridesOnlyDeclaredDifferences() {
    val catalog = catalog(
      LevelCatalogEntry("742", name = "Main 742", kind = LevelKind.MAIN, campaignId = "future", campaignOrder = 1000),
      LevelCatalogEntry("742.13", parentId = "742", name = "Future Sublevel", kind = LevelKind.SUBLEVEL, campaignId = "future", campaignOrder = 2000)
    )
    val parent = fullProfileRaw("742")
    val child = """{
      "schemaVersion":2,
      "id":"742.13",
      "inheritsFrom":"742",
      "canonPatch":{
        "environmentTagsAdd":["child_environment"],
        "environmentTagsRemove":["parent_replaceable_environment"],
        "allowedPhenomenaAdd":["child_phenomenon"],
        "allowedPhenomenaRemove":["parent_phenomenon"],
        "forbiddenClaimsAdd":["child_forbidden_claim"],
        "transitionTagsAdd":["child_transition"],
        "transitionTagsRemove":["parent_transition"],
        "metadataSet":{"transitionTarget":"743","childRule":"different"},
        "metadataRemove":["replaceableRule"]
      },
      "generationConstraintsPatch":{
        "minZones":3,
        "maxZones":12,
        "allowSurvivors":false,
        "maxRequiredActions":5
      },
      "metadata":{"profileSource":"sublevel-delta"}
    }""".trimIndent()

    val definitions = ProceduralLevelProfileLoader.load(
      listOf(
        ProceduralLevelProfileDocument("level_profiles/742.json", parent),
        ProceduralLevelProfileDocument("level_profiles/742.13.json", child)
      ),
      catalog
    )
    val definition = definitions.single { it.id == "742.13" }

    assertEquals("742", definition.parentId)
    assertEquals("Future Sublevel", definition.name)
    assertTrue("parent_environment" in definition.canonProfile.environmentTags)
    assertTrue("child_environment" in definition.canonProfile.environmentTags)
    assertFalse("parent_replaceable_environment" in definition.canonProfile.environmentTags)
    assertFalse("parent_phenomenon" in definition.canonProfile.allowedPhenomena)
    assertTrue("child_phenomenon" in definition.canonProfile.allowedPhenomena)
    assertTrue("backrooms_confirmed_conscious" in definition.canonProfile.forbiddenClaims)
    assertTrue("child_forbidden_claim" in definition.canonProfile.forbiddenClaims)
    assertFalse("parent_transition" in definition.canonProfile.transitionTags)
    assertTrue("child_transition" in definition.canonProfile.transitionTags)
    assertEquals("743", definition.canonProfile.metadata["transitionTarget"])
    assertEquals("different", definition.canonProfile.metadata["childRule"])
    assertFalse(definition.canonProfile.metadata.containsKey("replaceableRule"))
    assertEquals(3, definition.generationConstraints.minZones)
    assertEquals(12, definition.generationConstraints.maxZones)
    assertFalse(definition.generationConstraints.allowSurvivors)
    assertEquals(5, definition.generationConstraints.maxRequiredActions)
    assertEquals("742", definition.metadata["profileInheritedFrom"])
    assertTrue(LevelDefinitionValidator.validate(definition).valid)

    val request = LevelGenerationRequestFactory.build(definition, "inheritance-seed").toString()
    assertTrue(request.contains("child_environment"))
    assertTrue(request.contains("child_transition"))
    assertFalse(request.contains("PROFILE_EXIT_PATTERN_CONFIRMED"))
    assertFalse(request.contains("follow_profile_transition"))
    assertFalse(request.contains("profile-fallback:"))
  }

  @Test fun sublevelCanInheritFromExplicitParentDefinition() {
    val catalog = catalog(
      LevelCatalogEntry("0", name = "Explicit Main", kind = LevelKind.MAIN, campaignId = "future", campaignOrder = 1000),
      LevelCatalogEntry("0.01", parentId = "0", name = "Explicit Child", kind = LevelKind.SUBLEVEL, campaignId = "future", campaignOrder = 2000)
    )
    val parentProfile = ProceduralLevelProfileJson.decode(fullProfileRaw("0"))
    val explicitParent = ProceduralLevelProfileCompiler.compile(parentProfile, catalog)
    val child = """{
      "schemaVersion":2,
      "id":"0.01",
      "inheritsFrom":"0",
      "canonPatch":{
        "environmentTagsAdd":["sublevel_only"],
        "transitionTagsAdd":["forward_only_child_transition"]
      },
      "generationConstraintsPatch":{"minZones":3}
    }""".trimIndent()

    val definitions = ProceduralLevelProfileLoader.load(
      listOf(ProceduralLevelProfileDocument("level_profiles/0.01.json", child)),
      catalog,
      mapOf("0" to explicitParent)
    )
    val definition = definitions.single()

    assertEquals("0.01", definition.id)
    assertEquals("0", definition.parentId)
    assertTrue("parent_environment" in definition.canonProfile.environmentTags)
    assertTrue("sublevel_only" in definition.canonProfile.environmentTags)
    assertTrue("backrooms_confirmed_conscious" in definition.canonProfile.forbiddenClaims)
    assertEquals(3, definition.generationConstraints.minZones)
    assertTrue(LevelDefinitionValidator.validate(definition).valid)
  }

  @Test fun inheritanceMustFollowCatalogParentAndHaveAResolvableSource() {
    val catalog = catalog(
      LevelCatalogEntry("main-a", name = "A", kind = LevelKind.MAIN),
      LevelCatalogEntry("main-b", name = "B", kind = LevelKind.MAIN),
      LevelCatalogEntry("child", parentId = "main-a", name = "Child", kind = LevelKind.SUBLEVEL)
    )
    val wrongParent = inheritedProfileRaw("child", "main-b")
    try {
      ProceduralLevelProfileLoader.load(
        listOf(ProceduralLevelProfileDocument("level_profiles/child.json", wrongParent)),
        catalog
      )
      fail("Expected wrong inheritance parent to fail closed")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("profile_inheritance_must_match_catalog_parent"))
    }

    val missingSource = inheritedProfileRaw("child", "main-a")
    try {
      ProceduralLevelProfileLoader.load(
        listOf(ProceduralLevelProfileDocument("level_profiles/child.json", missingSource)),
        catalog
      )
      fail("Expected missing inheritance source to fail closed")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("procedural_profile_inheritance_source_missing"))
    }
  }

  @Test fun legacySchemaStillLoadsButInheritanceRequiresSchemaTwo() {
    val catalog = catalog(
      LevelCatalogEntry("main", name = "Main", kind = LevelKind.MAIN),
      LevelCatalogEntry("child", parentId = "main", name = "Child", kind = LevelKind.SUBLEVEL)
    )
    val legacy = ProceduralLevelProfileJson.decode(fullProfileRaw("main"))
    assertEquals(1, legacy.schemaVersion)
    assertTrue(ProceduralLevelProfileValidator.validate(legacy, catalog).valid)

    val invalidInherited = inheritedProfileRaw("child", "main").replace("\"schemaVersion\":2", "\"schemaVersion\":1")
    val decoded = ProceduralLevelProfileJson.decode(invalidInherited)
    val validation = ProceduralLevelProfileValidator.validate(decoded, catalog)
    assertFalse(validation.valid)
    assertTrue("profile_inheritance_requires_schema_2" in validation.errors)
  }

  @Test fun forbiddenClaimRemovalAndUnknownPatchFieldsFailClosed() {
    val forbiddenRemoval = """{
      "schemaVersion":2,
      "id":"child",
      "inheritsFrom":"main",
      "canonPatch":{"forbiddenClaimsRemove":["backrooms_confirmed_conscious"]}
    }""".trimIndent()
    try {
      ProceduralLevelProfileJson.decode(forbiddenRemoval)
      fail("Expected forbidden-claim removal to fail closed")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("unknown_canon_patch_field:forbiddenClaimsRemove"))
    }

    val typo = """{
      "schemaVersion":2,
      "id":"child",
      "inheritsFrom":"main",
      "generationConstraintsPatch":{"minimumZones":3}
    }""".trimIndent()
    try {
      ProceduralLevelProfileJson.decode(typo)
      fail("Expected unknown constraint patch field to fail closed")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("unknown_generation_constraints_patch_field:minimumZones"))
    }
  }

  private fun catalog(vararg entries: LevelCatalogEntry): LevelCatalog = LevelCatalog.from(entries.toList())

  private fun inheritedProfileRaw(id: String, parent: String): String = """{
    "schemaVersion":2,
    "id":"$id",
    "inheritsFrom":"$parent",
    "canonPatch":{"environmentTagsAdd":["child"]}
  }""".trimIndent()

  private fun fullProfileRaw(id: String): String = """{
    "schemaVersion":1,
    "id":"$id",
    "canonProfile":{
      "environmentTags":["parent_environment","parent_replaceable_environment"],
      "requiredZoneTags":["entry","escape"],
      "allowedPhenomena":["parent_phenomenon"],
      "forbiddenClaims":["backrooms_confirmed_conscious","level_confirmed_intentionally_opposes_player"],
      "transitionTags":["parent_transition"],
      "metadata":{"transitionTarget":"next","replaceableRule":"parent"}
    },
    "generationConstraints":{
      "minZones":4,
      "maxZones":8,
      "minEvidencePerRequiredFact":2,
      "minEvidenceSourceTypesPerRequiredFact":2,
      "maxRequiredActions":4,
      "allowSurvivors":true,
      "allowEntities":true,
      "proceduralTopology":true,
      "proceduralLandmarks":true,
      "proceduralEvidencePlacement":true,
      "proceduralEscapeBlueprint":true
    }
  }""".trimIndent()
}
