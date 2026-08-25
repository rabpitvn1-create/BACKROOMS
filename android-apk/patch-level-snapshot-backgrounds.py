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
areas = manifest.get("areas", {})
if int(manifest.get("route_count", 0)) != 43 or len(areas) != 43:
    raise RuntimeError(f"Fandom snapshot manifest must cover all 43 campaign areas; found {len(areas)}")

pools: dict[str, list[str]] = {}
parents: dict[str, int] = {}
for area_id, area in areas.items():
    area_id = str(area_id)
    parent_level = int(area.get("parent_level", -1))
    if parent_level < 0 or parent_level > 6:
        raise RuntimeError(f"Invalid parent Level for snapshot area {area_id}: {parent_level}")
    parents[area_id] = parent_level
    refs: list[str] = []
    for record in area.get("images", []):
        name = str(record.get("local_file", "")).strip()
        title = str(record.get("file_title", "")).strip().lower()
        if not name or "sd-hexagon" in title:
            continue
        asset = SNAPSHOT_DIR / name
        if not asset.is_file() or asset.stat().st_size <= 0:
            raise RuntimeError(f"Fandom snapshot asset missing or empty: {asset}")
        refs.append(f"file:///android_asset/level_snapshots/{name}")
    if refs:
        pools[area_id] = refs

for level in range(7):
    refs = pools.get(str(level), [])
    if len(refs) < 2:
        raise RuntimeError(f"Parent Level {level} needs at least 2 packaged Fandom snapshots; found {len(refs)}")


def js_single(value: object) -> str:
    text = str(value).replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "\\n")
    return "'" + text + "'"


# This JavaScript is embedded inside a Java string literal. Keep generated strings
# single-quoted so Java does not see unescaped JSON double quotes.
pool_js = "{" + ",".join(
    js_single(area_id) + ":[" + ",".join(js_single(ref) for ref in refs) + "]"
    for area_id, refs in pools.items()
) + "}"
parent_js = "{" + ",".join(
    js_single(area_id) + ":" + str(parent)
    for area_id, parent in parents.items()
) + "}"

main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

# Keep the exact authoritative Level picker contract expected by later visual-state
# patches. Structured state wins; text parsing remains a compatibility fallback for
# old saves that predate the linear 43-area campaign route.
level_picker = (
    "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};"
    "var structuredLevel=state&&state.level&&state.level.number;"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level[^0-9]*([0-6])/i);"
    "var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
    "var exploration=state&&state.flags&&state.flags.exploration;"
    "var areaId=(exploration&&exploration.areaId!==undefined&&exploration.areaId!==null&&String(exploration.areaId).trim())?String(exploration.areaId).trim():String(lv);"
)

new = (
    level_picker
    + "var pools=" + pool_js + ";"
    + "var parentByArea=" + parent_js + ";"
    "var choices=pools[areaId];"
    "var dedicated=!!(choices&&choices.length);"
    "var resolvedParent=(parentByArea[areaId]!==undefined)?Number(parentByArea[areaId]):lv;"
    "if(!dedicated)choices=pools[String(resolvedParent)]||pools[String(lv)]||pools['0'];"
    "if(!choices||!choices.length)choices=[refs[resolvedParent]||refs[lv]||refs[0]];"
    "var bucket=Math.floor(Date.now()/300000);"
    "var areaSeed=0;for(var ai=0;ai<areaId.length;ai++)areaSeed=(areaSeed*33+areaId.charCodeAt(ai))%2147483647;"
    "var seed=(bucket*17+areaSeed*31+Number(state&&state.turn||0)*7);"
    "var pick=choices[Math.abs(seed)%choices.length];"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=pick;"
    "bg.alt='Area '+areaId+' — Backrooms Wiki Fandom snapshot';"
    "bg.setAttribute('data-fandom-area',areaId);"
    "bg.setAttribute('data-fandom-parent-level',String(resolvedParent));"
    "bg.setAttribute('data-fandom-dedicated',dedicated?'true':'false');"
    "bg.onerror=function(){this.onerror=null;this.src=choices[0]||refs[resolvedParent]||refs[0];};"
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
missing = manifest.get("missing_areas", [])
missing_non_main = [item for item in missing if str(item.get("area_type", "")) != "MAIN"]
print(
    "Backrooms Wiki Fandom area snapshots enabled with 5-minute rotation: "
    f"dedicated={len(pools)}/43, missing_non_main={len(missing_non_main)}; "
    "missing areas fall back only to their parent main Level; Kai stays overlaid on top."
)
