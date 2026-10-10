package com.rabpit.backroom.core

object ItemContentRules {
  private val forbiddenAmount = Regex("(?:\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|lit|lít|g|gram|kg|%)\\b|một nửa|nửa chai|nửa hộp|phần trăm)", RegexOption.IGNORE_CASE)
  fun hasForbiddenPreciseAmount(text: String): Boolean = forbiddenAmount.containsMatchIn(text)

  fun normalize(item: ItemStack): ItemStack {
    HealingItems.normalize(item)?.let { return it }
    val name = item.name.lowercase()
    val consumable = name.contains("chai nước") || name.contains("hộp thức ăn") || name.contains("hộp đồ ăn") ||
      name.contains("bình nhiên liệu") || name.contains("can nhiên liệu") || name.contains("viên đạn") ||
      item.metadata["physiologyEffect"]?.split(',', ';', '|')?.any { it.trim().uppercase() in setOf("WATER", "FOOD") } == true
    return if (consumable) item.copy(metadata = item.metadata + ("consumedOnUse" to "true")) else item
  }

  fun nextAfterUse(item: ItemStack): ItemStack? {
    val normalized = normalize(item)
    return if (normalized.metadata["consumedOnUse"].equals("true", true) || normalized.metadata["consumable"].equals("true", true)) null else normalized
  }

  fun sameStackState(left: ItemStack, right: ItemStack): Boolean {
    val a = normalize(left); val b = normalize(right)
    return a.itemId == b.itemId && a.metadata == b.metadata
  }

}
