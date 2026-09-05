from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

# Remove legacy prompt lines that contradict Inventory V2 by allowing story/SYSTEM/Copy to create
# ownership. Core now has exactly two quantity sources: Explore Loot and Entity Drop.
retired_fragments = [
    "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật",
    "Inventory chỉ tăng từ story/drop/SYSTEM đã được xác thực hoặc từ Copy/transfer hợp lệ",
]
lines = []
for line in main.splitlines():
    if any(fragment in line for fragment in retired_fragments):
        continue
    # The old operation schema let Gemini reconcile inventory directly. It is retired in V2.
    if "inventory_upsert{item,basis}" in line or "inventory_remove{name,basis}" in line:
        continue
    lines.append(line)
main = "\n".join(lines) + "\n"

policy = (
    "INVENTORY V2 CONTRACT: Inventory chỉ tăng từ Explore Loot hoặc Entity Drop do Core commit. "
    "Transfer, Request và Give-and-use chỉ di chuyển ownership hiện có; Use và Discard chỉ có thể giảm quantity. "
    "AI, UI, LiteRT, narrative và Omnivault không được tạo, copy, duplicate hay reconcile vật phẩm. "
    "Kai có 14 slot vật phẩm thường tối đa x9999 mỗi loại; mọi nhân vật khác có 8 slot tối đa x99; Equipment tách riêng. "
)
marker = "INVENTORY V2 CONTRACT: Inventory chỉ tăng từ Explore Loot hoặc Entity Drop"
if marker not in main:
    # ENTITY ROAMING HARD LOCK is emitted by the final knowledge/canon chain and survives the later
    # combat transforms. Use it rather than an older GAMEPLAY_ROLLS spelling that changes by patch.
    anchor = "ENTITY ROAMING HARD LOCK:"
    pos = main.find(anchor)
    if pos < 0:
        raise RuntimeError("Inventory V2 writer anchor missing")
    line_start = main.rfind("\n", 0, pos) + 1
    main = main[:line_start] + '            "' + policy + '" +\n' + main[line_start:]

for fragment in retired_fragments + ["inventory_upsert{item,basis}", "inventory_remove{name,basis}"]:
    if fragment in main:
        raise RuntimeError(f"Retired inventory writer authority survived V2: {fragment}")

for marker_required in [
    marker,
    "Explore Loot hoặc Entity Drop",
    "Kai có 14 slot",
    "8 slot tối đa x99",
    "Equipment tách riêng",
]:
    if marker_required not in main:
        raise RuntimeError(f"Inventory V2 writer marker missing: {marker_required}")

MAIN.write_text(main, encoding="utf-8")
print("Inventory V2 writer prompt aligned: no narrative/Copy ownership creation, capacity guidance matches Core.")
