package com.rabpit.backroom.core

const val BANDAGE_ID = ItemCatalog.BANDAGE
const val ANTISEPTIC_ID = ItemCatalog.ANTISEPTIC

object HealingItems {
  const val DROP_ROLL_KEY = "official-item-pool"
  const val BANDAGE_NAME = "Bandage"
  const val ANTISEPTIC_NAME = "Antiseptic"
  const val BANDAGE_HEAL_HP = 15
  const val ANTISEPTIC_HEAL_HP = 10

  private fun key(item: ItemStack): String = "${item.itemId} ${item.archetypeId} ${item.name}".lowercase()
  fun healAmount(item: ItemStack): Int = when {
    key(item).contains("bandage") || key(item).contains("băng gạc") -> BANDAGE_HEAL_HP
    key(item).contains("antiseptic") || key(item).contains("thuốc sát trùng") -> ANTISEPTIC_HEAL_HP
    else -> 0
  }

  fun normalize(item: ItemStack): ItemStack? {
    val id = when (healAmount(item)) {
      BANDAGE_HEAL_HP -> BANDAGE_ID
      ANTISEPTIC_HEAL_HP -> ANTISEPTIC_ID
      else -> return null
    }
    val canonical = ItemCatalog.stack(id) ?: return null
    return canonical.copy(
      itemId = if (ItemIdentity.isOmnivaultCopy(item)) item.itemId else id,
      quantity = item.quantity,
      condition = item.condition,
      metadata = canonical.metadata + item.metadata - "remainingContent" - "contentAmount" - "contentPercent" - "contentState"
    )
  }
}
