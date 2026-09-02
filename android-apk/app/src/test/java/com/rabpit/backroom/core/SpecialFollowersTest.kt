package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowersTest {
  @Test fun irisAndSyvialAreSeededAsOptionalFollowersOutsideParty() {
    val state = GameState.initial()
    val iris = state.characters[IRIS_ID]!!
    val syvial = state.characters[SYVIAL_ID]!!

    assertEquals("follower", iris.metadata["npcType"])
    assertEquals("true", iris.metadata["joinEligible"])
    assertEquals("0%", iris.metadata["encounterChance"])
    assertEquals("STORY_ONLY", iris.metadata["encounterLevels"])
    assertEquals(CharacterPresence.SEPARATED, iris.presence)
    assertEquals("94", iris.metadata["fixedEncounterLevel"])
    assertEquals("Scout / Target Eliminator", iris.metadata["role"])
    assertNull(iris.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.irisEquipmentSlots, state.equipment[IRIS_ID]!!.slots)

    assertEquals("follower", syvial.metadata["npcType"])
    assertEquals("true", syvial.metadata["joinEligible"])
    assertEquals("UR+", syvial.metadata["combatTier"])
    assertEquals("0%", syvial.metadata["encounterChance"])
    assertEquals("STORY_ONLY", syvial.metadata["encounterLevels"])
    assertEquals(CharacterPresence.SEPARATED, syvial.presence)
    assertEquals("37", syvial.metadata["fixedEncounterLevel"])
    assertEquals(SpecialFollowersCanon.syvialEquipmentSlots, state.equipment[SYVIAL_ID]!!.slots)

    assertFalse(IRIS_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
    assertEquals("false", state.characters[AN_NHIEN_ID]!!.metadata["mandatoryEncounter"])
    assertEquals("0.25%", state.characters[AN_NHIEN_ID]!!.metadata["encounterChance"])
  }

  @Test fun decodeBackfillsSpecialFollowersWithoutForcingThemIntoParty() {
    val base = GameState.initial()
    val stripped = base.copy(
      characters = base.characters - IRIS_ID - SYVIAL_ID,
      inventories = base.inventories - IRIS_ID - SYVIAL_ID,
      equipment = base.equipment - IRIS_ID - SYVIAL_ID
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(stripped))
    assertTrue(IRIS_ID in decoded.characters)
    assertTrue(SYVIAL_ID in decoded.characters)
    assertTrue(IRIS_ID in decoded.inventories)
    assertTrue(SYVIAL_ID in decoded.inventories)
    assertFalse(IRIS_ID in decoded.party.memberIds)
    assertFalse(SYVIAL_ID in decoded.party.memberIds)
  }

  @Test fun kaiAndAllThreeSpecialFollowersFitExactlyInParty() {
    var state = GameState.initial()
    for ((index, id) in listOf(AN_NHIEN_ID, IRIS_ID, SYVIAL_ID).withIndex()) {
      val result = PartyEngine.execute(state, PartyCommand(
        commandId = "add-special-$index",
        turnId = state.turn.currentTurnId,
        actorId = KAI_ID,
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
    assertEquals(listOf(KAI_ID, AN_NHIEN_ID, IRIS_ID, SYVIAL_ID), state.party.memberIds)
  }
}
