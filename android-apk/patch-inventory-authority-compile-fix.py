from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
COMMAND = ROOT / "app/src/main/java/com/rabpit/backroom/core/CommandPipeline.kt"
HEALING = ROOT / "app/src/main/java/com/rabpit/backroom/core/HealingItems.kt"
ITEM_CONTENT = ROOT / "app/src/main/java/com/rabpit/backroom/core/ItemContent.kt"

text = FACADE.read_text(encoding="utf-8")
old = r'Regex("\s+")'
new = r'Regex("\\s+")'
count = text.count(old)
if count != 1:
    raise RuntimeError(f"Inventory authority compile fix expected exactly 1 invalid regex escape, found {count}")
text = text.replace(old, new, 1)
if old in text:
    raise RuntimeError("Invalid Kotlin regex escape survived inventory authority compile fix")
FACADE.write_text(text, encoding="utf-8")

# MadGod's earlier resolver layer replaces the baseline literal slot with equipmentSlot(it).
# The Omnivault finalizer deliberately owns the final slot resolver, so normalize only those
# two branch anchors immediately before it runs. This preserves patch ordering rather than
# teaching the finalizer to depend on whichever historical patch happened to run first.
command = COMMAND.read_text(encoding="utf-8")
anchor_pairs = (
    (
        '      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, equipmentSlot(it)) }\n',
        '      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, "weapon") }\n',
        "equip",
    ),
    (
        '      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, equipmentSlot(it)) }\n',
        '      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, "weapon") }\n',
        "unequip",
    ),
)
for historical, normalized, label in anchor_pairs:
    if historical in command:
        command = command.replace(historical, normalized, 1)
    elif normalized not in command:
        raise RuntimeError(f"Inventory authority {label} compatibility anchor missing")

# Once both branches stop calling MadGod's historical equipmentSlot helper it is dead code.
# Removing that one generated helper also restores the resolvedQuantity -> itemCommand adjacency
# expected by the final authority patch, without changing the behavior of any surviving caller.
slot_helper_pattern = re.compile(
    r'  private fun equipmentSlot\(item: Pair<String,String>\): String = MadGodCanon\.slot\(item\.first,item\.second\).*?\n\n',
    re.DOTALL,
)
command, removed_helpers = slot_helper_pattern.subn('', command, count=1)
if removed_helpers == 0 and 'private fun equipmentSlot(item: Pair<String,String>)' in command:
    raise RuntimeError("Inventory authority could not normalize historical equipmentSlot helper")

COMMAND.write_text(command, encoding="utf-8")

print("Inventory authority compile fix applied: Kotlin regex corrected and final resolver compatibility normalized.")

# Omnivault instance identity is the last gameplay-state layer. It depends on the final
# inventory authority/world-loot contract above and must execute after its generated Kotlin
# is syntactically corrected, so no older patch can restore itemId-only Mark/Copy behavior.
runpy.run_path(str(ROOT / "patch-omnivault-instance-authority-finalize.py"), run_name="__main__")

# The Omnivault layer initially preserved healing-copy IDs by replacing the long-standing
# ItemContent healing hook. Existing CI intentionally asserts that hook verbatim. Preserve
# the same behavior without weakening the contract: move the copy-ID exception into
# HealingItems.normalize(), then restore ItemContent's canonical hook exactly.
item_content = ITEM_CONTENT.read_text(encoding="utf-8")
omnivault_healing_hook = '''    HealingItems.normalize(item)?.let { healing ->
      if (!ItemIdentity.isOmnivaultCopy(item)) return healing
      return healing.copy(itemId = item.itemId, metadata = healing.metadata + item.metadata)
    }
'''
canonical_healing_hook = '    HealingItems.normalize(item)?.let { return it }\n'
if omnivault_healing_hook in item_content:
    item_content = item_content.replace(omnivault_healing_hook, canonical_healing_hook, 1)
elif canonical_healing_hook not in item_content:
    raise RuntimeError("Healing normalization compatibility hook missing after Omnivault finalizer")
ITEM_CONTENT.write_text(item_content, encoding="utf-8")

healing = HEALING.read_text(encoding="utf-8")
old_healing_id = '      itemId = id,\n'
copy_safe_healing_id = '      itemId = if (ItemIdentity.isOmnivaultCopy(item)) item.itemId else id,\n'
if old_healing_id in healing:
    if healing.count(old_healing_id) != 1:
        raise RuntimeError(f"Healing copy identity anchor expected exactly 1 match, found {healing.count(old_healing_id)}")
    healing = healing.replace(old_healing_id, copy_safe_healing_id, 1)
elif copy_safe_healing_id not in healing:
    raise RuntimeError("Healing copy identity compatibility anchor missing")
HEALING.write_text(healing, encoding="utf-8")

if canonical_healing_hook not in ITEM_CONTENT.read_text(encoding="utf-8"):
    raise RuntimeError("Canonical HealingItems normalization hook was not restored")
if copy_safe_healing_id not in HEALING.read_text(encoding="utf-8"):
    raise RuntimeError("Omnivault healing-copy identity protection was not preserved")

print("Healing contract compatibility restored without losing Omnivault copy identity.")
