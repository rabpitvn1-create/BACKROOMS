from pathlib import Path
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ROTATION = ROOT / "app/src/main/assets/level_snapshots/rotation"
FETCHER = ROOT / "fetch-level-snapshots.py"
SUBLEVEL_MANIFEST = ROOT / "sublevel_snapshot_sources.json"
main = MAIN.read_text(encoding="utf-8")

sublevel_manifest = json.loads(SUBLEVEL_MANIFEST.read_text(encoding="utf-8"))
sublevel_entries = sublevel_manifest.get("sublevels")
if not isinstance(sublevel_entries, list):
    raise RuntimeError("sublevel_snapshot_sources.json sublevels missing")
expected_sublevel_images = sum(len(entry.get("images") or []) for entry in sublevel_entries)

parent_files = list(ROTATION.glob("level_*_*.*")) if ROTATION.exists() else []
sublevel_files = list(ROTATION.glob("sublevel_*_*_*.*")) if ROTATION.exists() else []
if len(parent_files) != 28 or len(sublevel_files) != expected_sublevel_images:
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

subrefs = {}
for entry in sublevel_entries:
    sublevel_id = str(entry.get("id", ""))
    images = entry.get("images") or []
    row = []
    for slot in range(1, len(images) + 1):
        prefix = f"sublevel_{sublevel_id.replace('.', '_')}_{slot}"
        matches = sorted(ROTATION.glob(prefix + ".*"))
        if len(matches) != 1:
            raise RuntimeError(
                f"Level {sublevel_id} slot {slot}: expected exactly one fetched image, found {len(matches)}"
            )
        asset = matches[0]
        if asset.suffix.lower() not in {".jpg", ".png", ".gif", ".webp"}:
            raise RuntimeError(f"Unsupported sub-level snapshot asset type: {asset.name}")
        row.append(f"file:///android_asset/level_snapshots/rotation/{asset.name}")
    if not 1 <= len(row) <= 4:
        raise RuntimeError(f"Level {sublevel_id} must expose 1-4 snapshots, found {len(row)}")
    subrefs[sublevel_id] = row

refs_js = json.dumps(refs, ensure_ascii=False, separators=(",", ":"))
subrefs_js = json.dumps(subrefs, ensure_ascii=False, separators=(",", ":"))
# This JavaScript is injected inside a Java string literal in MainActivity.java.
# Escape it for the Java source while preserving valid JSON at runtime.
refs_java = refs_js.replace("\\", "\\\\").replace('"', '\\"')
subrefs_java = subrefs_js.replace("\\", "\\\\").replace('"', '\\"')

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = (
    f"var refs={refs_java};var subrefs={subrefs_java};"
    "var levelNumber=state&&state.level&&state.level.number;"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var sm=where.match(/Level\\s*([01]\\.[0-9]+)/i);"
    "var subkey=sm&&subrefs[sm[1]]?sm[1]:'';"
    "var lm=where.match(/Level[^0-9]*([0-6])/i);"
    "var lv=levelNumber!=null?Math.max(0,Math.min(6,Number(levelNumber)||0)):(lm?Number(lm[1]):0);"
    "var seq=subkey?subrefs[subkey]:(refs[lv]||refs[0]);"
    "var snapshotTurn=Math.max(1,Number(state&&state.turn||1)||1);"
    "var snapshotSlot=Math.floor((snapshotTurn-1)/3)%seq.length;"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=seq[snapshotSlot];"
    "var snapshotLabel=subkey?('Level '+subkey):('Level '+lv);"
    "bg.alt=snapshotLabel+' Snapshot '+(snapshotSlot+1)+' / '+seq.length;"
    "bg.onerror=function(){this.onerror=null;this.src=(refs[lv]||refs[0])[0];};box.appendChild(bg);"
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
print(
    f"Parent Level 0-6 snapshots plus {len(subrefs)} Level 0-1 sub-level snapshot sets enabled; "
    "state.level keeps the integer parent while location selects sub-level art and each set rotates every three turns."
)
