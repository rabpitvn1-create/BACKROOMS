package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerDiceRuntimeTest {
  @Test fun handClassificationMatchesV2Contract() {
    assertEquals("SSF", PokerDiceRuntime.classify(1, 2, 3, 4, 5))
    assertEquals("SSF", PokerDiceRuntime.classify(5, 4, 3, 2, 1))
    assertEquals("STRAIGHT", PokerDiceRuntime.classify(2, 3, 4, 5, 6))
    assertEquals("STRAIGHT", PokerDiceRuntime.classify(6, 5, 4, 3, 2))
    assertEquals("NO HAND", PokerDiceRuntime.classify(5, 1, 4, 2, 3))
    assertEquals("ONE PAIR", PokerDiceRuntime.classify(2, 2, 1, 4, 6))
    assertEquals("TWO PAIR", PokerDiceRuntime.classify(2, 2, 4, 4, 6))
    assertEquals("THREE OF A KIND", PokerDiceRuntime.classify(3, 3, 3, 1, 6))
    assertEquals("FULL HOUSE", PokerDiceRuntime.classify(3, 3, 3, 6, 6))
    assertEquals("FOUR OF A KIND", PokerDiceRuntime.classify(4, 4, 4, 4, 2))
    assertEquals("FSF", PokerDiceRuntime.classify(6, 6, 6, 6, 6))
  }

  @Test fun initialRollIsFiveDiceAndHoldSurvivesReroll() {
    val combat = CombatRuntime.start(GameState.initial(), "hound")
    var state = PokerDiceRuntime.ensureInitialRoll(combat)
    var dice = PokerDiceRuntime.toJson(state)!!
    assertEquals(5, dice.getJSONArray("values").length())
    assertEquals(5, dice.getJSONArray("held").length())
    assertTrue(dice.getBoolean("hasRolled"))
    assertEquals(0, dice.getInt("rerollsUsed"))
    assertEquals(3, dice.getInt("maxRerolls"))

    val first = dice.getJSONArray("values").getInt(0)
    state = PokerDiceRuntime.setHold(state, 0, true)
    state = PokerDiceRuntime.roll(state)
    dice = PokerDiceRuntime.toJson(state)!!
    assertEquals(first, dice.getJSONArray("values").getInt(0))
    assertTrue(dice.getJSONArray("held").getBoolean(0))
    assertTrue(dice.getInt("rerollsUsed") <= 1)
  }

  @Test fun finishAndResolveStateAreExplicitAndPersistent() {
    val combat = CombatRuntime.start(GameState.initial(), "clump")
    var state = PokerDiceRuntime.ensureInitialRoll(combat)
    state = PokerDiceRuntime.finish(state)
    var dice = PokerDiceRuntime.toJson(state)!!
    assertTrue(dice.getBoolean("finalized"))
    assertFalse(dice.getBoolean("resolved"))
    assertTrue(dice.getString("hand").isNotBlank())

    state = PokerDiceRuntime.markResolved(state)
    dice = PokerDiceRuntime.toJson(state)!!
    assertTrue(dice.getBoolean("resolved"))
    assertEquals(dice.getString("hand"), PokerDiceRuntime.currentResolvedHand(state))
  }

  @Test fun v2HandScalingContractIsPreserved() {
    assertEquals(125, PokerDiceRuntime.damagePercent("ONE PAIR"))
    assertTrue(PokerDiceRuntime.evadeResponse("TWO PAIR"))
    assertEquals(150, PokerDiceRuntime.damagePercent("STRAIGHT"))
    assertEquals(200, PokerDiceRuntime.damagePercent("FULL HOUSE"))
    assertEquals(250, PokerDiceRuntime.damagePercent("FOUR OF A KIND"))
    assertTrue(PokerDiceRuntime.ultimateHand("SSF"))
    assertEquals(2, PokerDiceRuntime.ultimateMultiplier("FSF"))
  }
}
