package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LuciaRegenIntervalTest {
  private fun injuredLucia(): GameState {
    val base = LuciaCanon.ensure(GameState.initial())
    val lucia = base.characters.getValue(LUCIA_ID)
    return base.copy(characters = base.characters + (
      LUCIA_ID to lucia.copy(
        vitalState = lucia.vitalState.copy(
          currentHp = 90,
          completedTurnsSinceRegen = 0,
          lastRegenCompletedTurnId = null
        )
      )
    ))
  }

  @Test fun luciaHealsTwoHpOnlyAfterThreeDistinctCompletedTurns() {
    val start = injuredLucia()
    val first = CharacterStatEngine.applyCompletedTurnRegen(start, "TURN_1")
    assertEquals(90, first.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(1, first.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)

    val second = CharacterStatEngine.applyCompletedTurnRegen(first, "TURN_2")
    assertEquals(90, second.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(2, second.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)

    val third = CharacterStatEngine.applyCompletedTurnRegen(second, "TURN_3")
    assertEquals(92, third.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(0, third.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)

    val duplicate = CharacterStatEngine.applyCompletedTurnRegen(third, "TURN_3")
    assertEquals(92, duplicate.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(0, duplicate.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)
  }

  @Test fun saveLoadPreservesLuciaThreeTurnRegenProgress() {
    val first = CharacterStatEngine.applyCompletedTurnRegen(injuredLucia(), "TURN_1")
    val second = CharacterStatEngine.applyCompletedTurnRegen(first, "TURN_2")
    val restored = GameStateCodec.decode(GameStateCodec.encode(second))
    val third = CharacterStatEngine.applyCompletedTurnRegen(restored, "COMBAT_TURN_3")

    val lucia = third.characters.getValue(LUCIA_ID)
    assertEquals(92, lucia.vitalState.currentHp)
    assertEquals(0, lucia.vitalState.completedTurnsSinceRegen)
    assertEquals(3, lucia.statProfile.regen.intervalCompletedTurns)
    assertEquals(2, lucia.statProfile.regen.amountPerCompletedTurn)
  }
}
