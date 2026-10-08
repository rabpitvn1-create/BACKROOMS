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

# The file names are assets, not progression authority. WorldContentCatalog
# supplies the exact IDs; image selection must never unlock an exit or node.
import re

CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/progression/WorldContentCatalog.kt"
SUBLEVEL_SNAPSHOTS = {}
AREA_SNAPSHOTS = {}
known_base = {name for names in LEVEL_SNAPSHOTS.values() for name in names}

for asset in sorted(ASSET_DIR.glob("*.webp")):
    name = asset.name
    if name in known_base:
        continue

    match = re.fullmatch(r"level_(\d{2})_snapshot_\d{3}\.webp", name)
    if match:
        LEVEL_SNAPSHOTS.setdefault(int(match.group(1)), []).append(name)
        continue

    match = re.fullmatch(r"level_(\d{2})p(\d+)_(?:reference_)?snapshot_\d{3}\.webp", name)
    if match:
        # Preserve literal decimals: 0.01 is NOT 0.1.
        key = f"level-{int(match.group(1))}.{match.group(2)}"
        SUBLEVEL_SNAPSHOTS.setdefault(key, []).append(name)
        continue

    match = re.fullmatch(r"area_(\d{2})_(.+?)_(?:reference_)?snapshot_\d{3}\.webp", name)
    if match:
        slug = match.group(2).replace("_", "-")
        slug = {"asset-11p1": "asset-11-1", "scene-01p2": "scene-01-2"}.get(slug, slug)
        key = f"area:{int(match.group(1))}:{slug}"
        AREA_SNAPSHOTS.setdefault(key, []).append(name)
        continue

    raise RuntimeError(f"Unrecognized Level Snapshot filename: {name}")

catalog = CATALOG.read_text(encoding="utf-8")
canon_nodes = set(re.findall(r'WorldContentEntry\(WorldNodeId\("(level-[0-9]+(?:\.[0-9]+)?)"\)', catalog))
canon_areas = {
    f"area:{parent}:{slug}"
    for parent, slug in re.findall(r'WorldNamedSection\((\d+), "([a-z0-9-]+)"', catalog)
}
mapped_nodes = {f"level-{lv}" for lv in LEVEL_SNAPSHOTS} | set(SUBLEVEL_SNAPSHOTS)
if mapped_nodes != canon_nodes or set(AREA_SNAPSHOTS) != canon_areas:
    raise RuntimeError(
        f"Snapshot registry differs from WorldContentCatalog: "
        f"missing nodes={sorted(canon_nodes - mapped_nodes)}, "
        f"missing areas={sorted(canon_areas - set(AREA_SNAPSHOTS))}, "
        f"unknown nodes={sorted(mapped_nodes - canon_nodes)}, "
        f"unknown areas={sorted(set(AREA_SNAPSHOTS) - canon_areas)}"
    )

all_pools = list(LEVEL_SNAPSHOTS.values()) + list(SUBLEVEL_SNAPSHOTS.values()) + list(AREA_SNAPSHOTS.values())
all_names = [name for pool in all_pools for name in pool]
on_disk = {path.name for path in ASSET_DIR.glob("*.webp")}
if len(all_names) != len(set(all_names)) or set(all_names) != on_disk:
    raise RuntimeError("Level Snapshot assets must be mapped exactly once")

for name in all_names:
    raw = (ASSET_DIR / name).read_bytes()
    if len(raw) < 12 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
        raise RuntimeError(f"Invalid packaged WebP: {name}")

main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

def js_array(names):
    return "[" + ",".join(f"'file:///android_asset/level_snapshots/{name}'" for name in names) + "]"


def js_map(mapping):
    return "{" + ",".join(f"'{key}':{js_array(names)}" for key, names in mapping.items()) + "}"


refs_js = "{" + ",".join(
    f"{level}:{js_array(names)}" for level, names in sorted(LEVEL_SNAPSHOTS.items())
) + "}"
sub_refs_js = js_map(SUBLEVEL_SNAPSHOTS)
area_refs_js = js_map(AREA_SNAPSHOTS)

new = (
    "var refs=" + refs_js + ";var subRefs=" + sub_refs_js + ";var areaRefs=" + area_refs_js + ";"
    "var structuredLevel=state&&state.level&&state.level.number;"
    "var candidate=Number(structuredLevel);"
    "var hasLevel=structuredLevel!==undefined&&structuredLevel!==null&&String(structuredLevel).trim()!==''&&"
    "Number.isInteger(candidate)&&candidate>=0&&candidate<=13;"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level[^0-9]*([0-9]{1,2})(?=[^0-9]|$)/i);"
    "var lv=hasLevel?candidate:(lm?Number(lm[1]):0);"
    "var pool=refs[lv]||refs[0];"
    "var flags=state&&state.flags||{};"
    "var scene=String(flags.visualAreaKey||'').trim().toLowerCase();"
    "var node=String(state&&state.worldNodeId||state&&state.level&&state.level.nodeId||flags.worldNodeId||'').trim().toLowerCase();"
    "var area=scene.indexOf('area:')===0?scene:'area:'+lv+':'+scene.replace(/_/g,'-');"
    "var sub=scene.indexOf('level-')===0?scene:node;"
    "if(scene&&areaRefs[area]&&area.indexOf('area:'+lv+':')===0){pool=areaRefs[area];}"
    "else if(sub&&subRefs[sub]&&sub.indexOf('level-'+lv+'.')===0){pool=subRefs[sub];}"
    "else{var loc=String(state&&state.location||'').trim();"
    "var sm=loc.match(/^Level[ ]+([0-9]{1,2})[.]([0-9]{1,2})(?=[^0-9]|$)/i);"
    "if(sm&&Number(sm[1])===lv){var sk='level-'+lv+'.'+sm[2];if(subRefs[sk])pool=subRefs[sk];}}"
    "var levelTurn=Number(state&&state.flags&&state.flags.exploration&&state.flags.exploration.levelTurns);"
    "var frame=isFinite(levelTurn)&&levelTurn>=0?Math.floor(levelTurn):Math.max(0,Math.floor(Number(state&&state.turn||1)-1));"
    "var fallback=pool[frame%pool.length]||refs[0][0];"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:fallback;"
    "bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' local snapshot';"
    "if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[lv]?refs[lv][0]:refs[0][0];};"
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
