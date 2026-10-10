package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyCombatHealthTest {
  private fun party(vararg health: Pair<String, Int>): GameState {
    val base = GameState.initial()
    val characters = health.associate { (id, hp) ->
      val character = base.characters[id] ?: CharacterState(id = id, name = id)
      id to character.copy(vitalState = character.vitalState.copy(currentHp = hp))
    }
    return base.copy(
      characters = base.characters + characters,
      party = PartyState(memberIds = health.map { it.first })
    )
  }

  @Test fun activeRosterHonorsMembershipPresenceAndFourMemberLimit() {
    val initial = party(KAI_ID to 50, LUCIA_ID to 45, IRIS_ID to 0, SYVIAL_ID to 35, "extra" to 50)
    assertEquals(listOf(KAI_ID, LUCIA_ID, IRIS_ID, SYVIAL_ID), PartyCombatHealth.members(initial))
    assertEquals(listOf(KAI_ID, LUCIA_ID, SYVIAL_ID), PartyCombatHealth.actingMembers(initial))
    assertEquals(130, PartyCombatHealth.currentHp(initial))
    val separated = initial.characters.getValue(LUCIA_ID).copy(presence = CharacterPresence.SEPARATED)
    val withoutLucia = initial.copy(characters = initial.characters + (LUCIA_ID to separated))
    assertEquals(listOf(KAI_ID, SYVIAL_ID), PartyCombatHealth.actingMembers(withoutLucia))
    assertEquals(85, PartyCombatHealth.currentHp(withoutLucia))
  }

  @Test fun oneHitIsSharedExactlyOnce() {
    val initial = party(KAI_ID to 50, LUCIA_ID to 45, IRIS_ID to 40, SYVIAL_ID to 35)
    val hit = PartyCombatHealth.applySharedDamage(initial, 21, round = 0)
    assertEquals(21, hit.appliedDamage)
    assertEquals(mapOf(KAI_ID to 6, LUCIA_ID to 5, IRIS_ID to 5, SYVIAL_ID to 5), hit.damageByMember)
    assertEquals(149, PartyCombatHealth.currentHp(hit.state))
    assertEquals(170, PartyCombatHealth.currentHp(initial))
  }

  @Test fun lowHpOverflowIsRedistributedWithoutLosingDamage() {
    val initial = party(KAI_ID to 2, LUCIA_ID to 10, IRIS_ID to 10)
    val hit = PartyCombatHealth.applySharedDamage(initial, 18, round = 0)
    assertEquals(mapOf(KAI_ID to 2, LUCIA_ID to 8, IRIS_ID to 8), hit.damageByMember)
    assertEquals(4, PartyCombatHealth.currentHp(hit.state))
    assertEquals(listOf(LUCIA_ID, IRIS_ID), PartyCombatHealth.actingMembers(hit.state))
  }

  @Test fun oneAndTwoMemberPartiesTakeOneResolvedHit() {
    val solo = party(KAI_ID to 30)
    val soloHit = PartyCombatHealth.applySharedDamage(solo, 12, round = 0)
    assertEquals(mapOf(KAI_ID to 12), soloHit.damageByMember)
    assertEquals(18, PartyCombatHealth.currentHp(soloHit.state))

    val duo = party(KAI_ID to 30, LUCIA_ID to 30)
    val duoHit = PartyCombatHealth.applySharedDamage(duo, 13, round = 0)
    assertEquals(mapOf(KAI_ID to 7, LUCIA_ID to 6), duoHit.damageByMember)
    assertEquals(47, PartyCombatHealth.currentHp(duoHit.state))
  }

  @Test fun maxHpStillIncludesFallenActiveMember() {
    val initial = party(KAI_ID to 10, LUCIA_ID to 0)
    val expected = CharacterStatEngine.effective(initial, KAI_ID).maxHp +
      CharacterStatEngine.effective(initial, LUCIA_ID).maxHp
    assertEquals(expected, PartyCombatHealth.maxHp(initial))
    assertEquals(10, PartyCombatHealth.currentHp(initial))
  }

  @Test fun remainderRotatesAndOverkillStopsAtZero() {
    val initial = party(KAI_ID to 10, LUCIA_ID to 10, IRIS_ID to 10)
    val rotated = PartyCombatHealth.applySharedDamage(initial, 5, round = 1)
    assertEquals(mapOf(KAI_ID to 1, LUCIA_ID to 2, IRIS_ID to 2), rotated.damageByMember)
    val overkill = PartyCombatHealth.applySharedDamage(initial, Int.MAX_VALUE, round = 4)
    assertEquals(30, overkill.appliedDamage)
    assertEquals(0, PartyCombatHealth.currentHp(overkill.state))
    assertTrue(PartyCombatHealth.actingMembers(overkill.state).isEmpty())
    assertEquals(0, PartyCombatHealth.applySharedDamage(initial, -10, round = 0).appliedDamage)
  }
}
