package com.rabpit.backroom.core

import org.json.JSONObject

object OmnivaultEngine {
  const val RESTORE_COOLDOWN_MS = 24L * 60L * 60L * 1000L
  const val MAX_SCAN_SLOTS = 3

  private enum class SourceKind { INVENTORY, STORED, WORLD }
  private data class ScanSource(val kind: SourceKind, val key: String, val item: ItemStack, val worldIndex: Int? = null)

  fun execute(state: GameState, command: OmnivaultCommand): ExecutionResult {
    if (command.actorId != KAI_ID) return invalid(state, "omnivault_owner_only")
    if (command.isLiving) return invalid(state, "living_target_forbidden")
    if (command.quantity <= 0) return invalid(state, "quantity_must_be_positive")
    if (command.operation == OmnivaultCommand.Operation.SCAN && command.isLargeAssembly) return invalid(state, "large_assembly_forbidden")
    return when (command.operation) {
      OmnivaultCommand.Operation.STORE -> store(state, command)
      OmnivaultCommand.Operation.WITHDRAW -> withdraw(state, command)
      OmnivaultCommand.Operation.SCAN -> invalid(state, "omnivault_capability_retired")
      OmnivaultCommand.Operation.COPY -> invalid(state, "omnivault_capability_retired")
      OmnivaultCommand.Operation.RESTORE -> restore(state, command)
      OmnivaultCommand.Operation.QUERY -> ExecutionResult(state, applied = false)
    }
  }

  private fun store(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val inventory = state.inventories[c.actorId] ?: return invalid(state, "inventory_missing")
    val owned = inventory.items[c.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < c.quantity) return invalid(state, "insufficient_item_quantity")
    if (InventoryPolicy.isKaiSignatureEquipment(state, owned)) return invalid(state, "signature_equipment_locked")
    val split = ItemIdentity.split(owned, c.quantity, "legacy:${c.actorId}:${owned.itemId}") ?: return invalid(state, "insufficient_item_quantity")
    val nextInventory = inventory.copy(items = if (split.remaining == null) inventory.items - c.itemId else inventory.items + (c.itemId to split.remaining))
    val oldStored = state.omnivault.storedItems[c.itemId]
    val stored = if (oldStored == null) split.taken else ItemIdentity.merge(oldStored, split.taken)
    return changed(
      state.copy(
        inventories = state.inventories + (c.actorId to nextInventory),
        omnivault = state.omnivault.copy(storedItems = state.omnivault.storedItems + (c.itemId to stored))
      ),
      "omnivault_stored"
    )
  }

  private fun withdraw(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val stored = state.omnivault.storedItems[c.itemId] ?: return invalid(state, "item_not_stored")
    if (stored.quantity < c.quantity) return invalid(state, "insufficient_stored_quantity")
    val split = ItemIdentity.split(stored, c.quantity, "legacy:omnivault:${stored.itemId}") ?: return invalid(state, "insufficient_stored_quantity")
    val inventory = state.inventories[c.actorId] ?: InventoryState(c.actorId)
    val validation = InventoryPolicy.validateAddition(state, c.actorId, inventory, split.taken, c.quantity)
    if (validation != null) return invalid(state, validation)
    val nextStored = if (split.remaining == null) state.omnivault.storedItems - c.itemId else state.omnivault.storedItems + (c.itemId to split.remaining)
    val old = inventory.items[split.taken.itemId]
    val item = if (old == null) split.taken else ItemIdentity.merge(old, split.taken)
    return changed(
      state.copy(
        inventories = state.inventories + (c.actorId to inventory.copy(items = inventory.items + (item.itemId to item))),
        omnivault = state.omnivault.copy(storedItems = nextStored)
      ),
      "omnivault_withdrawn"
    )
  }

