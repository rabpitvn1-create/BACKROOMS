package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LevelCatalogTest {
  @Test fun bundledCatalogPreservesExactIdsAndCampaignOrder() {
    val document = LevelCatalogDocument(
      "level_catalog/test.json",
      """{
        "schemaVersion":1,
        "campaignId":"campaign-a",
        "entries":[
          {"id":"1.10","name":"First","kind":"SUBLEVEL","parentId":"1","parentMainLevel":1,"campaignOrder":2000},
          {"id":"1.1","name":"Second","kind":"SUBLEVEL","parentId":"1","parentMainLevel":1,"campaignOrder":3000},
          {"id":"1","name":"Main","kind":"MAIN","parentMainLevel":1,"campaignOrder":1000}
        ]
      }""".trimIndent()
    )

    val catalog = LevelCatalogLoader.load(listOf(document))

    assertTrue(catalog.contains("1.10"))
    assertTrue(catalog.contains("1.1"))
    assertNotEquals(catalog.require("1.10").id, catalog.require("1.1").id)
    assertEquals(listOf("1", "1.10", "1.1"), catalog.campaign("campaign-a").map { it.id })
    assertTrue(catalog.unresolvedParents().isEmpty())
  }

  @Test fun arbitraryFutureLevelRegistersWithoutRuntimeCodeChanges() {
    val catalog = LevelCatalogLoader.load(listOf(
      LevelCatalogDocument(
        "level_catalog/future/742.13.json",
        """{"id":"742.13","name":"Future Sublevel","kind":"SUBLEVEL","parentId":"742","parentMainLevel":742}"""
      )
    ))

    assertTrue(catalog.contains("742.13"))
    assertEquals("742", catalog.require("742.13").parentId)
    assertEquals(setOf("742"), catalog.unresolvedParents())
    assertTrue(catalog.campaign("campaign-a").isEmpty())
  }

  @Test fun separateDocumentsComposeIntoOneCampaignAutomatically() {
    val catalog = LevelCatalogLoader.load(listOf(
      LevelCatalogDocument("level_catalog/a.json", """{"id":"999","name":"Main 999","kind":"MAIN","parentMainLevel":999,"campaignId":"future","campaignOrder":1000}"""),
      LevelCatalogDocument("level_catalog/b.json", """{"id":"999.alpha","name":"Alpha","kind":"SUBLEVEL","parentId":"999","parentMainLevel":999,"campaignId":"future","campaignOrder":2000}""")
    ))

    assertEquals(listOf("999", "999.alpha"), catalog.campaign("future").map { it.id })
    assertEquals(listOf("999.alpha"), catalog.childrenOf("999").map { it.id })
  }

  @Test fun duplicateCampaignOrderFailsClosed() {
    try {
      LevelCatalogLoader.load(listOf(
        LevelCatalogDocument(
          "level_catalog/bad.json",
          """{
            "campaignId":"same",
            "entries":[
              {"id":"7","name":"Seven","kind":"MAIN","parentMainLevel":7,"campaignOrder":1000},
              {"id":"7.1","name":"Seven One","kind":"SUBLEVEL","parentId":"7","parentMainLevel":7,"campaignOrder":1000}
            ]
          }""".trimIndent()
        )
      ))
      fail("Expected duplicate campaign order to be rejected")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty().contains("duplicate_campaign_order"))
    }
  }

  @Test fun invalidIdsAndSelfParentsFailClosed() {
    val invalid = listOf(
      LevelCatalogEntry("bad/id", name = "Bad", kind = LevelKind.MAIN),
      LevelCatalogEntry("8", parentId = "8", name = "Self", kind = LevelKind.SUBLEVEL)
    )

    invalid.forEach { entry ->
      assertFalse(LevelCatalogValidator.validate(entry).valid)
    }
  }
}
