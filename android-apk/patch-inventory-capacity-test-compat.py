from pathlib import Path

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

# Inventory V2 already migrated these tests. This guard deliberately does not rewrite tests,
# because mutating regression coverage late in the patch chain can hide real failures.
capacity = (TESTS / "InventoryV2CapacityContractTest.kt").read_text(encoding="utf-8")
for marker in [
    "kaiAllows9999UnitsButRejects10000",
    "normalCharacterAllows99UnitsButRejects100",
    "InventoryProfile(14, 9999)",
    "InventoryProfile(8, 99)",
]:
    # The profile assertions may live in the generated V2 contract rather than this focused file.
    if marker.startswith("InventoryProfile"):
        generated = (TESTS / "InventoryV2GeneratedTest.kt").read_text(encoding="utf-8")
        if marker not in generated:
            raise RuntimeError(f"Inventory V2 generated capacity assertion missing: {marker}")
    elif marker not in capacity:
        raise RuntimeError(f"Inventory V2 capacity regression missing: {marker}")

an = (TESTS / "AnNhienFollowerTest.kt").read_text(encoding="utf-8")
for marker in [
    "inventoryAcceptsOnlyFoodAndUsesNormalV2Capacity",
    'assertEquals("an_nhien_food_only"',
    "assertEquals(8, profile.maxTypes)",
    "assertEquals(99, profile.maxPerType)",
]:
    if marker not in an:
        raise RuntimeError(f"An Nhiên Inventory V2 regression missing: {marker}")

canon = TESTS / "CharacterCanonR07Test.kt"
if canon.is_file():
    text = canon.read_text(encoding="utf-8")
    if "assertEquals(100, profile.maxPerType)" in text:
        raise RuntimeError("Generated Lucia test reintroduced x100 capacity")
    if "assertEquals(99, profile.maxPerType)" not in text:
        raise RuntimeError("Generated Lucia test does not enforce x99 capacity")

for path in [
    TESTS / "InventoryV2CapacityContractTest.kt",
    TESTS / "InventoryV2GeneratedTest.kt",
    TESTS / "AnNhienFollowerTest.kt",
]:
    text = path.read_text(encoding="utf-8")
    for retired in ["Operation.PICKUP", "Operation.DROP", "Operation.SCAN", "Operation.COPY", "scanSlots", "markedSourceIds"]:
        if retired in text:
            raise RuntimeError(f"Retired inventory API survived in {path.name}: {retired}")

print("Inventory V2 capacity regression guard passed without rewriting or deleting tests.")
