package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RuleIntentInterpreterTest {
  private val parser = RuleIntentInterpreter()
  private val context = GameContext(GameState.initial())

  private fun parse(text: String) = parser.interpretSync(text, context)

  @Test fun deterministicCommandsAndRetiredAcquisitionStayLocal() {
    assertEquals(GameIntent.DISCARD_ITEM, parse("Kai vứt chai nước").candidates.single().intent)
    assertEquals(GameIntent.OMNIVAULT_STORE, parse("Kai cất khẩu súng vào nhẫn").candidates.single().intent)
    assertEquals(GameIntent.PARTY_JOIN_REQUEST, parse("Iris vào party").candidates.single().intent)

    val pickup = parse("Kai nhặt chai nước")
    assertEquals(GameIntent.NO_ACTION, pickup.candidates.single().intent)
    assertEquals("world_item_unavailable", pickup.candidates.single().reason)
    assertFalse(pickup.requiresFallback)

    val copy = parse("Kai tạo thêm 3 vỏ chai nước rỗng bằng nhẫn")
    assertEquals(GameIntent.NO_ACTION, copy.candidates.single().intent)
    assertEquals("omnivault_creation_removed", copy.candidates.single().reason)
    assertFalse(copy.requiresFallback)
  }

  @Test fun splitsMultipleActions() {
    val result = parse("Kai lấy hai chai nước ra khỏi nhẫn rồi đưa một chai cho Iris")
    assertEquals(listOf(GameIntent.OMNIVAULT_WITHDRAW, GameIntent.TRANSFER_ITEM), result.candidates.map { it.intent })
  }

  @Test fun safetyHandledMemoryNegationAndQuotesDoNotExecute() {
    val samples = listOf(
      "Kai nhớ lần trước mình bỏ súng vào nhẫn",
      "Kai không nhặt chai nước",
      "Iris nói: “nhặt chai nước lên”"
    )
    samples.forEach { assertEquals(it, GameIntent.NO_ACTION, parse(it).candidates.single().intent) }
  }

  @Test fun pureNarrativeObservationFallsBackWithoutExecutingLocalCommand() {
    val result = parse("Kai nhìn Iris lấy chai nước")
    assertEquals(GameIntent.UNKNOWN, result.candidates.single().intent)
    assertTrue(result.requiresFallback)
  }

  @Test fun unknownRequiresFallback() {
    val result = parse("Kai cân nhắc tình hình kỳ lạ trước mặt")
    assertEquals(GameIntent.UNKNOWN, result.candidates.single().intent)
    assertTrue(result.requiresFallback)
  }
}
