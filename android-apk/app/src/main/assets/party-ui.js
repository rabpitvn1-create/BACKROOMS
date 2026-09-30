(function(){
  if(window.__backroomPartyUi)return;
  window.__backroomPartyUi=true;

  var AVATARS={
    cao_minh:'file:///android_asset/avatars/cao_minh_avatar.jpg',
    lucia:'file:///android_asset/avatars/lucia_avatar.png',
    luc_tram:'file:///android_asset/avatars/luctram_avatar.png',
    syvial:'file:///android_asset/avatars/Syvial_avatar.jpg'
  };
  var META={
    cao_minh:{name:'Cao Minh',role:'Vạn Giới Ma Tôn'},
    lucia:{name:'Lucia Lục',role:'Tactical Riflewoman'},
    luc_tram:{name:'Lục Trầm',role:'Chân truyền Thiên Kiếm Môn'},
    syvial:{name:'Syvial',role:'Đội phó SRU'}
  };
  var BAND_LABELS={UNKNOWN:'Chưa xác định',NORMAL:'Bình thường',MILD:'Nhẹ',MODERATE:'Vừa',SEVERE:'Nặng',CRITICAL:'Nguy kịch'};

  var style=document.createElement('style');
  style.textContent='.party-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.party-member-card{appearance:none;-webkit-appearance:none;border:1px solid #313940;background:#111519;color:#eef1f3;padding:6px;display:grid;gap:5px;text-align:center;min-width:0;touch-action:manipulation;border-radius:8px}.party-member-card:active{transform:scale(.97)}.party-member-card[aria-expanded="true"]{border-color:#707b84;background:#171c20}.party-member-avatar{width:100%;aspect-ratio:1/1;object-fit:cover;border:1px solid #333b42;background:#0b0e11;border-radius:6px}.party-member-name{font-size:10px;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-member-state{font-size:8px;color:#8f9aa4;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-detail{margin-top:10px;border-top:1px solid #2b3137;padding-top:10px;animation:party-detail-in .16s ease-out}.party-detail-head{display:grid;grid-template-columns:92px 1fr auto;gap:11px;align-items:start}.party-detail-avatar{width:92px;height:92px;object-fit:cover;border:1px solid #3b444c;background:#0b0e11;border-radius:8px}.party-detail-name{font-size:18px;font-weight:800;line-height:1.2}.party-detail-role{margin-top:4px;color:#9aa4ad;font-size:11px}.party-detail-close{padding:7px 9px;min-width:38px;border-radius:6px}.party-detail-sections{display:grid;gap:8px;margin-top:10px}.party-detail-section{border:1px solid #2b3137;background:#0b0e11;padding:10px;border-radius:8px}.party-detail-section h3{margin:0 0 8px;font-size:10px;letter-spacing:.12em;color:#8f9aa4}.party-detail-row{display:grid;grid-template-columns:minmax(82px,35%) 1fr;gap:8px;padding:5px 0;border-bottom:1px solid #20262b;font-size:12px}.party-detail-row:last-child{border-bottom:0}.party-detail-row b{color:#7f8b95;font-weight:500}.party-detail-value.status-normal{color:#aeb8bf}.party-detail-value.status-warning{color:#d6c28a}.party-detail-value.status-danger{color:#df9a9a}.party-detail-tags{display:flex;flex-wrap:wrap;gap:6px}.party-detail-tag{border:1px solid #313940;padding:5px 7px;font-size:11px;border-radius:6px}.party-avatar-placeholder{width:100%;aspect-ratio:1/1;display:grid;place-items:center;border:1px solid #333b42;background:#0b0e11;color:#8f9aa4;font-size:20px;font-weight:800;border-radius:6px}@keyframes party-detail-in{from{opacity:0;transform:translateY(-4px)}to{opacity:1;transform:translateY(0)}}.core-stat-line{display:grid;grid-template-columns:44px 1fr auto;gap:7px;align-items:center;padding:6px 0;border-bottom:1px solid #20262b;font-size:12px}.core-stat-line:last-child{border-bottom:0}.core-stat-name{font-weight:800;color:#d7dde1}.core-stat-value{color:#eef1f3}.core-stat-upgrade{padding:7px 8px;min-width:74px;font-size:10px;touch-action:manipulation;border-radius:6px}.core-stat-upgrade:disabled{opacity:.45}.core-count{font-size:12px;font-weight:800;color:#f6c85f;margin-bottom:6px}.combat-status-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px}.combat-status-cell{border:1px solid #20262b;background:#101419;padding:7px 8px;border-radius:6px;min-width:0}.combat-status-label{display:block;font-size:9px;letter-spacing:.08em;color:#7f8b95;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.combat-status-value{display:block;margin-top:3px;font-size:14px;font-weight:800;color:#eef1f3}@media(max-width:520px){.party-grid{grid-template-columns:repeat(4,minmax(0,1fr));gap:5px}.party-member-card{padding:4px}.party-member-name{font-size:9px}.party-detail-head{grid-template-columns:76px 1fr auto}.party-detail-avatar{width:76px;height:76px}}';
  document.head.appendChild(style);

  var party=document.getElementById('party');
  if(!party)return;
  party.className='party-grid';
  var card=party.closest?party.closest('.card'):party.parentElement;
  var detail=document.createElement('div');
  detail.className='party-detail';
  detail.hidden=true;
  detail.id='partyCharacterDetail';
  party.insertAdjacentElement('afterend',detail);
  var selectedId='';

  function publishSelection(){
    var id=selectedId||'cao_minh';
    if(typeof window.backroomInventoryOwnerChanged==='function')window.backroomInventoryOwnerChanged(id);
  }

  function norm(raw){
    var value=String(raw||'').trim().toLowerCase();
    if(value.indexOf('cao_minh')>=0||value.indexOf('cao minh')>=0)return 'cao_minh';
    if(value.indexOf('lucia')>=0||value.indexOf('hứa thuý mai')>=0||value.indexOf('hứa thúy mai')>=0||value.indexOf('hua thuy mai')>=0)return 'lucia';
    if(value.indexOf('lục trầm')>=0||value.indexOf('luc tram')>=0||value.indexOf('luc_tram')>=0)return 'luc_tram';
    if(value.indexOf('syvial')>=0)return 'syvial';
    return value.replace(/\s+/g,'_');
  }

  function clonePlayer(){
    var p=(state&&state.player)||{};
    var result={};
    Object.keys(p).forEach(function(k){result[k]=p[k];});
    result.id='cao_minh';
    result.name=result.name||'Cao Minh';
    result.joined=true;
    result.__player=true;
    return result;
  }

  function detailProjection(id){
    var details=state&&state.partyDetails;
    var list=details&&Array.isArray(details.members)?details.members:[];
    for(var i=0;i<list.length;i++){
      var member=list[i];
      if(member&&norm((member.id||'')+' '+(member.name||''))===id)return member;
    }
    return null;
  }

  function mergeDetail(source){
    var id=norm(source&&((source.id||'')+' '+(source.name||'')));
    var projected=detailProjection(id);
    if(!projected)return source;
    var merged={};
    Object.keys(source||{}).forEach(function(k){merged[k]=source[k];});
    Object.keys(projected).forEach(function(k){merged[k]=projected[k];});
    if(source&&source.joined===true)merged.joined=true;
    if(source&&source.__player===true)merged.__player=true;
    return merged;
  }

  function members(){
    var out=[mergeDetail(clonePlayer())];
    var seen={cao_minh:true};
    (Array.isArray(state&&state.party)?state.party:[]).forEach(function(member){
      if(!member||member.joined!==true)return;
      var id=norm(member.id||member.name);
      if(!id||id==='cao_minh'||seen[id])return;
      seen[id]=true;
      out.push(mergeDetail(member));
    });
    return out.slice(0,4);
  }

  function displayName(member){
    var id=norm(member&&((member.id||'')+' '+(member.name||'')));
    return String((member&&member.name)||(META[id]&&META[id].name)||id||'Nhân vật');
  }

  function avatarFor(member){
    var id=norm(member&&((member.id||'')+' '+(member.name||'')));
    var custom=member&&(member.avatar||member.avatarRef);
    return custom?String(custom):AVATARS[id]||'';
  }

  function conditionFor(member){
    if(member&&member.condition)return String(member.condition);
    if(member&&member.healthState)return String(member.healthState);
    var hp=Number(member&&(member.currentHp!==undefined?member.currentHp:member.hp));
    if(Number.isFinite(hp)&&hp<=0)return 'Bị hạ';
    return 'Hoạt động';
  }

  function bandLabel(value){
    var key=String(value||'UNKNOWN').toUpperCase();
    return BAND_LABELS[key]||String(value||'Chưa xác định');
  }

  function bandClass(value){
    var key=String(value||'UNKNOWN').toUpperCase();
    if(key==='SEVERE'||key==='CRITICAL')return 'status-danger';
    if(key==='MILD'||key==='MODERATE')return 'status-warning';
    return 'status-normal';
  }

  function survivalText(physiology,bandKey,percentKey){
    var p=physiology||{},label=bandLabel(p[bandKey]);
    var percent=Number(p[percentKey]);
    return Number.isFinite(percent)?label+' · '+Math.max(0,Math.min(100,Math.round(percent)))+'%':label;
  }

  function formatMinutes(raw){
    var minutes=Math.max(0,Number(raw)||0),hours=Math.floor(minutes/60),rest=Math.floor(minutes%60);
    return hours>0?hours+' giờ '+rest+' phút':rest+' phút';
  }

  function makeAvatar(member,className){
    var src=avatarFor(member);
    if(src){
      var img=document.createElement('img');
      img.className=className;
      img.src=src;
      img.alt=displayName(member);
      img.loading='eager';
      return img;
    }
    var ph=document.createElement('div');
    ph.className='party-avatar-placeholder';
    ph.textContent=displayName(member).slice(0,1).toUpperCase();
    return ph;
  }

  function addRow(container,label,value,valueClass){
    if(value===undefined||value===null||String(value).trim()==='')return;
    var row=document.createElement('div');row.className='party-detail-row';
    var b=document.createElement('b');b.textContent=label;
    var span=document.createElement('span');span.className='party-detail-value'+(valueClass?' '+valueClass:'');span.textContent=String(value);
    row.appendChild(b);row.appendChild(span);container.appendChild(row);
  }

  function section(title){
    var box=document.createElement('section');box.className='party-detail-section';
    var h=document.createElement('h3');h.textContent=title;box.appendChild(h);
    return box;
  }

  function coreCount(){
    var details=state&&state.partyDetails;
    if(details&&Number.isFinite(Number(details.coreCount)))return Math.max(0,Number(details.coreCount));
    var resource=state&&state.characterProgression&&state.characterProgression.coreResource;
    return resource&&Number.isFinite(Number(resource.quantity))?Math.max(0,Number(resource.quantity)):0;
  }

  function statValue(member,key){
    var value=member&&member.stats&&member.stats[key];
    if(value&&typeof value==='object'){
      if(value.effective!==undefined)return Number(value.effective);
      if(value.base!==undefined)return Number(value.base);
    }
    return Number(value);
  }

  function statCost(member,key){
    var value=member&&member.stats&&member.stats[key];
    if(value&&typeof value==='object'&&Number.isFinite(Number(value.nextCoreCost)))return Number(value.nextCoreCost);
    return Infinity;
  }

  function mutationLocked(){
    return !!window.__coreUpgradeBusy||!!window.__inventoryBusy||!!window.__combatBusy
      ||(typeof busy!=='undefined'&&!!busy)
      ||!!(state&&state.combat&&state.combat.active);
  }

  function requestCoreUpgrade(characterId,stat){
    if(mutationLocked())return;
    if(!window.Android||typeof Android.coreUpgrade!=='function'){
      if(typeof status!=='undefined'&&status)status.textContent='Không tìm thấy Android Core upgrade bridge.';
      return;
    }
    window.__coreUpgradeBusy=true;
    if(typeof window.render==='function')window.render();
    Android.coreUpgrade(JSON.stringify(state),characterId,stat);
  }

  function appendCoreStats(container,member,id){
    var count=document.createElement('div');count.className='core-count';
    count.textContent='Core hiện có: '+coreCount();
    container.appendChild(count);
    ['STR','DEF','SKL','VIT'].forEach(function(key){
      var line=document.createElement('div');line.className='core-stat-line';
      var name=document.createElement('span');name.className='core-stat-name';name.textContent=key;
      var current=Math.max(1,statValue(member,key)||5),cost=statCost(member,key);
      var value=document.createElement('span');value.className='core-stat-value';
      var statLine=member&&member.stats&&member.stats[key];
      var permanent=Number(statLine&&statLine.base);
      var passiveBonus=Number(statLine&&statLine.passiveBonus);
      var projectedBase=Number.isFinite(permanent)?permanent:current;
      var passiveOnly=Number.isFinite(passiveBonus)&&passiveBonus>0;
      var projectedWithPassive=projectedBase+(passiveOnly?passiveBonus:0);
      var statText=passiveOnly
        ?String(projectedBase)+' (+'+String(passiveBonus)+')'
        :(Number.isFinite(permanent)&&permanent!==current
          ?String(permanent)+' → '+String(current):String(current));
      if(passiveOnly&&current!==projectedWithPassive)statText+=' → '+String(current);
      value.textContent=statText
        +' · tiếp theo: '+(Number.isFinite(cost)?String(cost):'—')+' Core';
      var button=document.createElement('button');button.type='button';button.className='core-stat-upgrade';
      button.textContent='+1 ('+(Number.isFinite(cost)?cost:'—')+')';
      button.disabled=mutationLocked()||coreCount()<cost;
      button.addEventListener('click',function(event){event.stopPropagation();requestCoreUpgrade(id,key);});
      line.appendChild(name);line.appendChild(value);line.appendChild(button);container.appendChild(line);
    });
  }


  function combatStatusSection(member){
    var status=(member&&member.combatStatus)||{},box=section('THÔNG SỐ CHIẾN ĐẤU');
    var grid=document.createElement('div');grid.className='combat-status-grid';
    var rows=[
      ['DMG',status.damage,false],
      ['DEFEND',status.defendPercent,true],
      ['CRITICAL',status.criticalChancePercent,true],
      ['EVASION',status.evasionPercent,true],
      ['RES CRITICAL',status.resCriticalPercent,true],
      ['RES EVASION',status.resEvasionPercent,true]
    ];
    rows.forEach(function(row){
      var cell=document.createElement('div');cell.className='combat-status-cell';
      var label=document.createElement('span');label.className='combat-status-label';label.textContent=row[0];
      var value=document.createElement('span');value.className='combat-status-value';
      var numeric=Number(row[1]);
      if(Number.isFinite(numeric)){
        if(row[2])value.textContent=(Math.round(numeric*10)/10).toString()+'%';
        else value.textContent=String(Math.round(numeric));
      }else{
        value.textContent='—';
      }
      cell.appendChild(label);cell.appendChild(value);grid.appendChild(cell);
    });
    box.appendChild(grid);
    return box;
  }

  function tagsSection(title,items){
    var box=section(title),wrap=document.createElement('div');wrap.className='party-detail-tags';
    if(!items.length){var empty=document.createElement('span');empty.className='party-detail-tag';empty.textContent='Chưa có dữ liệu.';wrap.appendChild(empty);}
    items.forEach(function(item){var tag=document.createElement('span');tag.className='party-detail-tag';tag.textContent=typeof item==='string'?item:String((item&&item.name)||item);wrap.appendChild(tag);});
    box.appendChild(wrap);return box;
  }

  function renderDetail(member){
    if(!member){detail.hidden=true;selectedId='';return;}
    var id=norm((member.id||'')+' '+(member.name||'')),meta=META[id]||{};
    detail.textContent='';
    var head=document.createElement('div');head.className='party-detail-head';
    var avatar=makeAvatar(member,'party-detail-avatar');
    var identity=document.createElement('div');
    var name=document.createElement('div');name.className='party-detail-name';name.textContent=displayName(member);
    var role=document.createElement('div');role.className='party-detail-role';role.textContent=meta.role||String(member.role||'Party member');
    identity.appendChild(name);identity.appendChild(role);
    var close=document.createElement('button');close.type='button';close.className='party-detail-close';close.textContent='×';close.setAttribute('aria-label','Đóng thông tin nhân vật');
    close.addEventListener('click',function(){selectedId='';detail.hidden=true;publishSelection();renderPartyUi();});
    head.appendChild(avatar);head.appendChild(identity);head.appendChild(close);detail.appendChild(head);

    var sections=document.createElement('div');sections.className='party-detail-sections';
    var status=section('TRẠNG THÁI');
    addRow(status,'Hiện diện',member.presence||'ACTIVE');
    addRow(status,'Tình trạng',conditionFor(member));
    var hp=member.currentHp!==undefined?member.currentHp:member.hp,maxHp=member.maxHp||member.maxHP;
    if(hp!==undefined)addRow(status,'HP',maxHp!==undefined?String(hp)+' / '+String(maxHp):hp);
    addRow(status,'Vai trò',meta.role||member.role);
    if(member.energy!==undefined)addRow(status,'Năng lượng',member.energy);
    if(member.hpRegen!==undefined)addRow(status,'Hồi HP',Number(member.hpRegen)>0?'+'+member.hpRegen+' / lượt':member.hpRegen);
    if(Array.isArray(member.injuries)&&member.injuries.length)addRow(status,'Thương tích',member.injuries.join(', '),'status-danger');
    sections.appendChild(status);

    var physiology=member.physiology||{};
    var survival=section('CHỈ SỐ SINH TỒN');
    addRow(survival,'Đói',survivalText(physiology,'hunger','foodPercent'),bandClass(physiology.hunger));
    addRow(survival,'Khát',survivalText(physiology,'thirst','waterPercent'),bandClass(physiology.thirst));
    addRow(survival,'Thiếu ngủ',survivalText(physiology,'sleepDeprivation','restPercent'),bandClass(physiology.sleepDeprivation));
    if(physiology.pain)addRow(survival,'Đau',physiology.pain,'status-warning');
    if(physiology.infection)addRow(survival,'Nhiễm trùng',physiology.infection,'status-danger');
    if(physiology.thermal)addRow(survival,'Nhiệt trạng',physiology.thermal,'status-warning');
    if(state&&state.partyDetails&&state.partyDetails.elapsedSubjectiveMinutes!==undefined){
      addRow(survival,'Thời gian chủ quan',formatMinutes(state.partyDetails.elapsedSubjectiveMinutes));
    }
    sections.appendChild(survival);

    var statBox=section('CHỈ SỐ COMBAT');
    appendCoreStats(statBox,member,id);
    sections.appendChild(statBox);
    sections.appendChild(combatStatusSection(member));

    if(Array.isArray(member.passives)&&member.passives.length){
      var passiveBox=section('PASSIVE');
      member.passives.forEach(function(passive){
        if(!passive)return;
        addRow(passiveBox,String(passive.name||'Passive'),String(passive.description||''));
      });
      sections.appendChild(passiveBox);
    }

    var effects=[];
    ['injuries','statuses','effects','statusEffects'].forEach(function(k){
      if(Array.isArray(member&&member[k]))member[k].forEach(function(x){
        var label=typeof x==='string'?x:(x&&x.type)||(x&&x.name)||(x&&x.id)||'';
        if(k==='statusEffects'&&x&&Number(x.remainingTurns)>0)label+=' · '+x.remainingTurns+' lượt';
        effects.push(label);
      });
    });
    effects=effects.filter(Boolean);
    if(effects.length)sections.appendChild(tagsSection('HIỆU ỨNG / THƯƠNG TÍCH',effects));
    detail.appendChild(sections);
    detail.hidden=false;
  }

  function openMember(member){
    var id=norm((member.id||'')+' '+(member.name||''));
    if(selectedId===id&&!detail.hidden){selectedId='';detail.hidden=true;publishSelection();renderPartyUi();return;}
    selectedId=id;publishSelection();renderDetail(member);renderPartyUi();
  }

  function renderPartyUi(){
    var list=members();
    party.textContent='';
    list.forEach(function(member){
      var id=norm((member.id||'')+' '+(member.name||''));
      var button=document.createElement('button');
      button.type='button';button.className='party-member-card';button.dataset.character=id;
      button.setAttribute('aria-expanded',String(selectedId===id&&!detail.hidden));
      button.appendChild(makeAvatar(member,'party-member-avatar'));
      var name=document.createElement('div');name.className='party-member-name';name.textContent=displayName(member);button.appendChild(name);
      var stateText=document.createElement('div');stateText.className='party-member-state';stateText.textContent=conditionFor(member);button.appendChild(stateText);
      button.addEventListener('click',function(){openMember(member);});
      party.appendChild(button);
    });
    if(selectedId){
      var selected=list.find(function(member){return norm((member.id||'')+' '+(member.name||''))===selectedId;});
      if(selected&&!detail.hidden)renderDetail(selected);
      else if(!selected){selectedId='';detail.hidden=true;publishSelection();}
    }
  }

  window.backroomCoreUpgrade=function(json){
    window.__coreUpgradeBusy=false;
    try{
      var result=JSON.parse(json);
      if(result&&result.state){
        state=result.state;
      }
      if(typeof status!=='undefined'&&status)status.textContent=result&&result.handled?String(result.reply||'Đã nâng chỉ số.'):String(result&&result.error||'Không thể nâng chỉ số.');
    }catch(_){
      if(typeof status!=='undefined'&&status)status.textContent='Core upgrade trả dữ liệu không hợp lệ.';
    }
    if(typeof window.render==='function')window.render();
  };

  window.renderPartyUi=renderPartyUi;
  var oldRender=window.render;
  if(typeof oldRender==='function'){
    window.render=function(){oldRender();renderPartyUi();};
  }
  publishSelection();
  renderPartyUi();
})();
