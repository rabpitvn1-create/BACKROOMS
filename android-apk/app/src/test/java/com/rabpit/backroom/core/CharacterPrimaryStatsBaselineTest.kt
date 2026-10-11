package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterPrimaryStatsBaselineTest {
  @Test fun allCharactersBeginWithFivePrimaryStatsAtFive() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    assertTrue(state.characters.isNotEmpty())
    state.characters.values.forEach { c ->
      val p = c.statProfile
      assertEquals(c.id, listOf(5, 5, 5, 5, 5), listOf(p.str, p.dex, p.agi, p.vit, p.eva))
      assertEquals(c.id, 50, p.baseMaxHp)
    }
  }

  @Test fun newPrimaryStatsPersistForEachCharacterIndependently() {
    val original = GameState.initial()
    val id = original.characters.keys.first()
    val character = original.characters.getValue(id)
    val changed = original.copy(characters = original.characters + (id to character.copy(
      statProfile = character.statProfile.copy(dex = 8, agi = 12, eva = 16)
    )))
    val decoded = GameStateCodec.decode(GameStateCodec.encode(changed))
    val p = decoded.characters.getValue(id).statProfile

    assertEquals(listOf(8, 12, 16), listOf(p.dex, p.agi, p.eva))
    decoded.characters.filterKeys { it != id }.values.forEach { other ->
      assertEquals(listOf(5, 5, 5), listOf(other.statProfile.dex, other.statProfile.agi, other.statProfile.eva))
    }
  }

  @Test fun normalizationClampsPrimaryStatsToCurrentValidRange() {
    val state = GameState.initial()
    val id = state.characters.keys.first()
    val character = state.characters.getValue(id)
    val invalid = state.copy(characters = state.characters + (id to character.copy(
      statProfile = character.statProfile.copy(dex = -10, agi = 1000, eva = 0)
    )))
    val p = CharacterProgressionCore.normalize(invalid).characters.getValue(id).statProfile
    assertEquals(listOf(5, 999, 5), listOf(p.dex, p.agi, p.eva))
  }
}
