from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
SNAPSHOT_DIR = ROOT / "app/src/main/assets/level_snapshots"

# Explicit user-provided Level 0 snapshot overrides sourced from the project's Google Drive.
# Keep validation local/offline so CI fails before packaging if any binary is missing or altered.
OVERRIDES = (
    ("level_0_1.webp", 29712, "4d54869e03962ccebde85d18eca451f234c80400e646c4a6029efb7775a0c187"),
    ("level_0_2.webp", 24070, "2931e27edbb7d2024b363d3fedffa6df4cf7a8df792e851ce71b2fa07007a749"),
    ("level_0_3.webp", 35232, "71aa383638e92fffd7a4174893c15ab434927814e8df676a512bfa504d85c935"),
    ("level_0_4.webp", 8774, "c54c73243ec2198bc8f2d52ae4482dbabcc1e7e02805fde530479039f5e30328"),
)

refs: list[str] = []
for name, expected_bytes, expected_sha in OVERRIDES:
    asset = SNAPSHOT_DIR / name
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError(f"Level 0 snapshot override missing or empty: {asset}")
    actual_bytes = asset.stat().st_size
    if actual_bytes != expected_bytes:
        raise RuntimeError(
            f"Level 0 snapshot override size mismatch: {asset} "
            f"expected={expected_bytes} actual={actual_bytes}"
        )
    actual_sha = hashlib.sha256(asset.read_bytes()).hexdigest()
    if actual_sha != expected_sha:
        raise RuntimeError(
            f"Level 0 snapshot override checksum mismatch: {asset} "
            f"expected={expected_sha} actual={actual_sha}"
        )
    refs.append(f"file:///android_asset/level_snapshots/{name}")

main = MAIN.read_text(encoding="utf-8")
anchor = "var genericFallbackRef="
anchor_count = main.count(anchor)
if anchor_count != 1:
    raise RuntimeError(
        "Level 0 snapshot override requires the base snapshot patch first: "
        f"expected 1 genericFallbackRef anchor, found {anchor_count}"
    )

start = main.index(anchor)
end = main.find(";", start)
if end < 0:
    raise RuntimeError("Level 0 snapshot override could not find genericFallbackRef terminator")

pool_js = "[" + ",".join("'" + ref + "'" for ref in refs) + "]"
injection = "pools['0']=" + pool_js + ";genericFallbackRef=pools['0'][0];"
if injection in main:
    raise RuntimeError("Level 0 snapshot override was already applied")

main = main[: end + 1] + injection + main[end + 1 :]
MAIN.write_text(main, encoding="utf-8")
print(
    "Google Drive Level 0 snapshot override enabled: "
    f"images={len(refs)}, rotation=5m, pool=0, generic_fallback=level_0_1.webp"
)
