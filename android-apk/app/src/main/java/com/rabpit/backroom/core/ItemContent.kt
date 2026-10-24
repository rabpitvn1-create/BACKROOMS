package com.rabpit.backroom.core

/**
 * Items have one content state. A consumable represents a complete unit and
 * USING it removes exactly the requested number of units; no FULL/LOW/EMPTY
 * variants, residual shells, or fractional-content tracking are generated.
 */
enum class ContentState { NONE }

object ItemContentRules {
  private val forbiddenAmount = Regex("(?:\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|lit|lít|g|gram|kg|%)\\b|một nửa|nửa chai|nửa hộp|phần trăm)", RegexOption.IGNORE_CASE)
  private val deprecatedContentKeys = setOf("contentState", "remainingContent", "contentAmount", "contentPercent")
  private val nonStackIdentityKeys = setOf(
    "omnivaultCopyCount", "vanKhoGioiChiCopyCount", "lastUsedAt",
    "physicalInstanceIds", "identitySeed", "worldInstanceId",
    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId"
  )

  fun hasForbiddenPreciseAmount(text: String): Boolean = forbiddenAmount.containsMatchIn(text)

  fun normalize(item: ItemStack): ItemStack {
    // Canonical healing items keep their stable IDs; Omnivault copies keep their
    // distinct copy IDs through HealingItems.normalize().
    val normalized = HealingItems.normalize(item) ?: item
    val name = normalized.name.lowercase()
    val metadata = normalized.metadata - deprecatedContentKeys
    val resourceUnit = name.contains("chai nước") || name.contains("hộp thức ăn") ||
      name.contains("hộp đồ ăn") || name.contains("bình nhiên liệu") ||
      name.contains("can nhiên liệu") || name.contains("viên đạn") ||
      metadata["physiologyEffect"]?.split(',', ';', '|')?.any {
        it.trim().uppercase() in setOf("WATER", "FOOD")
      } == true
    val consumed = when {
      metadata.containsKey("consumedOnUse") -> metadata["consumedOnUse"].equals("true", true)
      metadata["containerPersistent"].equals("true", true) -> false
      else -> metadata["consumable"].equals("true", true) || resourceUnit
    }
    return normalized.copy(
      contentState = ContentState.NONE,
      metadata = (metadata - "consumedOnUse").let {
        if (consumed) it + ("consumedOnUse" to "true") else it
      }
    )
  }

  /** A consumed unit leaves no synthetic 'empty' item behind. */
  fun nextAfterUse(item: ItemStack): ItemStack? =
    normalize(item).let { if (it.metadata["consumedOnUse"].equals("true", true)) null else it }

  fun sameStackState(left: ItemStack, right: ItemStack): Boolean {
    val a = normalize(left)
    val b = normalize(right)
    return a.itemId == b.itemId && a.archetypeId == b.archetypeId &&
      a.condition == b.condition && stackMetadata(a.metadata) == stackMetadata(b.metadata)
  }

  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> =
    metadata - nonStackIdentityKeys
}
