package com.rabpit.backroom.core

data class InventoryProfile(val maxTypes: Int, val maxPerType: Int)

/**
 * Authoritative backpack occupancy: an owned, currently equipped item uses no
 * backpack slot, regardless of how many equipment slots it occupies.
 */
object InventoryCapacityPolicy {
  fun equippedItemIds(state: GameState, characterId: String): Set<String> =
    state.equipment[characterId]?.slots.orEmpty().values.toSet()

  fun consumesSlot(state: GameState, characterId: String, itemId: String): Boolean =
    itemId in state.inventories[characterId]?.items.orEmpty() &&
      itemId !in equippedItemIds(state, characterId)

  fun usedSlots(state: GameState, characterId: String, inventory: InventoryState =
    state.inventories[characterId] ?: InventoryState(characterId)): Int {
    val equipped = equippedItemIds(state, characterId)
    return inventory.items.keys.count { it !in equipped }
  }

  fun maxSlots(state: GameState, characterId: String): Int =
    InventoryPolicy.profileFor(state, characterId).maxTypes
}

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
    // Subtraction bound-check avoids signed Int overflow from untrusted quantities.
    if (quantity > profile.maxPerType || (old?.quantity ?: 0) > profile.maxPerType - quantity) {
      return "inventory_stack_limit"
    }
    if (old == null && normalized.itemId !in InventoryCapacityPolicy.equippedItemIds(state, ownerId) &&
        InventoryCapacityPolicy.usedSlots(state, ownerId, inventory) >= profile.maxTypes) {
      return "inventory_slot_limit"
    }
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
