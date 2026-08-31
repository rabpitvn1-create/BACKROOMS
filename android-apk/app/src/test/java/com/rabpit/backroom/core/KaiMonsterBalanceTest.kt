package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class KaiMonsterBalanceTest {
  @Test fun monstersAboveOneThousandGainTwoHundredHp() {
    assertEquals(3199, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "diep_minh"))!!.entityMaxHp)
    assertEquals(3656, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "monster_x"))!!.entityMaxHp)
    assertEquals(1434, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "john_doe"))!!.entityMaxHp)
    assertEquals(1930, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "scp_173"))!!.entityMaxHp)
  }

  @Test fun monstersBelowOneThousandDoNotGainHpTierBonus() {
    assertEquals(500, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)
  }
}
