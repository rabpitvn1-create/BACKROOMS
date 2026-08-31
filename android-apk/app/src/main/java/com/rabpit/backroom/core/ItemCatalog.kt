package com.rabpit.backroom.core

enum class OfficialItemType { TOOL, CONSUMABLE }

data class OfficialItem(
  val id: String,
  val name: String,
  val type: OfficialItemType,
  val metadata: Map<String, String>
) {
  fun stack(quantity: Int = 1): ItemStack = ItemStack(id, name, quantity, metadata = metadata, archetypeId = id)
}

/** The only item pool used by level acquisition and entity drops. */
object ItemCatalog {
  const val FLASHLIGHT = "flashlight"
  const val LIGHTER = "lighter"
  const val ALMOND_WATER = "almond-water"
  const val CANNED_FOOD = "canned-food"
  const val BATTERY = "battery"
  const val LIGHTER_FUEL = "lighter-fuel"
  const val BANDAGE = "bandage"
  const val ANTISEPTIC = "antiseptic"
  const val PAINKILLER = "painkiller"
  const val SARDINES = "three-lady-cooks-sardines"
  const val LA_VIE = "la-vie-spring-water"
  const val CHICKEN_RICE_BOX = "chicken-rice-box"

  private fun tool(vararg values: Pair<String, String>) = mapOf("itemType" to "TOOL") + values
  private fun consumable(vararg values: Pair<String, String>) =
    mapOf("itemType" to "CONSUMABLE", "consumable" to "true", "consumedOnUse" to "true") + values

  val items: List<OfficialItem> = listOf(
    OfficialItem(FLASHLIGHT, "Flashlight", OfficialItemType.TOOL, tool(
      "durability" to "100", "battery" to "100", "batteryMax" to "100", "state" to "OFF",
      "lightRange" to "12", "beamAngle" to "70"
    )),
    OfficialItem(LIGHTER, "Lighter", OfficialItemType.TOOL, tool(
      "durability" to "50", "fuel" to "100", "fuelMax" to "100", "state" to "OFF", "lightRange" to "2"
    )),
    OfficialItem(ALMOND_WATER, "Almond Water", OfficialItemType.CONSUMABLE, consumable(
      "physiologyEffect" to "WATER", "hydration" to "40", "healHp" to "5", "stressReduction" to "light"
    )),
    OfficialItem(CANNED_FOOD, "Canned Food", OfficialItemType.CONSUMABLE, consumable(
      "physiologyEffect" to "FOOD", "hunger" to "45"
    )),
    OfficialItem(BATTERY, "Battery", OfficialItemType.CONSUMABLE, consumable("toolRecharge" to "FLASHLIGHT", "recharge" to "50")),
    OfficialItem(LIGHTER_FUEL, "Lighter Fuel", OfficialItemType.CONSUMABLE, consumable("toolRecharge" to "LIGHTER", "recharge" to "50")),
    OfficialItem(BANDAGE, "Bandage", OfficialItemType.CONSUMABLE, consumable("healHp" to "15", "statusTreatment" to "BLEEDING_LIGHT")),
    OfficialItem(ANTISEPTIC, "Antiseptic", OfficialItemType.CONSUMABLE, consumable("healHp" to "10", "conditionReduction" to "INFECTION_50")),
    OfficialItem(PAINKILLER, "Painkiller", OfficialItemType.CONSUMABLE, consumable("healHp" to "10", "conditionReduction" to "PAIN_50")),
    OfficialItem(SARDINES, "Cá Mòi Ba Cô Gái", OfficialItemType.CONSUMABLE, consumable(
      "englishAlias" to "Three Lady Cooks Sardines", "physiologyEffect" to "FOOD", "hunger" to "55", "normal" to "true"
    )),
    OfficialItem(LA_VIE, "Nước suối La Vie", OfficialItemType.CONSUMABLE, consumable(
      "englishAlias" to "La Vie Spring Water", "physiologyEffect" to "WATER", "hydration" to "50", "normal" to "true"
    )),
    OfficialItem(CHICKEN_RICE_BOX, "Hộp cơm gà", OfficialItemType.CONSUMABLE, consumable(
      "physiologyEffect" to "FOOD", "hunger" to "100"
    ))
  )

