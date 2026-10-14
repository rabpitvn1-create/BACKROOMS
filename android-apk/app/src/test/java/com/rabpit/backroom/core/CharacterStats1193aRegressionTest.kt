package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterStats1193aRegressionTest {
  @Test fun obsoleteCoreCurrencyIsRemovedDuringNormalization() {
    val stale = GameState.initial().copy(metadata = mapOf(
      "poker.core.count" to "999",
      "characterProgression.coreResource" to "999"
    ))
    val normalized = CharacterProgressionCore.normalize(stale)
    assertFalse("poker.core.count" in normalized.metadata)
    assertFalse("characterProgression.coreResource" in normalized.metadata)
    assertEquals(CharacterProgression(), normalized.characters.getValue(CAO_MINH_ID).progression)
  }

  @Test fun baselineAndCaoPassiveMatch1193a() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    val base = state.characters.getValue(CAO_MINH_ID).statProfile
    assertEquals(listOf(5,5,5,5,5), listOf(base.str, base.dex, base.agi, base.vit, base.eva))
    // 1.1.93a passive baseline is observable with the new percentage gear removed.
    val bare = state.copy(equipment = state.equipment + (CAO_MINH_ID to EquipmentState(CAO_MINH_ID)))
    val effective = CharacterStatCore.effective(bare, CAO_MINH_ID)
    assertEquals(listOf(6,6,6,6,6), listOf(effective.str, effective.dex, effective.agi, effective.vit, effective.eva))
    val equipped = CharacterStatCore.effective(state, CAO_MINH_ID)
    assertEquals(listOf(37,13,12,7,6), listOf(equipped.str,equipped.dex,equipped.agi,equipped.vit,equipped.eva))
    assertEquals(55, effective.maxHp)
    assertEquals(50, state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(5, effective.criticalChancePercent)
    assertEquals(6, effective.evasionPercent)
    assertEquals(0, effective.resCriticalPercent)
    assertEquals(0, effective.resEvasionPercent)
    assertEquals(545, effective.criticalChanceBasisPoints)
    assertEquals(550, effective.evasionBasisPoints)
    assertEquals(25, effective.resCriticalBasisPoints)
    assertEquals(25, effective.resEvasionBasisPoints)
    assertEquals(1, effective.def)
    assertEquals(400, effective.criticalDamagePercent)
    val capped = state.copy(characters = state.characters + (CAO_MINH_ID to state.characters.getValue(CAO_MINH_ID).copy(
      statProfile = base.copy(str=999, dex=999, agi=999, vit=999, eva=999)
    )))
    val cappedEffective = CharacterStatCore.effective(capped, CAO_MINH_ID)
    assertEquals(listOf(999,999,999,999,999), listOf(cappedEffective.str,cappedEffective.dex,cappedEffective.agi,cappedEffective.vit,cappedEffective.eva))
  }

  @Test fun legacySchemaResetsInsteadOfMappingAndPreservesHpSafely() {
    val encoded = JSONObject(GameStateCodec.encode(GameState.initial()))
    val chars = encoded.getJSONObject("characters")
    val cao = chars.getJSONObject(CAO_MINH_ID)
    cao.put("statProfile", JSONObject()
      .put("baseMaxHp", 999).put("str", 82).put("df", 78).put("agi", 92).put("crit", 95)
      .put("baseStats", JSONObject().put("LUCK", 500)).put("level", 77).put("exp", 99999))
    cao.put("vitalState", JSONObject().put("currentHp", 31).put("condition", "HURT"))
    val migrated = GameStateCodec.decode(encoded.toString())
    val p = migrated.characters.getValue(CAO_MINH_ID).statProfile
    assertEquals(listOf(5,5,5,5,5), listOf(p.str,p.dex,p.agi,p.vit,p.eva))
    assertEquals(31, migrated.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    val out = JSONObject(GameStateCodec.encode(migrated)).getJSONObject("characters").getJSONObject(CAO_MINH_ID).getJSONObject("statProfile")
    assertEquals(setOf("schema","baseMaxHp","STR","DEX","AGI","VIT","EVA"), out.keys().asSequence().toSet())
  }

  @Test fun expThresholdsRiseAndLevelProgressionIsPerCharacter() {
    assertEquals(listOf(10, 18, 26, 34), (0..3).map(CharacterProgressionCore::expRequired))
    val state = CharacterProgressionCore.grantExp(GameState.initial(), CAO_MINH_ID, 10, kotlin.random.Random(3)).state
    assertEquals(1, state.characters.getValue(CAO_MINH_ID).progression.level)
    assertEquals(0, state.characters.getValue(CAO_MINH_ID).progression.exp)
    state.characters.filterKeys { it != CAO_MINH_ID }.values.forEach {
      assertEquals(0, it.progression.level)
    }
  }

  @Test fun levelUpRaisesMaxHpButDoesNotEraseMissingHp() {
    val initial = CharacterStatEngine.setCurrentHp(GameState.initial(), CAO_MINH_ID, 40)
    val beforeMax = CharacterStatEngine.effective(initial, CAO_MINH_ID).maxHp
    val result = CharacterProgressionCore.grantExp(initial, CAO_MINH_ID, 10, kotlin.random.Random(5))
    val afterMax = CharacterStatEngine.effective(result.state, CAO_MINH_ID).maxHp
    assertTrue(afterMax > beforeMax)
    assertEquals(40 + (afterMax - beforeMax),
      result.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
  }

  @Test fun derivedCapsAndDamageProjectionMatch1193a() {
    assertEquals(30, CharacterStatCore.criticalChance(999))
    assertEquals(35, CharacterStatCore.evasion(999))
    assertEquals(100, CharacterStatCore.criticalResistance(999))
    assertEquals(100, CharacterStatCore.evasionResistance(999))
    assertEquals(30, CharacterStatCore.basicDamage(30, 5))
    assertEquals(31, CharacterStatCore.basicDamage(30, 6))
    assertEquals(77, CharacterStatCore.skillDamage(30, 170, 5, 150))
    assertEquals(80, CharacterStatCore.skillDamage(30, 170, 6, 150))
    assertEquals(20, CharacterStatCore.defendedIncomingDamage(20, 0))
    assertEquals(16, CharacterStatCore.defendedIncomingDamage(20, 25))
    assertEquals(10, CharacterStatCore.defendedIncomingDamage(20, 100))
    assertEquals(400, CharacterStatCore.criticalDamage(100))
  }

  @Test fun equipmentCannotCreateSecondStatSchemaAndWeaponDamageStaysWeaponProperty() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, CAO_MINH_ID)
    val stripped = state.copy(equipment = state.equipment + (CAO_MINH_ID to EquipmentState(CAO_MINH_ID)))
    val after = CharacterStatEngine.effective(stripped, CAO_MINH_ID)
    assertEquals(state.characters.getValue(CAO_MINH_ID).statProfile,
      stripped.characters.getValue(CAO_MINH_ID).statProfile)
    assertEquals(listOf(6,6,6,6,6), listOf(after.str,after.dex,after.agi,after.vit,after.eva))
    assertEquals(listOf(37,13,12,7,6), listOf(before.str,before.dex,before.agi,before.vit,before.eva))
    val weapon = CharacterStatEngine.weaponDamage(state, CAO_MINH_ID)
    val id = state.equipment.getValue(CAO_MINH_ID).slots["weapon"]
    assertEquals(EquipmentCatalog.definition(id!!)?.weapon?.dmg ?: 18, weapon)
  }

  @Test fun antiForgeRestoresStatsProgressionAndHp() {
    var authoritative = CharacterProgressionCore.grantExp(GameState.initial(), CAO_MINH_ID, 10, kotlin.random.Random(3)).state
    authoritative = CharacterStatEngine.setCurrentHp(authoritative, CAO_MINH_ID, 33)
    val c = authoritative.characters.getValue(CAO_MINH_ID)
    val forged = authoritative.copy(characters = authoritative.characters + (CAO_MINH_ID to c.copy(
      statProfile = c.statProfile.copy(str = 999, dex = 999, agi = 999, vit = 999, eva = 999),
      vitalState = c.vitalState.copy(currentHp = 200),
      progression = CharacterProgression(99, 100)
    )))
    val protected = CharacterProgressionCore.protectFromCandidate(authoritative, forged)
    assertEquals(c.statProfile, protected.characters.getValue(CAO_MINH_ID).statProfile)
    assertEquals(c.progression, protected.characters.getValue(CAO_MINH_ID).progression)
    assertEquals(33, protected.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
  }

  @Test fun daiDaoCombatStackIsSeparateAndResetsWithCombatMetadata() {
    var state = CharacterStatEngine.setCurrentHp(GameState.initial(), CAO_MINH_ID, 30)
    val baseCrit = CharacterStatEngine.effective(state, CAO_MINH_ID).criticalChancePercent
    val skillBefore = CharacterStatEngine.skillBaseDamage(state, CAO_MINH_ID)
    state = CaoMinhCombatPassive.afterCaoMinhTurn(state)
    assertEquals(1, CaoMinhCombatPassive.stacks(state))
    assertEquals(120, CaoMinhCombatPassive.attackPercent(state))
    assertTrue(CharacterStatEngine.skillBaseDamage(state, CAO_MINH_ID) > skillBefore)
    assertEquals((baseCrit + 20).coerceAtMost(100), CaoMinhCombatPassive.criticalChance(state, baseCrit))
    assertEquals(55, CaoMinhCombatPassive.allyCriticalChance(5))
    assertEquals(36, state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    val cleared = state.copy(metadata = state.metadata.filterKeys { !it.startsWith("combat.") })
    assertEquals(0, CaoMinhCombatPassive.stacks(cleared))
  }
}
