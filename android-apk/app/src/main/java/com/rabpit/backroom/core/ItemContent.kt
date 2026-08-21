package com.rabpit.backroom.core

enum class ContentState { FULL, LOW, EMPTY, NONE }

data class ContentProfile(
  val archetypeId: String,
  val fullName: String,
  val lowName: String,
  val emptyName: String,
  val supportsLow: Boolean = true
)

/**
 * Small deterministic registry for item-content state. The game never stores ml/grams/percentages.
 * Unstructured/legacy names are normalized once into FULL / LOW / EMPTY / NONE, then gameplay
 * operates only on the structured state.
 */
object ItemContentRules {
  private val emptyWords = Regex("(?:\\brỗng\\b|\\btrống\\b|\\bđã hết\\b|\\bhết sạch\\b)", RegexOption.IGNORE_CASE)
  private val lowWords = Regex("(?:còn ít|sắp hết|gần hết)", RegexOption.IGNORE_CASE)

  fun normalize(item: ItemStack): ItemStack {
    if (item.contentState != ContentState.NONE || item.metadata["contentState"] != null) {
      val state = item.metadata["contentState"]?.let { runCatching { ContentState.valueOf(it) }.getOrNull() } ?: item.contentState
      val profile = profileFor(item.name, item.archetypeId)
      return item.copy(
        archetypeId = profile?.archetypeId ?: item.archetypeId,
        contentState = state,
        name = displayName(profile, state, item.name),
        metadata = item.metadata - "remainingContent" - "contentAmount" - "contentPercent" + ("contentState" to state.name)
      )
    }

    val profile = profileFor(item.name, item.archetypeId) ?: return item.copy(contentState = ContentState.NONE)
    val state = when {
      emptyWords.containsMatchIn(item.name) || item.name.startsWith("vỏ ", true) -> ContentState.EMPTY
      lowWords.containsMatchIn(item.name) -> ContentState.LOW
      else -> ContentState.FULL
    }
    return item.copy(
      archetypeId = profile.archetypeId,
      contentState = state,
      name = displayName(profile, state, item.name),
      metadata = item.metadata - "remainingContent" - "contentAmount" - "contentPercent" + ("contentState" to state.name)
    )
  }

  fun nextAfterUse(item: ItemStack): ItemStack? {
    val normalized = normalize(item)
    val profile = profileFor(normalized.name, normalized.archetypeId) ?: return normalized
    val next = when (normalized.contentState) {
      ContentState.FULL -> if (profile.supportsLow) ContentState.LOW else ContentState.EMPTY
      ContentState.LOW -> ContentState.EMPTY
      ContentState.EMPTY -> return null
      ContentState.NONE -> return normalized
    }
    return normalized.copy(
      itemId = variantId(profile.archetypeId, next),
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
    return a.archetypeId == b.archetypeId && a.contentState == b.contentState &&
      a.condition == b.condition && stackMetadata(a.metadata) == stackMetadata(b.metadata)
  }

  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf("omnivaultCopyCount", "lastUsedAt")

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
      hint.contains("food-container") || n.contains("hộp thức ăn") || n.contains("hộp đồ ăn") || n.contains("vỏ thức ăn") || (n.contains("hộp") && n.contains("rỗng")) ->
        ContentProfile("food-container", "Hộp thức ăn", "Hộp thức ăn còn ít", "Hộp rỗng")
      hint.contains("fuel-container") || n.contains("bình nhiên liệu") || n.contains("can nhiên liệu") ->
        ContentProfile("fuel-container", "Bình nhiên liệu", "Bình nhiên liệu còn ít", "Bình rỗng")
      hint.contains("ammo-cartridge") || n.contains("viên đạn") || n.contains("vỏ đạn") ->
        ContentProfile("ammo-cartridge", "Viên đạn", "Viên đạn", "Vỏ đạn", supportsLow = false)
      else -> null
    }
  }
}
