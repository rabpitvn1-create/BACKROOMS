package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class FeaturedJourneyRoutesTest {
  private val expected = listOf(
    "level-0", "level-0.2", "area:0:red-rooms",
    "level-1", "area:1:base-alpha", "level-1.2", "level-1.5",
    "level-2", "level-3", "level-4", "level-5", "level-5.1",
    "level-6", "level-6.1", "level-7", "level-7.7", "level-8",
    "level-9", "level-10", "level-10.1", "level-11",
    "level-11.3", "level-12", "level-13",
  )
  @Test fun approvedTenStopsStayInTheirParentGroupsAndHaveLiveLinks() {
    assertEquals(expected, FeaturedJourneyRoutes.allStopKeys())
    assertEquals(24, expected.size)
    val main = (0..13).map { "level-$it" }.toSet()
    assertEquals(10, expected.count { it !in main })
    assertEquals("level-0.2", FeaturedJourneyRoutes.next("level-0")?.targetStopKey)
    assertEquals("area:0:red-rooms", FeaturedJourneyRoutes.next("level-0.2")?.targetStopKey)
    assertEquals("level-1", FeaturedJourneyRoutes.next("area:0:red-rooms")?.targetStopKey)
    assertEquals("area:1:base-alpha", FeaturedJourneyRoutes.next("level-1")?.targetStopKey)
    for (i in 0 until expected.lastIndex) {
      val route = FeaturedJourneyRoutes.next(expected[i])
      assertNotNull("Missing playable route from ${expected[i]}", route)
      assertEquals(expected[i + 1], route!!.targetStopKey)
      assertEquals(FeaturedJourneyRoutes.nodeIdAt(expected[i]), route.sourceNodeId)
      assertEquals(FeaturedJourneyRoutes.nodeIdAt(expected[i + 1]), route.targetNodeId)
    }
    assertNull(FeaturedJourneyRoutes.next("level-13"))
  }

  @Test fun namedAreasHaveDistinctStopKeysButNoSyntheticNodeOrRank() {
    val red = FeaturedJourneyRoutes.next("level-0.2")!!
    assertTrue(red.targetIsNamedArea)
    assertEquals("level-0.2", red.sourceNodeId)
    assertEquals("level-0.2", red.targetNodeId)
    assertEquals(200_000L, red.targetProgressionRank)
    assertEquals(0, red.targetLevelNumber)
    val alpha = FeaturedJourneyRoutes.next("level-1")!!
    assertTrue(alpha.targetIsNamedArea)
    assertEquals("level-1", alpha.targetNodeId)
    assertEquals(1_000_000L, alpha.targetProgressionRank)
  }

  @Test fun unselectedAndUnknownStopsHaveNoGameplayRoute() {
    for (key in listOf("level-0.1", "level-5.2", "area:7:the-hadal-zone", "level-99", "")) {
      assertFalse(FeaturedJourneyRoutes.contains(key))
      assertNull(FeaturedJourneyRoutes.next(key))
      assertNull(FeaturedJourneyRoutes.nodeIdAt(key))
    }
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-0"), WorldNodeId("level-0.1")) is TransitionResult.Rejected)
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-7"), WorldNodeId("level-7.6")) is TransitionResult.Rejected)
  }

  @Test fun progressionRankNeverExceedsRegisteredNodeRank() {
    for (stop in expected) {
      val id = FeaturedJourneyRoutes.nodeIdAt(stop)!!
      val known = WorldProgressionCore.rankOf(WorldNodeId(id)) as RankLookup.Known
      assertEquals(known.progressionRank,
        WorldProgressionCore.rankOf(WorldNodeId(id)).let { (it as RankLookup.Known).progressionRank })
    }
  }
}
