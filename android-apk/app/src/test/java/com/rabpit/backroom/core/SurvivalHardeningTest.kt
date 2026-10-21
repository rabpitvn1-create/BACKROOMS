package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class SurvivalHardeningTest {
  private fun withPhysiology(p: PhysiologyState, hp: Int = 50): GameState {
    val state = GameState.initial()
    val leader = state.characters.getValue(CAO_MINH_ID)
    return state.copy(characters = state.characters + (CAO_MINH_ID to leader.copy(
      physiology = p,
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
    assertEquals(42, later.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
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
