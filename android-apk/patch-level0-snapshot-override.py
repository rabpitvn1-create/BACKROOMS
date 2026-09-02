from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
SNAPSHOT_DIR = ROOT / "app/src/main/assets/level_snapshots"

# Explicit user-provided Level 0 snapshot overrides sourced from the project's Google Drive.
# Validate the packaged binaries locally/offline before wiring them into the runtime pool.
OVERRIDES = (
    "level_0_1.webp",
    "level_0_2.webp",
    "level_0_3.webp",
    "level_0_4.webp",
)

refs: list[str] = []
for name in OVERRIDES:
    asset = SNAPSHOT_DIR / name
    if not asset.is_file() or asset.stat().st_size <= 1024:
        raise RuntimeError(f"Level 0 snapshot override missing or unexpectedly small: {asset}")
    data = asset.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise RuntimeError(f"Level 0 snapshot override is not a valid WebP container: {asset}")
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
