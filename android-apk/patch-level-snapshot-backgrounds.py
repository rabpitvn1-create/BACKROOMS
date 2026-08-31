from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
SNAPSHOT_DIR = ROOT / "app/src/main/assets/level_snapshots"
MANIFEST = SNAPSHOT_DIR / "fandom_manifest.json"
CATALOG_DIR = ROOT / "app/src/main/assets/level_catalog"
REJECT_RENDER_TITLES = (
    "sd-hexagon",
    "default profile picture",
    "site logo",
    "readthepage",
    "whitebackground",
)

# Snapshot assets are optional visual data. The Level catalog is the authoritative roster and
# relationship source; snapshot manifests may cover only a subset of known Levels. Builds stay
# fully offline and validate every packaged asset that the manifest explicitly declares.
if not MANIFEST.is_file():
    raise RuntimeError("Packaged Fandom snapshot manifest missing; level snapshots must be committed before build")
if not CATALOG_DIR.is_dir():
    raise RuntimeError("Level catalog directory missing; snapshot fallback requires authoritative catalog data")

catalog_entries: dict[str, dict] = {}
for path in sorted(CATALOG_DIR.rglob("*.json")):
    document = json.loads(path.read_text(encoding="utf-8"))
    for entry in document.get("entries", []):
        area_id = str(entry.get("id", "")).strip()
        if not area_id:
            raise RuntimeError(f"Level catalog entry without id: {path}")
        if area_id in catalog_entries:
            raise RuntimeError(f"Duplicate Level catalog id for snapshot fallback: {area_id}")
        catalog_entries[area_id] = entry
if not catalog_entries:
    raise RuntimeError("Level catalog contains no entries")

catalog_parents: dict[str, str] = {}
for area_id, entry in catalog_entries.items():
    parent_id = str(entry.get("parentId", "")).strip()
    if not parent_id and entry.get("parentMainLevel") is not None:
        parent_id = str(entry.get("parentMainLevel")).strip()
    if parent_id and parent_id != area_id:
        catalog_parents[area_id] = parent_id

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
areas = manifest.get("areas", {})
if not isinstance(areas, dict) or not areas:
    raise RuntimeError("Fandom snapshot manifest contains no areas")

pools: dict[str, list[str]] = {}
legacy_parents: dict[str, str] = {}
verified_assets = 0
for area_id, area in areas.items():
    area_id = str(area_id)
    if area.get("parent_level") is not None:
        legacy_parent = str(area.get("parent_level")).strip()
        if legacy_parent and legacy_parent != area_id:
            legacy_parents[area_id] = legacy_parent
    refs: list[str] = []
    for record in area.get("images", []):
        name = str(record.get("local_file", "")).strip()
        title = str(record.get("file_title", "")).strip().lower()
        if not name:
            continue
        asset = SNAPSHOT_DIR / name
        if not asset.is_file() or asset.stat().st_size <= 0:
            raise RuntimeError(f"Packaged Fandom snapshot asset missing or empty: {asset}")
        expected_bytes = int(record.get("bytes") or 0)
        if expected_bytes and asset.stat().st_size != expected_bytes:
            raise RuntimeError(
                f"Packaged Fandom snapshot size mismatch: {asset} "
                f"expected={expected_bytes} actual={asset.stat().st_size}"
            )
        expected_sha = str(record.get("sha256") or "").strip().lower()
        if expected_sha:
            actual_sha = hashlib.sha256(asset.read_bytes()).hexdigest()
            if actual_sha != expected_sha:
                raise RuntimeError(
                    f"Packaged Fandom snapshot checksum mismatch: {asset} "
                    f"expected={expected_sha} actual={actual_sha}"
                )
        verified_assets += 1
        if any(part in title for part in REJECT_RENDER_TITLES):
            continue
        refs.append(f"file:///android_asset/level_snapshots/{name}")
    if refs:
        pools[area_id] = refs

if verified_assets <= 0:
    raise RuntimeError("Packaged Fandom snapshot manifest contains no image assets")
if not pools:
    raise RuntimeError("Packaged Fandom snapshot manifest has no renderable snapshot pools")

# Catalog relationships win. Legacy manifest parents are only a compatibility fallback for snapshot
# records that predate catalog migration; they never define gameplay progression.
parents = dict(legacy_parents)
parents.update(catalog_parents)

generic_fallback_key = "0" if pools.get("0") else sorted(pools)[0]
generic_fallback_ref = pools[generic_fallback_key][0]


def js_single(value: object) -> str:
    text = str(value).replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "\\n")
    return "'" + text + "'"


