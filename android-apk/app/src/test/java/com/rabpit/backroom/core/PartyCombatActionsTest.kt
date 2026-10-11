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
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, IRIS_ID, SYVIAL_ID))
    )
  }

  @Test fun attackButtonResolvesEveryActivePartyMemberInOneCombatEvent() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    assertTrue(result.reply.contains("PARTY ACTION TẤN CÔNG:"))
    assertTrue(result.reply.contains("Cao Minh"))
    assertTrue(result.reply.contains("Lucia"))
    assertTrue(result.reply.contains("Iris"))
    assertTrue(result.reply.contains("Syvial"))
    assertFalse(result.reply.contains("PARTY TURN 5/"))
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(result.reply.contains("Iris thực hiện lệnh TẤN CÔNG"))
    assertTrue(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
    assertFalse(result.reply.contains("An Nhiên thực hiện lệnh TẤN CÔNG"))
    assertTrue(after == null || after.eventCounter == before.eventCounter + 1)
  }

  @Test fun evadeButtonMovesTheWholePartyAndDoesNotFireOffensiveSkills() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("PARTY ACTION NÉ TRÁNH:"))
    assertTrue(result.reply.contains("Cao Minh"))
    assertTrue(result.reply.contains("Lucia"))
    assertTrue(result.reply.contains("Iris"))
    assertTrue(result.reply.contains("Syvial"))
    assertFalse(result.reply.contains("An Nhiên"))
    for (forbidden in listOf(
      "Huyết Ma Nhị Thập Tứ Trảm", "Lucia \"Lục\" bắn hỗ trợ", "Iris thực hiện lệnh TẤN CÔNG",
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
    assertTrue(result.reply.contains("PARTY ACTION BỎ CHẠY:"))
    assertEquals(beforeMembers, result.state.party.memberIds)
    assertFalse(result.reply.contains("Huyết Ma Nhị Thập Tứ Trảm"))
    assertFalse(result.reply.contains("Lucia \"Lục\" bắn hỗ trợ"))
    assertFalse(result.reply.contains("Iris thực hiện lệnh TẤN CÔNG"))
    assertFalse(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
  }
  @Test fun combatTurnsFollowActualRosterOrderAndDoNotRepeatTheDiceRound() {
    var state = fullParty().copy(party = PartyState(memberIds = listOf(IRIS_ID, LUCIA_ID, KAI_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val before = CombatRuntime.active(state)!!.eventCounter
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.handled)
    val reply = result.reply
    val markers = listOf("PARTY TURN 1/3: Iris", "PARTY TURN 2/3: Lucia", "PARTY TURN 3/3: Cao Minh")
    assertTrue(reply, markers.all { reply.contains(it) })
    assertTrue(reply.indexOf(markers[0]) < reply.indexOf(markers[1]))
    assertTrue(reply.indexOf(markers[1]) < reply.indexOf(markers[2]))
    assertEquals(before + 1, CombatRuntime.active(result.state)!!.eventCounter)
    assertFalse(reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
  }

  @Test fun fallenAndOutOfPartyCharactersGetNoAttackSlot() {
    var state = fullParty().copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, IRIS_ID)))
    state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 0)
    state = CombatRuntime.start(state, "diep_minh")
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.reply, result.reply.contains("PARTY TURN 1/2: Cao Minh"))
    assertTrue(result.reply, result.reply.contains("PARTY TURN 2/2: Iris"))
    assertFalse(result.reply.contains("Lucia \"Lục\" bắn hỗ trợ"))
    assertFalse(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
  }

  @Test fun anNhienUsesOneSupportSlotAndNeverWeaponDamage() {
    var state = fullParty().copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, AN_NHIEN_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.reply, result.reply.contains("PARTY TURN 3/3: An Nhiên"))
    assertTrue(result.reply.contains("An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ"))
    assertFalse(result.reply.contains("PARTY TURN 4/"))
  }

  @Test fun rosterCapPreventsFifthCharacterFromActingOrBuffing() {
    var state = fullParty().copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertFalse(result.reply.contains("PARTY TURN 5/"))
    assertFalse(result.reply.contains("An Nhiên dùng Quăng"))
    assertFalse(result.reply.contains("An Nhiên thực hiện lệnh TẤN CÔNG"))
  }

}
