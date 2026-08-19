from pathlib import Path
import base64
import hashlib
import re

ROOT = Path(__file__).resolve().parent
PARTS_DIR = ROOT / "kai-overlay-clean-parts"
TARGET = ROOT / "app/src/main/assets/kai_snapshot_overlay.webp"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

EXPECTED_PARTS = 12
EXPECTED_BYTES = 90736
EXPECTED_SHA256 = "071d4f966dfd1e3986323d1d55967fc53870533e58b05bc930fb2a38ea13531c"
EXPECTED_WIDTH = 512
EXPECTED_HEIGHT = 768

parts = sorted(PARTS_DIR.glob("part*.b64"))
if len(parts) != EXPECTED_PARTS:
    raise RuntimeError(f"Kai overlay: expected {EXPECTED_PARTS} parts, found {len(parts)}")

encoded = "".join(p.read_text(encoding="ascii").strip() for p in parts)
raw = base64.b64decode(encoded, validate=True)
sha = hashlib.sha256(raw).hexdigest()

if len(raw) != EXPECTED_BYTES:
    raise RuntimeError(f"Kai overlay: wrong size {len(raw)}, expected {EXPECTED_BYTES}")
if sha != EXPECTED_SHA256:
    raise RuntimeError(f"Kai overlay: SHA-256 mismatch {sha}")
if raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
    raise RuntimeError("Kai overlay: reconstructed file is not a valid WebP")

width = height = 0
if raw[12:16] == b"VP8X" and len(raw) >= 30:
    width = 1 + int.from_bytes(raw[24:27], "little")
    height = 1 + int.from_bytes(raw[27:30], "little")
    if (width, height) != (EXPECTED_WIDTH, EXPECTED_HEIGHT):
        raise RuntimeError(f"Kai overlay: wrong dimensions {width}x{height}")

TARGET.parent.mkdir(parents=True, exist_ok=True)
TARGET.write_bytes(raw)

main = MAIN.read_text(encoding="utf-8")
main = main.replace("kai.src='file:///android_asset/kai_snapshot_overlay.webp';", "kai.src='kai_snapshot_overlay.webp';")
main = main.replace('kai.src="file:///android_asset/kai_snapshot_overlay.webp";', 'kai.src="kai_snapshot_overlay.webp";')

css = (
    ".snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;"
    "max-width:55%;object-fit:contain;object-position:right bottom;z-index:5;pointer-events:none;"
    "display:block;opacity:1;visibility:visible;image-rendering:auto}"
)
main, n = re.subn(r"\.snapshot \.snapshot-character\{[^}]*\}", css, main)
if n < 1:
    raise RuntimeError("Kai overlay: snapshot-character CSS rule not found")

MAIN.write_text(main, encoding="utf-8")
print(f"Kai overlay verified and installed: {len(raw)} bytes, SHA-256 {sha}, {width or '?'}x{height or '?'}")
