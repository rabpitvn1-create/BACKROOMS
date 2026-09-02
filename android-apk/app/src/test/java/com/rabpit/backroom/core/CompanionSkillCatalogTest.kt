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
    assertFalse(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.effect.contains("DMG vũ khí") })
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
        assertFalse(fragment.contains("sát thương vũ khí"))
      }
    }
    assertTrue(observed)
  }

  @org.junit.Test fun luciaProjectsFullAutoBurstContract() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).first { it.name == "M4A1 Full Auto Burst" }
    org.junit.Assert.assertEquals("AUTO", skill.kind)
    org.junit.Assert.assertTrue(skill.trigger.contains("20%"))
    org.junit.Assert.assertTrue(skill.trigger.contains("2 lượt chiến đấu"))
    org.junit.Assert.assertTrue(skill.effect.contains("30 viên"))
    org.junit.Assert.assertTrue(skill.effect.contains("30 + sát thương cơ bản"))
    org.junit.Assert.assertTrue(skill.effect.contains("Né tránh của Thực thể"))
  }

  @org.junit.Test fun kaiCatalogUsesShotgunLanguageAndRaisedProcRates() {
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID).associateBy { it.name }
    org.junit.Assert.assertTrue(skills.getValue("The Last Requiem").trigger.contains("38%"))
    org.junit.Assert.assertTrue(skills.getValue("Silent Lullaby").trigger.contains("27%"))
    org.junit.Assert.assertTrue(skills.getValue("Salvation").trigger.contains("26%"))
    org.junit.Assert.assertTrue(skills.getValue("Quick Step").trigger.contains("35%"))
    for (name in listOf("The Last Requiem", "Silent Lullaby", "Salvation", "Quick Step")) {
      org.junit.Assert.assertTrue(name, skills.getValue(name).effect.contains("SRU-SG"))
    }
  }

  @org.junit.Test fun luciaCatalogProjectsTooYoungToDieContract() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).first { it.name == "Too Young To Die" }
    org.junit.Assert.assertEquals("AUTO", skill.kind)
    org.junit.Assert.assertTrue(skill.trigger.contains("15%"))
    org.junit.Assert.assertTrue(skill.trigger.contains("5 điểm phần trăm"))
    org.junit.Assert.assertTrue(skill.trigger.contains("3 điểm phần trăm"))
    org.junit.Assert.assertTrue(skill.effect.contains("60 viên"))
    org.junit.Assert.assertTrue(skill.effect.contains("sát thương cơ bản +5%"))
  }

  @org.junit.Test fun issue125SkillDescriptionsUseVietnameseGameplayWording() {
    val all = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUCIA_ID)
      .flatMap(CompanionSkillCatalog::forCharacter)
    val forbidden = listOf(
      " combat ", " turn", "Weapon DMG", " Armor", "Evasion", "Accuracy",
      "Bleeding", "Stun", " proc", " gate", "resolution", "hazard",
      "generic loot roll", "outgoing", "incoming", "Fully Exposed",
      "Armor Break", "Disarm", "Disoriented", " damage"
    )
    all.forEach { skill ->
      val prose = listOfNotNull(skill.trigger, skill.effect, skill.note).joinToString(" ")
      forbidden.forEach { token ->
        org.junit.Assert.assertFalse("${skill.name} still contains mixed-English token: $token | $prose", prose.contains(token, ignoreCase = false))
      }
    }
    org.junit.Assert.assertFalse(all.any { listOfNotNull(it.trigger, it.effect, it.note).joinToString(" ").contains("DMG") })
    org.junit.Assert.assertFalse(all.any { listOfNotNull(it.trigger, it.effect, it.note).joinToString(" ").contains("HP") })
  }

  @org.junit.Test fun issue126SkillDescriptionsReadAsNaturalVietnamese() {
    val all = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUCIA_ID)
      .flatMap(CompanionSkillCatalog::forCharacter)
    val prose = all.flatMap { listOfNotNull(it.trigger, it.effect, it.note) }.joinToString("\n")
    val retiredFragments = listOf(
      "Phân tích trong 3 lượt:",
      "2 phát chéo góc, 155% DMG",
      "Spatial Shift làm lệch trục phòng thủ rồi chém",
      "Đại diện lợi thế thực dụng trong lời kể của Game Master",
      "SRU-SG: 4 viên đạn quỷ lực theo nhịp giật kiểm soát",
      "Xả đúng 30 viên; mỗi viên gây"
    )
    retiredFragments.forEach { fragment ->
      org.junit.Assert.assertFalse("Old translation-like skill prose returned: $fragment", prose.contains(fragment))
    }
    org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(IRIS_ID).first().effect.contains("Trong 3 lượt tiếp theo"))
    org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(KAI_ID).first().effect.contains("Kai ghìm nhịp giật của SRU-SG"))
    org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(LUCIA_ID).last().note.orEmpty().contains("Tỷ lệ kích hoạt tối đa là 100%"))
  }
  @org.junit.Test fun playerFacingSkillDescriptionsAreFullyVietnamese() {
    val all = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUCIA_ID)
      .flatMap(CompanionSkillCatalog::forCharacter)
    val forbidden = listOf(
      "DMG", "HP", "Party", "Entity", "SEARCH", "Exit", "Mana",
      "Game Master", " canon", " boss", "ACTIVE", "Base DMG"
    )
    all.forEach { skill ->
      val prose = listOfNotNull(skill.trigger, skill.effect, skill.note).joinToString(" ")
      forbidden.forEach { token ->
        org.junit.Assert.assertFalse(
          "${skill.name} still contains mixed-English description token: $token | $prose",
          prose.contains(token, ignoreCase = false)
        )
      }
    }
  }

}
