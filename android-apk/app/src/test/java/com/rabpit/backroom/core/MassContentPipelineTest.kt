package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class MassContentPipelineTest {
  @Test fun thousandMainLevelsAndSublevelsCompileRegisterAndLookupWithoutNumericIds() {
    val mainCount = 1000
    val sublevelCount = 200
    val entries = mutableListOf<LevelCatalogEntry>()
    val documents = mutableListOf<ProceduralLevelProfileDocument>()

    repeat(mainCount) { index ->
      val id = "main-${index.toString().padStart(4, '0')}"
      val transitions = buildList {
        if (index + 1 < mainCount) add(LevelTransition("main-${(index + 1).toString().padStart(4, '0')}"))
        if (index % 100 == 0 && index + 2 < mainCount) add(LevelTransition("main-${(index + 2).toString().padStart(4, '0')}"))
      }
      entries += LevelCatalogEntry(
        id = id,
        name = "Synthetic $id",
        kind = LevelKind.MAIN,
        campaignId = "scale",
        campaignOrder = index * 10L,
        outgoingTransitions = transitions
      )
      documents += ProceduralLevelProfileDocument("level_profiles/$id.json", fullProfileRaw(id))
    }

    repeat(sublevelCount) { index ->
      val parent = "main-${index.toString().padStart(4, '0')}"
      val id = "$parent.sub.alpha"
      entries += LevelCatalogEntry(
        id = id,
        parentId = parent,
        name = "Synthetic child $id",
        kind = LevelKind.SUBLEVEL,
        campaignId = "scale",
        campaignOrder = mainCount * 10L + index
      )
      documents += ProceduralLevelProfileDocument(
        "level_profiles/sub/$index.json",
        inheritedProfileRaw(id, parent, allowEntities = index % 2 == 0)
      )
    }

    val catalog = LevelCatalog.from(entries)
    val definitions = ProceduralLevelProfileLoader.load(documents, catalog)
    val registry = LevelRegistry.from(definitions)

    assertEquals(1200, catalog.size)
    assertEquals(1200, registry.size)
    assertTrue(registry.contains("main-0000"))
    assertTrue(registry.contains("main-0199.sub.alpha"))
    assertEquals("main-0199.sub.alpha", registry.require("main-0199.sub.alpha").id)
    assertEquals("main-0199", registry.require("main-0199.sub.alpha").parentId)
    assertFalse(registry.require("main-0199.sub.alpha").generationConstraints.allowEntities)
    assertTrue(catalog.canTransition("main-0000", "main-0001"))
    assertTrue(catalog.canTransition("main-0000", "main-0002"))
    assertEquals(catalog.ids(), LevelCatalog.from(entries.reversed()).ids())
  }

  @Test fun fifteenHundredLevelInheritanceChainResolvesWithoutJvmRecursion() {
    val count = 1500
    val entries = (0 until count).map { index ->
      val id = "chain-$index"
      LevelCatalogEntry(
        id = id,
        parentId = if (index == 0) null else "chain-${index - 1}",
        name = id,
        kind = if (index == 0) LevelKind.MAIN else LevelKind.SUBLEVEL,
        campaignId = "deep-chain",
        campaignOrder = index.toLong()
      )
    }
    val catalog = LevelCatalog.from(entries)
    val profiles = (0 until count).map { index ->
      if (index == 0) {
        ProceduralLevelProfileJson.decode(fullProfileRaw("chain-0"))
      } else {
        ProceduralLevelProfileJson.decode(inheritedProfileRaw("chain-$index", "chain-${index - 1}", allowEntities = index % 2 == 0))
      }
    }

    val resolved = ProceduralLevelProfileResolver.resolveAll(profiles, catalog)
    val last = resolved.last()

    assertEquals(count, resolved.size)
    assertEquals("chain-1499", last.id)
    assertNull(last.inheritsFrom)
    assertFalse(last.generationConstraints.allowEntities)
    assertTrue("backrooms_confirmed_conscious" in last.canonProfile.forbiddenClaims)
  }

  @Test fun level742Point13OnboardsFromCatalogTransitionAndInheritedProfileOnly() {
    val catalog = LevelCatalog.from(listOf(
      LevelCatalogEntry(
        id = "742",
        name = "Synthetic Parent",
        kind = LevelKind.MAIN,
        campaignId = "fixture",
        campaignOrder = 1000,
        outgoingTransitions = listOf(LevelTransition("742.13"))
      ),
      LevelCatalogEntry(
        id = "742.13",
        parentId = "742",
        name = "Synthetic Child",
        kind = LevelKind.SUBLEVEL,
        campaignId = "fixture",
        campaignOrder = 2000,
        outgoingTransitions = listOf(LevelTransition("999.alpha"))
      ),
      LevelCatalogEntry(
        id = "999.alpha",
        name = "Synthetic Target",
        kind = LevelKind.SPECIAL,
        campaignId = "fixture",
        campaignOrder = 3000
      )
    ))
    val definitions = ProceduralLevelProfileLoader.load(
      listOf(
        ProceduralLevelProfileDocument("level_profiles/742.json", fullProfileRaw("742")),
        ProceduralLevelProfileDocument(
          "level_profiles/742.13.json",
          """{
            "schemaVersion":2,
            "id":"742.13",
            "inheritsFrom":"742",
            "canonPatch":{
              "environmentTagsAdd":["child_only"],
              "forbiddenClaimsAdd":["child_forbidden"]
            },
            "generationConstraintsPatch":{"allowEntities":false},
            "metadata":{"fixture":"data-only-onboarding"}
          }""".trimIndent()
        ),
        ProceduralLevelProfileDocument("level_profiles/999.alpha.json", fullProfileRaw("999.alpha"))
      ),
      catalog
    )
    val registry = LevelRegistry.from(definitions)
    val definition = registry.require("742.13")
    val request = LevelGenerationRequestFactory.build(definition, "fixture-seed").toString()

    assertEquals("742.13", definition.id)
    assertEquals("742", definition.parentId)
    assertTrue(catalog.canTransition("742", "742.13"))
    assertTrue(catalog.canTransition("742.13", "999.alpha"))
    assertFalse(definition.generationConstraints.allowEntities)
    assertTrue(request.contains("\"levelId\":\"742.13\""))
    assertTrue(request.contains("\"allowEntities\":false"))
    assertFalse(request.contains("escapeBlueprint"))
    assertFalse(request.contains("requiredActions"))
    assertFalse(request.contains("PROFILE_EXIT_PATTERN_CONFIRMED"))
    assertFalse(request.contains("follow_profile_transition"))
    assertFalse(request.contains("profile-fallback:"))
  }

  private fun inheritedProfileRaw(id: String, parent: String, allowEntities: Boolean): String = """{
    "schemaVersion":2,
    "id":"$id",
    "inheritsFrom":"$parent",
    "canonPatch":{"environmentTagsAdd":["child-$id"],"forbiddenClaimsAdd":["child_forbidden_$id"]},
    "generationConstraintsPatch":{"allowEntities":$allowEntities}
  }""".trimIndent()

  private fun fullProfileRaw(id: String): String = """{
    "schemaVersion":1,
    "id":"$id",
    "canonProfile":{
      "environmentTags":["synthetic_environment"],
      "requiredZoneTags":["entry","escape"],
      "allowedPhenomena":[],
      "forbiddenClaims":["backrooms_confirmed_conscious","level_confirmed_intentionally_opposes_player"],
      "transitionTags":["synthetic_transition"],
      "metadata":{}
    },
    "generationConstraints":{
      "minZones":2,
      "maxZones":4,
      "minEvidencePerRequiredFact":1,
      "minEvidenceSourceTypesPerRequiredFact":1,
      "maxRequiredActions":2,
      "allowSurvivors":false,
      "allowEntities":true,
      "proceduralTopology":true,
      "proceduralLandmarks":true,
      "proceduralEvidencePlacement":true,
      "proceduralEscapeBlueprint":true
    }
  }""".trimIndent()
}
