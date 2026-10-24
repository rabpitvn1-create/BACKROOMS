package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowersTest {
  @Test fun lucTramAndSyvialAreSeededAsOptionalFollowersOutsideParty() {
    val state = GameState.initial()
    val lucTram = state.characters[LUC_TRAM_ID]!!
    val syvial = state.characters[SYVIAL_ID]!!

    assertEquals("follower", lucTram.metadata["npcType"])
    assertEquals("true", lucTram.metadata["joinEligible"])
    assertEquals("0.25%", lucTram.metadata["encounterChance"])
    assertEquals("0-6", lucTram.metadata["encounterLevels"])
    assertEquals("Chính Đạo kiếm tu / Bạch Hồng Kiếm Nữ", lucTram.metadata["role"])
    assertNull(lucTram.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.lucTramEquipmentSlots, state.equipment[LUC_TRAM_ID]!!.slots)

    assertEquals("follower", syvial.metadata["npcType"])
    assertEquals("true", syvial.metadata["joinEligible"])
    assertEquals("0.25%", syvial.metadata["encounterChance"])
    assertEquals("UR+", syvial.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.syvialEquipmentSlots, state.equipment[SYVIAL_ID]!!.slots)

    assertFalse(LUC_TRAM_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
    assertEquals("false", state.characters[AN_NHIEN_ID]!!.metadata["mandatoryEncounter"])
    assertEquals("0.25%", state.characters[AN_NHIEN_ID]!!.metadata["encounterChance"])
  }

  @Test fun decodeBackfillsSpecialFollowersWithoutForcingThemIntoParty() {
    val base = GameState.initial()
    val stripped = base.copy(
      characters = base.characters - LUC_TRAM_ID - SYVIAL_ID,
      inventories = base.inventories - LUC_TRAM_ID - SYVIAL_ID,
      equipment = base.equipment - LUC_TRAM_ID - SYVIAL_ID
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(stripped))
    assertTrue(LUC_TRAM_ID in decoded.characters)
    assertTrue(SYVIAL_ID in decoded.characters)
    assertTrue(LUC_TRAM_ID in decoded.inventories)
    assertTrue(SYVIAL_ID in decoded.inventories)
    assertFalse(LUC_TRAM_ID in decoded.party.memberIds)
    assertFalse(SYVIAL_ID in decoded.party.memberIds)
  }

  @Test fun caoMinhAndAllThreeSpecialFollowersFitExactlyInParty() {
    var state = GameState.initial()
    for ((index, id) in listOf(AN_NHIEN_ID, LUC_TRAM_ID, SYVIAL_ID).withIndex()) {
      val result = PartyEngine.execute(state, PartyCommand(
        commandId = "add-special-$index",
        turnId = state.turn.currentTurnId,
        actorId = CAO_MINH_ID,
        targetId = id,
        source = CommandSource.SYSTEM,
        operation = PartyCommand.Operation.ADD,
        consentConfirmed = true,
        targetPresent = true
      ))
      assertTrue(result.validation.reason ?: "failed to add $id", result.applied)
      state = result.state
    }
    assertEquals(4, state.party.memberIds.size)
    assertEquals(listOf(CAO_MINH_ID, AN_NHIEN_ID, LUC_TRAM_ID, SYVIAL_ID), state.party.memberIds)
  }
}
