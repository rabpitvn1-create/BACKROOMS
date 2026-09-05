from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MANIFEST = ROOT / "kai-visual-source.json"
START = "<!-- KAI_DUAL_OVERLAY_BEGIN -->"
END = "<!-- KAI_DUAL_OVERLAY_END -->"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def png_dimensions(data: bytes) -> tuple[int, int]:
    if not data.startswith(PNG_SIGNATURE) or len(data) < 24:
        raise RuntimeError("Kai runtime visual is not a valid PNG")
    if data[12:16] != b"IHDR":
        raise RuntimeError("Kai runtime PNG IHDR chunk missing")
    return struct.unpack(">II", data[16:24])


manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
if manifest.get("qualityPolicy") != "overlays-exact-original-png-avatar-jpeg-decoded-to-png-without-resize":
    raise RuntimeError("Kai visual quality policy changed")
entries = {entry["role"]: entry for entry in manifest.get("assets") or []}
required_roles = {"normalOverlay", "entityEncounterOverlay", "avatar"}
if set(entries) != required_roles:
    raise RuntimeError("Kai visual manifest must contain normalOverlay, entityEncounterOverlay and avatar")

for role in ("normalOverlay", "entityEncounterOverlay"):
    entry = entries[role]
    path = ROOT / entry["runtimeFile"]
    if not path.is_file():
        raise RuntimeError(f"Missing Kai overlay: {path}")
    data = path.read_bytes()
    width, height = png_dimensions(data)
    if len(data) != entry["sourceSize"]:
        raise RuntimeError(f"{role}: exact source byte size changed")
    if hashlib.sha256(data).hexdigest() != entry["sourceSha256"]:
        raise RuntimeError(f"{role}: exact source PNG bytes changed")
    if width != entry["width"] or height != entry["height"]:
        raise RuntimeError(f"{role}: dimensions changed")

avatar = entries["avatar"]
avatar_path = ROOT / avatar["runtimeFile"]
if not avatar_path.is_file():
    raise RuntimeError(f"Missing Kai avatar: {avatar_path}")
avatar_width, avatar_height = png_dimensions(avatar_path.read_bytes())
if avatar_width != avatar["width"] or avatar_height != avatar["height"]:
    raise RuntimeError("Kai avatar dimensions changed")

html = INDEX.read_text(encoding="utf-8")
if "<!-- ENTITY_OVERLAY_LOCAL_END -->" not in html:
    raise RuntimeError("Kai dual overlay must run after the Entity overlay runtime")

if START not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Expected exactly one </body> anchor for Kai dual overlay injection")
    normal_file = Path(entries["normalOverlay"]["runtimeFile"]).name
    combat_file = Path(entries["entityEncounterOverlay"]["runtimeFile"]).name
    block = f'''{START}
<script>
(function(){{
  var KAI_NORMAL='file:///android_asset/{normal_file}';
  var KAI_COMBAT='file:///android_asset/{combat_file}';
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

  function syncKaiOverlay(){{
    var box=document.getElementById('snapshot');
    if(!box)return;
    var kai=box.querySelector('.snapshot-character');
    if(!kai)return;
    var combat=hasEntityEncounter();
    var desired=combat?KAI_COMBAT:KAI_NORMAL;
    if(kai.getAttribute('src')!==desired)kai.setAttribute('src',desired);
    kai.dataset.kaiOverlayMode=combat?'entity':'normal';
  }}

  function scheduleKaiOverlaySync(){{
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(function(){{scheduled=false;syncKaiOverlay();}});
  }}

  var priorTurn=window.backroomTurn;
  if(typeof priorTurn==='function'){{
    window.backroomTurn=function(){{
      var result=priorTurn.apply(this,arguments);
      scheduleKaiOverlaySync();
      return result;
    }};
  }}

  function attachObserver(){{
    var box=document.getElementById('snapshot');
    if(!box)return;
    new MutationObserver(scheduleKaiOverlaySync).observe(box,{{childList:true,subtree:true}});
    scheduleKaiOverlaySync();
  }}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attachObserver,{{once:true}});
  else attachObserver();
}})();
</script>
{END}'''
    html = html.replace("</body>", block + "\n</body>", 1)

for marker in (
    START,
    "kai_snapshot_overlay.png",
    "kai_snapshot_overlay_combat.png",
    "lastRolls||{}",
    "encounter.successIds",
    "kaiOverlayMode",
):
    if marker not in html:
        raise RuntimeError(f"Kai dual overlay runtime marker missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("Kai visuals verified and dual Snapshot overlay enabled: normal pose by default, combat pose on Entity encounter rolls.")
