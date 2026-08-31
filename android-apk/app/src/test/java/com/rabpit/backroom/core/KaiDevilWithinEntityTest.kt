package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KaiDevilWithinEntityTest {
  @Test fun startsWithExactCanonicalHpAndName() {
    val started = CombatRuntime.start(GameState.initial(), "kai_the_devil_within")
    val combat = CombatRuntime.active(started)
    assertNotNull(combat)
    assertEquals("Kai - The Devil Within", combat!!.entityName)
    assertEquals(5678, combat.entityMaxHp)
    assertEquals(5678, combat.entityHp)
  }
}
