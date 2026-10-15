package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class HealingItemTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  private fun add(state: GameState, id: String, name: String, quantity: Int = 1): GameState {
    return TestInventoryFixtures.place(state, id, name, quantity)
  }

  private fun use(state: GameState, id: String, name: String, quantity: Int = 1): ExecutionResult =
    InventoryEngine.execute(state, ItemCommand(
      commandId = "use:$id:$quantity",
      turnId = null,
      actorId = CAO_MINH_ID,
      source = CommandSource.UI,
      operation = ItemCommand.Operation.USE,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))

  @Test fun bandageHealsExactlyTenAndConsumesOne() {
    var state = add(fresh(), BANDAGE_ID, "Băng gạc", 2)
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, 40)
    val result = use(state, BANDAGE_ID, "Băng gạc")
    assertTrue(result.applied)
    assertEquals(50, result.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(1, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(BANDAGE_ID).quantity)
    assertTrue(result.events.contains("hp_healed:10"))
  }

  @Test fun antisepticHealsTwentyAndClampsToEffectiveMaxHp() {
    var state = add(fresh(), ANTISEPTIC_ID, "Thuốc sát trùng")
    val maxHp = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, maxHp - 5)
    val result = use(state, ANTISEPTIC_ID, "Thuốc sát trùng")
    assertTrue(result.applied)
    assertEquals(maxHp, result.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertFalse(result.state.inventories.getValue(CAO_MINH_ID).items.containsKey(ANTISEPTIC_ID))
    assertTrue(result.events.contains("hp_healed:5"))
  }

  @Test fun healingItemCannotReviveZeroHpAndIsNotConsumed() {
    var state = add(fresh(), ANTISEPTIC_ID, "Thuốc sát trùng")
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, 0)
    val result = use(state, ANTISEPTIC_ID, "Thuốc sát trùng")
    assertFalse(result.applied)
    assertEquals("healing_target_defeated", result.validation.reason)
    assertEquals(1, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(ANTISEPTIC_ID).quantity)
  }

  @Test fun healingItemsShareTheOrdinaryLootGate() {
    assertEquals(10, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(20, HealingItems.ANTISEPTIC_HEAL_HP)
  }
  @Test fun liquidPainHealsFifteenPercentOfEffectiveMaxHpPerItemAndConsumesQuantity() {
    var state = add(fresh(), LIQUID_PAIN_ID, "Liquid Pain", 3)
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, 1)
    val maxHp = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
    val result = use(state, LIQUID_PAIN_ID, "Liquid Pain", 2)
    assertTrue(result.applied)
    assertEquals((1 + (maxHp * 15 / 100) * 2).coerceAtMost(maxHp),
      result.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(1, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(LIQUID_PAIN_ID).quantity)
    assertEquals(state.time, result.state.time)
    assertEquals(state.turn, result.state.turn)
    assertEquals(15, HealingItems.healAmount(ItemStack(LIQUID_PAIN_ID, "Liquid Pain"), 101))
    assertEquals(30, HealingItems.healAmount(ItemStack(LIQUID_PAIN_ID, "Liquid Pain"), 200))
  }

  @Test fun liquidPainClampsHealingAndCannotRevive() {
    val initial = add(fresh(), LIQUID_PAIN_ID, "Liquid Pain")
    val maxHp = CharacterStatEngine.effective(initial, CAO_MINH_ID).maxHp
    val injured = CharacterStatEngine.setCurrentHp(initial, CAO_MINH_ID, maxHp - 1)
    val healed = use(injured, LIQUID_PAIN_ID, "Liquid Pain")
    assertTrue(healed.applied)
    assertEquals(maxHp, healed.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertFalse(healed.state.inventories.getValue(CAO_MINH_ID).items.containsKey(LIQUID_PAIN_ID))
    val defeated = CharacterStatEngine.setCurrentHp(initial, CAO_MINH_ID, 0)
    val rejected = use(defeated, LIQUID_PAIN_ID, "Liquid Pain")
    assertFalse(rejected.applied)
    assertEquals("healing_target_defeated", rejected.validation.reason)
    assertEquals(defeated, rejected.state)
  }
}

