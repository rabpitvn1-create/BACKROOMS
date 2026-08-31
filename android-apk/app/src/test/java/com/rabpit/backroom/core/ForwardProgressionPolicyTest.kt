package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ForwardProgressionPolicyTest {
  private val catalog = LevelCatalogLoader.load(listOf(
    LevelCatalogDocument(
      "level_catalog/test.json",
      """{
        "schemaVersion":1,
        "campaignId":"campaign-a",
        "entries":[
          {"id":"0","name":"Zero","kind":"MAIN","parentMainLevel":0,"campaignOrder":0},
          {"id":"epsilon","name":"Epsilon","kind":"SPECIAL","parentId":"0","parentMainLevel":0,"campaignOrder":1000},
          {"id":"1","name":"One","kind":"MAIN","parentMainLevel":1,"campaignOrder":16000},
          {"id":"1.01","name":"One Sublevel","kind":"SUBLEVEL","parentId":"1","parentMainLevel":1,"campaignOrder":17000},
          {"id":"Red Rooms","name":"Red Rooms","kind":"SPECIAL","parentId":"1","parentMainLevel":1,"campaignOrder":18000}
        ]
      }""".trimIndent()
    ),
    LevelCatalogDocument(
      "level_catalog/other.json",
      """{"id":"999.alpha","name":"Other","kind":"SPECIAL","campaignId":"campaign-b","campaignOrder":1000}"""
    ),
    LevelCatalogDocument(
      "level_catalog/unordered.json",
      """{"id":"742.13","name":"Unordered","kind":"SPECIAL"}"""
    )
  ))

  @Test fun bootstrapAcceptsCataloguedOpaqueStringId() {
    val decision = ForwardProgressionPolicy.evaluate(catalog, null, currentCompleted = false, requestedLevelId = "Red Rooms")
    assertTrue(decision.allowed)
  }

  @Test fun currentLevelCanContinueBeforeCompletion() {
    val decision = ForwardProgressionPolicy.evaluate(catalog, "1", currentCompleted = false, requestedLevelId = "1")
    assertTrue(decision.allowed)
  }

  @Test fun unfinishedLevelCannotBeLeftForLaterLevel() {
    val decision = ForwardProgressionPolicy.evaluate(catalog, "0", currentCompleted = false, requestedLevelId = "1")
    assertFalse(decision.allowed)
    assertEquals("progression_current_level_incomplete:0", decision.reason)
  }

  @Test fun completedLevelMayAdvanceToHigherCampaignOrderWithoutParsingIds() {
    val mainAdvance = ForwardProgressionPolicy.evaluate(catalog, "0", currentCompleted = true, requestedLevelId = "1")
    val specialAdvance = ForwardProgressionPolicy.evaluate(catalog, "1.01", currentCompleted = true, requestedLevelId = "Red Rooms")

    assertTrue(mainAdvance.allowed)
    assertTrue(specialAdvance.allowed)
  }

  @Test fun completedLevelCannotMoveBackward() {
    val decision = ForwardProgressionPolicy.evaluate(catalog, "1", currentCompleted = true, requestedLevelId = "0")
    assertFalse(decision.allowed)
    assertEquals("progression_not_forward:1:0", decision.reason)
  }

  @Test fun unorderedOrCrossCampaignTargetFailsClosedDuringTransition() {
    val unordered = ForwardProgressionPolicy.evaluate(catalog, "1", currentCompleted = true, requestedLevelId = "742.13")
    val crossCampaign = ForwardProgressionPolicy.evaluate(catalog, "1", currentCompleted = true, requestedLevelId = "999.alpha")

    assertFalse(unordered.allowed)
    assertEquals("progression_target_campaign_missing:742.13", unordered.reason)
    assertFalse(crossCampaign.allowed)
    assertEquals("progression_cross_campaign_forbidden:1:999.alpha", crossCampaign.reason)
  }
}
