package com.rabpit.backroom.core

const val LIQUID_PAIN_ID = "liquid-pain"
const val BANDAGE_ID = "medical:bandage"
const val ANTISEPTIC_ID = "medical:antiseptic"
const val BANH_TET_ID = "southern:banh-tet"
const val COM_TAM_ID = "southern:saigon-com-tam"
const val BANH_TET_HEAL_HP = 5
const val COM_TAM_HEAL_HP = 10

object HealingItems {
  const val LIQUID_PAIN_HEAL_PERCENT = 15
  const val LIQUID_PAIN_DESCRIPTION = "Vật phẩm tiêu hao. Khi dùng, hồi 15% Max HP hiện tại mỗi vật phẩm (làm tròn xuống), không vượt quá Max HP. Không hồi sinh nhân vật đã gục."
  const val BANDAGE_NAME = "Băng gạc"
  const val ANTISEPTIC_NAME = "Thuốc sát trùng"
  const val BANDAGE_HEAL_HP = 10
  const val ANTISEPTIC_HEAL_HP = 20

  private fun normalizedName(value: String): String = value.trim().lowercase()

  fun healAmount(item: ItemStack): Int = when {
    item.itemId == BANDAGE_ID || normalizedName(item.name) in setOf("băng gạc", "bang gac", "bandage") -> BANDAGE_HEAL_HP
    item.itemId == ANTISEPTIC_ID || normalizedName(item.name) in setOf("thuốc sát trùng", "thuoc sat trung", "antiseptic") -> ANTISEPTIC_HEAL_HP
    item.itemId == BANH_TET_ID -> BANH_TET_HEAL_HP
    item.itemId == COM_TAM_ID -> COM_TAM_HEAL_HP
    else -> item.metadata["healHp"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
  }

  fun healMaxHpPercent(item: ItemStack): Int =
    if (item.itemId == LIQUID_PAIN_ID) LIQUID_PAIN_HEAL_PERCENT else 0

  fun healAmount(item: ItemStack, maxHp: Int): Int {
    val percent = healMaxHpPercent(item)
    return if (percent > 0) (maxHp.coerceAtLeast(0).toLong() * percent / 100).toInt()
      else healAmount(item)
  }

  fun normalize(item: ItemStack): ItemStack? {
    if (item.itemId == LIQUID_PAIN_ID) return item.copy(name = "Liquid Pain",
      metadata = item.metadata + mapOf(
        "consumable" to "true", "consumedOnUse" to "true",
        "description" to LIQUID_PAIN_DESCRIPTION
      ))
    val isMedical = item.itemId == BANDAGE_ID || item.itemId == ANTISEPTIC_ID ||
      normalizedName(item.name) in setOf("băng gạc", "bang gac", "bandage", "thuốc sát trùng", "thuoc sat trung", "antiseptic")
    if (!isMedical) return null
    val heal = healAmount(item)
    if (heal <= 0) return null
    val bandage = heal == BANDAGE_HEAL_HP
    val id = if (bandage) BANDAGE_ID else ANTISEPTIC_ID
    val name = if (bandage) BANDAGE_NAME else ANTISEPTIC_NAME
    return item.copy(
      itemId = id,
      name = name,
      metadata = item.metadata + mapOf(
        "consumable" to "true",
        "consumedOnUse" to "true",
        "healHp" to heal.toString(),
        "itemCategory" to "medical"
      )
    )
  }
}

