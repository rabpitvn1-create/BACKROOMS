from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old_equipment = '''  function equipmentRows(member){
    const eq=member&&member.equipment||{};
    const keys=Object.keys(eq);
    if(!keys.length&&member&&member.id==='kai')return Object.keys(signatureEquipment).map(k=>signatureEquipment[k]);
    return keys.map(k=>String(k)+': '+String(eq[k]));
  }
'''
new_equipment = '''  function displayItemName(raw){
    const source=raw&&typeof raw==='object'?(raw.name||raw.displayName||raw.label||raw.itemId||raw.id||raw.archetypeId||''):raw;
    let text=String(source==null?'':source).trim();
    if(text.includes(':'))text=text.split(':').pop();
    text=text.replace(/[_-]+/g,' ').replace(/\\s+/g,' ').trim();
    return (text||'—').toLocaleUpperCase('vi-VN');
  }
  function displaySlotName(raw){
    return String(raw==null?'':raw).replace(/[_-]+/g,' ').replace(/\\s+/g,' ').trim().toLocaleUpperCase('vi-VN')||'ITEM';
  }
  function renderInventoryItems(inv){
    if(!Array.isArray(inv)||!inv.length)return '<span>Trống.</span>';
    return inv.map(x=>{
      const qty=x&&typeof x==='object'&&Number(x.quantity)>1?' ×'+Number(x.quantity):'';
      return '<span>'+esc(displayItemName(x)+qty)+'</span>';
    }).join('');
  }
  function equipmentRows(member){
    const eq=member&&member.equipment||{};
    const keys=Object.keys(eq);
    if(!keys.length&&member&&member.id==='kai')return Object.keys(signatureEquipment).map(k=>displaySlotName(k)+' : '+displayItemName(signatureEquipment[k]));
    return keys.map(k=>displaySlotName(k)+' : '+displayItemName(eq[k]));
  }
'''

if new_equipment not in html:
    if old_equipment not in html:
        raise RuntimeError("Equipment renderer anchor not found")
    html = html.replace(old_equipment, new_equipment, 1)

old_inventory = '    items.innerHTML=chips(inv);\n'
new_inventory = '    items.innerHTML=renderInventoryItems(inv);\n'
if new_inventory not in html:
    if old_inventory not in html:
        raise RuntimeError("Character inventory renderer anchor not found")
    html = html.replace(old_inventory, new_inventory, 1)

required = [
    "displaySlotName(k)+' : '+displayItemName(eq[k])",
    "items.innerHTML=renderInventoryItems(inv);",
    "text.split(':').pop()",
    "replace(/[_-]+/g,' ')",
    "toLocaleUpperCase('vi-VN')",
]
for token in required:
    if token not in html:
        raise RuntimeError(f"Friendly item display contract missing: {token}")

INDEX.write_text(html, encoding="utf-8")
print("Friendly item display applied: namespaces removed, separators normalized and equipment/inventory labels uppercased.")
