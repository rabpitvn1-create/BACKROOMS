package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LucTramCombatSkillsTest {
  private fun party(sword: Boolean = false): GameState {
    val initial = GameState.initial()
    val prepared = initial.copy(party = initial.party.copy(memberIds = listOf(CAO_MINH_ID, LUC_TRAM_ID)))
    if (!sword) return prepared
    val equip = prepared.equipment.getValue(LUC_TRAM_ID)
    return prepared.copy(equipment = prepared.equipment +
      (LUC_TRAM_ID to equip.copy(slots = equip.slots + ("weapon" to LUC_TRAM_TICH_QUANG_ID))))
  }

  @Test fun equipmentCatalogKeepsRevivalUnknownUntilExplicitlyEquipped() {
    assertNotNull(EquipmentCatalog.definition(LUC_TRAM_TICH_QUANG_ID))
    assertNotNull(EquipmentCatalog.definition(LUC_TRAM_KIEM_KHAI_ID))
    assertTrue(EquipmentCatalog.startingLoadout(LUC_TRAM_ID).isEmpty())
    assertFalse(LucTramCanon.hasConfirmedSword(party()))
    assertTrue(LucTramCanon.hasConfirmedSword(party(sword = true)))
    assertEquals(6, CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).size)
  }

  @Test fun mindPassiveImprovesExistingReadWithoutSpawningKnowledge() {
    val solo = CombatRuntime.resolve(CombatRuntime.start(GameState.initial(), "clump"),
      "SEARCH", "quan sát kỹ chuyển động")
    val withLuc = CombatRuntime.resolve(CombatRuntime.start(party(), "clump"),
      "SEARCH", "quan sát kỹ chuyển động")
    assertTrue(withLuc.reply.contains("Thiên Kiếm Linh Tâm"))
    assertFalse(solo.reply.contains("Thiên Kiếm Linh Tâm"))
    assertEquals(1, CombatRuntime.active(solo.state)!!.opening)
    assertEquals(2, CombatRuntime.active(withLuc.state)!!.opening)
  }

  @Test fun swordQiProtectsGroupOnEvadeAndCreatesOpeningOnGuard() {
    val evade = CombatRuntime.resolve(CombatRuntime.start(party(), "hound"),
      "EXECUTE", "Cả Party né tránh")
    assertTrue(evade.reply.contains("Vạn Kiếm Quy Tâm"))
    assertEquals(CombatRuntime.Cover.HARD, CombatRuntime.active(evade.state)!!.cover)
    val guard = CombatRuntime.resolve(CombatRuntime.start(party(), "hound"),
      "EXECUTE", "Cả Party phòng thủ")
    assertTrue(guard.reply.contains("Vạn Kiếm Quy Tâm"))
    assertTrue(CombatRuntime.active(guard.state)!!.opening >= 1)
  }

  @Test fun advanceAndBreakAreActualAttacksNotGenericFollowerFlavor() {
    val forward = CombatRuntime.resolve(CombatRuntime.start(party(), "diep_minh"),
      "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(forward.reply, forward.reply.contains("Bạch Hồng Quán Nhật"))
    assertEquals(CombatRuntime.RangeBand.CLOSE, CombatRuntime.active(forward.state)!!.range)
    val close = CombatRuntime.start(party(), "diep_minh").let {
      it.copy(metadata = it.metadata + ("combat.range" to CombatRuntime.RangeBand.CLOSE.name))
    }
    val breakAttack = CombatRuntime.resolve(close, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(breakAttack.reply, breakAttack.reply.contains("Nhất Tuyến Phá Vọng"))
    assertFalse(breakAttack.reply.contains("Bạch Hồng Quán Nhật"))
  }

  @Test fun localDomainRequiresConfirmedTichQuangAndConsumesOwnAttack() {
    val without = CombatRuntime.start(party(), "diep_minh").let {
      it.copy(metadata = it.metadata + ("combat.opening" to "2"))
    }
    val noSword = CombatRuntime.resolve(without, "EXECUTE", "Cả Party tấn công")
    assertFalse(noSword.reply.contains("Thiên Kiếm Định Giới"))

    val withSword = CombatRuntime.start(party(sword = true), "diep_minh").let {
      it.copy(metadata = it.metadata + ("combat.opening" to "2"))
    }
    val first = CombatRuntime.resolve(withSword, "EXECUTE", "Cả Party tấn công")
    assertTrue(first.reply.contains("Thiên Kiếm Định Giới"))
    assertTrue(first.state.metadata.containsKey("combat.lucTramDomainWave"))
    assertTrue(first.feedback.none { it.actorId == LUC_TRAM_ID && it.target == "entity" })
    assertEquals(CombatRuntime.Cover.HARD, CombatRuntime.active(first.state)!!.cover)
    val second = CombatRuntime.resolve(first.state, "EXECUTE", "Cả Party tấn công")
    assertFalse(second.reply.contains("Thiên Kiếm Định Giới"))
  }

  @Test fun counterAfterMissNeverManufacturesAbsentSword() {
    val withSword = CombatRuntime.resolve(CombatRuntime.start(party(sword = true), "hound"),
      "EXECUTE", "Cả Party phòng thủ")
    assertTrue(withSword.reply, withSword.reply.contains("Tịch Quang Phản Kiếm"))
    val unarmed = CombatRuntime.resolve(CombatRuntime.start(party(), "hound"),
      "EXECUTE", "Cả Party phòng thủ")
    assertFalse(unarmed.reply.contains("Tịch Quang Phản Kiếm"))
    val solo = CombatRuntime.resolve(CombatRuntime.start(GameState.initial(), "hound"),
      "EXECUTE", "Cả Party phòng thủ")
    assertFalse(solo.reply.contains("Tịch Quang Phản Kiếm"))
  }

  @Test fun downedLuctramDoesNotCastAbilities() {
    val initial = party()
    val luc = initial.characters.getValue(LUC_TRAM_ID)
    val down = initial.copy(characters = initial.characters +
      (LUC_TRAM_ID to luc.copy(vitalState = luc.vitalState.copy(currentHp = 0))))
    val result = CombatRuntime.resolve(CombatRuntime.start(down, "hound"),
      "SEARCH", "quan sát kỹ chuyển động")
    assertFalse(result.reply.contains("Thiên Kiếm Linh Tâm"))
  }
}
