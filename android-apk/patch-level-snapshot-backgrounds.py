from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FETCHER = ROOT / "fetch-level0-drive-snapshots.py"
LEVEL0_DIR = ROOT / "app/src/main/assets/level_snapshots/rotation"
LEVEL0_ASSETS = [
    "backrooms_level0_01_open_room_16bit.png",
    "backrooms_level0_02_long_corridor_16bit.png",
    "backrooms_level0_03_maze_junction_16bit.png",
    "backrooms_level0_04_ceiling_corner_16bit.png",
]

subprocess.run([sys.executable, str(FETCHER)], check=True)
missing = [name for name in LEVEL0_ASSETS if not (LEVEL0_DIR / name).is_file()]
if missing:
    raise RuntimeError(f"Missing Level 0 snapshot assets after fetch: {', '.join(missing)}")

main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = (
    "var refs={"
    "0:['file:///android_asset/level_snapshots/rotation/backrooms_level0_01_open_room_16bit.png','file:///android_asset/level_snapshots/rotation/backrooms_level0_02_long_corridor_16bit.png','file:///android_asset/level_snapshots/rotation/backrooms_level0_03_maze_junction_16bit.png','file:///android_asset/level_snapshots/rotation/backrooms_level0_04_ceiling_corner_16bit.png'],"
    "1:['file:///android_asset/level_snapshots/level_1.webp'],2:['file:///android_asset/level_snapshots/level_2.webp'],3:['file:///android_asset/level_snapshots/level_3.webp'],4:['file:///android_asset/level_snapshots/level_4.webp'],5:['file:///android_asset/level_snapshots/level_5.webp'],6:['file:///android_asset/level_snapshots/level_6.webp']};"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;"
    "var seq=refs[lv]||refs[0];var snapshotTurn=Math.max(1,Number(state&&state.turn||1)||1);"
    "var snapshotSlot=lv===0?Math.floor((snapshotTurn-1)/3)%seq.length:0;"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:seq[snapshotSlot];"
    "bg.alt=r?'Snapshot Turn '+(state.turn||''):('Level '+lv+' Snapshot '+(snapshotSlot+1)+' / '+seq.length);"
    "if(!r)bg.onerror=function(){this.onerror=null;this.src='file:///android_asset/level_snapshots/level_'+lv+'.webp';};box.appendChild(bg);"
    "var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"
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
print(
    "Four user-provided Level 0 snapshots enabled with a three-turn rotation; "
    "Levels 1-6 keep their packaged fallback and Gemini snapshots still take precedence."
)
