from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

policy = (
    "INVENTORY CAPACITY: Kai có 14 slot vật phẩm thường, mỗi loại tối đa x9999. "
    "Mọi nhân vật khác có 8 slot vật phẩm, mỗi loại tối đa x99. "
    "Equipment là vùng riêng và không chiếm Inventory slot. "
)
marker = "INVENTORY CAPACITY: Kai có 14 slot"

# The final knowledge writer prompt always carries the roaming hard-lock line. Insert this
# capacity rule as its own adjacent Java string literal instead of depending on an obsolete
# earlier KAI LOADOUT sentence that later prompt rewrites may remove.
if marker not in main:
    anchor = "ENTITY ROAMING HARD LOCK:"
    pos = main.find(anchor)
    if pos < 0:
        raise RuntimeError("Final writer prompt anchor missing for inventory capacity")
    line_start = main.rfind("      ", 0, pos)
    if line_start < 0:
        raise RuntimeError("Final writer prompt line start missing for inventory capacity")
    main = main[:line_start] + f'      "{policy}" +\n' + main[line_start:]

if marker not in main:
    raise RuntimeError("Final inventory capacity writer marker missing after insertion")

MAIN.write_text(main, encoding="utf-8")
print("Final GM inventory capacity prompt aligned: Kai 14x9999, all other characters 8x99, Equipment separate.")
