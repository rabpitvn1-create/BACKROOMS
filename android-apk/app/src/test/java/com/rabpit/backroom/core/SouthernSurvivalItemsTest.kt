package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Contract tests for the Southern survival set (BỘ VẬT PHẨM SINH TỒN NAM BỘ).
 *
 * Items live in [OfflineItemRegistry]; HP recognition lives in [HealingItems.healAmount];
 * consumption + physiology effects flow through [InventoryEngine] USE commands (source UI).
 */
class SouthernSurvivalItemsTest {
  companion object {
    const val COCONUT_ID = "southern:ben-tre-coconut"
    const val BANH_TET_ID = "southern:banh-tet"
    const val COM_TAM_ID = "southern:saigon-com-tam"
  }

  private fun stackOf(id: String, quantity: Int = 1): ItemStack {
    val def = OfflineItemRegistry.get(id) ?: error("missing registry definition: $id")
    return ItemStack(def.id, def.name, quantity, metadata = def.metadata)
  }

  private fun stateWith(
    itemId: String,
    quantity: Int = 1,
    minutesSinceFood: Long? = null,
    minutesSinceWater: Long? = null,
    currentHp: Int? = null
  ): GameState {
    var state = GameState.initial()
    val character = state.characters.getValue(CAO_MINH_ID)
    var physiology = character.physiology
    if (minutesSinceFood != null) physiology = physiology.copy(minutesSinceFood = minutesSinceFood)
    if (minutesSinceWater != null) physiology = physiology.copy(minutesSinceWater = minutesSinceWater)
    state = state.copy(characters = state.characters + (CAO_MINH_ID to character.copy(physiology = physiology)))
    if (currentHp != null) state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, currentHp)
    val inventory = state.inventories.getValue(CAO_MINH_ID)
    val stack = stackOf(itemId, quantity)
    return state.copy(
      inventories = state.inventories + (CAO_MINH_ID to inventory.copy(items = inventory.items + (itemId to stack)))
    )
  }

  private fun use(itemId: String, state: GameState, quantity: Int = 1): ExecutionResult {
    val def = OfflineItemRegistry.get(itemId) ?: error("missing registry definition: $itemId")
    return InventoryEngine.execute(
      state,
      ItemCommand(
        commandId = "test-use-$itemId",
        turnId = "TURN_1",
        actorId = CAO_MINH_ID,
        source = CommandSource.UI,
        operation = ItemCommand.Operation.USE,
        itemId = itemId,
        itemName = def.name,
        quantity = quantity
      )
    )
  }

  private fun maxHp(state: GameState): Int = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
  private fun hpOf(state: GameState): Int = state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
  private fun physiologyOf(state: GameState): PhysiologyState = state.characters.getValue(CAO_MINH_ID).physiology

  @Test fun registryContainsAllThreeItemsWithCorrectMetadata() {
    val coconut = OfflineItemRegistry.get(COCONUT_ID) ?: error("missing $COCONUT_ID")
    assertEquals("Dừa Xiêm Bến Tre", coconut.name)
    assertEquals("true", coconut.metadata["consumable"])
    assertEquals("WATER", coconut.metadata["physiologyEffect"])
    assertEquals("common", coconut.metadata["rarity"])
    assertNull("coconut must not heal", coconut.metadata["healHp"])

    val banhTet = OfflineItemRegistry.get(BANH_TET_ID) ?: error("missing $BANH_TET_ID")
    assertEquals("Bánh Tét Nhân Thịt", banhTet.name)
    assertEquals("true", banhTet.metadata["consumable"])
    assertEquals("FOOD", banhTet.metadata["physiologyEffect"])
    assertEquals("5", banhTet.metadata["healHp"])
    assertEquals("common", banhTet.metadata["rarity"])

    val comTam = OfflineItemRegistry.get(COM_TAM_ID) ?: error("missing $COM_TAM_ID")
    assertEquals("Cơm Tấm Sườn Bì Chả Sài Gòn", comTam.name)
    assertEquals("true", comTam.metadata["consumable"])
    assertEquals("FOOD", comTam.metadata["physiologyEffect"])
    assertEquals("10", comTam.metadata["healHp"])
    assertEquals("uncommon", comTam.metadata["rarity"])
  }

  @Test fun healAmountRecognizesSouthernItems() {
    assertEquals(0, HealingItems.healAmount(stackOf(COCONUT_ID)))
    assertEquals(5, HealingItems.healAmount(stackOf(BANH_TET_ID)))
    assertEquals(10, HealingItems.healAmount(stackOf(COM_TAM_ID)))
  }

  @Test fun healingNormalizeDoesNotRenameSouthernItems() {
    // normalize() only renames medical items; southern items keep their id/name
    // (a null result is also acceptable: it means "no rename", falling through to ItemContentRules).
    for (id in listOf(COCONUT_ID, BANH_TET_ID, COM_TAM_ID)) {
      val def = OfflineItemRegistry.get(id)!!
      val normalized = HealingItems.normalize(stackOf(id))
      if (normalized != null) {
        assertEquals("normalize must not rename $id", def.id, normalized.itemId)
        assertEquals("normalize must not rename $id", def.name, normalized.name)
      }
    }
    // End-to-end: ItemContentRules (used by useItem) preserves the registry id.
    for (id in listOf(COCONUT_ID, BANH_TET_ID, COM_TAM_ID)) {
      assertEquals(id, ItemContentRules.normalize(stackOf(id)).itemId)
    }
  }

  @Test fun coconutResetsWaterTimerWithoutHealing() {
    val before = stateWith(COCONUT_ID, minutesSinceWater = 120L, minutesSinceFood = 300L, currentHp = 40)
    val hpBefore = hpOf(before)

    val result = use(COCONUT_ID, before)

    assertTrue(result.toString(), result.applied)
    assertEquals(0L, physiologyOf(result.state).minutesSinceWater)
    assertEquals("food timer must be untouched", 300L, physiologyOf(result.state).minutesSinceFood)
    assertEquals("coconut must not heal", hpBefore, hpOf(result.state))
    assertFalse(
      "consumed coconut must leave the inventory",
      result.state.inventories.getValue(CAO_MINH_ID).items.containsKey(COCONUT_ID)
    )
  }

  @Test fun banhTetResetsFoodTimerAndHealsFive() {
    val before = stateWith(BANH_TET_ID, minutesSinceFood = 240L, minutesSinceWater = 120L, currentHp = 40)

    val result = use(BANH_TET_ID, before)

    assertTrue(result.toString(), result.applied)
    assertEquals(0L, physiologyOf(result.state).minutesSinceFood)
    assertEquals("water timer must be untouched", 120L, physiologyOf(result.state).minutesSinceWater)
    assertEquals(45, hpOf(result.state))
  }

  @Test fun banhTetHealCapsAtMaxHp() {
    val cap = maxHp(stateWith(BANH_TET_ID, currentHp = 40))
    val before = stateWith(BANH_TET_ID, currentHp = cap - 3)
    assertTrue("test needs HP below maxHp", hpOf(before) < cap)

    val result = use(BANH_TET_ID, before)

    assertTrue(result.toString(), result.applied)
    assertEquals("HP must never exceed maxHp", cap, hpOf(result.state))
  }

  @Test fun comTamResetsFoodTimerAndHealsTenCappedAtMaxHp() {
    val before = stateWith(COM_TAM_ID, minutesSinceFood = 240L, currentHp = 30)
    val cap = maxHp(before)

    val fullHeal = use(COM_TAM_ID, before)
    assertTrue(fullHeal.toString(), fullHeal.applied)
    assertEquals(0L, physiologyOf(fullHeal.state).minutesSinceFood)
    assertEquals(40, hpOf(fullHeal.state))

    val nearFull = stateWith(COM_TAM_ID, currentHp = cap - 4)
    val capped = use(COM_TAM_ID, nearFull)
    assertTrue(capped.toString(), capped.applied)
    assertEquals("HP must never exceed maxHp", cap, hpOf(capped.state))
  }

  @Test fun invalidPhysiologyEffectIsRejected() {
    val bad = ItemStack(
      "test:bad-elixir", "Thuốc lạ", 1,
      mapOf("consumable" to "true", "physiologyEffect" to "POISON")
    )
    var state = GameState.initial()
    val inventory = state.inventories.getValue(CAO_MINH_ID)
    state = state.copy(
      inventories = state.inventories + (CAO_MINH_ID to inventory.copy(items = inventory.items + (bad.itemId to bad)))
    )

    val result = InventoryEngine.execute(
      state,
      ItemCommand(
        commandId = "test-use-bad",
        turnId = "TURN_1",
        actorId = CAO_MINH_ID,
        source = CommandSource.UI,
        operation = ItemCommand.Operation.USE,
        itemId = bad.itemId,
        itemName = bad.name,
        quantity = 1
      )
    )

    assertFalse(result.toString(), result.applied)
    assertEquals("physiology_effect_invalid", result.validation.reason)
    assertEquals("rejected use must not consume the item", state, result.state)
  }
}
