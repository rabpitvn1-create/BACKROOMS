package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ExitStreakEngineTest {
  private val longAction = "Tôi tiếp tục khám phá khu vực phía trước."

  @Test fun requiresFifteenUnicodeCharactersAfterTrimming() {
    assertFalse(ExitStreakEngine.hasMinimumInput("12345678901234"))
    assertTrue(ExitStreakEngine.hasMinimumInput("123456789012345"))
    assertFalse(ExitStreakEngine.hasMinimumInput("     "))
    assertTrue(ExitStreakEngine.hasMinimumInput("  123456789012345  "))
    assertFalse(ExitStreakEngine.hasMinimumInput("😀".repeat(14)))
    assertTrue(ExitStreakEngine.hasMinimumInput("😀".repeat(15)))
  }

  @Test fun invalidInputDoesNotRollOrChangeStreak() {
    val result = ExitStreakEngine.advance(4, "quá ngắn", false) {
      throw AssertionError("Invalid turn must not consume RNG")
    }
    assertFalse(result.accepted)
    assertFalse(result.evaluated)
    assertEquals("input_too_short", result.error)
    assertEquals(4, result.streak)
    assertFalse(result.completed)
  }

  @Test fun combatNeverRollsOrChangesExistingProgress() {
    for (streak in 0..4) {
      val result = ExitStreakEngine.advance(streak, "hit", true) {
        throw AssertionError("Combat must not consume exit RNG")
      }
      assertTrue(result.accepted)
      assertFalse(result.evaluated)
      assertNull(result.success)
      assertEquals(streak, result.streak)
      assertFalse(result.completed)
    }
  }

  @Test fun fiveConsecutiveWinsCompleteLevel() {
    var streak = 0
    var totalRolls = 0
    repeat(5) { index ->
      val result = ExitStreakEngine.advance(streak, longAction, false) { bound ->
        assertEquals(2, bound)
        totalRolls++
        0
      }
      streak = result.streak
      assertEquals(index + 1, streak)
      assertEquals(index == 4, result.completed)
      assertTrue(result.evaluated)
    }
    assertEquals(5, totalRolls)
  }

  @Test fun anyLossResetsProgressToZero() {
    val result = ExitStreakEngine.advance(4, "xxxxxxxxxxxxxxx", false) { 1 }
    assertTrue(result.accepted)
    assertTrue(result.evaluated)
    assertFalse(result.success!!)
    assertEquals(0, result.streak)
    assertFalse(result.completed)
  }

  @Test fun nonsenseInputIsAcceptedWhenLongEnough() {
    val result = ExitStreakEngine.advance(2, "xxxxxxxxxxxxxxx", false) { 0 }
    assertTrue(result.accepted)
    assertEquals(3, result.streak)
  }

  @Test fun impossiblePersistedStreakDoesNotGrantCompletion() {
    val result = ExitStreakEngine.advance(100, longAction, false) { 0 }
    assertEquals(1, result.streak)
    assertFalse(result.completed)
  }
}
