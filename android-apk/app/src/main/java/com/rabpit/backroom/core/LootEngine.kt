package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class LootOrigin { EXPLORE_LOOT, ENTITY_DROP }

data class LootGrant(
  val origin: LootOrigin,
  val sourceId: String,
  val definitionId: String,
  val quantity: Int = 1,
  val recipientId: String = KAI_ID
)

data class LootEntry(
  val itemId: String?,
  val weight: Int,
  val minQuantity: Int = 1,
  val maxQuantity: Int = minQuantity
)

class LootTables private constructor(
  private val tables: Map<String, List<LootEntry>>
) {
  fun entries(key: String): List<LootEntry> = tables[key].orEmpty()
  fun resolve(primary: String, fallback: String): List<LootEntry> = entries(primary).ifEmpty { entries(fallback) }

  companion object {
    fun fromJson(raw: String): LootTables {
      val root = JSONObject(raw)
      require(root.optInt("schemaVersion", 0) == 1) { "unsupported_loot_table_schema" }
      val objectTables = root.optJSONObject("tables") ?: JSONObject()
      val result = linkedMapOf<String, List<LootEntry>>()
      objectTables.keys().forEach { key ->
        val array = objectTables.optJSONArray(key) ?: JSONArray()
        val entries = mutableListOf<LootEntry>()
        for (index in 0 until array.length()) {
          val json = array.getJSONObject(index)
          val weight = json.optInt("weight", 0)
          require(weight > 0) { "loot_weight_invalid:$key:$index" }
          val min = json.optInt("minQuantity", 1).coerceAtLeast(1)
          val max = json.optInt("maxQuantity", min)
          require(max >= min) { "loot_quantity_range_invalid:$key:$index" }
          entries += LootEntry(
            itemId = json.optString("itemId").trim().takeIf(String::isNotEmpty),
            weight = weight,
            minQuantity = min,
            maxQuantity = max
          )
        }
        result[key] = entries
      }
      return LootTables(result)
    }
  }
}

object LootEngine {
  fun exploreGrant(
    candidate: JSONObject,
    turnId: String,
    catalog: ItemCatalog,
    tables: LootTables,
    recipientId: String = KAI_ID
  ): LootGrant? {
    val lootRoll = candidate.optJSONObject("flags")
      ?.optJSONObject("lastRolls")
      ?.optJSONObject("loot") ?: return null
    if (!lootRoll.optBoolean("success", false)) return null
    val level = candidate.optJSONObject("level")?.optInt("number", 0)?.coerceAtLeast(0) ?: 0
    return chooseGrant(
      origin = LootOrigin.EXPLORE_LOOT,
      sourceId = "explore:$turnId",
      recipientId = recipientId,
      entries = tables.resolve("explore:$level", "explore:default"),
      catalog = catalog,
      seed = "$turnId|EXPLORE|$level"
    )
  }

  fun entityGrants(
    defeatedEntityIds: List<String>,
    encounterId: String,
    catalog: ItemCatalog,
    tables: LootTables,
    recipientId: String = KAI_ID
  ): List<LootGrant> = defeatedEntityIds.distinct().mapNotNull { entityId ->
    chooseGrant(
      origin = LootOrigin.ENTITY_DROP,
      sourceId = "entity:$encounterId:$entityId",
      recipientId = recipientId,
      entries = tables.resolve("entity:$entityId", "entity:default"),
      catalog = catalog,
      seed = "$encounterId|ENTITY|$entityId"
    )
  }

  fun commandFor(grant: LootGrant, turnId: String?, catalog: ItemCatalog): LootGrantCommand {
    val definition = catalog.definition(grant.definitionId) ?: error("loot_unknown_item:${grant.definitionId}")
    val item = definition.instantiate(
      quantity = grant.quantity,
      origin = grant.origin,
      sourceId = grant.sourceId,
      turnId = turnId ?: grant.sourceId,
      instanceNonce = grant.sourceId
    )
    return LootGrantCommand(
      commandId = "LOOT:${grant.origin}:${grant.sourceId}:${item.itemId}",
      turnId = turnId,
      actorId = grant.recipientId,
      targetId = grant.recipientId,
      source = CommandSource.SYSTEM,
      origin = grant.origin,
      sourceId = grant.sourceId,
      item = item,
      quantity = grant.quantity
    )
  }

  fun wasGrantCommitted(state: GameState, sourceId: String): Boolean {
    val marker = state.metadata["loot.processed.$sourceId"] ?: return false
    return marker.isNotBlank() && !marker.startsWith("lost:")
  }

  private fun chooseGrant(
    origin: LootOrigin,
    sourceId: String,
    recipientId: String,
    entries: List<LootEntry>,
    catalog: ItemCatalog,
    seed: String
  ): LootGrant? {
    if (entries.isEmpty()) return null
    val total = entries.sumOf { it.weight }.coerceAtLeast(1)
    var cursor = (positiveHash(seed) % total.toLong()).toInt()
    val chosen = entries.firstOrNull { entry ->
      if (cursor < entry.weight) true else {
        cursor -= entry.weight
        false
      }
    } ?: entries.last()
    val itemId = chosen.itemId ?: return null
    val definition = catalog.definition(itemId) ?: error("loot_unknown_item:$itemId")
    val quantityRange = chosen.maxQuantity - chosen.minQuantity + 1
    val quantity = chosen.minQuantity + (positiveHash("$seed|quantity") % quantityRange.toLong()).toInt()
    require(quantity <= definition.maxStack) { "loot_quantity_exceeds_definition:$itemId:$quantity" }
    return LootGrant(origin, sourceId, itemId, quantity, recipientId)
  }

  private fun positiveHash(seed: String): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
    var value = 0L
    for (index in 0 until 8) value = (value shl 8) or (digest[index].toLong() and 0xffL)
    return value and Long.MAX_VALUE
  }
}
