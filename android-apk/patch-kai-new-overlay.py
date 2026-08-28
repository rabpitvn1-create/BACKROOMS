from pathlib import Path
import base64
import shutil

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
ASSET = ASSETS / "Kai_new_overlay.png"
LEGACY = ASSETS / "BESTKAIV2.png"
DATA = ROOT / "Kai_new_overlay.b64"
PARTS = [ROOT / "Kai_new_overlay.b64.1", ROOT / "Kai_new_overlay.b64.2"]
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"

def load_payload() -> bytes:
    if ASSET.is_file() and ASSET.stat().st_size > 24:
        return ASSET.read_bytes()
    if DATA.is_file():
        return base64.b64decode("".join(DATA.read_text(encoding="utf-8").split()))
    if all(p.is_file() for p in PARTS):
        return base64.b64decode("".join(p.read_text(encoding="ascii").strip() for p in PARTS))
    raise RuntimeError("Kai_new_overlay payload missing: assets PNG or android-apk/Kai_new_overlay.b64")

raw = load_payload()
if raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai_new_overlay.png is not a valid PNG")
width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 768:
    raise RuntimeError("Kai_new_overlay.png is too small: %sx%s" % (width, height))

ASSETS.mkdir(parents=True, exist_ok=True)
ASSET.write_bytes(raw)
shutil.copyfile(ASSET, LEGACY)

for path in (MAIN, INDEX):
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    for old in ("BESTKAIV2.png", "BestKai.png", "kai_snapshot_overlay.png", "kai_snapshot_overlay.webp"):
        text = text.replace(old, "Kai_new_overlay.png")
    path.write_text(text, encoding="utf-8")

combined = ""
if MAIN.exists():
    combined += MAIN.read_text(encoding="utf-8")
if INDEX.exists():
    combined += INDEX.read_text(encoding="utf-8")
if "Kai_new_overlay.png" not in combined:
    raise RuntimeError("Kai_new_overlay.png is not referenced by the snapshot runtime")
if "BESTKAIV2.png" in combined:
    raise RuntimeError("Old BESTKAIV2.png snapshot overlay reference remains")

print("Kai snapshot overlay replaced: Kai_new_overlay.png %sx%s, %s bytes" % (width, height, len(raw)))
