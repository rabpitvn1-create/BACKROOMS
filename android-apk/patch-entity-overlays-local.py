from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
INDEX = ASSETS / "index.html"
OVERLAY_DIR = ASSETS / "entity_overlays"
MANIFEST = ROOT / "entity-overlay-source.json"

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
START = "<!-- ENTITY_OVERLAY_LOCAL_BEGIN -->"
END = "<!-- ENTITY_OVERLAY_LOCAL_END -->"


def png_dimensions(data: bytes) -> tuple[int, int]:
    if not data.startswith(PNG_SIGNATURE) or len(data) < 24:
        raise RuntimeError("Not a valid PNG")
    if data[12:16] != b"IHDR":
        raise RuntimeError("PNG IHDR chunk missing")
    return struct.unpack(">II", data[16:24])


manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
entries = manifest.get("entities") or []
if len(entries) != 18 or len({entry["id"] for entry in entries}) != 18:
    raise RuntimeError("Entity overlay manifest must contain exactly 18 unique Entity IDs")
if manifest.get("qualityPolicy") != "exact-original-png-bytes-no-resize-no-recompression":
    raise RuntimeError("Entity overlay quality policy changed")

catalog = []
for entry in entries:
    path = OVERLAY_DIR / entry["file"]
    if not path.is_file():
        raise RuntimeError(f"Missing Entity overlay: {path}")
    data = path.read_bytes()
    actual_size = len(data)
    actual_sha = hashlib.sha256(data).hexdigest()
    actual_width, actual_height = png_dimensions(data)
    if actual_size != entry["size"]:
        raise RuntimeError(f"{entry['file']}: original byte size changed")
    if actual_sha != entry["sha256"]:
        raise RuntimeError(f"{entry['file']}: original PNG bytes changed")
    if actual_width != entry["width"] or actual_height != entry["height"]:
        raise RuntimeError(f"{entry['file']}: original dimensions changed")
    catalog.append({
        "id": entry["id"],
        "name": entry["name"],
        "file": entry["file"],
        "width": entry["width"],
        "height": entry["height"],
    })

html = INDEX.read_text(encoding="utf-8")
if START not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Expected exactly one </body> anchor for Entity overlay injection")
    catalog_json = json.dumps(catalog, ensure_ascii=False, separators=(",", ":"))
    block = f'''{START}
<style>
.snapshot{{position:relative;overflow:hidden}}
.snapshot .snapshot-entities{{position:absolute;inset:0;z-index:4;pointer-events:none;display:flex;align-items:flex-end;justify-content:flex-start;gap:0;overflow:hidden}}
.snapshot .snapshot-entity-overlay{{display:block;width:auto;object-fit:contain;object-position:left bottom;flex:0 1 auto;pointer-events:none;filter:drop-shadow(0 4px 9px rgba(0,0,0,.72))}}
</style>
<script>
(function(){{
  var ENTITY_OVERLAY_CATALOG={catalog_json};
  var byId={{}};
  ENTITY_OVERLAY_CATALOG.forEach(function(entry){{byId[entry.id]=entry;}});
  var scheduled=false;

  function currentEntityIds(){{
    try{{
      if(typeof state==='undefined'||!state)return [];
      var flags=state.flags||{{}};
      var lastRolls=flags.lastRolls||{{}};
      var encounter=lastRolls.entityEncounter||{{}};
      var ids=Array.isArray(encounter.successIds)?encounter.successIds:[];
      return ids.filter(function(id){{return !!byId[id];}});
    }}catch(error){{
      return [];
    }}
  }}

  function syncEntityOverlays(){{
    var box=document.getElementById('snapshot');
    if(!box)return;
    var ids=currentEntityIds();
    var sig=ids.join('|');
    var existing=box.querySelector('.snapshot-entities');
    if(box.dataset.entityOverlaySig===sig&&((ids.length===0&&!existing)||(ids.length>0&&existing)))return;
    if(existing)existing.remove();
    box.dataset.entityOverlaySig=sig;
    if(!ids.length)return;

    var layer=document.createElement('div');
    layer.className='snapshot-entities';
    var count=ids.length;
    var maxWidth=count===1?52:(count===2?38:(count===3?30:Math.max(18,Math.floor(90/count))));
    var height=count===1?97:(count===2?94:90);
    ids.forEach(function(id){{
      var entry=byId[id];
      var img=document.createElement('img');
      img.className='snapshot-entity-overlay';
      img.src='file:///android_asset/entity_overlays/'+entry.file;
      img.alt=entry.name;
      img.title=entry.name;
      img.draggable=false;
      img.decoding='async';
      img.style.maxWidth=maxWidth+'%';
      img.style.height=height+'%';
      img.dataset.entityId=id;
      layer.appendChild(img);
    }});
    box.appendChild(layer);
  }}

  function scheduleEntityOverlaySync(){{
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(function(){{scheduled=false;syncEntityOverlays();}});
  }}

  if(typeof window.renderSnapshot==='function'){{
    var renderSnapshotBase=window.renderSnapshot;
    window.renderSnapshot=function(){{
      var result=renderSnapshotBase.apply(this,arguments);
      scheduleEntityOverlaySync();
      return result;
    }};
  }}

  function attachObserver(){{
    var box=document.getElementById('snapshot');
    if(!box)return;
    new MutationObserver(scheduleEntityOverlaySync).observe(box,{{childList:true,subtree:true}});
    scheduleEntityOverlaySync();
  }}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attachObserver,{{once:true}});
  else attachObserver();
}})();
</script>
{END}'''
    html = html.replace("</body>", block + "\n</body>", 1)

required = [
    START,
    "file:///android_asset/entity_overlays/",
    "lastRolls.entityEncounter" if False else "lastRolls=flags.lastRolls||{}",
    "encounter.successIds",
    "snapshot-entity-overlay",
]
for marker in required:
    if marker not in html:
        raise RuntimeError(f"Entity overlay runtime marker missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("18 exact original PNG Entity overlays verified byte-for-byte and wired into Snapshot rendering.")

# Last-mile character canon runs after every existing generated runtime transform so legacy
# equipment/knowledge/follower patches cannot overwrite Kai R10, Iris R06, Syvial R04 or Lucia R03.
# Normalize the chained JSONObject write into two equivalent statements before execution;
# this keeps the finalizer's fail-closed marker aligned with the actual generated Java.
canon_path = ROOT / "patch-character-canon-r07.py"
canon_code = canon_path.read_text(encoding="utf-8")
old_chain = '''      lucia.put("exists", true)
        .put("encountered", true)'''
new_chain = '''      lucia.put("encountered", true);
      lucia.put("exists", true)'''
if canon_code.count(old_chain) != 1:
    raise RuntimeError(f"Character Canon R07 Lucia encounter chain: expected exactly one source anchor, found {canon_code.count(old_chain)}")
canon_code = canon_code.replace(old_chain, new_chain, 1)
exec(compile(canon_code, str(canon_path), "exec"), {"__name__": "__main__", "__file__": str(canon_path)})
