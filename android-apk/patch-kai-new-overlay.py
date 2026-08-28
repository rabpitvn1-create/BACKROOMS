#!/usr/bin/env python3
"""Install Kai_new_overlay.png as the default Kai snapshot overlay.

Looks for payload in this order:
  1. already-written app/src/main/assets/Kai_new_overlay.png
  2. sibling Kai_new_overlay.png (next to this script)
  3. Kai_new_overlay.b64
  4. Kai_new_overlay.b64.* parts, sorted
  5. existing assets/BESTKAIV2.png (CI fallback)

Writes both Kai_new_overlay.png and a BESTKAIV2.png copy for CI, then remaps
legacy overlay filenames in MainActivity.java and index.html.
MadGod overlay logic is left untouched.
"""
from pathlib import Path
import base64
import shutil

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
TARGET = ASSETS / "Kai_new_overlay.png"
LEGACY = ASSETS / "BESTKAIV2.png"
SIBLING = ROOT / "Kai_new_overlay.png"
DATA = ROOT / "Kai_new_overlay.b64"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"


def load_payload() -> bytes:
    if TARGET.is_file() and TARGET.stat().st_size > 24:
        return TARGET.read_bytes()
    if SIBLING.is_file() and SIBLING.stat().st_size > 24:
        return SIBLING.read_bytes()
    if DATA.is_file() and DATA.stat().st_size > 24:
        return base64.b64decode("".join(DATA.read_text(encoding="utf-8").split()))
    parts = sorted(p for p in ROOT.glob("Kai_new_overlay.b64.*") if p.is_file() and p.stat().st_size > 0)
    if parts:
        return base64.b64decode("".join(p.read_text(encoding="ascii").strip() for p in parts))
    if LEGACY.is_file() and LEGACY.stat().st_size > 24:
        print("WARNING: Kai_new_overlay payload missing; falling back to BESTKAIV2.png")
        return LEGACY.read_bytes()
    raise RuntimeError(
        "Kai_new_overlay payload missing. Place Kai_new_overlay.png or "
        "Kai_new_overlay.b64 / .b64.* next to this patch."
    )


raw = load_payload()
if raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai_new_overlay payload is not a valid PNG")
width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 768:
    raise RuntimeError(f"Kai_new_overlay.png is too small: {width}x{height}")

ASSETS.mkdir(parents=True, exist_ok=True)
TARGET.write_bytes(raw)
if LEGACY.resolve() != TARGET.resolve():
    shutil.copyfile(TARGET, LEGACY)

replacements = (
    "BESTKAIV2.png",
    "BestKai.png",
    "kai_snapshot_overlay.png",
    "kai_snapshot_overlay.webp",
)
for path in (MAIN, INDEX):
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    for old in replacements:
        text = text.replace(old, "Kai_new_overlay.png")
    path.write_text(text, encoding="utf-8")

print(
    f"Installed default Kai snapshot overlay Kai_new_overlay.png "
    f"({width}x{height}, {len(raw)} bytes)"
)
