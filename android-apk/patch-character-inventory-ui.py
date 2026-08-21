from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

party_old = '<div class="card"><h2>Party</h2><div class="chips" id="party"></div></div>\n<div class="card"><h2>Inventory</h2><div class="chips" id="inventory"></div></div>'
party_new = '''<div class="card"><h2>Party</h2><div id="party" class="party-grid"></div></div>
<div id="characterInventoryView" class="character-inventory-view" hidden>
  <div class="character-inventory-head"><button type="button" id="characterInventoryBack">← Trở lại</button><div><div class="eyebrow">CHARACTER INVENTORY</div><h2 id="characterInventoryName">Kai Akechi</h2></div></div>
  <div class="character-profile">
    <img id="characterInventoryAvatar" src="avatars/kai_avatar.png" alt="Kai Akechi">
    <div><div class="inventory-capacity" id="characterInventoryCapacity">0 / 9 loại vật phẩm</div><div class="inventory-limit">Tối đa ×999 mỗi loại</div></div>
  </div>
  <div class="character-section"><h3>Equipment</h3><div class="equipment-list"><span>Vũ khí cá nhân</span><span>Bộ giáp</span><span>Nhẫn Omnivault</span></div></div>
  <div class="character-section"><h3>Inventory</h3><div class="chips" id="characterInventoryItems"></div></div>
</div>'''
if party_new not in html:
    if party_old not in html:
        raise RuntimeError("Party + global Inventory panel anchor not found")
    html = html.replace(party_old, party_new, 1)

css_anchor = '.chips span{border:1px solid #313940;padding:5px 7px;font-size:12px}'
css_extra = '''.party-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.party-member{border:1px solid #313940;background:#111519;padding:7px;display:grid;gap:6px;text-align:center;cursor:pointer}.party-member img{width:100%;aspect-ratio:1/1;object-fit:cover;border:1px solid #333b42}.party-member strong{font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-member button{padding:7px;font-size:10px}.character-inventory-view{position:fixed;inset:0;z-index:50;background:#080a0c;padding:14px;overflow:auto}.character-inventory-head{display:flex;align-items:center;gap:12px;border-bottom:1px solid #2b3137;padding-bottom:12px}.character-inventory-head button{width:auto}.character-inventory-head h2{margin:3px 0 0}.character-profile{display:grid;grid-template-columns:110px 1fr;gap:14px;align-items:center;padding:16px 0}.character-profile img{width:110px;height:110px;object-fit:cover;border:1px solid #3b444c}.inventory-capacity{font-size:18px;font-weight:800}.inventory-limit{margin-top:5px;color:#8f9aa4;font-size:12px}.character-section{border:1px solid #2b3137;background:#0e1114;padding:14px;margin-top:10px}.character-section h3{margin:0 0 10px;font-size:12px;letter-spacing:.12em;text-transform:uppercase}.equipment-list{display:grid;gap:7px}.equipment-list span{border:1px solid #313940;padding:9px;color:#dce2e5}@media(max-width:520px){.party-grid{grid-template-columns:repeat(4,1fr)}.party-member{padding:4px}.party-member button{padding:5px 2px;font-size:9px}}'''
if css_extra not in html:
    if css_anchor not in html:
        raise RuntimeError("CSS anchor not found")
    html = html.replace(css_anchor, css_anchor + css_extra, 1)

script_anchor = '</script>\n</body>'
script_extra = r'''
<script>
(function(){
  const party=document.getElementById('party');
  const view=document.getElementById('characterInventoryView');
  const items=document.getElementById('characterInventoryItems');
  const capacity=document.getElementById('characterInventoryCapacity');
  const back=document.getElementById('characterInventoryBack');
  function kaiItems(){return Array.isArray(state&&state.inventory)?state.inventory:[]}
  function renderKaiInventory(){const inv=kaiItems();capacity.textContent=inv.length+' / 9 loại vật phẩm';items.innerHTML=chips(inv)}
  function openKai(){renderKaiInventory();view.hidden=false}
  function renderPartyCards(){
    if(!party)return;
    party.innerHTML='<div class="party-member" data-character="kai"><img src="avatars/kai_avatar.png" alt="Kai Akechi"><strong>Kai Akechi</strong><button type="button">Inventory</button></div>';
    const card=party.querySelector('[data-character="kai"]');
    if(card)card.addEventListener('click',openKai);
  }
  if(back)back.addEventListener('click',()=>{view.hidden=true});
  const priorRender=window.render;
  if(typeof priorRender==='function')window.render=function(){priorRender();renderPartyCards();if(view&&!view.hidden)renderKaiInventory()};
  renderPartyCards();
})();
</script>
'''
if script_extra not in html:
    if script_anchor not in html:
        raise RuntimeError("script footer anchor not found")
    html = html.replace(script_anchor, '</script>\n' + script_extra + '</body>', 1)

# The old global inventory element must no longer remain in the main layout.
if '<div class="card"><h2>Inventory</h2>' in html:
    raise RuntimeError("Global Inventory panel still present")

INDEX.write_text(html, encoding="utf-8")
print("Kai Party avatar and character-scoped Inventory view applied; global Inventory panel removed.")
