package com.rabpit.backroom.core

data class InventoryProfile(val maxTypes: Int, val maxPerType: Int)

object InventoryPolicy {
  val CAO_MINH = InventoryProfile(maxTypes = 9, maxPerType = 999)
  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 4, maxPerType = 20)
  val AN_NHIEN = InventoryProfile(maxTypes = 2, maxPerType = 20)
  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)

  fun profileFor(state: GameState, characterId: String): InventoryProfile {
    if (characterId == CAO_MINH_ID) return CAO_MINH
    if (characterId == AN_NHIEN_ID) return AN_NHIEN
    val character = state.characters[characterId]
    val key = (character?.id.orEmpty() + " " + character?.name.orEmpty()).lowercase()
    return if (key.contains("luc-tram") || key.contains("syvial")) SPECIAL_COMPANION else NORMAL
  }

  fun validateAddition(state: GameState, ownerId: String, inventory: InventoryState, item: ItemStack, quantity: Int): String? {
    if (quantity <= 0) return "quantity_must_be_positive"
    val normalized = ItemContentRules.normalize(item)
    if (ownerId == AN_NHIEN_ID && !AnNhienCanon.isFoodItem(normalized)) return "an_nhien_food_only"
    val profile = profileFor(state, ownerId)
    val old = inventory.items[normalized.itemId]
    val resultingQuantity = (old?.quantity ?: 0) + quantity
    if (resultingQuantity > profile.maxPerType) return "inventory_stack_limit"
    if (old == null && inventory.items.size >= profile.maxTypes) return "inventory_slot_limit"
    return null
  }

  fun isCaoMinhSignatureEquipment(state: GameState, item: ItemStack): Boolean {
    if (item.metadata["caoMinhSignatureEquipment"].equals("true", true)) return true
    val equippedIds = state.equipment[CAO_MINH_ID]?.slots?.values.orEmpty().toSet()
    if (item.itemId in equippedIds) return true
    val key = (item.itemId + " " + item.name).lowercase()
    return key.contains("vạn khố giới chỉ") || key.contains("nhẫn vạn khố") || key.contains("van kho gioi chi") || key.contains("nhẫn vạn tàng")
  }
}
