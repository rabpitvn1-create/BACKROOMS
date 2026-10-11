package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GameCoreFacadeContractTest {
  @Test fun newRunStartsWithCleanAuthoritativeState() {
    val state = GameState.initial()
    assertEquals(listOf(CAO_MINH_ID), state.party.memberIds)
    assertEquals(CURRENT_SAVE_VERSION, state.saveVersion)
  }
}
