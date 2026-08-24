from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
COMMAND = ROOT / "app/src/main/java/com/rabpit/backroom/core/CommandPipeline.kt"

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
COMMAND.write_text(command, encoding="utf-8")

print("Inventory authority compile fix applied: Kotlin regex corrected and final equipment resolver anchors normalized.")

# Omnivault instance identity is the last gameplay-state layer. It depends on the final
# inventory authority/world-loot contract above and must execute after its generated Kotlin
# is syntactically corrected, so no older patch can restore itemId-only Mark/Copy behavior.
runpy.run_path(str(ROOT / "patch-omnivault-instance-authority-finalize.py"), run_name="__main__")
