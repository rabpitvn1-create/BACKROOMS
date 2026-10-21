package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterFiveStatCombatMathTest {
  @Test fun fiveStatsDeriveTheirOnlyIntendedCombatAttributes() {
    val original = GameState.initial()
    val id = CAO_MINH_ID
    val actor = original.characters.getValue(id)
    val state = original.copy(characters = original.characters + (id to actor.copy(
      statProfile = actor.statProfile.copy(str = 6, dex = 6, agi = 6, vit = 6, eva = 6)
    )))
    val sampled = CharacterStatCore.effective(state, id)
    // +10% intrinsic passive is applied before the bound weapon/armor percentage bonuses.
    // Raw 6 -> passive 7; gear: STR +520%, DEX +130%, AGI +100%, VIT +20%.
    assertEquals(listOf(43,30,28,8,7), listOf(sampled.str,sampled.dex,sampled.agi,sampled.vit,sampled.eva))
    assertEquals(CharacterStatCore.maxHp(sampled.vit), sampled.maxHp)
    assertEquals(CharacterStatCore.defense(sampled.vit), sampled.def)
    assertEquals(CharacterProgressionCore.statPercent(sampled.str), sampled.attackPercent)
    assertEquals(sampled.attackPercent * 9, sampled.skillDamagePercent)
    assertEquals(CharacterStatCore.criticalChanceBasisPoints(sampled.agi), sampled.criticalChanceBasisPoints)
    assertEquals(CharacterStatCore.criticalResistanceBasisPoints(sampled.dex), sampled.resCriticalBasisPoints)
    assertEquals(CharacterStatCore.evasionBasisPoints(sampled.eva), sampled.evasionBasisPoints)
    assertEquals(CharacterStatCore.evasionResistanceBasisPoints(sampled.eva), sampled.resEvasionBasisPoints)
  }

  @Test fun dexAgiEvaKeepTheirDistinctCombatResponsibilities() {
    val original = GameState.initial()
    val id = CAO_MINH_ID
    val actor = original.characters.getValue(id)
    fun withStat(profile: CharacterStatProfile): EffectiveCharacterStats =
      CharacterStatCore.effective(original.copy(characters = original.characters +
        (id to actor.copy(statProfile = profile))), id)
    val base = withStat(actor.statProfile)
    val withAgi = withStat(actor.statProfile.copy(agi = 25))
    assertTrue(withAgi.criticalChanceBasisPoints > base.criticalChanceBasisPoints)
    assertEquals(base.maxHp, withAgi.maxHp)
    assertEquals(base.evasionBasisPoints, withAgi.evasionBasisPoints)
    assertEquals(base.resCriticalBasisPoints, withAgi.resCriticalBasisPoints)

    val withDex = withStat(actor.statProfile.copy(dex = 25))
    assertTrue(withDex.resCriticalBasisPoints > base.resCriticalBasisPoints)
    assertEquals(base.criticalChanceBasisPoints, withDex.criticalChanceBasisPoints)
    assertEquals(base.evasionBasisPoints, withDex.evasionBasisPoints)

    val withEva = withStat(actor.statProfile.copy(eva = 25))
    assertTrue(withEva.evasionBasisPoints > base.evasionBasisPoints)
    assertTrue(withEva.resEvasionBasisPoints > base.resEvasionBasisPoints)
    assertEquals(base.criticalChanceBasisPoints, withEva.criticalChanceBasisPoints)
    assertEquals(base.def, withEva.def)
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
    val member = CharacterDetailProjector.projectCharacter(GameState.initial(), CAO_MINH_ID)!!
    val json = CharacterDetailJson.encodeCharacter(member)
    val primary = json.getJSONObject("stats")
    assertEquals(setOf("STR", "DEX", "AGI", "VIT", "EVA"), primary.keys().asSequence().toSet())
    val effective = CharacterStatCore.effective(GameState.initial(), CAO_MINH_ID)
    assertEquals(2, effective.def)
    assertEquals(1355, effective.criticalChanceBasisPoints)
    assertEquals(effective.def, json.getJSONObject("combatStatus").getInt("defense"))
    assertEquals(400, json.getJSONObject("combatStatus").getInt("criticalDamagePercent"))
    assertEquals(effective.criticalChanceBasisPoints / 100.0,
      json.getJSONObject("combatStatus").getDouble("criticalChancePercent"), 0.001)
    assertEquals(effective.evasionBasisPoints / 100.0,
      json.getJSONObject("combatStatus").getDouble("evasionPercent"), 0.001)
  }
}
