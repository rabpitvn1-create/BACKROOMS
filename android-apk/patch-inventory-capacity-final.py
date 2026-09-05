from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

# Inventory V2 owns capacity and ownership semantics. This patch only makes the already-authoritative
# 14x9999 / 8x99 contract visible in Character Detail; it never mutates Core inventory state.
old_capacity = "capacity.textContent=inv.length+' / '+(member&&member.id==='kai'?14:8)+' ô vật phẩm';"
profile_line = "const inventoryProfile=member&&member.id==='kai'?{slots:14,maxPerType:9999}:{slots:8,maxPerType:99};"
new_capacity = "\n".join([
    profile_line,
    "capacity.textContent=inv.length+' / '+inventoryProfile.slots+' ô vật phẩm';",
    "const inventoryLimit=document.getElementById('characterInventoryLimit');",
    "if(inventoryLimit)inventoryLimit.textContent='Tối đa '+inventoryProfile.maxPerType+' đơn vị mỗi loại · Equipment không chiếm Kho đồ';",
])
if profile_line not in html:
    if html.count(old_capacity) != 1:
        raise RuntimeError(f"Inventory V2 capacity renderer anchor count: {html.count(old_capacity)}")
    html = html.replace(old_capacity, new_capacity, 1)

old_limit = '<div class="inventory-limit">Inventory của nhân vật đang chọn</div>'
new_limit = '<div class="inventory-limit" id="characterInventoryLimit">Tối đa 9999 đơn vị mỗi loại · Equipment không chiếm Kho đồ</div>'
if 'id="characterInventoryLimit"' not in html:
    if html.count(old_limit) != 1:
        raise RuntimeError(f"Inventory V2 limit-label anchor count: {html.count(old_limit)}")
    html = html.replace(old_limit, new_limit, 1)

for marker in [
    "0 / 14 ô vật phẩm",
    "slots:14,maxPerType:9999",
    "slots:8,maxPerType:99",
    "Equipment không chiếm Kho đồ",
]:
    if marker not in html:
        raise RuntimeError(f"Inventory V2 presentation marker missing: {marker}")

for legacy in ["0 / 9 loại vật phẩm", " / 9 loại vật phẩm", "2 slot thực phẩm", "8x100"]:
    if legacy in html:
        raise RuntimeError(f"Legacy inventory presentation survived V2: {legacy}")

INDEX.write_text(html, encoding="utf-8")
print("Inventory V2 Character Detail presentation aligned: Kai 14x9999, non-Kai 8x99, Equipment separate.")
