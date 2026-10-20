package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PlayerFootstepIntentTest {
  @Test fun countedForwardStepsAreDeterministic() {
    assertEquals(1, PlayerFootstepIntent.count("Tiến lên 1 bước"))
    assertEquals(3, PlayerFootstepIntent.count("Tiến lên 3 bước"))
    assertEquals(3, PlayerFootstepIntent.count("Cao Minh tiến thêm ba bước chân."))
    assertEquals(12, PlayerFootstepIntent.count("Tôi đi về phía trước mười hai bước"))
  }

  @Test fun compositeOrAmbiguousCommandsRemainWithNormalPlanner() {
    assertEquals(0, PlayerFootstepIntent.count("Tiến 3 bước rồi mở cửa"))
    assertEquals(0, PlayerFootstepIntent.count("Đi tới phòng số 3"))
    assertEquals(0, PlayerFootstepIntent.count("Tiến vào khu tiếp theo"))
    assertEquals(0, PlayerFootstepIntent.count("Lùi lại ba bước"))
    assertThrows(IllegalArgumentException::class.java) { PlayerFootstepIntent.count("Tiến 0 bước") }
    assertThrows(IllegalArgumentException::class.java) { PlayerFootstepIntent.count("Tiến 13 bước") }
  }
}
