package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSkillCatalogTest {
  @Test fun skillCatalogExposesNewCompanionSets() {
    assertEquals(8, CompanionSkillCatalog.forCharacter(IRIS_ID).size)
    assertEquals(10, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)
    assertEquals(8, CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).size)
    assertTrue(CompanionSkillCatalog.forCharacter(IRIS_ID).any { it.name == "ARGUS // Thousandfold Execution" })
    assertTrue(CompanionSkillCatalog.forCharacter(SYVIAL_ID).any { it.name.contains("Twenty-Four Severance") })
    assertTrue(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.name == "Kế Hoạch Không Có Trong Kế Hoạch" })
  }

  @Test fun anNhienRemainsNonCombatAndWeaponLocked() {
    val character = AnNhienCanon.character()
    assertEquals("true", character.metadata["nonCombat"])
    assertEquals("false", character.metadata["canUseWeapons"])
    assertFalse(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.effect.contains("Weapon DMG") })
  }

  @Test fun irisAndSyvialAutomaticSkillsResolveWhenTheyAreActivePartyMembers() {
    val seen = mutableSetOf<String>()
    for (counter in 0..360) {
      if (seen.size == 2) break
      var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(
        party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID))
      )
      state = CombatRuntime.start(state, "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (result.reply.contains("Twosome Time tự động kích hoạt") || result.reply.contains("ARGUS // Thousandfold Execution")) seen += "iris"
      if (result.reply.contains("Rift Sever tự động kích hoạt") || result.reply.contains("GodKiller Override // Twenty-Four Severance")) seen += "syvial"
    }
    assertEquals(setOf("iris", "syvial"), seen)
  }

  @Test fun anNhienCombatUtilityNeverDealsDamageDirectly() {
    var observed = false
    for (counter in 0..360) {
      if (observed) break
      var state = AnNhienCanon.ensure(GameState.initial()).copy(
        party = PartyState(memberIds = listOf(KAI_ID, AN_NHIEN_ID))
      )
      state = CombatRuntime.start(state, "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (result.reply.contains("Quăng Đại Cái Gì Đó") || result.reply.contains("Kế Hoạch Không Có Trong Kế Hoạch")) {
        observed = true
        val fragment = result.reply.substringAfter("An Nhiên", result.reply)
        assertFalse(fragment.contains("Weapon DMG"))
      }
    }
    assertTrue(observed)
  }
}
