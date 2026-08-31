package com.rabpit.backroom.core

data class InventoryProfile(val maxTypes: Int, val maxPerType: Int)

object InventoryPolicy {
  val KAI = InventoryProfile(maxTypes = 9, maxPerType = 999)
  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 6, maxPerType = 20)
  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)
  val AN_NHIEN = InventoryProfile(maxTypes = 2, maxPerType = 20)
  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)

  fun profileFor(state: GameState, characterId: String): InventoryProfile {
    val capacity = ItemSystem.capacityFor(state, characterId)
    return InventoryProfile(capacity.maxTypes, capacity.maxPerType)
  }

  fun validateAddition(state: GameState, ownerId: String, inventory: InventoryState, item: ItemStack, quantity: Int): String? {
    if (quantity <= 0) return "quantity_must_be_positive"
    val normalized = ItemContentRules.normalize(item)
    if (!ItemSystem.allowsItem(state, ownerId, normalized)) return ItemSystem.restrictionReason(state, ownerId)
    val profile = profileFor(state, ownerId)
    val old = inventory.items[normalized.itemId]
    val resultingQuantity = (old?.quantity ?: 0).toLong() + quantity.toLong()
    if (resultingQuantity > Int.MAX_VALUE) return "inventory_stack_overflow"
    if (!ItemIdentity.isOmnivaultCopy(normalized) && resultingQuantity > profile.maxPerType.toLong()) return "inventory_stack_limit"
    val carriedTypes = InventoryCapacityPolicy.usedSlots(state, ownerId, inventory)
    if (old == null && carriedTypes >= profile.maxTypes) return "inventory_slot_limit"
    return null
  }

  fun isKaiSignatureEquipment(state: GameState, item: ItemStack): Boolean {
    if (item.metadata["kaiSignatureEquipment"].equals("true", true)) return true
    val equippedIds = state.equipment[KAI_ID]?.slots?.values.orEmpty().toSet()
    if (item.itemId in equippedIds) return true
    val key = (item.itemId + " " + item.name).lowercase()
    return key.contains("omnivault ring") || key.contains("nhẫn omnivault") || key.contains("nhẫn vạn tàng")
  }
}
