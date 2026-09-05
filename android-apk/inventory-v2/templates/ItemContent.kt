package com.rabpit.backroom.core

enum class ContentState { FULL, LOW, EMPTY, NONE }

data class ContentProfile(
  val archetypeId: String,
  val fullName: String,
  val lowName: String,
  val emptyName: String,
  val supportsLow: Boolean = true,
  val instanceBaseId: String? = null
)

object ItemContentRules {
  private val emptyWords = Regex("(?:\\brỗng\\b|\\btrống\\b|\\bđã hết\\b|\\bhết sạch\\b)", RegexOption.IGNORE_CASE)
  private val lowWords = Regex("(?:còn ít|sắp hết|gần hết)", RegexOption.IGNORE_CASE)
  private val forbiddenPreciseAmount = Regex("(?:\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|lit|lít|g|gram|kg|%)\\b|một nửa|nửa chai|nửa hộp|phần trăm)", RegexOption.IGNORE_CASE)

  fun hasForbiddenPreciseAmount(text: String): Boolean = forbiddenPreciseAmount.containsMatchIn(text)

  fun normalize(item: ItemStack): ItemStack {
    val profile = profileFor(item)
    if (profile == null) {
      return item.copy(
        contentState = ContentState.NONE,
        metadata = item.metadata - "remainingContent" - "contentAmount" - "contentPercent" - "contentState"
      )
    }

    val explicit = item.metadata["contentState"]?.let { runCatching { ContentState.valueOf(it) }.getOrNull() }
    val state = explicit ?: if (item.contentState != ContentState.NONE) item.contentState else when {
      emptyWords.containsMatchIn(item.name) || item.name.startsWith("vỏ ", true) -> ContentState.EMPTY
      lowWords.containsMatchIn(item.name) -> ContentState.LOW
      else -> ContentState.FULL
    }
    val canonicalId = variantId(profile, state)
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
    val profile = profileFor(normalized) ?: return normalized
    val next = when (normalized.contentState) {
      ContentState.FULL -> if (profile.supportsLow) ContentState.LOW else ContentState.EMPTY
      ContentState.LOW -> ContentState.EMPTY
      ContentState.EMPTY -> return null
      ContentState.NONE -> return normalized
    }
    return normalized.copy(
      itemId = variantId(profile, next),
      name = displayName(profile, next, normalized.name),
      contentState = next,
      metadata = normalized.metadata + ("contentState" to next.name)
    )
  }

  fun sameStackState(left: ItemStack, right: ItemStack): Boolean {
    val a = normalize(left)
    val b = normalize(right)
    return a.itemId == b.itemId &&
      a.archetypeId == b.archetypeId &&
      a.contentState == b.contentState &&
      a.condition == b.condition &&
      stackMetadata(a.metadata) == stackMetadata(b.metadata)
  }

  private fun variantId(profile: ContentProfile, state: ContentState): String {
    val base = profile.instanceBaseId ?: profile.archetypeId
    return if (state == ContentState.NONE) base else "$base:${state.name.lowercase()}"
  }

  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf(
    "omnivaultCopyCount",
    "lastUsedAt",
    "loot.origin",
    "loot.sourceId",
    "loot.turnId"
  )

  private fun displayName(profile: ContentProfile, state: ContentState, fallback: String): String = when (state) {
    ContentState.FULL -> profile.fullName
    ContentState.LOW -> profile.lowName
    ContentState.EMPTY -> profile.emptyName
    ContentState.NONE -> fallback
  }

  private fun profileFor(item: ItemStack): ContentProfile? {
    if (item.metadata["catalog.contentModel"] == "FULL_LOW_EMPTY") {
      val definitionId = item.metadata["catalog.definitionId"] ?: item.archetypeId
      val instanceBase = item.metadata["catalog.instanceId"]
      return ContentProfile(
        archetypeId = definitionId,
        fullName = item.metadata["catalog.stateNameFull"] ?: item.name,
        lowName = item.metadata["catalog.stateNameLow"] ?: "${item.name} còn ít",
        emptyName = item.metadata["catalog.stateNameEmpty"] ?: "${item.name} rỗng",
        supportsLow = true,
        instanceBaseId = instanceBase
      )
    }

    val name = item.name.lowercase()
    val hint = item.archetypeId.lowercase()
    return when {
      hint.contains("water-bottle") || name.contains("chai nước") || (name.contains("chai") && (name.contains("rỗng") || name.startsWith("vỏ chai"))) ->
        ContentProfile("water-bottle", "Chai nước", "Chai nước còn ít nước", "Chai rỗng")
      hint.contains("food-container") || name.contains("hộp thức ăn") || name.contains("hộp đồ ăn") || name.contains("vỏ thức ăn") ->
        ContentProfile("food-container", "Hộp thức ăn", "Hộp thức ăn còn ít", "Hộp thức ăn rỗng")
      hint.contains("generic-container") || (name.contains("hộp") && name.contains("rỗng")) || name.contains("vỏ hộp") ->
        ContentProfile("generic-container", "Hộp", "Hộp còn ít vật chứa", "Hộp rỗng")
      hint.contains("fuel-container") || name.contains("bình nhiên liệu") || name.contains("can nhiên liệu") ->
        ContentProfile("fuel-container", "Bình nhiên liệu", "Bình nhiên liệu còn ít", "Bình rỗng")
      hint.contains("ammo-cartridge") || name.contains("viên đạn") || name.contains("vỏ đạn") ->
        ContentProfile("ammo-cartridge", "Viên đạn", "Viên đạn", "Vỏ đạn", supportsLow = false)
      else -> null
    }
  }
}
