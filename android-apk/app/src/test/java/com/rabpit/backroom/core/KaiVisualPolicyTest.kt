package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class KaiVisualPolicyTest {
  @Test fun newGameUsesSruAvatar() {
    assertEquals("avatars/SRU_AVATAR.jpg", GameState.initial().characters.getValue(KAI_ID).avatarRef)
  }
}
