package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RestoredBossProfileTest {
  @Test fun diepMinhHasOriginalApkMaxHp() {
    val started = CombatRuntime.start(GameState.initial(), "diep_minh")
    val active = CombatRuntime.active(started)
    assertNotNull(active)
    assertEquals("diep_minh", active!!.entityKey)
    assertEquals(2999, active.entityMaxHp)
    assertEquals(2999, active.entityHp)
  }

  @Test fun bossCombatIsStoredInsideCurrentGameState() {
    val started = CombatRuntime.start(GameState.initial(), "diep_minh")
    assertNotNull(CombatRuntime.active(started))
    assertEquals(2999, CombatRuntime.active(started)?.entityMaxHp)
  }
}
