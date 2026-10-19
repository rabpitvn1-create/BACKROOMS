(function(global){
  'use strict';
  const bar=document.getElementById('playerActionBar'),form=document.getElementById('form');
  const journal=document.getElementById('sceneJournal'),room=document.getElementById('sceneJournalRoom'),objective=document.getElementById('sceneJournalObjective'),records=document.getElementById('sceneJournalRecords'),inference=document.getElementById('sceneJournalInference');
  const menuTools=document.getElementById('explorerMenuTools');
  const objectivePanel=document.getElementById('explorerMenuObjective');
  const objectiveText=document.getElementById('explorerCurrentObjective');
  const casebook=document.getElementById('explorerCasebook');
  if(!bar||!form||!journal||!room||!objective||!records||
    !menuTools||!objectivePanel||!objectiveText||!casebook)return;
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
      else{busy=false;refresh();}
    }catch(e){global.backroomError('Không gửi được yêu cầu thử lại tới Core.');}
  };
  casebook.onclick=()=>{
    if(!state?.scene?.map)return;
    document.getElementById('gameMenuClose')?.click();
    openCasebook();
  };
  actionSlot.append(playerAction,retry);panel.append(row,actionSlot);bar.appendChild(panel);
  const dialog=document.createElement('dialog');dialog.id='explorerChoices';dialog.setAttribute('aria-label','Chọn tương tác');
  const header=document.createElement('div');header.className='explorer-dialog-header';
  const heading=document.createElement('strong');heading.textContent='Chọn tương tác';
  const close=document.createElement('button');close.type='button';close.className='explorer-dialog-close';
  close.textContent='×';close.setAttribute('aria-label','Đóng lựa chọn');close.onclick=()=>dialog.close();
  const mapSubtitle=document.createElement('span');
  mapSubtitle.className='explorer-map-subtitle';mapSubtitle.hidden=true;
  header.append(heading,close,mapSubtitle);
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
  let preparing=false,failed=false,batchBusy=false,draft='',activeMechanismId=null;
  actionInput.oninput=()=>{draft=actionInput.value;actionSend.disabled=!draft.trim();};
  actionSend.onclick=()=>{
    if(locked()||!actionInput.value.trim())return;
    draft=actionInput.value;
    // Opening the panel itself does not consume a turn or roll an encounter.
    const devices=(state.scene?.availableActions||[]).filter(a=>a.mechanismId);
    if(/^(?:mở|xem|sử dụng)\s+(?:bảng điều khiển|bảng điện|cần gạt)/i.test(draft)&&
       !/(?:rồi|sau đó|tiếp theo|đồng thời)/i.test(draft)&&devices.length){
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
    heading.textContent=title;
    dialog.className=title==='MAP'?'explorer-map-mode':'';
    mapSubtitle.hidden=true;mapSubtitle.textContent='';
    choices.replaceChildren();nextArea.replaceChildren();nextArea.hidden=true;
    if(!dialog.open)dialog.showModal();
  }
  function sendPlayerAction(value){
    if(!value.trim()||!state.scene||state.scene.generationNeeded)return;
    batchBusy=true;failed=false;busy=true;refresh();
    try{
      if(!Android.submitPlayerAction)throw Error('Player Action chưa được Core hỗ trợ.');
      Android.submitPlayerAction(JSON.stringify(state),value);
    }catch(e){batchBusy=false;busy=false;global.backroomError('Không gửi được Player Action tới Core.');}
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
  let hintTier=0;
  function openAvailableCoreActions(){
    activeMechanismId=null;showPopup('Hành động Core khả dụng');
    const actions=Array.isArray(state.scene?.availableActions)?state.scene.availableActions:[];
    if(!actions.length){
      const p=document.createElement('p');p.textContent='Hiện không thể hành động tại đây.';
      choices.appendChild(p);return;
    }
    actions.forEach(action=>{
      const b=document.createElement('button');b.type='button';
      b.textContent=action.label;b.onclick=()=>submitChoice(action,false);
      choices.appendChild(b);
    });
  }
  function openCasebook(){
    activeMechanismId=null;hintTier=0;showPopup('Sổ tay điều tra');
    const shortcuts=document.createElement('button');shortcuts.type='button';
    shortcuts.textContent='Xem hành động Core đang khả dụng';
    shortcuts.onclick=()=>openAvailableCoreActions();
    choices.appendChild(shortcuts);
    const notes=Array.isArray(state.scene?.records)?state.scene.records:[];
    if(!notes.length){
      const empty=document.createElement('p');empty.textContent='Chưa có ghi chép nào được xác nhận.';
      choices.appendChild(empty);
    }
    notes.forEach(note=>{
      if(typeof note.text!=='string')return;
      const record=document.createElement('p');record.className='explorer-casebook-record';
      record.textContent=note.text;choices.appendChild(record);
    });
    if(state.scene?.runtime==='world-graph-v3'){
      const hint=document.createElement('button');hint.type='button';hint.textContent='Gợi ý cấp 1';
      hint.onclick=()=>{
        if(hintTier>=3||typeof Android.requestWorldHint!=='function')return;
        hintTier++;
        Android.requestWorldHint(hintTier);
        hint.textContent=hintTier<3?'Gợi ý cấp '+(hintTier+1):'Đã xem đủ 3 cấp';
        hint.disabled=hintTier>=3;
      };
      choices.appendChild(hint);
    }
  }
  global.backroomWorldHint=function(raw){
    if(!dialog.open||heading.textContent!=='Sổ tay điều tra')return;
    const packet=JSON.parse(raw);
    if(packet.error){
      const warning=document.createElement('p');
      warning.textContent='Không thể lấy gợi ý tại thời điểm này.';choices.appendChild(warning);
      return;
    }
    if(packet.roomId!==state.scene?.room?.id||packet.sceneVersion!==state.scene?.sceneVersion)return;
    const p=document.createElement('p');p.className='explorer-hint';
    p.textContent='Gợi ý cấp '+packet.tier+': '+packet.text;choices.appendChild(p);
  };
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
    heading.textContent='LEVEL 0';
    const map=state.scene?.map;
    const rooms=Array.isArray(map?.rooms)?map.rooms:[];
    const currentRoom=rooms.find(r=>r.id===map?.currentRoomId);
    // Canon zone name is already public at the player's current location.
    mapSubtitle.textContent=state.scene?.environment?.name||
      state.scene?.room?.name||(currentRoom?.visited?currentRoom.name:'')||'';
    mapSubtitle.hidden=!mapSubtitle.textContent;
    if(!rooms.length){
      const p=document.createElement('p');p.textContent='Bản đồ đang được chuẩn bị.';
      choices.appendChild(p);return;
    }
    const byId=new Map(rooms.map(r=>[r.id,r]));
    const root=byId.has(map.entryRoomId)?map.entryRoomId:rooms[0].id;
    const layers=[],depth=new Map([[root,0]]),queue=[root];
    while(queue.length){
      const id=queue.shift(),level=depth.get(id);
      if(!layers[level])layers[level]=[];
      layers[level].push(id);
      (byId.get(id).adjacentRoomIds||[]).forEach(other=>{
        if(byId.has(other)&&!depth.has(other)){
          depth.set(other,level+1);queue.push(other);
        }
      });
    }
    // Only Core-published nodes and edges are drawn. Never invent a shortcut.
    rooms.forEach(r=>{
      if(!depth.has(r.id)){
        const level=layers.length;layers[level]=[r.id];depth.set(r.id,level);
      }
    });
    const cardWidth=196,cardHeight=138,xGap=240,yGap=192;
    const width=Math.max(350,Math.max(...layers.map(layer=>layer.length))*xGap+80);
    const height=Math.max(280,layers.length*yGap+90);
    const positions=new Map();
    layers.forEach((layer,y)=>layer.forEach((id,x)=>{
      positions.set(id,{x:width/2+(x-(layer.length-1)/2)*xGap,y:98+y*yGap});
    }));
    const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');
    const svgEl=(tag,attrs)=>{
      const el=document.createElementNS(ns,tag);
      Object.entries(attrs||{}).forEach(([k,v])=>el.setAttribute(k,String(v)));
      return el;
    };
    svg.setAttribute('viewBox','0 0 '+width+' '+height);
    svg.setAttribute('width',String(width));svg.setAttribute('height',String(height));
    svg.setAttribute('role','img');svg.setAttribute('aria-label','Đồ thị các phòng đã phát hiện và các lối chưa khám phá');
    const defs=svgEl('defs'),seenEdges=new Set();
    const snapshotIds=map.snapshots&&typeof map.snapshots==='object'?map.snapshots:{};
    const assets=global.BackroomSnapshotAssets;
    const fallback='file:///android_asset/level_snapshots/level_00__liminal_hall.webp';
    function snapshot(r){
      const identity=typeof snapshotIds[r.id]==='string'?snapshotIds[r.id]:null;
      const file=identity&&assets?.environmentFile?.(identity);
      return file?'file:///android_asset/level_snapshots/'+file:fallback;
    }
    rooms.forEach(r=>(r.adjacentRoomIds||[]).forEach(to=>{
      if(!positions.has(to))return;
      const key=[r.id,to].sort().join('|');
      if(seenEdges.has(key))return;seenEdges.add(key);
      const from=positions.get(r.id),dest=positions.get(to);
      const unlocked=r.visited&&byId.get(to).visited;
      svg.appendChild(svgEl('line',{
        x1:from.x,y1:from.y,x2:dest.x,y2:dest.y,
        stroke:unlocked?'#88b6a0':'#c0a26c','stroke-width':unlocked?3:2.8,
        'stroke-dasharray':unlocked?'none':'10 8',
        class:unlocked?'explorer-map-edge-open':'explorer-map-edge-locked'
      }));
    }));
    rooms.forEach((r,index)=>{
      const pos=positions.get(r.id),current=r.id===map.currentRoomId;
      const left=pos.x-cardWidth/2,top=pos.y-cardHeight/2,visited=!!r.visited;
      const clipId='explorer-map-node-clip-'+index;
      const clip=svgEl('clipPath',{id:clipId});
      clip.appendChild(svgEl('rect',{x:left,y:top,width:cardWidth,height:cardHeight,rx:9}));
      defs.appendChild(clip);
      const g=svgEl('g',{'data-map-node':visited?'explored':'locked'});
      // Every rendered node has the exact snapshot of its Core environment.
      g.appendChild(svgEl('rect',{x:left,y:top,width:cardWidth,height:cardHeight,rx:9,fill:'#111a1d'}));
      g.appendChild(svgEl('image',{x:left,y:top,width:cardWidth,height:cardHeight,
        href:snapshot(r),'preserveAspectRatio':'xMidYMid slice','clip-path':'url(#'+clipId+')'}));
      if(!visited){
        g.appendChild(svgEl('rect',{x:left,y:top,width:cardWidth,height:cardHeight,rx:9,
          fill:'#03070a',opacity:'.79'}));
        const lockX=pos.x,lockY=pos.y+2;
        g.appendChild(svgEl('circle',{cx:lockX,cy:lockY,r:25,
          fill:'#111920',stroke:'#b99b65','stroke-width':1.5}));
        g.appendChild(svgEl('path',{d:'M'+(lockX-10)+' '+(lockY-3)+' v-8 a10 10 0 0 1 20 0 v8',
          fill:'none',stroke:'#e8cc90','stroke-width':3,'stroke-linecap':'round'}));
        g.appendChild(svgEl('rect',{x:lockX-13,y:lockY-4,width:26,height:21,rx:4,
          fill:'#e8cc90'}));
        g.appendChild(svgEl('circle',{cx:lockX,cy:lockY+5,r:2.6,fill:'#293037'}));
      }else{
        g.appendChild(svgEl('rect',{x:left,y:top+96,width:cardWidth,height:42,rx:0,
          fill:'#081216',opacity:'.92'}));
        const words=String(r.name||'').split(/\s+/),lines=[''];
        words.forEach(word=>{
          const previous=lines[lines.length-1];
          if(previous&&previous.length+word.length+1>27&&lines.length<2)lines.push(word);
          else lines[lines.length-1]=(previous?previous+' ':'')+word;
        });
        const label=svgEl('text',{x:pos.x,y:top+(lines.length>1?111:119),
          'text-anchor':'middle','font-size':'12.5','font-weight':'700',
          fill:'#f5e2b4',class:'explorer-map-room-label'});
        lines.forEach((line,n)=>{
          const t=svgEl('tspan',{x:pos.x,dy:n===0?0:15});t.textContent=line;label.appendChild(t);
        });
        g.appendChild(label);
        if(r.marked||r.traced){
          const marker=svgEl('text',{x:left+9,y:top+18,'font-size':'15',
            fill:'#f4cf8d',class:'explorer-map-annotations'});
          marker.textContent=(r.marked?'◆ ':'')+(r.traced?'✚':'');
          g.appendChild(marker);
        }
      }
      g.appendChild(svgEl('rect',{x:left,y:top,width:cardWidth,height:cardHeight,rx:9,
        fill:'none',stroke:current?'#6ec9ff':visited?'#c5ad71':'#89704a',
        'stroke-width':current?3.5:2}));
      if(current){
        // Google-Maps-like blue navigation disc with white direction arrow.
        const cx=pos.x+cardWidth/2-10,cy=top+8;
        const pin=svgEl('g',{'data-map-location':'cao-minh'});
        pin.appendChild(svgEl('circle',{cx,cy,r:25,fill:'#1a73e8',opacity:'.23'}));
        pin.appendChild(svgEl('circle',{cx,cy,r:18,fill:'#1a73e8',stroke:'#ffffff',
          'stroke-width':3.2}));
        pin.appendChild(svgEl('path',{d:'M'+cx+' '+(cy-11)+' L'+(cx-8)+' '+(cy+9)+
          ' L'+cx+' '+(cy+5)+' L'+(cx+8)+' '+(cy+9)+' Z',fill:'#ffffff'}));
        g.appendChild(pin);
      }
      svg.appendChild(g);
    });
    svg.appendChild(defs);
    const sc=document.createElement('div');sc.className='explorer-map-scroll';
    sc.setAttribute('tabindex','0');
    sc.setAttribute('aria-label','Bản đồ có thể cuộn ngang và dọc');
    sc.appendChild(svg);choices.appendChild(sc);
    const legend=document.createElement('p');legend.className='explorer-map-legend';
    legend.textContent='Kéo để xem bản đồ · Nét đứt: chưa khám phá · ◆ Dấu cá nhân · ✚ Vết khắc';
    choices.appendChild(legend);
    const here=positions.get(map.currentRoomId);
    if(here){
      sc.scrollLeft=Math.max(0,here.x-(sc.clientWidth||350)/2);
      sc.scrollTop=Math.max(0,here.y-(sc.clientHeight||400)/2);
    }
  }
  function refresh(){
    const scene=state&&state.scene,combat=!!(state&&state.combat&&state.combat.active);
    panel.hidden=combat||!scene;form.hidden=!combat;
    menuTools.hidden=combat||!scene;
    journal.hidden=true; // Area Log replaces the obsolete clue/answer journal.
    const currentObjective=typeof scene?.objective==='string'?scene.objective.trim():'';
    objectivePanel.hidden=!scene||!currentObjective;
    if(objectiveText.textContent!==currentObjective)objectiveText.textContent=currentObjective;
    if(!scene)return;
    room.textContent='';objective.textContent='';records.replaceChildren();
    if(inference)inference.textContent='';
    buttons.MAP.disabled=!scene.map;
    buttons.AREA_LOG.disabled=!scene.map;
    buttons.TALK.disabled=locked()||!(scene.availableActions||[]).some(a=>a.kind==='TALK');
    casebook.disabled=!scene.map;
    // Invalid input/planner failures leave Player Action editable.
    const showRetry=!!scene.pendingActionId||(failed&&!!scene.generationNeeded);
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
    // MainActivity wraps backroomTurn and renders Snapshot AFTER this callback.
    // Keep the guard alive until that wrapper returns; batches clear it explicitly.
    if(!batchBusy&&global.backroomFreezeSnapshot){
      if(typeof global.requestAnimationFrame==='function')
        global.requestAnimationFrame(()=>{global.backroomFreezeSnapshot=false;});
      else global.backroomFreezeSnapshot=false;
    }
  };
  global.backroomError=function(message){
    preparing=false;failed=true;clearWriterPending();if(error)error(message);
    batchBusy=false;global.backroomFreezeSnapshot=false;refresh();
  };
  global.backroomPlayerActionProgress=function(json){
    // Core has committed a step. Save the newest combined presentation for
    // process-death recovery, but DO NOT render until the whole utterance ends.
    // A multi-action command is one readable turn, not a stream of GM messages.
    const update=JSON.parse(json);
    if(update.state){
      try{
        if(global.localStorage)
          global.localStorage.setItem('backroom-apk-state',JSON.stringify(update.state));
      }catch(_){/* Core save remains authoritative. */}
    }
    const status=document.getElementById('status');
    if(status)status.textContent='Đang xử lý hành động '+update.executed+'/'+update.requested+'…';
    busy=true;refresh();
  };
  function playerActionStopText(result,count){
    const step=Number.isInteger(result.stoppedStep)&&result.stoppedStep>0
      ?' Dừng tại bước '+result.stoppedStep+'.':'';
    return 'Đã xác nhận '+count+' bước.'+step+' '+String(result.message||'Chuỗi hành động chưa hoàn tất.');
  }
  global.backroomPlayerActionFinished=function(raw){
    const result=JSON.parse(raw);batchBusy=false;busy=false;
    failed=false;
    // A confirmed step is never re-submitted by tapping the old draft again.
    // Continuation always requires a fresh deliberate player instruction.
    if(result.executed>0||result.completed){
      draft='';actionInput.value='';actionSend.disabled=true;
    }
    if(!result.completed){
      const s=document.getElementById('status');
      if(s)s.textContent=playerActionStopText(result,result.executed);
    }
    refresh();
  };
  global.backroomPlayerActionError=function(raw){
    const result=JSON.parse(raw);
    batchBusy=false;busy=false;
    // Saved Core pending always has priority over replanning the original text.
    const stopped=playerActionStopText(result,result.committed);
    global.backroomError(stopped);
    const s=document.getElementById('status');if(s)s.textContent=stopped;
    // A partially committed plan must never be replayed from its first step.
    if(result.committed>0){
      draft='';actionInput.value='';actionSend.disabled=true;
      failed=false;refresh();
    }
  };
  global.backroomSyncExplorer=refresh;
  refresh();
})(window);
