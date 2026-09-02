package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RuleIntentInterpreterTest {
  private val parser = RuleIntentInterpreter()
  private val context = GameContext(GameState.initial())

  private fun parse(text: String) = parser.interpretSync(text, context)

  @Test fun deterministicCommandsStayLocal() {
    assertEquals(GameIntent.PICKUP_ITEM, parse("Kai nhặt chai nước").candidates.single().intent)
    assertEquals(GameIntent.OMNIVAULT_STORE, parse("Bỏ khẩu súng vào nhẫn").candidates.single().intent)
    assertEquals(GameIntent.OMNIVAULT_COPY, parse("Tạo thêm 3 vỏ chai nước rỗng").candidates.single().intent)
    assertEquals(GameIntent.PARTY_JOIN_REQUEST, parse("Iris vào party").candidates.single().intent)
    assertFalse(parse("Kai nhặt chai nước").requiresFallback)
  }

  @Test fun splitsMultipleActions() {
    val result = parse("Kai lấy hai chai nước ra khỏi nhẫn rồi đưa Iris một chai")
    assertEquals(listOf(GameIntent.OMNIVAULT_WITHDRAW, GameIntent.TRANSFER_ITEM), result.candidates.map { it.intent })
  }

  @Test fun narrativeMemoryNegationAndQuotesDoNotExecute() {
    val samples = listOf(
      "Kai nhìn Iris lấy chai nước",
      "Kai nhớ lần trước mình bỏ súng vào nhẫn",
      "Kai không nhặt chai nước",
      "Iris nói: “nhặt chai nước lên”"
    )
    samples.forEach { assertEquals(it, GameIntent.NO_ACTION, parse(it).candidates.single().intent) }
  }

  @Test fun unknownRequiresFallback() {
    val result = parse("Kai cân nhắc tình hình kỳ lạ trước mặt")
    assertEquals(GameIntent.UNKNOWN, result.candidates.single().intent)
    assertTrue(result.requiresFallback)
  }

  @Test fun dialogueAndTacticalRequestsStayOutOfItemAuthority() {
    val nonMutating = listOf(
      "Bạn hỏi cô ấy sử dụng súng gì và muốn biết loại đạn cô ấy sử dụng có giống với của mình không",
      "Bạn hỏi xin cô ấy một viên đạn.",
      "Bạn yêu cầu Lucia đi Trinh Sát",
      "Lucia đang dùng loại súng nào vậy?",
      "Bảo Lucia giữ góc hành lang và quan sát."
    )
    nonMutating.forEach { input ->
      val candidate = parse(input).candidates.single()
      assertEquals(input, GameIntent.NO_ACTION, candidate.intent)
      assertEquals(input, IntentConfidence.HIGH, candidate.confidence)
    }

    assertEquals(GameIntent.USE_ITEM, parse("Kai dùng băng gạc").candidates.single().intent)
    assertEquals(GameIntent.TRANSFER_ITEM, parse("Kai đưa Lucia một chai nước hạnh nhân").candidates.single().intent)
  }
}
