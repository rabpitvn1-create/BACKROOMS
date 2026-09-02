from __future__ import annotations

from pathlib import Path
import hashlib
import os
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent
DEST = ROOT / "app/src/main/res/raw/backroom_bgm.m4a"
TMP = DEST.with_suffix(".m4a.tmp")
FILE_ID = "1oLzs8RtsSIbzjjlYG1ywG5jgZI-xtJtB"
EXPECTED_BYTES = 4_063_885
EXPECTED_SHA256 = "f9eca6ee4c8618d310296b19b0f919c50b0e6c85b6d55f73753cce83bef3cce2"
URLS = [
    f"https://drive.usercontent.google.com/download?id={FILE_ID}&export=download&confirm=t",
    f"https://drive.google.com/uc?export=download&confirm=t&id={FILE_ID}",
]


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def valid(path: Path) -> bool:
    return path.is_file() and path.stat().st_size == EXPECTED_BYTES and digest(path) == EXPECTED_SHA256


if valid(DEST):
    print(f"Background music already pinned: {DEST} ({EXPECTED_BYTES} bytes)")
    raise SystemExit(0)

DEST.parent.mkdir(parents=True, exist_ok=True)
TMP.unlink(missing_ok=True)
last_error: Exception | None = None

for url in URLS:
    try:
        request = urllib.request.Request(
            url,
            headers={"User-Agent": "Mozilla/5.0 BACKROOMS-CI/1.0", "Accept": "audio/*,*/*;q=0.8"},
        )
        with urllib.request.urlopen(request, timeout=90) as response, TMP.open("wb") as output:
            content_type = (response.headers.get("Content-Type") or "").lower()
            if "text/html" in content_type:
                raise RuntimeError(f"Google Drive returned HTML instead of audio: {content_type}")
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
        if valid(TMP):
            os.replace(TMP, DEST)
            print(f"Pinned background music downloaded: {DEST} ({EXPECTED_BYTES} bytes, sha256={EXPECTED_SHA256})")
            raise SystemExit(0)
        actual_size = TMP.stat().st_size if TMP.exists() else 0
        actual_sha = digest(TMP) if TMP.exists() and actual_size else "empty"
        raise RuntimeError(f"download mismatch: bytes={actual_size}, sha256={actual_sha}")
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, RuntimeError, OSError) as error:
        last_error = error
        TMP.unlink(missing_ok=True)

raise SystemExit(f"Unable to fetch pinned background music: {type(last_error).__name__}: {last_error}")
