package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class DevilTriggerPassiveTest {
  @Test fun readyUsesExactlyThirtyFivePercentThreshold() {
    assertTrue(DevilTriggerPassive.beginTurn(DevilTriggerState(), 34).triggeredThisTurn)
    assertFalse(DevilTriggerPassive.beginTurn(DevilTriggerState(), 35).triggeredThisTurn)
  }

  @Test fun activeLastsThreeTurnsThenReturnsReadyWithoutCooldown() {
    var turn = DevilTriggerPassive.beginTurn(DevilTriggerState(), 0)
    var state = DevilTriggerPassive.endTurn(turn)
    assertEquals(2, state.activeTurns)
    turn = DevilTriggerPassive.beginTurn(state, 99); state = DevilTriggerPassive.endTurn(turn)
    assertEquals(1, state.activeTurns)
    turn = DevilTriggerPassive.beginTurn(state, 99); state = DevilTriggerPassive.endTurn(turn)
    assertEquals(DevilTriggerState(), state)
    assertEquals(0, DevilTriggerPassive.COOLDOWN_TURNS)
    assertTrue(DevilTriggerPassive.beginTurn(state, 0).triggeredThisTurn)
  }

  @Test fun activeEffectsRemainKaiDevilTriggerEffects() {
    assertEquals(500, DevilTriggerPassive.damage(100, true))
    assertEquals(100, DevilTriggerPassive.evasionBonus(true))
    assertEquals(5, DevilTriggerPassive.healAmount(100))
  }
}
