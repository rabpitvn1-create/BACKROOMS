package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class WorldInternetCanonTest {

  @Test fun all67JourneyStopsHavePreciselyOneGameCanonicalRecord() {
    val stops = WorldJourneyOrder.STOPS
    val records = WorldInternetCanon.RECORDS
    assertEquals(67, records.size)
    assertEquals(stops.map { it.key }, records.map { it.stopKey })
    assertEquals(67, records.map { it.stopKey }.toSet().size)
    records.forEachIndexed { index, record ->
      assertEquals(stops[index].parentLevel, record.parentLevel)
      assertEquals(stops[index].title, record.projectTitle)
      assertEquals(stops[index].authority, record.projectAuthority)
      assertEquals(stops[index].worldNodeId, record.worldNodeId)
      assertEquals(record, WorldInternetCanon.record(record.stopKey))
    }
    assertNull(WorldInternetCanon.record("unknown"))
    assertNull(WorldInternetCanon.record("level-14"))
  }

  @Test fun twentyTwoDirectWebPagesHaveAttributionAndLinksWithoutInventedSourcePages() {
    val reviewed = WorldInternetCanon.directlyReviewed()
    assertEquals(22, reviewed.size)
    val levelZeroPages = setOf(
      "level-0", "level-0.1", "level-0.2", "level-0.3",
      "level-0.5", "level-0.7", "area:0:manila-room",
      "area:0:red-rooms", "area:0:the-torment",
    )
    val otherFullLevels = (1..13).map { "level-$it" }.toSet()
    assertEquals(levelZeroPages + otherFullLevels, reviewed.map { it.stopKey }.toSet())
    reviewed.forEach {
      assertTrue(it.source.wikiPageUrl!!.startsWith("https://backrooms-wiki.wikidot.com/"))
      assertNotNull(it.source.creditedAuthors)
      assertEquals("CC BY-SA 3.0", it.source.wikiTextLicense)
      assertEquals("2026-10-08", it.source.observedDate)
    }
    assertEquals("https://backrooms-wiki.wikidot.com/licensing-guide", WorldInternetCanon.LICENSE_GUIDE)
  }

  @Test fun projectOverridesPreserveNamesAndExcludeConflictingWikiEnvironment() {
    val zeroOne = WorldInternetCanon.record("level-0.1")!!
    assertEquals("Deep Emptiness", zeroOne.projectTitle)
    assertEquals("Level 0.1 - Zenith Station", zeroOne.source.wikiTitle)
    assertEquals(CanonWebReview.PROJECT_OVERRIDE, zeroOne.source.review)
    assertTrue(zeroOne.source.environmentSignals.isEmpty())
    assertTrue(zeroOne.source.conflicts.isNotEmpty())
    val zeroSeven = WorldInternetCanon.record("level-0.7")!!
    assertEquals("Claustrophobia", zeroSeven.projectTitle)
    assertEquals("Level 0.7 - The Reminiscence District", zeroSeven.source.wikiTitle)
    assertEquals(CanonWebReview.PROJECT_OVERRIDE, zeroSeven.source.review)
    assertTrue(zeroSeven.source.environmentSignals.isEmpty())
    assertEquals("Lights Out", WorldInternetCanon.record("level-6")?.projectTitle)
  }

  @Test fun allMainLevelsHaveSourceAndRewriteGatesWhileKeepingProjectCanon() {
    for (n in 0..13) {
      val record = WorldInternetCanon.record("level-$n")!!
      assertNotNull("Missing verified URL for level-$n", record.source.wikiPageUrl)
      assertNotNull(record.source.creditedAuthors)
      assertEquals(n, record.parentLevel)
    }
    assertEquals(CanonWebReview.SOURCE_TRIMMED,
      WorldInternetCanon.record("level-4")?.source?.review)
    assertEquals(CanonWebReview.PROJECT_OVERRIDE,
      WorldInternetCanon.record("level-6")?.source?.review)
    assertTrue(WorldInternetCanon.record("level-6")!!.source.environmentSignals.isEmpty())
    assertTrue(WorldContentCatalog.entry(WorldNodeId("level-6"))!!.environmentBaseline!!.contains("tundra"))
    assertEquals(CanonWebReview.SOURCE_TRIMMED,
      WorldInternetCanon.record("level-7")?.source?.review)
    assertEquals(CanonWebReview.PAGE_REVIEWED,
      WorldInternetCanon.record("level-11")?.source?.review)
    assertEquals(CanonWebReview.PAGE_REVIEWED,
      WorldInternetCanon.record("level-12")?.source?.review)
    assertEquals(CanonWebReview.PAGE_REVIEWED,
      WorldInternetCanon.record("level-13")?.source?.review)
    assertTrue(WorldInternetCanon.record("level-12")!!.source.environmentSignals
      .any { it.contains("hình ảnh") })
    assertEquals(42, WorldProgressionCore.EDGES.size)
  }

  @Test fun trimmedAndOpenArticlesCannotBePromotedIntoGameplayByIngestion() {
    val icy = WorldInternetCanon.record("level-0.3")!!
    assertEquals(CanonWebReview.SOURCE_TRIMMED, icy.source.review)
    assertTrue(icy.source.environmentSignals.isEmpty())
    assertTrue(icy.source.riskReports.isEmpty())
    val ls2 = WorldInternetCanon.record("area:0:ls-2")!!
    assertEquals(CanonWebReview.OPEN_PENDING, ls2.source.review)
    assertNull(ls2.source.wikiPageUrl)
    assertEquals(CanonWebReview.OPEN_PENDING,
      WorldInternetCanon.record("level-6.2")?.source?.review)
    assertEquals(CanonWebReview.INDEX_PENDING,
      WorldInternetCanon.record("level-11.3")?.source?.review)
    assertEquals(CanonWebReview.PROJECT_ONLY,
      WorldInternetCanon.record("level-0.22")?.source?.review)
  }

  @Test fun publishedExitReportsAreOnlyClaimsNotApprovedRoutes() {
    val red = WorldInternetCanon.record("area:0:red-rooms")!!
    assertEquals(CanonWebReview.PROJECT_OVERRIDE, red.source.review)
    assertTrue(red.source.conflicts.any { it.contains("Level 1") })
    assertEquals("level-1", WorldJourneyOrder.nextAfter(red.stopKey)?.key)
    assertNull(red.worldNodeId)
    assertEquals(42, WorldProgressionCore.EDGES.size)
    val route = WorldProgressionCore.validateTransition(
      WorldNodeId("level-0"), WorldNodeId("level-0.5"))
    assertTrue(route is TransitionResult.Rejected)
    val manila = WorldInternetCanon.record("area:0:manila-room")!!
    assertNull(manila.worldNodeId)
    assertTrue(manila.source.exitReports.any { it.contains("Level 1") })
  }

  @Test fun levelZeroBatchIncludesAllTwentyStopsButOnlyNineReviewedArticles() {
    val group = WorldInternetCanon.recordsForLevel(0)
    assertEquals(20, group.size)
    assertEquals(WorldJourneyOrder.groupStops(0).map { it.key }, group.map { it.stopKey })
    assertEquals(9, group.count { it.source.wikiPageUrl != null })
    assertEquals(11, group.count { it.source.wikiPageUrl == null })
    assertEquals(47, WorldInternetCanon.RECORDS.count { it.parentLevel != 0 })
    assertTrue(WorldInternetCanon.recordsForLevel(99).isEmpty())
  }
}
