from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSET = ROOT / "app/src/main/assets/LUCIA_LUC_OVERLAY.png"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


raw = ASSET.read_bytes()
if raw[:8] != b"\x89PNG\r\n\x1a\n" or len(raw) < 33:
    raise RuntimeError("Lucia combat overlay is not a valid PNG")
width, height = struct.unpack(">II", raw[16:24])
if width < 256 or height < 512:
    raise RuntimeError(f"Lucia combat overlay is unexpectedly small: {width}x{height}")

main = MAIN.read_text(encoding="utf-8")
request_marker = '      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById(\'status\');if(s)s.textContent=\'Snapshot chưa được cấu hình.\';}" +\n'

lucia_js = r'''      "/* LUCIA_COMBAT_OVERLAY_R01 */var __luciaBaseRenderSnapshot=renderSnapshot;" +
      "function luciaCombatVisible(){var c=state&&state.combat;if(!c||c.active!==true)return false;var members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:null;if(members){for(var i=0;i<members.length;i++){var m=members[i];if(String(m&&m.id||'').toLowerCase()==='lucia'&&String(m&&m.presence||'ACTIVE').toUpperCase()==='ACTIVE')return true;}return false;}var party=state&&state.party;if(Array.isArray(party)){for(var j=0;j<party.length;j++){var p=party[j],id=typeof p==='string'?p:(p&&p.id);if(String(id||'').toLowerCase()==='lucia')return true;}}return false;}" +
      "function appendLuciaCombatOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-lucia');if(old)old.remove();if(!luciaCombatVisible())return;var img=document.createElement('img');img.className='snapshot-lucia';img.alt='Lucia \"Lục\"';img.src='file:///android_asset/LUCIA_LUC_OVERLAY.png';img.style.position='absolute';img.style.right='28%';img.style.bottom='0';img.style.height='92%';img.style.width='auto';img.style.maxWidth='40%';img.style.objectFit='contain';img.style.objectPosition='right bottom';img.style.pointerEvents='none';img.style.zIndex='2';img.style.filter='drop-shadow(0 4px 8px rgba(0,0,0,.58))';box.appendChild(img);}" +
      "renderSnapshot=function(){__luciaBaseRenderSnapshot();appendLuciaCombatOverlay();};" +
'''

if "LUCIA_COMBAT_OVERLAY_R01" not in main:
    main = replace_once(main, request_marker, lucia_js + request_marker, "Lucia combat overlay runtime insertion")

for marker in (
    "LUCIA_COMBAT_OVERLAY_R01",
    "function luciaCombatVisible()",
    "function appendLuciaCombatOverlay()",
    "img.className='snapshot-lucia'",
    "LUCIA_LUC_OVERLAY.png",
    "renderSnapshot=function(){__luciaBaseRenderSnapshot();appendLuciaCombatOverlay();};",
):
    if marker not in main:
        raise RuntimeError("Lucia combat overlay contract missing: " + marker)

# Preserve Cao Minh's existing overlay source and positioning. Lucia is additive only.
if "function kaiOverlaySource()" not in main or "CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png" not in main:
    raise RuntimeError("Cao Minh combat overlay contract changed before Lucia overlay patch")

MAIN.write_text(main, encoding="utf-8")
print(f"Lucia combat overlay connected from LUCIA_LUC_OVERLAY.png ({width}x{height}); Cao Minh overlay unchanged.")
