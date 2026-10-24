package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The only runtime loot generation authority. GM output is never an input.
 * A drop creates a tangible world instance, not an owned Inventory item.
 */
object CoreLootEngine {
  const val ENTITY_DROP_PERCENT = 80
  const val EXPLORATION_DROP_PERCENT = 8

  enum class Source { ENTITY, EXPLORE }
  data class Outcome(val state: GameState, val rolled: Boolean, val dropped: ItemStack? = null)

  private val pool = listOf(
    ItemStack("medical:bandage", "Băng gạc", metadata = mapOf("consumable" to "true", "consumedOnUse" to "true")),
    ItemStack("medical:antiseptic", "Thuốc sát trùng", metadata = mapOf("consumable" to "true", "consumedOnUse" to "true")),
    ItemStack("almond-water", "Almond Water", metadata = mapOf("consumable" to "true", "physiologyEffect" to "WATER")),
    ItemStack("food-ration", "Hộp thức ăn", metadata = mapOf("consumable" to "true", "physiologyEffect" to "FOOD"))
  )

  fun isExplorationAction(action: String): Boolean {
    val words = action.trim().lowercase()
    if (words.isEmpty()) return false
    if (listOf("không ", "đừng ", "chưa ", "giả sử ", "nếu ", "xem inventory",
        "kiểm tra inventory", "xem túi", "kiểm tra túi", "xem trạng thái").any { words.startsWith(it) }) return false
    return Regex("(?:^|\\s)(?:tìm kiếm|tìm|lục soát|lục|khám phá|khảo sát|thăm dò|rà soát|quét|scan|quan sát kỹ|mở ngăn|mở hộp)(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(words)
  }

  /** A unique event key (turn or encounter) is rolled once, success or failure. */
  fun roll(
    state: GameState,
    source: Source,
    eventKey: String,
    random: (Int) -> Int = { Random.Default.nextInt(it) }
  ): Outcome {
    require(eventKey.isNotBlank() && eventKey.length <= 160 && eventKey.all {
      it.isLetterOrDigit() || it in setOf('-', '_', ':', '.')
    }) { "invalid_loot_event_key" }
    val marker = "loot.rolled.${source.name}:$eventKey"
    if (marker in state.metadata) return Outcome(state, false)
    val chance = if (source == Source.ENTITY) ENTITY_DROP_PERCENT else EXPLORATION_DROP_PERCENT
    val dice = random(100)
    require(dice in 0 until 100) { "invalid_loot_roll" }
    val stamped = state.copy(metadata = state.metadata + (marker to "true"))
    if (dice >= chance) return Outcome(stamped, true)

    val chosen = random(pool.size)
    require(chosen in pool.indices) { "invalid_loot_selection" }
    val item = ItemContentRules.normalize(pool[chosen])
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: "{}") }.getOrNull()
      ?: return Outcome(state, false) // Never overwrite malformed world state.
    val items = flags.optJSONArray("worldItems") ?: JSONArray()
    val instanceId = "core:${source.name.lowercase()}:$eventKey"
    items.put(JSONObject().apply {
      put("id", item.itemId)
      put("name", item.name)
      put("quantity", 1)
      put("instanceId", instanceId)
      put("available", true)
      put("metadata", JSONObject(item.metadata + mapOf("lootOrigin" to source.name, "worldInstanceId" to instanceId)))
    })
    flags.put("worldItems", items)
    return Outcome(stamped.copy(world = stamped.world + ("flagsJson" to flags.toString())), true, item)
  }
}