  private fun restore(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val requested = when {
      EquipmentCatalog.definition(c.itemId)?.id in setOf(KAI_SRU_SG_ID, KAI_SRU_MK20_ID, KAI_OMNIVAULT_RING_ID) -> EquipmentCatalog.definition(c.itemId)!!.id
      c.itemName.contains("SRU-SG", true) -> KAI_SRU_SG_ID
      c.itemName.contains("SRU-MK20", true) -> KAI_SRU_MK20_ID
      c.itemName.contains("Omnivault", true) -> KAI_OMNIVAULT_RING_ID
      else -> return invalid(state, "omnivault_restore_noncurrent_equipment")
    }
    val definition = EquipmentCatalog.definition(requested) ?: return invalid(state, "omnivault_restore_unknown_equipment")
    val now = c.timestampEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    val cooldownUntil = state.omnivault.restoreCooldownUntilEpochMs[requested] ?: 0L
    if (cooldownUntil > now) return invalid(state, "omnivault_restore_cooldown")

    val inventory = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val equipment = state.equipment[KAI_ID] ?: EquipmentState(KAI_ID)
    val existing = inventory.items[requested]
    val equipped = definition.occupiesSlots.all { equipment.slots[it.key] == requested }
    val ready = existing?.condition.equals("READY", true)
    if (existing != null && ready && equipped) return invalid(state, "omnivault_restore_not_needed")

    val restored = EquipmentCatalog.stackFor(requested).copy(
      condition = "READY",
      metadata = EquipmentCatalog.stackFor(requested).metadata + existing?.metadata.orEmpty() + mapOf("restoredByOmnivault" to "true")
    )
    val slots = equipment.slots.toMutableMap()
    definition.occupiesSlots.forEach { slots[it.key] = requested }
    val next = state.copy(
      inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (requested to restored))),
      equipment = state.equipment + (KAI_ID to equipment.copy(slots = slots)),
      omnivault = state.omnivault.copy(
        restoreCooldownUntilEpochMs = state.omnivault.restoreCooldownUntilEpochMs + (requested to (now + RESTORE_COOLDOWN_MS))
      )
    )
    return changed(CharacterStatEngine.preserveMissingHp(state, next, KAI_ID), "omnivault_equipment_restored")
  }

  private fun scan(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val source = resolveScanSource(state, c) ?: return invalid(state, "scan_source_missing")
    if (!c.isOriginal || ItemIdentity.isOmnivaultCopy(source.item)) return invalid(state, "copy_cannot_be_scanned")
    if (source.item.metadata["scannable"].equals("false", true)) return invalid(state, "copy_cannot_be_scanned")
    if (isLiving(source.item)) return invalid(state, "living_target_forbidden")
    if (isLargeAssembly(source.item)) return invalid(state, "large_assembly_forbidden")
    if (InventoryPolicy.isKaiSignatureEquipment(state, source.item)) return invalid(state, "signature_equipment_locked")

    val seed = when (source.kind) {
      SourceKind.WORLD -> source.item.metadata["worldInstanceId"] ?: "world:${source.key}:${source.worldIndex ?: 0}"
      SourceKind.STORED -> "omnivault:${source.key}"
      SourceKind.INVENTORY -> "inventory:${c.actorId}:${source.key}"
    }
    val ensured = ItemIdentity.ensureOriginalInstances(ItemContentRules.normalize(source.item), seed)
    val ids = ItemIdentity.instanceIds(ensured)
    val marked = state.omnivault.markedSourceIds
    val legacyConsumed = source.item.itemId in marked && ids.none { it in marked }
    val candidates = if (legacyConsumed) ids.drop(1) else ids
    val sourceInstanceId = candidates.firstOrNull { it !in marked } ?: return invalid(state, "source_already_marked")

    var base = state
    if (source.kind == SourceKind.INVENTORY) {
      val inventory = state.inventories[c.actorId] ?: InventoryState(c.actorId)
      base = state.copy(inventories = state.inventories + (c.actorId to inventory.copy(items = inventory.items + (source.key to ensured))))
    } else if (source.kind == SourceKind.STORED) {
      base = state.copy(omnivault = state.omnivault.copy(storedItems = state.omnivault.storedItems + (source.key to ensured)))
    }

    val templateId = "template:${safe(c.commandId)}"
    val template = ItemIdentity.withInstanceIds(ensured.copy(quantity = 1), listOf(sourceInstanceId)).copy(
      metadata = ItemIdentity.withInstanceIds(ensured.copy(quantity = 1), listOf(sourceInstanceId)).metadata + mapOf(
        "omnivaultTemplateId" to templateId,
        "omnivaultSourceInstanceId" to sourceInstanceId,
        "omnivaultTemplate" to "true"
      )
    )
    val slots = base.omnivault.scanSlots.toMutableList()
    if (slots.size == MAX_SCAN_SLOTS) slots.removeAt(0)
    slots += ScanSlot(slots.size + 1, ensured.itemId, template, c.timestampEpochMs)
    val normalizedSlots = slots.mapIndexed { index, slot -> slot.copy(slot = index + 1) }
    val nextOmnivault = base.omnivault.copy(
      scanSlots = normalizedSlots,
      markedSourceIds = base.omnivault.markedSourceIds + ensured.itemId + sourceInstanceId
    )
    return changed(base.copy(omnivault = nextOmnivault), "omnivault_scanned")
  }

  private fun copy(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val templateSlot = resolveTemplate(state, c) ?: return invalid(state, if (isTemplateAmbiguous(state, c)) "scan_template_ambiguous" else "scan_template_missing")
    val template = templateSlot.templateItem
    if (ItemIdentity.isOmnivaultCopy(template)) return invalid(state, "copy_template_invalid")
    if (InventoryPolicy.isKaiSignatureEquipment(state, template)) return invalid(state, "signature_equipment_locked")
    val templateId = ItemIdentity.templateId(templateSlot)
    val inventory = state.inventories[c.actorId] ?: InventoryState(c.actorId)
    val existingTotal = inventory.items.values.filter { candidate ->
      candidate.metadata["copySourceTemplateId"] == templateId ||
        (!ItemIdentity.isOmnivaultCopy(candidate) && ItemIdentity.sameTemplateState(template, candidate))
    }.sumOf { it.quantity.toLong() }
    val toCreate = c.targetTotal?.let { target -> (target.toLong() - existingTotal).coerceAtLeast(0L) } ?: c.quantity.toLong()
    if (toCreate == 0L) return changed(state, "omnivault_copy_target_met")
    if (toCreate > Int.MAX_VALUE) return invalid(state, "inventory_stack_overflow")

    val created = ItemIdentity.copyFromTemplate(template, templateId, toCreate.toInt())
    val validation = InventoryPolicy.validateAddition(state, c.actorId, inventory, created, created.quantity)
    if (validation != null) return invalid(state, validation)
    val old = inventory.items[created.itemId]
    val merged = if (old == null) created else ItemIdentity.merge(old, created)
    val counted = merged.copy(metadata = merged.metadata + ("omnivaultCopyCount" to merged.quantity.toString()))
    return changed(
      state.copy(inventories = state.inventories + (c.actorId to inventory.copy(items = inventory.items + (counted.itemId to counted)))),
      "omnivault_copied"
    )
  }

  private fun resolveTemplate(state: GameState, c: OmnivaultCommand): ScanSlot? {
    c.templateId?.takeIf(String::isNotBlank)?.let { id ->
      return state.omnivault.scanSlots.firstOrNull { ItemIdentity.templateId(it) == id }
    }
    c.templateSlot?.let { slot ->
      return state.omnivault.scanSlots.firstOrNull { it.slot == slot }
    }
    val matches = templateMatches(state, c)
    return matches.singleOrNull()
  }

  private fun isTemplateAmbiguous(state: GameState, c: OmnivaultCommand): Boolean =
    c.templateId.isNullOrBlank() && c.templateSlot == null && templateMatches(state, c).size > 1

  private fun templateMatches(state: GameState, c: OmnivaultCommand): List<ScanSlot> = state.omnivault.scanSlots.filter { slot ->
    slot.sourceItemId == c.itemId || slot.templateItem.itemId == c.itemId || slot.templateItem.archetypeId == c.itemId ||
      slot.templateItem.name.equals(c.itemName, true)
  }

  private fun resolveScanSource(state: GameState, c: OmnivaultCommand): ScanSource? {
    val inventory = state.inventories[c.actorId]
    val exactInventory = inventory?.items?.get(c.itemId)
    if (exactInventory != null) return ScanSource(SourceKind.INVENTORY, c.itemId, exactInventory)
    inventory?.items?.entries?.firstOrNull { (_, item) ->
      !ItemIdentity.isOmnivaultCopy(item) && sameNamedObject(item, c)
    }?.let { return ScanSource(SourceKind.INVENTORY, it.key, it.value) }

    val exactStored = state.omnivault.storedItems[c.itemId]
    if (exactStored != null) return ScanSource(SourceKind.STORED, c.itemId, exactStored)
    state.omnivault.storedItems.entries.firstOrNull { (_, item) ->
      !ItemIdentity.isOmnivaultCopy(item) && sameNamedObject(item, c)
    }?.let { return ScanSource(SourceKind.STORED, it.key, it.value) }

    return worldSource(state, c)
  }

  private fun worldSource(state: GameState, c: OmnivaultCommand): ScanSource? {
    val flags = runCatching { JSONObject(state.world["flagsJson"] ?: return null) }.getOrNull() ?: return null
    val items = flags.optJSONArray("worldItems") ?: return null
    var best: Pair<Int, JSONObject>? = null
    var bestScore = 0
    for (index in 0 until items.length()) {
      val raw = items.optJSONObject(index) ?: continue
      if (!raw.optBoolean("available", true)) continue
      val id = raw.optString("id").trim()
      val name = raw.optString("name").trim()
      val score = matchScore(id, name, c.itemId, c.itemName)
      if (score > bestScore) {
        bestScore = score
        best = index to raw
      }
    }
    val selected = best ?: return null
    val index = selected.first
    val raw = selected.second
    val name = raw.optString("name").trim().ifBlank { c.itemName }
    val id = raw.optString("id").trim().ifBlank { stableItemId(name) }
    val metadata = linkedMapOf<String, String>()
    raw.optJSONObject("metadata")?.let { json -> json.keys().forEach { key -> metadata[key] = json.optString(key) } }
    val instanceId = raw.optString("instanceId").trim().ifBlank { "world:${safe(id)}:$index" }
    metadata["worldInstanceId"] = instanceId
    metadata["itemOrigin"] = "WORLD"
    metadata["omnivaultOriginal"] = "true"
    for (key in listOf("isLiving", "living", "isLargeAssembly", "largeAssembly")) {
      if (raw.has(key)) metadata[key] = raw.optBoolean(key, false).toString()
    }
    val item = ItemContentRules.normalize(ItemStack(id, name, raw.optInt("quantity", 1).coerceAtLeast(1), metadata = metadata))
    return ScanSource(SourceKind.WORLD, item.itemId, item, index)
  }

  private fun sameNamedObject(item: ItemStack, c: OmnivaultCommand): Boolean =
    item.itemId == c.itemId || item.archetypeId == c.itemId || matchScore(item.itemId, item.name, c.itemId, c.itemName) >= 20

  private fun matchScore(id: String, name: String, wantedId: String, wantedName: String): Int {
    if (id.isNotBlank() && id.equals(wantedId, true)) return 100
    if (name.isNotBlank() && name.equals(wantedName, true)) return 90
    val a = words(name)
    val b = words(wantedName)
    val overlap = a.intersect(b).size
    return if (overlap >= 2) 20 + overlap else if (overlap == 1 && (a.size == 1 || b.size == 1)) 10 else 0
  }

  private fun words(value: String): Set<String> = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .split(' ')
    .map(String::trim)
    .filter { it.length >= 3 }
    .toSet()

  private fun isLiving(item: ItemStack): Boolean =
    flag(item, "isLiving") || flag(item, "living") || item.metadata["itemCategory"].equals("living", true) ||
      item.metadata["itemCategory"].equals("entity", true) || item.metadata["entityId"].orEmpty().isNotBlank()

  private fun isLargeAssembly(item: ItemStack): Boolean = flag(item, "isLargeAssembly") || flag(item, "largeAssembly")

  private fun flag(item: ItemStack, key: String): Boolean = item.metadata[key].equals("true", true) || item.metadata[key] == "1"

  private fun stableItemId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-')
    .ifBlank { "world-item" }

  private fun safe(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9._:-]+"), "-")
    .trim('-')
    .ifBlank { "item" }
}
