package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PhysiologyEngineTest {
  private fun command(
    id: String,
    operation: PhysiologyCommand.Operation,
    targetId: String = KAI_ID,
    painState: String? = null,
    infectionState: String? = null,
    thermalState: String? = null
  ) = PhysiologyCommand(
    commandId = id,
    turnId = "TURN_1",
    actorId = KAI_ID,
    targetId = targetId,
    source = CommandSource.SYSTEM,
    operation = operation,
    painState = painState,
    infectionState = infectionState,
    thermalState = thermalState
  )

  @Test fun recordFoodWaterAndSleepResetOnlyTheirOwnTimers() {
    val kai = GameState.initial().characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(
        minutesSinceFood = 300L,
        minutesSinceWater = 120L,
        minutesAwake = 900L,
        painState = "mild"
      )
    )
    val initial = GameState.initial().copy(characters = mapOf(KAI_ID to kai))

    val afterFood = StateReducer.execute(initial, command("p-food", PhysiologyCommand.Operation.RECORD_FOOD))
    assertTrue(afterFood.applied)
    assertEquals(0L, afterFood.state.characters.getValue(KAI_ID).physiology.minutesSinceFood)
    assertEquals(120L, afterFood.state.characters.getValue(KAI_ID).physiology.minutesSinceWater)
    assertEquals(900L, afterFood.state.characters.getValue(KAI_ID).physiology.minutesAwake)

    val afterWater = StateReducer.execute(afterFood.state, command("p-water", PhysiologyCommand.Operation.RECORD_WATER))
    assertTrue(afterWater.applied)
    assertEquals(0L, afterWater.state.characters.getValue(KAI_ID).physiology.minutesSinceWater)
    assertEquals(900L, afterWater.state.characters.getValue(KAI_ID).physiology.minutesAwake)

    val afterSleep = StateReducer.execute(afterWater.state, command("p-sleep", PhysiologyCommand.Operation.RECORD_SLEEP))
    assertTrue(afterSleep.applied)
    val finalPhysiology = afterSleep.state.characters.getValue(KAI_ID).physiology
    assertEquals(0L, finalPhysiology.minutesSinceFood)
    assertEquals(0L, finalPhysiology.minutesSinceWater)
    assertEquals(0L, finalPhysiology.minutesAwake)
    assertEquals("mild", finalPhysiology.painState)
  }

  @Test fun conditionUpdatePatchesOnlyProvidedFieldsAndTrimsValues() {
    val kai = GameState.initial().characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(
        painState = "mild",
        infectionState = "none",
        thermalState = "normal"
      )
    )
    val state = GameState.initial().copy(characters = mapOf(KAI_ID to kai))
    val result = StateReducer.execute(state, command(
      "p-condition",
      PhysiologyCommand.Operation.UPDATE_CONDITION,
      painState = " severe ",
      thermalState = " cold "
    ))

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals("severe", physiology.painState)
    assertEquals("none", physiology.infectionState)
    assertEquals("cold", physiology.thermalState)
    assertEquals(listOf("physiology_condition_updated"), result.events)
  }

  @Test fun blankOrMissingConditionUpdateIsRejectedWithoutMutation() {
    val state = GameState.initial()
    val missing = StateReducer.execute(state, command("p-missing", PhysiologyCommand.Operation.UPDATE_CONDITION))
    val blank = StateReducer.execute(state, command("p-blank", PhysiologyCommand.Operation.UPDATE_CONDITION, painState = "   "))

    assertFalse(missing.applied)
    assertEquals("physiology_condition_required", missing.validation.reason)
    assertEquals(state, missing.state)
    assertFalse(blank.applied)
    assertEquals("physiology_condition_blank", blank.validation.reason)
    assertEquals(state, blank.state)
  }

  @Test fun deadTargetCannotReceivePhysiologyMutation() {
    val dead = CharacterState("dead", "Dead", presence = CharacterPresence.DEAD, physiology = PhysiologyState(minutesSinceWater = 100L))
    val state = GameState.initial().copy(characters = GameState.initial().characters + (dead.id to dead))
    val result = StateReducer.execute(state, command("p-dead", PhysiologyCommand.Operation.RECORD_WATER, targetId = dead.id))

    assertFalse(result.applied)
    assertEquals("physiology_target_dead", result.validation.reason)
    assertEquals(state, result.state)
  }

  @Test fun duplicatePhysiologyCommandNeverAppliesTwice() {
    val state = GameState.initial().copy(
      characters = mapOf(KAI_ID to GameState.initial().characters.getValue(KAI_ID).copy(
        physiology = PhysiologyState(minutesSinceFood = 180L)
      ))
    )
    val cmd = command("p-duplicate", PhysiologyCommand.Operation.RECORD_FOOD)
    val first = StateReducer.execute(state, cmd)
    val second = StateReducer.execute(first.state, cmd)

    assertTrue(first.applied)
    assertTrue(second.duplicate)
    assertFalse(second.applied)
    assertEquals(0L, second.state.characters.getValue(KAI_ID).physiology.minutesSinceFood)
  }
}
