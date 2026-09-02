from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / "android-apk/patch-level-snapshot-backgrounds.py"
SOURCES = ROOT / "android-apk/app/src/main/assets/level_snapshots/SOURCES.md"
SNAPSHOT_DIR = ROOT / "android-apk/app/src/main/assets/level_snapshots"

EPSILON_ORIGINALS = (
    ("level_epsilon_1.webp", 1647706, "a7270abc3995d7944ae87101e584b5af1d78edfd54f55ae8ee644d14957f0452"),
    ("level_epsilon_2.webp", 1598158, "52fcd023419ae4b1df1d957309479a73a71672184a457deb71b5f6121acf4a23"),
    ("level_epsilon_3.webp", 1579430, "f1c1574ba1402b690c565c3d9ab3ea46694a37a2077235d855644418643ed5e7"),
    ("level_epsilon_4.webp", 605806, "6b3f541dbfbc89b604ab187656666111a4b845fc95bcd026762721cad0cec082"),
)

for name, expected_bytes, expected_sha in EPSILON_ORIGINALS:
    asset = SNAPSHOT_DIR / name
    if not asset.is_file():
        raise SystemExit(f"Missing downloaded epsilon snapshot: {asset}")
    data = asset.read_bytes()
    if len(data) != expected_bytes:
        raise SystemExit(f"epsilon snapshot size mismatch: {name} expected={expected_bytes} actual={len(data)}")
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise SystemExit(f"epsilon snapshot is not WebP: {name}")
    actual_sha = hashlib.sha256(data).hexdigest()
    if actual_sha != expected_sha:
        raise SystemExit(f"epsilon snapshot checksum mismatch: {name} expected={expected_sha} actual={actual_sha}")

patch = PATCH.read_text(encoding="utf-8")
old_constant = '''# Exact byte-for-byte Level 0 originals from the project's Google Drive.
# Do not resize, crop, recompress, or re-encode these files. CI hard-locks both size and SHA-256.
LEVEL0_ORIGINALS = (
    ("level_0_1.webp", 217992, "619a3a035d6ba4e5c9fa8301e57e6b8d643272d52e4ff86105f8c3a517a84762"),
    ("level_0_2.webp", 145638, "34da3b0d0f1914be884cad5d388bbe8c263050e48f63c1656861558e9e5961fb"),
    ("level_0_3.webp", 268618, "7e8cfe0a09b30d932df5a37748c5be51517b51011f320e0835338ab0249e4696"),
    ("level_0_4.webp", 212864, "2b0dbbe6c8f70c5722b97578ebf98207f6ea81b9985afaed2c2f96818ce5cf87"),
)
'''
new_constant = '''# Exact byte-for-byte originals explicitly supplied from the project's Google Drive.
# Do not resize, crop, recompress, or re-encode these files. CI hard-locks both size and SHA-256.
ORIGINAL_QUALITY_OVERRIDES = {
    "0": (
        ("level_0_1.webp", 217992, "619a3a035d6ba4e5c9fa8301e57e6b8d643272d52e4ff86105f8c3a517a84762"),
        ("level_0_2.webp", 145638, "34da3b0d0f1914be884cad5d388bbe8c263050e48f63c1656861558e9e5961fb"),
        ("level_0_3.webp", 268618, "7e8cfe0a09b30d932df5a37748c5be51517b51011f320e0835338ab0249e4696"),
        ("level_0_4.webp", 212864, "2b0dbbe6c8f70c5722b97578ebf98207f6ea81b9985afaed2c2f96818ce5cf87"),
    ),
    "epsilon": (
        ("level_epsilon_1.webp", 1647706, "a7270abc3995d7944ae87101e584b5af1d78edfd54f55ae8ee644d14957f0452"),
        ("level_epsilon_2.webp", 1598158, "52fcd023419ae4b1df1d957309479a73a71672184a457deb71b5f6121acf4a23"),
        ("level_epsilon_3.webp", 1579430, "f1c1574ba1402b690c565c3d9ab3ea46694a37a2077235d855644418643ed5e7"),
        ("level_epsilon_4.webp", 605806, "6b3f541dbfbc89b604ab187656666111a4b845fc95bcd026762721cad0cec082"),
    ),
}
'''
if patch.count(old_constant) != 1:
    raise SystemExit("Level 0 original-quality constant anchor changed")
patch = patch.replace(old_constant, new_constant, 1)

replacements = (
    ('    if area_id == "0":\n', '    if area_id in ORIGINAL_QUALITY_OVERRIDES:\n'),
    ('        for name, expected_bytes, expected_sha in LEVEL0_ORIGINALS:\n', '        for name, expected_bytes, expected_sha in ORIGINAL_QUALITY_OVERRIDES[area_id]:\n'),
    ('f"Original Level 0 snapshot size mismatch: {asset} "', 'f"Original-quality snapshot size mismatch area={area_id}: {asset} "'),
    ('raise RuntimeError(f"Original Level 0 snapshot is not a WebP container: {asset}")', 'raise RuntimeError(f"Original-quality snapshot is not a WebP container area={area_id}: {asset}")'),
    ('f"Original Level 0 snapshot checksum mismatch: {asset} "', 'f"Original-quality snapshot checksum mismatch area={area_id}: {asset} "'),
)
for old, new in replacements:
    if patch.count(old) != 1:
        raise SystemExit(f"Snapshot patch anchor changed: {old!r}")
    patch = patch.replace(old, new, 1)
PATCH.write_text(patch, encoding="utf-8")

sources = SOURCES.read_text(encoding="utf-8")
section = '''\n## Epsilon original-quality override\n\nArea `epsilon` (`Incessant Hum-Buzz`) uses `level_epsilon_1.webp` through `level_epsilon_4.webp` copied byte-for-byte from the project Google Drive folder `Backrooms Level`. All four are 1672x941 WebP originals. Their exact sizes and SHA-256 values are hard-locked in `patch-level-snapshot-backgrounds.py`. The former `area_01_epsilon_trusted_01.webp` through `area_01_epsilon_trusted_04.webp` parent-fallback assets are removed after replacement.\n'''
if "## Epsilon original-quality override" not in sources:
    SOURCES.write_text(sources.rstrip() + "\n" + section, encoding="utf-8")

print("epsilon original-quality snapshot metadata updated")
