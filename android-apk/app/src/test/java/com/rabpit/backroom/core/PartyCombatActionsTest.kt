package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyCombatActionsTest {
  private fun fullParty(): GameState {
    var state = SpecialFollowersCanon.ensure(GameState.initial())
    state = LuciaCanon.ensure(state)
    state = AnNhienCanon.ensure(state)
    return state.copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID))
    )
  }

  @Test fun attackButtonResolvesEveryActivePartyMemberInOneCombatEvent() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    assertTrue(result.reply.contains("HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG:"))
    assertTrue(result.reply.contains("Kai Akechi"))
    assertTrue(result.reply.contains("Lucia"))
    assertTrue(result.reply.contains("Iris"))
    assertTrue(result.reply.contains("Syvial"))
    assertTrue(result.reply.contains("An Nhiên"))
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(result.reply.contains("Iris thực hiện lệnh TẤN CÔNG"))
    assertTrue(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
    assertTrue(result.reply.contains("An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ"))
    assertFalse(result.reply.substringAfter("An Nhiên thực hiện lệnh TẤN CÔNG").contains("sát thương vũ khí"))
    assertTrue(after == null || after.eventCounter == before.eventCounter + 1)
  }

  @Test fun evadeButtonMovesTheWholePartyAndDoesNotFireOffensiveSkills() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("HÀNH ĐỘNG CỦA ĐỘI - NÉ TRÁNH:"))
    assertTrue(result.reply.contains("Kai Akechi"))
    assertTrue(result.reply.contains("Lucia"))
    assertTrue(result.reply.contains("Iris"))
    assertTrue(result.reply.contains("Syvial"))
    assertTrue(result.reply.contains("An Nhiên"))
    for (forbidden in listOf(
      "Guilty Crown Override", "Lucia \"Lục\" bắn hỗ trợ", "Iris thực hiện lệnh TẤN CÔNG",
      "Syvial thực hiện lệnh TẤN CÔNG", "Twosome Time tự động kích hoạt", "Rift Sever tự động kích hoạt",
      "Dead Angle: Iris", "Counterphase: Syvial"
    )) assertFalse(forbidden, result.reply.contains(forbidden))
  }

  @Test fun fleeButtonWithdrawsTheWholePartyWithoutDroppingFollowersOrFiring() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.escapeProgress" to "95"
    ))
    val beforeMembers = state.party.memberIds
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng bỏ chạy")
    assertTrue(result.handled)
    assertTrue(result.escaped)
    assertTrue(result.reply.contains("HÀNH ĐỘNG CỦA ĐỘI - BỎ CHẠY:"))
    assertEquals(beforeMembers, result.state.party.memberIds)
    assertFalse(result.reply.contains("Guilty Crown Override"))
    assertFalse(result.reply.contains("Lucia \"Lục\" bắn hỗ trợ"))
    assertFalse(result.reply.contains("Iris thực hiện lệnh TẤN CÔNG"))
    assertFalse(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
  }
}
