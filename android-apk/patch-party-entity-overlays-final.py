from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MANIFEST = ROOT / "party-entity-overlay-source.json"
START = "<!-- PARTY_ENTITY_OVERLAYS_BEGIN -->"
END = "<!-- PARTY_ENTITY_OVERLAYS_END -->"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def png_dimensions(data: bytes) -> tuple[int, int]:
    if not data.startswith(PNG_SIGNATURE) or len(data) < 24:
        raise RuntimeError("Party Entity overlay is not a valid PNG")
    if data[12:16] != b"IHDR":
        raise RuntimeError("Party Entity overlay PNG IHDR chunk missing")
    return struct.unpack(">II", data[16:24])


manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
if manifest.get("qualityPolicy") != "exact-original-png-bytes-no-resize-no-recompression":
    raise RuntimeError("Party Entity overlay quality policy changed")
if manifest.get("triggerPolicy") != "active-party-member-during-successful-entity-encounter":
    raise RuntimeError("Party Entity overlay trigger policy changed")
entries = manifest.get("assets") or []
if {entry.get("id") for entry in entries} != {"lucia", "syvial"} or len(entries) != 2:
    raise RuntimeError("Party Entity overlay manifest must contain exactly Lucia and Syvial")

catalog = []
for entry in entries:
    path = ROOT / entry["runtimeFile"]
    if not path.is_file():
        raise RuntimeError(f"Missing Party Entity overlay: {path}")
    data = path.read_bytes()
    width, height = png_dimensions(data)
    if len(data) != entry["size"]:
        raise RuntimeError(f"{entry['id']}: original PNG byte size changed")
    if hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise RuntimeError(f"{entry['id']}: original PNG bytes changed")
    if width != entry["width"] or height != entry["height"]:
        raise RuntimeError(f"{entry['id']}: original PNG dimensions changed")
    catalog.append({
        "id": entry["id"],
        "name": entry["name"],
        "file": Path(entry["runtimeFile"]).name,
    })

html = INDEX.read_text(encoding="utf-8")
if "<!-- KAI_DUAL_OVERLAY_END -->" not in html:
    raise RuntimeError("Party Entity overlays must run after the Kai dual-overlay runtime")

