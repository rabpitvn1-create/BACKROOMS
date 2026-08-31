package com.rabpit.backroom.core

object ItemIdentity {
  private const val IDS_KEY = "physicalInstanceIds"
  private const val SEED_KEY = "identitySeed"

  data class Split(val remaining: ItemStack?, val taken: ItemStack)

  fun isOmnivaultCopy(item: ItemStack): Boolean =
    item.metadata["itemOrigin"].equals("OMNIVAULT_COPY", true) ||
      item.metadata["omnivaultCopy"].equals("true", true) ||
      item.metadata["copySourceTemplateId"].orEmpty().isNotBlank() ||
      item.itemId.startsWith("omnivault-copy:")

  fun instanceIds(item: ItemStack): List<String> = item.metadata[IDS_KEY].orEmpty()
    .split('|')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

  fun ensureOriginalInstances(item: ItemStack, seed: String): ItemStack {
    if (isOmnivaultCopy(item)) return item
    val ids = LinkedHashSet(instanceIds(item))
    item.metadata["worldInstanceId"]?.trim()?.takeIf(String::isNotEmpty)?.let(ids::add)
    val identitySeed = item.metadata[SEED_KEY]?.trim()?.takeIf(String::isNotEmpty) ?: seed
    var ordinal = 1
    while (ids.size < item.quantity) {
      ids += "instance:${safe(identitySeed)}:$ordinal"
      ordinal += 1
    }
    val metadata = item.metadata + mapOf(
      IDS_KEY to ids.take(item.quantity).joinToString("|"),
      SEED_KEY to identitySeed,
      "omnivaultOriginal" to "true"
    )
    return item.copy(metadata = metadata)
  }

  fun withInstanceIds(item: ItemStack, ids: List<String>): ItemStack {
    val metadata = if (ids.isEmpty()) item.metadata - IDS_KEY else item.metadata + (IDS_KEY to ids.distinct().joinToString("|"))
    return item.copy(metadata = metadata)
  }

  fun split(item: ItemStack, quantity: Int, seed: String = "legacy:${item.itemId}"): Split? {
    if (quantity <= 0 || item.quantity < quantity) return null
    val normalized = if (isOmnivaultCopy(item)) item else ensureOriginalInstances(item, seed)
    val ids = instanceIds(normalized)
    val takenIds = if (isOmnivaultCopy(normalized)) emptyList() else ids.take(quantity)
    val remainingIds = if (isOmnivaultCopy(normalized)) emptyList() else ids.drop(quantity)
    val taken = withInstanceIds(normalized.copy(quantity = quantity), takenIds)
    val remaining = if (normalized.quantity == quantity) null
      else withInstanceIds(normalized.copy(quantity = normalized.quantity - quantity), remainingIds)
    return Split(remaining, taken)
  }

  fun merge(old: ItemStack, incoming: ItemStack): ItemStack {
    val total = old.quantity.toLong() + incoming.quantity.toLong()
    require(total <= Int.MAX_VALUE) { "item_quantity_overflow" }
    if (isOmnivaultCopy(old) || isOmnivaultCopy(incoming)) {
      return old.copy(quantity = total.toInt(), metadata = old.metadata + incoming.metadata)
    }
    val ids = (instanceIds(old) + instanceIds(incoming)).distinct()
    val oldSeed = old.metadata[SEED_KEY]?.takeIf(String::isNotBlank)
    val incomingSeed = incoming.metadata[SEED_KEY]?.takeIf(String::isNotBlank)
    var metadata = old.metadata + incoming.metadata
    if (ids.isNotEmpty()) metadata = metadata + (IDS_KEY to ids.joinToString("|"))
    (oldSeed ?: incomingSeed)?.let { metadata = metadata + (SEED_KEY to it) }
    return old.copy(quantity = total.toInt(), metadata = metadata)
  }

  fun templateId(slot: ScanSlot): String = slot.templateItem.metadata["omnivaultTemplateId"]
    ?.takeIf(String::isNotBlank)
    ?: "legacy-template:${slot.slot}:${safe(slot.sourceItemId)}:${slot.scannedAtEpochMs}"

  fun copyStackId(templateId: String): String = "omnivault-copy:${safe(templateId)}"

  fun copyFromTemplate(template: ItemStack, templateId: String, quantity: Int): ItemStack {
    val metadata = template.metadata - setOf(
      IDS_KEY, SEED_KEY, "worldInstanceId", "omnivaultOriginal", "omnivaultSourceInstanceId"
    ) + mapOf(
      "itemOrigin" to "OMNIVAULT_COPY",
      "omnivaultCopy" to "true",
      "copySourceTemplateId" to templateId,
      "scannable" to "false",
      "omnivaultCopyCount" to quantity.toString()
    )
    return template.copy(
      itemId = copyStackId(templateId),
      quantity = quantity,
      metadata = metadata
    )
  }

  fun sameTemplateState(template: ItemStack, candidate: ItemStack): Boolean {
    val a = ItemContentRules.normalize(template)
    val b = ItemContentRules.normalize(candidate)
    return a.archetypeId == b.archetypeId && a.contentState == b.contentState && a.condition == b.condition
  }

  private fun safe(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9._:-]+"), "-")
    .trim('-')
    .ifBlank { "item" }
}
