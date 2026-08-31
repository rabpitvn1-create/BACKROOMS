package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainStorySeparationTest {
  @Test fun freshCampaignStartsWithIrisAndSyvialSeparatedFromKaiAndEachOther() {
    val state = GameState.initial()

    assertEquals(CharacterPresence.ACTIVE, state.characters[KAI_ID]!!.presence)
    assertEquals(CharacterPresence.SEPARATED, state.characters[IRIS_ID]!!.presence)
    assertEquals(CharacterPresence.SEPARATED, state.characters[SYVIAL_ID]!!.presence)
    assertFalse(IRIS_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
  }
}
