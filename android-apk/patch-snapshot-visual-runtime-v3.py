from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"


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
/* One reusable Entity slot on the right. Kai is mirrored only during an Entity encounter. */
.snapshot.entity-encounter-present .snapshot-character{left:1.5%!important;right:auto!important;object-position:left bottom!important;scale:-1 1!important}
.snapshot:not(.entity-encounter-present) .snapshot-character{scale:1 1!important}
.snapshot.entity-encounter-present .snapshot-party-entity-overlay{left:1.5%!important;right:auto!important;max-width:48%!important}
.snapshot.entity-encounter-present:not(.combat-turn-managed) .snapshot-party-entity-overlay{opacity:0!important}
.snapshot .snapshot-entities{position:absolute!important;inset:0!important;display:block!important;z-index:6!important;overflow:hidden!important;pointer-events:none!important}
.snapshot .snapshot-entity-overlay{position:absolute!important;left:auto!important;right:1.5%!important;bottom:2.2%!important;width:auto!important;height:91%!important;max-width:50%!important;opacity:0;transform:translateX(64px) scale(.97);transform-origin:50% 92%!important;transition:opacity .26s ease,transform .38s cubic-bezier(.2,.82,.26,1)!important}
.snapshot:not(.combat-turn-managed) .snapshot-entity-overlay:first-child{opacity:1!important;transform:translateX(0) scale(1)!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.combat-active-entity{opacity:1!important;transform:translateX(0) scale(1)!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.entity-slide-out-v3{opacity:0!important;transform:translateX(82px) scale(.96)!important}
.snapshot .snapshot-entity-overlay.entity-slide-in-v3{animation:entitySlideInV3 .48s cubic-bezier(.18,.82,.24,1) both!important}
@keyframes entitySlideInV3{0%{opacity:0;transform:translateX(86px) scale(.95)}66%{opacity:1;transform:translateX(-4px) scale(1.01)}100%{opacity:1;transform:translateX(0) scale(1)}}

/* Ground contact shadows are simple geometry. No alpha scan, no sprite-content guessing. */
.snapshot .snapshot-contact-shadow-layer-v3{position:absolute;inset:0;z-index:3;overflow:hidden;pointer-events:none}
.snapshot .snapshot-contact-shadow-v3{position:absolute;transform:translate(-50%,-50%);border-radius:50%;background:radial-gradient(ellipse at center,rgba(0,0,0,.48) 0 34%,rgba(0,0,0,.30) 56%,rgba(0,0,0,0) 100%);filter:blur(.7px);pointer-events:none;transition:left 55ms linear,top 55ms linear,width 55ms linear,opacity 65ms linear}

/* Hit feedback comes only from authoritative ATTACK/SKILL timeline events. */
.snapshot .snapshot-hit-spark-v3{position:absolute;z-index:12;pointer-events:none;border-radius:50%;background:radial-gradient(circle,rgba(255,253,242,.96) 0 12%,rgba(255,226,177,.70) 30%,rgba(255,188,122,.18) 58%,rgba(255,188,122,0) 76%);mix-blend-mode:screen;animation:hitSparkV3 .36s ease-out both}
@keyframes hitSparkV3{0%{opacity:0;transform:translate(-50%,-50%) scale(.28)}24%{opacity:1;transform:translate(-50%,-50%) scale(1)}100%{opacity:0;transform:translate(-50%,-50%) scale(1.55)}}

/* Light detector geometry is never painted. Only a soft halo and the luminous fixture core render. */
.snapshot .snapshot-light-layer-v3{position:absolute;inset:0;z-index:2;overflow:hidden;pointer-events:none}
.snapshot .snapshot-light-fixture-v3{position:absolute;pointer-events:none;transform:translate(-50%,-50%);transform-origin:center;mix-blend-mode:screen;animation:lampBreathV3 2.4s ease-in-out infinite alternate}
.snapshot .snapshot-light-halo-v3{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);border-radius:50%;background:radial-gradient(ellipse at center,rgba(255,252,230,.25) 0 9%,rgba(255,246,208,.15) 30%,rgba(255,238,185,.07) 49%,rgba(255,238,185,0) 72%);filter:blur(1.2px)}
.snapshot .snapshot-light-core-v3{position:absolute;inset:0;border-radius:999px;background:linear-gradient(90deg,rgba(255,248,218,.62),rgba(255,255,246,.96) 34%,rgba(255,255,250,.98) 66%,rgba(255,248,218,.62));filter:blur(.35px) drop-shadow(0 0 2px rgba(255,251,226,.76)) drop-shadow(0 0 6px rgba(255,239,188,.34))}
.snapshot .snapshot-light-fixture-v3[data-kind="point"] .snapshot-light-core-v3{border-radius:50%;background:radial-gradient(circle,rgba(255,255,250,.98) 0 34%,rgba(255,244,205,.72) 64%,rgba(255,244,205,0) 100%)}
@keyframes lampBreathV3{0%{opacity:.70;filter:brightness(.98)}100%{opacity:.94;filter:brightness(1.10)}}
</style>'''

script = r'''<script id="snapshot-visual-runtime-v3-script">
(function(){
  const lightCache=new Map();
  let scheduled=false;
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  function root(){return document.getElementById('snapshot')}

  function actorNodes(box){
    const out=[];
    const kai=box.querySelector('.snapshot-character');if(kai)out.push(['kai',kai,'kai']);
    box.querySelectorAll('.snapshot-party-entity-overlay').forEach(node=>out.push(['party:'+String(node.dataset.partyEntityId||''),node,'party']));
    box.querySelectorAll('.snapshot-entity-overlay').forEach(node=>out.push(['entity:'+String(node.dataset.entityId||''),node,'entity']));
    return out;
  }

  function ensureShadowLayer(box){
    let layer=box.querySelector('.snapshot-contact-shadow-layer-v3');
    if(!layer){layer=document.createElement('div');layer.className='snapshot-contact-shadow-layer-v3';box.appendChild(layer)}
    return layer;
  }

  function syncShadows(){
    const box=root();if(!box)return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    const layer=ensureShadowLayer(box),live=new Set();
    actorNodes(box).forEach(([key,node,role])=>{
      const cs=getComputedStyle(node),rect=node.getBoundingClientRect();
      if(cs.display==='none'||Number(cs.opacity||1)<.10||rect.width<3||rect.height<3)return;
      live.add(key);
      const x=rect.left-rr.left+rect.width*.5;
      const y=rect.bottom-rr.top-1;
      const ratio=role==='entity'?.30:.36;
      const width=clamp(Math.min(rect.width*ratio,rect.height*.22),28,84);
      const height=clamp(width*.16,6,12);
      let shadow=Array.from(layer.children).find(child=>child.dataset.shadowKey===key);
      if(!shadow){shadow=document.createElement('div');shadow.className='snapshot-contact-shadow-v3';shadow.dataset.shadowKey=key;layer.appendChild(shadow)}
      shadow.style.left=clamp(x,6,rr.width-6)+'px';
      shadow.style.top=clamp(y,5,rr.height-4)+'px';
      shadow.style.width=width+'px';
      shadow.style.height=height+'px';
      shadow.style.opacity='1';
    });
    Array.from(layer.children).forEach(node=>{if(!live.has(node.dataset.shadowKey||''))node.remove()});
  }

  function animateShadows(ms){
    const until=performance.now()+ms;
    function frame(){syncShadows();if(performance.now()<until)requestAnimationFrame(frame)}
    requestAnimationFrame(frame);
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
    animateShadows(700);
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
    spark.remove();syncShadows();
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

    lights.forEach(light=>{
      const cx=ox+Number(light.x||0)*nw*scale;
      const cy=oy+Number(light.y||0)*nh*scale;
      const coreW=clamp(Number(light.w||0)*nw*scale,3,bw*.34);
      const coreH=clamp(Number(light.h||0)*nh*scale,2,bh*.12);
      if(!Number.isFinite(cx)||!Number.isFinite(cy)||coreW<=0||coreH<=0)return;
      const horizontal=coreW>=coreH;
      const haloW=horizontal?clamp(coreW*2.35,18,bw*.55):clamp(Math.max(coreW*4.2,coreH*.62),14,bw*.24);
      const haloH=horizontal?clamp(Math.max(coreH*4.4,coreW*.24),12,bh*.30):clamp(coreH*2.35,18,bh*.50);
      const fixture=document.createElement('div');fixture.className='snapshot-light-fixture-v3';fixture.dataset.kind=String(light.kind||'linear');
      fixture.style.left=cx+'px';fixture.style.top=cy+'px';fixture.style.width=coreW+'px';fixture.style.height=coreH+'px';
      fixture.style.opacity=String(clamp(.66+Number(light.confidence||.5)*.26,.68,.93));
      const halo=document.createElement('div');halo.className='snapshot-light-halo-v3';halo.style.width=haloW+'px';halo.style.height=haloH+'px';
      const core=document.createElement('div');core.className='snapshot-light-core-v3';
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

  function sync(){scheduled=false;encounterClass();syncShadows();syncLights()}
  function schedule(){if(scheduled)return;scheduled=true;requestAnimationFrame(sync)}
  function attach(){
    const box=root();if(!box)return;
    new MutationObserver(schedule).observe(box,{childList:true,subtree:true,attributes:true,attributeFilter:['src','class']});
    if(window.ResizeObserver)new ResizeObserver(schedule).observe(box);
    window.__backroomCombatVisuals=Object.assign({},window.__backroomCombatVisuals||{},{hit,syncShadows,syncLights});
    schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attach,{once:true});else attach();
})();
</script>'''

marker = "<!-- SNAPSHOT_VISUAL_RUNTIME_V3 -->"
if marker not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("snapshot visual v3 expected exactly one </body>")
    html = html.replace("</body>", marker + "\n" + style + "\n" + script + "\n<!-- SNAPSHOT_VISUAL_RUNTIME_V3_END -->\n</body>", 1)

for forbidden in [
    "snapshot-light-bloom-v2",
    "snapshot-light-bloom{",
    "snapshot-ground-shadow-v2",
    "snapshot-ground-shadow{",
    "backroomLampPulse",
    "runtimeLampPulse",
    "detectFixtureCandidates(bg)",
    "FOOT_PROFILES",
]:
    if forbidden in html:
        raise RuntimeError(f"legacy snapshot visual code survived rebuild: {forbidden}")

for required in [
    marker,
    "scale:-1 1!important",
    "snapshot-contact-shadow-v3",
    "rect.left-rr.left+rect.width*.5",
    "snapshot-light-halo-v3",
    "snapshot-light-core-v3",
    "radial-gradient(ellipse at center",
    "window.__backroomCombatVisuals=Object.assign",
    "entity-slide-in-v3",
]:
    if required not in html:
        raise RuntimeError(f"snapshot visual v3 contract missing: {required}")

INDEX.write_text(html, encoding="utf-8")
print("Snapshot visual runtime v3 rebuilt from scratch: centered contact shadows, correct Kai facing, one-slot Entity rotation, hit feedback and halo-only lamp glow.")
