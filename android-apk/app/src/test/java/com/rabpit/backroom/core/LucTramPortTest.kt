package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LucTramPortTest {
  @Test fun backfillAllowsRecruitmentWithoutCreatingEquipmentOrJoiningAutomatically() {
    val state = LucTramFollower.ensure(GameState.initial())
    val character = state.characters.getValue("luc_tram")
    assertEquals("true", character.metadata["joinEligible"])
    assertFalse("luc_tram" in state.party.memberIds)
    assertTrue(state.equipment["luc_tram"]?.slots.orEmpty().isEmpty())
    assertEquals(7, CompanionSkillCatalog.forCharacter("luc_tram").size)
    val joined = PartyEngine.execute(state, PartyCommand(
      "recruit-luc-tram", state.turn.currentTurnId, KAI_ID, "luc_tram",
      CommandSource.SYSTEM, PartyCommand.Operation.ADD,
      consentConfirmed = true, targetPresent = true))
    assertTrue(joined.validation.reason, joined.applied)
    assertTrue("luc_tram" in joined.state.party.memberIds)
  }

  @Test fun followerPortPreservesUpgradesHpAndInventoryAcrossReload() {
    val initial = GameState.initial()
    val character = CharacterState("luc_tram", "Lục Trầm",
      statProfile = CharacterStatProfile(str = 8),
      vitalState = CharacterVitalState(currentHp = 17))
    val state = initial.copy(characters = initial.characters + (character.id to character),
      party = initial.party.copy(memberIds = listOf(KAI_ID, character.id)))
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    val follower = loaded.characters.getValue("luc_tram")
    assertEquals(50, follower.statProfile.baseMaxHp)
    assertEquals(8, follower.statProfile.str)
    assertEquals(listOf(5, 5, 5), listOf(follower.statProfile.def, follower.statProfile.skl, follower.statProfile.vit))
    assertEquals(17, follower.vitalState.currentHp)
    assertEquals("avatars/luctram_avatar.png", follower.avatarRef)
    assertEquals(24, CharacterStatEngine.weaponDamage(loaded, "luc_tram"))
    assertEquals(state.inventories, loaded.inventories.filterKeys { it in state.inventories })
    assertTrue("luc_tram" in loaded.party.memberIds)
    assertFalse("luc_tram_hac_hoa" in loaded.characters)
  }
}
