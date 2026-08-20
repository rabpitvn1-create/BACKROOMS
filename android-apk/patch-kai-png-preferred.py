from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
PNG = ASSETS / "kai_snapshot_overlay.png"
WEBP = ASSETS / "kai_snapshot_overlay.webp"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# This patch is deliberately a no-op until the real PNG is committed to assets.
# That keeps the currently-green APK build stable while allowing the PNG to take
# over automatically as soon as it exists in the repository.
if not PNG.exists():
    print("Kai PNG not present; keeping packaged WebP fallback.")
    raise SystemExit(0)

raw = PNG.read_bytes()
if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai asset is not a valid PNG")

width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 683:
    raise RuntimeError(f"Kai PNG is too small: {width}x{height}")

main = MAIN.read_text(encoding="utf-8")
old = "kai_snapshot_overlay.webp"
new = "kai_snapshot_overlay.png"
if old not in main and new not in main:
    raise RuntimeError("Kai Snapshot asset reference was not found in MainActivity.java")
main = main.replace(old, new)
MAIN.write_text(main, encoding="utf-8")

# Do not package two Kai overlays once the PNG is available.
if WEBP.exists():
    WEBP.unlink()

print(
    f"Kai PNG selected for APK: {width}x{height}, {len(raw)} bytes, "
    f"SHA-256 {hashlib.sha256(raw).hexdigest()}"
)
