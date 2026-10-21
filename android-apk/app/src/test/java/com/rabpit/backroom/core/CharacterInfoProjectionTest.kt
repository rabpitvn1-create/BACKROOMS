package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CharacterInfoProjectionTest {
  @Test fun partyDetailsExposeLevelExpAndAllCombatRatesPerCharacter() {
    val initial = GameState.initial().copy(
      party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID))
    )
    val advanced = CharacterProgressionCore.grantExp(initial, IRIS_ID, 13, Random(42)).state
    val details = CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(advanced))
      .getJSONArray("members")
    val caoMinh = details.getJSONObject(0)
    val iris = details.getJSONObject(1)
    assertEquals(CAO_MINH_ID, caoMinh.getString("id"))
    assertEquals(IRIS_ID, iris.getString("id"))
    assertEquals(0, caoMinh.getInt("level"))
    assertEquals(0, caoMinh.getInt("exp"))
    assertEquals(1, iris.getInt("level"))
    assertEquals(3, iris.getInt("exp"))
    assertEquals(18, iris.getInt("expToNextLevel"))
    assertTrue(iris.getInt("maxHp") >= iris.getInt("currentHp"))
    val statLines = iris.getJSONObject("stats")
    assertEquals(setOf("STR", "DEX", "AGI", "VIT", "EVA"),
      statLines.keys().asSequence().toSet())
    assertEquals(35, listOf("STR", "DEX", "AGI", "VIT", "EVA")
      .sumOf { statLines.getJSONObject(it).getInt("base") })
    statLines.keys().forEach { key ->
      val stat = statLines.getJSONObject(key)
      assertEquals(stat.getInt("effective") - stat.getInt("base"), stat.getInt("passive"))
    }
    val combat = iris.getJSONObject("combatStatus")
    assertEquals(setOf("defense", "attackPercent", "skillDamagePercent", "skillDamageMultiplier", "accuracyPercent",
      "criticalChancePercent", "criticalDamagePercent", "evasionPercent",
      "criticalResistancePercent", "evasionResistancePercent"),
      combat.keys().asSequence().toSet())
    val effective = CharacterStatCore.effective(advanced, IRIS_ID)
    assertEquals(effective.accuracyBasisPoints / 100.0,
      combat.getDouble("accuracyPercent"), 0.001)
    assertEquals(effective.criticalChanceBasisPoints / 100.0,
      combat.getDouble("criticalChancePercent"), 0.001)
    assertEquals(effective.evasionBasisPoints / 100.0,
      combat.getDouble("evasionPercent"), 0.001)
    assertEquals(400, combat.getInt("criticalDamagePercent"))
    assertFalse(combat.has("SKL"))
    assertEquals(1, combat.getInt("skillDamageMultiplier"))
    val caoCombat = caoMinh.getJSONObject("combatStatus")
    assertEquals(9, caoCombat.getInt("skillDamageMultiplier"))
    assertEquals(caoCombat.getInt("attackPercent") * 9, caoCombat.getInt("skillDamagePercent"))
  }

  @Test fun maxLevelProducesExplicitNullExpRequirement() {
    val initial = GameState.initial()
    val character = initial.characters.getValue(CAO_MINH_ID)
    val capped = initial.copy(characters = initial.characters + (CAO_MINH_ID to
      character.copy(progression = CharacterProgression(CharacterProgressionCore.MAX_LEVEL, 0))
    ))
    val detail = CharacterDetailJson.encodeCharacter(CharacterDetailProjector.projectCharacter(capped, CAO_MINH_ID)!!)
    assertEquals(CharacterProgressionCore.MAX_LEVEL, detail.getInt("level"))
    assertEquals(0, detail.getInt("exp"))
    assertTrue(detail.isNull("expToNextLevel"))
  }
}
