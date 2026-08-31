#!/usr/bin/env python3
"""Rebuild packaged Backrooms Snapshot pools from approved source metadata.

Only these source sites are allowed:
- https://backrooms-wiki.wikidot.com/
- https://backrooms.fandom.com/wiki/Backrooms_Wiki
- http://backrooms-vn.wikidot.com/

The source manifest provides page/media provenance already resolved for the campaign. Media CDN
hosts are transport only; every accepted record must point back to an approved source page. The
builder downloads source imagery again, then performs crop/resize/WebP encoding only. It never
synthesizes or AI-generates scene content.
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import re
import shutil
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps, ImageStat, UnidentifiedImageError

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots"
MANIFEST = OUT_DIR / "fandom_manifest.json"  # legacy filename retained for renderer compatibility
CATALOG_ROOT = ROOT / "app/src/main/assets/level_catalog"
SOURCES_DOC = OUT_DIR / "SOURCES.md"
CAMPAIGN_ID = "BACKROOMS_FANDOM_LEVELS_0_6_R01"

WIDTH = 512
HEIGHT = 288
SLOTS_PER_AREA = 4
USER_AGENT = "BACKROOMS-Android-TrustedSnapshotBuilder/2.1 (+https://github.com/rabpitvn1-create/BACKROOMS)"

APPROVED_SOURCE_ROOTS = (
    "https://backrooms-wiki.wikidot.com/",
    "https://backrooms.fandom.com/wiki/Backrooms_Wiki",
    "http://backrooms-vn.wikidot.com/",
)
APPROVED_PAGE_PREFIXES = (
    "https://backrooms-wiki.wikidot.com/",
    "https://backrooms.fandom.com/wiki/",
    "http://backrooms-vn.wikidot.com/",
    "https://backrooms-vn.wikidot.com/",
)
REJECT_RENDER_TITLES = (
    "sd-hexagon",
    "default profile picture",
    "site logo",
    "readthepage",
    "whitebackground",
    "survival class",
    "threat index",
    "favicon",
    "navigation",
    "discord",
    "license icon",
)
CROP_VARIANTS = (
    (0.50, 0.50, 1.00),
    (0.32, 0.50, 0.94),
    (0.68, 0.50, 0.90),
    (0.50, 0.34, 0.86),
    (0.24, 0.42, 0.82),
    (0.76, 0.58, 0.80),
    (0.44, 0.72, 0.78),
    (0.58, 0.26, 0.76),
)


def load_route() -> list[tuple[int, str, str, str]]:
    entries: list[dict] = []
    for path in sorted(CATALOG_ROOT.rglob("*.json")):
        if path.name.startswith("_"):
            continue
        raw = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(raw, list):
            document_entries = raw
            inherited_campaign = ""
        elif isinstance(raw, dict):
            document_entries = raw.get("entries", [raw])
            inherited_campaign = str(raw.get("campaignId") or "").strip()
        else:
            continue
        for entry in document_entries:
            if not isinstance(entry, dict):
                continue
            item = dict(entry)
            if inherited_campaign:
                item.setdefault("campaignId", inherited_campaign)
            entries.append(item)
    route_entries = [
        entry for entry in entries
        if str(entry.get("campaignId") or "").strip() == CAMPAIGN_ID
        and entry.get("campaignOrder") is not None
    ]
    route_entries.sort(key=lambda entry: int(entry["campaignOrder"]))
    route = [
        (
            int(entry["parentMainLevel"]),
            str(entry["id"]).strip(),
            str(entry["name"]).strip(),
            str(entry["kind"]).strip().upper(),
        )
        for entry in route_entries
    ]
    if len(route) != 43:
        raise RuntimeError(f"Expected catalog-backed 43-area route, found {len(route)}")
    return route


def approved_page(url: str) -> bool:
    return any(url.startswith(prefix) for prefix in APPROVED_PAGE_PREFIXES)


def fetch(url: str, referer: str, attempts: int = 2) -> tuple[bytes, str]:
    last: Exception | None = None
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": USER_AGENT,
                    "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                    "Referer": referer,
                },
            )
            with urllib.request.urlopen(req, timeout=20) as response:
                return response.read(), response.geturl()
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            last = exc
            if attempt + 1 < attempts:
                time.sleep(0.8)
    raise RuntimeError(f"download failed: {url}: {last}")


def decode_source(data: bytes) -> Image.Image | None:
    try:
        with Image.open(io.BytesIO(data)) as image:
            image.load()
            if image.width < 320 or image.height < 180:
                return None
            rgb = image.convert("RGB")
            probe = rgb.resize((64, 64), Image.Resampling.BILINEAR)
            variance = sum(ImageStat.Stat(probe).var)
            if variance < 4.0:
                return None
            return rgb
    except (UnidentifiedImageError, OSError, ValueError):
        return None


def source_manifest_path() -> Path:
    override = os.environ.get("TRUSTED_SOURCE_MANIFEST", "").strip()
    return Path(override) if override else MANIFEST


def normalize_source_records(source_manifest: dict, route: list[tuple[int, str, str, str]]) -> dict[str, list[dict]]:
    source_areas = source_manifest.get("areas") or {}
    direct: dict[str, list[dict]] = {}
    for _, area_id, _, _ in route:
        area = source_areas.get(area_id) or {}
        records: list[dict] = []
        for record in area.get("images") or []:
            title = str(record.get("file_title") or "").strip()
            if any(part in title.lower() for part in REJECT_RENDER_TITLES):
                continue
            page_url = str(record.get("page_url") or area.get("page_url") or "").strip()
            media_url = str(record.get("download_url") or "").strip()
            if not page_url or not approved_page(page_url) or not media_url:
                continue
            records.append(
                {
                    "file_title": title or f"{area_id} source image",
                    "page_url": page_url,
                    "description_url": str(record.get("description_url") or page_url),
                    "download_url": media_url,
                    "source_license": str(record.get("source_license") or record.get("license") or "See source page"),
                }
            )
        direct[area_id] = records
    return direct


def download_pool(records: list[dict], cache: dict[str, dict]) -> list[dict]:
    pool: list[dict] = []
    seen: set[str] = set()
    for record in records:
        url = record["download_url"]
        if url in cache:
            cached = cache[url]
            if cached.get("valid"):
                pool.append({**record, **cached})
            continue
        try:
            data, final_url = fetch(url, record["page_url"])
        except RuntimeError as exc:
            print(f"SOURCE_WARN page={record['page_url']} media={url} error={exc}")
            cache[url] = {"valid": False}
            continue
        image = decode_source(data)
        if image is None:
            cache[url] = {"valid": False}
            continue
        source_sha = hashlib.sha256(data).hexdigest()
        if source_sha in seen:
            continue
        seen.add(source_sha)
        cached = {
            "valid": True,
            "source_bytes": data,
            "source_sha256": source_sha,
            "source_width": image.width,
            "source_height": image.height,
            "final_download_url": final_url,
        }
        cache[url] = cached
        pool.append({**record, **cached})
    return pool


def crop_variant(image: Image.Image, variant_index: int) -> Image.Image:
    cx, cy, zoom = CROP_VARIANTS[variant_index % len(CROP_VARIANTS)]
    working = image
    if zoom < 0.999:
        crop_w = max(2, int(image.width * zoom))
        crop_h = max(2, int(image.height * zoom))
        left = max(0, min(image.width - crop_w, int((image.width - crop_w) * cx)))
        top = max(0, min(image.height - crop_h, int((image.height - crop_h) * cy)))
        working = image.crop((left, top, left + crop_w, top + crop_h))
    return ImageOps.fit(working, (WIDTH, HEIGHT), method=Image.Resampling.LANCZOS, centering=(cx, cy))


def render_unique(pool: list[dict], slot: int, output: Path, used_hashes: set[str]) -> tuple[dict, int, str]:
    for attempt in range(max(8, len(pool) * len(CROP_VARIANTS))):
        source = pool[(slot + attempt) % len(pool)]
        image = decode_source(source["source_bytes"])
        if image is None:
            continue
        variant = slot + attempt
        frame = crop_variant(image, variant)
        buffer = io.BytesIO()
        frame.save(buffer, "WEBP", quality=88, method=6)
        payload = buffer.getvalue()
        digest = hashlib.sha256(payload).hexdigest()
        if digest in used_hashes:
            continue
        output.write_bytes(payload)
        with Image.open(output) as check:
            if check.format != "WEBP" or check.size != (WIDTH, HEIGHT):
                raise RuntimeError(f"Generated Snapshot contract failed: {output}: {check.format} {check.size}")
        used_hashes.add(digest)
        return source, len(payload), digest
    raise RuntimeError(f"Could not derive four distinct snapshots from approved source pool: {output}")


def safe_slug(route_index: int, area_id: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9]+", "_", area_id).strip("_").lower()
    return slug or f"route_{route_index:02d}"


def build() -> None:
    route = load_route()
    source_path = source_manifest_path()
    if not source_path.is_file():
        raise RuntimeError(f"Approved source manifest missing: {source_path}")
    source_manifest = json.loads(source_path.read_text(encoding="utf-8"))
    direct_records = normalize_source_records(source_manifest, route)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    download_cache: dict[str, dict] = {}
    resolved_pools: dict[str, list[dict]] = {}
    generated_areas: dict[str, dict] = {}
    fallback_areas: list[dict] = []

    with tempfile.TemporaryDirectory(prefix="trusted-snapshot-build-") as temporary:
        temp_root = Path(temporary)
        generated_dir = temp_root / "images"
        generated_dir.mkdir()

        for route_index, (parent_level, area_id, area_name, area_type) in enumerate(route):
            print(f"AREA {route_index + 1}/43 id={area_id} name={area_name}", flush=True)
            pool = download_pool(direct_records.get(area_id, []), download_cache)
            resolution = "direct"
            if not pool:
                parent_id = str(parent_level)
                pool = resolved_pools.get(parent_id, [])
                if not pool:
                    parent_pool = download_pool(direct_records.get(parent_id, []), download_cache)
                    if parent_pool:
                        resolved_pools[parent_id] = parent_pool
                        pool = parent_pool
                if not pool:
                    raise RuntimeError(f"No usable approved-source image for area={area_id} or parent Level {parent_level}")
                resolution = "parent_source_fallback"
                fallback_areas.append({"area_id": area_id, "area_name": area_name, "parent_level": parent_level})
            if area_type == "MAIN":
                resolved_pools[area_id] = pool

            used_hashes: set[str] = set()
            images: list[dict] = []
            slug = safe_slug(route_index, area_id)
            for slot in range(SLOTS_PER_AREA):
                name = f"area_{route_index:02d}_{slug}_trusted_{slot + 1:02d}.webp"
                output = generated_dir / name
                source, byte_count, digest = render_unique(pool, slot, output, used_hashes)
                images.append(
                    {
                        "local_file": name,
                        "file_title": source["file_title"],
                        "page_url": source["page_url"],
                        "description_url": source["description_url"],
                        "download_url": source["final_download_url"],
                        "source_site": "approved_backrooms_source",
                        "source_license": source["source_license"],
                        "source_sha256": source["source_sha256"],
                        "source_width": source["source_width"],
                        "source_height": source["source_height"],
                        "derived_operation": "crop+resize+webp only; no AI generation",
                        "mime": "image/webp",
                        "width": WIDTH,
                        "height": HEIGHT,
                        "bytes": byte_count,
                        "sha256": digest,
                    }
                )
            generated_areas[area_id] = {
                "route_index": route_index,
                "parent_level": parent_level,
                "area_id": area_id,
                "area_name": area_name,
                "area_type": area_type,
                "status": "ok",
                "resolution": resolution,
                "images": images,
            }

        manifest = {
            "source": "Trusted Backrooms sources only",
            "approved_source_roots": list(APPROVED_SOURCE_ROOTS),
            "route_source": "level_catalog/BACKROOMS_FANDOM_LEVELS_0_6_R01",
            "route_count": len(route),
            "selection": {
                "images_per_area": SLOTS_PER_AREA,
                "width": WIDTH,
                "height": HEIGHT,
                "format": "webp",
                "policy": "Approved source-page imagery only; crop/resize/WebP encoding only; no AI generation.",
            },
            "areas": generated_areas,
            "fallback_areas": fallback_areas,
        }
        temp_manifest = temp_root / "fandom_manifest.json"
        temp_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        if len(generated_areas) != 43:
            raise RuntimeError(f"Expected 43 generated areas, got {len(generated_areas)}")
        total = 0
        for area_id, area in generated_areas.items():
            if len(area["images"]) != 4:
                raise RuntimeError(f"Area {area_id} does not have exactly four snapshots")
            hashes = {record["sha256"] for record in area["images"]}
            if len(hashes) != 4:
                raise RuntimeError(f"Area {area_id} snapshots are not distinct")
            for record in area["images"]:
                asset = generated_dir / record["local_file"]
                if asset.stat().st_size != record["bytes"]:
                    raise RuntimeError(f"Snapshot byte mismatch: {asset}")
                if hashlib.sha256(asset.read_bytes()).hexdigest() != record["sha256"]:
                    raise RuntimeError(f"Snapshot hash mismatch: {asset}")
                total += 1
        if total != 172:
            raise RuntimeError(f"Expected 172 generated snapshots, got {total}")

        # Destructive replacement occurs only after the complete new set passes verification.
        for path in OUT_DIR.iterdir():
            if path.is_file() and (
                (path.name.startswith("area_") and path.suffix.lower() in {".webp", ".jpg", ".jpeg", ".png"})
                or re.fullmatch(r"level_[0-6]\.webp", path.name)
                or path.name == "pixel16_manifest.json"
            ):
                path.unlink()
        for asset in generated_dir.iterdir():
            shutil.copy2(asset, OUT_DIR / asset.name)
        shutil.copy2(temp_manifest, MANIFEST)

    SOURCES_DOC.write_text(
        "# Level Snapshot image sources\n\n"
        "The APK accepts Snapshot imagery only from these three Backrooms source sites:\n\n"
        "1. https://backrooms-wiki.wikidot.com/\n"
        "2. https://backrooms.fandom.com/wiki/Backrooms_Wiki\n"
        "3. http://backrooms-vn.wikidot.com/\n\n"
        "Every campaign Level, sublevel, and special area receives exactly four 512x288 WebP Snapshot backgrounds. The builder re-downloads imagery referenced by approved source pages and performs only crop, resize, and WebP encoding. It does not AI-generate environment imagery.\n\n"
        "Media bytes may be delivered by infrastructure used by those sites, such as `static.wikia.nocookie.net` or `*.wdfiles.com`; the manifest always retains the approved Backrooms page as provenance.\n\n"
        "If a sub-area has no usable approved-source image, it uses its parent main Level source pool and records `parent_source_fallback`. Old Fandom snapshots, the rejected Pixel16 asset/manifest, and legacy Escape the Backrooms fallback images are removed only after the complete replacement set verifies successfully.\n",
        encoding="utf-8",
    )
    print(f"Trusted Snapshot rebuild complete: areas=43 assets=172 fallbacks={len(fallback_areas)}")


if __name__ == "__main__":
    build()
