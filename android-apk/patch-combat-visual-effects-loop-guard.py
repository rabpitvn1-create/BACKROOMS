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

for token in [
    "layer.dataset.lightSig===lightSig",
    "layer.dataset.lightSig=lightSig",
    "const lightSig=src+'|'",
]:
    if token not in html:
        raise RuntimeError(f"lamp bloom loop guard contract missing: {token}")

INDEX.write_text(html, encoding="utf-8")
print("Lamp bloom mutation loop guard applied: unchanged snapshot lights are not rebuilt each observer frame.")
