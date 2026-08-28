from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
NEW = ASSETS / "Kai_new_overlay.png"
BEST = ASSETS / "BESTKAIV2.png"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"
RETIRED = (
    ASSETS / "BestKai.png",
    ASSETS / "kai_snapshot_overlay.png",
    ASSETS / "kai_snapshot_overlay.webp",
    ROOT / "kai_snapshot_overlay_hd.webp",
)

source = NEW if NEW.is_file() and NEW.stat().st_size > 24 else BEST
if not source.is_file():
    raise RuntimeError("Kai overlay PNG is missing")

raw = source.read_bytes()
if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai overlay PNG is not a valid PNG")
width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 768:
    raise RuntimeError(f"Kai overlay PNG is too small: {width}x{height}")

ASSETS.mkdir(parents=True, exist_ok=True)
NEW.write_bytes(raw)
if BEST.resolve() != NEW.resolve():
    shutil.copyfile(NEW, BEST)

replacements = (
    "BESTKAIV2.png",
    "BestKai.png",
    "kai_snapshot_overlay.png",
    "kai_snapshot_overlay.webp",
)
for path in (MAIN, INDEX):
    text = path.read_text(encoding="utf-8")
    for old in replacements:
        text = text.replace(old, "Kai_new_overlay.png")
    path.write_text(text, encoding="utf-8")

for path in RETIRED:
    if path.exists():
        path.unlink()

combined = MAIN.read_text(encoding="utf-8") + INDEX.read_text(encoding="utf-8")
if "Kai_new_overlay.png" not in combined:
    raise RuntimeError("Kai_new_overlay.png is not referenced by the finalized runtime")
if "kai_snapshot_overlay.png" in combined or "kai_snapshot_overlay.webp" in combined:
    raise RuntimeError("Retired Kai overlay reference remains in finalized runtime")
if "BestKai.png" in combined:
    raise RuntimeError("Retired BestKai overlay reference remains in finalized runtime")
if any(path.exists() for path in RETIRED):
    raise RuntimeError("Retired Kai overlay asset remains packaged")
if not BEST.is_file() or BEST.stat().st_size <= 0:
    raise RuntimeError("BESTKAIV2.png compatibility copy missing")

print(
    f"Kai_new_overlay.png finalized as the default Kai overlay: {width}x{height}; "
    f"retired overlays removed."
)
