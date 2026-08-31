package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStorySeparationTest {
  @Test fun freshCampaignKeepsIrisAndSyvialOutsideKaisParty() {
    val state = GameState.initial()

    assertEquals(listOf(KAI_ID), state.party.memberIds)
    assertTrue(IRIS_ID in state.characters)
    assertTrue(SYVIAL_ID in state.characters)
    assertFalse(IRIS_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
  }
}
