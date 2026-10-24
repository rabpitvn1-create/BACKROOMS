package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RestoredCompanionCombatTest {
  @Test fun isolatedFollowerNeverAttacksForTheParty() {
    val state = GameState.initial()
    val start = CombatRuntime.active(CombatRuntime.start(state, "hound"))!!
    val profile = CombatRuntime.Profile("hound", "Hound", 80, 15, 2, 8)
    val resolved = RestoredCompanionCombat.beforeEnemyResponse(
      state, start, profile, CombatRuntime.Intent.ATTACK,
      "PARTY_ATTACK", "Party tấn công"
    )
    assertEquals(start.entityHp, resolved.combat.entityHp)
    assertTrue(resolved.feedback.isEmpty())
  }

  @Test fun readOrGuardDoesNotFireExtraAttacks() {
    val state = GameState.initial()
    val start = CombatRuntime.active(CombatRuntime.start(state, "hound"))!!
    val profile = CombatRuntime.Profile("hound", "Hound", 80, 15, 2, 8)
    val result = RestoredCompanionCombat.beforeEnemyResponse(
      state, start, profile, CombatRuntime.Intent.GUARD,
      "PARTY_GUARD", "Party phòng thủ"
    )
    assertEquals(start.entityHp, result.combat.entityHp)
    assertTrue(result.feedback.isEmpty())
  }
}
