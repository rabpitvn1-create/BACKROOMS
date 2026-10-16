package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CombatAccuracyTest {
  @Test fun bothDexAndAgiIncreaseAccuracyInBasisPoints() {
    assertEquals(5800, CharacterStatCore.accuracyBasisPoints(5, 5))
    assertEquals(5850, CharacterStatCore.accuracyBasisPoints(6, 5))
    assertEquals(5850, CharacterStatCore.accuracyBasisPoints(5, 6))
    assertEquals(6300, CharacterStatCore.accuracyBasisPoints(10, 10))
    assertEquals(9500, CharacterStatCore.accuracyBasisPoints(999, 999))
    assertEquals(5800, CharacterStatCore.accuracyBasisPoints(-10, 0))
  }

  @Test fun tacticalAccuracyUsesTheSameDerivedStatAndRespectsCaps() {
    assertEquals(5800, CharacterStatCore.combatAccuracyBasisPoints(5, 5, 0, 0, 0, 0))
    assertEquals(8100, CharacterStatCore.combatAccuracyBasisPoints(10, 10, 18, 0, 0, 0))
    assertEquals(7000, CharacterStatCore.combatAccuracyBasisPoints(10, 10, -5, 1, 0, 1))
    assertEquals(2000, CharacterStatCore.combatAccuracyBasisPoints(5, 5, -200, 0, 0, 0))
    assertEquals(9900, CharacterStatCore.combatAccuracyBasisPoints(999, 999, 18, 3, 3, 7))
  }

  @Test fun higherDexActuallyTurnsCombatMissesIntoHitsWithTheSameSeeds() {
    var improvedHits = 0
    repeat(64) { sample ->
      val baseline = GameState.initial().let { state ->
        state.copy(time = state.time.copy(elapsedSubjectiveMinutes = sample.toLong()))
      }
      val leader = baseline.characters.getValue(CAO_MINH_ID)
      val higherDex = baseline.copy(characters = baseline.characters +
        (CAO_MINH_ID to leader.copy(statProfile = leader.statProfile.copy(dex = 250))))
      fun firstBasicHit(state: GameState): Boolean {
        val started = CombatRuntime.start(state, "hound")
        val result = CombatRuntime.resolve(started, "EXECUTE", "Đánh Hound bằng Magnum")
        val first = result.feedback.firstOrNull {
          it.actorId == CAO_MINH_ID && it.phase == "actor" &&
            (it.outcome == "hit" || it.outcome == "miss")
        }
        assertNotNull("Cao Minh's basic attack must produce hit/miss feedback", first)
        return first!!.outcome == "hit"
      }
      val oldHit = firstBasicHit(baseline)
      val boostedHit = firstBasicHit(higherDex)
      assertFalse("DEX must never reduce hit probability for the same seed", oldHit && !boostedHit)
      if (!oldHit && boostedHit) improvedHits++
    }
    assertTrue("DEX must change actual CombatRuntime hit outcomes", improvedHits > 0)
  }

  @Test fun effectiveEquipmentAndPassiveAffectAccuracyWithoutChangingPrimarySchema() {
    val state = GameState.initial()
    val actor = state.characters.getValue(CAO_MINH_ID)
    val baseline = CharacterStatCore.effective(state, CAO_MINH_ID)
    val stronger = state.copy(characters = state.characters + (
      CAO_MINH_ID to actor.copy(statProfile = actor.statProfile.copy(dex = 6, agi = 6))))
    val upgraded = CharacterStatCore.effective(stronger, CAO_MINH_ID)
    assertEquals(CharacterStatCore.accuracyBasisPoints(baseline.dex, baseline.agi), baseline.accuracyBasisPoints)
    assertTrue(upgraded.accuracyBasisPoints > baseline.accuracyBasisPoints)
    val detail = CharacterDetailJson.encodeCharacter(
      CharacterDetailProjector.projectCharacter(stronger, CAO_MINH_ID)!!)
    assertEquals(upgraded.accuracyBasisPoints / 100.0,
      detail.getJSONObject("combatStatus").getDouble("accuracyPercent"), 0.001)
    assertEquals(setOf("STR", "DEX", "AGI", "VIT", "EVA"),
      detail.getJSONObject("stats").keys().asSequence().toSet())
  }
}
