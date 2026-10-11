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
}
