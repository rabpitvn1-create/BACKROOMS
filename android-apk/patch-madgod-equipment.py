from pathlib import Path

base = Path(__file__).resolve().parent / "patch-madgod-base.py"
src = base.read_text(encoding="utf-8")

# The final three-action runtime rewrites GameCoreFacade immediately before MadGod is applied.
# Replace brittle exact-block patches in the historical base script with scoped method insertions.
lines = src.splitlines()
for i, line in enumerate(lines):
    if "'cheat rule')" in line and line.lstrip().startswith("f=one("):
        lines[i] = r'''process_marker='  fun processRule(legacyStateJson: String, action: String): String {\n'
process_pos=f.find(process_marker)
if process_pos < 0: raise RuntimeError('MadGod processRule method missing')
state_anchor='    val state = loadOrMigrate(legacy)\n'
state_pos=f.find(state_anchor,process_pos)
if state_pos < 0: raise RuntimeError('MadGod processRule state anchor missing')
insert_at=state_pos+len(state_anchor)
process_end=f.find('\n  fun ',insert_at)
if process_end < 0: process_end=len(f)
if 'MadGodCanon.cheat(action)' not in f[process_pos:process_end]:
    f=f[:insert_at]+'    if (MadGodCanon.cheat(action)) return applyMadGodCheat(legacy,state)\n'+f[insert_at:]'''
    if "'cheat begin')" in line and line.lstrip().startswith("f=one("):
        lines[i] = r'''begin_marker='  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n'
begin_pos=f.find(begin_marker)
if begin_pos < 0: raise RuntimeError('MadGod beginAction method missing')
state_anchor='    val state = loadOrMigrate(legacy)\n'
begin_state=f.find(state_anchor,begin_pos)
if begin_state < 0: raise RuntimeError('MadGod beginAction state anchor missing')
insert_at=begin_state+len(state_anchor)
begin_end=f.find('\n  fun ',insert_at)
if begin_end < 0: begin_end=len(f)
if 'MadGodCanon.cheat(action)' not in f[begin_pos:begin_end]:
    f=f[:insert_at]+'    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)\n'+f[insert_at:]'''
src = "\n".join(lines) + "\n"

# The generated InventoryEngine is stable at the individual mutation lines, not as one giant block.
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
    raise RuntimeError("MadGod base wrapper engine anchor missing")
src = src.replace(needle, replacement, 1)

exec(compile(src, str(base), "exec"), {"__name__": "__main__", "__file__": str(base)})
