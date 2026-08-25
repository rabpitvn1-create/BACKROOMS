#!/usr/bin/env python3
"""Download a small, attributed Level 0-6 snapshot pool from Backrooms Wiki Fandom.

The canonical Android build runs this before the Snapshot renderer patch. Images are
selected from each Level's own MediaWiki image list, filtered to avoid UI/badges,
downloaded into the APK assets directory, and described in a generated manifest.
"""

from __future__ import annotations

import hashlib
import html
import json
import re
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots"
MANIFEST = OUT_DIR / "fandom_manifest.json"
API = "https://backrooms.fandom.com/api.php"
WIKI_ROOT = "https://backrooms.fandom.com/wiki/"
USER_AGENT = "BACKROOMS-Android-SnapshotBuilder/1.0 (+https://github.com/rabpitvn1-create/BACKROOMS)"
LEVELS = {level: f"Level_{level}" for level in range(7)}
MIN_IMAGES_PER_LEVEL = 2
MAX_IMAGES_PER_LEVEL = 6
THUMB_WIDTH = 1280

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
            raise RuntimeError(f"Fandom page missing: {page_name}")
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


def candidate_score(level: int, order: int, title: str, info: dict) -> tuple[float, int]:
    name = title.lower().replace("file:", "")
    metadata = info.get("extmetadata") or {}
    description = clean_metadata(metadata.get("ImageDescription")).lower()
    score = 0.0
    if "level" in name:
        score += 18
    if re.search(rf"(?:^|[^0-9]){level}(?:[^0-9]|$)", name):
        score += 5
    if any(word in name or word in description for word in ("hall", "room", "corridor", "office", "hotel", "parking", "tunnel", "station", "lobby", "workroom", "boiler", "tundra")):
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


def prepare_level(level: int, page_name: str) -> list[dict[str, object]]:
    page_url = WIKI_ROOT + urllib.parse.quote(page_name, safe="_")
    titles = page_image_titles(page_name)
    metadata = image_metadata(titles)
    ranked: list[tuple[tuple[float, int], int, str, dict]] = []
    for order, title in enumerate(titles):
        info = metadata.get(title)
        if not info or not usable_candidate(title, info):
            continue
        ranked.append((candidate_score(level, order, title, info), order, title, info))
    ranked.sort(key=lambda item: item[0], reverse=True)

    records: list[dict[str, object]] = []
    for _, _, title, info in ranked:
        if len(records) >= MAX_IMAGES_PER_LEVEL:
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
            print(f"WARN Level {level}: skip {title}: {exc}")
            continue

        filename = f"level_{level}_fandom_{len(records) + 1:02d}{MIME_EXTENSIONS[mime]}"
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
        print(f"Level {level}: {title} -> {filename} ({len(data)} bytes)")

    if len(records) < MIN_IMAGES_PER_LEVEL:
        raise RuntimeError(
            f"Level {level} ({page_name}) produced only {len(records)} usable Fandom image(s); "
            f"need at least {MIN_IMAGES_PER_LEVEL}."
        )
    return records


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for stale in OUT_DIR.glob("level_*_fandom_*"):
        if stale.is_file():
            stale.unlink()
    if MANIFEST.exists():
        MANIFEST.unlink()

    levels: dict[str, list[dict[str, object]]] = {}
    try:
        for level, page_name in LEVELS.items():
            levels[str(level)] = prepare_level(level, page_name)
    except Exception:
        for generated in OUT_DIR.glob("level_*_fandom_*"):
            if generated.is_file():
                generated.unlink()
        raise

    manifest = {
        "source": "Backrooms Wiki | Fandom",
        "wiki_url": "https://backrooms.fandom.com/wiki/Backrooms_Wiki",
        "selection": {
            "minimum_per_level": MIN_IMAGES_PER_LEVEL,
            "maximum_per_level": MAX_IMAGES_PER_LEVEL,
            "thumbnail_width": THUMB_WIDTH,
            "policy": "Large non-UI images used on each matching Level page; no cross-level mixing.",
        },
        "levels": levels,
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    counts = ", ".join(f"L{level}={len(levels[str(level)])}" for level in LEVELS)
    print(f"Backrooms Fandom Level snapshot pools prepared: {counts}")


if __name__ == "__main__":
    main()
