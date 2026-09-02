from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / "android-apk/patch-level-snapshot-backgrounds.py"
SOURCES = ROOT / "android-apk/app/src/main/assets/level_snapshots/SOURCES.md"
SNAPSHOT_DIR = ROOT / "android-apk/app/src/main/assets/level_snapshots"

LEVEL_0_01_ORIGINALS = (
    ("level_0.01_1.webp", 535304, "59d07f7e8664cf4e24e8e5c95c1ad5d91fbc319af5c6a5a74d5696b8e609d5e9"),
    ("level_0.01_2.webp", 534180, "cdefc4ece01a7269e7424acd159f25cf0d934d849ef472889aec49abf835da5b"),
    ("level_0.01_3.webp", 556052, "c03ff0963fb81b9908c847ae4031549a2764fb1bb73fb52702ca3a89b91bdee0"),
    ("level_0.01_4.webp", 450190, "89da48027b7faba019b9d9450ada63b83776a6a61d79e2baefbc1ba6ae26aee2"),
)

OLD_ASSETS = tuple(
    SNAPSHOT_DIR / f"area_02_0_01_trusted_{index:02d}.webp"
    for index in range(1, 5)
)

for asset in OLD_ASSETS:
    if not asset.is_file():
        raise SystemExit(f"Expected former Level 0.01 snapshot is missing before replacement: {asset}")

for name, expected_bytes, expected_sha in LEVEL_0_01_ORIGINALS:
    asset = SNAPSHOT_DIR / name
    if not asset.is_file():
        raise SystemExit(f"Missing downloaded Level 0.01 snapshot: {asset}")
    data = asset.read_bytes()
    if len(data) != expected_bytes:
        raise SystemExit(
            f"Level 0.01 snapshot size mismatch: {name} expected={expected_bytes} actual={len(data)}"
        )
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise SystemExit(f"Level 0.01 snapshot is not WebP: {name}")
    actual_sha = hashlib.sha256(data).hexdigest()
    if actual_sha != expected_sha:
        raise SystemExit(
            f"Level 0.01 snapshot checksum mismatch: {name} expected={expected_sha} actual={actual_sha}"
        )

patch = PATCH.read_text(encoding="utf-8")
if '    "0.01": (' in patch:
    raise SystemExit("Level 0.01 original-quality override already exists")
anchor = '    "epsilon": (\n'
if patch.count(anchor) != 1:
    raise SystemExit("Snapshot override table epsilon anchor changed")
entry = '''    "0.01": (
        ("level_0.01_1.webp", 535304, "59d07f7e8664cf4e24e8e5c95c1ad5d91fbc319af5c6a5a74d5696b8e609d5e9"),
        ("level_0.01_2.webp", 534180, "cdefc4ece01a7269e7424acd159f25cf0d934d849ef472889aec49abf835da5b"),
        ("level_0.01_3.webp", 556052, "c03ff0963fb81b9908c847ae4031549a2764fb1bb73fb52702ca3a89b91bdee0"),
        ("level_0.01_4.webp", 450190, "89da48027b7faba019b9d9450ada63b83776a6a61d79e2baefbc1ba6ae26aee2"),
    ),
'''
patch = patch.replace(anchor, entry + anchor, 1)
PATCH.write_text(patch, encoding="utf-8")

sources = SOURCES.read_text(encoding="utf-8")
heading = "## Level 0.01 original-quality override"
if heading in sources:
    raise SystemExit("Level 0.01 source documentation already exists")
section = '''
## Level 0.01 original-quality override

Area `0.01` (`The Exit ?`) uses `level_0.01_1.webp` through `level_0.01_4.webp` copied byte-for-byte from the project Google Drive folder `Backrooms Level`. All four are 1672x941 WebP originals. Their exact sizes and SHA-256 values are hard-locked in `patch-level-snapshot-backgrounds.py`. The former `area_02_0_01_trusted_01.webp` through `area_02_0_01_trusted_04.webp` assets are removed after replacement.

`fandom_manifest.json` retains the historical Level 0.01 Fandom records for provenance, while runtime loading uses `ORIGINAL_QUALITY_OVERRIDES["0.01"]` as the authoritative packaged pool.
'''
SOURCES.write_text(sources.rstrip() + "\n\n" + section.lstrip(), encoding="utf-8")

print("Level 0.01 original-quality snapshot metadata updated")
