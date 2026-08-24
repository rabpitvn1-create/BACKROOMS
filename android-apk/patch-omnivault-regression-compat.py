from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
OMNIVAULT = CORE / "OmnivaultEngine.kt"
COMMAND = CORE / "CommandPipeline.kt"
NATURAL_TEST = TESTS / "OmnivaultNaturalFlowTest.kt"
IDENTITY_TEST = TESTS / "OmnivaultInstanceAuthorityTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Exact item IDs must win over fuzzy same-name matching. Otherwise scanning an
# Omnivault copy can silently resolve back to the original object and report the
# wrong validation reason (or worse, operate on the wrong physical source).
omnivault = OMNIVAULT.read_text(encoding="utf-8")
old_inventory_source = '''    val inventory = state.inventories[c.actorId]
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
'''
new_inventory_source = '''    val inventory = state.inventories[c.actorId]
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
'''
omnivault = replace_once(omnivault, old_inventory_source, new_inventory_source, "Exact Omnivault scan source precedence")

# The instance-authority rewrite must not erase the pre-existing MadGod canon:
# MadGod items are neither scannable nor copyable, even while unequipped.
scan_anchor = '    val source = resolveScanSource(state, c) ?: return invalid(state, "scan_source_missing")\n'
scan_hardened = scan_anchor + '    if (MadGodCanon.isItem(source.item)) return invalid(state, "madgod_omnivault_copy_forbidden")\n'
if 'MadGodCanon.isItem(source.item)' not in omnivault:
    omnivault = replace_once(omnivault, scan_anchor, scan_hardened, "MadGod Omnivault scan lock")

copy_anchor = '    val template = templateSlot.templateItem\n'
copy_hardened = copy_anchor + '    if (MadGodCanon.isItem(template)) return invalid(state, "madgod_omnivault_copy_forbidden")\n'
if 'MadGodCanon.isItem(template)' not in omnivault:
    omnivault = replace_once(omnivault, copy_anchor, copy_hardened, "MadGod Omnivault copy lock")
OMNIVAULT.write_text(omnivault, encoding="utf-8")

# Preserve MadGod's authoritative slot mapping in the final resolver. The old
# helper was intentionally removed before the finalizer, but its canon mapping
# still belongs ahead of generic keyword inference.
command = COMMAND.read_text(encoding="utf-8")
slot_anchor = '''    val owned = context.state.inventories[actor]?.items?.get(item.first)
    owned?.metadata?.get("equipmentSlot")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    KaiStartingEquipment.slotFor(item.first, item.second)?.let { return it }
'''
slot_hardened = '''    val owned = context.state.inventories[actor]?.items?.get(item.first)
    owned?.metadata?.get("equipmentSlot")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    MadGodCanon.slot(item.first, item.second)?.let { return it }
    owned?.metadata?.get("slot")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    KaiStartingEquipment.slotFor(item.first, item.second)?.let { return it }
'''
command = replace_once(command, slot_anchor, slot_hardened, "Final MadGod equipment slot mapping")
COMMAND.write_text(command, encoding="utf-8")

# The new target-total contract carries the requested total to the engine. The
# previous-turn natural-flow test still encoded the retired pre-subtraction value.
if NATURAL_TEST.exists():
    natural = NATURAL_TEST.read_text(encoding="utf-8")
    old_previous_turn = '''    assertEquals("almond-water", command.itemId)
    assertEquals(9, command.quantity)
'''
    new_previous_turn = '''    assertEquals("almond-water", command.itemId)
    assertEquals(10, command.quantity)
    assertEquals(10, command.targetTotal)
'''
    natural = replace_once(natural, old_previous_turn, new_previous_turn, "Previous-turn target-total expectation")
    NATURAL_TEST.write_text(natural, encoding="utf-8")

# Lock the MadGod prohibition so a later whole-engine rewrite cannot silently
# reintroduce Scan/Copy for those canonical items.
identity = IDENTITY_TEST.read_text(encoding="utf-8")
if 'madGodItemsRemainForbiddenToOmnivault' not in identity:
    test = r'''
  @Test fun madGodItemsRemainForbiddenToOmnivault() {
    val base = fresh()
    val inventory = base.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val madGodSet = MadGodCanon.setItem()
    val state = base.copy(inventories = base.inventories + (KAI_ID to inventory.copy(items = inventory.items + (madGodSet.itemId to madGodSet))))

    val scanned = scan(state, madGodSet.itemId, madGodSet.name, "scan:madgod")
    assertFalse(scanned.applied)
    assertEquals("madgod_omnivault_copy_forbidden", scanned.validation.reason)

    val template = madGodSet.copy(metadata = madGodSet.metadata + mapOf(
      "omnivaultTemplateId" to "template:madgod",
      "omnivaultSourceInstanceId" to "instance:madgod:1",
      "omnivaultTemplate" to "true"
    ))
    val templated = state.copy(omnivault = state.omnivault.copy(scanSlots = listOf(ScanSlot(1, madGodSet.itemId, template, 1L))))
    val copied = OmnivaultEngine.execute(templated, OmnivaultCommand(
      commandId = "copy:madgod", turnId = templated.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = madGodSet.itemId, itemName = madGodSet.name, quantity = 1
    ))
    assertFalse(copied.applied)
    assertEquals("madgod_omnivault_copy_forbidden", copied.validation.reason)
  }
'''
    closing = identity.rfind('\n}')
    if closing < 0:
        raise RuntimeError("Omnivault identity test closing brace missing")
    identity = identity[:closing] + test + identity[closing:]
    IDENTITY_TEST.write_text(identity, encoding="utf-8")

print("Omnivault regression compatibility applied: exact copy Scan rejection, target totals, and MadGod canon preserved.")
