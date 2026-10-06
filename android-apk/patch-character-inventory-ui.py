from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

# Signature gear belongs to Equipment, never to the normal 9-slot Inventory.
legacy_signature_inventory = '''  inventory:[
    {name:"White Wraith Magnum"},
    {name:"Blackblood Armor & linked modules"},
    {name:"Omnivault Ring / Nhẫn Vạn Tàng"}
  ],'''
if legacy_signature_inventory in html:
    html = html.replace(legacy_signature_inventory, '  inventory:[],', 1)

party_old = '<div class="card"><h2>Party</h2><div class="chips" id="party"></div></div>\n<div class="card"><h2>Inventory</h2><div class="chips" id="inventory"></div></div>'
party_new = '''<div class="card"><h2>Party</h2><div class="party-time" id="partyTime"></div><div id="party" class="party-grid"><div class="party-member" data-character="kai"><img src="avatars/kai_avatar.png" alt="Kai Akechi"><strong>Kai Akechi</strong></div></div></div>
<div id="characterInventoryView" class="character-inventory-view" hidden>
  <div class="character-inventory-head"><button type="button" id="characterInventoryBack">← Trở lại</button><div><div class="eyebrow">CHARACTER DETAIL</div><h2 id="characterInventoryName">Kai Akechi</h2></div></div>
  <div class="character-profile">
    <img id="characterInventoryAvatar" src="avatars/kai_avatar.png" alt="Kai Akechi">
    <div><div class="inventory-capacity" id="characterInventoryCapacity">0 / 9 loại vật phẩm</div><div class="inventory-limit">Inventory của nhân vật đang chọn</div></div>
  </div>
  <div class="character-section"><h3>Status</h3><div class="character-status-list" id="characterStatusList"></div></div>
  <div class="character-section"><h3>Equipment</h3><div class="equipment-list" id="characterEquipmentList"><span>W.W Magnum</span><span>Blackblood Armor & linked modules</span><span>Omnivault Ring</span></div></div>
  <div class="character-section"><h3>Inventory</h3><div class="chips" id="characterInventoryItems"></div></div>
</div>'''
if party_new not in html:
    if party_old not in html:
        raise RuntimeError("Party + global Inventory panel anchor not found")
    html = html.replace(party_old, party_new, 1)

# Removing the global Inventory panel made inventoryEl null. The old renderer still wrote to
# inventoryEl unconditionally, throwing before the Prologue log and Party enhancement could render.
# Make the legacy renderer tolerant of the intentionally removed element.
unsafe_inventory_render = 'inventoryEl.innerHTML=chips(state.inventory);'
safe_inventory_render = 'if(inventoryEl)inventoryEl.innerHTML=chips(state.inventory);'
if safe_inventory_render not in html:
    if unsafe_inventory_render not in html:
        raise RuntimeError("Legacy inventory renderer anchor not found")
    html = html.replace(unsafe_inventory_render, safe_inventory_render, 1)

css_anchor = '.chips span{border:1px solid #313940;padding:5px 7px;font-size:12px}'
css_extra = '''.party-time{min-height:16px;margin:-2px 0 9px;color:#78838c;font-size:11px}.party-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.party-member{border:1px solid #313940;background:#111519;padding:7px;display:grid;gap:6px;text-align:center;cursor:pointer;min-width:0}.party-member img,.party-avatar-placeholder{width:100%;aspect-ratio:1/1;object-fit:cover;border:1px solid #333b42;background:#0b0e11}.party-avatar-placeholder{display:grid;place-items:center;color:#78838c;font-size:18px;font-weight:800}.party-member strong{font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-member .party-state{color:#84909a;font-size:9px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-member.empty{cursor:default;opacity:.45}.party-member.empty .party-avatar-placeholder{font-size:11px}.character-inventory-view{position:fixed;inset:0;z-index:50;background:#080a0c;padding:14px;overflow:auto}.character-inventory-head{display:flex;align-items:center;gap:12px;border-bottom:1px solid #2b3137;padding-bottom:12px}.character-inventory-head button{width:auto}.character-inventory-head h2{margin:3px 0 0}.character-profile{display:grid;grid-template-columns:110px 1fr;gap:14px;align-items:center;padding:16px 0}.character-profile img{width:110px;height:110px;object-fit:cover;border:1px solid #3b444c}.inventory-capacity{font-size:18px;font-weight:800}.inventory-limit{margin-top:5px;color:#8f9aa4;font-size:12px}.character-section{border:1px solid #2b3137;background:#0e1114;padding:14px;margin-top:10px}.character-section h3{margin:0 0 10px;font-size:12px;letter-spacing:.12em;text-transform:uppercase}.equipment-list,.character-status-list{display:grid;gap:7px}.equipment-list span,.character-status-row{border:1px solid #313940;padding:9px;color:#dce2e5}.character-status-row{display:grid;grid-template-columns:minmax(90px,35%) 1fr;gap:8px}.character-status-row b{color:#78838c;font-weight:400}.status-normal{color:#9aa4ad}.status-warning{color:#d6c28a}.status-danger{color:#df9a9a}@media(max-width:520px){.party-grid{grid-template-columns:repeat(4,1fr)}.party-member{padding:4px}.party-member strong{font-size:9px}.party-member .party-state{font-size:8px}}'''
if css_extra not in html:
    if css_anchor not in html:
        raise RuntimeError("CSS anchor not found")
    html = html.replace(css_anchor, css_anchor + css_extra, 1)

