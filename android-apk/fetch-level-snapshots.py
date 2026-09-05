from __future__ import annotations

import hashlib
import json
import shutil
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "snapshot_sources.json"
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots/rotation"
MAX_BYTES = 12 * 1024 * 1024
USER_AGENT = "BACKROOMS-APK-SnapshotFetcher/1.0 (+https://github.com/rabpitvn1-create/BACKROOMS)"


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


def download(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise RuntimeError(f"image exceeds {MAX_BYTES} bytes")
    if len(data) < 1024:
        raise RuntimeError(f"image is unexpectedly small ({len(data)} bytes)")
    if image_extension(data) is None:
        raise RuntimeError("response is not a supported PNG/JPEG/GIF/WebP image")
    return data


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
            urls = image.get("urls")
            if not isinstance(urls, list) or not urls:
                raise RuntimeError(f"Level {level} slot {slot} has no download URL")

            data: bytes | None = None
            failures: list[str] = []
            chosen_url = ""
            for url in urls:
                try:
                    data = download(str(url))
                    chosen_url = str(url)
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
