from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
POLICY = CORE / "InventoryPolicy.kt"
CATALOG = ROOT / "app/src/main/assets/items/item_catalog.json"
ITEM_CATALOG_SOURCE = CORE / "ItemCatalog.kt"

# Inventory V2 is the only runtime authority. This file is intentionally verification-only.
# It must fail closed if an older capacity patch, character patch, or catalog regression changes
# the final contract after Inventory V2 has run.
policy = POLICY.read_text(encoding="utf-8")
required_policy = [
    "val KAI = InventoryProfile(maxTypes = 14, maxPerType = 9999)",
    "val NORMAL = InventoryProfile(maxTypes = 8, maxPerType = 99)",
    "if (characterId == KAI_ID) KAI else NORMAL",
    "if (ownerId == AN_NHIEN_ID && !AnNhienCanon.isFoodItem(normalized)) return \"an_nhien_food_only\"",
    "val equippedIds = state.equipment[ownerId]?.slots?.values.orEmpty().toSet()",
    "val usedTypes = inventory.items.keys.count { it !in equippedIds }",
]
for marker in required_policy:
    if marker not in policy:
        raise RuntimeError(f"Inventory V2 capacity policy marker missing: {marker}")

for legacy in [
    "maxTypes = 9, maxPerType = 999",
    "maxTypes = 4, maxPerType = 20",
    "maxTypes = 2, maxPerType = 2",
    "maxTypes = 8, maxPerType = 100",
]:
    if legacy in policy:
        raise RuntimeError(f"Legacy inventory capacity survived Inventory V2: {legacy}")

catalog_source = ITEM_CATALOG_SOURCE.read_text(encoding="utf-8")
for marker in [
    "val maxStack: Int = 9999",
    'maxStack = json.optInt("maxStack", if (json.optString("stackMode", "STACK").equals("INSTANCE", true)) 1 else 9999)',
]:
    if marker not in catalog_source:
        raise RuntimeError(f"Inventory V2 catalog default marker missing: {marker}")

raw = json.loads(CATALOG.read_text(encoding="utf-8"))
items = raw.get("items", [])
if not items:
    raise RuntimeError("Inventory V2 item catalog is empty")
for item in items:
    mode = str(item.get("stackMode", "STACK")).upper()
    max_stack = int(item.get("maxStack", 1 if mode == "INSTANCE" else 9999))
    if mode == "INSTANCE":
        if max_stack != 1:
            raise RuntimeError(f"INSTANCE item must remain maxStack=1: {item.get('id')}")
    elif max_stack != 9999:
        raise RuntimeError(
            f"Stackable item {item.get('id')} reintroduced a catalog cap {max_stack}; "
            "character InventoryPolicy must own the 9999/99 capacity ceiling"
        )

print("Inventory V2 capacity guard passed: Kai 14x9999, non-Kai 8x99, equipment separate, An Nhiên FOOD-only, catalog stack ceiling compatible.")
