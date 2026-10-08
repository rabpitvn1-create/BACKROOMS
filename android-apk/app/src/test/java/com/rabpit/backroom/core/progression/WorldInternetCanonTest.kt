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
    assertEquals(CanonWebReview.PAGE_METADATA_ONLY,
      WorldInternetCanon.record("level-6.2")?.source?.review)
    assertEquals(CanonWebReview.PAGE_METADATA_ONLY,
      WorldInternetCanon.record("level-11.3")?.source?.review)
    assertEquals(CanonWebReview.PROJECT_ONLY,
      WorldInternetCanon.record("level-0.22")?.source?.review)
  }

  @Test fun remaining34WikidotSublevelsAndNamedAreasAreMetadataOnly() {
    val linked = WorldInternetCanon.sourceLinked()
    val reviewed = WorldInternetCanon.directlyReviewed()
    val metadata = WorldInternetCanon.RECORDS.filter {
      it.source.review == CanonWebReview.PAGE_METADATA_ONLY
    }
    assertEquals(67, WorldInternetCanon.RECORDS.size)
    assertEquals(56, linked.size)
    assertEquals(22, reviewed.size)
    assertEquals(34, metadata.size)
    assertEquals(11, WorldInternetCanon.RECORDS.count { it.source.wikiPageUrl == null })
    metadata.forEach {
      assertNotNull(it.source.wikiPageUrl)
      assertEquals("CC BY-SA 3.0", it.source.wikiTextLicense)
      assertNotNull(it.source.creditedAuthors)
      assertEquals("2026-10-08", it.source.observedDate)
      assertNull(it.source.wikiTitle)
      assertTrue(it.source.environmentSignals.isEmpty())
      assertTrue(it.source.riskReports.isEmpty())
      assertTrue(it.source.exitReports.isEmpty())
    }
  }

  @Test fun wikiNumberedSlugNeverReclassifiesUnrankedProjectNamedSections() {
    val asset = WorldInternetCanon.record("area:11:asset-11-1")!!
    val scene = WorldInternetCanon.record("area:11:scene-01-2")!!
    assertNull(asset.worldNodeId)
    assertNull(scene.worldNodeId)
    assertEquals("https://backrooms-wiki.wikidot.com/level-11-1",
      asset.source.wikiPageUrl)
    assertEquals("https://backrooms-wiki.wikidot.com/level-11-2",
      scene.source.wikiPageUrl)
    assertEquals("https://backrooms-wiki.wikidot.com/the-sanctum",
      WorldInternetCanon.record("area:8:the-sanctum-subterraneous")!!.source.wikiPageUrl)
    assertEquals("ForestIsWatching",
      WorldInternetCanon.record("level-9.3")!!.source.creditedAuthors)
    assertEquals("Natedagreat563",
      WorldInternetCanon.record("level-5.1")!!.source.creditedAuthors)
    assertEquals(42, WorldProgressionCore.EDGES.size)
  }

  @Test fun wikiOutdatedMetadataDoesNotPromoteOpenSublevelToCanon() {
    val truncated = WorldInternetCanon.record("level-6.2")!!
    assertEquals(WorldContentAuthority.OPEN, truncated.projectAuthority)
    assertEquals(CanonWebReview.PAGE_METADATA_ONLY, truncated.source.review)
    assertTrue(truncated.source.conflicts.any { it.contains("trimmed") })
    val retired = WorldInternetCanon.record("level-1.1")!!
    assertEquals(WorldContentAuthority.OPEN, retired.projectAuthority)
    assertEquals(CanonWebReview.PAGE_METADATA_ONLY, retired.source.review)
    assertTrue(retired.source.conflicts.any { it.contains("outdated") })
    assertNull(WorldInternetCanon.record("area:0:ls-2")!!.source.wikiPageUrl)
    assertEquals(CanonWebReview.OPEN_PENDING,
      WorldInternetCanon.record("area:0:ls-2")!!.source.review)
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
