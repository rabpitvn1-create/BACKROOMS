package com.rabpit.backroom.core

enum class ContentState { NONE }

object ItemContentRules {
  private val forbiddenAmount = Regex("(?:\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|lit|lít|g|gram|kg|%)\\b|một nửa|nửa chai|nửa hộp|phần trăm)", RegexOption.IGNORE_CASE)

  fun hasForbiddenPreciseAmount(text: String): Boolean = forbiddenAmount.containsMatchIn(text)

  fun normalize(item: ItemStack): ItemStack {
    val name = item.name.lowercase()
    val consumable = name.contains("chai nước") || name.contains("hộp thức ăn") || name.contains("hộp đồ ăn") ||
      name.contains("bình nhiên liệu") || name.contains("can nhiên liệu") || name.contains("viên đạn") ||
      item.metadata["physiologyEffect"]?.split(',', ';', '|')?.any { it.trim().uppercase() in setOf("WATER", "FOOD") } == true
    val metadata = item.metadata - setOf("contentState", "remainingContent", "contentAmount", "contentPercent", "containerPersistent")
    return item.copy(
      contentState = ContentState.NONE,
      metadata = if (consumable) metadata + ("consumedOnUse" to "true") else metadata
    )
  }

  fun nextAfterUse(item: ItemStack): ItemStack? {
    val normalized = normalize(item)
    return if (normalized.metadata["consumedOnUse"].equals("true", true) ||
      normalized.metadata["consumable"].equals("true", true)) null else normalized
  }

  fun sameStackState(left: ItemStack, right: ItemStack): Boolean {
    val a = normalize(left); val b = normalize(right)
    return a.itemId == b.itemId && a.archetypeId == b.archetypeId &&
      a.condition == b.condition && stackMetadata(a.metadata) == stackMetadata(b.metadata)
  }

  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf(
    "omnivaultCopyCount", "lastUsedAt", "physicalInstanceIds", "identitySeed", "worldInstanceId",
    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId"
  )
}
