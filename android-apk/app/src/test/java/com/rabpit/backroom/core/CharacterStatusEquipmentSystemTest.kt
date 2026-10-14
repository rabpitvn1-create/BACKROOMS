package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CharacterStatusEquipmentSystemTest {
  @Test fun equipmentChangesEffectiveStatsNotRawCanon() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, CAO_MINH_ID)
    val stripped = state.copy(equipment = state.equipment + (CAO_MINH_ID to EquipmentState(CAO_MINH_ID)))
    val after = CharacterStatEngine.effective(stripped, CAO_MINH_ID)
    assertEquals(state.characters.getValue(CAO_MINH_ID).statProfile,
      stripped.characters.getValue(CAO_MINH_ID).statProfile)
    assertEquals(listOf(37,13,12,7,6), listOf(before.str,before.dex,before.agi,before.vit,before.eva))
    assertEquals(listOf(6,6,6,6,6), listOf(after.str,after.dex,after.agi,after.vit,after.eva))
  }
  @Test fun weaponDamageIsRawWeaponAttribute() {
    val state = GameState.initial()
    val id = state.equipment.getValue(CAO_MINH_ID).slots.getValue("weapon")
    assertEquals(EquipmentCatalog.definition(id)!!.weapon!!.dmg, CharacterStatEngine.weaponDamage(state, CAO_MINH_ID))
  }
}
