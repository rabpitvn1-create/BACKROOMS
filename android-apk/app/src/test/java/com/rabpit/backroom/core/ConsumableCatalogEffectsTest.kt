package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ConsumableCatalogEffectsTest {
  private fun stateWithItem(
    itemId: String,
    effects: String,
    physiology: PhysiologyState = PhysiologyState(
      minutesSinceFood = 3000L,
      minutesSinceWater = 2000L,
      minutesAwake = 1500L
    ),
    characterTransform: (CharacterState) -> CharacterState = { it }
  ): GameState {
    val base = GameState.initial()
    var kai = base.characters.getValue(KAI_ID).copy(physiology = physiology)
    kai = characterTransform(kai)
    val item = ItemStack(
      itemId = itemId,
      name = itemId,
      quantity = 1,
      metadata = mapOf(
        "catalog.category" to "CONSUMABLE",
        "catalog.effects" to effects,
        "catalog.transferable" to "true",
        "catalog.discardable" to "true",
        "catalog.maxStack" to "9999"
      )
    )
    return base.copy(
      characters = base.characters + (KAI_ID to kai),
      inventories = base.inventories + (KAI_ID to InventoryState(KAI_ID, mapOf(itemId to item)))
    )
  }

  private fun use(state: GameState, itemId: String): ExecutionResult = StateReducer.execute(
    state,
    ItemCommand(
      commandId = "use-$itemId",
      turnId = "TURN_EFFECT_TEST",
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = itemId,
      itemName = itemId
    )
  )

  @Test fun additiveSurvivalEffectsUseCatalogMetadataAndConsumeItem() {
    val state = stateWithItem("frozen-fruits", "FOOD+20,REST+25")
    val result = use(state, "frozen-fruits")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(2136L, physiology.minutesSinceFood)
    assertEquals(2000L, physiology.minutesSinceWater)
    assertEquals(960L, physiology.minutesAwake)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("frozen-fruits"))
  }

  @Test fun almondStyleEffectsRecoverFoodAndWaterByConfiguredPercent() {
    val state = stateWithItem("almond-test", "WATER+40,FOOD+10")
    val result = use(state, "almond-test")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(2568L, physiology.minutesSinceFood)
    assertEquals(848L, physiology.minutesSinceWater)
  }

  @Test fun hpEffectHealsButNeverExceedsMaxHp() {
    val state = stateWithItem(
      "agrugua-fruit",
      "HP+30",
      characterTransform = { character ->
        CombatProgression.write(character, CombatProgression.read(character).copy(currentHp = 10))
      }
    )
    val result = use(state, "agrugua-fruit")

    assertTrue(result.applied)
    assertEquals(40, CombatProgression.read(result.state.characters.getValue(KAI_ID)).currentHp)
  }

  @Test fun bandageClearsBleedingStatusAndBleedingInjury() {
    val bleed = StatusEffect(
      id = "bleeding-left-arm",
      type = "BLEED",
      source = "combat",
      persistent = true
    )
    var state = stateWithItem(
      "bandage",
      "HP+10,CLEAR_BLEED",
      characterTransform = { character ->
        CombatProgression.write(
          character.copy(
            injuries = listOf("Bleeding cut on left arm", "Bruised shoulder"),
            statusIds = setOf(bleed.id)
          ),
          CombatProgression.read(character).copy(currentHp = 20)
        )
      }
    )
    state = state.copy(statuses = mapOf(bleed.id to bleed))

    val result = use(state, "bandage")

    assertTrue(result.applied)
    val kai = result.state.characters.getValue(KAI_ID)
    assertEquals(30, CombatProgression.read(kai).currentHp)
    assertFalse(bleed.id in kai.statusIds)
    assertFalse(bleed.id in result.state.statuses)
    assertEquals(listOf("Bruised shoulder"), kai.injuries)
  }

  @Test fun turquoiseVialClearsOnlyMildSickness() {
    val mild = StatusEffect(
      id = "sickness-mild",
      type = "SICKNESS",
      source = "world",
      persistent = true,
      metadata = mapOf("severity" to "mild")
    )
    val severe = StatusEffect(
      id = "infection-severe",
      type = "INFECTION",
      source = "world",
      persistent = true,
      metadata = mapOf("severity" to "severe")
    )
    var state = stateWithItem(
      "dark-reparation-vial-turquoise",
      "HP+20,CLEAR_MILD_SICKNESS",
      physiology = PhysiologyState(
        minutesSinceFood = 0L,
        minutesSinceWater = 0L,
        minutesAwake = 0L,
        infectionState = "mild"
      ),
      characterTransform = { character ->
        character.copy(statusIds = setOf(mild.id, severe.id))
      }
    )
    state = state.copy(statuses = mapOf(mild.id to mild, severe.id to severe))

    val result = use(state, "dark-reparation-vial-turquoise")

    assertTrue(result.applied)
    val kai = result.state.characters.getValue(KAI_ID)
    assertNull(kai.physiology.infectionState)
    assertFalse(mild.id in kai.statusIds)
    assertTrue(severe.id in kai.statusIds)
    assertFalse(mild.id in result.state.statuses)
    assertTrue(severe.id in result.state.statuses)
  }

  @Test fun invalidCatalogEffectIsAtomic() {
    val state = stateWithItem("bad-item", "HEAL+999")
    val result = use(state, "bad-item")

    assertFalse(result.applied)
    assertEquals("item_effect_invalid", result.validation.reason)
    assertEquals(state, result.state)
  }
}
