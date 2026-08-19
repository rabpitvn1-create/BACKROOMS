from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "kai_snapshot_overlay_clean.webp"
TARGET = ROOT / "app/src/main/assets/kai_snapshot_overlay.webp"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

raw = SOURCE.read_bytes()
if len(raw) < 30 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
    raise RuntimeError("Clean Kai overlay is not a valid WebP file")

width = height = 0
if raw[12:16] == b"VP8X" and len(raw) >= 30:
    width = 1 + int.from_bytes(raw[24:27], "little")
    height = 1 + int.from_bytes(raw[27:30], "little")
if width and (width < 512 or height < 768):
    raise RuntimeError(f"Clean Kai overlay is too small: {width}x{height}")

TARGET.parent.mkdir(parents=True, exist_ok=True)
TARGET.write_bytes(raw)

main = MAIN.read_text(encoding="utf-8")
main = main.replace(
    ".snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;max-width:55%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;image-rendering:auto}",
    ".snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;max-width:55%;object-fit:contain;object-position:right bottom;z-index:5;pointer-events:none;display:block;opacity:1;visibility:visible;image-rendering:auto}",
)
main = main.replace(
    "kai.src='file:///android_asset/kai_snapshot_overlay.webp';",
    "kai.src='kai_snapshot_overlay.webp';",
)
MAIN.write_text(main, encoding="utf-8")

print(f"Final Kai overlay installed: {len(raw)} bytes, {width or '?'}x{height or '?'}; z-index forced above Snapshot background.")
