package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class OfficialItemSystemTest {
  private fun grant(state: GameState, id: String, quantity: Int = 1): GameState {
    val item = ItemCatalog.find(id)!!
    val result = StateReducer.execute(state, ItemCommand(
      "grant-$id", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = id, itemName = item.name, quantity = quantity,
      metadata = item.metadata + ("acquisitionSource" to "WORLD_EVENT")
    ))
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    return result.state
  }

  private fun use(state: GameState, id: String, suffix: String = "1") = StateReducer.execute(state, ItemCommand(
    "use-$id-$suffix", "TURN_1", KAI_ID, source = CommandSource.RULE,
    operation = ItemCommand.Operation.USE, itemId = id, itemName = ItemCatalog.find(id)!!.name
  ))

  @Test fun catalogContainsExactlyTwoToolsNineConsumablesAndNoAmmo() {
    assertEquals(11, ItemCatalog.items.size)
    assertEquals(11, ItemCatalog.ids.size)
    assertEquals(2, ItemCatalog.items.count { it.type == OfficialItemType.TOOL })
    assertEquals(9, ItemCatalog.items.count { it.type == OfficialItemType.CONSUMABLE })
    assertFalse(ItemCatalog.items.any { it.id.contains("ammo", true) || it.name.contains("ammo", true) })
  }

  @Test fun wholeUnitFoodAndWaterUsesConsumeExactlyOne() {
    listOf(ItemCatalog.ALMOND_WATER, ItemCatalog.LA_VIE, ItemCatalog.CANNED_FOOD, ItemCatalog.SARDINES).forEach { id ->
      val result = use(grant(GameState.initial(), id, 2), id)
      assertTrue(result.applied)
      assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue(id).quantity)
      assertFalse(result.state.inventories.getValue(KAI_ID).items.keys.any { it.contains(":low") })
    }
  }

  @Test fun healingItemsUseCombatHpAuthorityAndClamp() {
    val base = GameState.initial().copy(metadata = mapOf("combat.playerHp" to "50", "combat.playerMaxHp" to "100"))
    listOf(ItemCatalog.ALMOND_WATER to 5, ItemCatalog.BANDAGE to 15, ItemCatalog.ANTISEPTIC to 10, ItemCatalog.PAINKILLER to 10).forEachIndexed { index, pair ->
      val result = use(grant(base, pair.first), pair.first, index.toString())
      assertEquals(50 + pair.second, result.state.metadata["combat.playerHp"]?.toInt())
    }
    val nearMax = base.copy(metadata = base.metadata + ("combat.playerHp" to "98"))
    assertEquals("100", use(grant(nearMax, ItemCatalog.BANDAGE), ItemCatalog.BANDAGE).state.metadata["combat.playerHp"])
  }

  @Test fun medicalItemsTreatOnlyTheirExistingAuthorities() {
    val bleed = StatusEffect("bleed-1", "BLEEDING", "combat", metadata = mapOf("tier" to "light"))
    val kai = GameState.initial().characters.getValue(KAI_ID).copy(statusIds = setOf(bleed.id), physiology = PhysiologyState(painState = "severe", infectionState = "moderate"))
    val base = GameState.initial().copy(characters = mapOf(KAI_ID to kai), statuses = mapOf(bleed.id to bleed), metadata = mapOf("combat.playerHp" to "40", "combat.playerMaxHp" to "100"))
    val bandaged = use(grant(base, ItemCatalog.BANDAGE), ItemCatalog.BANDAGE).state
    assertFalse(bandaged.statuses.containsKey(bleed.id))
    assertEquals("moderate", bandaged.characters.getValue(KAI_ID).physiology.infectionState)
    val antiseptic = use(grant(base, ItemCatalog.ANTISEPTIC), ItemCatalog.ANTISEPTIC).state
    assertEquals("mild", antiseptic.characters.getValue(KAI_ID).physiology.infectionState)
    assertEquals("severe", antiseptic.characters.getValue(KAI_ID).physiology.painState)
    assertTrue(antiseptic.statuses.containsKey(bleed.id))
    val painkiller = use(grant(base, ItemCatalog.PAINKILLER), ItemCatalog.PAINKILLER).state
    assertEquals("moderate", painkiller.characters.getValue(KAI_ID).physiology.painState)
    assertEquals("moderate", painkiller.characters.getValue(KAI_ID).physiology.infectionState)
  }

  @Test fun batteryAndFuelRechargeCorrectToolConsumeOneAndCap() {
    var state = grant(grant(GameState.initial(), ItemCatalog.FLASHLIGHT), ItemCatalog.BATTERY, 2)
    val flashlight = state.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.FLASHLIGHT)
    state = state.copy(inventories = state.inventories + (KAI_ID to state.inventories.getValue(KAI_ID).copy(items = state.inventories.getValue(KAI_ID).items + (ItemCatalog.FLASHLIGHT to flashlight.copy(metadata = flashlight.metadata + ("battery" to "70"))))))
    val charged = use(state, ItemCatalog.BATTERY).state
    assertEquals("100", charged.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.FLASHLIGHT).metadata["battery"])
    assertEquals(1, charged.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.BATTERY).quantity)
    var fuelState = grant(grant(GameState.initial(), ItemCatalog.LIGHTER), ItemCatalog.LIGHTER_FUEL)
    val lighter = fuelState.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.LIGHTER)
    fuelState = fuelState.copy(inventories = fuelState.inventories + (KAI_ID to fuelState.inventories.getValue(KAI_ID).copy(items = fuelState.inventories.getValue(KAI_ID).items + (ItemCatalog.LIGHTER to lighter.copy(metadata = lighter.metadata + ("fuel" to "40"))))))
    val fueled = use(fuelState, ItemCatalog.LIGHTER_FUEL).state
    assertEquals("90", fueled.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.LIGHTER).metadata["fuel"])
    assertFalse(fueled.inventories.getValue(KAI_ID).items.containsKey(ItemCatalog.LIGHTER_FUEL))
  }

  @Test fun authoritativeTimeDrainsOnlyOnToolsAndClampsAtZero() {
    var state = grant(grant(GameState.initial(), ItemCatalog.FLASHLIGHT), ItemCatalog.LIGHTER)
    state = use(state, ItemCatalog.FLASHLIGHT).state
    val advanced = TimeEngine.execute(state, TimeAdvanceCommand("time-1", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 3, reason = "test")).state
    assertEquals("97", advanced.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.FLASHLIGHT).metadata["battery"])
    assertEquals("100", advanced.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.LIGHTER).metadata["fuel"])
    var both = use(advanced, ItemCatalog.LIGHTER).state
    both = TimeEngine.execute(both, TimeAdvanceCommand("time-2", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 200, reason = "test")).state
    assertEquals("0", both.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.FLASHLIGHT).metadata["battery"])
    assertEquals("0", both.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.LIGHTER).metadata["fuel"])
  }

  @Test fun saveRoundTripPreservesToolState() {
    var state = use(grant(GameState.initial(), ItemCatalog.FLASHLIGHT), ItemCatalog.FLASHLIGHT).state
    state = TimeEngine.execute(state, TimeAdvanceCommand("time", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 28, reason = "test")).state
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals("72", loaded.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.FLASHLIGHT).metadata["battery"])
    assertTrue(ToolStateQueries.flashlightOn(loaded))
    assertEquals(12, ToolStateQueries.flashlightRange(loaded))
  }

  @Test fun entityLootIsOnePercentTotalOneItemAndIdempotent() {
    val miss = EntityLootEngine.onDefeat(GameState.initial(), "defeat-miss", LootRng { 99 })
    assertFalse(miss.world.keys.any { it.startsWith("entityLoot:") })
    var calls = 0
    val hit = EntityLootEngine.onDefeat(GameState.initial(), "defeat-hit", LootRng { bound -> calls++; if (bound == 100) 0 else 10 })
    assertEquals(2, calls)
    val loot = hit.world.filterKeys { it.startsWith("entityLoot:") }
    assertEquals(1, loot.size)
    assertTrue(loot.values.single().endsWith("|1|ENTITY_DROP"))
    assertTrue(loot.values.single().substringBefore('|') in ItemCatalog.ids)
    val duplicate = EntityLootEngine.onDefeat(hit, "defeat-hit", LootRng { fail("must not reroll"); 0 })
    assertEquals(hit, duplicate)
  }

  @Test fun playerAndUnprovenGeminiCannotManufacturePickup() {
    val item = ItemCatalog.find(ItemCatalog.BANDAGE)!!
    val player = StateReducer.execute(GameState.initial(), ItemCommand("p", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = ItemCommand.Operation.PICKUP, itemId = item.id, itemName = item.name))
    assertEquals("player_pickup_unavailable", player.validation.reason)
    val prose = StateReducer.execute(GameState.initial(), ItemCommand("g", "TURN_1", KAI_ID, source = CommandSource.GEMINI, operation = ItemCommand.Operation.PICKUP, itemId = item.id, itemName = item.name))
    assertEquals("acquisition_event_required", prose.validation.reason)
  }

  @Test fun authoritativeWorldLootCanBeAcquiredOnlyOnce() {
    val state = GameState.initial().copy(world = mapOf("entityLoot:defeat-1" to "bandage|Bandage|1|ENTITY_DROP"))
    val acquired = WorldLootAcquisition.acquire(state, "entityLoot:defeat-1")
    assertTrue(acquired.applied)
    assertEquals(1, acquired.state.inventories.getValue(KAI_ID).items.getValue(ItemCatalog.BANDAGE).quantity)
    assertFalse(acquired.state.world.containsKey("entityLoot:defeat-1"))
    assertEquals("world_loot_missing", WorldLootAcquisition.acquire(acquired.state, "entityLoot:defeat-1").validation.reason)
  }
}
