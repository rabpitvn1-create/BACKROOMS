package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterStats1193aRegressionTest {
  @Test fun baselineAndCaoPassiveMatch1193a() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    val base = state.characters.getValue(KAI_ID).statProfile
    assertEquals(listOf(5,5,5,5), listOf(base.str, base.def, base.skl, base.vit))
    val effective = CharacterStatCore.effective(state, KAI_ID)
    assertEquals(listOf(6,6,6,6), listOf(effective.str, effective.def, effective.skl, effective.vit))
    assertEquals(55, effective.maxHp)
    assertEquals(50, state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(7, effective.criticalChancePercent)
    assertEquals(2, effective.evasionPercent)
    assertEquals(2, effective.resCriticalPercent)
    assertEquals(2, effective.resEvasionPercent)
    assertEquals(150, effective.criticalDamagePercent)
    val capped = state.copy(characters = state.characters + (KAI_ID to state.characters.getValue(KAI_ID).copy(
      statProfile = base.copy(str=999, def=999, skl=999, vit=999)
    )))
    val cappedEffective = CharacterStatCore.effective(capped, KAI_ID)
    assertEquals(listOf(999,999,999,999), listOf(cappedEffective.str,cappedEffective.def,cappedEffective.skl,cappedEffective.vit))
  }

  @Test fun legacySchemaResetsInsteadOfMappingAndPreservesHpSafely() {
    val encoded = JSONObject(GameStateCodec.encode(GameState.initial()))
    val chars = encoded.getJSONObject("characters")
    val cao = chars.getJSONObject(KAI_ID)
    cao.put("statProfile", JSONObject()
      .put("baseMaxHp", 999).put("str", 82).put("df", 78).put("agi", 92).put("crit", 95)
      .put("baseStats", JSONObject().put("LUCK", 500)).put("level", 77).put("exp", 99999))
    cao.put("vitalState", JSONObject().put("currentHp", 31).put("condition", "HURT"))
    val migrated = GameStateCodec.decode(encoded.toString())
    val p = migrated.characters.getValue(KAI_ID).statProfile
    assertEquals(listOf(5,5,5,5), listOf(p.str,p.def,p.skl,p.vit))
    assertEquals(31, migrated.characters.getValue(KAI_ID).vitalState.currentHp)
    val out = JSONObject(GameStateCodec.encode(migrated)).getJSONObject("characters").getJSONObject(KAI_ID).getJSONObject("statProfile")
    assertEquals(setOf("schema","baseMaxHp","STR","DEF","SKL","VIT"), out.keys().asSequence().toSet())
  }

  @Test fun costSharedCoreAndPerCharacterProgressionAreCanonical() {
    assertEquals(listOf(1,1,2,2,3,3), (5..10).map(CharacterProgressionCore::upgradeCost))
    var state = CharacterProgressionCore.grantCore(GameState.initial(), 10)
    val other = state.characters.keys.firstOrNull { it != KAI_ID }
    if (other != null) {
      state = CharacterProgressionCore.upgrade(state, KAI_ID, "STR").state
      assertEquals(6, CharacterProgressionCore.baseStat(state, KAI_ID, "STR"))
      assertEquals(5, CharacterProgressionCore.baseStat(state, other, "STR"))
      assertEquals(9, CharacterProgressionCore.coreCount(state))
    }
  }

  @Test fun vitUpgradeRaisesMaxAndPreservesMissingHp() {
    var state = CharacterProgressionCore.grantCore(GameState.initial(), 2)
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 40)
    val beforeMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val result = CharacterProgressionCore.upgrade(state, KAI_ID, "VIT")
    val afterMax = CharacterStatEngine.effective(result.state, KAI_ID).maxHp
    assertTrue(afterMax > beforeMax)
    assertEquals(40 + (afterMax - beforeMax), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun derivedCapsAndDamageProjectionMatch1193a() {
    assertEquals(50, CharacterStatCore.criticalChance(999))
    assertEquals(35, CharacterStatCore.evasion(999))
    assertEquals(50, CharacterStatCore.criticalResistance(999))
    assertEquals(50, CharacterStatCore.evasionResistance(999))
    assertEquals(30, CharacterStatCore.basicDamage(30, 5))
    assertEquals(33, CharacterStatCore.basicDamage(30, 6))
    assertEquals(77, CharacterStatCore.skillDamage(30, 170, 5, 150))
    assertEquals(84, CharacterStatCore.skillDamage(30, 170, 6, 150))
    assertEquals(20, CharacterStatCore.defendedIncomingDamage(20, 5))
    assertEquals(18, CharacterStatCore.defendedIncomingDamage(20, 6))
    assertEquals(10, CharacterStatCore.defendedIncomingDamage(20, 15))
    assertEquals(150, CharacterStatCore.criticalDamage(100))
  }

  @Test fun equipmentCannotCreateSecondStatSchemaAndWeaponDamageStaysWeaponProperty() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, KAI_ID)
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    val after = CharacterStatEngine.effective(stripped, KAI_ID)
    assertEquals(before.copy(), after)
    val weapon = CharacterStatEngine.weaponDamage(state, KAI_ID)
    val id = state.equipment.getValue(KAI_ID).slots["weapon"]
    assertEquals(EquipmentCatalog.definition(id!!)?.weapon?.dmg ?: 18, weapon)
  }

  @Test fun antiForgeRestoresStatsCoreAndHp() {
    var authoritative = CharacterProgressionCore.grantCore(GameState.initial(), 7)
    authoritative = CharacterStatEngine.setCurrentHp(authoritative, KAI_ID, 33)
    val c = authoritative.characters.getValue(KAI_ID)
    val forged = authoritative.copy(
      characters = authoritative.characters + (KAI_ID to c.copy(
        statProfile = c.statProfile.copy(str=999,def=999,skl=999,vit=999),
        vitalState = c.vitalState.copy(currentHp=55)
      )),
      metadata = authoritative.metadata + (CharacterProgressionCore.CORE_KEY to "99999")
    )
    val protected = CharacterProgressionCore.protectFromCandidate(authoritative, forged)
    assertEquals(c.statProfile, protected.characters.getValue(KAI_ID).statProfile)
    assertEquals(33, protected.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(7, CharacterProgressionCore.coreCount(protected))
  }

  @Test fun daiDaoCombatStackIsSeparateAndResetsWithCombatMetadata() {
    var state = CharacterStatEngine.setCurrentHp(GameState.initial(), KAI_ID, 30)
    val baseCrit = CharacterStatEngine.effective(state, KAI_ID).criticalChancePercent
    val skillBefore = CharacterStatEngine.skillBaseDamage(state, KAI_ID)
    state = CaoMinhCombatPassive.afterCaoMinhTurn(state)
    assertEquals(1, CaoMinhCombatPassive.stacks(state))
    assertEquals(120, CaoMinhCombatPassive.attackPercent(state))
    assertTrue(CharacterStatEngine.skillBaseDamage(state, KAI_ID) > skillBefore)
    assertEquals((baseCrit + 20).coerceAtMost(100), CaoMinhCombatPassive.criticalChance(state, baseCrit))
    assertEquals(55, CaoMinhCombatPassive.allyCriticalChance(5))
    assertEquals(36, state.characters.getValue(KAI_ID).vitalState.currentHp)
    val cleared = state.copy(metadata = state.metadata.filterKeys { !it.startsWith("combat.") })
    assertEquals(0, CaoMinhCombatPassive.stacks(cleared))
  }
}
