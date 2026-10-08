package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the narrative-conflict detection shape from issue #453.
 * Detection is best-effort; authority NEVER flows from AI claims.
 */
class ProgressionConflictTest {

  @Test fun matchingClaimIsNoConflict() {
    val authoritative = WorldNodeId("level-2")
    assertNull(ProgressionConflictPolicy.detect("level-2", authoritative))
    assertNull(ProgressionConflictPolicy.detect("2", authoritative))
    assertNull(ProgressionConflictPolicy.detect("Level 2", authoritative))
    assertNull(ProgressionConflictPolicy.detect("level-1.sub-a", WorldNodeId("level-1.sub-a")))
  }

  @Test fun mismatchedClaimIsConflictWithoutMutatingProgression() {
    val conflict = ProgressionConflictPolicy.detect("level-3", WorldNodeId("level-2"))
    assertNotNull(conflict)
    assertEquals("level-3", conflict!!.claimed)
    assertEquals(WorldNodeId("level-2"), conflict.authoritativeNodeId)
    // The conflict record carries no rank/scale: nothing here can become authority.
  }

  @Test fun blankClaimIsNoConflict() {
    assertNull(ProgressionConflictPolicy.detect(null, WorldNodeId("level-2")))
    assertNull(ProgressionConflictPolicy.detect("   ", WorldNodeId("level-2")))
  }
}
