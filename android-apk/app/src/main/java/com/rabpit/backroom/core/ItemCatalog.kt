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

  // ITEM_IDENTITY_AUTHORITY_V2: official aliases resolve here and nowhere else.
  private fun identityKey(raw: String): String {
    val folded = java.text.Normalizer.normalize(raw.trim().lowercase(), java.text.Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "")
      .replace('đ', 'd')
    return folded.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .replace(Regex("\\s+"), " ").trim()
  }

  private val explicitIdentityAliases: Map<String, String> = linkedMapOf(
    "đèn pin" to FLASHLIGHT, "den pin" to FLASHLIGHT,
    "bật lửa" to LIGHTER, "bat lua" to LIGHTER,
    "nước hạnh nhân" to ALMOND_WATER, "nuoc hanh nhan" to ALMOND_WATER,
    "water-bottle" to ALMOND_WATER, "almond_water" to ALMOND_WATER,
    "thực phẩm đóng hộp" to CANNED_FOOD, "thuc pham dong hop" to CANNED_FOOD,
    "đồ hộp" to CANNED_FOOD, "do hop" to CANNED_FOOD,
    "food-container" to CANNED_FOOD, "canned_food" to CANNED_FOOD,
    "pin" to BATTERY,
    "nhiên liệu bật lửa" to LIGHTER_FUEL, "nhien lieu bat lua" to LIGHTER_FUEL,
    "fuel-container" to LIGHTER_FUEL, "lighter_fuel" to LIGHTER_FUEL,
    "băng gạc" to BANDAGE, "bang gac" to BANDAGE,
    "cuộn băng" to BANDAGE, "cuon bang" to BANDAGE,
    "băng y tế" to BANDAGE, "bang y te" to BANDAGE,
    "medical:bandage" to BANDAGE,
    "thuốc sát trùng" to ANTISEPTIC, "thuoc sat trung" to ANTISEPTIC,
    "dung dịch sát trùng" to ANTISEPTIC, "dung dich sat trung" to ANTISEPTIC,
    "medical:antiseptic" to ANTISEPTIC,
    "thuốc giảm đau" to PAINKILLER, "thuoc giam dau" to PAINKILLER,
    "cá mòi ba cô gái" to SARDINES, "ca moi ba co gai" to SARDINES,
    "three lady cooks sardines" to SARDINES,
    "nước suối la vie" to LA_VIE, "nuoc suoi la vie" to LA_VIE,
    "la vie spring water" to LA_VIE
  )

  private val identityAliases: List<Pair<String, String>> by lazy {
    val values = linkedMapOf<String, String>()
    fun register(alias: String?, id: String) {
      val key = identityKey(alias.orEmpty())
      if (key.isNotBlank()) values.putIfAbsent(key, id)
    }
    items.forEach { item ->
      register(item.id, item.id)
      register(item.name, item.id)
      register(item.metadata["englishAlias"], item.id)
    }
    explicitIdentityAliases.forEach { (alias, id) -> register(alias, id) }
    values.entries.map { it.key to it.value }.sortedByDescending { it.first.length }
  }

  fun resolveOfficial(rawId: String? = null, rawName: String? = null): OfficialItem? {
    listOf(rawId, rawName).forEach { raw ->
      val key = identityKey(raw.orEmpty())
      if (key.isNotBlank()) {
        identityAliases.firstOrNull { it.first == key }?.second?.let { id ->
          find(id)?.let { return it }
        }
      }
    }
    return null
  }

  private data class IdentityMention(val start: Int, val end: Int, val aliasLength: Int, val itemId: String)

  fun officialMentions(text: String): List<OfficialItem> {
    val normalized = identityKey(text)
    if (normalized.isBlank()) return emptyList()
    val matches = mutableListOf<IdentityMention>()
    identityAliases.forEach { (alias, id) ->
      val regex = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])")
      regex.findAll(normalized).forEach { match ->
        matches += IdentityMention(match.range.first, match.range.last, alias.length, id)
      }
    }
    val selected = mutableListOf<IdentityMention>()
    matches.sortedByDescending { it.aliasLength }.forEach { candidate ->
      if (selected.none { existing -> candidate.start <= existing.end && existing.start <= candidate.end }) {
        selected += candidate
      }
    }
    return selected.sortedBy { it.start }.mapNotNull { find(it.itemId) }.distinctBy { it.id }
  }

  fun officialMention(text: String): OfficialItem? = officialMentions(text).firstOrNull()

  fun withoutOfficialMentions(text: String): String {
    var normalized = identityKey(text)
    identityAliases.forEach { (alias, _) ->
      if (alias.isBlank()) return@forEach
      normalized = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])")
        .replace(normalized, " ")
    }
    return normalized.replace(Regex("\\s+"), " ").trim()
  }

  fun aliasTextsFor(rawId: String?, rawName: String?): Set<String> {
    val official = resolveOfficial(rawId, rawName)
      ?: return listOfNotNull(rawId, rawName).filter(String::isNotBlank).toSet()
    val aliases = linkedSetOf(official.id, official.name)
    official.metadata["englishAlias"]?.takeIf(String::isNotBlank)?.let(aliases::add)
    explicitIdentityAliases.filterValues { it == official.id }.keys.forEach(aliases::add)
    return aliases
  }

  fun stableCustomId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
    .ifBlank { "item-${name.hashCode().toUInt()}" }

  fun identityId(rawId: String? = null, name: String? = null): String {
    resolveOfficial(rawId, name)?.let { return it.id }
    return rawId?.trim()?.takeIf(String::isNotBlank) ?: stableCustomId(name.orEmpty())
  }

  fun sameIdentity(leftId: String?, leftName: String?, rightId: String?, rightName: String?): Boolean =
    identityId(leftId, leftName) == identityId(rightId, rightName)

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

