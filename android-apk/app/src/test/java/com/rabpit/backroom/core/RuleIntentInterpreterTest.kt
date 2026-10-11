package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RuleIntentInterpreterTest {
  private val parser = RuleIntentInterpreter()
  private val context = GameContext(GameState.initial())

  private fun parse(text: String) = parser.interpretSync(text, context)

  @Test fun deterministicCommandsStayLocal() {
    assertEquals(GameIntent.PICKUP_ITEM, parse("Cao Minh nhặt chai nước").candidates.single().intent)
    assertEquals(GameIntent.PARTY_JOIN_REQUEST, parse("Iris vào party").candidates.single().intent)
    assertFalse(parse("Cao Minh nhặt chai nước").requiresFallback)
  }

  @Test fun splitsMultipleActions() {
    val result = parse("Cao Minh nhặt chai nước rồi đưa Iris một chai")
    assertEquals(listOf(GameIntent.PICKUP_ITEM, GameIntent.TRANSFER_ITEM), result.candidates.map { it.intent })
  }

  @Test fun narrativeMemoryNegationAndQuotesDoNotExecute() {
    val samples = listOf(
      "Cao Minh nhìn Iris lấy chai nước",
      "Cao Minh nhớ lần trước mình bỏ súng vào nhẫn",
      "Cao Minh không nhặt chai nước",
      "Iris nói: “nhặt chai nước lên”"
    )
    samples.forEach { assertEquals(it, GameIntent.NO_ACTION, parse(it).candidates.single().intent) }
  }

  @Test fun unknownRequiresFallback() {
    val result = parse("Cao Minh cân nhắc tình hình kỳ lạ trước mặt")
    assertEquals(GameIntent.UNKNOWN, result.candidates.single().intent)
    assertTrue(result.requiresFallback)
  }
}
