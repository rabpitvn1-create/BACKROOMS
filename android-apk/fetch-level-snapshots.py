from __future__ import annotations

import hashlib
import json
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "snapshot_sources.json"
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots/rotation"
MAX_BYTES = 12 * 1024 * 1024
USER_AGENT = "BACKROOMS-APK-SnapshotFetcher/1.1 (+https://github.com/rabpitvn1-create/BACKROOMS)"
IMAGE_SUFFIXES = (".jpg", ".png", ".jpeg", ".webp")


def image_extension(data: bytes) -> str | None:
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return "gif"
    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def candidate_urls(url: str) -> list[str]:
    candidates = [url]
    path = urllib.parse.urlsplit(url).path.lower()
    if not path.endswith(IMAGE_SUFFIXES):
        candidates.extend(url + suffix for suffix in IMAGE_SUFFIXES)
    return candidates


def download_once(url: str, source_page: str = "") -> bytes:
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        "Accept-Encoding": "gzip, deflate",
    }
    if "upload.wikimedia.org" in url:
        headers["Referer"] = source_page or "https://commons.wikimedia.org/"
    elif "fandom.com" in url:
        headers["Referer"] = source_page or "https://backrooms.fandom.com/"
    elif "wikidot.com" in url or "wdfiles.com" in url:
        headers["Referer"] = source_page or "https://backrooms-wiki.wikidot.com/"

    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise RuntimeError(f"image exceeds {MAX_BYTES} bytes")
    if len(data) < 1024:
        raise RuntimeError(f"image is unexpectedly small ({len(data)} bytes)")
    if image_extension(data) is None:
        raise RuntimeError("response is not a supported PNG/JPEG/GIF/WebP image")
    return data


def retry_delay(error: urllib.error.HTTPError, attempt: int) -> int:
    retry_after = error.headers.get("Retry-After") if error.headers else None
    try:
        requested = int(retry_after or 0)
    except (TypeError, ValueError):
        requested = 0
    return min(60, max(attempt * 2, requested))


def download(url: str, source_page: str = "") -> bytes:
    last_error: BaseException | None = None
    for attempt in range(1, 4):
        try:
            return download_once(url, source_page)
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code == 429 and attempt < 3:
                time.sleep(retry_delay(exc, attempt))
                continue
            if exc.code < 500 or attempt == 3:
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError) as exc:
            last_error = exc
            if attempt == 3:
                raise
        time.sleep(attempt)
    assert last_error is not None
    raise last_error


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    levels = manifest.get("levels")
    if not isinstance(levels, list) or len(levels) != 7:
        raise RuntimeError("snapshot manifest must define exactly Level 0 through Level 6")

    expected_levels = list(range(7))
    actual_levels = [int(entry.get("level", -1)) for entry in levels]
    if actual_levels != expected_levels:
        raise RuntimeError(f"snapshot levels must be {expected_levels}, found {actual_levels}")

    if OUT_DIR.exists():
        shutil.rmtree(OUT_DIR)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    seen_hashes: dict[str, str] = {}
    downloaded = 0

    for level_entry in levels:
        level = int(level_entry["level"])
        images = level_entry.get("images")
        if not isinstance(images, list) or len(images) != 4:
            raise RuntimeError(f"Level {level} must define exactly four snapshots")

        slots = [int(image.get("slot", -1)) for image in images]
        if slots != [1, 2, 3, 4]:
            raise RuntimeError(f"Level {level} snapshot slots must be [1, 2, 3, 4], found {slots}")

        for image in images:
            slot = int(image["slot"])
            source_page = str(image.get("source_page") or "")
            urls = image.get("urls")
            if not isinstance(urls, list) or not urls:
                raise RuntimeError(f"Level {level} slot {slot} has no download URL")

            expanded_urls: list[str] = []
            for raw_url in urls:
                for candidate in candidate_urls(str(raw_url)):
                    if candidate not in expanded_urls:
                        expanded_urls.append(candidate)

            data: bytes | None = None
            failures: list[str] = []
            chosen_url = ""
            for url in expanded_urls:
                try:
                    data = download(url, source_page)
                    chosen_url = url
                    break
                except (OSError, RuntimeError, urllib.error.URLError, urllib.error.HTTPError) as exc:
                    failures.append(f"{url}: {exc}")

            if data is None:
                details = "\n  ".join(failures)
                raise RuntimeError(f"unable to fetch Level {level} slot {slot}:\n  {details}")

            digest = hashlib.sha256(data).hexdigest()
            label = f"Level {level} slot {slot}"
            if digest in seen_hashes:
                raise RuntimeError(f"duplicate snapshot bytes: {label} duplicates {seen_hashes[digest]}")
            seen_hashes[digest] = label

            ext = image_extension(data)
            assert ext is not None
            output = OUT_DIR / f"level_{level}_{slot}.{ext}"
            output.write_bytes(data)
            downloaded += 1
            print(f"{label}: {output.name} ({len(data)} bytes) <- {chosen_url}")

    if downloaded != 28:
        raise RuntimeError(f"expected 28 snapshots, downloaded {downloaded}")

    print(f"Fetched {downloaded} distinct Level snapshots into {OUT_DIR}.")


if __name__ == "__main__":
    main()
