from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
CATALOG = CORE / "ItemCatalog.kt"
IDENTITY_TEST = TESTS / "ItemIdentityAuthorityTest.kt"
HEALING_TEST = TESTS / "HealingItemTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


catalog = CATALOG.read_text(encoding="utf-8")
if 'const val CHICKEN_RICE_BOX = "chicken-rice-box"' not in catalog:
    raise RuntimeError("Chicken rice box catalog entry missing")

identity = IDENTITY_TEST.read_text(encoding="utf-8")
identity = replace_once(
    identity,
    '    "Nước suối La Vie" to ItemCatalog.LA_VIE\n  )',
    '    "Nước suối La Vie" to ItemCatalog.LA_VIE,\n    "Hộp cơm gà" to ItemCatalog.CHICKEN_RICE_BOX\n  )',
    "Item identity chicken rice entry",
)
IDENTITY_TEST.write_text(identity, encoding="utf-8")

healing = HEALING_TEST.read_text(encoding="utf-8")
healing = replace_once(
    healing,
    "    assertEquals(11, ItemCatalog.items.size)",
    "    assertEquals(12, ItemCatalog.items.size)",
    "Healing shared item pool count",
)
HEALING_TEST.write_text(healing, encoding="utf-8")

if '"Hộp cơm gà" to ItemCatalog.CHICKEN_RICE_BOX' not in identity:
    raise RuntimeError("Chicken rice box identity regression coverage missing")
if "assertEquals(12, ItemCatalog.items.size)" not in healing:
    raise RuntimeError("Healing item pool regression count missing")

print("Aligned generated official-item regressions with the 12-item catalog.")
