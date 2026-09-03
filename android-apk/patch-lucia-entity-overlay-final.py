from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSET = ROOT / "app/src/main/assets/file_000000000dbc8209b74585555f5786dc.png"
MARKER = "LUCIA_ENTITY_OVERLAY_V1"
SHADOW_MARKER = "CHARACTER_GROUND_SHADOW_V1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


if not ASSET.is_file() or ASSET.stat().st_size <= 0:
    raise RuntimeError("Lucia Entity overlay asset is missing or empty")
raw = ASSET.read_bytes()
if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Lucia Entity overlay asset is not a valid PNG")
width = int.from_bytes(raw[16:20], "big")
height = int.from_bytes(raw[20:24], "big")
if width < 128 or height < 128:
    raise RuntimeError(f"Lucia Entity overlay asset is unexpectedly small: {width}x{height}")

main = MAIN.read_text(encoding="utf-8")
if MARKER not in main:
    anchor = '      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};" +\n'
    replacement = r'''      "/* LUCIA_ENTITY_OVERLAY_V1 */" +
      "/* CHARACTER_GROUND_SHADOW_V1 */" +
      "function luciaEntityPartyPresent(){try{var details=state&&state.partyDetails;var members=details&&Array.isArray(details.members)?details.members:[];if(members.some(function(m){return String(m&&m.id||'').trim().toLowerCase()==='lucia';}))return true;var party=state&&state.party;if(!Array.isArray(party))return false;return party.some(function(m){var raw=(m&&typeof m==='object')?(m.id||m.name||''):m;var id=String(raw||'').trim().toLowerCase();return id==='lucia'||id==='lucia lục';});}catch(_){return false;}}" +
      "function appendLuciaEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-lucia-entity');if(old)old.remove();if(!activeEntityKey()||!luciaEntityPartyPresent())return;box.style.position='relative';box.style.overflow='hidden';var img=document.createElement('img');img.className='snapshot-lucia-entity';img.src='file:///android_asset/file_000000000dbc8209b74585555f5786dc.png';img.alt='Lucia Lục';img.style.position='absolute';img.style.right='27%';img.style.bottom='0';img.style.width='auto';img.style.height='92%';img.style.maxWidth='42%';img.style.objectFit='contain';img.style.objectPosition='right bottom';img.style.pointerEvents='none';img.style.zIndex='3';img.style.filter='drop-shadow(0 4px 8px rgba(0,0,0,.58))';box.appendChild(img);}" +
      "function appendGroundShadowForCharacter(box,img,owner){if(!img)return;var boxRect=box.getBoundingClientRect(),rect=img.getBoundingClientRect();if(rect.width<2||rect.height<2){if(!img.dataset.groundShadowWait){img.dataset.groundShadowWait='1';img.addEventListener('load',function(){img.dataset.groundShadowWait='';appendCharacterGroundShadows();},{once:true});}return;}var shadow=document.createElement('div');shadow.className='snapshot-character-ground-shadow snapshot-character-ground-shadow-'+owner;shadow.setAttribute('aria-hidden','true');var shadowWidth=Math.max(34,Math.min(rect.width*.58,boxRect.width*.34));var shadowHeight=Math.max(7,Math.min(14,rect.height*.045));var center=(rect.left-boxRect.left)+(rect.width/2);var left=Math.max(0,Math.min(boxRect.width-shadowWidth,center-shadowWidth/2));shadow.style.position='absolute';shadow.style.left=left+'px';shadow.style.bottom='1px';shadow.style.width=shadowWidth+'px';shadow.style.height=shadowHeight+'px';shadow.style.borderRadius='50%';shadow.style.background='radial-gradient(ellipse at center,rgba(0,0,0,.48) 0%,rgba(0,0,0,.30) 44%,rgba(0,0,0,0) 76%)';shadow.style.filter='blur(2px)';shadow.style.pointerEvents='none';shadow.style.zIndex='1';box.appendChild(shadow);}" +
      "function appendCharacterGroundShadows(){var box=document.getElementById('snapshot');if(!box)return;box.querySelectorAll('.snapshot-character-ground-shadow').forEach(function(n){n.remove();});appendGroundShadowForCharacter(box,box.querySelector('.snapshot-character'),'kai');appendGroundShadowForCharacter(box,box.querySelector('.snapshot-lucia-entity'),'lucia');}" +
      "if(!window.__backroomGroundShadowResize){window.__backroomGroundShadowResize=true;window.addEventListener('resize',function(){requestAnimationFrame(appendCharacterGroundShadows);});}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();appendLuciaEntityOverlay();appendCharacterGroundShadows();};" +
'''
    main = replace_once(main, anchor, replacement, "Lucia Entity overlay renderer")

for marker in (
    MARKER,
    SHADOW_MARKER,
    "function luciaEntityPartyPresent()",
    "function appendLuciaEntityOverlay()",
    "activeEntityKey()||!luciaEntityPartyPresent()",
    "state&&state.partyDetails",
    "String(m&&m.id||'').trim().toLowerCase()==='lucia'",
    "file:///android_asset/file_000000000dbc8209b74585555f5786dc.png",
    "snapshot-lucia-entity",
    "function appendGroundShadowForCharacter(",
    "function appendCharacterGroundShadows()",
    "snapshot-character-ground-shadow",
    "box.querySelector('.snapshot-character')",
    "box.querySelector('.snapshot-lucia-entity')",
    "appendEntityOverlay();appendLuciaEntityOverlay();appendCharacterGroundShadows()",
):
    if marker not in main:
        raise RuntimeError("Lucia/character overlay contract missing: " + marker)

if "appendGroundShadowForCharacter(box,box.querySelector('.snapshot-entity')" in main:
    raise RuntimeError("Entity overlay must not receive the character ground shadow")

MAIN.write_text(main, encoding="utf-8")
print(
    f"Lucia Entity overlay finalized ({width}x{height}): "
    "rendered only while an Entity is active and Lucia is an authoritative Party member; "
    "Kai/Lucia overlays receive responsive soft ground shadows below their feet."
)
