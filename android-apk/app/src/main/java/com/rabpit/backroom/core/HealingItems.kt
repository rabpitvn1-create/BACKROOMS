package com.rabpit.backroom.core

const val BANDAGE_ID = "medical:bandage"
const val ANTISEPTIC_ID = "medical:antiseptic"

object HealingItems {
  const val DROP_ROLL_KEY = "loot"
  const val BANDAGE_NAME = "Băng gạc"
  const val ANTISEPTIC_NAME = "Thuốc sát trùng"
  const val BANDAGE_HEAL_HP = 10
  const val ANTISEPTIC_HEAL_HP = 20

  private fun normalizedName(value: String): String = value.trim().lowercase()

  fun healAmount(item: ItemStack): Int = when {
    item.itemId == BANDAGE_ID || item.archetypeId == BANDAGE_ID || normalizedName(item.name) in setOf("băng gạc", "bang gac", "bandage") -> BANDAGE_HEAL_HP
    item.itemId == ANTISEPTIC_ID || item.archetypeId == ANTISEPTIC_ID || normalizedName(item.name) in setOf("thuốc sát trùng", "thuoc sat trung", "antiseptic") -> ANTISEPTIC_HEAL_HP
    else -> 0
  }

  fun normalize(item: ItemStack): ItemStack? {
    val heal = healAmount(item)
    if (heal <= 0) return null
    val bandage = heal == BANDAGE_HEAL_HP
    val id = if (bandage) BANDAGE_ID else ANTISEPTIC_ID
    val name = if (bandage) BANDAGE_NAME else ANTISEPTIC_NAME
    return item.copy(
      itemId = id,
      name = name,
      archetypeId = id,
      contentState = ContentState.NONE,
      metadata = item.metadata + mapOf(
        "consumable" to "true",
        "consumedOnUse" to "true",
        "healHp" to heal.toString(),
        "dropRoll" to DROP_ROLL_KEY,
        "itemCategory" to "medical"
      )
    )
  }
}
