package com.rabpit.backroom.core.knowledge

import org.junit.Assert.*
import org.junit.Test

class GmNarrativeVoicePolicyTest {
  @Test fun openingHistoryRetainsTheApprovedContextBudgetAndCompleteFinalBoundary() {
    val history = GmNarrativeVoicePolicy.openingContext()
    assertTrue("Expected a substantive 6,000–7,000-character summary, got ${history.length}", history.length in 6000..7000)
    assertTrue(history.startsWith("LỊCH SỬ MỞ ĐẦU"))
    assertTrue(history.endsWith("KẾT THÚC LỊCH SỬ MỞ ĐẦU."))
    assertTrue(history.split("\n\n").size >= 10)
  }

  @Test fun openingHistoryPreservesCausalEventsRelationshipsAndTheCostOfTheEscape() {
    val history = GmNarrativeVoicePolicy.openingContext()
    listOf("Thiên Kiếm Môn", "Tịch Quang", "Thiên Cơ Bạch Kim Kiếm Khải", "Cao Minh",
      "Vô Lượng Đại Tôn Giả", "Diệp Minh", "Sinh Mệnh Đạo", "ba nghìn hiệp",
      "Huyết Ma Nhị Thập Tứ Trảm", "Cửu Giới Huyền Giám", "con tin",
      "sợ chết", "còn muốn sống", "không có tọa độ", "không có mạch",
      "không có bản nguyên", "tổn hại căn cơ", "suy yếu", "Huyết Ma Kiếm").forEach {
      assertTrue("Missing essential opening fact: $it", history.contains(it))
    }
  }

  @Test fun openingHistoryDoesNotAuthorizeCurrentActorsPowersOrInventTheMissingAnswers() {
    val history = GmNarrativeVoicePolicy.openingContext()
    listOf("không xác lập danh phận vợ chồng hay đạo lữ", "nguyên nhân chi tiết không được giải thích",
      "không được tự cấp lại", "không được kể thành hồi sinh thành công",
      "không xác nhận nàng đang sống tại một phòng cụ thể",
      "Tri thức của người kể không tự trở thành tri thức NPC", "không lấy sự thân thiết quá khứ ghi đè mất ký ức",
      "tiến triển hiện tại chỉ theo Core").forEach {
      assertTrue("Missing continuity/authority guard: $it", history.contains(it))
    }
  }

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
    val gameplayGuidance = com.rabpit.backroom.core.GmNarrativeVoicePolicy.voiceGuidance(
      com.rabpit.backroom.core.GmNarrativeVoicePolicy.QUIET_DISCOVERY,
      com.rabpit.backroom.core.GmNarrativeVoicePolicy.NONE
    )
    assertTrue(gameplayGuidance.contains("tiếng Việt tự nhiên"))
    assertFalse(GmNarrativeVoicePolicy.hasSecondPersonNarration(
      "Cao Minh nghe tiếng đèn huỳnh quang. Hắn bước đến, rồi dừng lại."))
  }
}
