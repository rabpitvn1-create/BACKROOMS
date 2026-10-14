package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterCombatResolutionTest {
  private fun boosted(base: GameState, transform: (CharacterStatProfile) -> CharacterStatProfile): GameState {
    val c = base.characters.getValue(CAO_MINH_ID)
    return base.copy(characters = base.characters + (CAO_MINH_ID to c.copy(statProfile = transform(c.statProfile))))
  }

  private fun hit(state: GameState, damage: Int = 10, dodgeRoll: Int = 9999, critRoll: Int = 9999) =
    PartyCombatHealth.applyStatAdjustedDamage(
      state, damage, 0,
      dodgeRoll = { dodgeRoll }, criticalRoll = { critRoll }
    )

  @Test fun evasionCanAvoidTheWholeTargetShare() {
    val base = GameState.initial()
    val highEva = boosted(base) { it.copy(eva = 100) }
    assertTrue(hit(base, dodgeRoll = 1000).appliedDamage > 0)
    val evaded = hit(highEva, dodgeRoll = 1000)
    assertEquals(0, evaded.appliedDamage)
    assertEquals(listOf(CAO_MINH_ID), evaded.dodgedIds)
  }

  @Test fun dexReducesCriticalChanceAndCriticalDamageUsesFourHundredPercent() {
    val base = GameState.initial()
    val highDex = boosted(base) { it.copy(dex = 100) }
    val critical = hit(base, damage = 10, critRoll = 100)
    val resisted = hit(highDex, damage = 10, critRoll = 100)
    assertEquals(listOf(CAO_MINH_ID), critical.criticalIds)
    assertTrue(critical.appliedDamage > resisted.appliedDamage)
    assertTrue(resisted.criticalIds.isEmpty())
    assertEquals(400, CharacterStatCore.CRITICAL_DAMAGE_PERCENT)
  }

  @Test fun vitDefenseMitigatesNonCriticalPhysicalHit() {
    val base = GameState.initial()
    val highVit = boosted(base) { it.copy(vit = 70) }
    assertTrue(hit(highVit, damage = 40).appliedDamage < hit(base, damage = 40).appliedDamage)
    assertTrue(CharacterStatEngine.effective(highVit, CAO_MINH_ID).maxHp >
      CharacterStatEngine.effective(base, CAO_MINH_ID).maxHp)
  }

  @Test fun dodgedShareIsNotTransferredToOtherMembers() {
    val initial = GameState.initial()
    val party = initial.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID)))
    val hit = PartyCombatHealth.applyStatAdjustedDamage(
      party, 10, 0, dodgeRoll = { index -> if (index == 1) 0 else 9999 },
      criticalRoll = { 9999 }
    )
    assertEquals(listOf(IRIS_ID), hit.dodgedIds)
    assertEquals(5, hit.appliedDamage)
    assertEquals(5, hit.damageByMember[CAO_MINH_ID])
    assertNull(hit.damageByMember[IRIS_ID])
  }

  @Test fun quickStepBuffAddsToCaoMinhEvasionRatherThanEnemyMissChance() {
    val state = GameState.initial()
    assertTrue(hit(state, dodgeRoll = 1000).appliedDamage > 0)
    val protected = PartyCombatHealth.applyStatAdjustedDamage(
      state, 10, 0, dodgeRoll = { 1000 }, criticalRoll = { 9999 }, leaderEvasionBonusPercent = 30
    )
    assertEquals(0, protected.appliedDamage)
  }
}
