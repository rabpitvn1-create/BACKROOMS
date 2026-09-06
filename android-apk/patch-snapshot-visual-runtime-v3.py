from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
INDEX = ASSETS / "index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def strip_block(text: str, start: str, end: str) -> str:
    while start in text:
        a = text.index(start)
        b = text.find(end, a)
        if b < 0:
            raise RuntimeError(f"unterminated legacy block: {start}")
        text = text[:a] + text[b + len(end):]
    return text


def png_alpha(path: Path) -> tuple[int, int, list[int]]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"not a PNG: {path}")
    pos = 8
    width = height = bit_depth = color_type = interlace = None
    compressed = bytearray()
    transparency = b""
    while pos + 12 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"tRNS":
            transparency = payload
        elif kind == b"IEND":
            break
    if not width or not height or bit_depth != 8 or interlace != 0:
        raise RuntimeError(f"unsupported PNG layout for stage profile: {path.name}")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color_type)
    if channels is None:
        raise RuntimeError(f"unsupported PNG color type {color_type}: {path.name}")

    raw = zlib.decompress(bytes(compressed))
    stride = width * channels
    rows = []
    cursor = 0
    previous = bytearray(stride)

    def paeth(a: int, b: int, c: int) -> int:
        p = a + b - c
        pa = abs(p - a)
        pb = abs(p - b)
        pc = abs(p - c)
        return a if pa <= pb and pa <= pc else b if pb <= pc else c

    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        scan = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        for i in range(stride):
            left = scan[i - channels] if i >= channels else 0
            up = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if filter_type == 1:
                scan[i] = (scan[i] + left) & 0xff
            elif filter_type == 2:
                scan[i] = (scan[i] + up) & 0xff
            elif filter_type == 3:
                scan[i] = (scan[i] + ((left + up) // 2)) & 0xff
            elif filter_type == 4:
                scan[i] = (scan[i] + paeth(left, up, upper_left)) & 0xff
            elif filter_type != 0:
                raise RuntimeError(f"unsupported PNG filter {filter_type}: {path.name}")
        rows.append(scan)
        previous = scan

    alpha = [255] * (width * height)
    for y, row in enumerate(rows):
        for x in range(width):
            at = x * channels
            if color_type == 6:
                value = row[at + 3]
            elif color_type == 4:
                value = row[at + 1]
            elif color_type == 3:
                index = row[at]
                value = transparency[index] if index < len(transparency) else 255
            else:
                value = 255
            alpha[y * width + x] = value
    return width, height, alpha


def sprite_stage_profiles() -> dict[str, dict[str, float | int]]:
    roots = []
    for rel in ["kai_snapshot_overlay.png", "kai_snapshot_overlay_combat.png"]:
        path = ASSETS / rel
        if path.is_file():
            roots.append(path)
    for folder in [ASSETS / "entity_overlays", ASSETS / "party_entity_overlays"]:
        if folder.is_dir():
            roots.extend(sorted(folder.glob("*.png")))

    result: dict[str, dict[str, float | int]] = {}
    for path in roots:
        width, height, alpha = png_alpha(path)
        opaque = [(index % width, index // width) for index, value in enumerate(alpha) if value > 24]
        if not opaque:
            continue

        visible_min_x = min(x for x, _ in opaque)
        visible_max_x = max(x for x, _ in opaque)
        max_y = max(y for _, y in opaque)
        contact_depth = max(4, int(round(height * 0.045)))
        band_start = max(0, max_y - contact_depth)
        counts = [0] * width
        for x, y in opaque:
            if band_start <= y <= max_y:
                counts[x] += 1
        minimum_column_pixels = max(1, int(round(contact_depth * 0.10)))
        contact_x = [x for x, count in enumerate(counts) if count >= minimum_column_pixels]
        if not contact_x:
            contact_x = [x for x, y in opaque if band_start <= y <= max_y]
        if not contact_x:
            contact_x = [x for x, _ in opaque]

        min_x = min(contact_x)
        max_x = max(contact_x)
        key = str(path.relative_to(ASSETS)).replace("\\", "/")
        result[key] = {
            "centerX": round((min_x + max_x + 1) / (2.0 * width), 6),
            "bottomY": round((max_y + 1) / float(height), 6),
            "visibleMinX": round(visible_min_x / float(width), 6),
            "visibleMaxX": round((visible_max_x + 1) / float(width), 6),
            "sourceWidth": width,
            "sourceHeight": height,
        }

    if not result:
        raise RuntimeError("no sprite stage profiles generated")
    return result


stage_profiles = sprite_stage_profiles()
html = INDEX.read_text(encoding="utf-8")

# Remove every previous snapshot visual implementation before rebuilding. These markers are
# intentionally stripped even if a stale patch was accidentally run earlier in the chain.
html = strip_block(html, "<!-- COMBAT_VISUAL_EFFECTS_BEGIN -->", "<!-- COMBAT_VISUAL_EFFECTS_END -->")
html = strip_block(html, "<!-- RUNTIME_AUTHORITY_VISUAL_FINAL -->", "</script>")
html = strip_block(html, "<!-- SNAPSHOT_VISUAL_RUNTIME_V3 -->", "<!-- SNAPSHOT_VISUAL_RUNTIME_V3_END -->")

old_focus = "  function focusEntity(enemyId){if(enemyId)currentEnemy=String(enemyId);applyVisualFocus()}"
new_focus = '''  function beginEntityExit(enemyId){
    const root=box();if(!root)return;
    const wanted=String(enemyId||currentEnemy||'').toLowerCase();
    root.querySelectorAll('.snapshot-entity-overlay').forEach(node=>{
      if(!wanted||String(node.dataset.entityId||'').toLowerCase()===wanted){
        if(node.classList.contains('combat-active-entity'))node.classList.add('entity-slide-out-v3');
      }
    });
  }
  function focusEntity(enemyId){
    if(!enemyId)return;
    currentEnemy=String(enemyId);
    applyVisualFocus();
    const root=box();if(!root)return;
    const wanted=currentEnemy.toLowerCase();
    root.querySelectorAll('.snapshot-entity-overlay').forEach(node=>{
      if(String(node.dataset.entityId||'').toLowerCase()!==wanted)return;
      node.classList.remove('entity-slide-out-v3','entity-slide-in-v3');
      void node.offsetWidth;
      node.classList.add('entity-slide-in-v3');
      setTimeout(()=>node.classList.remove('entity-slide-in-v3'),520);
    });
  }'''
html = replace_once(html, old_focus, new_focus, "entity rotation controller")
html = replace_once(
    html,
    "      if(event.kind==='ENTITY_ENTER')focusEntity(event.enemyId);",
    "      if(event.kind==='ENTITY_DOWN')beginEntityExit(event.enemyId);\n      if(event.kind==='ENTITY_ENTER')focusEntity(event.enemyId);",
    "entity down/enter lifecycle",
)

style = r'''<style id="snapshot-visual-runtime-v3-style">
/* Encounter staging uses visible sprite bounds and one shared contact line. */
.snapshot .snapshot-character{z-index:4!important}
.snapshot .snapshot-party-entity-layer,.snapshot .snapshot-party-entity-overlay{z-index:5!important}
.snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,auto)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:right bottom!important;scale:1 1!important}
.snapshot:not(.entity-encounter-present) .snapshot-character{scale:1 1!important}
.snapshot.entity-encounter-present .snapshot-party-entity-overlay{left:var(--stage-left,auto)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;max-width:48%!important}
.snapshot.entity-encounter-present:not(.combat-turn-managed) .snapshot-party-entity-overlay{opacity:0!important}
.snapshot .snapshot-entities{position:absolute!important;inset:0!important;display:block!important;z-index:6!important;overflow:hidden!important;pointer-events:none!important}
.snapshot .snapshot-entity-overlay{position:absolute!important;left:var(--stage-left,0px)!important;right:auto!important;bottom:var(--stage-bottom,2.2%)!important;width:auto!important;height:91%!important;max-width:50%!important;opacity:0;transform:translateX(-64px) scale(.97);transform-origin:50% 92%!important;transition:opacity .26s ease,transform .38s cubic-bezier(.2,.82,.26,1)!important}
.snapshot:not(.combat-turn-managed) .snapshot-entity-overlay:first-child{opacity:1!important;transform:translateX(0) scale(1)!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.combat-active-entity{opacity:1!important;transform:translateX(0) scale(1)!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.entity-slide-out-v3{opacity:0!important;transform:translateX(-82px) scale(.96)!important}
.snapshot .snapshot-entity-overlay.entity-slide-in-v3{animation:entitySlideInV3 .48s cubic-bezier(.18,.82,.24,1) both!important}
@keyframes entitySlideInV3{0%{opacity:0;transform:translateX(-86px) scale(.95)}66%{opacity:1;transform:translateX(4px) scale(1.01)}100%{opacity:1;transform:translateX(0) scale(1)}}

/* Hit feedback comes only from authoritative ATTACK/SKILL timeline events. */
.snapshot .snapshot-hit-spark-v3{position:absolute;z-index:12;pointer-events:none;border-radius:50%;background:radial-gradient(circle,rgba(255,253,242,.96) 0 12%,rgba(255,226,177,.70) 30%,rgba(255,188,122,.18) 58%,rgba(255,188,122,0) 76%);mix-blend-mode:screen;animation:hitSparkV3 .36s ease-out both}
@keyframes hitSparkV3{0%{opacity:0;transform:translate(-50%,-50%) scale(.28)}24%{opacity:1;transform:translate(-50%,-50%) scale(1)}100%{opacity:0;transform:translate(-50%,-50%) scale(1.55)}}

/* Detector geometry is never painted. Core stays centered; only the emitted halo breathes. */
.snapshot .snapshot-light-layer-v3{position:absolute;inset:0;z-index:2;overflow:hidden;pointer-events:none}
.snapshot .snapshot-light-fixture-v3{position:absolute;pointer-events:none;transform:translate(-50%,-50%);transform-origin:center;mix-blend-mode:screen}
.snapshot .snapshot-light-halo-v3{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%) scale(.94,.92);transform-origin:50% 50%;border-radius:50%;background:radial-gradient(ellipse at center,rgba(255,253,233,.30) 0 8%,rgba(255,247,211,.18) 30%,rgba(255,239,187,.08) 52%,rgba(255,238,185,0) 76%);filter:blur(1.1px);animation:lampHaloBreathV4 2.65s ease-in-out infinite alternate}
.snapshot .snapshot-light-core-v3{position:absolute;inset:0;border-radius:999px;background:linear-gradient(90deg,rgba(255,248,218,.62),rgba(255,255,246,.96) 34%,rgba(255,255,250,.98) 66%,rgba(255,248,218,.62));filter:blur(.35px) drop-shadow(0 0 2px rgba(255,251,226,.76)) drop-shadow(0 0 6px rgba(255,239,188,.34));animation:lampCoreBreathV4 2.1s ease-in-out infinite alternate}
.snapshot .snapshot-light-fixture-v3[data-kind="point"] .snapshot-light-core-v3{border-radius:50%;background:radial-gradient(circle,rgba(255,255,250,.98) 0 34%,rgba(255,244,205,.72) 64%,rgba(255,244,205,0) 100%)}
@keyframes lampHaloBreathV4{0%{opacity:.54;transform:translate(-50%,-50%) scale(.94,.92);filter:blur(1.0px)}55%{opacity:.76;transform:translate(-50%,-50%) scale(1.02,.99);filter:blur(1.35px)}100%{opacity:.88;transform:translate(-50%,-50%) scale(1.10,1.06);filter:blur(1.7px)}}
@keyframes lampCoreBreathV4{0%{opacity:.84;filter:blur(.35px) brightness(.99) drop-shadow(0 0 2px rgba(255,251,226,.70)) drop-shadow(0 0 5px rgba(255,239,188,.28))}100%{opacity:1;filter:blur(.35px) brightness(1.08) drop-shadow(0 0 3px rgba(255,251,226,.82)) drop-shadow(0 0 8px rgba(255,239,188,.42))}}
</style>'''

script_template = r'''<script id="snapshot-visual-runtime-v3-script">
(function(){
  const STAGE_PROFILES=__STAGE_PROFILES__;
  const lightCache=new Map();
  let scheduled=false;
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  function root(){return document.getElementById('snapshot')}
  function assetKey(node){
    const src=String(node&&((node.currentSrc||node.src))||'');
    const marker='/android_asset/';
    const at=src.indexOf(marker);
    return at>=0?src.substring(at+marker.length).split('?')[0].split('#')[0]:'';
  }
  function stageProfile(node){return STAGE_PROFILES[assetKey(node)]||{centerX:.5,bottomY:.96,visibleMinX:0,visibleMaxX:1,sourceWidth:1,sourceHeight:1}}

  function actorNodes(box){
    const out=[];
    const kai=box.querySelector('.snapshot-character');if(kai)out.push(['kai',kai,'kai']);
    box.querySelectorAll('.snapshot-party-entity-overlay').forEach(node=>out.push(['party:'+String(node.dataset.partyEntityId||''),node,'party']));
    box.querySelectorAll('.snapshot-entity-overlay').forEach(node=>out.push(['entity:'+String(node.dataset.entityId||''),node,'entity']));
    return out;
  }

  function syncStage(){
    const box=root();if(!box||!box.classList.contains('entity-encounter-present'))return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    const targetY=rr.height*.965;
    actorNodes(box).forEach(([,node,role])=>{
      const rect=node.getBoundingClientRect();
      if(rect.width<3||rect.height<3)return;
      const p=stageProfile(node);
      const visibleMinX=clamp(Number(p.visibleMinX)||0,0,1);
      const visibleMaxX=clamp(Number(p.visibleMaxX)||1,visibleMinX,1);
      let left;
      if(role==='entity'){
        left=rr.width*.008-rect.width*visibleMinX;
      }else{
        left=rr.width*.995-rect.width*visibleMaxX;
      }
      const minLeft=rr.width*.004-rect.width*visibleMinX;
      const maxLeft=rr.width*.996-rect.width*visibleMaxX;
      left=clamp(left,minLeft,maxLeft);
      const bottom=rr.height-targetY-rect.height*(1-clamp(Number(p.bottomY)||.96,.55,1));
      node.style.setProperty('--stage-left',left.toFixed(2)+'px');
      node.style.setProperty('--stage-bottom',bottom.toFixed(2)+'px');
    });
  }

  function combatant(id){
    const box=root();if(!box||!id)return null;
    const wanted=String(id).toLowerCase();
    if(wanted==='kai')return box.querySelector('.snapshot-character');
    for(const node of box.querySelectorAll('.snapshot-entity-overlay'))if(String(node.dataset.entityId||'').toLowerCase()===wanted)return node;
    for(const node of box.querySelectorAll('.snapshot-party-entity-overlay'))if(String(node.dataset.partyEntityId||'').toLowerCase()===wanted)return node;
    return null;
  }

  async function hit(event){
    if(!event||!event.targetId)return;
    const kind=String(event.kind||'');
    if(kind!=='ATTACK'&&kind!=='SKILL')return;
    const target=combatant(event.targetId);if(!target)return;
    const attacker=combatant(event.actorId),box=root();if(!box)return;
    const tr=target.getBoundingClientRect(),ar=attacker&&attacker.getBoundingClientRect(),rr=box.getBoundingClientRect();
    const direction=ar&&ar.width>0&&ar.left+ar.width*.5>tr.left+tr.width*.5?-1:1;
    const spark=document.createElement('div');spark.className='snapshot-hit-spark-v3';
    spark.style.left=clamp(tr.left-rr.left+tr.width*.52,8,rr.width-8)+'px';
    spark.style.top=clamp(tr.top-rr.top+tr.height*.42,8,rr.height-8)+'px';
    spark.style.width=clamp(Math.min(tr.width*.36,tr.height*.30),26,74)+'px';
    spark.style.height=spark.style.width;box.appendChild(spark);
    if(typeof target.animate==='function'){
      const animation=target.animate([
        {offset:0,transform:'translateX(0)',filter:'brightness(1) contrast(1)'},
        {offset:.12,transform:'translateX('+(direction*3)+'px)',filter:'brightness(2.25) contrast(1.28)'},
        {offset:.34,transform:'translateX('+(direction*18)+'px)',filter:'brightness(1.38) contrast(1.12)'},
        {offset:.62,transform:'translateX('+(direction*7)+'px)',filter:'brightness(1.08) contrast(1.04)'},
        {offset:.82,transform:'translateX('+(direction*-3)+'px)',filter:'brightness(1.02) contrast(1.01)'},
        {offset:1,transform:'translateX(0)',filter:'brightness(1) contrast(1)'}
      ],{duration:560,easing:'cubic-bezier(.16,.82,.28,1)',fill:'none'});
      try{await animation.finished}catch(ignore){await sleep(560)}
    }else await sleep(560);
    spark.remove();syncStage();
  }

  function encounterClass(){
    const box=root();if(!box)return;
    box.classList.toggle('entity-encounter-present',!!box.querySelector('.snapshot-entities .snapshot-entity-overlay'));
  }

  function renderLights(bg,lights){
    const box=root();if(!box||!bg)return;
    const bw=box.clientWidth,bh=box.clientHeight,nw=bg.naturalWidth||1,nh=bg.naturalHeight||1;
    if(bw<2||bh<2)return;
    let layer=box.querySelector('.snapshot-light-layer-v3');
    const src=String(bg.currentSrc||bg.src||'');
    const sig=src+'|'+Math.round(bw)+'x'+Math.round(bh)+'|'+JSON.stringify(lights||[]);
    if(layer&&layer.dataset.lightSig===sig)return;
    if(layer)layer.remove();
    if(!Array.isArray(lights)||!lights.length)return;

    const scale=Math.max(bw/nw,bh/nh),drawW=nw*scale,drawH=nh*scale,ox=(bw-drawW)/2,oy=(bh-drawH)/2;
    layer=document.createElement('div');layer.className='snapshot-light-layer-v3';layer.dataset.lightSig=sig;box.appendChild(layer);

    lights.forEach((light,index)=>{
      const cx=ox+Number(light.x||0)*nw*scale;
      const cy=oy+Number(light.y||0)*nh*scale;
      const coreW=clamp(Number(light.w||0)*nw*scale,3,bw*.34);
      const coreH=clamp(Number(light.h||0)*nh*scale,2,bh*.12);
      if(!Number.isFinite(cx)||!Number.isFinite(cy)||coreW<=0||coreH<=0)return;
      const horizontal=coreW>=coreH;
      const haloW=horizontal?clamp(coreW*2.55,20,bw*.58):clamp(Math.max(coreW*4.4,coreH*.66),16,bw*.26);
      const haloH=horizontal?clamp(Math.max(coreH*4.8,coreW*.27),14,bh*.32):clamp(coreH*2.55,20,bh*.52);
      const fixture=document.createElement('div');fixture.className='snapshot-light-fixture-v3';fixture.dataset.kind=String(light.kind||'linear');
      fixture.style.left=cx+'px';fixture.style.top=cy+'px';fixture.style.width=coreW+'px';fixture.style.height=coreH+'px';
      fixture.style.opacity=String(clamp(.70+Number(light.confidence||.5)*.24,.72,.95));
      const halo=document.createElement('div');halo.className='snapshot-light-halo-v3';halo.style.width=haloW+'px';halo.style.height=haloH+'px';
      const core=document.createElement('div');core.className='snapshot-light-core-v3';
      halo.style.animationDelay=(-((index%5)*.19))+'s';
      core.style.animationDelay=(-((index%4)*.17))+'s';
      fixture.appendChild(halo);fixture.appendChild(core);layer.appendChild(fixture);
    });
  }

  function syncLights(){
    const box=root();if(!box)return;
    const bg=box.querySelector('.snapshot-bg');if(!bg)return;
    const src=String(bg.currentSrc||bg.src||'');if(!src)return;
    const run=()=>{
      if(!bg.naturalWidth||!bg.naturalHeight)return;
      let parsed=lightCache.get(src);
      if(parsed===undefined){
        parsed=null;
        try{if(window.Android&&typeof Android.analyzeSnapshotLights==='function')parsed=JSON.parse(Android.analyzeSnapshotLights(src))}catch(ignore){}
        lightCache.set(src,parsed);
      }
      renderLights(bg,parsed&&Array.isArray(parsed.lights)?parsed.lights:[]);
    };
    if(bg.complete)run();else bg.addEventListener('load',run,{once:true});
  }

  function sync(){scheduled=false;encounterClass();syncStage();syncLights()}
  function schedule(){if(scheduled)return;scheduled=true;requestAnimationFrame(sync)}
  function attach(){
    const box=root();if(!box)return;
    new MutationObserver(schedule).observe(box,{childList:true,subtree:true,attributes:true,attributeFilter:['src','class']});
    if(window.ResizeObserver)new ResizeObserver(schedule).observe(box);
    box.querySelectorAll('img').forEach(img=>{if(!img.complete)img.addEventListener('load',schedule,{once:true})});
    window.__backroomCombatVisuals=Object.assign({},window.__backroomCombatVisuals||{},{hit,syncStage,syncLights});
    schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attach,{once:true});else attach();
})();
</script>'''

script = script_template.replace("__STAGE_PROFILES__", json.dumps(stage_profiles, separators=(",", ":")))

marker = "<!-- SNAPSHOT_VISUAL_RUNTIME_V3 -->"
if marker not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("snapshot visual v3 expected exactly one </body>")
    html = html.replace("</body>", marker + "\n" + style + "\n" + script + "\n<!-- SNAPSHOT_VISUAL_RUNTIME_V3_END -->\n</body>", 1)

for forbidden in [
    "snapshot-light-bloom-v2",
    "snapshot-light-bloom{",
    "backroomLampPulse",
    "runtimeLampPulse",
    "detectFixtureCandidates(bg)",
    "FOOT_PROFILES",
]:
    if forbidden in html:
        raise RuntimeError(f"removed snapshot visual code survived rebuild: {forbidden}")

for required in [
    marker,
    "STAGE_PROFILES=",
    "visibleMinX",
    "visibleMaxX",
    "role==='entity'",
    "rr.width*.008-rect.width*visibleMinX",
    "rr.width*.995-rect.width*visibleMaxX",
    "snapshot-light-halo-v3",
    "snapshot-light-core-v3",
    "lampHaloBreathV4",
    "lampCoreBreathV4",
    "window.__backroomCombatVisuals=Object.assign",
    "entity-slide-in-v3",
]:
    if required not in html:
        raise RuntimeError(f"snapshot visual v3 contract missing: {required}")

INDEX.write_text(html, encoding="utf-8")
print("Snapshot visual runtime v3 finalized: Character right, Entity left, shared contact line, centered breathing lamp glow, Entity rotation and hit feedback.")
