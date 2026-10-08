package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class MainLevelExitRoutesTest {
  @Test fun everyMainLevelZeroThroughTwelveHasExactlyItsNextLevel() {
    for (level in 0..12) {
      val route = MainLevelExitRoutes.next(level)
      assertNotNull("Main Level $level must connect forward", route)
      assertEquals("level-$level", route!!.sourceNodeId)
      assertEquals("level-${level + 1}", route.targetNodeId)
      assertEquals(level + 1, route.targetLevelNumber)
      assertEquals((level + 1).toLong() * RANK_PER_FULL_LEVEL, route.targetProgressionRank)
      assertEquals(WorldContentCatalog.entry(WorldNodeId(route.targetNodeId))?.title, route.targetTitle)
      assertTrue(route.targetTitle.isNotBlank())
    }
  }

  @Test fun levelThirteenIsTerminalAndUnknownNumbersNeverFallback() {
    for (level in listOf(-10, -1, 13, 14, 999)) assertNull(MainLevelExitRoutes.next(level))
    assertNull(MainLevelExitRoutes.titleFor(14))
    assertNull(MainLevelExitRoutes.titleFor(-1))
    assertEquals("Thalassophobia", MainLevelExitRoutes.titleFor(7))
    assertEquals("The Boiling Frogs", MainLevelExitRoutes.titleFor(13))
  }

  @Test fun noSublevelOrNamedAreaReceivesAnExitByImplicitOrdering() {
    assertTrue(WorldProgressionCore.EDGES.none {
      it.from.value.contains('.') || it.to.value.contains('.')
    })
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-7.6"), WorldNodeId("level-7.7")) is TransitionResult.Rejected)
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-7"), WorldNodeId("level-9")) is TransitionResult.Rejected)
  }
}
