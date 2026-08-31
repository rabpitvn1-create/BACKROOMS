#!/usr/bin/env python3
"""Rebuild packaged Backrooms Snapshot pools from the project's three approved visual sources.

Approved source pages:
- https://backrooms-wiki.wikidot.com/
- https://backrooms.fandom.com/wiki/Backrooms_Wiki
- http://backrooms-vn.wikidot.com/

Media CDNs used by those sites are delivery infrastructure only. Every accepted image keeps an
approved source-page URL in the manifest. The generator creates exactly four 512x288 WebP
snapshots for every canonical campaign area. It never synthesizes or AI-generates imagery: when a
page exposes fewer than four distinct images, deterministic crops of those source images fill the
remaining slots. If an area exposes no usable image on any approved source, its parent main Level
source pool is used and the manifest marks that fallback explicitly.
"""

from __future__ import annotations

import ast
import hashlib
import html
from html.parser import HTMLParser
import io
import json
import re
import shutil
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps, UnidentifiedImageError

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots"
MANIFEST = OUT_DIR / "fandom_manifest.json"  # legacy filename retained for runtime compatibility
ROUTE_SOURCE = ROOT / "patch-linear-sublevel-progression.py"
SOURCES_DOC = OUT_DIR / "SOURCES.md"

WIDTH = 512
HEIGHT = 288
SLOTS_PER_AREA = 4
MAX_SOURCE_IMAGES = 10
USER_AGENT = "BACKROOMS-Android-TrustedSnapshotBuilder/2.0 (+https://github.com/rabpitvn1-create/BACKROOMS)"

APPROVED_SOURCE_ROOTS = (
    "https://backrooms-wiki.wikidot.com/",
    "https://backrooms.fandom.com/wiki/Backrooms_Wiki",
    "http://backrooms-vn.wikidot.com/",
)
WIKIDOT_SITES = (
    ("wikidot_en", "https://backrooms-wiki.wikidot.com"),
    ("wikidot_vi", "http://backrooms-vn.wikidot.com"),
)
FANDOM_API = "https://backrooms.fandom.com/api.php"
FANDOM_ROOT = "https://backrooms.fandom.com/wiki/"

REJECT_PARTS = (
    "logo", "favicon", "avatar", "rating", "discord", "license", "creative-commons",
    "survival-class", "survival_class", "threat-index", "threat_index", "badge", "navbar",
    "navigation", "button", "profile-picture", "profile_picture", "sd-hexagon", "readthepage",
)
IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".webp")
CROP_VARIANTS = (
    (0.50, 0.50, 1.00),
    (0.34, 0.50, 0.94),
    (0.66, 0.50, 0.90),
    (0.50, 0.38, 0.86),
)


class LinkCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.urls: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        value = values.get("src") if tag == "img" else values.get("href") if tag == "a" else None
        if value:
            self.urls.append(html.unescape(value.strip()))


def load_route() -> list[tuple[int, str, str, str]]:
    module = ast.parse(ROUTE_SOURCE.read_text(encoding="utf-8"), filename=str(ROUTE_SOURCE))
    route = None
    for node in module.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "ROUTE" for t in node.targets):
            route = ast.literal_eval(node.value)
            break
    if not isinstance(route, list) or len(route) != 43:
        raise RuntimeError(f"Expected canonical 43-area ROUTE, found {0 if route is None else len(route)}")
    output: list[tuple[int, str, str, str]] = []
    for item in route:
        if not isinstance(item, tuple) or len(item) != 4:
            raise RuntimeError(f"Invalid route entry: {item!r}")
        parent, area_id, name, area_type = item
        output.append((int(parent), str(area_id), str(name), str(area_type)))
    return output


def fetch(url: str, *, referer: str | None = None, attempts: int = 3) -> tuple[bytes, str, str]:
    last: Exception | None = None
    headers = {"User-Agent": USER_AGENT, "Accept": "*/*"}
    if referer:
        headers["Referer"] = referer
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=40) as response:
                return response.read(), response.headers.get_content_type().lower(), response.geturl()
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            last = exc
            if attempt + 1 < attempts:
                time.sleep(1.0 + attempt)
    raise RuntimeError(f"download failed: {url}: {last}")


def fetch_text(url: str, *, referer: str | None = None) -> tuple[str, str]:
    raw, _, final_url = fetch(url, referer=referer)
    return raw.decode("utf-8", errors="replace"), final_url


