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
    assertEquals(CharacterProgression(0, 8), result.state.characters.getValue(KAI_ID).progression)
    assertTrue(result.reply.contains("+8 EXP"))
  }

  @Test fun rewardsUseEncounterRankNotSubsequentWorldLevel() {
    val started = CombatRuntime.start(levelState(2), "hound")
    val changed = started.copy(world = levelState(6).world)
    val result = defeated(changed)
    assertEquals(6, result.state.characters.getValue(KAI_ID).progression.exp)
  }

  @Test fun defeatEachWaveAndRepeatedEntityKeysPayExactlyOnce() {
    val started = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "hound", "clump"))
    val first = defeated(started)
    assertFalse(first.entityDestroyed)
    assertEquals(2, first.state.characters.getValue(KAI_ID).progression.exp)
    val second = defeated(first.state)
    assertFalse(second.entityDestroyed)
    assertEquals(4, second.state.characters.getValue(KAI_ID).progression.exp)
    val third = defeated(second.state)
    assertTrue(third.entityDestroyed)
    assertEquals(6, third.state.characters.getValue(KAI_ID).progression.exp)
    assertEquals(3, third.state.metadata.keys.count { it.startsWith("exp.awarded.") })
    assertEquals(3, third.state.metadata.keys.count { it.startsWith("loot.awarded.") })
  }

  @Test fun afterFiveKillsCharacterLevelsUpWithTenStatPoints() {
    var state = GameState.initial()
    repeat(5) {
      state = defeated(CombatRuntime.start(state, "hound")).state
    }
    val c = state.characters.getValue(KAI_ID)
    assertEquals(CharacterProgression(1, 0), c.progression)
    assertEquals(35, c.statProfile.let { it.str + it.dex + it.agi + it.vit + it.eva })
    assertEquals(5, state.metadata.keys.count { it.startsWith("exp.awarded.") })
  }

  @Test fun activeLivingCompanionsReceiveFullRewardButFallenDoNot() {
    val started = GameState.initial()
    val party = started.copy(party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID)))
    val fallen = CharacterStatEngine.setCurrentHp(party, SYVIAL_ID, 0)
    val result = defeated(CombatRuntime.start(fallen, "hound"))
    assertEquals(2, result.state.characters.getValue(KAI_ID).progression.exp)
    assertEquals(2, result.state.characters.getValue(IRIS_ID).progression.exp)
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
    assertEquals(2, loaded.characters.getValue(KAI_ID).progression.exp)
    val different = EntityExpRewards.award(loaded, "encounter:wave:1", 0L)
    assertEquals(4, different.state.characters.getValue(KAI_ID).progression.exp)
  }

  @Test fun noExpIsGrantedForUnresolvedEnemyOrEscape() {
    val started = CombatRuntime.start(GameState.initial(), "hound")
    val look = CombatRuntime.resolve(started, "SEARCH", "quan sát")
    assertFalse(look.entityDestroyed)
    assertEquals(CharacterProgression(), look.state.characters.getValue(KAI_ID).progression)
    assertFalse(look.state.metadata.keys.any { it.startsWith("exp.awarded.") })
  }
}
