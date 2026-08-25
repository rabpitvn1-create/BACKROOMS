from pathlib import Path
import json
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
SNAPSHOT_DIR = ROOT / "app/src/main/assets/level_snapshots"
MANIFEST = SNAPSHOT_DIR / "fandom_manifest.json"
PREPARE = ROOT / "prepare-fandom-level-snapshots.py"

if not PREPARE.is_file():
    raise RuntimeError("Fandom snapshot preparation script missing")
runpy.run_path(str(PREPARE), run_name="__main__")
if not MANIFEST.is_file():
    raise RuntimeError("Fandom snapshot manifest missing after preparation")

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
pools = {}
for level in range(7):
    records = manifest.get("levels", {}).get(str(level), [])
    refs = []
    for record in records:
        name = str(record.get("local_file", "")).strip()
        title = str(record.get("file_title", "")).strip().lower()
        if not name or "sd-hexagon" in title:
            continue
        asset = SNAPSHOT_DIR / name
        if not asset.is_file() or asset.stat().st_size <= 0:
            raise RuntimeError(f"Fandom snapshot asset missing or empty: {asset}")
        refs.append(f"file:///android_asset/level_snapshots/{name}")
    if len(refs) < 2:
        raise RuntimeError(f"Level {level} needs at least 2 packaged Fandom snapshots; found {len(refs)}")
    pools[level] = refs

pool_js = json.dumps(pools, ensure_ascii=False, separators=(",", ":"))
main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

# Keep the exact authoritative Level picker contract expected by the later
# visual-state synchronization patch. `refs` is a compatibility fallback map;
# active rendering uses the multi-image Fandom pools below.
level_picker = (
    "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};"
    "var structuredLevel=state&&state.level&&state.level.number;"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level[^0-9]*([0-6])/i);"
    "var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
)

new = (
    level_picker
    + "var pools=" + pool_js + ";"
    "var choices=pools[String(lv)]||pools[lv]||pools['0']||pools[0];"
    "var bucket=Math.floor(Date.now()/300000);"
    "var seed=(bucket*17+lv*31+Number(state&&state.turn||0)*7);"
    "var pick=choices[Math.abs(seed)%choices.length];"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=pick;"
    "bg.alt='Level '+lv+' — Backrooms Wiki Fandom snapshot';"
    "bg.setAttribute('data-fandom-level',String(lv));"
    "bg.onerror=function(){this.onerror=null;this.src=choices[0];};"
    "box.appendChild(bg);"
    "var kai=document.createElement('img');kai.className='snapshot-character';"
    "kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"
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
counts = ", ".join(f"L{level}={len(pools[level])}" for level in range(7))
print(f"Backrooms Wiki Fandom snapshot pools enabled with 5-minute rotation ({counts}); Kai stays overlaid on top.")
