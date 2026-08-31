package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class GmItemGain(
  val itemId: String,
  val itemName: String,
  val quantity: Int,
  val metadata: Map<String, String>
)

object GmItemGainPolicy {
  private data class Desired(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val metadata: Map<String, String>
  )

  private fun stableItemId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }

  private fun metadata(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    json.keys().forEach { key -> result[key] = json.optString(key) }
    return result
  }

  fun positiveDeltas(current: Map<String, ItemStack>, candidateInventory: JSONArray?): List<GmItemGain> {
    if (candidateInventory == null) return emptyList()
    val desired = linkedMapOf<String, Desired>()
    for (index in 0 until candidateInventory.length()) {
      val json = candidateInventory.optJSONObject(index) ?: continue
      val name = json.optString("name").trim()
      if (name.isBlank()) continue
      val byName = current.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
      val explicitId = json.optString("id").trim()
      val byIdentity = current.values.firstOrNull { ItemCatalog.sameIdentity(it.itemId, it.name, explicitId, name) }
      val id = byIdentity?.itemId ?: byName?.itemId ?: ItemCatalog.identityId(explicitId.takeIf(String::isNotBlank), name)
      val old = current[id] ?: byIdentity ?: byName
      val quantity = json.optInt("quantity", 1).coerceIn(1, 999)
      val mergedMetadata = old?.metadata.orEmpty() + metadata(json.optJSONObject("metadata"))
      desired[id] = Desired(id, name, quantity, mergedMetadata)
    }

    return desired.values.mapNotNull { item ->
      val currentStack = current[item.itemId] ?: current.values.firstOrNull { it.name.equals(item.itemName, ignoreCase = true) }
      val oldQuantity = currentStack?.quantity ?: 0
      val delta = item.quantity - oldQuantity
      if (delta <= 0) null else GmItemGain(item.itemId, item.itemName, delta, item.metadata)
    }
  }
}
