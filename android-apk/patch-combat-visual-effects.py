from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
START = "<!-- COMBAT_VISUAL_EFFECTS_BEGIN -->"
END = "<!-- COMBAT_VISUAL_EFFECTS_END -->"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


html = INDEX.read_text(encoding="utf-8")

# The hit effect is driven by the authoritative combat timeline. Only successful ATTACK/SKILL
# events with a target and HP loss animate; EVADE and narration never fake an impact.
hit_hook_old = """      appendCombatLine(combat,event,i);
      const kind=String(event.kind||'');"""
hit_hook_new = """      appendCombatLine(combat,event,i);
      if(window.__backroomCombatVisuals&&typeof window.__backroomCombatVisuals.hit==='function')await window.__backroomCombatVisuals.hit(event);
      const kind=String(event.kind||'');"""
html = replace_once(html, hit_hook_old, hit_hook_new, "combat timeline hit-reaction hook")

if START not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("combat visual effects: expected exactly one </body> anchor")
    block = r'''<!-- COMBAT_VISUAL_EFFECTS_BEGIN -->
<style id="combat-visual-effects-style">
/* Scene stack: photographed Level background -> detected lamp bloom -> pixel-ground shadows -> actors. */
.snapshot .snapshot-light-layer{position:absolute;inset:0;z-index:2;overflow:hidden;pointer-events:none}
.snapshot .snapshot-light-bloom{position:absolute;pointer-events:none;mix-blend-mode:screen;background:rgba(var(--lamp-rgb,255,248,220),.22);box-shadow:0 0 5px 2px rgba(var(--lamp-rgb,255,248,220),.48),0 0 15px 7px rgba(var(--lamp-rgb,255,248,220),.22);filter:brightness(1.18);opacity:.92}
.snapshot .snapshot-ground-shadow-layer{position:absolute;inset:0;z-index:3;overflow:hidden;pointer-events:none}
.snapshot .snapshot-ground-shadow{position:absolute;transform:translate(-50%,-50%);border-radius:50%;background:rgba(0,0,0,.76);border:1px solid rgba(0,0,0,.92);box-shadow:none;filter:none;pointer-events:none;image-rendering:pixelated;transition:left 70ms linear,top 70ms linear,width 70ms linear,opacity 80ms linear}
.snapshot .snapshot-character{z-index:4!important;transform-origin:50% 88%}
.snapshot .snapshot-party-entity-layer{z-index:5!important}
.snapshot .snapshot-party-entity-overlay{transform-origin:50% 88%}
.snapshot .snapshot-entities{z-index:6!important}
.snapshot .snapshot-entity-overlay{transform-origin:50% 88%}
.snapshot .combat-impact-burst{position:absolute;z-index:9;width:26px;height:26px;transform:translate(-50%,-50%);pointer-events:none;animation:combatImpactBurst .34s steps(4,end) both}
.snapshot .combat-impact-burst:before,.snapshot .combat-impact-burst:after{content:"";position:absolute;left:50%;top:50%;width:24px;height:4px;background:#fff8df;box-shadow:0 -7px 0 -1px rgba(255,224,154,.9),0 7px 0 -1px rgba(255,224,154,.74);transform:translate(-50%,-50%)}
.snapshot .combat-impact-burst:after{transform:translate(-50%,-50%) rotate(90deg)}
.snapshot .combat-hit-push-right{animation:combatHitPushRight .56s cubic-bezier(.18,.76,.3,1) both!important}
.snapshot .combat-hit-push-left{animation:combatHitPushLeft .56s cubic-bezier(.18,.76,.3,1) both!important}
@keyframes combatHitPushRight{0%{transform:translateX(0) rotate(0) scale(1);filter:brightness(1) contrast(1)}14%{transform:translateX(3px) rotate(-2deg) scaleX(.93) scaleY(1.04);filter:brightness(2.15) contrast(1.35)}30%{transform:translateX(18px) rotate(6deg) scaleX(.86) scaleY(1.08);filter:brightness(1.38) contrast(1.16)}52%{transform:translateX(10px) rotate(3deg) scaleX(.94) scaleY(1.04);filter:brightness(1.08)}72%{transform:translateX(-3px) rotate(-1.5deg) scale(1.015);filter:brightness(1)}100%{transform:translateX(0) rotate(0) scale(1);filter:brightness(1) contrast(1)}}
@keyframes combatHitPushLeft{0%{transform:translateX(0) rotate(0) scale(1);filter:brightness(1) contrast(1)}14%{transform:translateX(-3px) rotate(2deg) scaleX(.93) scaleY(1.04);filter:brightness(2.15) contrast(1.35)}30%{transform:translateX(-18px) rotate(-6deg) scaleX(.86) scaleY(1.08);filter:brightness(1.38) contrast(1.16)}52%{transform:translateX(-10px) rotate(-3deg) scaleX(.94) scaleY(1.04);filter:brightness(1.08)}72%{transform:translateX(3px) rotate(1.5deg) scale(1.015);filter:brightness(1)}100%{transform:translateX(0) rotate(0) scale(1);filter:brightness(1) contrast(1)}}
@keyframes combatImpactBurst{0%{opacity:0;transform:translate(-50%,-50%) scale(.35)}24%{opacity:1;transform:translate(-50%,-50%) scale(1.18)}58%{opacity:.9;transform:translate(-50%,-50%) scale(.92) rotate(10deg)}100%{opacity:0;transform:translate(-50%,-50%) scale(1.45) rotate(18deg)}}
</style>
<script>
(function(){
  const LIGHT_SAMPLE_W=96,LIGHT_SAMPLE_H=54,MAX_LIGHTS=6;
  const lightCache=new Map();
  let sceneScheduled=false;

  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const clamp=(value,min,max)=>Math.max(min,Math.min(max,value));
  function snapshot(){return document.getElementById('snapshot')}
  function byData(nodes,key,value){
    const wanted=String(value||'').toLowerCase();
    for(const node of nodes){if(String(node.dataset&&node.dataset[key]||'').toLowerCase()===wanted)return node}
    return null;
  }
  function combatantElement(id){
    const root=snapshot();if(!root||!id)return null;
    const raw=String(id),lower=raw.toLowerCase();
    if(lower==='kai')return root.querySelector('.snapshot-character');
    if(lower.indexOf('entity.')===0)return byData(root.querySelectorAll('.snapshot-entity-overlay'),'entityId',raw);
    return byData(root.querySelectorAll('.snapshot-party-entity-overlay'),'partyEntityId',lower);
  }
  function actorElements(){
    const root=snapshot();if(!root)return [];
    const out=[];
    const kai=root.querySelector('.snapshot-character');if(kai)out.push(['kai',kai]);
    root.querySelectorAll('.snapshot-party-entity-overlay').forEach(node=>out.push(['party:'+String(node.dataset.partyEntityId||''),node]));
    root.querySelectorAll('.snapshot-entity-overlay').forEach(node=>out.push(['entity:'+String(node.dataset.entityId||''),node]));
    return out;
  }
  function ensureLayer(className){
    const root=snapshot();if(!root)return null;
    let layer=root.querySelector('.'+className);
    if(!layer){layer=document.createElement('div');layer.className=className;root.appendChild(layer)}
    return layer;
  }
  function syncGroundShadows(){
    const root=snapshot();if(!root)return;
    const rootRect=root.getBoundingClientRect();if(rootRect.width<2||rootRect.height<2)return;
    const layer=ensureLayer('snapshot-ground-shadow-layer');if(!layer)return;
    const live=new Set();
    actorElements().forEach(([key,node])=>{
      const style=getComputedStyle(node);const rect=node.getBoundingClientRect();
      if(style.display==='none'||Number(style.opacity||1)<.12||rect.width<3||rect.height<3)return;
      live.add(key);
      let shadow=Array.from(layer.children).find(child=>child.dataset.shadowKey===key);
      if(!shadow){shadow=document.createElement('div');shadow.className='snapshot-ground-shadow';shadow.dataset.shadowKey=key;layer.appendChild(shadow)}
      const width=clamp(rect.width*.42,22,76);
      const height=clamp(width*.16,5,11);
      shadow.style.left=clamp(rect.left-rootRect.left+rect.width*.5,6,rootRect.width-6)+'px';
      shadow.style.top=clamp(rect.bottom-rootRect.top-2,5,rootRect.height-4)+'px';
      shadow.style.width=width+'px';shadow.style.height=height+'px';shadow.style.opacity='1';
    });
    Array.from(layer.children).forEach(node=>{if(!live.has(node.dataset.shadowKey||''))node.remove()});
  }
  function animateShadowFollow(duration){
    const until=performance.now()+duration;
    function frame(){syncGroundShadows();if(performance.now()<until)requestAnimationFrame(frame)}
    requestAnimationFrame(frame);
  }
  function addImpactBurst(target){
    const root=snapshot();if(!root||!target)return;
    const rr=root.getBoundingClientRect(),tr=target.getBoundingClientRect();
    const burst=document.createElement('div');burst.className='combat-impact-burst';
    burst.style.left=clamp(tr.left-rr.left+tr.width*.5,8,rr.width-8)+'px';
    burst.style.top=clamp(tr.top-rr.top+tr.height*.42,8,rr.height-8)+'px';
    root.appendChild(burst);setTimeout(()=>burst.remove(),420);
  }
  async function hit(event){
    if(!event||!event.targetId)return;
    const kind=String(event.kind||'');
    if(kind!=='ATTACK'&&kind!=='SKILL')return;
    if(!/-\s*\d+\s*HP/i.test(String(event.text||'')))return;
    const target=combatantElement(event.targetId);if(!target)return;
    const attacker=combatantElement(event.actorId);
    const targetRect=target.getBoundingClientRect();
    const attackerRect=attacker&&attacker.getBoundingClientRect();
    const targetIsEntity=String(event.targetId).toLowerCase().indexOf('entity.')===0;
    const pushRight=attackerRect&&attackerRect.width>0?(attackerRect.left+attackerRect.width*.5)<(targetRect.left+targetRect.width*.5):!targetIsEntity;
    target.classList.remove('combat-hit-push-left','combat-hit-push-right');
    void target.offsetWidth;
    target.classList.add(pushRight?'combat-hit-push-right':'combat-hit-push-left');
    addImpactBurst(target);animateShadowFollow(620);
    await sleep(560);
    target.classList.remove('combat-hit-push-left','combat-hit-push-right');
    syncGroundShadows();
  }

  function rgbaAt(data,index){return [data[index],data[index+1],data[index+2]]}
  function luminance(rgb){return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2]}
  function isFixturePixel(data,index){
    const rgb=rgbaAt(data,index),max=Math.max(rgb[0],rgb[1],rgb[2]),min=Math.min(rgb[0],rgb[1],rgb[2]);
    return luminance(rgb)>=205&&(max-min)<=105;
  }
  function detectFixtureCandidates(bg){
    const canvas=document.createElement('canvas');canvas.width=LIGHT_SAMPLE_W;canvas.height=LIGHT_SAMPLE_H;
    const ctx=canvas.getContext('2d',{willReadFrequently:true});if(!ctx)return [];
    try{ctx.drawImage(bg,0,0,LIGHT_SAMPLE_W,LIGHT_SAMPLE_H)}catch(ignore){return []}
    let pixels;try{pixels=ctx.getImageData(0,0,LIGHT_SAMPLE_W,LIGHT_SAMPLE_H).data}catch(ignore){return []}
    const size=LIGHT_SAMPLE_W*LIGHT_SAMPLE_H,mask=new Uint8Array(size),seen=new Uint8Array(size);
    for(let i=0;i<size;i++){if(isFixturePixel(pixels,i*4))mask[i]=1}
    const found=[];
    for(let start=0;start<size;start++){
      if(!mask[start]||seen[start])continue;
      const stack=[start];seen[start]=1;let area=0,minX=LIGHT_SAMPLE_W,maxX=0,minY=LIGHT_SAMPLE_H,maxY=0,sumLum=0,sumR=0,sumG=0,sumB=0;
      while(stack.length){
        const p=stack.pop(),x=p%LIGHT_SAMPLE_W,y=(p/LIGHT_SAMPLE_W)|0,idx=p*4,r=pixels[idx],g=pixels[idx+1],b=pixels[idx+2];
        area++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);sumLum+=.2126*r+.7152*g+.0722*b;sumR+=r;sumG+=g;sumB+=b;
        if(x>0){const q=p-1;if(mask[q]&&!seen[q]){seen[q]=1;stack.push(q)}}
        if(x+1<LIGHT_SAMPLE_W){const q=p+1;if(mask[q]&&!seen[q]){seen[q]=1;stack.push(q)}}
        if(y>0){const q=p-LIGHT_SAMPLE_W;if(mask[q]&&!seen[q]){seen[q]=1;stack.push(q)}}
        if(y+1<LIGHT_SAMPLE_H){const q=p+LIGHT_SAMPLE_W;if(mask[q]&&!seen[q]){seen[q]=1;stack.push(q)}}
      }
      const w=maxX-minX+1,h=maxY-minY+1,boxArea=w*h,fill=area/Math.max(1,boxArea),aspect=w/Math.max(1,h),avg=sumLum/Math.max(1,area);
      if(area<2||area>size*.075||fill<.42||minY>LIGHT_SAMPLE_H*.86)continue;
      const fixtureShape=(aspect>=1.65&&aspect<=14&&w>=4)||(aspect<=.72&&h>=4)||(area<=16&&aspect>=.72&&aspect<=1.4&&avg>=238);
      if(!fixtureShape)continue;
      let ringLum=0,ringCount=0;
      for(let y=Math.max(0,minY-3);y<=Math.min(LIGHT_SAMPLE_H-1,maxY+3);y++)for(let x=Math.max(0,minX-3);x<=Math.min(LIGHT_SAMPLE_W-1,maxX+3);x++){
        if(x>=minX&&x<=maxX&&y>=minY&&y<=maxY)continue;
        ringLum+=luminance(rgbaAt(pixels,(y*LIGHT_SAMPLE_W+x)*4));ringCount++;
      }
      const surround=ringCount?ringLum/ringCount:0,contrast=avg-surround;
      if(contrast<18&&avg<242)continue;
      found.push({x:minX,y:minY,w,h,r:Math.round(sumR/area),g:Math.round(sumG/area),b:Math.round(sumB/area),score:contrast*Math.sqrt(area)*(aspect>=1.65?1.15:1)});
    }
    found.sort((a,b)=>b.score-a.score);
    const kept=[];
    for(const item of found){
      const overlaps=kept.some(other=>{
        const ax=item.x+item.w/2,ay=item.y+item.h/2,bx=other.x+other.w/2,by=other.y+other.h/2;
        return Math.abs(ax-bx)<Math.max(item.w,other.w)*.55&&Math.abs(ay-by)<Math.max(item.h,other.h)*.7;
      });
      if(!overlaps)kept.push(item);if(kept.length>=MAX_LIGHTS)break;
    }
    return kept;
  }
  function renderDetectedLights(bg,candidates){
    const root=snapshot();if(!root||!bg)return;
    let layer=root.querySelector('.snapshot-light-layer');if(layer)layer.remove();
    if(!candidates.length)return;
    layer=document.createElement('div');layer.className='snapshot-light-layer';root.appendChild(layer);
    const boxW=root.clientWidth,boxH=root.clientHeight,nw=bg.naturalWidth||1,nh=bg.naturalHeight||1;
    if(boxW<2||boxH<2)return;
    const scale=Math.max(boxW/nw,boxH/nh),drawW=nw*scale,drawH=nh*scale,offsetX=(boxW-drawW)/2,offsetY=(boxH-drawH)/2;
    candidates.forEach(item=>{
      const sx=item.x/LIGHT_SAMPLE_W*nw,sy=item.y/LIGHT_SAMPLE_H*nh,sw=item.w/LIGHT_SAMPLE_W*nw,sh=item.h/LIGHT_SAMPLE_H*nh;
      const glow=document.createElement('div');glow.className='snapshot-light-bloom';
      const pad=Math.max(2,Math.min(8,sw*scale*.13));
      glow.style.left=(offsetX+sx*scale-pad)+'px';glow.style.top=(offsetY+sy*scale-pad)+'px';glow.style.width=(sw*scale+pad*2)+'px';glow.style.height=(sh*scale+pad*2)+'px';
      glow.style.setProperty('--lamp-rgb',item.r+','+item.g+','+item.b);layer.appendChild(glow);
    });
  }
  function syncLights(){
    const root=snapshot();if(!root)return;
    const bg=root.querySelector('.snapshot-bg');if(!bg)return;
    const src=bg.currentSrc||bg.src||'';if(!src)return;
    const run=()=>{
      if(!bg.naturalWidth||!bg.naturalHeight)return;
      let candidates=lightCache.get(src);
      if(!candidates){candidates=detectFixtureCandidates(bg);lightCache.set(src,candidates)}
      renderDetectedLights(bg,candidates);
    };
    if(bg.complete)run();else bg.addEventListener('load',run,{once:true});
  }
  function scheduleSceneSync(){
    if(sceneScheduled)return;sceneScheduled=true;
    requestAnimationFrame(()=>{sceneScheduled=false;syncGroundShadows();syncLights()});
  }
  function attach(){
    const root=snapshot();if(!root)return;
    new MutationObserver(scheduleSceneSync).observe(root,{childList:true,subtree:true,attributes:true,attributeFilter:['src','class']});
    if(window.ResizeObserver)new ResizeObserver(scheduleSceneSync).observe(root);
    scheduleSceneSync();
  }
  window.__backroomCombatVisuals={hit,syncGroundShadows,syncLights};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attach,{once:true});else attach();
})();
</script>
<!-- COMBAT_VISUAL_EFFECTS_END -->'''
    html = html.replace("</body>", block + "\n</body>", 1)

required = [
    START,
    "snapshot-ground-shadow",
    "combat-hit-push-right",
    "combat-impact-burst",
    "detectFixtureCandidates(bg)",
    "snapshot-light-bloom",
    "window.__backroomCombatVisuals.hit(event)",
    "kind!=='ATTACK'&&kind!=='SKILL'",
    "-\\s*\\d+\\s*HP",
]
for marker in required:
    if marker not in html:
        raise RuntimeError(f"combat visual effect contract missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("Combat visual effects applied: directional hit reactions, pixel ellipse ground shadows, and runtime lamp-fixture bloom detection.")
