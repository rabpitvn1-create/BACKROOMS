from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent
SNAPSHOT_DIR = ROOT / "app/src/main/assets/level_snapshots"
BASE_MANIFEST = SNAPSHOT_DIR / "fandom_manifest.json"
PIXEL_MANIFEST = SNAPSHOT_DIR / "pixel16_manifest.json"

if not BASE_MANIFEST.is_file():
    raise RuntimeError("Base snapshot manifest missing")
if not PIXEL_MANIFEST.is_file():
    raise RuntimeError("Pixel16 background manifest missing")

base = json.loads(BASE_MANIFEST.read_text(encoding="utf-8"))
overlay = json.loads(PIXEL_MANIFEST.read_text(encoding="utf-8"))

if int(overlay.get("width") or 0) != 512 or int(overlay.get("height") or 0) != 288:
    raise RuntimeError("Pixel16 background contract must stay exactly 512x288")
if str(overlay.get("style", "")).strip().lower() != "16-bit pixel art":
    raise RuntimeError("Pixel16 background style contract missing")

areas = base.get("areas")
if not isinstance(areas, dict):
    raise RuntimeError("Base snapshot manifest areas missing")

def webp_size(data: bytes) -> tuple[int, int]:
    if len(data) < 30 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise RuntimeError("Asset is not a valid WEBP container")
    chunk = data[12:16]
    if chunk == b"VP8X":
        width = 1 + int.from_bytes(data[24:27], "little")
        height = 1 + int.from_bytes(data[27:30], "little")
        return width, height
    if chunk == b"VP8L":
        if data[20] != 0x2F:
            raise RuntimeError("Invalid VP8L signature")
        bits = int.from_bytes(data[21:25], "little")
        width = 1 + (bits & 0x3FFF)
        height = 1 + ((bits >> 14) & 0x3FFF)
        return width, height
    raise RuntimeError(f"Unsupported WEBP chunk for pixel background: {chunk!r}")

replaced = 0
for area_id, area_overlay in (overlay.get("areas") or {}).items():
    area_id = str(area_id)
    area = areas.get(area_id)
    if not isinstance(area, dict):
        raise RuntimeError(f"Pixel16 area does not exist in base manifest: {area_id}")
    images = area.get("images")
    if not isinstance(images, list):
        raise RuntimeError(f"Base snapshot area has no image list: {area_id}")
    slots = area_overlay.get("slots") or {}
    for slot_id, record in sorted(slots.items(), key=lambda item: int(item[0])):
        new_name = str(record.get("local_file", "")).strip()
        old_name = str(record.get("replaces", "")).strip()
        if not new_name or not old_name:
            raise RuntimeError(f"Pixel16 replacement path missing: area={area_id} slot={slot_id}")
        asset = SNAPSHOT_DIR / new_name
        if not asset.is_file():
            raise RuntimeError(f"Pixel16 asset missing: {asset}")
        data = asset.read_bytes()
        width, height = webp_size(data)
        if (width, height) != (512, 288):
            raise RuntimeError(f"Pixel16 asset dimensions must be 512x288: {asset} is {width}x{height}")
        expected_bytes = int(record.get("bytes") or 0)
        if expected_bytes != len(data):
            raise RuntimeError(f"Pixel16 asset byte-size mismatch: {asset}")
        expected_sha = str(record.get("sha256", "")).lower()
        actual_sha = hashlib.sha256(data).hexdigest()
        if expected_sha != actual_sha:
            raise RuntimeError(f"Pixel16 asset checksum mismatch: {asset}")
        indexes = [i for i, item in enumerate(images) if str(item.get("local_file", "")).strip() == old_name]
        if len(indexes) != 1:
            raise RuntimeError(
                f"Pixel16 replacement target must occur exactly once: area={area_id} old={old_name} matches={len(indexes)}"
            )
        page_url = str(area.get("page_url", "")).strip()
        images[indexes[0]] = {
            "local_file": new_name,
            "file_title": str(record.get("file_title", f"Pixel16 background {slot_id}")),
            "page_url": page_url,
            "description_url": (record.get("references") or [{}])[0].get("url", page_url),
            "download_url": "",
            "mime": "image/webp",
            "width": width,
            "height": height,
            "bytes": len(data),
            "sha256": actual_sha,
        }
        replaced += 1

if replaced <= 0:
    raise RuntimeError("Pixel16 manifest contains no replacements")

BASE_MANIFEST.write_text(json.dumps(base, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Applied Pixel16 background overrides: replacements={replaced}, size=512x288.")
