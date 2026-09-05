from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old = '''  function renderDetectedLights(bg,candidates){
    const root=snapshot();if(!root||!bg)return;
    let layer=root.querySelector('.snapshot-light-layer');if(layer)layer.remove();
    if(!candidates.length)return;
    layer=document.createElement('div');layer.className='snapshot-light-layer';root.appendChild(layer);
    const boxW=root.clientWidth,boxH=root.clientHeight,nw=bg.naturalWidth||1,nh=bg.naturalHeight||1;
    if(boxW<2||boxH<2)return;
'''
new = '''  function renderDetectedLights(bg,candidates){
    const root=snapshot();if(!root||!bg)return;
    const boxW=root.clientWidth,boxH=root.clientHeight,nw=bg.naturalWidth||1,nh=bg.naturalHeight||1;
    if(boxW<2||boxH<2)return;
    const src=bg.currentSrc||bg.src||'';
    const lightSig=src+'|'+Math.round(boxW)+'x'+Math.round(boxH);
    let layer=root.querySelector('.snapshot-light-layer');
    if(layer&&layer.dataset.lightSig===lightSig)return;
    if(layer)layer.remove();
    if(!candidates.length)return;
    layer=document.createElement('div');layer.className='snapshot-light-layer';layer.dataset.lightSig=lightSig;root.appendChild(layer);
'''

if new not in html:
    count = html.count(old)
    if count != 1:
        raise RuntimeError(f"lamp bloom loop guard: expected exactly one anchor, found {count}")
    html = html.replace(old, new, 1)

# Smooth deterministic pulse: detected lamps breathe from a soft glow to a stronger bloom and back.
static_bloom = ".snapshot .snapshot-light-bloom{position:absolute;pointer-events:none;mix-blend-mode:screen;background:rgba(var(--lamp-rgb,255,248,220),.22);box-shadow:0 0 5px 2px rgba(var(--lamp-rgb,255,248,220),.48),0 0 15px 7px rgba(var(--lamp-rgb,255,248,220),.22);filter:brightness(1.18);opacity:.92}"
pulsing_bloom = ".snapshot .snapshot-light-bloom{position:absolute;pointer-events:none;mix-blend-mode:screen;background:rgba(var(--lamp-rgb,255,248,220),.22);box-shadow:0 0 5px 2px rgba(var(--lamp-rgb,255,248,220),.48),0 0 15px 7px rgba(var(--lamp-rgb,255,248,220),.22);filter:brightness(1.18);opacity:.92;animation:backroomLampPulse 2.8s ease-in-out infinite alternate}@keyframes backroomLampPulse{0%{opacity:.48;filter:brightness(.92);box-shadow:0 0 3px 1px rgba(var(--lamp-rgb,255,248,220),.24),0 0 8px 3px rgba(var(--lamp-rgb,255,248,220),.12)}100%{opacity:1;filter:brightness(1.42);box-shadow:0 0 7px 3px rgba(var(--lamp-rgb,255,248,220),.66),0 0 22px 10px rgba(var(--lamp-rgb,255,248,220),.34)}}"
if pulsing_bloom not in html:
    if html.count(static_bloom) != 1:
        raise RuntimeError(f"lamp pulse style: expected exactly one static bloom anchor, found {html.count(static_bloom)}")
    html = html.replace(static_bloom, pulsing_bloom, 1)

for token in [
    "layer.dataset.lightSig===lightSig",
    "layer.dataset.lightSig=lightSig",
    "const lightSig=src+'|'",
    "animation:backroomLampPulse 2.8s ease-in-out infinite alternate",
    "@keyframes backroomLampPulse",
]:
    if token not in html:
        raise RuntimeError(f"lamp bloom loop guard/pulse contract missing: {token}")

INDEX.write_text(html, encoding="utf-8")
print("Lamp bloom loop guard + smooth low-to-high pulse applied.")
