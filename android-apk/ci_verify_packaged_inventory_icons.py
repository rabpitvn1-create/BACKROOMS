from pathlib import Path
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "inventory_icon_manifest.json"
SOURCE_DIR = ROOT / "app/src/main/assets/inventory-icons"

if len(sys.argv) != 2:
    raise SystemExit("usage: ci_verify_packaged_inventory_icons.py <apk>")

apk = Path(sys.argv[1])
if not apk.is_file():
    raise SystemExit(f"APK missing: {apk}")

payload = json.loads(MANIFEST.read_text(encoding="utf-8"))
ids = [str(item["id"]).strip() for item in payload["items"]] + ["generic"]

with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    index = archive.read("assets/index.html").decode("utf-8")
    for marker in (
        "INVENTORY_ICON_HARD_LOCK_R01",
        "function inventoryIconMarkup(item)",
        "inventory-icons/'+encodeURIComponent(key)+'.webp",
    ):
        assert marker in index, marker

    for item_id in ids:
        member = f"assets/inventory-icons/{item_id}.webp"
        assert member in names, member
        packaged = archive.read(member)
        source = (SOURCE_DIR / f"{item_id}.webp").read_bytes()
        assert packaged == source, f"packaged icon differs from generated source: {item_id}"
        assert 0 < len(packaged) <= 65536
        assert packaged[:4] == b"RIFF" and packaged[8:12] == b"WEBP"

print(f"Packaged inventory icons verified: {len(ids) - 1} official + generic fallback.")
