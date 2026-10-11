package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterFiveStatCombatMathTest {
  @Test fun fiveStatsDeriveTheirOnlyIntendedCombatAttributes() {
    val base = CharacterStatProfiles.forId("iris")
    val original = GameState.initial()
    val id = original.characters.keys.first()
    val actor = original.characters.getValue(id)
    val state = original.copy(characters = original.characters + (id to actor.copy(
      statProfile = base.copy(str = 6, dex = 6, agi = 6, vit = 6, eva = 6)
    )))
    // Use a non-Cao-Minh id for testing values without Cao Minh's 10% passive.
    val sampled = CharacterStatCore.effective(state, id)
    val extraPassive = if (CharacterStatCore.isCaoMinh(id)) 1 else 0
    val stat = 6 + extraPassive
    assertEquals(50 + 5 * (stat - 5), sampled.maxHp)
    assertEquals(stat - 5, sampled.def)
    assertEquals(100 + 4 * (stat - 5), sampled.attackPercent)
    assertEquals(sampled.attackPercent, sampled.skillDamagePercent)
    assertEquals(CharacterStatCore.criticalChanceBasisPoints(stat), sampled.criticalChanceBasisPoints)
    assertEquals(CharacterStatCore.criticalResistanceBasisPoints(stat), sampled.resCriticalBasisPoints)
    assertEquals(CharacterStatCore.evasionBasisPoints(stat), sampled.evasionBasisPoints)
    assertEquals(CharacterStatCore.evasionResistanceBasisPoints(stat), sampled.resEvasionBasisPoints)
  }

  @Test fun fractionalChancesAndCapsRetainBasisPointPrecision() {
    assertEquals(500, CharacterStatCore.criticalChanceBasisPoints(5))
    assertEquals(545, CharacterStatCore.criticalChanceBasisPoints(6))
    assertEquals(3000, CharacterStatCore.criticalChanceBasisPoints(999))
    assertEquals(500, CharacterStatCore.evasionBasisPoints(5))
    assertEquals(550, CharacterStatCore.evasionBasisPoints(6))
    assertEquals(3500, CharacterStatCore.evasionBasisPoints(999))
    assertEquals(25, CharacterStatCore.criticalResistanceBasisPoints(6))
    assertEquals(25, CharacterStatCore.evasionResistanceBasisPoints(6))
    assertEquals(475, CharacterStatCore.effectiveChanceBasisPoints(500, 25))
    assertEquals(0, CharacterStatCore.effectiveChanceBasisPoints(500, 10000))
  }

  @Test fun strIncreasesAttackAndSkillDamageExactlyOnce() {
    assertEquals(104, CharacterStatCore.basicDamage(100, 6))
    assertEquals(156, CharacterStatCore.skillDamage(100, 150, 6))
    assertEquals(400, CharacterStatCore.criticalDamage(100))
    assertEquals(50, CharacterStatCore.defendedIncomingDamage(100, 100))
  }

  @Test fun characterDetailExposesFiveStatsAndFractionalCombatRates() {
    val member = CharacterDetailProjector.projectCharacter(GameState.initial(), KAI_ID)!!
    val json = CharacterDetailJson.encodeCharacter(member)
    val primary = json.getJSONObject("stats")
    assertEquals(setOf("STR", "DEX", "AGI", "VIT", "EVA"), primary.keys().asSequence().toSet())
    assertEquals(1, json.getJSONObject("combatStatus").getInt("defense"))
    assertEquals(400, json.getJSONObject("combatStatus").getInt("criticalDamagePercent"))
    assertEquals(5.45, json.getJSONObject("combatStatus").getDouble("criticalChancePercent"), 0.001)
    assertEquals(5.50, json.getJSONObject("combatStatus").getDouble("evasionPercent"), 0.001)
  }
}
