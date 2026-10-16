package com.rabpit.backroom.core.knowledge

import org.junit.Assert.*
import org.junit.Test

class GmNarrativeVoicePolicyTest {
  @Test fun narratorRejectsOneHundredDistinctSecondPersonCases() {
    val verbs = listOf("nhìn", "nghe", "thấy", "bước", "chạy", "đứng", "quay",
      "rút", "nhận ra", "cảm thấy")
    val scenes = listOf("", "Trước cửa, ", "Giữa hành lang, ", "Một lúc sau, ",
      "Ngay khoảnh khắc ấy, ", "Trong bóng tối, ", "Đột nhiên, ",
      "Trên vách tường, ", "Khi đèn chớp tắt, ", "Bấy giờ, ")
    var checked = 0
    for (scene in scenes) for (verb in verbs) {
      val reply = scene + (if (checked % 2 == 0) "Bạn" else "bạn") +
        " $verb dấu vết trên nền gạch."
      assertTrue("Expected second-person narrator rejection #$checked: $reply",
        GmNarrativeVoicePolicy.hasSecondPersonNarration(reply))
      checked++
    }
    assertEquals(100, checked)
  }

  @Test fun narratorPreservesOneHundredDistinctValidDialogueAndThirdPersonCases() {
    val verbs = listOf("nhìn", "nghe", "thấy", "bước", "chạy", "đứng", "quay",
      "rút", "nhận ra", "cảm thấy")
    var checked = 0
    repeat(100) { index ->
      val verb = verbs[index % verbs.size]
      val suffix = " Lần " + (index + 1) + "."
      val reply = when (index % 5) {
        0 -> "Lucia nói: “Bạn $verb ra hành lang!”" + suffix
        1 -> "— Bạn $verb cửa sổ giúp tôi.\nCao Minh lặng im." + suffix
        2 -> "Cao Minh $verb về phía hành lang, người bạn đồng hành vẫn ở sau." + suffix
        3 -> "Hắn $verb thấy bóng đen sau bức tường." + suffix
        else -> "Iris nói: \"Bạn $verb điều đó chứ?\"" + suffix
      }
      assertFalse("Do not change legitimate dialogue/third-person narrator #$index: $reply",
        GmNarrativeVoicePolicy.hasSecondPersonNarration(reply))
      checked++
    }
    assertEquals(100, checked)
  }

  @Test fun promptKeepsNarratorVoiceDistinctFromCharacterEraAndPreservesAgency() {
    val policy = GmNarrativeVoicePolicy.prompt()
    assertTrue(policy.contains("ngôi THỨ BA"))
    assertTrue(policy.contains("Cao Minh"))
    assertTrue(policy.contains("hắn"))
    assertTrue(policy.contains("KHÔNG gọi Cao Minh là \"bạn\""))
    for (actor in listOf("LỤC TRẦM", "LUCIA", "IRIS", "SYVIAL", "AN NHIÊN")) {
      assertTrue("Missing character voice authority: $actor", policy.contains(actor))
    }
    assertTrue(policy.contains("UNKNOWN"))
    assertTrue(policy.contains("người chơi"))
    assertTrue(policy.contains("tiếng Việt tự nhiên"))
    assertFalse(GmNarrativeVoicePolicy.hasSecondPersonNarration(
      "Cao Minh nghe tiếng đèn huỳnh quang. Hắn bước đến, rồi dừng lại."))
  }
}
