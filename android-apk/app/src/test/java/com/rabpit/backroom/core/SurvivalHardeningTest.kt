package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class SurvivalHardeningTest {
  private fun withPhysiology(p: PhysiologyState, hp: Int = 50): GameState {
    val state = GameState.initial()
    val leader = state.characters.getValue(CAO_MINH_ID)
    return state.copy(characters = state.characters + (CAO_MINH_ID to leader.copy(
      // These recovery tests represent a fresh run with known needs, not UNKNOWN data.
      physiology = p.copy(
        minutesSinceFood = p.minutesSinceFood ?: 0L,
        minutesSinceWater = p.minutesSinceWater ?: 0L,
        minutesAwake = p.minutesAwake ?: 0L),
      vitalState = leader.vitalState.copy(currentHp = hp)
    )))
  }

  private fun advance(state: GameState, minutes: Int, activity: TimeActivity = TimeActivity.NORMAL): GameState {
    val command = TimeAdvanceCommand("TIME-${state.time.elapsedSubjectiveMinutes}", "TURN_1", CAO_MINH_ID,
      source = CommandSource.SYSTEM, minutes = minutes, reason = "survival_test",
      activity = activity, restTargets = if (activity in setOf(TimeActivity.SLEEP, TimeActivity.REST)) setOf(CAO_MINH_ID) else emptySet())
    val result = TimeEngine.execute(state, command)
    assertTrue(result.toString(), result.applied)
    return result.state
  }

  @Test fun simultaneousNeedsDamageAddWithoutOverallCap() {
    val p = PhysiologyState(minutesSinceFood = 1500, minutesSinceWater = 800, minutesAwake = 1200)
    val result = advance(withPhysiology(p), 360)
    val c = result.characters.getValue(CAO_MINH_ID)
    assertEquals(40, c.vitalState.currentHp)
    assertEquals(2, c.physiology.recoverableFoodHp)
    assertEquals(4, c.physiology.recoverableWaterHp)
    assertEquals(4, c.physiology.recoverableSleepHp)
  }

  @Test fun elapsedSlicesHaveIdenticalDamageAndNeverDoubleApplyOnRetry() {
    val p = PhysiologyState(minutesSinceFood = 1500, minutesSinceWater = 800, minutesAwake = 1200)
    val single = advance(withPhysiology(p), 360)
    val half = advance(withPhysiology(p), 180)
    val split = advance(half, 180)
    assertEquals(single.characters.getValue(CAO_MINH_ID).vitalState.currentHp,
      split.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    val cmd = TimeAdvanceCommand("one", "TURN_1", CAO_MINH_ID,
      source = CommandSource.SYSTEM, minutes = 60, reason = "walk")
    val once = StateReducer.execute(withPhysiology(p), cmd)
    val retry = StateReducer.execute(once.state, cmd)
    assertTrue(retry.duplicate)
    assertEquals(once.state, retry.state)
  }

  @Test fun restingHealsOnlyEligibleSourceAndNeverCombatDamage() {
    val p = PhysiologyState(
      minutesSinceFood = 1500, minutesSinceWater = 0, minutesAwake = 0,
      recoverableFoodHp = 3, recoverableWaterHp = 2)
    val rested = advance(withPhysiology(p, hp = 40), 60, TimeActivity.REST)
    val c = rested.characters.getValue(CAO_MINH_ID)
    assertEquals(42, c.vitalState.currentHp)
    assertEquals(3, c.physiology.recoverableFoodHp)
    assertEquals(0, c.physiology.recoverableWaterHp)
    val later = advance(rested, 60)
    // Food deprivation crosses its 180-minute damage boundary at minute 1620.
    // Normal movement must not heal the remaining food debt; it causes 1 new HP loss.
    assertEquals(41, later.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(4, later.characters.getValue(CAO_MINH_ID).physiology.recoverableFoodHp)
  }

  @Test fun ordinaryHealingConsumesRestEligibilitySoItCannotDoubleHeal() {
    val initial = withPhysiology(PhysiologyState(recoverableFoodHp = 2, recoverableWaterHp = 3), hp = 40)
    val healed = CharacterStatEngine.setCurrentHp(initial, CAO_MINH_ID, 44)
    val p = healed.characters.getValue(CAO_MINH_ID).physiology
    assertEquals(0, p.recoverableFoodHp)
    assertEquals(1, p.recoverableWaterHp)
    val rested = advance(healed, 180, TimeActivity.REST)
    assertEquals(45, rested.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
  }

  @Test fun longRestAndCheckpointedRestHealIdenticallyWhenNeedCrossesThreshold() {
    val p = PhysiologyState(minutesSinceFood = 1400, minutesSinceWater = 0,
      minutesAwake = 0, recoverableFoodHp = 3)
    val single = advance(withPhysiology(p, hp = 40), 60, TimeActivity.REST)
    val first = advance(withPhysiology(p, hp = 40), 30, TimeActivity.REST)
    val split = advance(first, 30, TimeActivity.REST)
    assertEquals(41, single.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(split.characters.getValue(CAO_MINH_ID).vitalState.currentHp,
      single.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(split.characters.getValue(CAO_MINH_ID).physiology,
      single.characters.getValue(CAO_MINH_ID).physiology)
  }

  @Test fun arbitraryRestCheckpointsMatchSingleContinuousInterval() {
    val p = PhysiologyState(minutesSinceFood = 1407, minutesSinceWater = 0,
      minutesAwake = 0, recoverableFoodHp = 3)
    val longRest = advance(withPhysiology(p, hp = 40), 40, TimeActivity.REST)
    val part = advance(withPhysiology(p, hp = 40), 20, TimeActivity.REST)
    val split = advance(part, 20, TimeActivity.REST)
    assertEquals(41, longRest.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(longRest.characters.getValue(CAO_MINH_ID).vitalState,
      split.characters.getValue(CAO_MINH_ID).vitalState)
    assertEquals(longRest.characters.getValue(CAO_MINH_ID).physiology,
      split.characters.getValue(CAO_MINH_ID).physiology)
  }

  @Test fun interruptedRestKeepsOnlyEarnedRecoveryTimeAndDoesNotHealDuringMovement() {
    val p = PhysiologyState(recoverableFoodHp = 3, restRecoveryMinutes = 20)
    val began = withPhysiology(p, hp = 40)
    val moving = advance(began, 5, TimeActivity.EXERTION)
    assertEquals(40, moving.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(20, moving.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
    val rested = advance(moving, 10, TimeActivity.REST)
    assertEquals(41, rested.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(0, rested.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
  }

  @Test fun healingCannotLeaveMoreRestEligibleHpThanActuallyMissing() {
    val original = withPhysiology(PhysiologyState(
      recoverableFoodHp = 30, recoverableWaterHp = 20, recoverableSleepHp = 10), hp = 30)
    val healed = CharacterStatEngine.setCurrentHp(original, CAO_MINH_ID, 95)
    val c = healed.characters.getValue(CAO_MINH_ID)
    val maxHp = CharacterStatCore.effective(healed, CAO_MINH_ID).maxHp
    val ledger = c.physiology.recoverableFoodHp + c.physiology.recoverableWaterHp +
      c.physiology.recoverableSleepHp
    assertTrue(ledger <= maxHp - c.vitalState.currentHp)
    val full = CharacterStatEngine.setCurrentHp(original, CAO_MINH_ID, maxHp)
    assertEquals(0, full.characters.getValue(CAO_MINH_ID).physiology.recoverableFoodHp +
      full.characters.getValue(CAO_MINH_ID).physiology.recoverableWaterHp +
      full.characters.getValue(CAO_MINH_ID).physiology.recoverableSleepHp)
  }

  @Test fun restWhileHealthyCannotBankFreeHpRecoveryForLaterInjury() {
    val healthy = withPhysiology(PhysiologyState(), hp = 40)
    val idle = advance(healthy, 20, TimeActivity.REST)
    assertEquals(0, idle.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
    val injured = CharacterStatEngine.applySurvivalDamage(idle, CAO_MINH_ID, 1, "FOOD")
    val tooShort = advance(injured, 10, TimeActivity.REST)
    assertEquals(39, tooShort.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(10, tooShort.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
    val finished = advance(tooShort, 20, TimeActivity.REST)
    assertEquals(40, finished.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(0, finished.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
  }

  @Test fun fullyHealedDebtDoesNotBankUnusedRestTime() {
    val p = PhysiologyState(recoverableFoodHp = 1)
    val rested = advance(withPhysiology(p, hp = 40), 40, TimeActivity.REST)
    assertEquals(41, rested.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(0, rested.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
    val interrupted = advance(withPhysiology(PhysiologyState(recoverableFoodHp = 2), hp = 40),
      20, TimeActivity.REST)
    val bandaged = CharacterStatEngine.setCurrentHp(interrupted, CAO_MINH_ID, 42)
    assertEquals(0, bandaged.characters.getValue(CAO_MINH_ID).physiology.restRecoveryMinutes)
  }

  @Test fun simultaneousRestHealingOffsetsDamageBeforeFatality() {
    // A rest tick can earn one HP while an independent deprivation takes one.
    // The net result must preserve a living one-HP character.
    val p = PhysiologyState(minutesSinceFood = 1619, minutesSinceWater = 0,
      minutesAwake = 0, recoverableWaterHp = 1, restRecoveryMinutes = 29)
    val survived = advance(withPhysiology(p, hp = 1), 1, TimeActivity.REST)
    val c = survived.characters.getValue(CAO_MINH_ID)
    assertEquals(1, c.vitalState.currentHp)
    assertEquals(CharacterPresence.ACTIVE, c.presence)
    assertEquals(1, c.physiology.recoverableFoodHp)
    assertEquals(0, c.physiology.recoverableWaterHp)
  }

  @Test fun sleepingRestoresAwakeTimeProportionallyAndRestDoesNot() {
    val p = PhysiologyState(minutesSinceFood = 0, minutesSinceWater = 0, minutesAwake = 1000)
    val slept = advance(withPhysiology(p), 60, TimeActivity.SLEEP)
    assertEquals(880L, slept.characters.getValue(CAO_MINH_ID).physiology.minutesAwake)
    val rested = advance(withPhysiology(p), 60, TimeActivity.REST)
    assertEquals(1060L, rested.characters.getValue(CAO_MINH_ID).physiology.minutesAwake)
  }

  @Test fun severeThermalConditionsRaiseWaterNeedAndWorsenSleepQuality() {
    val p = PhysiologyState(minutesSinceFood = 0, minutesSinceWater = 0, minutesAwake = 1000, thermalState = "hot")
    val slept = advance(withPhysiology(p), 60, TimeActivity.SLEEP)
    val next = slept.characters.getValue(CAO_MINH_ID).physiology
    assertEquals(120L, next.minutesSinceWater)
    assertEquals(940L, next.minutesAwake)
  }

  @Test fun survivalDeathIsFinalWhileOrdinaryKnockoutIsNotReclassified() {
    val starving = withPhysiology(PhysiologyState(minutesSinceFood = 0,
      minutesSinceWater = 810, minutesAwake = 0), hp = 1)
    val after = advance(starving, 90)
    val c = after.characters.getValue(CAO_MINH_ID)
    assertEquals(CharacterPresence.DEAD, c.presence)
    assertEquals(CharacterCondition.DEAD, c.vitalState.condition)
    val combatKnockout = CharacterStatEngine.setCurrentHp(GameState.initial(), CAO_MINH_ID, 0)
    assertEquals(CharacterCondition.DEFEATED, combatKnockout.characters.getValue(CAO_MINH_ID).vitalState.condition)
  }

  @Test fun needPenaltiesDoNotChangeMaxHpOrPermanentStats() {
    val normal = withPhysiology(PhysiologyState())
    val depleted = withPhysiology(PhysiologyState(
      minutesSinceFood = 4000, minutesSinceWater = 2600, minutesAwake = 2000))
    val original = CharacterStatCore.effective(normal, CAO_MINH_ID)
    val weakened = CharacterStatCore.effective(depleted, CAO_MINH_ID)
    assertEquals(original.maxHp, weakened.maxHp)
    assertTrue(weakened.str < original.str)
    assertTrue(weakened.agi < original.agi)
    assertEquals(normal.characters.getValue(CAO_MINH_ID).statProfile,
      depleted.characters.getValue(CAO_MINH_ID).statProfile)
  }

  @Test fun saveLoadPreservesRecoveryProvenance() {
    val state = withPhysiology(PhysiologyState(
      minutesSinceFood = 42, recoverableFoodHp = 3, recoverableWaterHp = 4,
      restRecoveryMinutes = 17))
    val restored = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.characters.getValue(CAO_MINH_ID).physiology,
      restored.characters.getValue(CAO_MINH_ID).physiology)
  }
  @Test fun consumedPortionsStackByQuantityWithoutAutoFilling() {
    val item = OfflineItemRegistry.get("southern:ben-tre-coconut")!!
    var state = withPhysiology(PhysiologyState(minutesSinceWater = 500))
    val inventory = state.inventories.getValue(CAO_MINH_ID)
    state = state.copy(inventories = state.inventories + (CAO_MINH_ID to inventory.copy(
      items = mapOf(item.id to ItemStack(item.id, item.name, 2, item.metadata)))))
    val command = ItemCommand("drink-two", "TURN_1", CAO_MINH_ID,
      source = CommandSource.UI, operation = ItemCommand.Operation.USE,
      itemId = item.id, itemName = item.name, quantity = 2)
    val result = StateReducer.execute(state, command)
    assertTrue(result.toString(), result.applied)
    assertEquals(140L, result.state.characters.getValue(CAO_MINH_ID).physiology.minutesSinceWater)
    assertFalse(result.state.inventories.getValue(CAO_MINH_ID).items.containsKey(item.id))
  }

  @Test fun defeatedAndDeadExplorersCannotStartNewActions() {
    val defeated = CharacterStatEngine.setCurrentHp(GameState.initial(), CAO_MINH_ID, 0)
    val rejected = ActionRuntime.start(defeated, "s", "TURN_1", CAO_MINH_ID,
      ActionKind.EXECUTE, "khám phá")
    assertFalse(rejected.applied)
    assertEquals("action_actor_unavailable", rejected.error)
  }
}
