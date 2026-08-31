package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowerShortcutTest {
  @Test fun uploadedAvatarsAreLinkedToFollowerCharacters() {
    val state = GameState.initial()
    assertEquals("avatars/Iris_avatar.jpg", state.characters.getValue(IRIS_ID).avatarRef)
    assertEquals("avatars/Syvial_avatar.jpg", state.characters.getValue(SYVIAL_ID).avatarRef)
  }

  @Test fun exactSlashCodesResolveToTheRequestedFollowers() {
    assertEquals(IRIS_ID, SpecialFollowersCanon.matchesPartyCheatCode(" /iris123 "))
    assertEquals(SYVIAL_ID, SpecialFollowersCanon.matchesPartyCheatCode(" /Syv123 "))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/syv123"))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/iris12"))
  }

  @Test fun slashCodesAddFollowersImmediatelyAndIdempotently() {
    val base = GameState.initial()
    val (withIris, irisError) = SpecialFollowersCanon.forceIntoParty(base, IRIS_ID)
    assertNull(irisError)
    assertTrue(IRIS_ID in withIris.party.memberIds)

    val (withBoth, syvialError) = SpecialFollowersCanon.forceIntoParty(withIris, SYVIAL_ID)
    assertNull(syvialError)
    assertTrue(SYVIAL_ID in withBoth.party.memberIds)

    val (again, againError) = SpecialFollowersCanon.forceIntoParty(withBoth, SYVIAL_ID)
    assertNull(againError)
    assertEquals(withBoth.party.memberIds, again.party.memberIds)
  }

  @Test fun shortcutsNeverEvictAnExistingMemberWhenPartyIsFull() {
    val base = GameState.initial()
    val full = base.copy(
      characters = base.characters + mapOf(
        "a" to CharacterState("a", "A"),
        "b" to CharacterState("b", "B"),
        "c" to CharacterState("c", "C")
      ),
      party = PartyState(KAI_ID, listOf(KAI_ID, "a", "b", "c"), 4)
    )
    val (unchanged, error) = SpecialFollowersCanon.forceIntoParty(full, IRIS_ID)
    assertEquals("party_full", error)
    assertFalse(IRIS_ID in unchanged.party.memberIds)
    assertEquals(listOf(KAI_ID, "a", "b", "c"), unchanged.party.memberIds)
  }
}
