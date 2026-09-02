package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterStatSchemaTest {
  @Test fun namedProfilesUseGameplayNormalizedBaselines() {
    val kai = CharacterStatProfiles.forId(KAI_ID)
    assertEquals(100, kai.baseMaxHp)
    assertEquals(EnergyMode.INFINITE, kai.energy.mode)
    assertEquals(4, kai.regen.amountPerCompletedTurn)
    assertEquals(82, kai.str)
    assertEquals(78, kai.df)
    assertEquals(92, kai.agi)
    assertEquals(95, kai.crit)
    assertEquals(StatSource.GAMEPLAY_NORMALIZED, kai.statSource)

    val iris = CharacterStatProfiles.forId("iris")
    assertEquals(listOf(58, 60, 84, 90), listOf(iris.str, iris.df, iris.agi, iris.crit))
    assertEquals(EnergyMode.INFINITE, iris.energy.mode)
    assertEquals(4, iris.regen.amountPerCompletedTurn)

    val syvial = CharacterStatProfiles.forId("syvial")
    assertEquals(listOf(94, 84, 96, 88), listOf(syvial.str, syvial.df, syvial.agi, syvial.crit))
    assertEquals(EnergyMode.INFINITE, syvial.energy.mode)
    assertEquals(4, syvial.regen.amountPerCompletedTurn)

    val anNhien = CharacterStatProfiles.forId("an-nhien")
    assertEquals(100, anNhien.baseMaxHp)
    assertEquals(EnergyMode.NOT_APPLICABLE, anNhien.energy.mode)
    assertFalse(anNhien.regen.enabled)
    assertEquals(0, anNhien.crit)
  }

  @Test fun unknownCharacterGetsSafeExtensibleFallback() {
    val character = CharacterState("future-character", "Future Character")
    assertEquals(100, character.statProfile.baseMaxHp)
    assertEquals(100, character.vitalState.currentHp)
    assertEquals(StatSource.GAMEPLAY_FALLBACK, character.statProfile.statSource)
    assertEquals(EnergyMode.NOT_APPLICABLE, character.statProfile.energy.mode)
    assertFalse(character.statProfile.regen.enabled)
  }

  @Test fun codecPersistsAuthoritativeProfileAndVitals() {
    val custom = CharacterState(
      id = KAI_ID,
      name = "Kai Akechi",
      statProfile = CharacterStatProfiles.forId(KAI_ID),
      vitalState = CharacterVitalState(63, CharacterCondition.WOUNDED, "TURN_12")
    )
    val state = GameState.initial().copy(characters = GameState.initial().characters + (KAI_ID to custom))
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    val kai = decoded.characters.getValue(KAI_ID)
    assertEquals(custom.statProfile, kai.statProfile)
    assertEquals(custom.vitalState, kai.vitalState)
  }

  @Test fun currentSaveWithoutNewFieldsBackfillsProfileWithoutBreakingLoad() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial()))
    val kai = root.getJSONObject("characters").getJSONObject(KAI_ID)
    kai.remove("statProfile")
    kai.remove("vitalState")
    val decoded = GameStateCodec.decode(root.toString()).characters.getValue(KAI_ID)
    assertEquals(CharacterStatProfiles.forId(KAI_ID), decoded.statProfile)
    assertEquals(100, decoded.vitalState.currentHp)
  }
}
