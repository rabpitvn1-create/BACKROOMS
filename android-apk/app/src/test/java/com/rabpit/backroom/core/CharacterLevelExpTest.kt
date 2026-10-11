package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CharacterLevelExpTest {
  private fun primary(p: CharacterStatProfile): List<Int> = listOf(p.str, p.dex, p.agi, p.vit, p.eva)

  @Test fun freshPartyHasIndependentLevelZeroWithTenExpRequired() {
    val state = GameState.initial()
    state.characters.values.forEach { c ->
      assertEquals(0, c.progression.level)
      assertEquals(0, c.progression.exp)
      assertEquals(listOf(5, 5, 5, 5, 5), primary(c.statProfile))
    }
    assertEquals(10, CharacterProgressionCore.expRequired(0))
    assertEquals(18, CharacterProgressionCore.expRequired(1))
  }

  @Test fun oneLevelDistributesExactlyFiveGuaranteedAndFiveRandomPoints() {
    val state = GameState.initial()
    val awarded = CharacterProgressionCore.grantExp(state, KAI_ID, 10, Random(17))
    val before = primary(state.characters.getValue(KAI_ID).statProfile)
    val after = primary(awarded.state.characters.getValue(KAI_ID).statProfile)
    val increases = after.zip(before).map { (a, b) -> a - b }
    assertEquals(1, awarded.levelsGained)
    assertEquals(1, awarded.state.characters.getValue(KAI_ID).progression.level)
    assertEquals(0, awarded.state.characters.getValue(KAI_ID).progression.exp)
    assertEquals(10, increases.sum())
    assertTrue(increases.all { it in 1..6 })
    assertEquals(5, increases.sumOf { it - 1 })
    assertEquals(primary(CharacterStatProfiles.forId(IRIS_ID)), primary(awarded.state.characters.getValue(IRIS_ID).statProfile))
    val replayed = CharacterProgressionCore.grantExp(state, KAI_ID, 10, Random(17))
    assertEquals(awarded.state.characters.getValue(KAI_ID), replayed.state.characters.getValue(KAI_ID))
  }

  @Test fun expOverflowCanAdvanceMultipleLevelsAndKeepsRemainder() {
    val award = CharacterProgressionCore.grantExp(GameState.initial(), KAI_ID, 31, Random(22))
    assertEquals(2, award.levelsGained)
    assertEquals(CharacterProgression(2, 3), award.state.characters.getValue(KAI_ID).progression)
    assertEquals(45, primary(award.state.characters.getValue(KAI_ID).statProfile).sum())
  }

  @Test fun saveLoadKeepsCharacterExpAndRandomStatsWithoutReroll() {
    val advanced = CharacterProgressionCore.grantExp(GameState.initial(), KAI_ID, 33, Random(9)).state
    val result = GameStateCodec.decode(GameStateCodec.encode(advanced))
    val before = advanced.characters.getValue(KAI_ID)
    val after = result.characters.getValue(KAI_ID)
    assertEquals(before.progression, after.progression)
    assertEquals(before.statProfile, after.statProfile)
    val encoded = JSONObject(GameStateCodec.encode(result))
      .getJSONObject("characters").getJSONObject(KAI_ID).getJSONObject("progression")
    assertEquals(2, encoded.getInt("level"))
    assertEquals(5, encoded.getInt("exp"))
    val detail = CharacterDetailJson.encodeCharacter(CharacterDetailProjector.projectCharacter(result, KAI_ID)!!)
    assertEquals(2, detail.getInt("level"))
    assertEquals(5, detail.getInt("exp"))
    assertEquals(26, detail.getInt("expToNextLevel"))
  }

  @Test fun deadCharactersDoNotReviveWhenLeveling() {
    val state = CharacterStatEngine.setCurrentHp(GameState.initial(), KAI_ID, 0)
    val after = CharacterProgressionCore.grantExp(state, KAI_ID, 10, Random(7)).state
    assertEquals(0, after.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun rejectsInvalidGrantsAndStopsAtSafeCap() {
    try {
      CharacterProgressionCore.grantExp(GameState.initial(), KAI_ID, -1)
      fail("negative EXP should fail")
    } catch (_: IllegalArgumentException) {}
    try {
      CharacterProgressionCore.grantExp(GameState.initial(), "missing", 10)
      fail("missing Character should fail")
    } catch (_: IllegalArgumentException) {}
    val initial = GameState.initial()
    val c = initial.characters.getValue(KAI_ID)
    val capped = initial.copy(characters = initial.characters + (KAI_ID to c.copy(
      progression = CharacterProgression(CharacterProgressionCore.MAX_LEVEL, 0)
    )))
    val noOp = CharacterProgressionCore.grantExp(capped, KAI_ID, Int.MAX_VALUE, Random(11))
    assertEquals(0, noOp.levelsGained)
    assertEquals(0, noOp.awardedExp)
    assertEquals(CharacterProgression(CharacterProgressionCore.MAX_LEVEL, 0),
      noOp.state.characters.getValue(KAI_ID).progression)
  }
}
