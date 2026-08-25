from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
BEST = ASSETS / "BESTKAIV2.png"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"
RETIRED = (
    ASSETS / "BestKai.png",
    ASSETS / "kai_snapshot_overlay.png",
    ASSETS / "kai_snapshot_overlay.webp",
    ROOT / "kai_snapshot_overlay_hd.webp",
)

raw = BEST.read_bytes()
if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("BESTKAIV2.png is not a valid PNG")
width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 768:
    raise RuntimeError(f"BESTKAIV2.png is too small: {width}x{height}")

for path in (MAIN, INDEX):
    text = path.read_text(encoding="utf-8")
    text = text.replace("kai_snapshot_overlay.png", "BESTKAIV2.png")
    text = text.replace("kai_snapshot_overlay.webp", "BESTKAIV2.png")
    path.write_text(text, encoding="utf-8")

for path in RETIRED:
    if path.exists():
        path.unlink()

combined = MAIN.read_text(encoding="utf-8") + INDEX.read_text(encoding="utf-8")
if "BESTKAIV2.png" not in combined:
    raise RuntimeError("BESTKAIV2.png is not referenced by the finalized runtime")
if "kai_snapshot_overlay.png" in combined or "kai_snapshot_overlay.webp" in combined:
    raise RuntimeError("Retired Kai overlay reference remains in finalized runtime")
if any(path.exists() for path in RETIRED):
    raise RuntimeError("Retired Kai overlay asset remains packaged")

print(f"BestKai finalized as the default Kai overlay: {width}x{height}; retired overlays removed.")
