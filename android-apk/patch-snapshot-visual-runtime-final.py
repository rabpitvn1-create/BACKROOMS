from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
ASSETS = APP / "src/main/assets"
INDEX = ASSETS / "index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def sprite_foot_profiles() -> dict[str, dict[str, float]]:
    try:
        import tensorflow as tf
    except Exception as error:
        raise RuntimeError(f"sprite foot profile generation requires TensorFlow: {error}") from error
    roots = []
    for rel in ["kai_snapshot_overlay.png", "kai_snapshot_overlay_combat.png"]:
        path = ASSETS / rel
        if path.is_file():
            roots.append(path)
    for folder in [ASSETS / "entity_overlays", ASSETS / "party_entity_overlays"]:
        if folder.is_dir():
            roots.extend(sorted(folder.glob("*.png")))
    result = {}
    for path in roots:
        rgba = tf.io.decode_png(path.read_bytes(), channels=4).numpy()
        alpha = rgba[:, :, 3]
        ys, xs = (alpha > 18).nonzero()
        if len(xs) == 0:
            continue
        max_y = int(ys.max())
        band_start = max(0, max_y - max(2, int(round(rgba.shape[0] * 0.12))))
        band = (alpha[band_start:max_y + 1, :] > 18).nonzero()
        if len(band[1]) == 0:
            min_x = int(xs.min())
            max_x = int(xs.max())
        else:
            min_x = int(band[1].min())
            max_x = int(band[1].max())
        key = str(path.relative_to(ASSETS)).replace("\\", "/")
        result[key] = {
            "centerX": round((min_x + max_x + 1) / (2.0 * rgba.shape[1]), 6),
            "bottomY": round((max_y + 1) / float(rgba.shape[0]), 6),
            "footWidth": round((max_x - min_x + 1) / float(rgba.shape[1]), 6),
        }
    if not result:
        raise RuntimeError("no sprite foot profiles generated")
    return result


foot_profiles = sprite_foot_profiles()
html = INDEX.read_text(encoding="utf-8")

old_focus_entity = "  function focusEntity(enemyId){if(enemyId)currentEnemy=String(enemyId);applyVisualFocus()}"
new_focus_entity = '''  function beginEntityExit(enemyId){
    const root=box();if(!root)return;
    const wanted=String(enemyId||currentEnemy||'').toLowerCase();
    root.querySelectorAll('.snapshot-entity-overlay').forEach(img=>{
      if(!wanted||String(img.dataset.entityId||'').toLowerCase()===wanted){
        if(img.classList.contains('combat-active-entity'))img.classList.add('entity-slide-out');
      }
    });
  }
  function focusEntity(enemyId){
    if(!enemyId)return;
    currentEnemy=String(enemyId);
    applyVisualFocus();
    const root=box();if(!root)return;
    const wanted=currentEnemy.toLowerCase();
    root.querySelectorAll('.snapshot-entity-overlay').forEach(img=>{
      if(String(img.dataset.entityId||'').toLowerCase()!==wanted)return;
      img.classList.remove('entity-slide-out','entity-slide-in');
      void img.offsetWidth;
      img.classList.add('entity-slide-in');
      setTimeout(()=>img.classList.remove('entity-slide-in'),520);
    });
  }'''
html = replace_once(html, old_focus_entity, new_focus_entity, "Entity rotation controller")
html = replace_once(
    html,
    "      if(event.kind==='ENTITY_ENTER')focusEntity(event.enemyId);",
    "      if(event.kind==='ENTITY_DOWN')beginEntityExit(event.enemyId);\n      if(event.kind==='ENTITY_ENTER')focusEntity(event.enemyId);",
    "Entity down/enter visual lifecycle",
)

