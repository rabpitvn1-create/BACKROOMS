package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class PokerDiceCoreBackportTest {
  @Test fun handClassificationIsUnchanged() {
    assertEquals("FSF", PokerDiceCore.classify(listOf(4,4,4,4,4)))
    assertEquals("SSF", PokerDiceCore.classify(listOf(1,2,3,4,5)))
    assertEquals("STRAIGHT", PokerDiceCore.classify(listOf(2,3,4,5,6)))
    assertEquals("FOUR OF A KIND", PokerDiceCore.classify(listOf(6,6,6,6,2)))
    assertEquals("FULL HOUSE", PokerDiceCore.classify(listOf(3,3,3,5,5)))
    assertEquals("TWO PAIR", PokerDiceCore.classify(listOf(1,1,5,5,3)))
  }
  @Test fun rerollCapAndActionBindingAreUnchanged() {
    var state = PokerDiceCore.prepare(GameState.initial(), PokerDiceCore.DIRECT_COMBAT_ACTION, "E1")
    repeat(3) {
      val held = PokerDiceCore.diceJson(state)!!.getJSONArray("held")
      for (i in 0 until 5) if (held.optBoolean(i,false)) state = PokerDiceCore.setHold(state,i,false)
      state = PokerDiceCore.reroll(state)
    }
    try { PokerDiceCore.reroll(state); fail("fourth reroll must fail") } catch (_: IllegalStateException) {}
    try { PokerDiceCore.prepare(state, "Bỏ chạy", "E1"); fail("action switch must fail") } catch (_: IllegalStateException) {}
  }
  @Test fun coreStatsProjectionIsReadOnlyAndIncludesLevelExp() {
    val state = GameState.initial()
    val snapshot = PokerDiceCore.statsJson(state, CAO_MINH_ID)
    assertEquals(0, snapshot.getInt("level"))
    assertEquals(0, snapshot.getInt("exp"))
    assertEquals(10, snapshot.getInt("expToNextLevel"))
    assertFalse(snapshot.has("sharedCore"))
    assertEquals(5, snapshot.getJSONObject("stats").getJSONObject("STR").getInt("base"))
    assertFalse(snapshot.getJSONObject("stats").getJSONObject("STR").has("nextCoreCost"))
  }
  private fun hand(values: List<Int>, held: List<Boolean>, seed: Long): GameState {
    val prepared = PokerDiceCore.prepare(GameState.initial(), PokerDiceCore.DIRECT_COMBAT_ACTION, "TEST-DICE")
    return prepared.copy(metadata = prepared.metadata + mapOf(
      "poker.dice.values" to values.joinToString(","),
      "poker.dice.held" to held.joinToString(",") { if (it) "1" else "0" },
      "poker.dice.seed" to seed.toString()
    ))
  }

  private fun targetRate(values: List<Int>, held: List<Boolean>, slot: Int, target: Int): Double {
    var matches = 0
    val samples = 12000
    val fixture = hand(values, held, 0L)
    for (seed in 0 until samples) {
      val before = fixture.copy(metadata = fixture.metadata +
        ("poker.dice.seed" to (seed.toLong() * 104729L).toString()))
      val result = PokerDiceCore.diceJson(PokerDiceCore.reroll(before))!!
      val dice = result.getJSONArray("values")
      if (dice.getInt(slot) == target) matches++
      for (i in 0 until 5) if (held[i]) assertEquals(values[i], dice.getInt(i))
      assertEquals(1, result.getInt("rerollsUsed"))
    }
    return matches.toDouble() / samples
  }

  @Test fun aHeldQuadMoreOftenCompletesFsfButDoesNotGuaranteeIt() {
    val rate = targetRate(listOf(4,4,4,4,2), listOf(true,true,true,true,false), 4, 4)
    assertTrue("FSF matching rate=$rate", rate in 0.56..0.61)
  }

  @Test fun matchingPairsAndTriplesGetProgressivelyStrongerAssistance() {
    val pair = targetRate(listOf(4,4,1,2,6), listOf(true,true,false,false,false), 2, 4)
    val triple = targetRate(listOf(4,4,4,2,6), listOf(true,true,true,false,false), 3, 4)
    assertTrue("pair matching rate=$pair", pair in 0.42..0.47)
    assertTrue("triple matching rate=$triple", triple in 0.50..0.55)
    assertTrue(triple > pair)
  }

  @Test fun bothSsfDirectionsGainAssistanceWithoutReclassifyingOrdinaryStraight() {
    val held = listOf(true,true,true,true,false)
    val shortRun = targetRate(listOf(1,2,6,6,6), listOf(true,true,false,false,false), 4, 5)
    val middleRun = targetRate(listOf(1,2,3,6,6), listOf(true,true,true,false,false), 4, 5)
    val ascending = targetRate(listOf(1,2,3,4,6), held, 4, 5)
    assertTrue("two-die SSF progress=$shortRun", shortRun in 0.37..0.43)
    assertTrue("three-die SSF progress=$middleRun", middleRun in 0.43..0.48)
    assertTrue(middleRun > shortRun && ascending > middleRun)
    val descending = targetRate(listOf(5,4,3,2,6), held, 4, 1)
    val ordinary = targetRate(listOf(2,3,4,5,1), held, 4, 6)
    assertTrue("ascending SSF rate=$ascending", ascending in 0.47..0.53)
    assertTrue("descending SSF rate=$descending", descending in 0.47..0.53)
    assertTrue("ordinary straight rate=$ordinary", ordinary in 0.24..0.29)
    assertEquals("NO HAND", PokerDiceCore.classify(listOf(1,3,2,4,5)))
  }

  @Test fun releasingEveryHoldKeepsUniformRollsAndRetryIsDeterministic() {
    val values = listOf(4,4,4,4,2)
    val held = List(5) { false }
    val rate = targetRate(values, held, 4, 4)
    assertTrue("unheld rate=$rate", rate in 0.14..0.19)
    val before = hand(values, held, 42L)
    assertEquals(PokerDiceCore.reroll(before), PokerDiceCore.reroll(before))
    val finalized = PokerDiceCore.finish(before)
    try { PokerDiceCore.reroll(finalized); fail("finalized hand must not reroll") }
    catch (_: IllegalStateException) {}
  }

}
