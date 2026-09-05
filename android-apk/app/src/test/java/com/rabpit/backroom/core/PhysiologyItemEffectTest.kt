package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PhysiologyItemEffectTest {
  private fun stateWithPhysiology(): GameState {
    val base = GameState.initial()
    val kai = base.characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(
        minutesSinceFood = 240L,
        minutesSinceWater = 90L,
        minutesAwake = 720L,
        painState = "mild",
        infectionState = "none",
        thermalState = "normal"
      )
    )
    return base.copy(characters = base.characters + (KAI_ID to kai))
  }

  private fun grant(
    state: GameState,
    id: String,
    name: String,
    metadata: Map<String, String>
  ): GameState {
    val sourceId = "test:$id"
    val item = ItemStack(
      itemId = id,
      name = name,
      metadata = metadata + mapOf(
        "loot.origin" to LootOrigin.EXPLORE_LOOT.name,
        "loot.sourceId" to sourceId,
        "loot.turnId" to "TURN_1"
      )
    )
    val result = StateReducer.execute(
      state,
      LootGrantCommand(
        commandId = "grant-$id",
        turnId = "TURN_1",
        actorId = KAI_ID,
        origin = LootOrigin.EXPLORE_LOOT,
        sourceId = sourceId,
        item = item
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
      actorId = KAI_ID,
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

    val result = use(granted, "use-water", "water-bottle:full")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
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

    val result = use(granted, "use-food", "food-container:full")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
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
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(0L, physiology.minutesSinceFood)
    assertEquals(0L, physiology.minutesSinceWater)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("ration-gel"))
    assertTrue("physiology_water_recorded" in result.events)
    assertTrue("physiology_food_recorded" in result.events)
  }

  @Test fun untaggedItemDoesNotMutatePhysiology() {
    val granted = grant(stateWithPhysiology(), "tool", "Small tool", emptyMap())
    val before = granted.characters.getValue(KAI_ID).physiology

    val result = use(granted, "use-tool", "tool")

    assertTrue(result.applied)
    assertEquals(before, result.state.characters.getValue(KAI_ID).physiology)
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
    assertEquals("item_effect_invalid", result.validation.reason)
    assertEquals(granted, result.state)
  }

  @Test fun failedUseDoesNotApplyPhysiologyEffect() {
    val granted = grant(
      stateWithPhysiology(),
      "empty-water",
      "Chai rỗng",
      mapOf("physiologyEffect" to "WATER")
    )

    val result = use(granted, "use-empty-water", "water-bottle:empty")

    assertFalse(result.applied)
    assertEquals("item_content_empty", result.validation.reason)
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
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = "water-bottle:full",
      itemName = "water-bottle:full"
    )

    val first = StateReducer.execute(granted, command)
    assertTrue(first.applied)
    assertEquals(0L, first.state.characters.getValue(KAI_ID).physiology.minutesSinceWater)

    val second = StateReducer.execute(first.state, command)
    assertFalse(second.applied)
    assertTrue(second.duplicate)
    assertEquals(first.state, second.state)
  }
}
