package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatCoreTest {
  private class ConstantRandom(private val value: Double) : CombatRandom {
    override fun nextDouble(): Double = value
  }

  @Test fun baseStatsMatchSurvivalCombatContract() {
    val stats = CombatStats()
    assertEquals(50, stats.maxHp)
    assertEquals(15, stats.defensePoints)
    assertEquals(0.10, stats.evasionChance, 0.000001)
    assertEquals(0.05, stats.criticalChance, 0.000001)
    assertEquals(10, CombatRules.finalDamage(12, 5))
    assertEquals(26, CombatRules.finalDamage(30, 5))
  }

  @Test fun agilityAndCritScaleFromBaseFiveAndAreCapped() {
    assertEquals(0.104, CombatRules.evasionChance(9), 0.000001)
    assertEquals(0.07, CombatRules.criticalChance(9), 0.000001)
    assertEquals(0.35, CombatRules.evasionChance(500), 0.000001)
    assertEquals(0.50, CombatRules.criticalChance(500), 0.000001)
  }

  @Test fun survivalCompletionUsesCharacterSpecificGrowth() {
    val before = CombatStats(currentHp = 31, survival = 9, survivalTarget = 10)
    val kai = CombatProgression.awardEntityKill("kai", before)
    assertEquals(9, kai.hpStat)
    assertEquals(9, kai.defend)
    assertEquals(9, kai.agi)
    assertEquals(9, kai.crit)
    assertEquals(35, kai.currentHp)
    assertEquals(54, kai.maxHp)
    assertEquals(0, kai.survival)
    assertEquals(15, kai.survivalTarget)

    assertEquals(3, CombatProgression.growthPerCompletion("iris"))
    assertEquals(3, CombatProgression.growthPerCompletion("syvial"))
    assertEquals(2, CombatProgression.growthPerCompletion("lucia"))
  }

  @Test fun differentEffectsCoexistWhileSameEffectRefreshesWithoutStacking() {
    var effects = emptyMap<CombatEffectType, CombatEffect>()
    effects = CombatEffects.apply(effects, CombatEffectType.BLEED, 3)
    effects = CombatEffects.apply(effects, CombatEffectType.POISON, 3)
    effects = CombatEffects.apply(effects, CombatEffectType.STUN, 1)
    assertEquals(3, effects.size)

    effects = effects + (CombatEffectType.BLEED to CombatEffect(CombatEffectType.BLEED, 1))
    effects = CombatEffects.apply(effects, CombatEffectType.BLEED, 3)
    assertEquals(3, effects.size)
    assertEquals(3, effects.getValue(CombatEffectType.BLEED).remainingTurns)
    assertEquals(3, effects.getValue(CombatEffectType.POISON).remainingTurns)

    val (stunned, afterStun) = CombatEffects.consumeStun(effects)
    assertTrue(stunned)
    assertFalse(afterStun.containsKey(CombatEffectType.STUN))

    val (afterDot, remaining) = CombatEffects.tickDamage(CombatStats(currentHp = 50), afterStun)
    assertEquals(45, afterDot.currentHp)
    assertEquals(2, remaining.getValue(CombatEffectType.BLEED).remainingTurns)
    assertEquals(2, remaining.getValue(CombatEffectType.POISON).remainingTurns)
  }

  @Test fun entityGrowthFollowsPlayerGrowthAndBackroomsLevel() {
    val kai = CombatStats(hpStat = 13, defend = 13, agi = 13, crit = 13, currentHp = 58)
    assertEquals(7, CombatProfiles.enemyGrowth(kai, 3))
    val hound = CombatProfiles.entity("ENTITY.HOUND", kai, 3)
    assertEquals(12, hound.stats.hpStat)
    assertEquals(57, hound.stats.maxHp)
    assertEquals(12, hound.stats.defend)
    assertEquals(11, hound.baseDamage)
  }

  @Test fun combatStartsWithKaiRotatesPartyAndQueuesEntitiesOneAtATime() {
    val party = listOf(
      CombatantState("lucia", "Lucia", false, CombatStats(), CombatProfiles.partyBaseDamage("lucia")),
      CombatantState("kai", "Kai", false, CombatStats(), CombatProfiles.partyBaseDamage("kai"))
    )
    val result = AutoTurnCombatEngine(ConstantRandom(0.99)).resolve(
      encounterId = "TURN_9",
      partyInput = party,
      entityIds = listOf("ENTITY.HOUND", "ENTITY.SMILER"),
      level = 0
    )

    assertEquals(CombatOutcome.VICTORY, result.outcome)
    assertEquals(listOf("ENTITY.HOUND", "ENTITY.SMILER"), result.defeatedEntities)
    val focuses = result.timeline.filter { it.kind == "FOCUS" }
    assertTrue(focuses.size >= 2)
    assertEquals("kai", focuses[0].actorId)
    assertEquals("lucia", focuses[1].actorId)
    assertEquals(2, result.party.first { it.id == "kai" }.stats.survival)
    assertEquals(2, result.party.first { it.id == "lucia" }.stats.survival)

    val houndDown = result.timeline.indexOfFirst { it.kind == "ENTITY_DOWN" && it.enemyId == "ENTITY.HOUND" }
    val smilerEnter = result.timeline.indexOfFirst { it.kind == "ENTITY_ENTER" && it.enemyId == "ENTITY.SMILER" }
    assertTrue(houndDown >= 0)
    assertTrue(smilerEnter > houndDown)
  }
}
