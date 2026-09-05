from pathlib import Path

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# Do not rewrite regression tests. Inventory V2 owns them; this file only checks that the final
# runtime/presentation remains consistent with those tests and with the retired-command policy.
capacity = (TESTS / "InventoryV2CapacityContractTest.kt").read_text(encoding="utf-8")
for marker in ["kaiAllows9999UnitsButRejects10000", "normalCharacterAllows99UnitsButRejects100"]:
    if marker not in capacity:
        raise RuntimeError(f"Inventory V2 capacity regression missing: {marker}")

an = (TESTS / "AnNhienFollowerTest.kt").read_text(encoding="utf-8")
for marker in [
    "inventoryAcceptsOnlyFoodAndUsesNormalV2Capacity",
    'assertEquals("an_nhien_food_only"',
    "assertEquals(8, profile.maxTypes)",
    "assertEquals(99, profile.maxPerType)",
]:
    if marker not in an:
        raise RuntimeError(f"An Nhiên V2 regression missing: {marker}")

html = INDEX.read_text(encoding="utf-8")
for marker in ["slots:14,maxPerType:9999", "slots:8,maxPerType:99", "Equipment không chiếm Kho đồ"]:
    if marker not in html:
        raise RuntimeError(f"Inventory V2 UI regression missing: {marker}")

main = MAIN.read_text(encoding="utf-8")
for forbidden in [
    "Inventory chỉ tăng từ story/drop/SYSTEM đã được xác thực hoặc từ Copy/transfer hợp lệ",
    "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật",
    "inventory_upsert{item,basis}",
    "inventory_remove{name,basis}",
]:
    if forbidden in main:
        raise RuntimeError(f"Retired inventory prompt path survived: {forbidden}")
if "INVENTORY V2 CONTRACT: Inventory chỉ tăng từ Explore Loot hoặc Entity Drop" not in main:
    raise RuntimeError("Inventory V2 writer contract missing")

print("Inventory V2 presentation and writer regression guard passed without mutating tests.")
