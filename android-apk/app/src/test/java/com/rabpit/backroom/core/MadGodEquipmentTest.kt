package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class MadGodEquipmentTest {
  @Test fun cheatWeaponKeepsWeaponDamageButNoCharacterStatBonus() {
    val def = EquipmentCatalog.definition(MADGOD_SET_ID)!!
    assertEquals(55, def.weapon!!.dmg)
    assertFalse(def.bonuses.any())
  }
}