def slugify(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii").lower()
    return re.sub(r"[^a-z0-9]+", "-", value).strip("-")


def page_slugs(area_id: str, area_name: str, area_type: str) -> list[str]:
    values: list[str] = []

    def add(value: str) -> None:
        value = value.strip("-/ ")
        if value and value not in values:
            values.append(value)

    normalized_id = area_id.replace(".", "-")
    if area_type == "MAIN":
        add(f"level-{normalized_id}")
    elif area_type == "SUBLEVEL":
        add(f"level-{normalized_id}")
        add(f"level-{area_id}")
        add(slugify(f"Level {area_id} {area_name}"))
    else:
        add(slugify(area_id))
        add(slugify(area_name))
        add(slugify(f"Level {area_name}"))
        add(f"level-{normalized_id}")
    return values


def wikidot_candidates(site_id: str, root: str, area_id: str, area_name: str, area_type: str) -> list[dict]:
    output: list[dict] = []
    for slug in page_slugs(area_id, area_name, area_type):
        page_url = root.rstrip("/") + "/" + slug
        try:
            body, final_page = fetch_text(page_url, referer=root + "/")
        except RuntimeError:
            continue
        lower = body.lower()
        if "the page you are looking for does not exist" in lower or "this page doesn't exist" in lower:
            continue
        parser = LinkCollector()
        parser.feed(body)
        seen: set[str] = set()
        for raw_url in parser.urls:
            media = urllib.parse.urljoin(final_page, raw_url)
            parsed = urllib.parse.urlparse(media)
            path_lower = parsed.path.lower()
            if not ("local--files" in path_lower or path_lower.endswith(IMAGE_EXTENSIONS)):
                continue
            name = path_lower.rsplit("/", 1)[-1]
            if any(part in name for part in REJECT_PARTS):
                continue
            if media in seen:
                continue
            seen.add(media)
            output.append({
                "source_site": site_id,
                "source_page": final_page,
                "media_url": media,
                "description_url": final_page,
                "license": "See source page / attachment attribution",
            })
        if output:
            break
    return output


def fandom_api(params: dict[str, object]) -> dict:
    query = {"format": "json", "formatversion": "2", "origin": "*", **params}
    url = FANDOM_API + "?" + urllib.parse.urlencode(query, doseq=True)
    raw, _, _ = fetch(url, referer=FANDOM_ROOT)
    return json.loads(raw.decode("utf-8"))


def fandom_titles(area_id: str, area_name: str, area_type: str) -> list[str]:
    candidates = []
    if area_type == "MAIN":
        candidates.append(f"Level {area_id}")
    elif area_type == "SUBLEVEL":
        candidates.extend((f"Level {area_id}", f"Level {area_id} - {area_name}", f"Level {area_id}: {area_name}"))
    else:
        candidates.extend((area_name, f"Level {area_name}", f"Level {area_id}"))
    payload = fandom_api({"action": "query", "prop": "info", "redirects": "1", "titles": "|".join(candidates)})
    for page in payload.get("query", {}).get("pages", []):
        if not page.get("missing") and page.get("title"):
            return [str(page["title"])]
    search = fandom_api({"action": "query", "list": "search", "srsearch": f'"{area_name}"', "srnamespace": "0", "srlimit": "5"})
    return [str(row.get("title")) for row in search.get("query", {}).get("search", []) if row.get("title")][:1]


def clean_meta(value: object) -> str:
    if isinstance(value, dict):
        value = value.get("value", "")
    text = html.unescape(str(value or ""))
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def fandom_candidates(area_id: str, area_name: str, area_type: str) -> list[dict]:
    titles = fandom_titles(area_id, area_name, area_type)
    if not titles:
        return []
    page_title = titles[0]
    page_url = FANDOM_ROOT + urllib.parse.quote(page_title.replace(" ", "_"), safe="_()-,.'")
    payload = fandom_api({"action": "query", "prop": "images", "titles": page_title, "imlimit": "max"})
    pages = payload.get("query", {}).get("pages", [])
    image_titles = [str(row.get("title")) for row in (pages[0].get("images", []) if pages else []) if row.get("title")]
    output: list[dict] = []
    for start in range(0, len(image_titles), 40):
        batch = image_titles[start:start + 40]
        metadata = fandom_api({
            "action": "query", "prop": "imageinfo", "titles": "|".join(batch),
            "iiprop": "url|size|mime|extmetadata", "iiurlwidth": "1280", "iilimit": "1",
        })
        for image_page in metadata.get("query", {}).get("pages", []):
            title = str(image_page.get("title", ""))
            lower = title.lower()
            if any(part in lower for part in REJECT_PARTS):
                continue
            infos = image_page.get("imageinfo") or []
            if not infos:
                continue
            info = infos[0]
            mime = str(info.get("mime", "")).lower()
            if not mime.startswith("image/") or mime.endswith("svg+xml"):
                continue
            width = int(info.get("width") or 0)
            height = int(info.get("height") or 0)
            if width < 320 or height < 200:
                continue
            media = str(info.get("thumburl") or info.get("url") or "")
            if not media:
                continue
            ext = info.get("extmetadata") or {}
            license_name = clean_meta(ext.get("LicenseShortName")) or clean_meta(ext.get("UsageTerms")) or "See Fandom file page"
            output.append({
                "source_site": "fandom",
                "source_page": page_url,
                "media_url": media,
                "description_url": str(info.get("descriptionurl") or page_url),
                "license": license_name,
                "file_title": title,
            })
    return output


def candidate_stream(area_id: str, area_name: str, area_type: str) -> list[dict]:
    output: list[dict] = []
    for site_id, root in WIKIDOT_SITES:
        output.extend(wikidot_candidates(site_id, root, area_id, area_name, area_type))
    try:
        output.extend(fandom_candidates(area_id, area_name, area_type))
    except RuntimeError as exc:
        print(f"SOURCE_WARN area={area_id} site=fandom error={exc}")
    return output


def decode_image(data: bytes) -> Image.Image | None:
    try:
        with Image.open(io.BytesIO(data)) as im:
            im.load()
            if im.width < 240 or im.height < 160:
                return None
            return im.convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError):
        return None


