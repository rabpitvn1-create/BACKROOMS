from pathlib import Path

base = Path(__file__).resolve().parent / "patch-madgod-base.py"
src = base.read_text(encoding="utf-8")
needle = "e=one(e,old,new,'equip engine');E.write_text(e,encoding='utf-8')"
replacement = r'''equip_line='        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")\n'
equip_new=''' + "'''" + r'''        val current=equipment.slots[slot]
        if (MadGodCanon.isId(current) && current!=command.itemId) return invalid(state,"madgod_equipment_permanent")
        if (MadGodCanon.isId(command.itemId) && (command.actorId!=KAI_ID || MadGodCanon.slot(command.itemId,source.items[command.itemId]?.name.orEmpty())!=slot)) return invalid(state,"madgod_equipment_slot_mismatch")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
''' + "'''" + r'''
e=one(e,equip_line,equip_new,'equip engine line')
unequip_line='        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")\n'
unequip_new=''' + "'''" + r'''        if (MadGodCanon.isId(command.itemId)) return invalid(state,"madgod_equipment_permanent")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
''' + "'''" + r'''
e=one(e,unequip_line,unequip_new,'unequip engine line')
E.write_text(e,encoding='utf-8')'''
if needle not in src:
    raise RuntimeError("MadGod base wrapper anchor missing")
src = src.replace(needle, replacement, 1)
exec(compile(src, str(base), "exec"), {"__name__": "__main__", "__file__": str(base)})
