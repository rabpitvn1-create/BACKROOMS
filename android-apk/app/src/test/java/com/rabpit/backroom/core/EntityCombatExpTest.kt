package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EntityCombatExpTest {
  private fun levelState(level: Int): GameState = GameState.initial().copy(
    world = mapOf("levelJson" to JSONObject().put("number", level).put("name", "Level $level").toString())
  )

  private fun defeated(state: GameState): CombatRuntime.Resolution =
    CombatRuntime.resolve(state.copy(metadata = state.metadata + ("combat.entityHp" to "0")),
      "SEARCH", "quan sát")

  @Test fun everyWorldLevelGrantsIncreasingExpPerEntity() {
    assertEquals(listOf(2, 4, 6, 8, 10, 12, 14), (0..6).map {
      EntityExpRewards.expForRank(EntityPowerScaling.rankForLevel(it))
    })
    val result = defeated(CombatRuntime.start(levelState(3), "hound"))
    assertTrue(result.entityDestroyed)
    assertEquals(CharacterProgression(1, 6), result.state.characters.getValue(CAO_MINH_ID).progression)
    assertTrue(result.reply.contains("+16 EXP"))
  }

  @Test fun diepMinhKeepsTwentyFourTimesOriginalBaseWhileOrdinaryEntityExpDoubles() {
    val boss = defeated(CombatRuntime.start(levelState(0), "diep_minh"))
    val ordinary = defeated(CombatRuntime.start(levelState(0), "hound"))
    assertTrue(boss.entityDestroyed)
    assertTrue(boss.state.metadata.filterKeys { it.startsWith("exp.awarded.") }.values.contains("48"))
    assertTrue(boss.reply.contains("+48 EXP"))
    assertTrue(ordinary.reply.contains("+4 EXP"))
    assertTrue(ordinary.state.metadata.filterKeys { it.startsWith("exp.awarded.") }.values.contains("4"))
    assertEquals(1, boss.state.metadata.keys.count { it.startsWith("exp.awarded.") })
  }

  @Test fun luckyBearAlwaysPaysExactly200ExpOnlyAfterConfirmedDefeat() {
    for (level in listOf(0, 3, 6)) {
      val initial = levelState(level)
      val party = initial.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID)))
      val fallen = CharacterStatEngine.setCurrentHp(party, SYVIAL_ID, 0)
      val started = CombatRuntime.start(fallen, "LuckyBear")
      val look = CombatRuntime.resolve(started, "SEARCH", "quan sát")
      assertFalse(look.entityDestroyed)
      assertFalse(look.state.metadata.keys.any { it.startsWith("exp.awarded.") })
      val completed = defeated(started)
      assertTrue(completed.entityDestroyed)
      assertTrue(completed.reply.contains("+200 EXP"))
      assertEquals(setOf("200"), completed.state.metadata.filterKeys { it.startsWith("exp.awarded.") }.values.toSet())
      assertTrue(completed.state.characters.getValue(IRIS_ID).progression != party.characters.getValue(IRIS_ID).progression)
      assertEquals(party.characters.getValue(SYVIAL_ID).progression, completed.state.characters.getValue(SYVIAL_ID).progression)
    }
    val original = EntityExpRewards.award(GameState.initial(), "lucky:repeat", 0L, fixedExp = 200)
    val persisted = GameStateCodec.decode(GameStateCodec.encode(original.state))
    val repeated = EntityExpRewards.award(persisted, "lucky:repeat", 0L, fixedExp = 200)
    assertFalse(repeated.newlyAwarded)
    assertEquals(persisted, repeated.state)
  }

  @Test fun fractionalWorldRankDoesNotAdvanceFullLevelExpReward() {
    assertEquals(4, EntityExpRewards.expForRank(1_500_000L))
    try {
      EntityExpRewards.expForRank(-1L)
      fail("invalid negative progression rank must not award EXP")
    } catch (_: IllegalArgumentException) {}
  }

  @Test fun rewardsUseEncounterRankNotSubsequentWorldLevel() {
    val started = CombatRuntime.start(levelState(2), "hound")
    val changed = started.copy(world = levelState(6).world)
    val result = defeated(changed)
    assertEquals(CharacterProgression(1, 2), result.state.characters.getValue(CAO_MINH_ID).progression)
    assertTrue(result.state.metadata.filterKeys { it.startsWith("exp.awarded.") }.values.contains("12"))
  }

  @Test fun defeatEachWaveAndRepeatedEntityKeysPayExactlyOnce() {
    val started = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "hound", "clump"))
    val first = defeated(started)
    assertFalse(first.entityDestroyed)
    assertEquals(4, first.state.characters.getValue(CAO_MINH_ID).progression.exp)
    val second = defeated(first.state)
    assertFalse(second.entityDestroyed)
    assertEquals(8, second.state.characters.getValue(CAO_MINH_ID).progression.exp)
    val third = defeated(second.state)
    assertTrue(third.entityDestroyed)
    assertEquals(CharacterProgression(1, 2), third.state.characters.getValue(CAO_MINH_ID).progression)
    assertEquals(3, third.state.metadata.keys.count { it.startsWith("exp.awarded.") })
    assertEquals(3, third.state.metadata.keys.count { it.startsWith("loot.awarded.") })
  }

  @Test fun afterFiveKillsCharacterLevelsUpWithTenStatPoints() {
    var state = GameState.initial()
    repeat(5) {
      state = defeated(CombatRuntime.start(state, "hound")).state
    }
    val c = state.characters.getValue(CAO_MINH_ID)
    assertEquals(CharacterProgression(1, 10), c.progression)
    assertEquals(35, c.statProfile.let { it.str + it.dex + it.agi + it.vit + it.eva })
    assertEquals(5, state.metadata.keys.count { it.startsWith("exp.awarded.") })
  }

  @Test fun activeLivingCompanionsReceiveFullRewardButFallenDoNot() {
    val started = GameState.initial()
    val party = started.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID)))
    val fallen = CharacterStatEngine.setCurrentHp(party, SYVIAL_ID, 0)
    val result = defeated(CombatRuntime.start(fallen, "hound"))
    assertEquals(4, result.state.characters.getValue(CAO_MINH_ID).progression.exp)
    assertEquals(4, result.state.characters.getValue(IRIS_ID).progression.exp)
    assertEquals(0, result.state.characters.getValue(SYVIAL_ID).progression.exp)
    assertEquals(0, result.state.characters.getValue(AN_NHIEN_ID).progression.exp)
  }

  @Test fun savedRewardMarkerPreventsReplayWithoutRerollingStats() {
    val first = EntityExpRewards.award(GameState.initial(), "encounter:wave:0", 0L)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(first.state))
    val second = EntityExpRewards.award(loaded, "encounter:wave:0", 0L)
    assertFalse(second.newlyAwarded)
    assertEquals(0, second.expPerCharacter)
    assertEquals(loaded, second.state)
    assertEquals(2, loaded.characters.getValue(CAO_MINH_ID).progression.exp)
    val different = EntityExpRewards.award(loaded, "encounter:wave:1", 0L)
    assertEquals(4, different.state.characters.getValue(CAO_MINH_ID).progression.exp)
  }

  @Test fun noExpIsGrantedForUnresolvedEnemyOrEscape() {
    val started = CombatRuntime.start(GameState.initial(), "hound")
    val look = CombatRuntime.resolve(started, "SEARCH", "quan sát")
    assertFalse(look.entityDestroyed)
    assertEquals(CharacterProgression(), look.state.characters.getValue(CAO_MINH_ID).progression)
    assertFalse(look.state.metadata.keys.any { it.startsWith("exp.awarded.") })
  }
}
