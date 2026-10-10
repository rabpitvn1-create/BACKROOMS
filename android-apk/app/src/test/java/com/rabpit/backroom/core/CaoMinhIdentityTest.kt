package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CaoMinhIdentityTest {
  @Test fun freshPlayerOwnsPartyInventoryAndEquipmentUnderNewId() {
    val state = GameState.initial()
    assertEquals("cao_minh", KAI_ID)
    assertEquals("Cao Minh", state.characters.getValue("cao_minh").name)
    assertEquals("cao_minh", state.party.leaderId)
    assertTrue(state.party.memberIds.contains("cao_minh"))
    assertTrue(state.inventories.containsKey("cao_minh"))
    assertTrue(state.equipment.containsKey("cao_minh"))
    assertFalse(state.characters.containsKey("kai"))
  }

  @Test fun newSaveRoundTripKeepsPlayerIdentityAndOwnership() {
    val decoded = GameStateCodec.decode(GameStateCodec.encode(GameState.initial()))
    assertEquals("Cao Minh", decoded.characters.getValue("cao_minh").name)
    assertEquals("cao_minh", decoded.party.leaderId)
    assertTrue(decoded.inventories.containsKey("cao_minh"))
    assertTrue(decoded.equipment.containsKey("cao_minh"))
    assertFalse(decoded.characters.containsKey("kai"))
  }
}
