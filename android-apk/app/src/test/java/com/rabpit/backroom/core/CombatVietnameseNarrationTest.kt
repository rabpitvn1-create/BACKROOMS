package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatVietnameseNarrationTest {
  @Test fun issue123ExampleIsVietnameseWhileSkillNamesStayCanonical() {
    val raw = "Bleeding từ The Last Requiem gây -97 HP (5% Max HP; 381/1930); còn 1 turn. " +
      "Concrete Rush: Kai Akechi -35 HP (25% Max HP, vulnerable Blink/Blind/Stun); CD 2; Stun 1 lượt (35% proc). " +
      "SCP-173 Snap Strike -11 HP (10% Max HP); Stun không proc."

    val localized = CombatRuntime.localizeCombatNarration(raw)

    assertEquals(
      "Chảy máu từ The Last Requiem gây -97 HP (5% Max HP • 381/1930) • còn 1 lượt. " +
        "Concrete Rush: Kai Akechi -35 HP (25% Max HP, dễ bị ảnh hưởng bởi Blink/Blind/Stun) • hồi chiêu còn 2 • Choáng 1 lượt (35% tỷ lệ kích hoạt). " +
        "SCP-173 Snap Strike -11 HP (10% Max HP) • hiệu ứng Choáng không kích hoạt.",
      localized
    )
    assertTrue(localized.contains("The Last Requiem"))
    assertTrue(localized.contains("Concrete Rush"))
    assertTrue(localized.contains("Snap Strike"))
    assertFalse(localized.contains("Bleeding"))
    assertFalse(localized.contains("CD 2"))
    assertFalse(localized.contains("Stun không proc"))
  }

  @Test fun partyActionHeaderAndCombatTurnAreVietnamese() {
    val raw = "PARTY ACTION TẤN CÔNG: Kai Akechi, Lucia \"Lục\" cùng khai triển đòn đánh trong một combat turn."
    assertEquals(
      "HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG: Kai Akechi, Lucia \"Lục\" cùng khai triển đòn đánh trong một lượt tấn công.",
      CombatRuntime.localizeCombatNarration(raw)
    )
  }
}
