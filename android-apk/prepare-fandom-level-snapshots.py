#!/usr/bin/env python3
"""Prepare attributed Snapshot pools for every campaign area from Backrooms Wiki Fandom.

The canonical campaign route lives in patch-linear-sublevel-progression.py. This script
reads that literal route without executing the patch, resolves the matching Fandom page
for each area, downloads a small local image pool, and records areas whose page has no
usable image. Missing non-main areas fall back to their parent Level at runtime; main
Levels remain mandatory so Snapshot never becomes blank.
"""

from __future__ import annotations

import ast
import difflib
import hashlib
import html
import json
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots"
MANIFEST = OUT_DIR / "fandom_manifest.json"
ROUTE_SOURCE = ROOT / "patch-linear-sublevel-progression.py"
API = "https://backrooms.fandom.com/api.php"
WIKI_ROOT = "https://backrooms.fandom.com/wiki/"
USER_AGENT = "BACKROOMS-Android-SnapshotBuilder/1.1 (+https://github.com/rabpitvn1-create/BACKROOMS)"
MIN_IMAGES_PER_MAIN_AREA = 2
MAX_IMAGES_PER_AREA = 3
THUMB_WIDTH = 768

REJECT_NAME_PARTS = (
    "survival class",
    "survival_class",
    "threat index",
    "threat_index",
    "class badge",
    "class_badge",
    "difficulty",
    "wikilogo",
    "wiki logo",
    "fandom logo",
    "favicon",
    "navigation",
    "navbar",
    "rating",
    "button",
    "discord",
    "license icon",
    "creative commons",
)
REJECT_TEXT_PARTS = (
    "survival difficulty",
    "threat index",
    "class designation",
    "navigation icon",
)
MIME_EXTENSIONS = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
}


def load_route() -> list[tuple[int, str, str, str]]:
    if not ROUTE_SOURCE.is_file():
        raise RuntimeError("Canonical linear sublevel route patch is missing")
    module = ast.parse(ROUTE_SOURCE.read_text(encoding="utf-8"), filename=str(ROUTE_SOURCE))
    route = None
    for node in module.body:
        if not isinstance(node, ast.Assign):
            continue
        if any(isinstance(target, ast.Name) and target.id == "ROUTE" for target in node.targets):
            route = ast.literal_eval(node.value)
            break
    if not isinstance(route, list) or len(route) != 43:
        raise RuntimeError(f"Expected canonical 43-area ROUTE, found {0 if route is None else len(route)}")
    cleaned: list[tuple[int, str, str, str]] = []
    for item in route:
        if not isinstance(item, tuple) or len(item) != 4:
            raise RuntimeError(f"Invalid route entry: {item!r}")
        parent_level, area_id, area_name, area_type = item
        if not isinstance(parent_level, int) or not 0 <= parent_level <= 6:
            raise RuntimeError(f"Invalid parent Level in route entry: {item!r}")
        cleaned.append((parent_level, str(area_id), str(area_name), str(area_type)))
    return cleaned


def request_bytes(url: str, referer: str | None = None, attempts: int = 3) -> tuple[bytes, str]:
    last: Exception | None = None
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
    }
    if referer:
        headers["Referer"] = referer
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=35) as response:
                return response.read(), response.headers.get_content_type().lower()
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            last = exc
            if attempt + 1 < attempts:
                time.sleep(1.2 * (attempt + 1))
    raise RuntimeError(f"download failed after {attempts} attempts: {url}: {last}")


def api_call(params: dict[str, object]) -> dict:
    query = {
        "format": "json",
        "formatversion": "2",
        "origin": "*",
        **params,
    }
    url = API + "?" + urllib.parse.urlencode(query, doseq=True)
    raw, _ = request_bytes(url, referer=WIKI_ROOT)
    return json.loads(raw.decode("utf-8"))


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold().replace("’", "'")
    return re.sub(r"[^0-9a-z\u0080-\uffff]+", "", value)


def page_candidates(area_id: str, area_name: str, area_type: str) -> list[str]:
    candidates: list[str] = []

    def add(value: str) -> None:
        value = re.sub(r"\s+", " ", value).strip()
        if value and value not in candidates:
            candidates.append(value)

    if area_type == "MAIN":
        add(f"Level {area_id}")
    elif area_id == "epsilon":
        add("Level ε")
        add("Level Epsilon")
        add("Level epsilon")
        add(area_name)
    elif area_type == "SUBLEVEL":
        add(f"Level {area_id}")
        add(f"Level {area_id} - {area_name}")
        add(f"Level {area_id}: {area_name}")
    else:
        add(area_id)
        add(area_name)
        add(f"Level {area_id}")
        add(f"Level {area_name}")
    return candidates


