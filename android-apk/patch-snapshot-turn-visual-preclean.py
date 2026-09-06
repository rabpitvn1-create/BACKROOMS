from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Snapshot turn preclean {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Normalize the current right-facing encounter CSS first. Older PR #419 branches still carried
# the mirrored variant; current main already has the normalized rule, so this stays idempotent.
old_css = ".snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,7%)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:left bottom!important;scale:-1 1!important}"
new_css = ".snapshot.entity-encounter-present .snapshot-character{left:var(--stage-left,auto)!important;right:auto!important;bottom:var(--stage-bottom,0px)!important;object-position:right bottom!important;scale:1 1!important}"
if new_css not in html:
    if html.count(old_css) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one reversed Character CSS rule, found {html.count(old_css)}")
    html = html.replace(old_css, new_css, 1)
if old_css in html:
    raise RuntimeError("Snapshot turn preclean failed to remove reversed Character CSS")

# PR #419 was authored against the earlier Snapshot v3 runtime. Main subsequently gained a newer
# visible-bounds stage implementation before #419 was merged. Adapt that known runtime shape into
# the strict legacy anchors consumed by the final contract instead of weakening the finalizer.
if "  const GROUND_PROFILES=" not in html:
    marker = "  const STAGE_PROFILES="
    if html.count(marker) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one STAGE_PROFILES declaration, found {html.count(marker)}")
    html = html.replace(marker, "  const GROUND_PROFILES=", 1)

current_profile = "  function stageProfile(node){return STAGE_PROFILES[assetKey(node)]||{centerX:.5,bottomY:.96,visibleMinX:0,visibleMaxX:1,sourceWidth:1,sourceHeight:1}}\n"
legacy_profile = "  function groundProfile(node){return GROUND_PROFILES[assetKey(node)]||{centerX:.5,bottomY:.96,contactWidth:.20}}\n"
html = replace_once(html, current_profile, legacy_profile, "profile lookup compatibility")

visual_center = '''  function visualCenterX(node,role,p){
    const raw=clamp(Number(p.centerX)||.5,0,1);
    return role==='kai'&&root()&&root().classList.contains('entity-encounter-present')?1-raw:raw;
  }

'''
if visual_center not in html:
    anchor = "  function syncStage(){\n"
    if html.count(anchor) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one syncStage anchor, found {html.count(anchor)}")
    html = html.replace(anchor, visual_center + anchor, 1)

current_stage = '''  function syncStage(){
    const box=root();if(!box||!box.classList.contains('entity-encounter-present'))return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    const targetY=rr.height*.965;
    actorNodes(box).forEach(([,node,role])=>{
      const rect=node.getBoundingClientRect();
      if(rect.width<3||rect.height<3)return;
      const p=groundProfile(node);
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
'''
legacy_stage = '''  function syncStage(){
    const box=root();if(!box||!box.classList.contains('entity-encounter-present'))return;
    const rr=box.getBoundingClientRect();if(rr.width<2||rr.height<2)return;
    actorNodes(box).forEach(([,node,role])=>{
      const rect=node.getBoundingClientRect();
      if(rect.width<3||rect.height<3)return;
      const p=groundProfile(node);
      const targetX=rr.width*(role==='entity'?.76:.24);
      const targetY=rr.height*.965;
      const centerX=visualCenterX(node,role,p);
      const left=targetX-rect.width*centerX;
      const bottom=rr.height-targetY-rect.height*(1-clamp(Number(p.bottomY)||.96,.55,1));
      node.style.setProperty('--stage-left',left.toFixed(2)+'px');
      node.style.setProperty('--stage-bottom',bottom.toFixed(2)+'px');
    });
  }
'''
html = replace_once(html, current_stage, legacy_stage, "stage compatibility")

# Reintroduce the contact-shadow hooks that the final V4/V5 contract intentionally upgrades to
# a larger pixel sprite. The newer v3 runtime had removed these hooks while keeping the same
# authoritative alpha-derived stage profiles.
shadow_style = '''/* Contact shadow compatibility hooks; V5 replaces the paint with pixel art. */
.snapshot .snapshot-contact-shadow-layer-v3{position:absolute;inset:0;z-index:3;overflow:hidden;pointer-events:none}
.snapshot .snapshot-contact-shadow-v3{position:absolute;transform:translate(-50%,-50%);border-radius:50%;background:radial-gradient(ellipse at center,rgba(0,0,0,.64) 0 27%,rgba(0,0,0,.42) 48%,rgba(0,0,0,.17) 70%,rgba(0,0,0,0) 100%);filter:blur(.65px);pointer-events:none;transition:left 55ms linear,top 55ms linear,width 55ms linear,opacity 65ms linear}

'''
if ".snapshot .snapshot-contact-shadow-layer-v3{" not in html:
    style_anchor = "/* Hit feedback comes only from authoritative ATTACK/SKILL timeline events. */\n"
    if html.count(style_anchor) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one hit-feedback style anchor, found {html.count(style_anchor)}")
    html = html.replace(style_anchor, shadow_style + style_anchor, 1)

shadow_script = '''  function ensureShadowLayer(box){
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
      const p=stageProfile(node);
      const x=rect.left-rr.left+rect.width*visualCenterX(node,role,p);
      const y=rect.top-rr.top+rect.height*clamp(Number(p.bottomY)||.96,.55,1)+1.5;
      const contactWidth=rect.width*clamp(Number(p.contactWidth)||.20,.06,.72);
      const width=clamp(contactWidth*1.22,24,role==='entity'?118:94);
      const height=clamp(width*.18,6,15);
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
    function frame(){syncStage();syncShadows();if(performance.now()<until)requestAnimationFrame(frame)}
    requestAnimationFrame(frame);
  }

'''
if "  function ensureShadowLayer(box){\n" not in html:
    script_anchor = "  function combatant(id){\n"
    if html.count(script_anchor) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one combatant anchor, found {html.count(script_anchor)}")
    html = html.replace(script_anchor, shadow_script + script_anchor, 1)

html = replace_once(
    html,
    "    const spark=document.createElement('div');spark.className='snapshot-hit-spark-v3';\n",
    "    const spark=document.createElement('div');spark.className='snapshot-hit-spark-v3';\n",
    "hit spark anchor",
)
if "    animateShadows(700);\n" not in html:
    hit_anchor = "    spark.style.height=spark.style.width;box.appendChild(spark);\n"
    if html.count(hit_anchor) != 1:
        raise RuntimeError(f"Snapshot turn preclean expected one hit shadow anchor, found {html.count(hit_anchor)}")
    html = html.replace(hit_anchor, hit_anchor + "    animateShadows(700);\n", 1)

html = replace_once(
    html,
    "    spark.remove();syncStage();\n",
    "    spark.remove();syncStage();syncShadows();\n",
    "post-hit shadow sync",
)
html = replace_once(
    html,
    "  function sync(){scheduled=false;encounterClass();syncStage();syncLights()}\n",
    "  function sync(){scheduled=false;encounterClass();syncStage();syncShadows();syncLights()}\n",
    "scheduled shadow sync",
)
html = replace_once(
    html,
    "    window.__backroomCombatVisuals=Object.assign({},window.__backroomCombatVisuals||{},{hit,syncStage,syncLights});\n",
    "    window.__backroomCombatVisuals=Object.assign({},window.__backroomCombatVisuals||{},{hit,syncStage,syncShadows,syncLights});\n",
    "shadow runtime export",
)

INDEX.write_text(html, encoding="utf-8")
print("Snapshot turn preclean reconciled current Snapshot v3 with the final V4/V5 contract and restored contact-shadow hooks.")
