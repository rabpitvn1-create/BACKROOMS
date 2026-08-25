from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
PNG = ASSETS / "BestKai.png"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

if not PNG.exists():
    raise RuntimeError("BestKai.png is missing")

raw = PNG.read_bytes()
if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai asset is not a valid PNG")

width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 683:
    raise RuntimeError(f"Kai PNG is too small: {width}x{height}")

main = MAIN.read_text(encoding="utf-8")
old = "kai_snapshot_overlay.webp"
new = "BestKai.png"
if old not in main and new not in main:
    raise RuntimeError("Kai Snapshot asset reference was not found in MainActivity.java")
main = main.replace(old, new)
MAIN.write_text(main, encoding="utf-8")

print(
    f"BestKai PNG selected for APK: {width}x{height}, {len(raw)} bytes, "
    f"SHA-256 {hashlib.sha256(raw).hexdigest()}"
)
