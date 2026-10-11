package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CaoMinhSkillsEquipmentTest {
  @Test fun daiDaoIsIntrinsicAndEquipmentDoesNotChangeBaseStats() {
    val state = GameState.initial()
    val skills = CompanionSkillCatalog.forCharacter(CAO_MINH_ID)
    val passive = skills.single { it.name == "Đại Đạo Ma Tôn" }
    assertEquals("PASSIVE", passive.kind)
    assertTrue(passive.effect.contains("+10% Base STR/DEX/AGI/VIT/EVA"))
    assertFalse(passive.effect.contains("+50 Max HP"))
    assertTrue(passive.effect.contains("AGI"))
    val base = state.characters.getValue(CAO_MINH_ID).statProfile
    assertEquals(listOf(5,5,5,5,5), listOf(base.str,base.dex,base.agi,base.vit,base.eva))
    val stripped = state.copy(equipment = state.equipment + (CAO_MINH_ID to EquipmentState(CAO_MINH_ID)))
    assertEquals(CharacterStatEngine.effective(state,CAO_MINH_ID), CharacterStatEngine.effective(stripped,CAO_MINH_ID))
  }
  @Test fun nonStatSkillKitRemainsPresent() {
    val skills = CompanionSkillCatalog.forCharacter(CAO_MINH_ID)
    assertTrue(skills.any { it.name == "Huyết Ma Nhị Thập Tứ Trảm" })
  }
}
