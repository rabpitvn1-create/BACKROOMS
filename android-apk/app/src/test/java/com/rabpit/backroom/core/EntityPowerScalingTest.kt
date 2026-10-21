package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EntityPowerScalingTest {
  private fun levelState(level: Int): GameState {
    val initial = GameState.initial()
    val player = initial.characters.getValue(CAO_MINH_ID)
    return initial.copy(characters = initial.characters +
      (CAO_MINH_ID to player.copy(progression = CharacterProgression(level))))
  }

  @Test fun onlyCompletedFiveLevelStepsIncreasePower() {
    for (level in 0..CharacterProgressionCore.MAX_LEVEL) {
      assertEquals((level / 5) * 1_000_000L, EntityPowerScaling.rankFor(levelState(level)))
    }
    assertEquals(1000, EntityPowerScaling.scale(1000, EntityPowerScaling.rankFor(levelState(4))))
    assertEquals(1100, EntityPowerScaling.scale(1000, EntityPowerScaling.rankFor(levelState(5))))
    assertEquals(1100, EntityPowerScaling.scale(1000, EntityPowerScaling.rankFor(levelState(9))))
    assertEquals(1200, EntityPowerScaling.scale(1000, EntityPowerScaling.rankFor(levelState(10))))
    assertEquals(1300, EntityPowerScaling.scale(1000, EntityPowerScaling.rankFor(levelState(15))))
  }

  @Test fun worldLevelAndFollowerLevelsCannotChangePower() {
    val initial = levelState(4)
    for (world in 0..6) assertEquals(0L, EntityPowerScaling.rankFor(initial.copy(
      world = initial.world + ("levelJson" to JSONObject().put("number", world).toString()))))
    val follower = initial.characters.getValue(IRIS_ID)
    assertEquals(0L, EntityPowerScaling.rankFor(initial.copy(characters = initial.characters +
      (IRIS_ID to follower.copy(progression = CharacterProgression(50))))))
  }

  @Test fun linearFullLevelsAreOneHundredTenPercentagePointsPerLevel() {
    val base = 1_000
    assertEquals(1_000, EntityPowerScaling.scale(base, 0L))
    assertEquals(1_100, EntityPowerScaling.scale(base, 1_000_000L))
    assertEquals(1_200, EntityPowerScaling.scale(base, 2_000_000L))
    assertEquals(1_300, EntityPowerScaling.scale(base, 3_000_000L))
  }

  @Test fun fractionalRanksKeepFixedPointPrecision() {
    assertEquals(1_050, EntityPowerScaling.scale(1_000, 500_000L))
    assertEquals(1_105, EntityPowerScaling.scale(1_000, 1_050_000L))
    assertEquals(1_150, EntityPowerScaling.scale(1_000, 1_500_000L))
  }

  @Test fun ordinaryEntitiesDoubleFinalScaledHpAtEveryCharacterTier() {
    val hound0 = CombatRuntime.active(CombatRuntime.start(levelState(0), "hound"))!!
    val hound2 = CombatRuntime.active(CombatRuntime.start(levelState(10), "hound"))!!
    assertEquals(220, hound0.entityMaxHp)
    assertEquals(264, hound2.entityMaxHp)
    assertEquals(0L, hound0.progressionRank)
    assertEquals(2_000_000L, hound2.progressionRank)
  }

  @Test fun whistlers926IsExactlyTwiceHoundCombatHpAtEveryLevel() {
    assertEquals("Whistlers", CombatRuntime.entityName("926"))
    assertEquals(true, CombatRuntime.supportsEntityKey("926"))
    for (level in listOf(0, 4, 5, 9, 10, 15, 30)) {
      val hound = CombatRuntime.active(CombatRuntime.start(levelState(level), "hound"))!!
      val whistlers = CombatRuntime.active(CombatRuntime.start(levelState(level), "926"))!!
      assertEquals("926", whistlers.entityKey)
      assertEquals(hound.entityMaxHp * 2, whistlers.entityMaxHp)
      assertEquals(whistlers.entityMaxHp, whistlers.entityHp)
    }
  }

  @Test fun entity927HasExactlyTwice926CombatHpAtEveryLevel() {
    assertEquals("Entity 927", CombatRuntime.entityName("927"))
    assertEquals(true, CombatRuntime.supportsEntityKey("927"))
    for (level in listOf(0, 4, 5, 9, 10, 15, 30)) {
      val elder = CombatRuntime.active(CombatRuntime.start(levelState(level), "926"))!!
      val creature = CombatRuntime.active(CombatRuntime.start(levelState(level), "927"))!!
      assertEquals("927", creature.entityKey)
      assertEquals(elder.entityMaxHp * 2, creature.entityMaxHp)
      assertEquals(creature.entityMaxHp, creature.entityHp)
    }
    assertEquals(880, CombatRuntime.active(CombatRuntime.start(levelState(0), "927"))!!.entityMaxHp)
  }

  @Test fun lucTramEnemyFollowsBossHpScalingAtAllLevels() {
    for (level in listOf(0, 4, 5, 9, 10, 15, 30)) {
      val boss = CombatRuntime.active(CombatRuntime.start(levelState(level), "diep_minh"))!!
      val enemy = CombatRuntime.active(CombatRuntime.start(levelState(level), "luc_tram_hac_hoa"))!!
      assertEquals(boss.entityMaxHp, enemy.entityMaxHp)
    }
  }

  @Test fun activeEncounterKeepsItsRankSnapshotWhenCharacterLevelChanges() {
    val started = CombatRuntime.start(levelState(10), "hound")
    val before = CombatRuntime.active(started)!!
    val movedWorld = started.copy(characters = levelState(30).characters)
    val after = CombatRuntime.active(movedWorld)!!
    assertEquals(2_000_000L, before.progressionRank)
    assertEquals(before.progressionRank, after.progressionRank)
    assertEquals(before.entityMaxHp, after.entityMaxHp)
  }

  @Test fun diepMinhUsesTheSameLinearHpScaleWithoutChangingLevelZeroBossBalance() {
    val boss0 = CombatRuntime.active(CombatRuntime.start(levelState(0), "diep_minh"))!!
    val boss1 = CombatRuntime.active(CombatRuntime.start(levelState(5), "diep_minh"))!!
    assertEquals(2999, boss0.entityMaxHp)
    assertEquals(3299, boss1.entityMaxHp)
  }

  @Test fun levelUpAcrossThresholdScalesTheNextWaveButDoesNotIncreaseExp() {
    var state = CombatRuntime.startWaves(levelState(4), listOf("Brownies", "hound"))
    assertEquals(0L, CombatRuntime.active(state)!!.progressionRank)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "0"))
    val next = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    assertTrue(next.state.characters.getValue(CAO_MINH_ID).progression.level >= 5)
    assertEquals(1_000_000L, CombatRuntime.active(next.state)!!.progressionRank)
    assertEquals(242, CombatRuntime.active(next.state)!!.entityMaxHp)
    val defeated = CombatRuntime.resolve(next.state.copy(metadata = next.state.metadata +
      ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    assertTrue(defeated.reply.contains("+4 EXP"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun invalidCharacterLevelFailsClosed() {
    EntityPowerScaling.rankForCharacterLevel(-1)
  }
}
