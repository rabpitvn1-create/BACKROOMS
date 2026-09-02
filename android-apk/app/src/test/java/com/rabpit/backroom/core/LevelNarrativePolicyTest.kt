package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LevelNarrativePolicyTest {
  @Test fun rejectsScreenshotSceneryEvenWithoutAnExplicitLevelNumber() {
    val reply = "Cả hai tiến sâu vào không gian dạng bãi đỗ xe rộng lớn. Cấu trúc bê tông dần chiếm trọn tầm nhìn."
    assertTrue(LevelNarrativePolicy.contradictsArea("0", reply))
    assertTrue(LevelNarrativePolicy.contradictsArea("epsilon", reply))
    assertFalse(LevelNarrativePolicy.contradictsArea("1", reply))
  }

  @Test fun permitsCurrentAreaObservationsAndOtherSublevelMaterials() {
    assertFalse(LevelNarrativePolicy.contradictsArea("0", "Dãy đèn rung nhẹ trên giấy tường vàng và thảm ẩm."))
    assertFalse(LevelNarrativePolicy.contradictsArea("0.5", "Bạn rà tay trên tường bê tông."))
    assertTrue(LevelNarrativePolicy.contradictsArea("0", "Hai người tới BAI DAU XE."))
  }
}
