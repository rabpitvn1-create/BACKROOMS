package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for equipment percentage stat bonuses in CharacterStatCore.effective().
 *
 * Formula (per implementation):
 *   final_stat = ((base + passive) capped at 999) * (100 + sumPct) / 100, capped at 999
 *   DEF = defense(boosted_vit) * (100 + defPct) / 100, capped at 999
 * Cao Minh has a +10% passive (DAI_DAO_MA_TON_STAT_BONUS_PERCENT).
 */
class EquipmentBonusStatsTest {

  private fun profile(str: Int = 5, dex: Int = 5, agi: Int = 5, vit: Int = 5, eva: Int = 5) =
    CharacterStatProfile(str = str, dex = dex, agi = agi, vit = vit, eva = eva)

  private fun stateWith(characterId: String, statProfile: CharacterStatProfile, vararg itemIds: String): GameState {
    val base = GameState.initial()
    val char = base.characters.getValue(characterId).copy(statProfile = statProfile)
    val equip = EquipmentState(characterId, itemIds.mapIndexed { i, id -> "slot$i" to id }.toMap())
    return base.copy(
      characters = base.characters + (characterId to char),
      equipment = base.equipment + (characterId to equip)
    )
  }

  @Test fun caoMinhSwordAppliesRealItemBonuses() {
    // Use the real catalog item: Huyết Ma Kiếm has strPct=500, dexPct=100.
    val def = EquipmentCatalog.definition(CAO_MINH_SWORD_ID)
    assertNotNull("Huyết Ma Kiếm must exist in catalog", def)
    assertEquals(500, def!!.bonuses.strPct)
    assertEquals(100, def.bonuses.dexPct)

    val state = stateWith(CAO_MINH_ID, profile(), CAO_MINH_SWORD_ID)
    val eff = CharacterStatCore.effective(state, CAO_MINH_ID)
    // Base 5 + Cao Minh passive 1 = 6; STR: 6*600/100=36, DEX: 6*200/100=12.
    assertEquals(36, eff.str)
    assertEquals(12, eff.dex)
    assertEquals(6, eff.agi)
  }

  @Test fun noEquipmentKeepsBasePlusPassive() {
    val state = stateWith(CAO_MINH_ID, profile())
    val eff = CharacterStatCore.effective(state, CAO_MINH_ID)
    // Base 5 + passive 1 = 6 for every stat; DEF = 6-5 = 1.
    assertEquals(6, eff.str)
    assertEquals(6, eff.dex)
    assertEquals(6, eff.agi)
    assertEquals(6, eff.vit)
    assertEquals(6, eff.eva)
    assertEquals(1, eff.def)
  }

  @Test fun thienCoKiemKhaiBoostsVitThenDef() {
    // Thiên Cơ Bạch Kim Kiếm Khải: defPct=75, dexPct=50, agiPct=50, vitPct=20.
    val def = EquipmentCatalog.definition(LUC_TRAM_KIEM_KHAI_ID)
    assertNotNull("Thiên Cơ Bạch Kim Kiếm Khải must exist in catalog", def)
    assertEquals(75, def!!.bonuses.defPct)
    assertEquals(20, def.bonuses.vitPct)

    // Lục Trầm has no passive; use VIT=100 for meaningful numbers.
    val state = stateWith(LUC_TRAM_ID, profile(vit = 100), LUC_TRAM_KIEM_KHAI_ID)
    val eff = CharacterStatCore.effective(state, LUC_TRAM_ID)
    // VIT: 100*120/100=120. DEF: defense(120)=115, 115*175/100=201.
    assertEquals(120, eff.vit)
    assertEquals(201, eff.def)
    // DEX: 5*150/100=7, AGI: 5*150/100=7.
    assertEquals(7, eff.dex)
    assertEquals(7, eff.agi)
  }

  @Test fun multipleItemsStackAdditively() {
    // Huyết Ma Kiếm (strPct=500, dexPct=100) + Huyết Ma Chiến Khải (strPct=20, dexPct=30).
    val state = stateWith(CAO_MINH_ID, profile(), CAO_MINH_SWORD_ID, CAO_MINH_ARMOR_ID)
    val eff = CharacterStatCore.effective(state, CAO_MINH_ID)
    // STR: 6*620/100=37. DEX: 6*230/100=13.
    assertEquals(37, eff.str)
    assertEquals(13, eff.dex)
  }

  @Test fun bonusesRespectMaxStatCap() {
    // Lục Trầm (no passive), STR=900, Tịch Quang strPct=200 → 900*300/100=2700 → cap 999.
    val state = stateWith(LUC_TRAM_ID, profile(str = 900), LUC_TRAM_TICH_QUANG_ID)
    val eff = CharacterStatCore.effective(state, LUC_TRAM_ID)
    assertEquals(999, eff.str)
  }
}