  val ids: Set<String> = items.mapTo(linkedSetOf()) { it.id }
  fun find(id: String): OfficialItem? = items.firstOrNull { it.id == id.lowercase().trim() }
  fun stack(id: String): ItemStack? = find(id)?.stack()

  fun canonicalId(raw: String): String {
    val value = raw.lowercase().substringBefore(':').trim()
    return when {
      value in setOf("water-bottle", "almond-water", "almond_water") -> ALMOND_WATER
      value in setOf("food-container", "canned-food", "canned_food") -> CANNED_FOOD
      value in setOf("fuel-container", "lighter-fuel", "lighter_fuel") -> LIGHTER_FUEL
      else -> value
    }
  }
}

fun interface LootRng { fun nextInt(bound: Int): Int }

object EntityLootEngine {
  const val DROP_CHANCE_PERCENT = 1

  fun onDefeat(state: GameState, defeatId: String, rng: LootRng): GameState {
    val marker = "entityLootRolled:$defeatId"
    if (defeatId.isBlank() || state.world[marker] != null) return state
    var next = state.copy(world = state.world + (marker to "NONE"))
    if (rng.nextInt(100) != 0) return next
    val item = ItemCatalog.items[rng.nextInt(ItemCatalog.items.size)].stack()
    val lootId = "entityLoot:$defeatId"
    next = next.copy(world = next.world + mapOf(
      marker to item.itemId,
      lootId to "${item.itemId}|${item.name}|1|ENTITY_DROP"
    ))
    return next
  }
}

object LevelLootEngine {
  /** Search completion is eligible, never guaranteed. The stable roll prevents retry farming. */
  fun onSearchCompleted(state: GameState, sessionId: String, location: String?): GameState {
    val marker = "levelLootRolled:$sessionId"
    if (state.world[marker] != null) return state
    val seed = "$sessionId|${location.orEmpty()}".hashCode().toLong() and Long.MAX_VALUE
    var next = state.copy(world = state.world + (marker to "NONE"))
    if (seed % 100L >= 20L) return next
    val item = ItemCatalog.items[((seed / 100L) % ItemCatalog.items.size).toInt()].stack()
    return next.copy(world = next.world + mapOf(
      marker to item.itemId,
      "levelLoot:$sessionId" to "${item.itemId}|${item.name}|1|SEARCH"
    ))
  }
}

object WorldLootAcquisition {
  fun acquire(state: GameState, lootKey: String, actorId: String = KAI_ID): ExecutionResult {
    val raw = state.world[lootKey] ?: return invalid(state, "world_loot_missing")
    val parts = raw.split('|')
    if (parts.size != 4 || parts[2] != "1" || parts[3] !in setOf("SEARCH", "ENTITY_DROP")) return invalid(state, "world_loot_invalid")
    val item = ItemCatalog.find(parts[0]) ?: return invalid(state, "world_loot_item_unknown")
    val acquired = InventoryEngine.execute(state, ItemCommand(
      commandId = "ACQUIRE:$lootKey", turnId = state.turn.currentTurnId, actorId = actorId,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = item.id, itemName = item.name, quantity = 1,
      metadata = item.metadata + ("acquisitionSource" to parts[3])
    ))
    if (!acquired.applied) return acquired
    return acquired.copy(state = acquired.state.copy(world = acquired.state.world - lootKey), events = acquired.events + "world_loot_acquired")
  }
}

object ToolStateQueries {
  private fun tool(state: GameState, ownerId: String, id: String) = state.inventories[ownerId]?.items?.get(id)?.let(ItemContentRules::normalize)
  fun hasFlashlight(state: GameState, ownerId: String = KAI_ID) = tool(state, ownerId, ItemCatalog.FLASHLIGHT) != null
  fun flashlightOn(state: GameState, ownerId: String = KAI_ID) = tool(state, ownerId, ItemCatalog.FLASHLIGHT)?.let {
    it.metadata["state"].equals("ON", true) && flashlightBattery(state, ownerId) > 0
  } ?: false
  fun flashlightBattery(state: GameState, ownerId: String = KAI_ID) = tool(state, ownerId, ItemCatalog.FLASHLIGHT)?.metadata?.get("battery")?.toIntOrNull()?.coerceIn(0, 100) ?: 0
  fun flashlightRange(state: GameState, ownerId: String = KAI_ID) = if (flashlightOn(state, ownerId)) 12 else 0
}