script_anchor = '</script>\n</body>'
script_extra = r'''
<script>
(function(){
  const party=document.getElementById('party');
  const partyTime=document.getElementById('partyTime');
  const view=document.getElementById('characterInventoryView');
  const items=document.getElementById('characterInventoryItems');
  const capacity=document.getElementById('characterInventoryCapacity');
  const back=document.getElementById('characterInventoryBack');
  const detailName=document.getElementById('characterInventoryName');
  const detailAvatar=document.getElementById('characterInventoryAvatar');
  const equipment=document.getElementById('characterEquipmentList');
  const statusList=document.getElementById('characterStatusList');
  const signatureNames=['w.w magnum','white wraith magnum','blackblood armor','omnivault ring','nhẫn vạn tàng','nhẫn omnivault'];
  const signatureEquipment={weapon:'W.W Magnum',armor:'Blackblood Armor & linked modules',ring:'Omnivault Ring'};
  const bandLabels={UNKNOWN:'Chưa xác định',NORMAL:'Bình thường',MILD:'Nhẹ',MODERATE:'Vừa',SEVERE:'Nặng',CRITICAL:'Nguy kịch'};
  let selectedCharacterId='kai';

  function isSignatureItem(x){const n=String((x&&x.name)||x||'').toLocaleLowerCase('vi-VN');return signatureNames.some(k=>n.includes(k))}
  function repairState(){
    let changed=false;
    if(state&&Array.isArray(state.inventory)){
      const normal=state.inventory.filter(x=>!isSignatureItem(x));
      if(normal.length!==state.inventory.length){state.inventory=normal;changed=true}
    }
    const turn=Number(state&&state.turn)||1;
    if(turn<=2&&(!Array.isArray(state&&state.log)||state.log.length===0)&&Array.isArray(initial&&initial.log)&&initial.log.length){
      state.log=JSON.parse(JSON.stringify(initial.log));changed=true
    }
    if(changed){try{localStorage.setItem('backroom-apk-state',JSON.stringify(state))}catch(ignore){}}
  }
  function kaiItems(){repairState();return Array.isArray(state&&state.inventory)?state.inventory.filter(x=>!isSignatureItem(x)):[]}
  function formatMinutes(raw){const m=Math.max(0,Number(raw)||0),h=Math.floor(m/60),min=m%60;return h>0?h+' giờ '+min+' phút':min+' phút'}
  function bandLabel(value){return bandLabels[String(value||'UNKNOWN').toUpperCase()]||String(value||'Chưa xác định')}
  function bandClass(value){const v=String(value||'UNKNOWN').toUpperCase();return v==='SEVERE'||v==='CRITICAL'?'status-danger':v==='MILD'||v==='MODERATE'?'status-warning':'status-normal'}
  function detailMembers(){
    const members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:null;
    if(members&&members.length)return members;
    const fallback=[{id:'kai',name:'Kai Akechi',avatar:'avatars/kai_avatar.png',presence:'ACTIVE',isLeader:true,physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'},inventory:kaiItems(),equipment:signatureEquipment,statuses:[],injuries:[]}];
    if(Array.isArray(state&&state.party))state.party.forEach(x=>{if(x&&x.id&&x.id!=='kai')fallback.push({id:x.id,name:x.name||x.id,avatar:x.avatar,presence:x.presence||'ACTIVE',isLeader:false,physiology:{hunger:'UNKNOWN',thirst:'UNKNOWN',sleepDeprivation:'UNKNOWN'},inventory:[],equipment:{},statuses:[],injuries:[]})});
    return fallback;
  }
  function memberById(id){return detailMembers().find(x=>String(x.id)===String(id))||detailMembers()[0]}
  function statusRows(member){
    const p=member&&member.physiology||{};
    const rows=[];
    rows.push(['Hiện diện',member&&member.presence||'ACTIVE','status-normal']);
    if(member&&member.healthState)rows.push(['Thể trạng',member.healthState,'status-normal']);
    if(Array.isArray(member&&member.injuries)&&member.injuries.length)rows.push(['Thương tích',member.injuries.join(', '),'status-danger']);
    rows.push(['Đói',bandLabel(p.hunger),bandClass(p.hunger)]);
    rows.push(['Khát',bandLabel(p.thirst),bandClass(p.thirst)]);
    rows.push(['Thiếu ngủ',bandLabel(p.sleepDeprivation),bandClass(p.sleepDeprivation)]);
    if(p.pain)rows.push(['Đau',p.pain,'status-warning']);
    if(p.infection)rows.push(['Nhiễm trùng',p.infection,'status-danger']);
    if(p.thermal)rows.push(['Nhiệt trạng',p.thermal,'status-warning']);
    if(Array.isArray(member&&member.statuses)&&member.statuses.length)rows.push(['Hiệu ứng',member.statuses.map(x=>x.type||x.id).join(', '),'status-warning']);
    return rows;
  }
  function equipmentRows(member){
    const eq=member&&member.equipment||{};
    const keys=Object.keys(eq);
    if(!keys.length&&member&&member.id==='kai')return Object.keys(signatureEquipment).map(k=>signatureEquipment[k]);
    return keys.map(k=>String(k)+': '+String(eq[k]));
  }
  function renderMemberDetail(member){
    if(!member)return;
    selectedCharacterId=member.id||'kai';
    detailName.textContent=member.name||member.id||'—';
    detailAvatar.src=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png');
    detailAvatar.alt=member.name||member.id||'Nhân vật';
    const inv=Array.isArray(member.inventory)?member.inventory:(member.id==='kai'?kaiItems():[]);
    capacity.textContent=inv.length+' / 9 loại vật phẩm';
    items.innerHTML=chips(inv);
    const eqRows=equipmentRows(member);
    equipment.innerHTML=eqRows.length?eqRows.map(x=>'<span>'+esc(x)+'</span>').join(''):'<span>Không có trang bị được ghi nhận.</span>';
    statusList.innerHTML=statusRows(member).map(row=>'<div class="character-status-row '+row[2]+'"><b>'+esc(row[0])+'</b><span>'+esc(row[1])+'</span></div>').join('');
  }
  function openMember(id){const member=memberById(id);renderMemberDetail(member);view.hidden=false}
  function renderPartyCards(){
    if(!party)return;
    const members=detailMembers();
    const max=Math.max(1,Math.min(4,Number(state&&state.partyDetails&&state.partyDetails.maxMembers)||4));
    const cards=[];
    for(let i=0;i<max;i++){
      const member=members[i];
      if(!member){cards.push('<div class="party-member empty"><div class="party-avatar-placeholder">Trống</div><strong>Trống</strong></div>');continue}
      const avatar=member.avatar||member.avatarRef;
      const visual=avatar?'<img src="'+esc(avatar)+'" alt="'+esc(member.name||member.id)+'">':'<div class="party-avatar-placeholder">'+esc(String(member.name||member.id||'?').slice(0,1).toUpperCase())+'</div>';
      cards.push('<div class="party-member" data-character="'+esc(member.id)+'">'+visual+'<strong>'+esc(member.name||member.id)+'</strong><div class="party-state">'+esc(member.presence||'ACTIVE')+'</div></div>');
    }
    party.innerHTML=cards.join('');
    party.querySelectorAll('.party-member[data-character]').forEach(card=>card.addEventListener('click',()=>openMember(card.getAttribute('data-character'))));
    if(partyTime){const t=state&&state.gameTime&&state.gameTime.elapsedSubjectiveMinutes!=null?state.gameTime.elapsedSubjectiveMinutes:state&&state.partyDetails&&state.partyDetails.elapsedSubjectiveMinutes;partyTime.textContent=t==null?'':'Thời gian chủ quan: '+formatMinutes(t)}
  }
  if(back)back.addEventListener('click',()=>{view.hidden=true});
  const priorRender=window.render;
  if(typeof priorRender==='function')window.render=function(){
    repairState();
    priorRender();
    renderPartyCards();
    if(view&&!view.hidden)renderMemberDetail(memberById(selectedCharacterId));
  };
  repairState();
  if(typeof window.render==='function')window.render();else renderPartyCards();
})();
</script>
'''
if script_extra not in html:
    if script_anchor not in html:
        raise RuntimeError("script footer anchor not found")
    html = html.replace(script_anchor, '</script>\n' + script_extra + '</body>', 1)

if '<div class="card"><h2>Inventory</h2>' in html:
    raise RuntimeError("Global Inventory panel still present")
if unsafe_inventory_render in html.replace(safe_inventory_render, ''):
    raise RuntimeError("Removed global Inventory still has an unsafe renderer write")
if 'White Wraith Magnum"},\n    {name:"Blackblood Armor' in html:
    raise RuntimeError("Kai signature equipment still seeded into normal Inventory")

INDEX.write_text(html, encoding="utf-8")
print("Party character detail UI applied: up to four avatar slots with per-character Status, Equipment and Inventory.")
