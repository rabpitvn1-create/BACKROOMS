package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CombatStatusLifecycleTest {
  @Test fun bleedDamagesOncePerTurnAndExpires() {
    val fresh = GameState.initial()
    val leader = fresh.characters.getValue(CAO_MINH_ID)
    val effect = StatusEffect("bleed-1", "BLEED", "hound", durationTurns = 2)
    val state = fresh.copy(
      characters = fresh.characters + (CAO_MINH_ID to leader.copy(statusIds = setOf(effect.id))),
      statuses = mapOf(effect.id to effect)
    )
    val before = leader.vitalState.currentHp
    val first = CombatStatusLifecycle.advance(state)
    assertTrue(first.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp < before)
    assertEquals(1, first.state.statuses.getValue(effect.id).durationTurns)
    assertEquals(1, first.feedback.size)
    val second = CombatStatusLifecycle.advance(first.state)
    assertFalse(second.state.statuses.containsKey(effect.id))
    assertFalse(effect.id in second.state.characters.getValue(CAO_MINH_ID).statusIds)
    assertEquals(1, second.feedback.size)
    val third = CombatStatusLifecycle.advance(second.state)
    assertTrue(third.feedback.isEmpty())
  }

  @Test fun statusDoesNotDamageOutOfPartyCharacters() {
    val initial = GameState.initial()
    val target = initial.characters.getValue(SYVIAL_ID)
    val effect = StatusEffect("poison-s", "POISON", "entity", durationTurns = 1)
    val state = initial.copy(characters=initial.characters + (SYVIAL_ID to target.copy(statusIds=setOf(effect.id))),
      statuses=mapOf(effect.id to effect))
    val result = CombatStatusLifecycle.advance(state)
    assertTrue(result.feedback.isEmpty())
    assertEquals(target.vitalState.currentHp, result.state.characters.getValue(SYVIAL_ID).vitalState.currentHp)
  }
}