style = r'''<style id="runtime-authority-visual-final-style">
.snapshot.entity-encounter-present .snapshot-character{left:1.5%!important;right:auto!important;object-position:left bottom!important}
.snapshot.entity-encounter-present .snapshot-party-entity-overlay{left:1.5%!important;right:auto!important;max-width:48%!important}
.snapshot.entity-encounter-present:not(.combat-turn-managed) .snapshot-party-entity-overlay{opacity:0!important}
.snapshot .snapshot-entities{position:absolute!important;inset:0!important;display:block!important;z-index:6!important;overflow:hidden!important;pointer-events:none!important}
.snapshot .snapshot-entity-overlay{position:absolute!important;left:auto!important;right:1.5%!important;bottom:2.2%!important;width:auto!important;height:91%!important;max-width:50%!important;opacity:0;transform:translateX(52px) rotate(0deg) scale(.985);transform-origin:50% 92%!important;transition:opacity .30s ease,transform .38s cubic-bezier(.2,.8,.25,1)!important}
.snapshot:not(.combat-turn-managed) .snapshot-entity-overlay:first-child{opacity:1!important;transform:translateX(0) rotate(0) scale(1)!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.combat-active-entity{opacity:1!important;transform:translateX(0) rotate(0) scale(1)!important}
.snapshot.combat-turn-managed .snapshot-entity-overlay.entity-slide-out{opacity:0!important;transform:translateX(72px) rotate(2deg) scale(.96)!important}
.snapshot .snapshot-entity-overlay.entity-slide-in{animation:runtimeEntitySlideIn .48s cubic-bezier(.18,.82,.24,1) both!important}
@keyframes runtimeEntitySlideIn{0%{opacity:0;transform:translateX(76px) rotate(2deg) scale(.95)}62%{opacity:1;transform:translateX(-5px) rotate(-.5deg) scale(1.01)}100%{opacity:1;transform:translateX(0) rotate(0) scale(1)}}
.snapshot .snapshot-ground-shadow-layer{display:none!important}
.snapshot .snapshot-ground-shadow-layer-v2{position:absolute;inset:0;z-index:3;overflow:hidden;pointer-events:none}
.snapshot .snapshot-ground-shadow-v2{position:absolute;transform:translate(-50%,-50%);border-radius:50%;background:rgba(0,0,0,.52);filter:blur(.35px);pointer-events:none;transition:left 55ms linear,top 55ms linear,width 55ms linear,opacity 65ms linear}
.snapshot .combat-hit-flash-v2{position:absolute;z-index:12;pointer-events:none;border-radius:18%;background:radial-gradient(ellipse at center,rgba(255,248,224,.88) 0%,rgba(255,222,174,.38) 38%,rgba(255,190,140,0) 76%);mix-blend-mode:screen;animation:runtimeHitFlash .46s ease-out both}
.snapshot .combat-hit-react-v2{animation:runtimeHitReact .52s cubic-bezier(.16,.82,.28,1) both!important}
@keyframes runtimeHitReact{0%{filter:brightness(1) contrast(1);transform:translateX(0)}12%{filter:brightness(2.7) contrast(1.45);transform:translateX(0)}34%{filter:brightness(1.55) contrast(1.18);transform:translateX(var(--hit-x,16px))}58%{filter:brightness(1.12) contrast(1.06);transform:translateX(var(--hit-mid,8px))}78%{filter:brightness(1.02);transform:translateX(var(--hit-back,-3px))}100%{filter:brightness(1) contrast(1);transform:translateX(0)}}
@keyframes runtimeHitFlash{0%{opacity:0;transform:scale(.38)}22%{opacity:1;transform:scale(1.08)}100%{opacity:0;transform:scale(1.42)}}
.snapshot .snapshot-light-layer-v2{position:absolute;inset:0;z-index:2;overflow:hidden;pointer-events:none}
.snapshot .snapshot-light-bloom-v2{position:absolute;pointer-events:none;mix-blend-mode:screen;border-radius:18%;background:rgba(255,249,222,.30);box-shadow:0 0 8px 3px rgba(255,249,222,.68),0 0 30px 14px rgba(255,237,190,.36);animation:runtimeLampPulse 1.9s ease-in-out infinite alternate}
@keyframes runtimeLampPulse{0%{opacity:.38;filter:brightness(.95)}100%{opacity:1;filter:brightness(1.85)}}
</style>'''