data class EnvironmentLootPreview(
  val eligible: Boolean,
  val baseThreshold: Int,
  val pityTurn: Int,
  val pityThreshold: Int,
  val followerThreshold: Int,
  val threshold: Int,
  val roll: Int?,
  val success: Boolean
) {
  val chancePercent: Double get() = threshold / 100.0
}

object EntityLootEngine {
  const val DROP_CHANCE_PERCENT = 100
  fun dropChancePercent(state: GameState): Int = DROP_CHANCE_PERCENT
  private fun jane(defeatId: String) = defeatId.contains(":john_doe:")
  private fun slot(state: GameState, defeatId: String, n: Int, rng: LootRng): GameState {
    val suffix = if (n == 1) "" else ":$n"
    val marker = "entityLootRolled:$defeatId$suffix"
    val lootId = "entityLoot:$defeatId$suffix"
    if (state.world[marker] != null) return if (state.world[lootId] == null) state else WorldLootAcquisition.acquire(state, lootId, KAI_ID).state
    val item = ItemCatalog.items[rng.nextInt(ItemCatalog.items.size)].stack()
    val selected = state.copy(world = state.world + mapOf(marker to item.itemId, lootId to "${item.itemId}|${item.name}|1|ENTITY_DROP"))
    return WorldLootAcquisition.acquire(selected, lootId, KAI_ID).state
  }
  fun onDefeat(state: GameState, defeatId: String, rng: LootRng): GameState {
    if (defeatId.isBlank()) return state
    var next = slot(state, defeatId, 1, rng)
    if (jane(defeatId)) next = slot(next, defeatId, 2, rng)
    return next
  }
}

object LevelLootEngine {
  const val ENVIRONMENT_PITY_KEY = "lootPity.environmentFailures"
  const val PITY_STEP_BASIS_POINTS = 100
  const val BASE_EXPLORATION_BONUS_BASIS_POINTS = 500
  const val GUARANTEED_TURN = 100
  private const val PREVIEW_PREFIX = "actionRuntime.loot."
  private val BASE_THRESHOLDS = intArrayOf(35, 120, 100, 150, 180, 100, 45)

  private fun eligible(kind: ActionKind): Boolean = kind == ActionKind.SEARCH || kind == ActionKind.EXPLORE

  private fun parentLevel(state: GameState): Int {
    val levelJson = state.world["levelJson"].orEmpty()
    val structured = Regex("\\\"number\\\"\\s*:\\s*(\\d+)").find(levelJson)
      ?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (structured != null) return structured.coerceIn(0, 6)
    val fallback = Regex("Level\\s+(\\d+)", RegexOption.IGNORE_CASE)
      .find(state.world["title"].orEmpty() + " " + state.world["location"].orEmpty())
      ?.groupValues?.getOrNull(1)?.toIntOrNull()
    return (fallback ?: 0).coerceIn(0, 6)
  }

  fun environmentFailures(state: GameState): Int =
    state.metadata[ENVIRONMENT_PITY_KEY]?.toIntOrNull()?.coerceIn(0, GUARANTEED_TURN - 1) ?: 0

  private fun followerBonusThreshold(state: GameState): Int {
    var bonus = 0
    if (AN_NHIEN_ID in state.party.memberIds) bonus += 1000
    if (LUCIA_ID in state.party.memberIds) bonus += 500
    return bonus
  }

  private fun stablePositiveHash(value: String): Long {
    var hash = 1469598103934665603L
    value.forEach { ch -> hash = (hash xor ch.code.toLong()) * 1099511628211L }
    return hash and Long.MAX_VALUE
  }

