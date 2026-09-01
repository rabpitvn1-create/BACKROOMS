package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryCompanionContinuityTest {
  @Test fun fixedLevelsAreCanonical() {
    assertEquals(0, StoryCompanionContinuity.fixedLevel("lucia"))
    assertEquals(37, StoryCompanionContinuity.fixedLevel("syvial"))
    assertEquals(94, StoryCompanionContinuity.fixedLevel("iris"))
  }

  @Test fun storyCompanionsNeverUseRandomSpawn() {
    assertFalse(StoryCompanionContinuity.randomSpawnAllowed("lucia"))
    assertFalse(StoryCompanionContinuity.randomSpawnAllowed("syvial"))
    assertFalse(StoryCompanionContinuity.randomSpawnAllowed("iris"))
    assertTrue(StoryCompanionContinuity.randomSpawnAllowed("unknown-survivor"))
  }

  @Test fun materializationOnlyOccursAtTheFixedLevelOnce() {
    assertTrue(StoryCompanionContinuity.canMaterialize("lucia", 0, false))
    assertFalse(StoryCompanionContinuity.canMaterialize("lucia", 1, false))
    assertFalse(StoryCompanionContinuity.canMaterialize("lucia", 0, true))

    assertTrue(StoryCompanionContinuity.canMaterialize("syvial", 37, false))
    assertFalse(StoryCompanionContinuity.canMaterialize("syvial", 36, false))
    assertFalse(StoryCompanionContinuity.canMaterialize("syvial", 38, false))

    assertTrue(StoryCompanionContinuity.canMaterialize("iris", 94, false))
    assertFalse(StoryCompanionContinuity.canMaterialize("iris", 93, false))
    assertFalse(StoryCompanionContinuity.canMaterialize("iris", 95, false))
  }
}
