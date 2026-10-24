package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSkillCatalogTest {
  @Test fun skillCatalogExposesCanonSets() {
    assertEquals(11, CompanionSkillCatalog.forCharacter(CAO_MINH_ID).size)
    assertEquals(4, CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).size)
    assertEquals(10, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)
    assertEquals(8, CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).size)
    assertEquals("PASSIVE", CompanionSkillCatalog.forCharacter(CAO_MINH_ID).single { it.name == "Đại Ma Đầu" }.kind)
    assertTrue(CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).any { it.name == "Thiên Kiếm Linh Tâm" })
    assertTrue(CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).any { it.name == "Tịch Quang" })
    assertTrue(CompanionSkillCatalog.forCharacter(SYVIAL_ID).any { it.name.contains("Twenty-Four Severance") })
    assertTrue(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.name == "Kế Hoạch Không Có Trong Kế Hoạch" })
  }

  @Test fun anNhienRemainsNonCombatAndWeaponLocked() {
    val character = AnNhienCanon.character()
    assertEquals("true", character.metadata["nonCombat"])
    assertEquals("false", character.metadata["canUseWeapons"])
    assertFalse(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.effect.contains("Weapon DMG") })
  }

  // NOTE (2026-10-24 chain repair): the automatic companion combat block in
  // patch-companion-skills-ui.py targets an older CombatRuntime generation and
  // is skipped when its anchors are absent on the current tree, so runtime
  // combat-proc tests are not generated here. The catalog contract above is
  // what this patch guarantees on the current generation.
}
