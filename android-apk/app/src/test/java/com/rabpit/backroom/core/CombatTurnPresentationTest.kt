package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CombatTurnPresentationTest {
  @Test fun actorCuesFollowActualPartyOrderIncludingMissesWithoutChangingRoundCounter() {
    var state = SpecialFollowersCanon.ensure(GameState.initial())
    state = LuciaCanon.ensure(state)
    state = state.copy(party = PartyState(memberIds = listOf(IRIS_ID, LUCIA_ID, KAI_ID)))
    val started = CombatRuntime.start(state, "diep_minh")
    val before = CombatRuntime.active(started)!!.eventCounter
    val result = CombatRuntime.resolve(started, "EXECUTE", "Cả Party cùng tấn công")
    val actors = result.turnCues.filter { it.kind == "actor" }
    assertEquals(listOf(IRIS_ID, LUCIA_ID, KAI_ID), actors.map { it.actorId })
    assertEquals(listOf("Iris", "Lucia", "Cao Minh"), actors.map { it.actorName })
    assertEquals(1, actors.map { it.waveNumber }.distinct().size)
    assertTrue(actors.zipWithNext().all { it.first.eventIndex <= it.second.eventIndex })
    assertTrue(result.turnCues.all { it.eventIndex in 0..result.feedback.size })
    assertEquals(before + 1, CombatRuntime.active(result.state)!!.eventCounter)
    assertEquals(2, CombatRuntime.toJson(result.state)!!.getInt("round"))
  }

  @Test fun anNhienGetsOwnNonWeaponCueAndFallenMemberIsSkipped() {
    var state = AnNhienCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, AN_NHIEN_ID)))
    state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 0)
    val result = CombatRuntime.resolve(CombatRuntime.start(state, "diep_minh"),
      "EXECUTE", "Cả Party cùng tấn công")
    assertEquals(listOf(KAI_ID, AN_NHIEN_ID), result.turnCues.filter { it.kind == "actor" }.map { it.actorId })
    assertTrue(result.reply.contains("An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ"))
  }

  @Test fun waveCuesSeparateTargetsWithoutExtraPartyActions() {
    var state = CombatRuntime.startWaves(GameState.initial(), listOf("hound", "diep_minh"))
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "1"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertEquals(listOf("actor", "wave"), result.turnCues.map { it.kind })
    assertEquals(1, result.turnCues[0].waveNumber)
    assertEquals(2, result.turnCues[1].waveNumber)
    assertEquals("Diệp Minh", result.turnCues[1].entityName)
    assertFalse(result.turnCues.any { it.kind == "entity" })
    assertEquals(2, CombatRuntime.toJson(result.state)!!.getInt("waveNumber"))
  }

  @Test fun passiveCommandsPublishOnePartyCueAndEnemyCueWithoutInventingExtraTurns() {
    val state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    assertEquals(listOf("party", "entity"), result.turnCues.map { it.kind })
    assertTrue(result.turnCues.all { it.eventIndex in 0..result.feedback.size })
    assertEquals(1, result.turnCues.first().waveNumber)
  }
}
