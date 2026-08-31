package com.rabpit.backroom.core

enum class ContentState { FULL, LOW, EMPTY, NONE }

data class ContentProfile(
  val archetypeId: String,
  val fullName: String,
  val lowName: String,
  val emptyName: String,
  val supportsLow: Boolean = true
)

object ItemContentRules {
  private val emptyWords = Regex("(?:\\brỗng\\b|\\btrống\\b|\\bđã hết\\b|\\bhết sạch\\b)", RegexOption.IGNORE_CASE)
  private val lowWords = Regex("(?:còn ít|sắp hết|gần hết)", RegexOption.IGNORE_CASE)
  private val forbiddenPreciseAmount = Regex("(?:\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|lit|lít|g|gram|kg|%)\\b|một nửa|nửa chai|nửa hộp|phần trăm)", RegexOption.IGNORE_CASE)

  fun hasForbiddenPreciseAmount(text: String): Boolean = forbiddenPreciseAmount.containsMatchIn(text)

  fun normalize(item: ItemStack): ItemStack {
    HealingItems.normalize(item)?.let { return it }
    val official = ItemCatalog.resolveOfficial(item.itemId, item.name)
    if (official != null) {
      return official.stack(item.quantity).copy(
        name = item.name.takeIf(String::isNotBlank) ?: official.name,
        condition = item.condition,
        // Preserve authority metadata (including Omnivault bookkeeping); remove only the obsolete
        // partial-content fields. Tool resource values naturally override their catalog defaults.
        metadata = official.metadata + item.metadata - "remainingContent" - "contentAmount" - "contentPercent" - "contentState",
        contentState = ContentState.NONE
      )
    }
    val profile = profileFor(item.name, item.archetypeId)
    if (profile == null) {
      return item.copy(contentState = ContentState.NONE, metadata = item.metadata - "remainingContent" - "contentAmount" - "contentPercent")
    }

    val explicit = item.metadata["contentState"]?.let { runCatching { ContentState.valueOf(it) }.getOrNull() }
    val state = explicit ?: if (item.contentState != ContentState.NONE) item.contentState else when {
      emptyWords.containsMatchIn(item.name) || item.name.startsWith("vỏ ", true) -> ContentState.EMPTY
      lowWords.containsMatchIn(item.name) -> ContentState.LOW
      else -> ContentState.FULL
    }
    // Legacy consumable FULL/LOW saves become one whole canonical unit. EMPTY is retained only
    // as unknown legacy data so loading never deletes user inventory.
    if (state != ContentState.EMPTY && profile.archetypeId in setOf("water-bottle", "food-container", "fuel-container")) {
      val canonical = ItemCatalog.stack(ItemCatalog.canonicalId(profile.archetypeId))!!
      return canonical.copy(quantity = item.quantity, condition = item.condition,
        metadata = canonical.metadata + (item.metadata - "remainingContent" - "contentAmount" - "contentPercent" - "contentState") + ("migratedPartialContent" to state.name))
    }
    val canonicalId = if (ItemIdentity.isOmnivaultCopy(item)) item.itemId else variantId(profile.archetypeId, state)
    return item.copy(
      itemId = canonicalId,
      archetypeId = profile.archetypeId,
      contentState = state,
      name = displayName(profile, state, item.name),
      metadata = item.metadata - "remainingContent" - "contentAmount" - "contentPercent" + ("contentState" to state.name)
    )
  }

  fun nextAfterUse(item: ItemStack): ItemStack? {
    val normalized = normalize(item)
    if (ItemCatalog.find(normalized.itemId)?.type == OfficialItemType.CONSUMABLE) return null
    val profile = profileFor(normalized.name, normalized.archetypeId) ?: return normalized
    val next = when (normalized.contentState) {
      ContentState.FULL -> if (profile.supportsLow) ContentState.LOW else ContentState.EMPTY
      ContentState.LOW -> ContentState.EMPTY
      ContentState.EMPTY -> return null
      ContentState.NONE -> return normalized
    }
    return normalized.copy(
      itemId = if (ItemIdentity.isOmnivaultCopy(normalized)) normalized.itemId else variantId(profile.archetypeId, next),
      name = displayName(profile, next, normalized.name),
      contentState = next,
      metadata = normalized.metadata + ("contentState" to next.name)
    )
  }

  fun variantId(archetypeId: String, state: ContentState): String = when (state) {
    ContentState.NONE -> archetypeId
    else -> "$archetypeId:${state.name.lowercase()}"
  }

  fun sameStackState(left: ItemStack, right: ItemStack): Boolean {
    val a = normalize(left); val b = normalize(right)
    return a.itemId == b.itemId && a.archetypeId == b.archetypeId && a.contentState == b.contentState &&
      a.condition == b.condition && stackMetadata(a.metadata) == stackMetadata(b.metadata)
  }

  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf(
    "omnivaultCopyCount", "lastUsedAt", "physicalInstanceIds", "identitySeed", "worldInstanceId",
    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId"
  )

  private fun displayName(profile: ContentProfile?, state: ContentState, fallback: String): String {
    if (profile == null) return fallback
    return when (state) {
      ContentState.FULL -> profile.fullName
      ContentState.LOW -> profile.lowName
      ContentState.EMPTY -> profile.emptyName
      ContentState.NONE -> fallback
    }
  }

  private fun profileFor(name: String, archetypeHint: String): ContentProfile? {
    val n = name.lowercase()
    val hint = archetypeHint.lowercase()
    return when {
      hint.contains("water-bottle") || n.contains("chai nước") || (n.contains("chai") && (n.contains("rỗng") || n.startsWith("vỏ chai"))) ->
        ContentProfile("water-bottle", "Chai nước", "Chai nước còn ít nước", "Chai rỗng")
      hint.contains("food-container") || n.contains("hộp thức ăn") || n.contains("hộp đồ ăn") || n.contains("vỏ thức ăn") ->
        ContentProfile("food-container", "Hộp thức ăn", "Hộp thức ăn còn ít", "Hộp thức ăn rỗng")
      hint.contains("generic-container") || (n.contains("hộp") && n.contains("rỗng")) || n.contains("vỏ hộp") ->
        ContentProfile("generic-container", "Hộp", "Hộp còn ít vật chứa", "Hộp rỗng")
      hint.contains("fuel-container") || n.contains("bình nhiên liệu") || n.contains("can nhiên liệu") ->
        ContentProfile("fuel-container", "Bình nhiên liệu", "Bình nhiên liệu còn ít", "Bình rỗng")
      hint.contains("ammo-cartridge") || n.contains("viên đạn") || n.contains("vỏ đạn") ->
        ContentProfile("ammo-cartridge", "Viên đạn", "Viên đạn", "Vỏ đạn", supportsLow = false)
      else -> null
    }
  }
}
