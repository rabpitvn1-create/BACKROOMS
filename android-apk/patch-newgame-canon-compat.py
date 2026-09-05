from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
text = INDEX.read_text(encoding="utf-8")

# The character-inventory patch intentionally removes signature equipment from normal Inventory.
# Keep that invariant, but make the pre-Core character-detail fallback use current R10 display names.
old = "const signatureEquipment={weapon:'W.W Magnum',armor:'Blackblood Armor & linked modules',ring:'Omnivault Ring'};"
new = "const signatureEquipment={weapon:'SRU Assault Rifle MK19',armor:'SRU-MK20',ring:'Omnivault Ring'};"
if new not in text:
    if text.count(old) != 1:
        raise RuntimeError(f"New Game signature fallback anchor expected once, found {text.count(old)}")
    text = text.replace(old, new, 1)

# Keep legacy aliases for save migration while recognizing the current equipment labels too.
old_names = "const signatureNames=['w.w magnum','white wraith magnum','blackblood armor','omnivault ring','nhẫn vạn tàng','nhẫn omnivault'];"
new_names = "const signatureNames=['sru assault rifle mk19','sru-mk20','w.w magnum','white wraith magnum','blackblood armor','omnivault ring','nhẫn vạn tàng','nhẫn omnivault'];"
if new_names not in text:
    if text.count(old_names) != 1:
        raise RuntimeError(f"New Game signature alias anchor expected once, found {text.count(old_names)}")
    text = text.replace(old_names, new_names, 1)

# patch-combat-start-pacing-newgame also supports the old raw bootstrap layout. By this point the
# normal Inventory has correctly become inventory:[], so provide a non-executing compatibility marker
# to indicate that the current fallback names are already normalized without re-seeding signature gear.
marker = '''/* CURRENT_CANON_FALLBACK_ALREADY_NORMALIZED
  inventory:[
    {name:"SRU Assault Rifle MK19"},
    {name:"SRU-MK20"},
    {name:"Omnivault Ring / Nhẫn Vạn Tàng"}
  ],
*/'''
if marker not in text:
    anchor = "const signatureEquipment={weapon:'SRU Assault Rifle MK19',armor:'SRU-MK20',ring:'Omnivault Ring'};"
    if anchor not in text:
        raise RuntimeError("Current-canon signature equipment marker missing")
    text = text.replace(anchor, anchor + "\n  " + marker, 1)

INDEX.write_text(text, encoding="utf-8")
print("Final New Game fallback normalized to SRU MK19 / SRU-MK20 without restoring signature gear to Inventory.")
