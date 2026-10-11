package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CaoMinhSkillsEquipmentTest {
  @Test fun daiDaoIsIntrinsicAndEquipmentDoesNotChangeBaseStats() {
    val state = GameState.initial()
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID)
    val passive = skills.single { it.name == "Đại Đạo Ma Tôn" }
    assertEquals("PASSIVE", passive.kind)
    assertTrue(passive.effect.contains("+10% Base STR/DEF/SKL/VIT"))
    assertFalse(passive.effect.contains("+50 Max HP"))
    assertFalse(passive.effect.contains("AGI"))
    val base = state.characters.getValue(KAI_ID).statProfile
    assertEquals(listOf(5,5,5,5), listOf(base.str,base.def,base.skl,base.vit))
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    assertEquals(CharacterStatEngine.effective(state,KAI_ID), CharacterStatEngine.effective(stripped,KAI_ID))
  }
  @Test fun nonStatSkillKitRemainsPresent() {
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID)
    assertTrue(skills.any { it.name == "Huyết Ma Nhị Thập Tứ Trảm" })
  }
}
