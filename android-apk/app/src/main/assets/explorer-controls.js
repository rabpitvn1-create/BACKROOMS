(function(global){
  'use strict';
  const bar=document.getElementById('playerActionBar'),form=document.getElementById('form');
  const journal=document.getElementById('sceneJournal'),room=document.getElementById('sceneJournalRoom'),objective=document.getElementById('sceneJournalObjective'),records=document.getElementById('sceneJournalRecords'),inference=document.getElementById('sceneJournalInference');
  if(!bar||!form||!journal||!room||!objective||!records)return;
  const panel=document.createElement('section');panel.id='explorerControls';panel.setAttribute('aria-label','Khám phá');
  const row=document.createElement('div');row.className='explorer-buttons';
  const buttons={};
  Object.entries({MAP:'MAP',AREA_LOG:'Area Log',TALK:'Giao tiếp'}).forEach(([kind,label])=>{
    const slot=document.createElement('div');slot.className='explorer-control-slot';
    const b=document.createElement('button');b.type='button';b.textContent=label;
    b.onclick=()=>kind==='MAP'?openMap():kind==='AREA_LOG'?openAreaLog():openTalk();
    buttons[kind]=b;slot.appendChild(b);row.appendChild(slot);
  });
  const actionSlot=document.createElement('div');actionSlot.className='explorer-action-slot';
  const playerAction=document.createElement('button');playerAction.type='button';
  playerAction.id='explorerPlayerButton';playerAction.textContent='Player Action';
  playerAction.onclick=()=>{if(locked())return;if(!actionDialog.open)actionDialog.showModal();};
  const retry=document.createElement('button');retry.type='button';retry.id='explorerRetry';
  retry.className='explorer-retry-danger';retry.textContent='Retry';retry.hidden=true;
  retry.onclick=()=>{
    if(busy||batchBusy||coreUnavailable)return;
    const scene=state&&state.scene;if(!scene)return;
    failed=false;busy=true;refresh();
    try{
      if(scene.pendingActionId)Android.submitSceneAction(JSON.stringify(state),'RETRY');
      else if(scene.generationNeeded){writerPending();Android.prepareScene(JSON.stringify(state));}
      else if(retryPlayer&&draft.trim())sendPlayerAction(draft);
      else{busy=false;refresh();}
    }catch(e){global.backroomError('Không gửi được yêu cầu thử lại tới Core.');}
  };
  actionSlot.append(playerAction,retry);panel.append(row,actionSlot);bar.appendChild(panel);
  const dialog=document.createElement('dialog');dialog.id='explorerChoices';dialog.setAttribute('aria-label','Chọn tương tác');
  const header=document.createElement('div');header.className='explorer-dialog-header';
  const heading=document.createElement('strong');heading.textContent='Chọn tương tác';
  const close=document.createElement('button');close.type='button';close.className='explorer-dialog-close';
  close.textContent='×';close.setAttribute('aria-label','Đóng lựa chọn');close.onclick=()=>dialog.close();
  header.append(heading,close);
  const choices=document.createElement('div');choices.className='explorer-choice-list';
  const nextArea=document.createElement('section');nextArea.className='explorer-next-area-panel';nextArea.hidden=true;
  dialog.append(header,choices,nextArea);document.body.appendChild(dialog);
  const actionDialog=document.createElement('dialog');actionDialog.id='explorerPlayerAction';
  actionDialog.setAttribute('aria-label','Player Action');
  const actionTitle=document.createElement('strong');actionTitle.textContent='Player Action';
  const actionClose=document.createElement('button');actionClose.type='button';actionClose.textContent='×';
  actionClose.setAttribute('aria-label','Đóng Player Action');actionClose.onclick=()=>actionDialog.close();
  const actionInput=document.createElement('textarea');actionInput.id='explorerActionText';
  actionInput.maxLength=900;actionInput.rows=5;
  actionInput.placeholder='Cao Minh đi qua hành lang vàng tới góc ẩm, kiểm tra bảng điện rồi gạt cầu dao 2-1-3-5-4-6.';
  const actionSend=document.createElement('button');actionSend.type='button';actionSend.textContent='Thực hiện';
  const actionHead=document.createElement('div');actionHead.className='explorer-dialog-header';
  actionHead.append(actionTitle,actionClose);actionDialog.append(actionHead,actionInput,actionSend);
  document.body.appendChild(actionDialog);
  let preparing=false,failed=false,batchBusy=false,retryPlayer=false,draft='',activeMechanismId=null;
  actionInput.oninput=()=>{draft=actionInput.value;actionSend.disabled=!draft.trim();};
  actionSend.onclick=()=>{
    if(locked()||!actionInput.value.trim())return;
    draft=actionInput.value.trim();
    // Opening the panel itself does not consume a turn or roll an encounter.
    const devices=(state.scene?.availableActions||[]).filter(a=>a.mechanismId);
    if(/^(?:mở|xem|sử dụng)\s+(?:bảng điều khiển|bảng điện|cần gạt)/i.test(draft)&&devices.length){
      actionDialog.close();showMechanism(devices[0].mechanismId);if(!dialog.open)dialog.showModal();return;
    }
    actionDialog.close();sendPlayerAction(draft);
  };
  function writerPending(provider){
    const log=document.getElementById('log');if(!log)return;
    let pending=log.querySelector('[data-scene-preparing="1"]');
    if(!pending){
      pending=document.createElement('article');pending.className='message gm pending scene-generation-pending';
      pending.setAttribute('data-scene-preparing','1');pending.setAttribute('data-pending','1');
      const role=document.createElement('div');role.className='role';role.textContent='BIÊN KỊCH';
      const line=document.createElement('div');line.className='text';
      const indicator=document.createElement('div');indicator.className='gm-thinking';indicator.setAttribute('role','status');
      const mark=document.createElement('img');mark.src='provider-logos/gemini.svg';mark.alt='';mark.className='scene-provider-logo';
      const label=document.createElement('span');label.className='gm-thinking-name';
      indicator.append(mark,label);line.append(indicator);pending.append(role,line);log.appendChild(pending);
    }
    // One fixed UI identity for the entire current mini-map. The real provider
    // can change to Gehihi in the worker; that detail stays in diagnostics.
    const labelText='Gemini 4.0 Argon đang chuẩn bị màn chơi. Xin vui lòng chờ trong giây lát.';
    const indicator=pending.querySelector('.gm-thinking');
    if(indicator)indicator.setAttribute('aria-label',labelText);
    const label=pending.querySelector('.gm-thinking-name');if(label)label.textContent=labelText;
    const mark=pending.querySelector('.scene-provider-logo');if(mark)mark.hidden=false;
    log.scrollTop=log.scrollHeight;
  }
  function clearWriterPending(){
    const log=document.getElementById('log');
    if(log)log.querySelectorAll('[data-scene-preparing="1"]').forEach(n=>n.remove());
  }
  global.backroomSceneWriterProvider=()=>{if(preparing)writerPending();};
  function locked(){return busy||batchBusy||coreUnavailable||!!state.pendingEncounter||!!(state.combat&&state.combat.active);}
  function showPopup(title){
    heading.textContent=title;choices.replaceChildren();nextArea.replaceChildren();nextArea.hidden=true;
    if(!dialog.open)dialog.showModal();
  }
  function sendPlayerAction(value){
    if(!value.trim()||!state.scene||state.scene.generationNeeded)return;
    batchBusy=true;retryPlayer=false;failed=false;busy=true;refresh();
    try{
      if(!Android.submitPlayerAction)throw Error('Player Action chưa được Core hỗ trợ.');
      Android.submitPlayerAction(JSON.stringify(state),value.trim());
    }catch(e){batchBusy=false;busy=false;retryPlayer=true;global.backroomError('Không gửi được Player Action tới Core.');}
  }
  function submitChoice(a,keepPopup){
    if(locked())return;
    const command={actionId:a.actionId,sceneVersion:a.sceneVersion,roomId:a.roomId,kind:a.kind,targetId:a.targetId};
    if(!keepPopup)dialog.close();
    if(keepPopup)global.backroomFreezeSnapshot=true;
    busy=true;
    if(keepPopup)Array.from(choices.children).forEach(b=>{b.disabled=true;});
    else appendActionPending(a.label);
    refresh();
    try{Android.submitSceneAction(JSON.stringify(state),JSON.stringify(command));}
    catch(e){global.backroomError('Không gửi được hành động tới Core.');}
  }
  function showMechanism(id){
    activeMechanismId=id;showPopup('Bảng điều khiển');
    const controls=(state.scene?.availableActions||[]).filter(a=>a.kind==='SEARCH'&&a.mechanismId===id);
    if(!controls.length){activeMechanismId=null;dialog.close();return;}
    const back=document.createElement('button');back.type='button';back.textContent='Đóng bảng điều khiển';
    back.onclick=()=>{activeMechanismId=null;dialog.close();};choices.appendChild(back);
    controls.forEach(a=>{
      const b=document.createElement('button');b.type='button';b.textContent=a.label;
      b.onclick=()=>submitChoice(a,true);choices.appendChild(b);
    });
  }
  function openTalk(){
    if(locked())return;
    activeMechanismId=null;showPopup('Giao tiếp');
    (state.scene?.availableActions||[]).filter(a=>a.kind==='TALK').forEach(a=>{
      const b=document.createElement('button');b.type='button';b.textContent=a.label;
      b.onclick=()=>submitChoice(a,false);choices.appendChild(b);
    });
    if(!choices.children.length){const p=document.createElement('p');p.textContent='Không có ai để giao tiếp tại đây.';choices.appendChild(p);}
  }
  function openAreaLog(){
    activeMechanismId=null;showPopup('Area Log');
    const events=state.scene?.areaLog||[];
    if(!events.length){const p=document.createElement('p');p.textContent='Chưa ghi nhận sự kiện nào tại khu vực này.';choices.appendChild(p);}
    events.forEach(e=>{
      const p=document.createElement('p');p.className='explorer-area-event';
      p.textContent=e.text;choices.appendChild(p);
    });
  }
  function openMap(){
    activeMechanismId=null;showPopup('MAP');
    const map=state.scene?.map;
    if(!map||!Array.isArray(map.rooms)||!map.rooms.length){
      const p=document.createElement('p');p.textContent='Bản đồ đang được chuẩn bị.';choices.appendChild(p);return;
    }
    const rooms=map.rooms,byId=new Map(rooms.map(r=>[r.id,r]));
    const root=byId.has(map.entryRoomId)?map.entryRoomId:rooms[0].id;
    const layers=[],depth=new Map([[root,0]]),queue=[root];
    while(queue.length){
      const id=queue.shift(),level=depth.get(id);
      if(!layers[level])layers[level]=[];
      layers[level].push(id);
      (byId.get(id).adjacentRoomIds||[]).forEach(next=>{
        if(byId.has(next)&&!depth.has(next)){depth.set(next,level+1);queue.push(next);}
      });
    }
    // Disconnected frontier nodes are still represented; never fabricate edges.
    rooms.forEach(r=>{if(!depth.has(r.id)){const level=layers.length;layers[level]=[r.id];depth.set(r.id,level);}});
    const widest=Math.max(1,...layers.map(layer=>layer.length));
    const width=Math.max(360,widest*190+40),height=layers.length*112+55;
    const positions=new Map();
    layers.forEach((layer,y)=>layer.forEach((id,x)=>{
      positions.set(id,{x:width/2+(x-(layer.length-1)/2)*190,y:60+y*112});
    }));
    const sc=document.createElement('div');sc.className='explorer-map-scroll';
    const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');
    svg.setAttribute('viewBox','0 0 '+width+' '+height);
    svg.setAttribute('width',String(width));svg.setAttribute('height',String(height));
    svg.setAttribute('role','img');svg.setAttribute('aria-label','Sơ đồ nhánh các phòng đã phát hiện');
    const edges=new Set();
    rooms.forEach(r=>(r.adjacentRoomIds||[]).forEach(other=>{
      if(!positions.has(other))return;
      const key=[r.id,other].sort().join('|');if(edges.has(key))return;edges.add(key);
      const a=positions.get(r.id),b=positions.get(other),line=document.createElementNS(ns,'line');
      [['x1',a.x],['y1',a.y],['x2',b.x],['y2',b.y],['stroke','#ad9663'],['stroke-width','2']].forEach(([k,v])=>line.setAttribute(k,String(v)));
      svg.appendChild(line);
    }));
    rooms.forEach(r=>{
      const pos=positions.get(r.id),current=r.id===map.currentRoomId;
      const g=document.createElementNS(ns,'g');
      const rect=document.createElementNS(ns,'rect');
      [['x',pos.x-83],['y',pos.y-23],['width',166],['height',46],['rx',8],
       ['fill',current?'#205e3d':r.visited?'#293334':'#20252a'],
       ['stroke',current?'#b3ffc6':'#9d885a'],['stroke-width',current?3:1.5]].forEach(([k,v])=>rect.setAttribute(k,String(v)));
      const label=document.createElementNS(ns,'text');
      label.setAttribute('x',String(pos.x));label.setAttribute('y',String(pos.y+5));
      label.setAttribute('text-anchor','middle');label.setAttribute('font-size','12');
      label.setAttribute('fill',current?'#ecfff1':'#e3d4af');
      label.textContent=(current?'● ':'')+(r.name||'Chưa khám phá').slice(0,24);
      g.append(rect,label);svg.appendChild(g);
    });
    sc.appendChild(svg);choices.appendChild(sc);
    const legend=document.createElement('p');legend.className='explorer-map-legend';
    legend.textContent='● Cao Minh đang ở đây · MAP chỉ để xem, di chuyển bằng Player Action.';
    choices.appendChild(legend);
  }
  function refresh(){
    const scene=state&&state.scene,combat=!!(state&&state.combat&&state.combat.active);
    panel.hidden=combat||!scene;form.hidden=!combat;
    journal.hidden=true; // Area Log replaces the obsolete clue/answer journal.
    if(!scene)return;
    room.textContent='';objective.textContent='';records.replaceChildren();
    if(inference)inference.textContent='';
    buttons.MAP.disabled=!scene.map;
    buttons.AREA_LOG.disabled=!scene.map;
    buttons.TALK.disabled=locked()||!(scene.availableActions||[]).some(a=>a.kind==='TALK');
    const showRetry=!!scene.pendingActionId||
      failed&&(!!scene.generationNeeded||retryPlayer);
    playerAction.hidden=showRetry;retry.hidden=!showRetry;
    playerAction.disabled=locked()||!!scene.generationNeeded;
    retry.disabled=busy||batchBusy||coreUnavailable;
    if(scene.generationNeeded&&!scene.pendingActionId&&!locked()&&!preparing&&!failed){
      preparing=true;busy=true;refresh();
      writerPending();
      try{Android.prepareScene(JSON.stringify(state));}catch(e){global.backroomError('Không thể chuẩn bị mini-map.');}
    }
  }
  // The combat system still owns its direct action form; Explorer has no text input.
  form.addEventListener('submit',e=>{if(!(state.combat&&state.combat.active)){e.preventDefault();e.stopImmediatePropagation();}},true);
  const turn=global.backroomTurn,error=global.backroomError;
  global.backroomTurn=function(json){
    preparing=false;failed=false;clearWriterPending();if(turn)turn(json);refresh();
    if(activeMechanismId&&dialog.open&&!busy)showMechanism(activeMechanismId);
    global.backroomFreezeSnapshot=false;
  };
  global.backroomError=function(message){
    preparing=false;failed=true;clearWriterPending();if(error)error(message);
    batchBusy=false;global.backroomFreezeSnapshot=false;refresh();
  };
  global.backroomPlayerActionProgress=function(json){
    // Preserve the draft and lock the controls until the entire ordered plan finishes.
    if(turn)turn(json);
    busy=true;refresh();
  };
  global.backroomPlayerActionFinished=function(raw){
    const result=JSON.parse(raw);batchBusy=false;busy=false;
    failed=false;retryPlayer=false;
    if(result.completed){draft='';actionInput.value='';actionSend.disabled=true;}
    else{
      const s=document.getElementById('status');
      if(s)s.textContent='Đã thực hiện '+result.executed+'/'+result.requested+
        ' bước. Chuỗi dừng tại giới hạn của Core hoặc sự kiện phát sinh.';
    }
    refresh();
  };
  global.backroomPlayerActionError=function(raw){
    const result=JSON.parse(raw);
    batchBusy=false;busy=false;
    retryPlayer=result.committed===0;
    global.backroomError(result.message||'Player Action không thành công.');
    // A partially committed plan must never be replayed from its first step.
    if(result.committed>0){failed=false;retryPlayer=false;refresh();}
  };
  global.backroomSyncExplorer=refresh;
  refresh();
})(window);
