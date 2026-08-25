package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class ItemCapacity(val maxTypes: Int, val maxPerType: Int)

data class ItemInspection(
  val id: String,
  val name: String,
  val quantity: Int,
  val description: String,
  val itemType: String,
  val ownerId: String?,
  val locationKey: String?,
  val contentState: String,
  val capabilities: Set<String>
)

/** Shared data contract for every current and future item, character and location. */
object ItemSystem {
  private val capacityProfiles = mapOf(
    "kai" to ItemCapacity(9, 999),
    "special_companion" to ItemCapacity(6, 20),
    "lucia_gift_inventory" to ItemCapacity(3, 100),
    "an_nhien_food_only" to ItemCapacity(2, 20),
    "normal" to ItemCapacity(2, 2)
  )

  private val officialDescriptions = mapOf(
    ItemCatalog.FLASHLIGHT to "Nguồn sáng cầm tay dùng pin.",
    ItemCatalog.LIGHTER to "Bật lửa cầm tay dùng nhiên liệu.",
    ItemCatalog.ALMOND_WATER to "Nước Hạnh Nhân có thể uống để hồi phục và giải khát.",
    ItemCatalog.CANNED_FOOD to "Thực phẩm đóng hộp có thể sử dụng để giảm đói.",
    ItemCatalog.BATTERY to "Pin thay thế dùng để nạp cho thiết bị tương thích.",
    ItemCatalog.LIGHTER_FUEL to "Nhiên liệu dùng để nạp cho bật lửa tương thích.",
    ItemCatalog.BANDAGE to "Băng gạc dùng để sơ cứu và xử lý chảy máu nhẹ.",
    ItemCatalog.ANTISEPTIC to "Dung dịch sát trùng dùng để xử lý nhiễm trùng.",
    ItemCatalog.PAINKILLER to "Thuốc giảm đau dùng để làm nhẹ tình trạng đau.",
    ItemCatalog.SARDINES to "Cá mòi đóng hộp có thể dùng làm thức ăn.",
    ItemCatalog.LA_VIE to "Nước suối đóng chai có thể dùng để giải khát."
  )

  fun capacityFor(state: GameState, ownerId: String): ItemCapacity {
    val metadata = state.characters[ownerId]?.metadata.orEmpty()
    val explicitTypes = metadata["inventoryMaxTypes"]?.toIntOrNull()?.takeIf { it > 0 }
    val explicitPerType = metadata["inventoryMaxPerType"]?.toIntOrNull()?.takeIf { it > 0 }
    if (explicitTypes != null && explicitPerType != null) return ItemCapacity(explicitTypes, explicitPerType)
    val profile = metadata["inventoryProfile"]?.trim()?.lowercase()
      ?: if (ownerId == KAI_ID) "kai" else "normal"
    return capacityProfiles[profile] ?: capacityProfiles.getValue("normal")
  }

  fun allowsItem(state: GameState, ownerId: String, item: ItemStack): Boolean {
    val allowed = state.characters[ownerId]?.metadata?.get("inventoryAllowedCategories").orEmpty()
      .split(',', ';', '|').map { it.trim().uppercase() }.filter(String::isNotBlank).toSet()
    if (allowed.isEmpty()) return true
    val physiologyEffects = item.metadata["physiologyEffect"].orEmpty().uppercase()
      .split(',', ';', '|').map(String::trim).toSet()
    val category = when {
      "FOOD" in physiologyEffects -> "FOOD"
      "WATER" in physiologyEffects -> "DRINK"
      else -> item.metadata["itemCategory"]?.uppercase()
      ?: item.metadata["category"]?.uppercase()
      ?: item.metadata["itemType"]?.uppercase()
      ?: ItemCatalog.find(item.archetypeId)?.type?.name
      ?: "GENERIC"
    }
    return category in allowed
  }

  fun restrictionReason(state: GameState, ownerId: String): String =
    state.characters[ownerId]?.metadata?.get("inventoryRestrictionReason")
      ?.takeIf(String::isNotBlank)
      ?: "inventory_item_category_forbidden"

  fun inspect(item: ItemStack, ownerId: String? = null, locationKey: String? = null): ItemInspection {
    val normalized = ItemContentRules.normalize(item)
    val official = ItemCatalog.find(normalized.archetypeId) ?: ItemCatalog.find(normalized.itemId)
    val copy = isOmnivaultCopy(normalized)
    val living = flag(normalized, "isLiving") || flag(normalized, "living")
    val largeAssembly = flag(normalized, "isLargeAssembly") || flag(normalized, "largeAssembly")
    val usable = normalized.metadata["usable"]?.let { value ->
      when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> null
      }
    }
      ?: (official != null || normalized.metadata.containsKey("useEffect") || normalized.metadata.containsKey("consumable"))
    val capabilities = linkedSetOf("INSPECT", "PICKUP", "TRANSFER", "DROP")
    if (usable) capabilities += "USE"
    if (!copy && !living && !largeAssembly && !normalized.metadata["scannable"].equals("false", true)) capabilities += "SCAN"
    if (!living && !largeAssembly) capabilities += "COPY_FROM_SCAN"
    return ItemInspection(
      id = normalized.itemId,
      name = normalized.name,
      quantity = normalized.quantity,
      description = normalized.metadata["description"]?.takeIf(String::isNotBlank)
        ?: officialDescriptions[official?.id]
        ?: "Vật phẩm chưa có mô tả chi tiết.",
      itemType = normalized.metadata["itemType"] ?: official?.type?.name ?: "GENERIC",
      ownerId = ownerId,
      locationKey = locationKey,
      contentState = normalized.contentState.name,
      capabilities = capabilities
    )
  }

  /** DROP transfers an item from its owner back into the current world location. */
  fun placeInWorld(state: GameState, item: ItemStack): GameState {
    val location = state.world["location"].orEmpty()
    val flags = runCatching { JSONObject(state.world["flagsJson"].orEmpty()) }.getOrElse { JSONObject() }
    val worldItems = flags.optJSONArray("worldItems") ?: JSONArray()
    val inspection = inspect(item, locationKey = location)
    val metadata = JSONObject().apply {
      item.metadata.forEach { (key, value) -> put(key, value) }
      put("description", inspection.description)
      put("itemType", inspection.itemType)
      put("dropped", "true")
    }
    worldItems.put(JSONObject().apply {
      put("id", item.itemId); put("name", item.name); put("quantity", item.quantity)
      put("available", true); put("locationKey", location)
      put("instanceId", "drop:${state.turn.currentTurnId}:${item.itemId}:${worldItems.length()}")
      put("metadata", metadata)
    })
    flags.put("worldItems", worldItems)
    return state.copy(world = state.world + ("flagsJson" to flags.toString()))
  }

  fun isOmnivaultCopy(item: ItemStack): Boolean =
    item.metadata["itemOrigin"].equals("OMNIVAULT_COPY", true) ||
      item.metadata["omnivaultCopy"].equals("true", true) ||
      item.metadata["copySourceTemplateId"].orEmpty().isNotBlank() ||
      item.itemId.startsWith("omnivault-copy:")

  private fun flag(item: ItemStack, key: String): Boolean =
    item.metadata[key].equals("true", true) || item.metadata[key] == "1"
}
