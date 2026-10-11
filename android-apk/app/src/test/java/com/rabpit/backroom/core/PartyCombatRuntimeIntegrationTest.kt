package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyCombatRuntimeIntegrationTest {
  private fun twoPersonEncounter(entityKey: String): GameState {
    var state = LuciaCanon.ensure(GameState.initial())
    state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    for (id in listOf(KAI_ID, LUCIA_ID)) {
      state = CharacterStatEngine.setCurrentHp(state, id, CharacterStatEngine.effective(state, id).maxHp)
    }
    return CombatRuntime.start(state, entityKey)
  }

  @Test fun combatProjectionUsesPartyHpButSnapshotKeepsCanonicalLeaderHp() {
    val started = twoPersonEncounter("hound")
    val leaderHp = started.characters.getValue(KAI_ID).vitalState.currentHp
    val combat = CombatRuntime.active(started)!!
    assertEquals(leaderHp, combat.playerHp)
    assertEquals(CharacterStatEngine.effective(started, KAI_ID).maxHp, combat.playerMaxHp)

    val projected = CombatRuntime.toJson(started)!!
    assertEquals(PartyCombatHealth.currentHp(started), projected.getInt("playerHp"))
    assertEquals(PartyCombatHealth.maxHp(started), projected.getInt("playerMaxHp"))
    assertEquals(2, projected.getInt("partyMemberCount"))
    assertTrue(projected.getInt("playerHp") > combat.playerHp)
  }

  @Test fun ordinaryCounterattackDistributesOneHitAndPreservesLeaderPassiveHealing() {
    val started = twoPersonEncounter("hound")
    val maxKai = CharacterStatEngine.effective(started, KAI_ID).maxHp
    val maxLucia = CharacterStatEngine.effective(started, LUCIA_ID).maxHp
    val passiveHeal = maxOf(1, (maxKai * CaoMinhCombatPassive.HEAL_PERCENT + 50) / 100)
    var found = false

    for (counter in 0..200) {
      val candidate = started.copy(metadata = started.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(candidate, "SEARCH", "quan sát")
      if (!result.reply.contains("phản công: Party chịu")) continue

      val incoming = result.feedback.filter { it.phase == "entity" && it.target == "actor" }
      val amounts = incoming.groupBy { it.actorId }.mapValues { entry -> entry.value.sumOf { it.amount } }
      val total = incoming.sumOf { it.amount }
      assertTrue("One Entity hit must yield a positive total loss", total > 0)
      assertEquals(setOf(KAI_ID, LUCIA_ID), amounts.keys)
      assertTrue(result.reply.contains("Party chịu -$total HP"))
      assertEquals(maxLucia - amounts.getValue(LUCIA_ID),
        result.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
      assertEquals(minOf(maxKai, maxKai - amounts.getValue(KAI_ID) + passiveHeal),
        result.state.characters.getValue(KAI_ID).vitalState.currentHp)
      val projected = CombatRuntime.toJson(result.state)!!
      assertEquals(PartyCombatHealth.currentHp(result.state), projected.getInt("playerHp"))
      assertEquals(PartyCombatHealth.maxHp(result.state), projected.getInt("playerMaxHp"))
      found = true
      break
    }
    assertTrue("Expected an ordinary Hound counterattack within deterministic turns", found)
  }

  @Test fun lifeformBleedRemainsPersonalAndUsesLeaderMaxHpNotPartyMaxHp() {
    val started = twoPersonEncounter("blackroot_sentinel")
    val state = started.copy(metadata = started.metadata + ("combat.lifeformBleedTurns" to "1"))
    val expected = maxOf(1, (CharacterStatEngine.effective(state, KAI_ID).maxHp * 3 + 99) / 100)
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    val ticks = result.feedback.filter {
      it.target == "actor" && it.status == CombatRuntime.FeedbackStatus.BLEED
    }
    assertEquals(1, ticks.size)
    assertEquals(expected, ticks.single().amount)
    assertEquals(KAI_ID, ticks.single().actorId)
    assertFalse(result.reply.contains("Lifeform Bleed: Party"))
  }

  @Test fun zeroHpMemberIsExcludedFromPartyDamageAndVisualPool() {
    var started = twoPersonEncounter("hound")
    started = CharacterStatEngine.setCurrentHp(started, LUCIA_ID, 0)
    assertEquals(listOf(KAI_ID), PartyCombatHealth.actingMembers(started))
    assertEquals(CharacterStatEngine.effective(started, KAI_ID).maxHp,
      CombatRuntime.toJson(started)!!.getInt("playerHp"))
    assertEquals(PartyCombatHealth.maxHp(started), CombatRuntime.toJson(started)!!.getInt("playerMaxHp"))
  }
}