script_template = r'''<script id="runtime-authority-visual-final-script">
(function(){
  const FOOT_PROFILES=__FOOT_PROFILES__;
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
  function profile(node){return FOOT_PROFILES[assetKey(node)]||{centerX:.5,bottomY:.92,footWidth:.22}}
  function actorNodes(box){
    const out=[];
    const kai=box.querySelector('.snapshot-character');if(kai)out.push(['kai',kai]);
    box.querySelectorAll('.snapshot-party-entity-overlay').forEach(n=>out.push(['party:'+String(n.dataset.partyEntityId||''),n]));
    box.querySelectorAll('.snapshot-entity-overlay').forEach(n=>out.push(['entity:'+String(n.dataset.entityId||''),n]));
    return out;
  }
  function ensureShadowLayer(box){
    let layer=box.querySelector('.snapshot-ground-shadow-layer-v2');
    if(!layer){layer=document.createElement('div');layer.className='snapshot-ground-shadow-layer-v2';box.appendChild(layer)}
    return layer;
  }
  function syncShadows(){
    const box=root();if(!box)return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    const layer=ensureShadowLayer(box),live=new Set();
    actorNodes(box).forEach(([key,node])=>{
      const cs=getComputedStyle(node),rect=node.getBoundingClientRect();
      if(cs.display==='none'||Number(cs.opacity||1)<.10||rect.width<3||rect.height<3)return;
      live.add(key);
      const p=profile(node);
      const x=rect.left-rr.left+rect.width*clamp(Number(p.centerX)||.5,0,1);
      const y=rect.top-rr.top+rect.height*clamp(Number(p.bottomY)||.92,.55,1);
      const visibleFoot=rect.width*clamp(Number(p.footWidth)||.22,.08,.65);
      const width=clamp(visibleFoot*1.18,18,62),height=clamp(width*.14,4,9);
      let shadow=Array.from(layer.children).find(n=>n.dataset.shadowKey===key);
      if(!shadow){shadow=document.createElement('div');shadow.className='snapshot-ground-shadow-v2';shadow.dataset.shadowKey=key;layer.appendChild(shadow)}
      shadow.style.left=clamp(x,5,rr.width-5)+'px';shadow.style.top=clamp(y+1,4,rr.height-3)+'px';
      shadow.style.width=width+'px';shadow.style.height=height+'px';shadow.style.opacity='1';
    });
    Array.from(layer.children).forEach(n=>{if(!live.has(n.dataset.shadowKey||''))n.remove()});
  }
  function animateShadows(ms){const until=performance.now()+ms;function tick(){syncShadows();if(performance.now()<until)requestAnimationFrame(tick)}requestAnimationFrame(tick)}
  function combatant(id){
    const box=root();if(!box||!id)return null;
    const wanted=String(id).toLowerCase();
    if(wanted==='kai')return box.querySelector('.snapshot-character');
    for(const n of box.querySelectorAll('.snapshot-entity-overlay'))if(String(n.dataset.entityId||'').toLowerCase()===wanted)return n;
    for(const n of box.querySelectorAll('.snapshot-party-entity-overlay'))if(String(n.dataset.partyEntityId||'').toLowerCase()===wanted)return n;
    return null;
  }
  async function hit(event){
    if(!event||!event.targetId)return;
    const kind=String(event.kind||'');if(kind!=='ATTACK'&&kind!=='SKILL')return;
    const target=combatant(event.targetId);if(!target)return;
    const attacker=combatant(event.actorId),box=root();if(!box)return;
    const tr=target.getBoundingClientRect(),ar=attacker&&attacker.getBoundingClientRect(),rr=box.getBoundingClientRect();
    const direction=ar&&ar.width>0&&ar.left+ar.width*.5>tr.left+tr.width*.5?-1:1;
    target.style.setProperty('--hit-x',(direction*20)+'px');
    target.style.setProperty('--hit-mid',(direction*8)+'px');
    target.style.setProperty('--hit-back',(direction*-3)+'px');
    target.classList.remove('combat-hit-react-v2');void target.offsetWidth;target.classList.add('combat-hit-react-v2');
    const flash=document.createElement('div');flash.className='combat-hit-flash-v2';
    flash.style.left=clamp(tr.left-rr.left+tr.width*.18,4,rr.width-8)+'px';
    flash.style.top=clamp(tr.top-rr.top+tr.height*.12,4,rr.height-8)+'px';
    flash.style.width=clamp(tr.width*.64,34,170)+'px';flash.style.height=clamp(tr.height*.72,44,190)+'px';
    box.appendChild(flash);animateShadows(620);
    await sleep(520);target.classList.remove('combat-hit-react-v2');flash.remove();syncShadows();
  }
  function encounterClass(){
    const box=root();if(!box)return;
    box.classList.toggle('entity-encounter-present',!!box.querySelector('.snapshot-entities .snapshot-entity-overlay'));
  }
  function renderLights(bg,lights){
    const box=root();if(!box||!bg)return;
    const bw=box.clientWidth,bh=box.clientHeight,nw=bg.naturalWidth||1,nh=bg.naturalHeight||1;
    if(bw<2||bh<2)return;
    const src=String(bg.currentSrc||bg.src||'');
    const sig=src+'|'+Math.round(bw)+'x'+Math.round(bh)+'|'+String(Array.isArray(lights)?lights.length:0);
    let layer=box.querySelector('.snapshot-light-layer-v2');
    if(layer&&layer.dataset.lightSig===sig)return;
    if(layer)layer.remove();
    if(!Array.isArray(lights)||!lights.length)return;
    const scale=Math.max(bw/nw,bh/nh),drawW=nw*scale,drawH=nh*scale,ox=(bw-drawW)/2,oy=(bh-drawH)/2;
    layer=document.createElement('div');layer.className='snapshot-light-layer-v2';layer.dataset.lightSig=sig;box.appendChild(layer);
    lights.forEach(light=>{
      const sx=Number(light.x||0)*nw,sy=Number(light.y||0)*nh,sw=Number(light.w||0)*nw,sh=Number(light.h||0)*nh;
      if(sw<=0||sh<=0)return;
      const glow=document.createElement('div');glow.className='snapshot-light-bloom-v2';
      const pad=clamp(sw*scale*.17,3,12);
      glow.style.left=(ox+sx*scale-pad)+'px';glow.style.top=(oy+sy*scale-pad)+'px';
      glow.style.width=(sw*scale+pad*2)+'px';glow.style.height=(sh*scale+pad*2)+'px';
      glow.style.opacity=String(clamp(.45+Number(light.confidence||.5)*.55,.45,1));layer.appendChild(glow);
    });
    const legacy=box.querySelector('.snapshot-light-layer');if(legacy)legacy.style.display='none';
  }
  function syncLights(){
    const box=root();if(!box)return;const bg=box.querySelector('.snapshot-bg');if(!bg)return;
    const src=String(bg.currentSrc||bg.src||'');if(!src)return;
    const run=()=>{
      if(!bg.naturalWidth||!bg.naturalHeight)return;
      let parsed=lightCache.get(src);
      if(parsed===undefined){
        parsed=null;
        try{if(window.Android&&typeof Android.analyzeSnapshotLights==='function')parsed=JSON.parse(Android.analyzeSnapshotLights(src))}catch(ignore){}
        lightCache.set(src,parsed);
      }
      if(parsed&&Array.isArray(parsed.lights)&&parsed.lights.length)renderLights(bg,parsed.lights);
    };
    if(bg.complete)run();else bg.addEventListener('load',run,{once:true});
  }
  function sync(){scheduled=false;encounterClass();syncShadows();syncLights()}
  function schedule(){if(scheduled)return;scheduled=true;requestAnimationFrame(sync)}
  function attach(){
    const box=root();if(!box)return;
    new MutationObserver(schedule).observe(box,{childList:true,subtree:true,attributes:true,attributeFilter:['src','class']});
    if(window.ResizeObserver)new ResizeObserver(schedule).observe(box);
    if(window.__backroomCombatVisuals)window.__backroomCombatVisuals.hit=hit;
    schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attach,{once:true});else attach();
})();
</script>'''
script = script_template.replace("__FOOT_PROFILES__", json.dumps(foot_profiles, ensure_ascii=False, separators=(",", ":")))
marker = "<!-- RUNTIME_AUTHORITY_VISUAL_FINAL -->"
if marker not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Runtime visual finalizer expected exactly one </body>")
    html = html.replace("</body>", marker + "\n" + style + "\n" + script + "\n</body>", 1)

for token in [
    marker,
    "entity-encounter-present",
    "runtimeEntitySlideIn",
    "beginEntityExit(event.enemyId)",
    "combat-hit-react-v2",
    "snapshot-ground-shadow-layer-v2",
    "FOOT_PROFILES=",
    "Android.analyzeSnapshotLights(src)",
    "snapshot-light-bloom-v2",
]:
    if token not in html:
        raise RuntimeError(f"Runtime visual contract missing: {token}")
INDEX.write_text(html, encoding="utf-8")
print("Snapshot visual runtime finalized: one right-side Entity slot, slide rotation, hit reaction, alpha-foot shadows and LiteRT lamp bloom.")
