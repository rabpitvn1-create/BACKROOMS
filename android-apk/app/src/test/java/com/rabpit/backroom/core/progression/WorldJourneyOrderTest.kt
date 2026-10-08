package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class WorldJourneyOrderTest {

  @Test fun exactParentInterleavedLevelAndSectionOrderIsPinned() {
    val expected = listOf(
      "level-0",
      "area:0:epsilon",
      "area:0:ls-2",
      "area:0:manila-room",
      "area:0:the-torment",
      "level-0.2",
      "area:0:dullness",
      "area:0:red-rooms",
      "level-1",
      "area:1:base-alpha",
      "area:1:traders-vault",
      "level-1.1",
      "level-1.2",
      "level-1.3",
      "level-1.5",
      "level-2",
      "area:2:office-space-el3a",
      "level-2.1",
      "level-3",
      "level-3.5",
      "level-4",
      "area:4:the-office-market",
      "level-5",
      "level-5.1",
      "level-5.2",
      "level-5.3",
      "level-6",
      "level-6.1",
      "level-6.2",
      "level-6.3",
      "level-6.31",
      "level-7",
      "area:7:the-hadal-zone",
      "level-7.6",
      "level-7.7",
      "level-7.8",
      "level-8",
      "area:8:the-sanctum-subterraneous",
      "level-8.1",
      "level-9",
      "level-9.2",
      "level-9.3",
      "level-9.5",
      "level-10",
      "level-10.1",
      "level-10.2",
      "level-11",
      "area:11:asset-11-1",
      "area:11:scene-01-2",
      "area:11:after-hours",
      "area:11:the-headquarters",
      "area:11:radio-backrooms-studio",
      "level-11.3",
      "level-12",
      "level-13",
    )
    assertEquals(55, expected.size)
    assertEquals(expected, WorldJourneyOrder.STOPS.map { it.key })
  }

  @Test fun eachGroupInterleavesItsOwnAreasAndSublevelsWithoutChangingTheirParents() {
    assertEquals((0..13).toList(), WorldJourneyOrder.GROUPS.map { it.levelNumber })
    WorldJourneyOrder.GROUPS.forEach { group ->
      val stages = WorldJourneyOrder.groupStops(group.levelNumber)
      assertEquals(listOf("level-${group.levelNumber}") + group.orderedChildKeys,
        stages.map { it.key })
      assertEquals(WorldJourneyStopKind.LEVEL, stages.first().kind)
      assertTrue(stages.all { it.parentLevel == group.levelNumber })
      assertEquals(group.numberedSublevelIds,
        stages.filter { it.kind == WorldJourneyStopKind.NUMBERED_SUB_LEVEL }.map { it.key })
      assertEquals(group.namedSectionKeys,
        stages.filter { it.kind == WorldJourneyStopKind.NAMED_SECTION }
          .map { it.key.substringAfterLast(':') })
    }
  }

  @Test fun namedAreasAreIntegratedNextToTheirParentInsteadOfAppendedByDefault() {
    assertEquals("area:0:epsilon", WorldJourneyOrder.nextAfter("level-0")?.key)
    assertEquals("area:1:base-alpha", WorldJourneyOrder.nextAfter("level-1")?.key)
    assertEquals("area:2:office-space-el3a", WorldJourneyOrder.nextAfter("level-2")?.key)
    assertEquals("area:4:the-office-market", WorldJourneyOrder.nextAfter("level-4")?.key)
    assertEquals("area:7:the-hadal-zone", WorldJourneyOrder.nextAfter("level-7")?.key)
    assertEquals("area:8:the-sanctum-subterraneous", WorldJourneyOrder.nextAfter("level-8")?.key)
    assertEquals("area:11:asset-11-1", WorldJourneyOrder.nextAfter("level-11")?.key)
    assertEquals("level-11.3", WorldJourneyOrder.nextAfter("area:11:radio-backrooms-studio")?.key)
    assertEquals("area:0:dullness", WorldJourneyOrder.nextAfter("level-0.2")?.key)
    assertEquals("area:0:red-rooms", WorldJourneyOrder.nextAfter("area:0:dullness")?.key)
  }

  @Test fun numberedSublevelsStillRiseInRankWithinEachParent() {
    WorldJourneyOrder.GROUPS.forEach { group ->
      val ranks = WorldJourneyOrder.groupStops(group.levelNumber)
        .mapNotNull { stop ->
          if (stop.kind != WorldJourneyStopKind.NUMBERED_SUB_LEVEL) null
          else (WorldProgressionCore.rankOf(stop.worldNodeId!!) as RankLookup.Known).progressionRank
        }
      assertTrue("Sub-level rank regression in Level ${group.levelNumber}",
        ranks.zipWithNext().all { (a, b) -> b > a })
    }
  }

  @Test fun redRoomsIsLastBeforeLevelOneAndNeverSkipped() {
    val zero = WorldJourneyOrder.groupStops(0)
    assertEquals("area:0:red-rooms", zero.last().key)
    assertEquals("level-1", WorldJourneyOrder.nextAfter(zero.last().key)?.key)
    assertEquals("area:0:red-rooms", WorldJourneyOrder.previousBefore("level-1")?.key)
    assertEquals("area:0:epsilon", WorldJourneyOrder.nextAfter("level-0")?.key)
    assertEquals("level-0.2", WorldJourneyOrder.nextAfter("area:0:the-torment")?.key)
    assertEquals("area:0:dullness", WorldJourneyOrder.nextAfter("level-0.2")?.key)
  }

  @Test fun allStopsHaveUniqueStableKeysAndSuccessors() {
    val stops = WorldJourneyOrder.STOPS
    assertEquals(55, stops.size)
    assertEquals(stops.size, stops.map { it.key }.toSet().size)
    for (i in stops.indices) {
      assertEquals(stops[i], WorldJourneyOrder.stop(stops[i].key))
      assertEquals(stops.getOrNull(i + 1), WorldJourneyOrder.nextAfter(stops[i].key))
      assertEquals(stops.getOrNull(i - 1), WorldJourneyOrder.previousBefore(stops[i].key))
    }
    assertNull(WorldJourneyOrder.previousBefore("level-0"))
    assertNull(WorldJourneyOrder.nextAfter("level-13"))
    assertNull(WorldJourneyOrder.nextAfter("unknown"))
    assertNull(WorldJourneyOrder.stop("area:0:made-up"))
  }

  @Test fun entireRegistryAndNamedAreaCatalogIsCoveredWithoutInventingRanks() {
    val stops = WorldJourneyOrder.STOPS
    assertEquals(38, stops.count { it.worldNodeId != null })
    assertEquals(17, stops.count { it.worldNodeId == null })
    assertEquals(WorldProgressionCore.NODES.map { it.id }.toSet(),
      stops.mapNotNull { it.worldNodeId }.toSet())
    assertTrue(stops.filter { it.kind == WorldJourneyStopKind.NAMED_SECTION }
      .all { it.worldNodeId == null })
    assertEquals(WorldContentAuthority.PROJECT_CANON,
      WorldJourneyOrder.stop("area:0:red-rooms")?.authority)
    assertNull(WorldJourneyOrder.stop("level-0.3"))
  }

  @Test fun itineraryLookupCannotOpenGameplayExitOrChangeRank() {
    assertTrue(WorldProgressionCore.EDGES.none {
      it.from == WorldNodeId("level-0") && it.to == WorldNodeId("level-0.01")
    })
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-0"), WorldNodeId("level-0.01")) is TransitionResult.Rejected)
    assertEquals(0L,
      (WorldProgressionCore.rankOf(WorldNodeId("level-0")) as RankLookup.Known).progressionRank)
    assertEquals(RankLookup.Unknown, WorldProgressionCore.rankOf(WorldNodeId("level-0.1")))
    assertEquals(64, WorldProgressionCore.EDGES.size)
  }
}
