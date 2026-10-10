package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class HealingItemTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  private fun add(state: GameState, id: String, name: String, quantity: Int = 1): GameState {
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "add:$id:$quantity",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    return result.state
  }

  private fun use(state: GameState, id: String, name: String, quantity: Int = 1): ExecutionResult =
    InventoryEngine.execute(state, ItemCommand(
      commandId = "use:$id:$quantity",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))

  @Test fun bandageHealsExactlyTenAndConsumesOne() {
    var state = add(fresh(), BANDAGE_ID, "Băng gạc", 2)
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 40)
    val result = use(state, BANDAGE_ID, "Băng gạc")
    assertTrue(result.applied)
    assertEquals(50, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID).quantity)
    assertTrue(result.events.contains("hp_healed:10"))
  }

  @Test fun antisepticHealsTwentyAndClampsToEffectiveMaxHp() {
    var state = add(fresh(), ANTISEPTIC_ID, "Thuốc sát trùng")
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, maxHp - 5)
    val result = use(state, ANTISEPTIC_ID, "Thuốc sát trùng")
    assertTrue(result.applied)
    assertEquals(maxHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(ANTISEPTIC_ID))
    assertTrue(result.events.contains("hp_healed:5"))
  }

  @Test fun healingItemCannotReviveZeroHpAndIsNotConsumed() {
    var state = add(fresh(), ANTISEPTIC_ID, "Thuốc sát trùng")
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 0)
    val result = use(state, ANTISEPTIC_ID, "Thuốc sát trùng")
    assertFalse(result.applied)
    assertEquals("healing_target_defeated", result.validation.reason)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue(ANTISEPTIC_ID).quantity)
  }

  @Test fun healingItemsShareTheOrdinaryLootGate() {
    assertEquals("loot", HealingItems.DROP_ROLL_KEY)
    assertEquals(10, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(20, HealingItems.ANTISEPTIC_HEAL_HP)
  }
}