def resolve_direct_page(candidates: list[str]) -> str | None:
    if not candidates:
        return None
    payload = api_call(
        {
            "action": "query",
            "prop": "info",
            "redirects": "1",
            "titles": "|".join(candidates),
        }
    )
    for page in payload.get("query", {}).get("pages", []):
        if page.get("missing"):
            continue
        title = str(page.get("title", "")).strip()
        if title:
            return title
    return None


def page_match_score(title: str, area_id: str, area_name: str) -> float:
    title_norm = normalize_text(title)
    id_norm = normalize_text(area_id)
    level_id_norm = normalize_text(f"Level {area_id}")
    name_norm = normalize_text(area_name)
    if not title_norm:
        return 0.0
    if title_norm in {id_norm, level_id_norm, name_norm}:
        return 100.0
    score = 0.0
    if name_norm and len(name_norm) >= 4 and (name_norm in title_norm or title_norm in name_norm):
        score = max(score, 90.0)
    if level_id_norm and level_id_norm in title_norm:
        score = max(score, 88.0)
    elif id_norm and len(id_norm) >= 2 and id_norm in title_norm:
        score = max(score, 76.0)
    if name_norm:
        score = max(score, difflib.SequenceMatcher(None, title_norm, name_norm).ratio() * 80.0)
    return score


def resolve_search_page(area_id: str, area_name: str) -> str | None:
    queries = [f'"{area_name}"', f'"Level {area_id}"', area_name]
    seen: set[str] = set()
    ranked: list[tuple[float, str]] = []
    for query in queries:
        if not query.strip() or query in seen:
            continue
        seen.add(query)
        payload = api_call(
            {
                "action": "query",
                "list": "search",
                "srsearch": query,
                "srnamespace": "0",
                "srlimit": "10",
            }
        )
        for result in payload.get("query", {}).get("search", []):
            title = str(result.get("title", "")).strip()
            if not title:
                continue
            ranked.append((page_match_score(title, area_id, area_name), title))
    if not ranked:
        return None
    ranked.sort(key=lambda item: item[0], reverse=True)
    return ranked[0][1] if ranked[0][0] >= 76.0 else None


def resolve_page(area_id: str, area_name: str, area_type: str) -> tuple[str | None, str, list[str]]:
    candidates = page_candidates(area_id, area_name, area_type)
    direct = resolve_direct_page(candidates)
    if direct:
        return direct, "direct", candidates
    searched = resolve_search_page(area_id, area_name)
    if searched:
        return searched, "search", candidates
    return None, "missing", candidates


def page_image_titles(page_name: str) -> list[str]:
    titles: list[str] = []
    continuation: dict[str, object] = {}
    while True:
        payload = api_call(
            {
                "action": "query",
                "prop": "images",
                "titles": page_name,
                "imlimit": "max",
                **continuation,
            }
        )
        pages = payload.get("query", {}).get("pages", [])
        if not pages or pages[0].get("missing"):
            raise RuntimeError(f"Fandom page disappeared after resolution: {page_name}")
        for image in pages[0].get("images", []):
            title = str(image.get("title", "")).strip()
            if title and title not in titles:
                titles.append(title)
        continuation = payload.get("continue") or {}
        if not continuation:
            break
    return titles


def image_metadata(titles: list[str]) -> dict[str, dict]:
    output: dict[str, dict] = {}
    for start in range(0, len(titles), 40):
        batch = titles[start : start + 40]
        payload = api_call(
            {
                "action": "query",
                "prop": "imageinfo",
                "titles": "|".join(batch),
                "iiprop": "url|size|mime|extmetadata",
                "iiurlwidth": THUMB_WIDTH,
                "iilimit": "1",
            }
        )
        for page in payload.get("query", {}).get("pages", []):
            title = str(page.get("title", "")).strip()
            infos = page.get("imageinfo") or []
            if title and infos:
                output[title] = infos[0]
    return output


def clean_metadata(value: object) -> str:
    if isinstance(value, dict):
        value = value.get("value", "")
    text = html.unescape(str(value or ""))
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def usable_candidate(title: str, info: dict) -> bool:
    lower_name = title.lower().replace("file:", "")
    if any(part in lower_name for part in REJECT_NAME_PARTS):
        return False
    mime = str(info.get("mime", "")).lower()
    if mime not in MIME_EXTENSIONS:
        return False
    width = int(info.get("width") or 0)
    height = int(info.get("height") or 0)
    if width < 450 or height < 300 or width * height < 180_000:
        return False
    ratio = width / max(height, 1)
    if ratio < 0.55 or ratio > 3.2:
        return False
    metadata = info.get("extmetadata") or {}
    description = clean_metadata(metadata.get("ImageDescription"))
    if any(part in description.lower() for part in REJECT_TEXT_PARTS):
        return False
    return True


