package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CharacterStatusEquipmentSystemTest {
  @Test fun equipmentDoesNotAlterCanonicalStats() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, CAO_MINH_ID)
    val stripped = state.copy(equipment = state.equipment + (CAO_MINH_ID to EquipmentState(CAO_MINH_ID)))
    assertEquals(before, CharacterStatEngine.effective(stripped, CAO_MINH_ID))
  }
  @Test fun weaponDamageIsRawWeaponAttribute() {
    val state = GameState.initial()
    val id = state.equipment.getValue(CAO_MINH_ID).slots.getValue("weapon")
    assertEquals(EquipmentCatalog.definition(id)!!.weapon!!.dmg, CharacterStatEngine.weaponDamage(state, CAO_MINH_ID))
  }

  @Test fun startingEquipmentMatchesCurrentSwordCanon() {
    val state = GameState.initial()
    val equipped = state.equipment.getValue(CAO_MINH_ID).slots
    assertEquals(setOf("weapon", "armor", "ring"), equipped.keys)
    assertEquals("avatars/CAO_MINH_AVATAR.jpg", state.characters.getValue(CAO_MINH_ID).avatarRef)
    val sword = EquipmentCatalog.definition(CAO_MINH_HUYET_MA_KIEM_ID)!!
    assertEquals("CAO_MINH_CODEX R17", sword.canonRef)
    assertEquals(32, sword.weapon!!.dmg)
    assertNull(sword.weapon!!.ammoDisplay)
    assertNull(sword.weapon!!.rpmCapability)
    assertTrue(sword.weapon!!.fireModes.isEmpty())
    assertTrue(sword.abilities.any { it.name == "Ngự Kiếm" })
    assertTrue(sword.abilities.none { it.description.contains("Sparda", ignoreCase = true) })
  }

  @Test fun lucTramEquipmentIsCultivationCanonNotDualFirearms() {
    val sword = EquipmentCatalog.definition(LUC_TRAM_TICH_QUANG_SET_ID)!!
    assertEquals("LUC_TRAM_CODEX R05", sword.canonRef)
    assertEquals(24, sword.weapon!!.dmg)
    assertNull(sword.weapon!!.ammoDisplay)
    assertNull(sword.weapon!!.rpmCapability)
    assertTrue(sword.components.isEmpty())
    assertTrue(sword.abilities.any { it.name == "Kiếm Niệm Hồi Kiếm" })
    val armor = EquipmentCatalog.definition(LUC_TRAM_KIEM_KHAI_ID)!!
    assertEquals("LUC_TRAM_CODEX R05", armor.canonRef)
    assertTrue(armor.restrictions.any { it.contains("không có AI") })
  }

  @Test fun defensiveStatActuallyMitigatesIncomingDamage() {
    val raw = 30
    val base = CharacterStatCore.defendedIncomingDamage(raw, 5)
    val boosted = CharacterStatCore.defendedIncomingDamage(raw, 8)
    assertEquals(raw, base)
    assertTrue(boosted < base)
    assertTrue(boosted > 0)
  }

  @Test fun freshGameUsesFullEffectiveHpButNormalizationDoesNotHeal() {
    val initial = GameState.initial()
    val id = CAO_MINH_ID
    val maxHp = CharacterStatCore.effective(initial, id).maxHp
    assertTrue(maxHp > CharacterProgressionCore.BASE_MAX_HP)
    assertEquals(maxHp, initial.characters.getValue(id).vitalState.currentHp)
    val wounded = CharacterStatEngine.setCurrentHp(initial, id, 7)
    val normalized = CharacterEquipmentSystem.normalize(wounded)
    assertEquals(7, normalized.characters.getValue(id).vitalState.currentHp)
  }
}
