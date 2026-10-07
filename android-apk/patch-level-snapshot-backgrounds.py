from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSET_DIR = ROOT / "app/src/main/assets/level_snapshots"

LEVEL_SNAPSHOTS = {
    0: [
        "level_0.webp",
        "level_00_snapshot_002.webp",
        "level_00_snapshot_003.webp",
        "level_00_snapshot_004.webp",
        "level_00_snapshot_005.webp",
        "level_00_snapshot_006.webp",
        "level_00_snapshot_007.webp",
        "level_00_liminal_hall.webp",
    ],
    1: ["level_1.webp", "level_01_snapshot_002.webp", "level_01_snapshot_003.webp"],
    2: ["level_2.webp", "level_02_snapshot_002.webp", "level_02_snapshot_003.webp"],
    3: ["level_3.webp", "level_03_snapshot_002.webp", "level_03_snapshot_003.webp"],
    4: ["level_4.webp", "level_04_snapshot_002.webp", "level_04_snapshot_003.webp"],
    5: ["level_5.webp", "level_05_snapshot_002.webp", "level_05_snapshot_003.webp"],
    6: ["level_6.webp", "level_06_snapshot_002.webp", "level_06_snapshot_003.webp"],
}

for level, names in LEVEL_SNAPSHOTS.items():
    if not names:
        raise RuntimeError(f"Level {level} snapshot pool is empty")
    for name in names:
        raw = (ASSET_DIR / name).read_bytes()
        if len(raw) < 12 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
            raise RuntimeError(f"Invalid packaged WebP: {name}")

main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

refs_js = "{" + ",".join(
    f"{level}:[" + ",".join(f"'file:///android_asset/level_snapshots/{name}'" for name in names) + "]"
    for level, names in LEVEL_SNAPSHOTS.items()
) + "}"

new = (
    "var refs=" + refs_js + ";"
    "var structuredLevel=state&&state.level&&state.level.number;"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level[^0-9]*([0-6])/i);"
    "var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
    "var pool=refs[lv]||refs[0];"
    "var levelTurn=Number(state&&state.flags&&state.flags.exploration&&state.flags.exploration.levelTurns);"
    "var frame=isFinite(levelTurn)&&levelTurn>=0?Math.floor(levelTurn):Math.max(0,Math.floor(Number(state&&state.turn||1)-1));"
    "var fallback=pool[frame%pool.length]||refs[0][0];"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:fallback;"
    "bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' local snapshot';"
    "if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0][0];};"
    "box.appendChild(bg);"
    "var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);"
    "if(!r){"
)

count = main.count(old)
if count != 1:
    raise RuntimeError(f"Snapshot layered renderer anchor: expected 1 match, found {count}")
main = main.replace(old, new, 1)

old_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot-placeholder{display:none}"
count = main.count(old_css)
if count != 1:
    raise RuntimeError(f"Snapshot placeholder style: expected 1 match, found {count}")
main = main.replace(old_css, new_css, 1)

MAIN.write_text(main, encoding="utf-8")
print("Local Google Drive backgrounds for Level 0-6 enabled inside Snapshot; Kai stays overlaid on top.")