if START not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Expected exactly one </body> anchor for Party Entity overlay injection")
    catalog_json = json.dumps(catalog, ensure_ascii=False, separators=(",", ":"))
    block = f'''{START}
<style>
.snapshot .snapshot-party-entity-layer{{position:absolute;inset:0;z-index:3;pointer-events:none;overflow:hidden}}
.snapshot .snapshot-party-entity-overlay{{position:absolute;bottom:0;display:block;width:auto;object-fit:contain;object-position:center bottom;pointer-events:none}}
.snapshot .snapshot-party-entity-layer[data-count="1"] .snapshot-party-entity-overlay{{right:34%;height:94%;max-width:43%}}
.snapshot .snapshot-party-entity-layer[data-count="2"] .snapshot-party-entity-overlay:nth-child(1){{right:50%;height:89%;max-width:31%}}
.snapshot .snapshot-party-entity-layer[data-count="2"] .snapshot-party-entity-overlay:nth-child(2){{right:27%;height:91%;max-width:31%}}
</style>
<script>
(function(){{
  var PARTY_ENTITY_OVERLAY_CATALOG={catalog_json};
  var byId={{}};
  PARTY_ENTITY_OVERLAY_CATALOG.forEach(function(entry){{byId[entry.id]=entry;}});
  var scheduled=false;

  function hasEntityEncounter(){{
    try{{
      if(typeof state==='undefined'||!state)return false;
      var flags=state.flags||{{}};
      var rolls=flags.lastRolls||{{}};
      var encounter=rolls.entityEncounter||{{}};
      return Array.isArray(encounter.successIds)&&encounter.successIds.length>0;
    }}catch(error){{
      return false;
    }}
  }}

  function normalizedMemberId(member){{
    if(member==null)return '';
    var raw=typeof member==='string'?member:String(member.id||member.name||'');
    raw=raw.trim().toLowerCase();
    if(raw==='lucia'||raw.indexOf('lucia ')===0)return 'lucia';
    if(raw==='syvial'||raw.indexOf('syvial ')===0)return 'syvial';
    return raw;
  }}

  function memberIsActive(member){{
    if(member==null)return false;
    if(typeof member==='string')return true;
    var presence=String(member.presence||'ACTIVE').toUpperCase();
    return presence==='ACTIVE';
  }}

  function activePartyIds(){{
    var ids={{}};
    var detailedPresence={{}};
    try{{
      if(typeof state==='undefined'||!state)return ids;
      var members=state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[];
      members.forEach(function(member){{
        var id=normalizedMemberId(member);
        if(!id)return;
        var active=memberIsActive(member);
        detailedPresence[id]=active;
        if(active)ids[id]=true;
      }});
      var legacy=Array.isArray(state.party)?state.party:[];
      legacy.forEach(function(member){{
        var id=normalizedMemberId(member);
        if(!id)return;
        if(Object.prototype.hasOwnProperty.call(detailedPresence,id))return;
        if(memberIsActive(member))ids[id]=true;
      }});
    }}catch(error){{}}
    return ids;
  }}

  function currentCombatPartyIds(){{
    if(!hasEntityEncounter())return [];
    var active=activePartyIds();
    return PARTY_ENTITY_OVERLAY_CATALOG.map(function(entry){{return entry.id;}}).filter(function(id){{return !!active[id];}});
  }}

  function syncPartyEntityOverlays(){{
    var box=document.getElementById('snapshot');
    if(!box)return;
    var ids=currentCombatPartyIds();
    var sig=ids.join('|');
    var existing=box.querySelector('.snapshot-party-entity-layer');
    if(box.dataset.partyEntityOverlaySig===sig&&((ids.length===0&&!existing)||(ids.length>0&&existing)))return;
    if(existing)existing.remove();
    box.dataset.partyEntityOverlaySig=sig;
    if(!ids.length)return;

    var layer=document.createElement('div');
    layer.className='snapshot-party-entity-layer';
    layer.dataset.count=String(ids.length);
    ids.forEach(function(id){{
      var entry=byId[id];
      var img=document.createElement('img');
      img.className='snapshot-party-entity-overlay';
      img.src='file:///android_asset/party_entity_overlays/'+entry.file;
      img.alt=entry.name+' combat overlay';
      img.title=entry.name;
      img.draggable=false;
      img.decoding='async';
      img.dataset.partyEntityId=id;
      layer.appendChild(img);
    }});
    box.appendChild(layer);
  }}

  function schedulePartyEntityOverlaySync(){{
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(function(){{scheduled=false;syncPartyEntityOverlays();}});
  }}

  var priorTurn=window.backroomTurn;
  if(typeof priorTurn==='function'){{
    window.backroomTurn=function(){{
      var result=priorTurn.apply(this,arguments);
      schedulePartyEntityOverlaySync();
      return result;
    }};
  }}

  function attachObserver(){{
    var box=document.getElementById('snapshot');
    if(!box)return;
    new MutationObserver(schedulePartyEntityOverlaySync).observe(box,{{childList:true,subtree:true}});
    schedulePartyEntityOverlaySync();
  }}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attachObserver,{{once:true}});
  else attachObserver();
}})();
</script>
{END}'''
    html = html.replace("</body>", block + "\n</body>", 1)

for marker in (
    START,
    "file:///android_asset/party_entity_overlays/",
    "state.partyDetails&&Array.isArray(state.partyDetails.members)",
    "encounter.successIds",
    "presence==='ACTIVE'",
    "Object.prototype.hasOwnProperty.call(detailedPresence,id)",
    "snapshot-party-entity-overlay",
    "partyEntityOverlaySig",
):
    if marker not in html:
        raise RuntimeError(f"Party Entity overlay runtime marker missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("Lucia and Syvial Entity-encounter overlays verified byte-for-byte and enabled for active Party members.")
