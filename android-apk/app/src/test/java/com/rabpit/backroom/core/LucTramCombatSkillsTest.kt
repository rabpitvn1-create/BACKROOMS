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

  @Test fun localDomainRequiresFinalizedSsfConfirmedTichQuangAndConsumesOwnAttack() {
    val action = PokerDiceCore.DIRECT_COMBAT_ACTION
    fun withSsf(state: GameState): GameState = state.copy(metadata = state.metadata + mapOf(
      "poker.dice.encounter" to CombatRuntime.active(state)!!.encounterId,
      "poker.dice.action" to action,
      "poker.dice.values" to "1,2,3,4,5",
      "poker.dice.held" to "0,0,0,0,0",
      "poker.dice.finalized" to "true",
      "poker.dice.hand" to "SSF"
    ))
    val without = CombatRuntime.start(party(), "diep_minh").let {
      it.copy(metadata = it.metadata + mapOf("combat.opening" to "2",
        "combat.entityHp" to "50000", "combat.entityMaxHp" to "50000"))
    }
    val noSword = CombatRuntime.resolve(withSsf(without), "EXECUTE", action)
    assertFalse(noSword.reply.contains("Thiên Kiếm Định Giới"))

    val withSword = CombatRuntime.start(party(sword = true), "diep_minh").let {
      it.copy(metadata = it.metadata + mapOf("combat.opening" to "2",
        "combat.entityHp" to "50000", "combat.entityMaxHp" to "50000"))
    }
    val noHand = CombatRuntime.resolve(withSword, "EXECUTE", action)
    assertFalse(noHand.reply.contains("Thiên Kiếm Định Giới"))
    val first = CombatRuntime.resolve(withSsf(withSword), "EXECUTE", action)
    assertTrue(first.reply.contains("Thiên Kiếm Định Giới"))
    assertTrue(first.state.metadata.containsKey("combat.lucTramDomainWave"))
    // The domain spends her actor action; an independent response-phase counter remains valid.
    assertTrue(first.feedback.none { it.actorId == LUC_TRAM_ID && it.target == "entity" && it.phase == "actor" })
    assertEquals(CombatRuntime.Cover.HARD, CombatRuntime.active(first.state)!!.cover)
    val second = CombatRuntime.resolve(withSsf(first.state), "EXECUTE", action)
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

  @Test fun caoMinhDamageOverTimeIsNotAttributedToLastPartyMember() {
    val started = CombatRuntime.start(party(), "diep_minh")
    val status = started.copy(metadata = started.metadata + ("combat.caoMinhBleedTurns" to "2"))
    val result = CombatRuntime.resolve(status, "EXECUTE", "Cả Party tấn công")
    val dot = result.feedback.filter { it.skill == "Huyết Ma Tứ Liên" && it.target == "entity" }
    assertTrue("Expected bleed tick when Cao Minh DoT is active", dot.isNotEmpty())
    assertTrue(dot.all { it.actorId == CAO_MINH_ID })
    assertFalse(dot.any { it.actorId == LUC_TRAM_ID })
  }
}
