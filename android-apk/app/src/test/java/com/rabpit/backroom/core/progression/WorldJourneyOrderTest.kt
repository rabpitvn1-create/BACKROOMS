package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class WorldJourneyOrderTest {

  @Test fun exactUserApprovedLevelAndSectionOrderIsPinned() {
    val expected = listOf(
      "level-0",
      "level-0.01",
      "level-0.1",
      "level-0.11",
      "level-0.2",
      "level-0.22",
      "level-0.23",
      "level-0.3",
      "level-0.41",
      "level-0.5",
      "level-0.66",
      "level-0.7",
      "level-0.8",
      "level-0.99",
      "area:0:epsilon",
      "area:0:dullness",
      "area:0:ls-2",
      "area:0:manila-room",
      "area:0:the-torment",
      "area:0:red-rooms",
      "level-1",
      "level-1.1",
      "level-1.2",
      "level-1.3",
      "level-1.5",
      "area:1:base-alpha",
      "area:1:traders-vault",
      "level-2",
      "level-2.1",
      "area:2:office-space-el3a",
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
      "level-7.6",
      "level-7.7",
      "level-7.8",
      "area:7:the-hadal-zone",
      "level-8",
      "level-8.1",
      "area:8:the-sanctum-subterraneous",
      "level-9",
      "level-9.2",
      "level-9.3",
      "level-9.5",
      "level-10",
      "level-10.1",
      "level-10.2",
      "level-11",
      "level-11.3",
      "area:11:asset-11-1",
      "area:11:scene-01-2",
      "area:11:after-hours",
      "area:11:the-headquarters",
      "area:11:radio-backrooms-studio",
      "level-12",
      "level-13",
    )
    assertEquals(67, expected.size)
    assertEquals(expected, WorldJourneyOrder.STOPS.map { it.key })
  }

  @Test fun eachGroupKeepsMainLevelFirstNamedAreasLast() {
    assertEquals((0..13).toList(), WorldJourneyOrder.GROUPS.map { it.levelNumber })
    WorldJourneyOrder.GROUPS.forEach { group ->
      val stages = WorldJourneyOrder.groupStops(group.levelNumber)
      assertEquals("level-${group.levelNumber}", stages.first().key)
      assertEquals(WorldJourneyStopKind.LEVEL, stages.first().kind)
      assertEquals(group.numberedSublevelIds,
        stages.filter { it.kind == WorldJourneyStopKind.NUMBERED_SUB_LEVEL }.map { it.key })
      assertEquals(group.namedSectionKeys,
        stages.filter { it.kind == WorldJourneyStopKind.NAMED_SECTION }
          .map { it.key.substringAfterLast(':') })
    }
  }

  @Test fun redRoomsIsLastBeforeLevelOneAndNeverSkipped() {
    val zero = WorldJourneyOrder.groupStops(0)
    assertEquals("area:0:red-rooms", zero.last().key)
    assertEquals("level-1", WorldJourneyOrder.nextAfter(zero.last().key)?.key)
    assertEquals("area:0:red-rooms", WorldJourneyOrder.previousBefore("level-1")?.key)
    assertEquals("level-0.01", WorldJourneyOrder.nextAfter("level-0")?.key)
    assertEquals("level-0.2", WorldJourneyOrder.nextAfter("level-0.11")?.key)
    assertEquals("level-0.22", WorldJourneyOrder.nextAfter("level-0.2")?.key)
  }

  @Test fun allStopsHaveUniqueStableKeysAndSuccessors() {
    val stops = WorldJourneyOrder.STOPS
    assertEquals(67, stops.size)
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
    assertEquals(50, stops.count { it.worldNodeId != null })
    assertEquals(17, stops.count { it.worldNodeId == null })
    assertEquals(WorldProgressionCore.NODES.map { it.id }.toSet(),
      stops.mapNotNull { it.worldNodeId }.toSet())
    assertTrue(stops.filter { it.kind == WorldJourneyStopKind.NAMED_SECTION }
      .all { it.worldNodeId == null })
    assertEquals(WorldContentAuthority.PROJECT_CANON,
      WorldJourneyOrder.stop("area:0:red-rooms")?.authority)
    assertEquals(WorldContentAuthority.OPEN,
      WorldJourneyOrder.stop("level-0.3")?.authority)
  }

  @Test fun itineraryLookupCannotOpenGameplayExitOrChangeRank() {
    assertTrue(WorldProgressionCore.EDGES.none {
      it.from == WorldNodeId("level-0") && it.to == WorldNodeId("level-0.01")
    })
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-0"), WorldNodeId("level-0.01")) is TransitionResult.Rejected)
    assertEquals(0L,
      (WorldProgressionCore.rankOf(WorldNodeId("level-0")) as RankLookup.Known).progressionRank)
    assertEquals(100_000L,
      (WorldProgressionCore.rankOf(WorldNodeId("level-0.1")) as RankLookup.Known).progressionRank)
    assertEquals(42, WorldProgressionCore.EDGES.size)
  }
}
