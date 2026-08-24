from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
IDENTITY = CORE / "ItemIdentity.kt"
GAME_COMMAND = CORE / "GameCommand.kt"
COMMAND = CORE / "CommandPipeline.kt"
ENGINES = CORE / "Engines.kt"
OMNIVAULT = CORE / "OmnivaultEngine.kt"
ITEM_CONTENT = CORE / "ItemContent.kt"
POLICY = CORE / "InventoryPolicy.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CORE_TEST = TESTS / "GameStateCoreTest.kt"
NATURAL_TEST = TESTS / "OmnivaultNaturalFlowTest.kt"
IDENTITY_TEST = TESTS / "OmnivaultInstanceAuthorityTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Physical identity is intentionally orthogonal to ItemStack quantity. Normal
# inventory can still stack identical objects, while Omnivault can identify the
# exact original physical instance that was scanned. Copies live in a separate
# stack keyed by template identity and can therefore never masquerade as an
# original source object.
# ---------------------------------------------------------------------------
IDENTITY.write_text(r'''package com.rabpit.backroom.core

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
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# OmnivaultCommand carries template identity and target-total semantics. Fields
# are appended with defaults so every existing call site remains source-compatible.
# ---------------------------------------------------------------------------
command_model = GAME_COMMAND.read_text(encoding="utf-8")
old_command_tail = '''  val isOriginal: Boolean = true,
  val timestampEpochMs: Long = 0L
) : GameCommand {
'''
new_command_tail = '''  val isOriginal: Boolean = true,
  val timestampEpochMs: Long = 0L,
  val templateId: String? = null,
  val templateSlot: Int? = null,
  val targetTotal: Int? = null
) : GameCommand {
'''
command_model = replace_once(command_model, old_command_tail, new_command_tail, "Omnivault command identity fields")
GAME_COMMAND.write_text(command_model, encoding="utf-8")


# ---------------------------------------------------------------------------
# Item content normalization must preserve the unique copy-stack ID. Identity
# metadata is excluded from stack-state comparison so separate original physical
# objects still form one ordinary inventory stack without losing their IDs.
# ---------------------------------------------------------------------------
content = ITEM_CONTENT.read_text(encoding="utf-8")
healing_old = '    HealingItems.normalize(item)?.let { return it }\n'
healing_new = '''    HealingItems.normalize(item)?.let { healing ->
      if (!ItemIdentity.isOmnivaultCopy(item)) return healing
      return healing.copy(itemId = item.itemId, metadata = healing.metadata + item.metadata)
    }
'''
if healing_old in content:
    content = content.replace(healing_old, healing_new, 1)

canonical_old = '    val canonicalId = variantId(profile.archetypeId, state)\n'
canonical_new = '    val canonicalId = if (ItemIdentity.isOmnivaultCopy(item)) item.itemId else variantId(profile.archetypeId, state)\n'
content = replace_once(content, canonical_old, canonical_new, "Copy content-state identity")

next_old = '      itemId = variantId(profile.archetypeId, next),\n'
next_new = '      itemId = if (ItemIdentity.isOmnivaultCopy(normalized)) normalized.itemId else variantId(profile.archetypeId, next),\n'
content = replace_once(content, next_old, next_new, "Copy next-use identity")

meta_old = '  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf("omnivaultCopyCount", "lastUsedAt")\n'
meta_new = '''  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf(
    "omnivaultCopyCount", "lastUsedAt", "physicalInstanceIds", "identitySeed", "worldInstanceId",
    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId"
  )
'''
content = replace_once(content, meta_old, meta_new, "Physical identity stack comparison")
ITEM_CONTENT.write_text(content, encoding="utf-8")


# ---------------------------------------------------------------------------
# Inventory mutations preserve the exact identities that move/are consumed.
# This closes the old hole where a transfer copied metadata for the whole stack
# and where a second world pickup could overwrite the first physical identity.
# ---------------------------------------------------------------------------
engines = ENGINES.read_text(encoding="utf-8")
old_helpers = '''private fun addItem(inventory: InventoryState, rawItem: ItemStack): InventoryState {
  val item = ItemContentRules.normalize(rawItem)
  val old = inventory.items[item.itemId]?.let(ItemContentRules::normalize)
  val merged = if (old == null) item else {
    if (!ItemContentRules.sameStackState(old, item)) return inventory.copy(items = inventory.items + (item.itemId to item))
    old.copy(quantity = old.quantity + item.quantity)
  }
  return inventory.copy(items = inventory.items + (item.itemId to merged))
}

private fun removeItem(inventory: InventoryState, itemId: String, quantity: Int): InventoryState? {
  val old = inventory.items[itemId] ?: return null
  if (quantity <= 0 || old.quantity < quantity) return null
  val items = if (old.quantity == quantity) inventory.items - itemId
  else inventory.items + (itemId to old.copy(quantity = old.quantity - quantity))
  return inventory.copy(items = items)
}
'''
new_helpers = '''private data class ItemTake(val inventory: InventoryState, val taken: ItemStack)

private fun addItem(inventory: InventoryState, rawItem: ItemStack): InventoryState {
  val item = ItemContentRules.normalize(rawItem)
  val old = inventory.items[item.itemId]?.let(ItemContentRules::normalize)
  val merged = if (old == null) item else {
    if (!ItemContentRules.sameStackState(old, item)) return inventory.copy(items = inventory.items + (item.itemId to item))
    ItemIdentity.merge(old, item)
  }
  return inventory.copy(items = inventory.items + (item.itemId to merged))
}

private fun takeItem(inventory: InventoryState, itemId: String, quantity: Int): ItemTake? {
  val old = inventory.items[itemId]?.let(ItemContentRules::normalize) ?: return null
  val split = ItemIdentity.split(old, quantity, "legacy:${inventory.ownerId}:${old.itemId}") ?: return null
  val items = if (split.remaining == null) inventory.items - itemId
    else inventory.items + (itemId to split.remaining)
  return ItemTake(inventory.copy(items = items), split.taken)
}

private fun removeItem(inventory: InventoryState, itemId: String, quantity: Int): InventoryState? =
  takeItem(inventory, itemId, quantity)?.inventory
'''
engines = replace_once(engines, old_helpers, new_helpers, "Identity-aware inventory helpers")

old_content_use = '''  if (owned.contentState == ContentState.FULL || owned.contentState == ContentState.LOW) {
    val nextVariant = ItemContentRules.nextAfterUse(owned) ?: return invalid(state, "item_content_empty")
    var nextInventory = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    val validation = InventoryPolicy.validateAddition(state, command.actorId, nextInventory, nextVariant, command.quantity)
    if (validation != null) return invalid(state, validation)
    nextInventory = addItem(nextInventory, nextVariant.copy(quantity = command.quantity))
'''
new_content_use = '''  if (owned.contentState == ContentState.FULL || owned.contentState == ContentState.LOW) {
    val removal = takeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
    val nextVariant = ItemContentRules.nextAfterUse(removal.taken) ?: return invalid(state, "item_content_empty")
    var nextInventory = removal.inventory
    val validation = InventoryPolicy.validateAddition(state, command.actorId, nextInventory, nextVariant, command.quantity)
    if (validation != null) return invalid(state, validation)
    nextInventory = addItem(nextInventory, nextVariant)
'''
engines = replace_once(engines, old_content_use, new_content_use, "Content use identity transfer")

item_line_old = '    val item = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))\n'
item_line_new = '''    val normalizedItem = ItemContentRules.normalize(ItemStack(command.itemId, command.itemName, command.quantity, metadata = command.metadata))
    val item = if (command.operation == ItemCommand.Operation.PICKUP)
      ItemIdentity.ensureOriginalInstances(normalizedItem, command.metadata["worldInstanceId"] ?: command.commandId)
    else normalizedItem
'''
engines = replace_once(engines, item_line_old, item_line_new, "Authoritative pickup physical identities")

transfer_old = '''        val transferred = ItemContentRules.normalize(owned).copy(quantity = command.quantity)
        val targetInventory = state.inventories[targetId] ?: InventoryState(targetId)
        val validation = InventoryPolicy.validateAddition(state, targetId, targetInventory, transferred, command.quantity)
        if (validation != null) return invalid(state, validation)
        val from = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val to = addItem(targetInventory, transferred)
'''
transfer_new = '''        val removal = takeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")
        val transferred = removal.taken
        val targetInventory = state.inventories[targetId] ?: InventoryState(targetId)
        val validation = InventoryPolicy.validateAddition(state, targetId, targetInventory, transferred, command.quantity)
        if (validation != null) return invalid(state, validation)
        val from = removal.inventory
        val to = addItem(targetInventory, transferred)
'''
engines = replace_once(engines, transfer_old, transfer_new, "Transfer physical identity")
ENGINES.write_text(engines, encoding="utf-8")


# Copies are unlimited by the ordinary per-type gameplay cap. They still require
# a backpack type slot when first materialized, preserving the existing inventory
# UI/carry model instead of silently inventing a second storage destination.
policy = POLICY.read_text(encoding="utf-8")
policy_old = '''    val old = inventory.items[normalized.itemId]
    val resultingQuantity = (old?.quantity ?: 0) + quantity
    if (resultingQuantity > profile.maxPerType) return "inventory_stack_limit"
'''
policy_new = '''    val old = inventory.items[normalized.itemId]
    val resultingQuantity = (old?.quantity ?: 0).toLong() + quantity.toLong()
    if (resultingQuantity > Int.MAX_VALUE) return "inventory_stack_overflow"
    if (!ItemIdentity.isOmnivaultCopy(normalized) && resultingQuantity > profile.maxPerType.toLong()) return "inventory_stack_limit"
'''
policy = replace_once(policy, policy_old, policy_new, "Omnivault copy quantity policy")
POLICY.write_text(policy, encoding="utf-8")


# ---------------------------------------------------------------------------
# Resolver: transfer roles are directional, equipment slots are inferred, COPY
# binds to a concrete scan template/slot, and target-total requests are carried
# to the engine instead of being approximated from the original stack alone.
# ---------------------------------------------------------------------------
resolver = COMMAND.read_text(encoding="utf-8")
class_anchor = ''') {
  fun resolveSequence(candidates: List<IntentCandidate>, turnId: String, context: GameContext): List<GameCommand?> {
'''
class_replacement = ''') {
  private data class CopyTemplateRef(val item: Pair<String, String>, val templateId: String?, val templateSlot: Int?)

  fun resolveSequence(candidates: List<IntentCandidate>, turnId: String, context: GameContext): List<GameCommand?> {
'''
if class_anchor in resolver:
    resolver = resolver.replace(class_anchor, class_replacement, 1)
elif 'private data class CopyTemplateRef' not in resolver:
    raise RuntimeError("CommandResolver final sequence anchor missing")

transfer_branch_old = '      GameIntent.TRANSFER_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.TRANSFER, it, quantity) }\n'
transfer_branch_new = '''      GameIntent.TRANSFER_ITEM -> item?.let {
        val parties = resolveTransferParties(candidate.clause, context, actor, target)
        itemCommand(commandId, turnId, parties.first, parties.second, source, ItemCommand.Operation.TRANSFER, it, quantity)
      }
'''
resolver = replace_once(resolver, transfer_branch_old, transfer_branch_new, "Directional transfer resolver")

equip_old = '      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, "weapon") }\n'
equip_new = '      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, resolveEquipmentSlot(candidate.clause, actor, it, context, false)) }\n'
resolver = replace_once(resolver, equip_old, equip_new, "Equipment slot resolver")

unequip_old = '      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, "weapon") }\n'
unequip_new = '      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, resolveEquipmentSlot(candidate.clause, actor, it, context, true)) }\n'
resolver = replace_once(resolver, unequip_old, unequip_new, "Unequipment slot resolver")

copy_old = '      GameIntent.OMNIVAULT_COPY -> item?.let { vaultCommand(commandId, turnId, actor, source, OmnivaultCommand.Operation.COPY, it, quantity) }\n'
copy_new = '''      GameIntent.OMNIVAULT_COPY -> resolveCopyTemplate(candidate.clause, item, context)?.let { ref ->
        vaultCommand(
          commandId, turnId, actor, source, OmnivaultCommand.Operation.COPY, ref.item, quantity,
          templateId = ref.templateId,
          templateSlot = ref.templateSlot,
          targetTotal = copyTargetTotal(candidate.clause, quantity)
        )
      }
'''
resolver = replace_once(resolver, copy_old, copy_new, "Copy template resolver")

helper_pattern = re.compile(r'''  private fun resolvedQuantity\(candidate: IntentCandidate, actor: String, item: Pair<String, String>\?, rawQuantity: Int, context: GameContext\): Int \{.*?\n  \}\n\n  private fun itemCommand''', re.DOTALL)
helper_match = helper_pattern.search(resolver)
if helper_match:
    helpers = r'''  private fun resolvedQuantity(candidate: IntentCandidate, actor: String, item: Pair<String, String>?, rawQuantity: Int, context: GameContext): Int = rawQuantity

  private fun copyTargetTotal(clause: String, rawQuantity: Int): Int? =
    if (Regex("(?:thành|tổng\\s+cộng|đủ)\\s+(?:\\d+|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|một\\s+trăm)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) rawQuantity else null

  private fun resolveCopyTemplate(clause: String, item: Pair<String, String>?, context: GameContext): CopyTemplateRef? {
    val explicitSlot = Regex("(?:slot|ô|mẫu)\\s*([1-3])\\b", RegexOption.IGNORE_CASE)
      .find(clause)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (explicitSlot != null) {
      val slot = context.state.omnivault.scanSlots.firstOrNull { it.slot == explicitSlot } ?: return item?.let { CopyTemplateRef(it, null, explicitSlot) }
      return CopyTemplateRef(slot.sourceItemId to slot.templateItem.name, ItemIdentity.templateId(slot), slot.slot)
    }
    if (item == null) {
      val only = context.state.omnivault.scanSlots.singleOrNull() ?: return null
      return CopyTemplateRef(only.sourceItemId to only.templateItem.name, ItemIdentity.templateId(only), only.slot)
    }
    val matches = context.state.omnivault.scanSlots.filter { slot ->
      slot.sourceItemId == item.first || slot.templateItem.itemId == item.first || slot.templateItem.archetypeId == item.first ||
        slot.templateItem.name.equals(item.second, true)
    }
    return if (matches.size == 1) {
      val slot = matches.single()
      CopyTemplateRef(slot.sourceItemId to slot.templateItem.name, ItemIdentity.templateId(slot), slot.slot)
    } else CopyTemplateRef(item, null, null)
  }

  private fun resolveTransferParties(
    clause: String,
    context: GameContext,
    defaultActor: String,
    defaultTarget: String?
  ): Pair<String, String?> {
    val verb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE).find(clause) ?: return defaultActor to defaultTarget
    val mentions = context.actorAliases.entries.mapNotNull { (alias, id) ->
      Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE).find(clause)?.let { Triple(it.range.first, it.range.last, id) }
    }.sortedBy { it.first }
    val actor = mentions.lastOrNull { it.second < verb.range.first }?.third ?: defaultActor
    val explicitTargetStart = Regex("(?:cho|sang)\\s+", RegexOption.IGNORE_CASE).find(clause, verb.range.last + 1)?.range?.last
    val target = if (explicitTargetStart != null) {
      mentions.firstOrNull { it.first > explicitTargetStart && it.third != actor }?.third
        ?: mentions.firstOrNull { it.first > explicitTargetStart }?.third
    } else {
      mentions.firstOrNull { it.first > verb.range.last && it.third != actor }?.third
        ?: mentions.firstOrNull { it.first > verb.range.last }?.third
    }
    return actor to (target ?: defaultTarget)
  }

  private fun resolveEquipmentSlot(
    clause: String,
    actor: String,
    item: Pair<String, String>,
    context: GameContext,
    unequip: Boolean
  ): String {
    val existing = context.state.equipment[actor]?.slots?.entries?.firstOrNull { it.value == item.first }?.key
    if (unequip && existing != null) return existing
    val owned = context.state.inventories[actor]?.items?.get(item.first)
    owned?.metadata?.get("equipmentSlot")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    KaiStartingEquipment.slotFor(item.first, item.second)?.let { return it }
    val lower = clause.lowercase()
    return when {
      lower.contains("nhẫn") || lower.contains("ring") -> "ring"
      lower.contains("giáp") || lower.contains("armor") || lower.contains("áo") || lower.contains("mặc") -> "armor"
      else -> existing ?: "weapon"
    }
  }

  private fun itemCommand'''
    resolver = resolver[:helper_match.start()] + helpers + resolver[helper_match.end():]
elif 'private fun copyTargetTotal(' not in resolver:
    raise RuntimeError("CommandResolver quantity helper final form missing")

vault_old = '''  private fun vaultCommand(id: String, turn: String, actor: String, source: CommandSource, operation: OmnivaultCommand.Operation, item: Pair<String, String>, quantity: Int) =
    OmnivaultCommand(id, turn, actor, source = source, operation = operation, itemId = item.first, itemName = item.second, quantity = quantity, timestampEpochMs = System.currentTimeMillis())
'''
vault_new = '''  private fun vaultCommand(
    id: String,
    turn: String,
    actor: String,
    source: CommandSource,
    operation: OmnivaultCommand.Operation,
    item: Pair<String, String>,
    quantity: Int,
    templateId: String? = null,
    templateSlot: Int? = null,
    targetTotal: Int? = null
  ) = OmnivaultCommand(
    id, turn, actor,
    source = source,
    operation = operation,
    itemId = item.first,
    itemName = item.second,
    quantity = quantity,
    timestampEpochMs = System.currentTimeMillis(),
    templateId = templateId,
    templateSlot = templateSlot,
    targetTotal = targetTotal
  )
'''
resolver = replace_once(resolver, vault_old, vault_new, "Omnivault resolver command metadata")
COMMAND.write_text(resolver, encoding="utf-8")


# ---------------------------------------------------------------------------
# Dedicated Omnivault engine rewrite. Every function in this file participates
# in the identity invariant, so replacing this small subsystem file is safer than
# leaving old itemId-based marking/merging paths alive beside the new ones.
# ---------------------------------------------------------------------------
OMNIVAULT.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject

object OmnivaultEngine {
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
      OmnivaultCommand.Operation.SCAN -> scan(state, command)
      OmnivaultCommand.Operation.COPY -> copy(state, command)
      OmnivaultCommand.Operation.RESTORE -> invalid(state, "restore_narrative_only")
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
    if (exactInventory != null && !ItemIdentity.isOmnivaultCopy(exactInventory)) return ScanSource(SourceKind.INVENTORY, c.itemId, exactInventory)
    inventory?.items?.entries?.firstOrNull { (_, item) ->
      !ItemIdentity.isOmnivaultCopy(item) && sameNamedObject(item, c)
    }?.let { return ScanSource(SourceKind.INVENTORY, it.key, it.value) }
    if (exactInventory != null) return ScanSource(SourceKind.INVENTORY, c.itemId, exactInventory)

    val exactStored = state.omnivault.storedItems[c.itemId]
    if (exactStored != null && !ItemIdentity.isOmnivaultCopy(exactStored)) return ScanSource(SourceKind.STORED, c.itemId, exactStored)
    state.omnivault.storedItems.entries.firstOrNull { (_, item) ->
      !ItemIdentity.isOmnivaultCopy(item) && sameNamedObject(item, c)
    }?.let { return ScanSource(SourceKind.STORED, it.key, it.value) }
    if (exactStored != null) return ScanSource(SourceKind.STORED, c.itemId, exactStored)

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
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# Writer contract: a tangible discovered object that remains available to take
# or scan must be represented in flags.worldItems. Discovery never means owned.
# This gives both PICKUP and SCAN the same authoritative world object instance.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
world_directive = (
    ' Nếu reply xác nhận Kai phát hiện một vật thể hữu hình còn nằm trong môi trường và có thể lấy hoặc quét, bắt buộc ghi vật đó vào flags.worldItems với id, name, quantity, instanceId duy nhất, available=true và metadata cần thiết; chỉ phát hiện không được thêm Inventory. Khi vật là sinh vật hoặc cấu kiện lớn, phải ghi isLiving/isLargeAssembly tương ứng để Omnivault từ chối Scan đúng luật.'
)
if "flags.worldItems" not in main:
    needle = 'Loot success chỉ mở cơ hội nhận biết/tương tác, không tự đặt vật phẩm vào Inventory.'
    if needle not in main:
        raise RuntimeError("World item writer authority anchor missing")
    main = main.replace(needle, needle + world_directive, 1)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Update old regression expectations that intentionally encoded the previous
# broken model where copies merged into the original stack.
# ---------------------------------------------------------------------------
core_test = CORE_TEST.read_text(encoding="utf-8")
old_core_copy = '''    val copied = StateReducer.execute(state, OmnivaultCommand("copy", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.COPY, itemId = "original-4", itemName = "Item 4", quantity = 2))
    assertEquals(3, copied.state.inventories.getValue(KAI_ID).items.getValue("original-4").quantity)
    assertEquals("2", copied.state.inventories.getValue(KAI_ID).items.getValue("original-4").metadata["omnivaultCopyCount"])
'''
new_core_copy = '''    val copied = StateReducer.execute(state, OmnivaultCommand("copy", "TURN_1", KAI_ID, source = CommandSource.RULE, operation = OmnivaultCommand.Operation.COPY, itemId = "original-4", itemName = "Item 4", quantity = 2))
    assertEquals(1, copied.state.inventories.getValue(KAI_ID).items.getValue("original-4").quantity)
    val copyStack = copied.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }
    assertEquals(2, copyStack.quantity)
    assertEquals("2", copyStack.metadata["omnivaultCopyCount"])
    assertFalse(copyStack.itemId == "original-4")
'''
core_test = replace_once(core_test, old_core_copy, new_core_copy, "GameStateCore copy identity expectation")
CORE_TEST.write_text(core_test, encoding="utf-8")

if NATURAL_TEST.exists():
    natural = NATURAL_TEST.read_text(encoding="utf-8")
    natural = natural.replace('    assertEquals(9, copy.quantity)\n', '    assertEquals(10, copy.quantity)\n    assertEquals(10, copy.targetTotal)\n')
    old_natural_result = '''    assertEquals(10, result.state.inventories.getValue(KAI_ID).items.getValue("almond-water").quantity)
    assertEquals("9", result.state.inventories.getValue(KAI_ID).items.getValue("almond-water").metadata["omnivaultCopyCount"])
'''
    new_natural_result = '''    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("almond-water").quantity)
    val copyStack = result.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }
    assertEquals(9, copyStack.quantity)
    assertEquals("9", copyStack.metadata["omnivaultCopyCount"])
    assertEquals(10, result.state.inventories.getValue(KAI_ID).items.values.filter { it.name == "Almond Water" }.sumOf { it.quantity })
'''
    natural = replace_once(natural, old_natural_result, new_natural_result, "Natural Scan-Copy identity expectation")
    NATURAL_TEST.write_text(natural, encoding="utf-8")


# ---------------------------------------------------------------------------
# New adversarial regression suite: world Scan, per-instance Marking, copy
# non-scannability, template identity, target totals, save round-trip, and
# directional transfers are all independently locked.
# ---------------------------------------------------------------------------
IDENTITY_TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OmnivaultInstanceAuthorityTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  private fun pickup(state: GameState, id: String, name: String, quantity: Int = 1, seed: String = id): GameState {
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "pickup:$seed",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    return result.state
  }

  private fun scan(state: GameState, id: String, name: String, commandId: String): ExecutionResult =
    OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = commandId,
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.SCAN,
      itemId = id,
      itemName = name,
      timestampEpochMs = commandId.hashCode().toLong()
    ))

  @Test fun twoPhysicalOriginalsOfSameItemCanBeScannedIndependently() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại", 2, "two-scrap")
    val ids = ItemIdentity.instanceIds(state.inventories.getValue(KAI_ID).items.getValue("scrap"))
    assertEquals(2, ids.size)
    val first = scan(state, "scrap", "Mảnh kim loại", "scan:first")
    assertTrue(first.applied)
    val second = scan(first.state, "scrap", "Mảnh kim loại", "scan:second")
    assertTrue(second.applied)
    assertEquals(2, second.state.omnivault.scanSlots.size)
    val sourceIds = second.state.omnivault.scanSlots.map { it.templateItem.metadata.getValue("omnivaultSourceInstanceId") }.toSet()
    assertEquals(ids.toSet(), sourceIds)
  }

  @Test fun samePhysicalOriginalCannotBeScannedTwiceEvenAfterTemplateOverwrite() {
    var state = pickup(fresh(), "a", "A")
    state = scan(state, "a", "A", "scan:a").state
    for (id in listOf("b", "c", "d")) {
      state = pickup(state, id, id.uppercase())
      state = scan(state, id, id.uppercase(), "scan:$id").state
    }
    assertEquals(3, state.omnivault.scanSlots.size)
    assertFalse(state.omnivault.scanSlots.any { it.sourceItemId == "a" })
    val retry = scan(state, "a", "A", "scan:a:retry")
    assertFalse(retry.applied)
    assertEquals("source_already_marked", retry.validation.reason)
  }

  @Test fun copiedObjectIsASeparateStackAndCanNeverBeScanned() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại")
    state = scan(state, "scrap", "Mảnh kim loại", "scan:scrap").state
    val copied = OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = "copy:scrap", turnId = state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "scrap", itemName = "Mảnh kim loại", quantity = 3
    ))
    assertTrue(copied.applied)
    val original = copied.state.inventories.getValue(KAI_ID).items.getValue("scrap")
    val copy = copied.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }
    assertEquals(1, original.quantity)
    assertEquals(3, copy.quantity)
    assertNotEquals(original.itemId, copy.itemId)
    val rejected = scan(copied.state, copy.itemId, copy.name, "scan:copy")
    assertFalse(rejected.applied)
    assertEquals("copy_cannot_be_scanned", rejected.validation.reason)
  }

  @Test fun copyTargetTotalCountsOriginalAndExistingCopiesWithoutOvershoot() {
    var state = pickup(fresh(), "almond-water", "Almond Water")
    val scanned = scan(state, "almond-water", "Almond Water", "scan:almond")
    assertTrue(scanned.applied)
    state = scanned.state
    val slot = state.omnivault.scanSlots.single()
    val first = OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = "copy:to10", turnId = state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "almond-water", itemName = "Almond Water", quantity = 10,
      templateId = ItemIdentity.templateId(slot), targetTotal = 10
    ))
    assertTrue(first.applied)
    assertEquals(10, first.state.inventories.getValue(KAI_ID).items.values.filter { it.name == "Almond Water" }.sumOf { it.quantity })
    val second = OmnivaultEngine.execute(first.state, OmnivaultCommand(
      commandId = "copy:to10:again", turnId = first.state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "almond-water", itemName = "Almond Water", quantity = 10,
      templateId = ItemIdentity.templateId(slot), targetTotal = 10
    ))
    assertTrue(second.applied)
    assertTrue(second.events.contains("omnivault_copy_target_met"))
    assertEquals(10, second.state.inventories.getValue(KAI_ID).items.values.filter { it.name == "Almond Water" }.sumOf { it.quantity })
  }

  @Test fun copyQuantityIsNotBoundByNormal999PerTypeLimit() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại")
    state = scan(state, "scrap", "Mảnh kim loại", "scan:large-copy").state
    val copied = OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = "copy:1500", turnId = state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "scrap", itemName = "Mảnh kim loại", quantity = 1500
    ))
    assertTrue(copied.validation.reason ?: "copy failed", copied.applied)
    assertEquals(1500, copied.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }.quantity)
  }

  @Test fun worldObjectCanBeScannedWithoutBeingPickedUp() {
    val worldItem = JSONObject()
      .put("id", "medical:bandage")
      .put("name", "Băng gạc")
      .put("quantity", 1)
      .put("instanceId", "world:bandage:alpha")
      .put("available", true)
    val flags = JSONObject().put("worldItems", JSONArray().put(worldItem))
    val state = fresh().copy(world = fresh().world + ("flagsJson" to flags.toString()))
    val result = scan(state, "medical:bandage", "Băng gạc", "scan:world-bandage")
    assertTrue(result.validation.reason ?: "world scan failed", result.applied)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(BANDAGE_ID))
    assertTrue("world:bandage:alpha" in result.state.omnivault.markedSourceIds)
    assertEquals("world:bandage:alpha", result.state.omnivault.scanSlots.single().templateItem.metadata["omnivaultSourceInstanceId"])
  }

  @Test fun authoritativeWorldLivingAndLargeFlagsOverrideCommandDefaults() {
    fun stateWith(flag: String): GameState {
      val item = JSONObject().put("id", "target").put("name", "Target").put("instanceId", "world:target").put("available", true).put(flag, true)
      val flags = JSONObject().put("worldItems", JSONArray().put(item))
      val base = fresh()
      return base.copy(world = base.world + ("flagsJson" to flags.toString()))
    }
    val living = scan(stateWith("isLiving"), "target", "Target", "scan:living")
    assertFalse(living.applied)
    assertEquals("living_target_forbidden", living.validation.reason)
    val large = scan(stateWith("isLargeAssembly"), "target", "Target", "scan:large")
    assertFalse(large.applied)
    assertEquals("large_assembly_forbidden", large.validation.reason)
  }

  @Test fun saveRoundTripPreservesTemplateAndPhysicalMarkIdentity() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại", 2, "save")
    state = scan(state, "scrap", "Mảnh kim loại", "scan:save").state
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.omnivault.markedSourceIds, decoded.omnivault.markedSourceIds)
    assertEquals(ItemIdentity.templateId(state.omnivault.scanSlots.single()), ItemIdentity.templateId(decoded.omnivault.scanSlots.single()))
    assertEquals(
      state.omnivault.scanSlots.single().templateItem.metadata["omnivaultSourceInstanceId"],
      decoded.omnivault.scanSlots.single().templateItem.metadata["omnivaultSourceInstanceId"]
    )
  }

  @Test fun resolverUnderstandsNpcToKaiAndKaiToNpcTransfers() {
    val iris = CharacterState("iris", "Iris")
    val state = fresh().copy(
      characters = fresh().characters + ("iris" to iris),
      inventories = fresh().inventories + ("iris" to InventoryState("iris"))
    )
    val context = GameContext(state, actorAliases = linkedMapOf("kai" to KAI_ID, "iris" to "iris"))
    val resolver = CommandResolver()
    val npcCandidate = IntentCandidate("Iris đưa Kai Băng gạc", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, 1f, CommandSource.RULE)
    val npc = resolver.resolve(npcCandidate, 0, state.turn.currentTurnId, context) as ItemCommand
    assertEquals("iris", npc.actorId)
    assertEquals(KAI_ID, npc.targetId)
    val kaiCandidate = IntentCandidate("Kai đưa Băng gạc cho Iris", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, 1f, CommandSource.RULE)
    val kai = resolver.resolve(kaiCandidate, 1, state.turn.currentTurnId, context) as ItemCommand
    assertEquals(KAI_ID, kai.actorId)
    assertEquals("iris", kai.targetId)
  }
}
''', encoding="utf-8")


combined = "\n".join(path.read_text(encoding="utf-8") for path in (
    IDENTITY, GAME_COMMAND, COMMAND, ENGINES, OMNIVAULT, ITEM_CONTENT, POLICY, MAIN, CORE_TEST, IDENTITY_TEST
))
for marker in (
    'object ItemIdentity',
    'physicalInstanceIds',
    'copySourceTemplateId',
    'templateId: String? = null',
    'targetTotal: Int? = null',
    'resolveTransferParties',
    'resolveEquipmentSlot',
    'scan_template_ambiguous',
    'omnivault_copy_target_met',
    'flags.worldItems',
    'worldObjectCanBeScannedWithoutBeingPickedUp',
    'copyQuantityIsNotBoundByNormal999PerTypeLimit',
):
    if marker not in combined:
        raise RuntimeError("Omnivault instance-authority contract missing: " + marker)

print("Omnivault instance authority finalized: physical originals, Mark persistence, direct world Scan, copy/template identity, target totals, directional transfers and copy non-scannability are authoritative.")