def candidate_score(area_id: str, area_name: str, order: int, title: str, info: dict) -> tuple[float, int]:
    name = title.lower().replace("file:", "")
    metadata = info.get("extmetadata") or {}
    description = clean_metadata(metadata.get("ImageDescription")).lower()
    haystack = normalize_text(name + " " + description)
    score = 0.0
    id_norm = normalize_text(area_id)
    if "level" in name:
        score += 12
    if id_norm and id_norm in haystack:
        score += 10
    for token in re.findall(r"[0-9a-zA-Z]{4,}", unicodedata.normalize("NFKD", area_name).encode("ascii", "ignore").decode("ascii").lower()):
        if token in name or token in description:
            score += 3
    if any(word in name or word in description for word in ("hall", "room", "corridor", "office", "hotel", "parking", "tunnel", "station", "lobby", "workroom", "boiler", "tundra", "atrium", "basement", "road")):
        score += 7
    if "background" in name:
        score += 2
    width = int(info.get("width") or 0)
    height = int(info.get("height") or 0)
    score += min(width * height / 1_000_000.0, 4.0)
    return score, -order


def verify_magic(data: bytes, mime: str) -> None:
    if len(data) < 4096:
        raise RuntimeError("image payload is unexpectedly small")
    if mime == "image/jpeg" and not data.startswith(b"\xff\xd8"):
        raise RuntimeError("JPEG signature mismatch")
    if mime == "image/png" and not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("PNG signature mismatch")
    if mime == "image/webp" and not (data.startswith(b"RIFF") and data[8:12] == b"WEBP"):
        raise RuntimeError("WebP signature mismatch")


def attribution(info: dict) -> dict[str, str]:
    metadata = info.get("extmetadata") or {}
    fields = {
        "artist": clean_metadata(metadata.get("Artist")),
        "credit": clean_metadata(metadata.get("Credit")),
        "license": clean_metadata(metadata.get("LicenseShortName")),
        "usage_terms": clean_metadata(metadata.get("UsageTerms")),
        "attribution_required": clean_metadata(metadata.get("AttributionRequired")),
    }
    return {key: value for key, value in fields.items() if value}


def safe_area_slug(route_index: int, area_id: str) -> str:
    ascii_id = unicodedata.normalize("NFKD", area_id).encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^A-Za-z0-9]+", "_", ascii_id).strip("_").lower()
    return slug or f"route_{route_index:02d}"


def prepare_area(route_index: int, parent_level: int, area_id: str, area_name: str, area_type: str) -> dict[str, object]:
    page_title, resolution, candidates = resolve_page(area_id, area_name, area_type)
    area: dict[str, object] = {
        "route_index": route_index,
        "parent_level": parent_level,
        "area_id": area_id,
        "area_name": area_name,
        "area_type": area_type,
        "status": "missing_page",
        "resolution": resolution,
        "page_candidates": candidates,
        "images": [],
    }
    if not page_title:
        return area

    page_url = WIKI_ROOT + urllib.parse.quote(page_title.replace(" ", "_"), safe="_()-,.'")
    area["page_title"] = page_title
    area["page_url"] = page_url
    titles = page_image_titles(page_title)
    if not titles:
        area["status"] = "page_has_no_images"
        return area

    metadata = image_metadata(titles)
    ranked: list[tuple[tuple[float, int], int, str, dict]] = []
    for order, title in enumerate(titles):
        info = metadata.get(title)
        if not info or not usable_candidate(title, info):
            continue
        ranked.append((candidate_score(area_id, area_name, order, title, info), order, title, info))
    ranked.sort(key=lambda item: item[0], reverse=True)
    if not ranked:
        area["status"] = "no_usable_images"
        return area

    records: list[dict[str, object]] = []
    download_failures = 0
    slug = safe_area_slug(route_index, area_id)
    for _, _, title, info in ranked:
        if len(records) >= MAX_IMAGES_PER_AREA:
            break
        source_url = str(info.get("thumburl") or info.get("url") or "").strip()
        if not source_url:
            continue
        try:
            data, response_mime = request_bytes(source_url, referer=page_url)
            declared_mime = str(info.get("thumbmime") or info.get("mime") or response_mime).lower()
            mime = response_mime if response_mime in MIME_EXTENSIONS else declared_mime
            if mime not in MIME_EXTENSIONS:
                continue
            verify_magic(data, mime)
        except Exception as exc:
            download_failures += 1
            print(f"WARN Area {area_id}: skip {title}: {exc}")
            continue

        filename = f"area_{route_index:02d}_{slug}_fandom_{len(records) + 1:02d}{MIME_EXTENSIONS[mime]}"
        path = OUT_DIR / filename
        path.write_bytes(data)
        record: dict[str, object] = {
            "local_file": filename,
            "file_title": title,
            "page_url": page_url,
            "description_url": str(info.get("descriptionurl") or ""),
            "download_url": source_url,
            "mime": mime,
            "width": int(info.get("thumbwidth") or info.get("width") or 0),
            "height": int(info.get("thumbheight") or info.get("height") or 0),
            "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest(),
        }
        record.update(attribution(info))
        records.append(record)
        print(f"Area {area_id}: {title} -> {filename} ({len(data)} bytes)")

    area["images"] = records
    if records:
        area["status"] = "ok"
    elif download_failures:
        area["status"] = "image_download_failed"
    else:
        area["status"] = "no_usable_images"
    return area


