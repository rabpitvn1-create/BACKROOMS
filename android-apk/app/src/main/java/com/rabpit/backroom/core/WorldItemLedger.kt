package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class WorldItemRecord(
  val itemId: String,
  val itemName: String,
  val quantity: Int,
  val metadata: Map<String, String>
)

data class WorldItemPickup(
  val items: List<WorldItemRecord>,
  val flagsJson: String
)

object WorldItemLedger {
  private fun normalized(value: String?): String = value.orEmpty().trim().lowercase()

  private fun stableId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-')
    .ifBlank { "world-item-${name.hashCode().toUInt()}" }

  private fun flags(raw: String?): JSONObject = runCatching {
    if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
  }.getOrElse { JSONObject() }

  private fun strings(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    val keys = json.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      result[key] = json.optString(key, "")
    }
    return result
  }

  private fun catalogFor(id: String, name: String): OfficialItem? = ItemCatalog.resolveOfficial(id, name)

  private fun canonical(raw: JSONObject, location: String): JSONObject? {
    val requestedName = raw.optString("name", "").trim()
    val requestedId = raw.optString("id", "").trim()
    val catalog = catalogFor(requestedId, requestedName)
    val name = requestedName.ifBlank { catalog?.name ?: requestedId }
    if (name.isBlank()) return null
    val id = ItemCatalog.identityId(requestedId.takeIf(String::isNotBlank), name)
    val metadata = JSONObject()
    catalog?.metadata?.forEach { (key, value) -> metadata.put(key, value) }
    raw.optJSONObject("metadata")?.let { extra ->
      val keys = extra.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        metadata.put(key, extra.optString(key, ""))
      }
    }
    return JSONObject()
      .put("id", id)
      .put("name", name)
      .put("quantity", raw.optInt("quantity", 1).coerceIn(1, 999))
      .put("available", true)
      .put("locationKey", location.trim())
      .put("metadata", metadata)
  }

  private fun sameLocation(record: JSONObject, location: String): Boolean {
    val recorded = normalized(record.optString("locationKey", ""))
    val current = normalized(location)
    return recorded.isBlank() || current.isBlank() || recorded == current
  }

  private fun sameIdentity(left: JSONObject, right: JSONObject): Boolean = ItemCatalog.sameIdentity(
    left.optString("id", ""), left.optString("name", ""),
    right.optString("id", ""), right.optString("name", "")
  )

  fun record(flagsJson: String?, location: String?, itemJson: String): String? {
    val rawItem = runCatching { JSONObject(itemJson) }.getOrNull() ?: return null
    val currentLocation = location.orEmpty().trim()
    val record = canonical(rawItem, currentLocation) ?: return null
    val root = flags(flagsJson)
    val items = root.optJSONArray("worldItems") ?: JSONArray()
    var existing = -1
    for (index in 0 until items.length()) {
      val candidate = items.optJSONObject(index) ?: continue
      if (sameIdentity(candidate, record) && sameLocation(candidate, currentLocation)) {
        existing = index
        break
      }
    }
    if (existing >= 0) {
      val previous = items.optJSONObject(existing)
      val merged = JSONObject(record.toString())
      if (previous != null && previous.optBoolean("available", true)) {
        merged.put("quantity", maxOf(previous.optInt("quantity", 1), record.optInt("quantity", 1)))
      }
      items.put(existing, merged)
    } else {
      items.put(record)
    }
    root.put("worldItems", items)
    return root.toString()
  }

  private fun aliases(record: JSONObject): Set<String> = ItemCatalog.aliasTextsFor(
    record.optString("id", ""), record.optString("name", "")
  ).map(::normalized).filter(String::isNotBlank).toSet()

  private fun matchesAction(record: JSONObject, action: String): Boolean {
    val text = normalized(action)
    return aliases(record).any { alias ->
      text.contains(alias) || alias.split(Regex("\\s+")).filter { it.length >= 4 }.any(text::contains)
    }
  }

  private fun genericPickup(action: String): Boolean {
    val text = normalized(action).replace(Regex("[.!?,]+$"), "").trim()
    if (text in setOf(
        "nhặt", "nhặt lấy", "lượm", "lấy", "nhặt vật phẩm", "nhặt lấy vật phẩm",
        "nhặt lấy vật phẩm trên bàn", "lấy vật phẩm trên bàn", "nhặt hết", "lấy hết",
        "nhặt tất cả", "lấy tất cả", "pick up", "pick up all", "take all"
      )) return true
    val pickupVerb = text.startsWith("nhặt") || text.startsWith("lượm") || text.startsWith("lấy") || text.startsWith("pick up") || text.startsWith("take")
    return pickupVerb && (
      text.contains("tất cả") || text.contains("cả hai") || text.contains("hết") ||
        text.contains("vật phẩm") || text.contains("đồ trên") || text.contains("trên bàn")
    )
  }

  private fun availabilityCue(text: String): Boolean = listOf(
    "nhìn thấy", "bạn thấy", "nằm ", "trên bàn", "trên mặt bàn", "bên trong", "đặt ",
    "lộ ra", "vật phẩm", "chưa qua sử dụng", "còn nguyên"
  ).any { normalized(text).contains(it) }

  private fun inferFromRecentNarrative(items: JSONArray, location: String, narratives: List<String>): Boolean {
    for (narrative in narratives.take(6)) {
      if (!availabilityCue(narrative)) continue
      val text = normalized(narrative)
      val discovered = ItemCatalog.officialMentions(text)
      if (discovered.isEmpty()) continue
      discovered.forEach { item ->
        val raw = JSONObject().put("id", item.id).put("name", item.name).put("quantity", 1).put("metadata", JSONObject(item.metadata))
        val record = canonical(raw, location) ?: return@forEach
        var duplicate = false
        for (index in 0 until items.length()) {
          val existing = items.optJSONObject(index) ?: continue
          if (sameIdentity(existing, record) && sameLocation(existing, location) && existing.optBoolean("available", true)) {
            duplicate = true
            break
          }
        }
        if (!duplicate) items.put(record)
      }
      return true
    }
    return false
  }

  fun reconcileNarrative(
    flagsJson: String?,
    location: String?,
    narrative: String,
    ownedInventoryJson: String? = null
  ): String {
    val currentLocation = location.orEmpty().trim()
    val root = flags(flagsJson)
    val items = root.optJSONArray("worldItems") ?: JSONArray()
    val firstInferredIndex = items.length()
    inferFromRecentNarrative(items, currentLocation, listOf(narrative))

    val owned = runCatching { JSONArray(ownedInventoryJson ?: "[]") }.getOrElse { JSONArray() }
    val ownedIds = linkedSetOf<String>()
    val ownedNames = linkedSetOf<String>()
    for (index in 0 until owned.length()) {
      val item = owned.optJSONObject(index) ?: continue
      normalized(item.optString("id", "")).takeIf(String::isNotBlank)?.let(ownedIds::add)
      normalized(item.optString("name", "")).takeIf(String::isNotBlank)?.let(ownedNames::add)
    }
    for (index in items.length() - 1 downTo firstInferredIndex) {
      val item = items.optJSONObject(index) ?: continue
      if (normalized(item.optString("id", "")) in ownedIds || normalized(item.optString("name", "")) in ownedNames) {
        items.remove(index)
      }
    }
    root.put("worldItems", items)
    return root.toString()
  }

  private fun localAvailable(items: JSONArray, location: String): List<Pair<Int, JSONObject>> {
    val result = mutableListOf<Pair<Int, JSONObject>>()
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      if (!item.optBoolean("available", true) || item.optInt("quantity", 1) <= 0) continue
      if (!sameLocation(item, location)) continue
      result += index to item
    }
    return result
  }

  fun consume(
    flagsJson: String?,
    location: String?,
    action: String,
    recentNarratives: List<String> = emptyList()
  ): WorldItemPickup? {
    val currentLocation = location.orEmpty().trim()
    val root = flags(flagsJson)
    val items = root.optJSONArray("worldItems") ?: JSONArray()
    var available = localAvailable(items, currentLocation)
    if (available.isEmpty() && inferFromRecentNarrative(items, currentLocation, recentNarratives)) {
      available = localAvailable(items, currentLocation)
    }
    if (available.isEmpty()) return null

    val matching = available.filter { (_, item) -> matchesAction(item, action) }
    val selected = when {
      matching.isNotEmpty() -> matching
      available.size == 1 -> available
      genericPickup(action) -> available
      else -> emptyList()
    }
    if (selected.isEmpty()) return null

    val taken = mutableListOf<WorldItemRecord>()
    selected.forEach { (index, item) ->
      val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
      val remaining = quantity - 1
      val id = item.optString("id", "").ifBlank { stableId(item.optString("name", "Item")) }
      val name = item.optString("name", "").ifBlank { id }
      val metadata = strings(item.optJSONObject("metadata")) + mapOf(
        "worldInstanceId" to item.optString("instanceId", "world:$id:$index").ifBlank { "world:$id:$index" },
        "itemOrigin" to "WORLD",
        "omnivaultOriginal" to "true"
      )
      taken += WorldItemRecord(id, name, 1, metadata)
      if (remaining <= 0) item.put("quantity", 0).put("available", false)
      else item.put("quantity", remaining)
      items.put(index, item)
    }
    root.put("worldItems", items)
    return WorldItemPickup(taken, root.toString())
  }
}
