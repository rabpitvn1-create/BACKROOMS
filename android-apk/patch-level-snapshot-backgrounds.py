from pathlib import Path
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ROTATION = ROOT / "app/src/main/assets/level_snapshots/rotation"
FETCHER = ROOT / "fetch-level-snapshots.py"
main = MAIN.read_text(encoding="utf-8")

rotation_files = list(ROTATION.glob("level_*_*.*")) if ROTATION.exists() else []
if len(rotation_files) != 28:
    subprocess.run([sys.executable, str(FETCHER)], check=True)

refs = {}
for level in range(7):
    row = []
    for slot in range(1, 5):
        matches = sorted(ROTATION.glob(f"level_{level}_{slot}.*"))
        if len(matches) != 1:
            raise RuntimeError(
                f"Level {level} slot {slot}: expected exactly one fetched image, found {len(matches)}"
            )
        asset = matches[0]
        if asset.suffix.lower() not in {".jpg", ".png", ".gif", ".webp"}:
            raise RuntimeError(f"Unsupported snapshot asset type: {asset.name}")
        row.append(f"file:///android_asset/level_snapshots/rotation/{asset.name}")
    refs[level] = row

refs_js = json.dumps(refs, ensure_ascii=False, separators=(",", ":"))

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = (
    f"var refs={refs_js};"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;"
    "var seq=refs[lv]||refs[0];"
    "var snapshotTurn=Math.max(1,Number(state&&state.turn||1)||1);"
    "var snapshotSlot=Math.floor((snapshotTurn-1)/3)%seq.length;"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=seq[snapshotSlot];"
    "bg.alt='Level '+lv+' Snapshot '+(snapshotSlot+1)+' / 4';"
    "bg.onerror=function(){this.onerror=null;this.src=refs[0][0];};box.appendChild(bg);"
    "var kai=document.createElement('img');kai.className='snapshot-character';"
    "kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);"
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
print("Four local snapshots per Level 0-6 enabled; background rotates deterministically every three turns while Kai stays overlaid on top.")