def clean_generated_assets() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for pattern in ("level_*_fandom_*", "area_*_fandom_*"):
        for stale in OUT_DIR.glob(pattern):
            if stale.is_file():
                stale.unlink()
    if MANIFEST.exists():
        MANIFEST.unlink()


def main() -> None:
    route = load_route()
    clean_generated_assets()

    areas: dict[str, dict[str, object]] = {}
    missing: list[dict[str, object]] = []
    try:
        for route_index, (parent_level, area_id, area_name, area_type) in enumerate(route):
            area = prepare_area(route_index, parent_level, area_id, area_name, area_type)
            areas[area_id] = area
            images = area.get("images") or []
            if not images:
                missing.append(
                    {
                        "route_index": route_index,
                        "parent_level": parent_level,
                        "area_id": area_id,
                        "area_name": area_name,
                        "area_type": area_type,
                        "reason": area.get("status", "unknown"),
                        "page_title": area.get("page_title", ""),
                    }
                )
    except Exception:
        for generated in OUT_DIR.glob("area_*_fandom_*"):
            if generated.is_file():
                generated.unlink()
        raise

    manifest = {
        "source": "Backrooms Wiki | Fandom",
        "wiki_url": "https://backrooms.fandom.com/wiki/Backrooms_Wiki",
        "route_source": "patch-linear-sublevel-progression.py",
        "route_count": len(route),
        "selection": {
            "minimum_per_main_area": MIN_IMAGES_PER_MAIN_AREA,
            "maximum_per_area": MAX_IMAGES_PER_AREA,
            "thumbnail_width": THUMB_WIDTH,
            "policy": "Each campaign area uses only images from its own resolved Fandom page; missing areas fall back to their parent main Level at runtime.",
        },
        "areas": areas,
        "missing_areas": missing,
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print("FANDOM_SNAPSHOT_AREA_REPORT_BEGIN")
    for area_id, area in areas.items():
        count = len(area.get("images") or [])
        if count:
            print(
                f"AVAILABLE area={area_id} parent=L{area['parent_level']} type={area['area_type']} "
                f"images={count} page={area.get('page_title', '')}"
            )
    for item in missing:
        print(
            f"MISSING area={item['area_id']} parent=L{item['parent_level']} type={item['area_type']} "
            f"name={item['area_name']} reason={item['reason']} page={item.get('page_title', '')}"
        )
    print(
        f"SUMMARY total={len(route)} available={len(route) - len(missing)} missing={len(missing)} "
        f"non_main_missing={sum(1 for item in missing if item['area_type'] != 'MAIN')}"
    )
    print("FANDOM_SNAPSHOT_AREA_REPORT_END")

    bad_main = []
    for parent_level in range(7):
        area = areas.get(str(parent_level)) or {}
        count = len(area.get("images") or [])
        if count < MIN_IMAGES_PER_MAIN_AREA:
            bad_main.append(f"Level {parent_level}={count}")
    if bad_main:
        raise RuntimeError(
            "Parent main Levels must keep at least "
            f"{MIN_IMAGES_PER_MAIN_AREA} Fandom snapshots for fallback: {', '.join(bad_main)}"
        )


if __name__ == "__main__":
    main()
