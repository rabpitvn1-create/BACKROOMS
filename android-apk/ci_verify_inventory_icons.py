from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "inventory_icon_manifest.json"
RULES = ROOT / "INVENTORY_ICON_RULES.md"
GENERATOR = ROOT / "generate_inventory_icons.py"
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/ItemCatalog.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
ICON_DIR = ROOT / "app/src/main/assets/inventory-icons"

payload = json.loads(MANIFEST.read_text(encoding="utf-8"))
assert payload.get("schema") == 1
assert payload.get("size") == 128
assert payload.get("format") == "webp"
assert payload.get("background") == "transparent"
assert payload.get("text") is False

items = payload.get("items")
assert isinstance(items, list) and items
ids = [str(item.get("id", "")).strip() for item in items]
assert all(ids) and len(ids) == len(set(ids))
assert all(str(item.get("recipe", "")).strip() for item in items)

catalog = CATALOG.read_text(encoding="utf-8")
constants = dict(re.findall(r'const val\s+([A-Z0-9_]+)\s*=\s*"([^"]+)"', catalog))
official_constants = re.findall(r'OfficialItem\(\s*([A-Z0-9_]+)\s*,', catalog)
missing_constants = sorted(set(official_constants) - set(constants))
assert not missing_constants, missing_constants
official_ids = {constants[name] for name in official_constants}
assert set(ids) == official_ids, f"manifest/catalog mismatch: manifest={sorted(ids)} catalog={sorted(official_ids)}"

rules = RULES.read_text(encoding="utf-8")
for marker in (
    "INVENTORY_ICON_HARD_LOCK_R01",
    "Không chữ.",
    "Không Base64",
    "128×128 px",
    "WebP nhị phân",
):
    assert marker in rules, marker

generator = GENERATOR.read_text(encoding="utf-8")
for forbidden in ("import base64", "ImageFont", "draw.text(", ".text(", "<text", "data:image"):
    assert forbidden not in generator, forbidden

html = INDEX.read_text(encoding="utf-8")
for marker in (
    "INVENTORY_ICON_HARD_LOCK_R01",
    'id="inventoryIconStyle"',
    "const INVENTORY_ICON_IDS=new Set(",
    "function inventoryIconMarkup(item)",
    "inventory-icons/'+encodeURIComponent(key)+'.webp",
    "inventoryIconMarkup(item)",
):
    assert marker in html, marker

for item_id in ids + ["generic"]:
    path = ICON_DIR / f"{item_id}.webp"
    assert path.is_file(), path
    data = path.read_bytes()
    assert 0 < len(data) <= 65536, (path, len(data))
    assert data[:4] == b"RIFF" and data[8:12] == b"WEBP", path

expected = {f"{item_id}.webp" for item_id in ids + ["generic"]}
actual = {path.name for path in ICON_DIR.glob("*.webp")}
assert actual == expected, f"unexpected inventory icon files: expected={sorted(expected)} actual={sorted(actual)}"

print(f"Inventory icon contracts verified: {len(ids)} official + generic fallback.")
