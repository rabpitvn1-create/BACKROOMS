from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSET = ROOT / "app/src/main/assets/entity/Newviolet.png"

main = MAIN.read_text(encoding="utf-8")
old_mapping = '("violet_warden".equals(entityKey) ? "Violet.png" : entityKey + ".png")'
new_mapping = '("violet_warden".equals(entityKey) ? "Newviolet.png" : entityKey + ".png")'

if new_mapping not in main:
    if old_mapping not in main:
        raise RuntimeError("Violet display mapping anchor missing from finalized MainActivity")
    main = main.replace(old_mapping, new_mapping, 1)

MAIN.write_text(main, encoding="utf-8")

final = MAIN.read_text(encoding="utf-8")
if new_mapping not in final:
    raise RuntimeError("Violet finalized runtime does not point to Newviolet.png")
if not ASSET.is_file() or ASSET.stat().st_size <= 0:
    raise RuntimeError("Violet display asset missing: android-apk/app/src/main/assets/entity/Newviolet.png")
raw = ASSET.read_bytes()
if raw[:8] != b'\x89PNG\r\n\x1a\n':
    raise RuntimeError("Newviolet.png is not a valid PNG asset")
if b'data:image' in raw[:1024].lower() or b'base64,' in raw[:1024].lower():
    raise RuntimeError("Newviolet.png must remain a raw PNG asset")

print("Violet display finalized: file:///android_asset/entity/Newviolet.png")
