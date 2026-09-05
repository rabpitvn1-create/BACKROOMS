package com.rabpit.backroom.core

data class InventoryProfile(val maxTypes: Int, val maxPerType: Int)

object InventoryPolicy {
  val KAI = InventoryProfile(maxTypes = 14, maxPerType = 9999)
  val NORMAL = InventoryProfile(maxTypes = 8, maxPerType = 99)

  fun profileFor(state: GameState, characterId: String): InventoryProfile =
    if (characterId == KAI_ID) KAI else NORMAL

  fun validateAddition(
    state: GameState,
    ownerId: String,
    inventory: InventoryState,
    item: ItemStack,
    quantity: Int
  ): String? {
    if (quantity <= 0) return "quantity_must_be_positive"
    val normalized = ItemContentRules.normalize(item)
    if (ownerId == AN_NHIEN_ID && !AnNhienCanon.isFoodItem(normalized)) return "an_nhien_food_only"

    val profile = profileFor(state, ownerId)
    val old = inventory.items[normalized.itemId]?.let(ItemContentRules::normalize)
    if (old != null && !ItemContentRules.sameStackState(old, normalized)) return "inventory_stack_state_conflict"

    val definitionLimit = ItemDefinitionMetadata.maxStack(normalized)
    val effectiveLimit = minOf(profile.maxPerType, definitionLimit)
    val resultingQuantity = (old?.quantity ?: 0) + quantity
    if (resultingQuantity > effectiveLimit) return "inventory_stack_limit"

    if (old == null) {
      val equippedIds = state.equipment[ownerId]?.slots?.values.orEmpty().toSet()
      val usedTypes = inventory.items.keys.count { it !in equippedIds }
      if (usedTypes >= profile.maxTypes) return "inventory_slot_limit"
    }
    return null
  }

  fun isEquipped(state: GameState, ownerId: String, itemId: String): Boolean =
    itemId in state.equipment[ownerId]?.slots?.values.orEmpty()

  fun isKaiSignatureEquipment(state: GameState, item: ItemStack): Boolean {
    if (item.metadata["kaiSignatureEquipment"].equals("true", true)) return true
    val equippedIds = state.equipment[KAI_ID]?.slots?.values.orEmpty().toSet()
    if (item.itemId in equippedIds && KaiStartingEquipment.isSignature(item.itemId, item.name)) return true
    return KaiStartingEquipment.isSignature(item.itemId, item.name)
  }
}
