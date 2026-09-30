(function(){
  'use strict';
  if(window.__inventoryUiInstalled)return;
  window.__inventoryUiInstalled=true;

  var style=document.createElement('style');
  style.textContent=[
    ".inventory-grid{display:grid;gap:7px}",
    ".inventory-item{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;text-align:left;padding:9px 10px;border-radius:8px}",
    ".inventory-item-name{font-family:'Play','Pretendard Std',system-ui,sans-serif;font-weight:700;color:#f6c85f;text-decoration:none}",
    ".inventory-item-qty{color:#aeb7be;font-size:12px;white-space:nowrap}",
    ".inventory-overlay{position:fixed;inset:0;z-index:1000;background:#000a;display:grid;place-items:end center;padding:12px}",
    ".inventory-sheet{width:min(560px,100%);max-height:82vh;overflow:auto;background:#101419;border:1px solid #39424a;box-shadow:0 18px 60px #000c;padding:14px;display:grid;gap:12px;border-radius:12px}",
    ".inventory-sheet h3{margin:0;font-family:'Play','Pretendard Std',system-ui,sans-serif;color:#f6c85f}",
    ".inventory-effect{color:#c4cbd1;font-size:13px}",
    ".inventory-quantity{display:grid;grid-template-columns:1fr 100px;gap:10px;align-items:center}",
    ".inventory-quantity input,.inventory-share select{width:100%;background:#090c0f;color:#fff;border:1px solid #30373e;padding:10px;border-radius:6px}",
    ".inventory-sheet-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}",
    ".inventory-share{display:grid;grid-template-columns:1fr auto;gap:8px}",
    ".inventory-drop{background:#241616;color:#f1b3b3;border-color:#663b3b}",
    ".inventory-note{font-size:12px;color:#8d979f}",
    ".vitals-inline{grid-column:2;font-size:12px;color:#aab3ba;margin-top:7px}"
  ].join('');
  document.head.appendChild(style);

  var inventory=document.getElementById('inventory');
  var player=document.getElementById('player');
  var status=document.getElementById('status');
  var inventoryTitle=inventory&&inventory.closest?inventory.closest('.card')&&inventory.closest('.card').querySelector('h2'):null;
  var selectedItem=null;
  var selectedOwnerId='cao_minh';
  var itemBusy=false;
  var CHARACTER_SET_EQUIPMENT={
    'huyết ma kiếm':true,
    'huyết ma chiến khải':true,
    'vạn tàng giới':true,
    'tịch quang kiếm':true,
    'thiên cơ bạch kim kiếm khải':true,
    'sru recon frame r03':true,
    'ivory & ebony':true,
    'godkiller':true,
    'lucifer armor':true
  };
  window.__inventoryBusy=false;
  function setItemBusy(value){itemBusy=!!value;window.__inventoryBusy=itemBusy}

  function norm(raw){
    var value=String(raw||'').trim().toLowerCase();
    if(value.indexOf('cao_minh')>=0||value.indexOf('cao minh')>=0)return 'cao_minh';
    if(value.indexOf('lucia')>=0||value.indexOf('hứa thuý mai')>=0||value.indexOf('hứa thúy mai')>=0||value.indexOf('hua thuy mai')>=0)return 'lucia';
    if(value.indexOf('lục trầm')>=0||value.indexOf('luc tram')>=0||value.indexOf('luc_tram')>=0)return 'luc_tram';
    if(value.indexOf('syvial')>=0)return 'syvial';
    return value.replace(/\s+/g,'_');
  }
  function partyMember(id){
    var key=norm(id),party=Array.isArray(state&&state.party)?state.party:[];
    for(var i=0;i<party.length;i++){
      var member=party[i];
      if(member&&member.joined===true&&norm((member.id||'')+' '+(member.name||''))===key)return member;
    }
    return null;
  }
  function ownerName(id){
    var key=norm(id);
    if(key==='cao_minh')return String(state&&state.player&&state.player.name||'Cao Minh');
    var member=partyMember(key);
    return String(member&&member.name||key||'Nhân vật');
  }
  function ownerInventory(id){
    var key=norm(id);
    if(key==='cao_minh')return Array.isArray(state&&state.inventory)?state.inventory:[];
    var member=partyMember(key);
    return Array.isArray(member&&member.inventory)?member.inventory:[];
  }
  function itemId(item){return String(item&&item.id||item&&item.name||'').trim()}
  function itemNameKey(item){return String(item&&item.name||'').trim().toLocaleLowerCase('vi')}
  function isCharacterSetEquipment(item){
    if(!item)return false;
    if(item.characterSet===true||item.setEquipment===true)return true;
    return CHARACTER_SET_EQUIPMENT[itemNameKey(item)]===true;
  }
  function visibleInventoryItems(id){
    return ownerInventory(id).filter(function(item){return item&&!isCharacterSetEquipment(item);});
  }
  function qty(item){return Math.max(1,Number(item&&item.quantity||1)||1)}
  function effectText(item){
    if(item&&item.kind==='equipment'){
      var parts=[];
      if(item.set)parts.push('Set: '+item.set);
      if(item.description)parts.push(String(item.description));
      return parts.join(' · ')||'Trang bị của Cao Minh.';
    }
    var e=item&&item.effects||{};
    var parts=[];
    if(Number(e.hunger||0)>0)parts.push('Đói +'+e.hunger);
    if(Number(e.thirst||0)>0)parts.push('Khát +'+e.thirst);
    if(Number(e.hp||0)>0)parts.push('HP +'+e.hp);
    return parts.join(' · ')||'Không có hiệu ứng tiêu hao.';
  }
  function isConsumable(item){
    var id=String(itemId(item)).toLowerCase();
    return item&&item.kind==='consumable'||id==='almond-water'||id==='bandage';
  }
  function closeSheet(){
    var old=document.getElementById('inventoryOverlay');
    if(old)old.remove();
    selectedItem=null;
  }
  function combatLocked(){return !!(state&&state.combat&&state.combat.active)}
  function processingLocked(){
    return itemBusy||!!window.__coreUpgradeBusy||!!window.__combatBusy
      ||(typeof busy!=='undefined'&&!!busy);
  }
  function interactionLocked(){return combatLocked()||processingLocked()}
  function send(operation,target,quantity){
    if(!selectedItem||!window.Android||typeof Android.itemAction!=='function')return;
    if(combatLocked()){if(status)status.textContent='Không thể quản lý vật phẩm trong chiến đấu.';return}
    if(processingLocked()){if(status)status.textContent='Đang xử lý thao tác khác. Hãy chờ hoàn tất trước khi dùng Inventory.';return}
    var q=Math.max(1,Math.min(qty(selectedItem),Number(quantity)||1));
    try{
      setItemBusy(true);
      Android.itemAction(JSON.stringify(state),selectedOwnerId,itemId(selectedItem),operation,String(target||''),q);
      if(status)status.textContent='Đang xử lý vật phẩm…';
    }catch(_){
      setItemBusy(false);
      if(status)status.textContent='Không thể gửi thao tác vật phẩm.';
    }
  }
  function openSheet(item){
    closeSheet();
    selectedItem=item;
    var overlay=document.createElement('div');overlay.id='inventoryOverlay';overlay.className='inventory-overlay';
    var sheet=document.createElement('div');sheet.className='inventory-sheet';overlay.appendChild(sheet);
    overlay.addEventListener('click',function(e){if(e.target===overlay)closeSheet()});

    var title=document.createElement('h3');title.textContent=(item.name||'Vật phẩm')+' ×'+qty(item);sheet.appendChild(title);
    var effect=document.createElement('div');effect.className='inventory-effect';effect.textContent=effectText(item);sheet.appendChild(effect);

    var qrow=document.createElement('div');qrow.className='inventory-quantity';
    var qlabel=document.createElement('span');qlabel.textContent='Số lượng';
    var qinput=document.createElement('input');qinput.type='number';qinput.min='1';qinput.max=String(qty(item));qinput.value='1';qinput.inputMode='numeric';
    qrow.appendChild(qlabel);qrow.appendChild(qinput);sheet.appendChild(qrow);

    var locked=interactionLocked();
    if(locked){var note=document.createElement('div');note.className='inventory-note';note.textContent=combatLocked()?'Battle đang hoạt động. Chỉ A/B/C được phép thực hiện.':'Đang xử lý thao tác khác. Inventory tạm khóa để tránh ghi đè state.';sheet.appendChild(note)}

    var actions=document.createElement('div');actions.className='inventory-sheet-actions';
    var use=document.createElement('button');use.type='button';use.textContent='SỬ DỤNG';use.disabled=locked||!isConsumable(item);
    use.addEventListener('click',function(){send('use','self',qinput.value)});
    actions.appendChild(use);

    var drop=document.createElement('button');drop.type='button';drop.className='inventory-drop';drop.textContent='VỨT BỎ';drop.disabled=locked;
    drop.addEventListener('click',function(){send('discard','',qinput.value)});
    actions.appendChild(drop);
    sheet.appendChild(actions);

    var targets=[{id:'cao_minh',name:String(state&&state.player&&state.player.name||'Cao Minh')}];
    (Array.isArray(state&&state.party)?state.party:[]).forEach(function(member){
      if(member&&member.joined===true)targets.push({id:String(member.id||member.name||''),name:String(member.name||member.id||'Đồng đội')});
    });
    targets=targets.filter(function(target){return norm(target.id)!==selectedOwnerId;});
    if(targets.length&&isConsumable(item)){
      var share=document.createElement('div');share.className='inventory-share';
      var select=document.createElement('select');
      targets.forEach(function(target){
        var option=document.createElement('option');
        option.value=String(target.id);
        option.textContent=String(target.name);
        select.appendChild(option);
      });
      var shareButton=document.createElement('button');shareButton.type='button';shareButton.textContent='DÙNG CHO';shareButton.disabled=locked||!select.options.length;
      shareButton.addEventListener('click',function(){send('share',select.value,qinput.value)});
      share.appendChild(select);share.appendChild(shareButton);sheet.appendChild(share);
    }

    var close=document.createElement('button');close.type='button';close.textContent='ĐÓNG';close.addEventListener('click',closeSheet);sheet.appendChild(close);
    document.body.appendChild(overlay);
  }

  function renderInventory(){
    if(!inventory)return;
    if(selectedOwnerId!=='cao_minh'&&!partyMember(selectedOwnerId))selectedOwnerId='cao_minh';
    inventory.className='inventory-grid';
    inventory.textContent='';
    if(inventoryTitle)inventoryTitle.textContent='Inventory · '+ownerName(selectedOwnerId);
    var items=visibleInventoryItems(selectedOwnerId);
    if(!items.length){var empty=document.createElement('span');empty.textContent='Trống.';inventory.appendChild(empty);return}
    items.forEach(function(item){
      if(!item)return;
      var button=document.createElement('button');button.type='button';button.className='inventory-item';
      var name=document.createElement('span');name.className='inventory-item-name';name.textContent=String(item.name||'Vật phẩm');
      var count=document.createElement('span');count.className='inventory-item-qty';count.textContent='×'+qty(item);
      button.appendChild(name);button.appendChild(count);button.addEventListener('click',function(){openSheet(item)});
      inventory.appendChild(button);
    });
  }

  function renderVitals(){
    if(!player||!state||!state.player)return;
    var existing=document.getElementById('playerVitals');
    if(existing)existing.remove();
    var values=[];
    if(state.player.hp!==undefined)values.push('HP '+state.player.hp+'/'+(state.player.maxHp||100));
    if(state.player.hunger!==undefined)values.push('Hunger '+state.player.hunger+'/'+(state.player.maxHunger||100));
    if(state.player.thirst!==undefined)values.push('Thirsty '+state.player.thirst+'/'+(state.player.maxThirst||100));
    if(!values.length)return;
    var line=document.createElement('div');line.id='playerVitals';line.className='vitals-inline';line.textContent=values.join(' · ');
    player.parentNode.appendChild(line);
  }

  window.backroomInventoryOwnerChanged=function(rawId){
    var next=norm(rawId)||'cao_minh';
    if(next!=='cao_minh'&&!partyMember(next))next='cao_minh';
    selectedOwnerId=next;
    closeSheet();
    renderInventory();
  };

  var previousRender=window.render;
  window.render=function(){
    if(typeof previousRender==='function')previousRender();
    renderInventory();
    renderVitals();
  };

  window.backroomItemAction=function(json){
    setItemBusy(false);
    try{
      var result=JSON.parse(json);
      if(result.state)state=result.state;
      if(typeof CURRENT_CHARACTER_CANON!=='undefined')state.characterCanon=CURRENT_CHARACTER_CANON;
      if(result.handled===false){
        if(typeof window.render==='function')window.render();
        if(status)status.textContent=result.error||'Không thể xử lý vật phẩm.';
        return;
      }
      closeSheet();
      if(typeof window.render==='function')window.render();
      if(status)status.textContent=result.reply||'Đã cập nhật Inventory.';
    }catch(_){
      if(status)status.textContent='Không thể cập nhật vật phẩm.';
    }
  };

  window.render();
})();
