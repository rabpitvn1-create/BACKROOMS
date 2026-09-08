from __future__ import annotations

import hashlib
import struct
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "app/src/main/assets/level_snapshots/rotation"
EXPECTED_SIZE = (768, 448)
MAX_BYTES = 2 * 1024 * 1024
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
USER_AGENT = "BACKROOMS-APK-Level0SnapshotFetcher/1.0 (+https://github.com/rabpitvn1-create/BACKROOMS)"

SOURCES = [
    {
        "name": "backrooms_level0_01_open_room_16bit.png",
        "file_id": "1zlwYYW1z4mOXT0d-ce-JZBLsB65vnvcD",
        "sha256": "d0a9d9fe641986b2c2121c89c3ae713b13f288160c8d6774aac8935099feba25",
    },
    {
        "name": "backrooms_level0_02_long_corridor_16bit.png",
        "file_id": "1Zd03AVNu4URBUG_FEi_D69yhCtJ_7-XU",
        "sha256": "cab2626d6c07a62b971cfa80a1d42ff96d3a3fad6422eea494f1b687eb1b6eb5",
    },
    {
        "name": "backrooms_level0_03_maze_junction_16bit.png",
        "file_id": "1M-r678UuEqU5w-24EAOOELU_Hb9th_vl",
        "sha256": "546abd384dc664ff0e43fad72c1c4021653b0d7463c0f72064c1eb964e3217ef",
    },
    {
        "name": "backrooms_level0_04_ceiling_corner_16bit.png",
        "file_id": "1R5S_ec0lm0TdOK0PoUA59B8dXzM3sTlq",
        "sha256": "6d1ca532d13254b86e76d4e59651154f0187f5aa91c63855cca6e6a84093663c",
    },
]


def png_dimensions(data: bytes) -> tuple[int, int]:
    if len(data) < 24 or not data.startswith(PNG_SIGNATURE) or data[12:16] != b"IHDR":
        raise RuntimeError("download is not a valid PNG with an IHDR header")
    return struct.unpack(">II", data[16:24])


def verify(data: bytes, source: dict[str, str]) -> str:
    if len(data) > MAX_BYTES:
        raise RuntimeError(f"{source['name']} is unexpectedly large: {len(data)} bytes")
    dimensions = png_dimensions(data)
    if dimensions != EXPECTED_SIZE:
        raise RuntimeError(
            f"{source['name']} must be {EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}, "
            f"found {dimensions[0]}x{dimensions[1]}"
        )
    digest = hashlib.sha256(data).hexdigest()
    if digest != source["sha256"]:
        raise RuntimeError(
            f"SHA-256 mismatch for {source['name']}: expected {source['sha256']}, found {digest}"
        )
    return digest


def candidate_urls(file_id: str) -> list[str]:
    query = urllib.parse.urlencode({"id": file_id, "export": "download", "confirm": "t"})
    return [
        f"https://drive.usercontent.google.com/download?{query}",
        f"https://drive.google.com/uc?{query}",
    ]


def download(source: dict[str, str]) -> bytes:
    failures: list[str] = []
    for url in candidate_urls(source["file_id"]):
        request = urllib.request.Request(
            url,
            headers={
                "User-Agent": USER_AGENT,
                "Accept": "image/png,image/*;q=0.8,*/*;q=0.5",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                content_type = (response.headers.get("Content-Type") or "").lower()
                data = response.read(MAX_BYTES + 1)
            if "text/html" in content_type:
                raise RuntimeError("Google Drive returned HTML instead of the image")
            verify(data, source)
            return data
        except (OSError, RuntimeError, urllib.error.URLError, urllib.error.HTTPError) as exc:
            failures.append(f"{url}: {exc}")
    details = "\n  ".join(failures)
    raise RuntimeError(f"unable to fetch {source['name']} from Google Drive:\n  {details}")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    seen_hashes: set[str] = set()

    for source in SOURCES:
        output = OUT_DIR / source["name"]
        if output.is_file():
            existing = output.read_bytes()
            try:
                digest = verify(existing, source)
                if digest in seen_hashes:
                    raise RuntimeError(f"duplicate Level 0 snapshot bytes for {source['name']}")
                seen_hashes.add(digest)
                print(f"Level 0 snapshot OK: {output.name} ({len(existing)} bytes)")
                continue
            except RuntimeError:
                pass

        data = download(source)
        digest = verify(data, source)
        if digest in seen_hashes:
            raise RuntimeError(f"duplicate Level 0 snapshot bytes for {source['name']}")
        seen_hashes.add(digest)

        temporary = output.with_suffix(output.suffix + ".tmp")
        temporary.write_bytes(data)
        temporary.replace(output)
        print(f"Fetched Level 0 snapshot: {output.name} ({len(data)} bytes)")

    if len(seen_hashes) != len(SOURCES):
        raise RuntimeError(f"expected {len(SOURCES)} distinct Level 0 snapshots, verified {len(seen_hashes)}")

    print(f"Verified {len(SOURCES)} user-provided Level 0 snapshots in {OUT_DIR}.")


if __name__ == "__main__":
    main()
