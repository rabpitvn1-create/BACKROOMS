package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CharacterStatusEquipmentSystemTest {
  @Test fun equipmentDoesNotAlterCanonicalStats() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, KAI_ID)
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    assertEquals(before, CharacterStatEngine.effective(stripped, KAI_ID))
  }
  @Test fun weaponDamageIsRawWeaponAttribute() {
    val state = GameState.initial()
    val id = state.equipment.getValue(KAI_ID).slots.getValue("weapon")
    assertEquals(EquipmentCatalog.definition(id)!!.weapon!!.dmg, CharacterStatEngine.weaponDamage(state, KAI_ID))
  }
}
