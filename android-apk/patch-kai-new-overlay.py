from pathlib import Path
import base64

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
ASSET = ASSETS / "Kai_new_overlay.png"
DATA = ROOT / "Kai_new_overlay.b64"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"

if ASSET.is_file() and ASSET.stat().st_size > 24:
    raw = ASSET.read_bytes()
else:
    if not DATA.is_file():
        raise RuntimeError("Kai_new_overlay payload missing: assets PNG or android-apk/Kai_new_overlay.b64")
    raw = base64.b64decode("".join(DATA.read_text(encoding="utf-8").split()))
    ASSET.write_bytes(raw)

if raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Kai_new_overlay.png is not a valid PNG")
width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 512 or height < 768:
    raise RuntimeError("Kai_new_overlay.png is too small: %sx%s" % (width, height))

for path in (MAIN, INDEX):
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    text = text.replace("BESTKAIV2.png", "Kai_new_overlay.png")
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
