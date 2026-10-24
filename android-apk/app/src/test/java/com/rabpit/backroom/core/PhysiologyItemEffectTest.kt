package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PhysiologyItemEffectTest {
  private fun stateWithPhysiology(): GameState {
    val base = GameState.initial()
    val caoMinh = base.characters.getValue(CAO_MINH_ID).copy(
      physiology = PhysiologyState(
        minutesSinceFood = 240L,
        minutesSinceWater = 90L,
        minutesAwake = 720L,
        painState = "mild",
        infectionState = "none",
        thermalState = "normal"
      )
    )
    return base.copy(characters = base.characters + (CAO_MINH_ID to caoMinh))
  }

  private fun grant(
    state: GameState,
    id: String,
    name: String,
    metadata: Map<String, String>
  ): GameState {
    val result = StateReducer.execute(
      state,
      ItemCommand(
        commandId = "grant-$id",
        turnId = "TURN_1",
        actorId = CAO_MINH_ID,
        source = CommandSource.SYSTEM,
        operation = ItemCommand.Operation.PICKUP,
        itemId = id,
        itemName = name,
        metadata = metadata
      )
    )
    assertTrue(result.applied)
    return result.state
  }

  private fun use(state: GameState, commandId: String, itemId: String): ExecutionResult = StateReducer.execute(
    state,
    ItemCommand(
      commandId = commandId,
      turnId = "TURN_1",
      actorId = CAO_MINH_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = itemId,
      itemName = itemId
    )
  )

  @Test fun waterEffectResetsOnlyWaterCounter() {
    val granted = grant(
      stateWithPhysiology(),
      "water",
      "Chai nước",
      mapOf("physiologyEffect" to "WATER")
    )

    val result = use(granted, "use-water", ItemContentRules.variantId("water-bottle", ContentState.FULL))

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(CAO_MINH_ID).physiology
    assertEquals(240L, physiology.minutesSinceFood)
    assertEquals(0L, physiology.minutesSinceWater)
    assertEquals(720L, physiology.minutesAwake)
    assertTrue("physiology_water_recorded" in result.events)
  }

  @Test fun foodEffectResetsOnlyFoodCounter() {
    val granted = grant(
      stateWithPhysiology(),
      "food",
      "Hộp thức ăn",
      mapOf("physiologyEffect" to "FOOD")
    )

    val result = use(granted, "use-food", ItemContentRules.variantId("food-container", ContentState.FULL))

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(CAO_MINH_ID).physiology
    assertEquals(0L, physiology.minutesSinceFood)
    assertEquals(90L, physiology.minutesSinceWater)
    assertEquals(720L, physiology.minutesAwake)
    assertTrue("physiology_food_recorded" in result.events)
  }

  @Test fun combinedEffectsResetFoodAndWaterTogether() {
    val granted = grant(
      stateWithPhysiology(),
      "ration-gel",
      "Emergency ration gel",
      mapOf(
        "physiologyEffect" to "WATER,FOOD",
        "consumable" to "true"
      )
    )

    val result = use(granted, "use-ration", "ration-gel")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(CAO_MINH_ID).physiology
    assertEquals(0L, physiology.minutesSinceFood)
    assertEquals(0L, physiology.minutesSinceWater)
    assertFalse(result.state.inventories.getValue(CAO_MINH_ID).items.containsKey("ration-gel"))
    assertTrue("physiology_water_recorded" in result.events)
    assertTrue("physiology_food_recorded" in result.events)
  }

  @Test fun untaggedItemDoesNotMutatePhysiology() {
    val granted = grant(stateWithPhysiology(), "tool", "Small tool", emptyMap())
    val before = granted.characters.getValue(CAO_MINH_ID).physiology

    val result = use(granted, "use-tool", "tool")

    assertTrue(result.applied)
    assertEquals(before, result.state.characters.getValue(CAO_MINH_ID).physiology)
  }

  @Test fun invalidEffectRejectsUseWithoutInventoryOrPhysiologyMutation() {
    val granted = grant(
      stateWithPhysiology(),
      "bad-tonic",
      "Bad tonic",
      mapOf(
        "physiologyEffect" to "HEAL",
        "consumable" to "true"
      )
    )

    val result = use(granted, "use-bad-tonic", "bad-tonic")

    assertFalse(result.applied)
    assertEquals("physiology_effect_invalid", result.validation.reason)
    assertEquals(granted, result.state)
  }

  @Test fun failedUseDoesNotApplyPhysiologyEffect() {
    val granted = grant(
      stateWithPhysiology(),
      "empty-water",
      "Chai rỗng",
      mapOf("physiologyEffect" to "WATER")
    )

    val result = use(granted, "use-empty-water", "missing-water")

    assertFalse(result.applied)
    assertEquals("item_not_owned", result.validation.reason)
    assertEquals(granted, result.state)
  }

  @Test fun duplicateUseNeverAppliesPhysiologyTwice() {
    val granted = grant(
      stateWithPhysiology(),
      "water",
      "Chai nước",
      mapOf("physiologyEffect" to "WATER")
    )
    val command = ItemCommand(
      commandId = "same-use",
      turnId = "TURN_1",
      actorId = CAO_MINH_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = ItemContentRules.variantId("water-bottle", ContentState.FULL),
      itemName = "water"
    )

    val first = StateReducer.execute(granted, command)
    assertTrue(first.applied)
    assertEquals(0L, first.state.characters.getValue(CAO_MINH_ID).physiology.minutesSinceWater)

    val second = StateReducer.execute(first.state, command)
    assertFalse(second.applied)
    assertTrue(second.duplicate)
    assertEquals(first.state, second.state)
  }
}