# This JavaScript is embedded inside a Java string literal. Keep generated strings single-quoted so
# Java does not see unescaped JSON double quotes. IDs remain strings end-to-end.
pool_js = "{" + ",".join(
    js_single(area_id) + ":[" + ",".join(js_single(ref) for ref in refs) + "]"
    for area_id, refs in sorted(pools.items())
) + "}"
parent_js = "{" + ",".join(
    js_single(area_id) + ":" + js_single(parent)
    for area_id, parent in sorted(parents.items())
) + "}"

main = MAIN.read_text(encoding="utf-8")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/BestKai.png';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

# Structured exploration areaId is authoritative for the legacy WebView projection. state.level is a
# compatibility fallback and is never coerced to Number, so arbitrary IDs such as 742.13, 999.alpha,
# epsilon or Red Rooms remain exact strings.
level_picker = (
    "var exploration=state&&state.flags&&state.flags.exploration;"
    "var structuredLevel=state&&state.level&&((state.level.id!==undefined&&state.level.id!==null)?state.level.id:state.level.number);"
    "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');"
    "var lm=where.match(/Level +([^ ]+)/i);"
    "var parsedLevel=lm?String(lm[1]).trim():'';"
    "var areaId=(exploration&&exploration.areaId!==undefined&&exploration.areaId!==null&&String(exploration.areaId).trim())?String(exploration.areaId).trim():((structuredLevel!==undefined&&structuredLevel!==null&&String(structuredLevel).trim())?String(structuredLevel).trim():(parsedLevel||'0'));"
)

new = (
    level_picker
    + "var pools=" + pool_js + ";"
    + "var parentByArea=" + parent_js + ";"
    + "var genericFallbackKey=" + js_single(generic_fallback_key) + ";"
    + "var genericFallbackRef=" + js_single(generic_fallback_ref) + ";"
    "function resolveSnapshotPool(id){var requested=String(id||'');var cursor=requested;var seen={};while(cursor&&!seen[cursor]){seen[cursor]=true;var own=pools[cursor];if(own&&own.length)return {key:cursor,choices:own,dedicated:cursor===requested};cursor=(parentByArea[cursor]!==undefined&&parentByArea[cursor]!==null)?String(parentByArea[cursor]):'';}var fallback=pools[genericFallbackKey];return {key:genericFallbackKey,choices:(fallback&&fallback.length)?fallback:[genericFallbackRef],dedicated:false};}"
    "var resolvedSnapshot=resolveSnapshotPool(areaId);"
    "var choices=resolvedSnapshot.choices;"
    "var dedicated=resolvedSnapshot.dedicated;"
    "var resolvedParent=resolvedSnapshot.key;"
    "var bucket=Math.floor(Date.now()/300000);"
    "var areaSeed=0;for(var ai=0;ai<areaId.length;ai++)areaSeed=(areaSeed*33+areaId.charCodeAt(ai))%2147483647;"
    "var seed=(bucket*17+areaSeed*31+Number(state&&state.turn||0)*7);"
    "var pick=choices[Math.abs(seed)%choices.length]||genericFallbackRef;"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=pick;"
    "bg.alt='Area '+areaId+' — Backrooms Wiki Fandom snapshot';"
    "bg.setAttribute('data-fandom-area',areaId);"
    "bg.setAttribute('data-fandom-parent-level',String(resolvedParent));"
    "bg.setAttribute('data-fandom-dedicated',dedicated?'true':'false');"
    "bg.onerror=function(){this.onerror=null;this.src=genericFallbackRef;};"
    "box.appendChild(bg);"
    "var kai=document.createElement('img');kai.className='snapshot-character';"
    "kai.src='file:///android_asset/BestKai.png';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"
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

effective_missing = []
for area_id, area in areas.items():
    area_id = str(area_id)
    if str(area.get("area_type", "")) == "MAIN" or area_id in pools:
        continue
    effective_missing.append(
        {
            "area_id": area_id,
            "parent_id": parents.get(area_id, generic_fallback_key),
            "area_name": str(area.get("area_name", "")),
            "reason": str(area.get("status", "no_environment_images_after_filter")) if not area.get("images") else "no_environment_images_after_filter",
        }
    )
for item in effective_missing:
    print(
        f"SNAPSHOT_MISSING area={item['area_id']} parent={item['parent_id']} "
        f"name={item['area_name']} reason={item['reason']}"
    )
print(
    "Packaged Backrooms Wiki Fandom snapshots enabled with 5-minute rotation: "
    f"verified_assets={verified_assets}, dedicated_pools={len(pools)}, manifest_areas={len(areas)}, "
    f"catalog_levels={len(catalog_entries)}, missing_manifest_pools={len(effective_missing)}, "
    f"generic_fallback={generic_fallback_key}; exact pool -> catalog parent -> generic fallback; Kai stays overlaid on top."
)