  private fun calculate(state: GameState, sessionId: String, kind: ActionKind, location: String?): EnvironmentLootPreview {
    if (!eligible(kind)) return EnvironmentLootPreview(false, 0, 0, 0, 0, 0, null, false)
    val failures = environmentFailures(state)
    val pityTurn = (failures + 1).coerceIn(1, GUARANTEED_TURN)
    val base = BASE_THRESHOLDS[parentLevel(state)]
    val pity = pityTurn * PITY_STEP_BASIS_POINTS
    val follower = followerBonusThreshold(state)
    val threshold = (base + BASE_EXPLORATION_BONUS_BASIS_POINTS + pity + follower).coerceAtMost(10000)
    if (threshold >= 10000) {
      return EnvironmentLootPreview(true, base, pityTurn, pity, follower, 10000, null, true)
    }
    val seed = stablePositiveHash("$sessionId|${kind.name}|${location.orEmpty()}|$pityTurn")
    val roll = (seed % 10000L).toInt() + 1
    return EnvironmentLootPreview(true, base, pityTurn, pity, follower, threshold, roll, roll <= threshold)
  }

  fun prepareAction(state: GameState, sessionId: String, kind: ActionKind, location: String?): GameState {
    if (!eligible(kind)) return state
    val preview = calculate(state, sessionId, kind, location)
    val metadata = state.metadata + mapOf(
      "${PREVIEW_PREFIX}sessionId" to sessionId,
      "${PREVIEW_PREFIX}eligible" to preview.eligible.toString(),
      "${PREVIEW_PREFIX}baseThreshold" to preview.baseThreshold.toString(),
      "${PREVIEW_PREFIX}pityTurn" to preview.pityTurn.toString(),
      "${PREVIEW_PREFIX}pityThreshold" to preview.pityThreshold.toString(),
      "${PREVIEW_PREFIX}followerThreshold" to preview.followerThreshold.toString(),
      "${PREVIEW_PREFIX}threshold" to preview.threshold.toString(),
      "${PREVIEW_PREFIX}roll" to (preview.roll?.toString() ?: ""),
      "${PREVIEW_PREFIX}success" to preview.success.toString()
    )
    return state.copy(metadata = metadata)
  }

  fun preparedPreview(state: GameState): EnvironmentLootPreview? {
    if (state.metadata["${PREVIEW_PREFIX}eligible"] != "true") return null
    val threshold = state.metadata["${PREVIEW_PREFIX}threshold"]?.toIntOrNull() ?: return null
    return EnvironmentLootPreview(
      eligible = true,
      baseThreshold = state.metadata["${PREVIEW_PREFIX}baseThreshold"]?.toIntOrNull() ?: 0,
      pityTurn = state.metadata["${PREVIEW_PREFIX}pityTurn"]?.toIntOrNull() ?: 1,
      pityThreshold = state.metadata["${PREVIEW_PREFIX}pityThreshold"]?.toIntOrNull() ?: 100,
      followerThreshold = state.metadata["${PREVIEW_PREFIX}followerThreshold"]?.toIntOrNull() ?: 0,
      threshold = threshold.coerceIn(0, 10000),
      roll = state.metadata["${PREVIEW_PREFIX}roll"]?.toIntOrNull(),
      success = state.metadata["${PREVIEW_PREFIX}success"].toBoolean()
    )
  }

  private fun withEnvironmentFailures(state: GameState, failures: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (failures > 0) metadata[ENVIRONMENT_PITY_KEY] = failures.coerceAtMost(GUARANTEED_TURN - 1).toString()
    else metadata.remove(ENVIRONMENT_PITY_KEY)
    return state.copy(metadata = metadata)
  }

  fun commitPrepared(state: GameState, sessionId: String, kind: ActionKind, location: String?): GameState {
    if (!eligible(kind)) return state
    val marker = "levelLootRolled:$sessionId"
    if (state.world[marker] != null) return state
    if (state.metadata["${PREVIEW_PREFIX}sessionId"] != sessionId) return state
    val preview = preparedPreview(state) ?: return state

    var next = state.copy(world = state.world + (marker to "NONE"))
    if (!preview.success) return withEnvironmentFailures(next, environmentFailures(state) + 1)

    next = withEnvironmentFailures(next, 0)
    val itemSeed = stablePositiveHash("$sessionId|${kind.name}|${location.orEmpty()}|item")
    val item = ItemCatalog.items[(itemSeed % ItemCatalog.items.size.toLong()).toInt()].stack()
    return next.copy(world = next.world + mapOf(
      marker to item.itemId,
      "levelLoot:$sessionId" to "${item.itemId}|${item.name}|1|SEARCH"
    ))
  }

  /** Compatibility entrypoint retained for older callers/tests. */
  fun onSearchCompleted(state: GameState, sessionId: String, location: String?): GameState {
    val prepared = prepareAction(state, sessionId, ActionKind.SEARCH, location)
    return commitPrepared(prepared, sessionId, ActionKind.SEARCH, location)
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