def download_sources(records: list[dict], cache_dir: Path) -> list[dict]:
    accepted: list[dict] = []
    seen_sha: set[str] = set()
    for index, record in enumerate(records):
        if len(accepted) >= MAX_SOURCE_IMAGES:
            break
        try:
            data, _, final_url = fetch(str(record["media_url"]), referer=str(record["source_page"]))
        except RuntimeError:
            continue
        image = decode_image(data)
        if image is None:
            continue
        digest = hashlib.sha256(data).hexdigest()
        if digest in seen_sha:
            continue
        seen_sha.add(digest)
        raw_path = cache_dir / f"source_{index:02d}.bin"
        raw_path.write_bytes(data)
        item = dict(record)
        item.update({"raw_path": str(raw_path), "media_url": final_url, "source_sha256": digest, "source_width": image.width, "source_height": image.height})
        accepted.append(item)
    return accepted


def render_slot(source: dict, slot_index: int, output: Path) -> tuple[int, str]:
    data = Path(str(source["raw_path"])).read_bytes()
    image = decode_image(data)
    if image is None:
        raise RuntimeError("source image became unreadable")
    cx, cy, zoom = CROP_VARIANTS[slot_index % len(CROP_VARIANTS)]
    if zoom < 0.999:
        crop_w = max(2, int(image.width * zoom))
        crop_h = max(2, int(image.height * zoom))
        left = max(0, min(image.width - crop_w, int((image.width - crop_w) * cx)))
        top = max(0, min(image.height - crop_h, int((image.height - crop_h) * cy)))
        image = image.crop((left, top, left + crop_w, top + crop_h))
    frame = ImageOps.fit(image, (WIDTH, HEIGHT), method=Image.Resampling.LANCZOS, centering=(cx, cy))
    frame.save(output, "WEBP", quality=88, method=6)
    payload = output.read_bytes()
    if not (payload.startswith(b"RIFF") and payload[8:12] == b"WEBP"):
        raise RuntimeError(f"invalid WebP output: {output}")
    with Image.open(output) as check:
        if check.size != (WIDTH, HEIGHT):
            raise RuntimeError(f"wrong snapshot size: {output}: {check.size}")
    return len(payload), hashlib.sha256(payload).hexdigest()


def area_slug(route_index: int, area_id: str) -> str:
    value = unicodedata.normalize("NFKD", area_id).encode("ascii", "ignore").decode("ascii")
    value = re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").lower()
    return value or f"route_{route_index:02d}"


