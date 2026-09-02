package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFollowerShortcutTest {
  @Test fun uploadedAvatarsRemainLinkedToStoryCharacters() {
    val state = GameState.initial()
    assertEquals("avatars/Iris_avatar.jpg", state.characters.getValue(IRIS_ID).avatarRef)
    assertEquals("avatars/Syvial_avatar.jpg", state.characters.getValue(SYVIAL_ID).avatarRef)
  }

  @Test fun retiredSlashCodesCannotBypassFixedReunionLevels() {
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/iris123"))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/Syv123"))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode(" /iris123 "))
  }

  @Test fun directLegacyForceHelperFailsClosedForStoryOwnedFollowers() {
    val base = GameState.initial()
    val (irisState, irisError) = SpecialFollowersCanon.forceIntoParty(base, IRIS_ID)
    assertEquals("story_owned", irisError)
    assertEquals(base.party.memberIds, irisState.party.memberIds)

    val (syvialState, syvialError) = SpecialFollowersCanon.forceIntoParty(base, SYVIAL_ID)
    assertEquals("story_owned", syvialError)
    assertEquals(base.party.memberIds, syvialState.party.memberIds)
  }
}