def build() -> None:
    route = load_route()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="trusted-snapshots-") as tmp:
        tmp_root = Path(tmp)
        generated_dir = tmp_root / "level_snapshots"
        generated_dir.mkdir()
        parent_sources: dict[str, list[dict]] = {}
        areas: dict[str, dict] = {}
        fallback_areas: list[dict] = []

        for route_index, (parent_level, area_id, area_name, area_type) in enumerate(route):
            print(f"AREA {route_index + 1}/{len(route)} id={area_id} name={area_name}", flush=True)
            area_cache = tmp_root / f"cache_{route_index:02d}"
            area_cache.mkdir()
            source_records = candidate_stream(area_id, area_name, area_type)
            sources = download_sources(source_records, area_cache)
            resolution = "direct"
            if not sources:
                parent_key = str(parent_level)
                sources = parent_sources.get(parent_key, [])
                if not sources:
                    raise RuntimeError(f"No approved-source image found for area={area_id} and parent Level {parent_level} has no source pool")
                resolution = "parent_source_fallback"
                fallback_areas.append({"area_id": area_id, "parent_level": parent_level, "area_name": area_name})
            if area_type == "MAIN":
                parent_sources[str(parent_level)] = sources

            slug = area_slug(route_index, area_id)
            images: list[dict] = []
            output_hashes: set[str] = set()
            for slot in range(SLOTS_PER_AREA):
                source = sources[slot % len(sources)]
                name = f"area_{route_index:02d}_{slug}_trusted_{slot + 1:02d}.webp"
                path = generated_dir / name
                size, digest = render_slot(source, slot, path)
                if digest in output_hashes:
                    source = sources[(slot + 1) % len(sources)]
                    size, digest = render_slot(source, slot + 1, path)
                output_hashes.add(digest)
                images.append({
                    "local_file": name,
                    "file_title": str(source.get("file_title") or f"{area_name} trusted snapshot {slot + 1}"),
                    "page_url": str(source["source_page"]),
                    "description_url": str(source.get("description_url") or source["source_page"]),
                    "download_url": str(source["media_url"]),
                    "source_site": str(source["source_site"]),
                    "source_license": str(source.get("license") or "See source page"),
                    "source_sha256": str(source["source_sha256"]),
                    "source_width": int(source["source_width"]),
                    "source_height": int(source["source_height"]),
                    "derived_operation": "crop+resize+webp only; no AI generation",
                    "mime": "image/webp",
                    "width": WIDTH,
                    "height": HEIGHT,
                    "bytes": size,
                    "sha256": digest,
                })
            if len(images) != SLOTS_PER_AREA:
                raise RuntimeError(f"Area {area_id} did not produce exactly four snapshots")
            areas[area_id] = {
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
            "route_source": ROUTE_SOURCE.name,
            "route_count": len(route),
            "selection": {
                "images_per_area": SLOTS_PER_AREA,
                "width": WIDTH,
                "height": HEIGHT,
                "format": "webp",
                "policy": "Only imagery exposed by the three approved Backrooms source sites; source images may be cropped/resized but never AI-generated.",
            },
            "areas": areas,
            "fallback_areas": fallback_areas,
        }
        manifest_path = tmp_root / "fandom_manifest.json"
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        if len(areas) != 43:
            raise RuntimeError(f"Expected 43 generated areas, got {len(areas)}")
        for area_id, area in areas.items():
            if len(area["images"]) != 4:
                raise RuntimeError(f"Area {area_id} snapshot count is not 4")
            for record in area["images"]:
                asset = generated_dir / record["local_file"]
                if not asset.is_file() or asset.stat().st_size != int(record["bytes"]):
                    raise RuntimeError(f"Generated snapshot verification failed: {asset}")
                if hashlib.sha256(asset.read_bytes()).hexdigest() != record["sha256"]:
                    raise RuntimeError(f"Generated snapshot checksum mismatch: {asset}")

        for path in OUT_DIR.iterdir():
            if path.is_file() and (
                (path.name.startswith("area_") and path.suffix.lower() in {".webp", ".jpg", ".jpeg", ".png"})
                or re.fullmatch(r"level_[0-6]\.webp", path.name)
                or path.name == "pixel16_manifest.json"
            ):
                path.unlink()
        for asset in generated_dir.iterdir():
            shutil.copy2(asset, OUT_DIR / asset.name)
        shutil.copy2(manifest_path, MANIFEST)

    SOURCES_DOC.write_text(
        "# Level Snapshot image sources\n\n"
        "Only these three source sites are approved for packaged Backrooms Snapshot imagery:\n\n"
        "1. https://backrooms-wiki.wikidot.com/\n"
        "2. https://backrooms.fandom.com/wiki/Backrooms_Wiki\n"
        "3. http://backrooms-vn.wikidot.com/\n\n"
        "`prepare-trusted-level-snapshots.py` resolves the canonical 43-area Level 0–6 route, gathers images exposed by those pages, and produces exactly four 512x288 WebP snapshots for every Level/sublevel/special area. It performs only crop, resize, and WebP encoding; it never AI-generates scene content.\n\n"
        "Wikidot and Fandom may serve attachment bytes from their own media/CDN hosts (for example `*.wdfiles.com` or `static.wikia.nocookie.net`). Those hosts are treated only as delivery infrastructure: every manifest record must retain an approved source-page URL from one of the three sites above.\n\n"
        "If an area has fewer than four distinct source images, deterministic crops of the available approved-source images fill the remaining slots. If none of the three sites exposes a usable image for a sub-area, the generator may use its parent main Level source pool and records that explicitly as `parent_source_fallback`; it never searches any fourth website.\n\n"
        "The generated files are committed into the APK so runtime remains offline. `fandom_manifest.json` keeps its historical filename only for renderer compatibility; its `source` and provenance fields are authoritative.\n",
        encoding="utf-8",
    )
    print(f"Trusted Snapshot rebuild complete: areas=43, images={43 * SLOTS_PER_AREA}, size={WIDTH}x{HEIGHT}, fallbacks={len(fallback_areas)}")


if __name__ == "__main__":
    build()
