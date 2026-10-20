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
      else if(state.playerAction?.recoverable){writerPending('Gehihi');Android.retryPlayerActionNarration(JSON.stringify(state));}
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
  close.textContent='×';close.setAttribute('aria-label','Đóng lựa chọn');
  close.onclick=()=>{dialog.close();dialog.className='';};
  // Closing through Escape/back must also clear the fullscreen presentation mode.
  dialog.addEventListener('close',()=>{dialog.className='';});
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
  const actionSend=document.createElement('button');actionSend.type='button';actionSend.textContent='Thực hiện';actionSend.disabled=true;
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
  function locked(){return busy||batchBusy||coreUnavailable||!!state.playerAction?.recoverable||!!state.pendingEncounter||!!(state.combat&&state.combat.active);}
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
    if(global.appendActionPending)global.appendActionPending(value);
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
    const atlas=Array.isArray(map?.overviewRooms)&&map.overviewRooms.length;
    const rooms=atlas?map.overviewRooms:(Array.isArray(map?.rooms)?map.rooms:[]);
    const currentId=atlas&&map.overviewCurrentRoomId||map?.currentRoomId;
    const entryId=atlas&&map.overviewEntryRoomId||map?.entryRoomId;
    const currentRoom=rooms.find(r=>r.id===currentId);
    // Canon zone name is already public at the player's current location.
    mapSubtitle.textContent=state.scene?.environment?.name||
      state.scene?.room?.name||(currentRoom?.visited?currentRoom.name:'')||'';
    mapSubtitle.hidden=!mapSubtitle.textContent;
    if(!rooms.length){
      const p=document.createElement('p');p.textContent='Bản đồ đang được chuẩn bị.';
      choices.appendChild(p);return;
    }
    const byId=new Map(rooms.map(r=>[r.id,r]));
    // Core supplies opaque Canon cluster membership. Layout never infers identity
    // from a name, snapshot or the randomized Core room id.
    const groups=new Map();
    rooms.forEach(r=>{
      const id=r.atlasGroup||'local';
      if(!groups.has(id))groups.set(id,[]);
      groups.get(id).push(r);
    });
    const cardWidth=196,cardHeight=138,cell=28;
    const groupList=[...groups.entries()];
    // Put strongly connected clusters next to each other. The eight-group search
    // is small (7! arrangements after fixing rotation), deterministic and offline.
    const ring=[[0,0],[1,0],[2,0],[2,1],[2,2],[1,2],[0,2],[0,1]];
    const groupOf=new Map(rooms.map(r=>[r.id,r.atlasGroup||'local']));
    const links=[];
    rooms.forEach(r=>(r.adjacentRoomIds||[]).forEach(to=>{
      if(byId.has(to)&&groupOf.get(to)!==groupOf.get(r.id)&&r.id<to)
        links.push([groupOf.get(r.id),groupOf.get(to)]);
    }));
    let order=groupList.map(([id])=>id),best=Infinity;
    if(order.length===8){
      const score=ids=>{
        const loc=new Map(ids.map((id,i)=>[id,ring[i]]));
        return links.reduce((sum,[a,b])=>{const p=loc.get(a),q=loc.get(b);
          return sum+Math.abs(p[0]-q[0])+Math.abs(p[1]-q[1]);},0);
      };
      const visit=(prefix,left)=>{
        if(!left.length){const s=score(prefix);if(s<best){best=s;order=prefix.slice();}return;}
        left.forEach((id,i)=>visit([...prefix,id],left.filter((_,n)=>n!==i)));
      };
      visit([order[0]],order.slice(1));
    }
    const positions=new Map(),clusters=[];
    // Eight perimeter slots keep the local ring/branches readable; annex at
    // centre. Cards and corridors align to the routing lattice.
    const slots=[[0,0],[9,0],[18,0],[18,8],[18,16],[9,16],[0,16],[0,8]];
    order.forEach((id,index)=>{
      const members=groups.get(id),hub=members.find(r=>r.atlasHub)||members[0];
      const annex=members.find(r=>r!==hub&&r.atlasHub);
      const path=[hub],remaining=new Set(members.filter(r=>r!==hub&&r!==annex).map(r=>r.id));
      // Follow actual intra-cluster adjacency for a stable local cycle; leave
      // non-cycle branches in deterministic slots without adding an edge.
      while(remaining.size){
        const next=(path.at(-1).adjacentRoomIds||[]).find(to=>remaining.has(to))||[...remaining][0];
        remaining.delete(next);path.push(byId.get(next));
      }
      const grid=order.length===8?ring[index]:[index%3,Math.floor(index/3)];
      const origin={x:140+grid[0]*32*cell,y:112+grid[1]*29*cell};
      path.forEach((r,n)=>{
        const slot=slots[n%slots.length];
        positions.set(r.id,{x:origin.x+slot[0]*cell,y:origin.y+slot[1]*cell});
      });
      if(annex)positions.set(annex.id,{x:origin.x+9*cell,y:origin.y+8*cell});
      clusters.push({id,members,x:origin.x-120,y:origin.y-96,width:744,height:676});
    });
    const width=Math.max(...clusters.map(c=>c.x+c.width))+28;
    const height=Math.max(...clusters.map(c=>c.y+c.height))+28;
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

    function snapshot(r){
      const identity=typeof snapshotIds[r.id]==='string'?snapshotIds[r.id]:null;
      const file=identity&&assets?.environmentFile?.(identity);
      return file?'file:///android_asset/level_snapshots/'+file:null;
    }
    clusters.forEach(c=>{
      svg.appendChild(svgEl('rect',{x:c.x,y:c.y,width:c.width,height:c.height,rx:18,
        fill:'#18282a','fill-opacity':'.65',stroke:'#927f50','stroke-opacity':'.6',
        'stroke-width':2,'data-map-cluster':c.id}));
      const name=c.members.find(r=>r.visited&&r.atlasGroupName)?.atlasGroupName;
      if(name){const label=svgEl('text',{x:c.x+18,y:c.y+23,fill:'#e5cd94',
        'font-size':16,'font-weight':700});label.textContent=name;svg.appendChild(label);}
    });
    // Orthogonal A* routes avoid every card, prefer few bends, and penalize
    // occupied corridors. All source edges survive, including loops/one-way.
    const cols=Math.ceil(width/cell)+1,rows=Math.ceil(height/cell)+1;
    const obstacles=new Map(),usage=new Map(),edgeViews=[];
    positions.forEach((p,id)=>{
      for(let x=Math.floor((p.x-cardWidth/2-10)/cell);x<=Math.ceil((p.x+cardWidth/2+10)/cell);x++)
        for(let y=Math.floor((p.y-cardHeight/2-10)/cell);y<=Math.ceil((p.y+cardHeight/2+10)/cell);y++)
          obstacles.set(y*cols+x,id);
    });
    function route(a,b){
      const start=positions.get(a),end=positions.get(b);
      const sx=Math.round(start.x/cell),sy=Math.round(start.y/cell),tx=Math.round(end.x/cell),ty=Math.round(end.y/cell);
      const heap=[];
      function push(v){heap.push(v);let i=heap.length-1;while(i){const p=(i-1)>>1;
        if(heap[p].f<=v.f)break;heap[i]=heap[p];i=p;}heap[i]=v;}
      function pop(){const first=heap[0],v=heap.pop();if(heap.length){let i=0;
        while(i*2+1<heap.length){let j=i*2+1;if(j+1<heap.length&&heap[j+1].f<heap[j].f)j++;
          if(heap[j].f>=v.f)break;heap[i]=heap[j];i=j;}heap[i]=v;}return first;}
      const costs=new Map(),parents=new Map(),dirs=[[1,0],[0,1],[-1,0],[0,-1]];
      const key=(x,y,d)=>(y*cols+x)*4+d;
      for(let d=0;d<4;d++){const k=key(sx,sy,d);costs.set(k,0);push({x:sx,y:sy,d,k,g:0,f:0});}
      while(heap.length){
        const v=pop();if(v.g!==costs.get(v.k))continue;
        if(v.x===tx&&v.y===ty){
          const points=[];let k=v.k;
          while(k!==undefined){const n=Math.floor(k/4);points.push({x:(n%cols)*cell,y:Math.floor(n/cols)*cell});k=parents.get(k);}
          points.reverse();points[0]=start;points[points.length-1]=end;
          points.forEach(p=>{const k=Math.round(p.y/cell)*cols+Math.round(p.x/cell);usage.set(k,(usage.get(k)||0)+1);});
          return points;
        }
        dirs.forEach(([dx,dy],d)=>{
          const x=v.x+dx,y=v.y+dy;if(x<1||x>=cols-1||y<1||y>=rows-1)return;
          const owner=obstacles.get(y*cols+x);if(owner&&owner!==a&&owner!==b)return;
          const k=key(x,y,d),g=v.g+1+(d===v.d?0:1.2)+(usage.get(y*cols+x)||0)*.7;
          if(g>=(costs.get(k)??Infinity))return;
          costs.set(k,g);parents.set(k,v.k);push({x,y,d,k,g,f:g+Math.abs(x-tx)+Math.abs(y-ty)});
        });
      }
      // A routing failure is explicit; never omit a Core edge.
      return [start,{x:start.x,y:28}, {x:end.x,y:28},end];
    }
    const arrow=svgEl('marker',{id:'explorer-map-arrow',viewBox:'0 0 10 10',refX:9,refY:5,
      markerWidth:6,markerHeight:6,orient:'auto-start-reverse'});
    arrow.appendChild(svgEl('path',{d:'M0 0 L10 5 L0 10 Z',fill:'#edc774'}));defs.appendChild(arrow);
    rooms.forEach(r=>(r.adjacentRoomIds||[]).forEach(to=>{
      if(!positions.has(to))return;
      const key=[r.id,to].sort().join('|');if(seenEdges.has(key))return;seenEdges.add(key);
      const unlocked=r.visited&&byId.get(to).visited;
      const points=route(r.id,to);
      const edge=svgEl('path',{d:points.map((p,i)=>(i?'L':'M')+p.x+' '+p.y).join(' '),
        fill:'none',stroke:unlocked?'#88b6a0':'#c0a26c','stroke-width':unlocked?3:2.8,
        'stroke-linejoin':'round','stroke-dasharray':unlocked?'none':'10 8',opacity:'.5',
        class:unlocked?'explorer-map-edge-open':'explorer-map-edge-locked'});
      // Direction arrow halfway down a one-way path, outside the destination card.
      if((r.oneWayRoomIds||[]).includes(to)){
        const mid=Math.max(1,Math.floor(points.length/2));
        const p=points[mid-1],q=points[mid];
        svg.appendChild(svgEl('path',{d:'M'+p.x+' '+p.y+' L'+q.x+' '+q.y,
          fill:'none',stroke:'#edc774','stroke-width':3,'marker-end':'url(#explorer-map-arrow)'}));
      }
      edgeViews.push({a:r.id,b:to,edge});svg.appendChild(edge);
    }));
    function focusRoom(id){edgeViews.forEach(({a,b,edge})=>{
      const active=a===id||b===id;edge.setAttribute('opacity',active?'1':'.16');
      edge.setAttribute('stroke-width',active?'5':'2');
    });}
    rooms.forEach((r,index)=>{
      const pos=positions.get(r.id),current=r.id===currentId;
      const left=pos.x-cardWidth/2,top=pos.y-cardHeight/2,visited=!!r.visited;
      const clipId='explorer-map-node-clip-'+index;
      const clip=svgEl('clipPath',{id:clipId});
      clip.appendChild(svgEl('rect',{x:left,y:top,width:cardWidth,height:cardHeight,rx:9}));
      defs.appendChild(clip);
      const g=svgEl('g',{'data-map-node':visited?'explored':'locked',tabindex:0,role:'button',
        'aria-label':visited?String(r.name||''):'Khu vực chưa khám phá'});
      g.addEventListener('click',()=>focusRoom(r.id));
      g.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();focusRoom(r.id);}});
      // Every rendered node has the exact snapshot of its Core environment.
      g.appendChild(svgEl('rect',{x:left,y:top,width:cardWidth,height:cardHeight,rx:9,fill:'#111a1d'}));
      if(snapshot(r))g.appendChild(svgEl('image',{x:left,y:top,width:cardWidth,height:cardHeight,
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
        fill:'none',stroke:current?'#f1c75e':visited?'#c5ad71':'#89704a',
        'stroke-width':current?3.5:2}));
      if(current){
        // Gold teardrop location pin based on the reference supplied for Cao Minh.
        // Keep the marker within the node, including on the topmost graph row.
        const pinGradient=svgEl('linearGradient',{id:'explorer-map-location-gold',
          x1:'0%',y1:'0%',x2:'85%',y2:'100%'});
        [['0%','#fff4a5'],['37%','#f1c75e'],['72%','#bd8429'],['100%','#704517']]
          .forEach(([offset,color])=>pinGradient.appendChild(
            svgEl('stop',{offset,'stop-color':color})));
        defs.appendChild(pinGradient);
        const pin=svgEl('g',{'data-map-location':'cao-minh',
          transform:'translate('+(pos.x+cardWidth/2-53)+' '+(top+4)+')'});
        pin.appendChild(svgEl('path',{d:'M21 54 C15 44 2 32 2 20 C2 9 10 2 21 2 C32 2 40 9 40 20 C40 32 27 44 21 54 Z',
          fill:'url(#explorer-map-location-gold)',stroke:'#603d17','stroke-width':2.2}));
        pin.appendChild(svgEl('circle',{cx:21,cy:20,r:13.5,
          fill:'#f8f4df',stroke:'#96641c','stroke-width':2}));
        const roads=svgEl('g',{fill:'none',stroke:'#c99936','stroke-width':1.55,
          'stroke-linecap':'round','stroke-linejoin':'round'});
        roads.appendChild(svgEl('path',{d:'M9 23 L16 20 L26 12 L33 17 M11 13 L21 20 L18 33 M26 9 L27 28 L34 26'}));
        roads.appendChild(svgEl('circle',{cx:17,cy:20,r:2.2,
          fill:'#c99936',stroke:'none'}));
        pin.appendChild(roads);
        g.appendChild(pin);
      }
      svg.appendChild(g);
    });
    svg.appendChild(defs);
    const sc=document.createElement('div');sc.className='explorer-map-scroll';
    sc.setAttribute('tabindex','0');
    sc.setAttribute('aria-label','Bản đồ có thể kéo và thu phóng');
    sc.appendChild(svg);choices.appendChild(sc);
    const toolbar=document.createElement('div');toolbar.className='explorer-map-zoom';
    toolbar.setAttribute('role','group');toolbar.setAttribute('aria-label','Thu phóng bản đồ');
    const zoomOut=document.createElement('button'),zoomIn=document.createElement('button');
    const zoomLabel=document.createElement('output'),locate=document.createElement('button');
    zoomOut.type=zoomIn.type=locate.type='button';
    zoomOut.textContent='−';zoomOut.setAttribute('aria-label','Thu nhỏ bản đồ');
    zoomIn.textContent='+';zoomIn.setAttribute('aria-label','Phóng to bản đồ');
    zoomLabel.setAttribute('aria-live','polite');zoomLabel.setAttribute('aria-label','Mức thu phóng');
    locate.textContent='Cao Minh';locate.setAttribute('aria-label','Căn vào vị trí Cao Minh');
    toolbar.append(zoomOut,zoomLabel,zoomIn,locate);choices.appendChild(toolbar);
    let zoom=1;
    const minZoom=.1,maxZoom=2.5;
    const viewport=()=>({x:sc.clientWidth/2,y:sc.clientHeight/2});
    function setZoom(value,anchor=viewport(),world){
      const next=Math.max(minZoom,Math.min(maxZoom,value));
      const point=world||{x:(sc.scrollLeft+anchor.x)/zoom,y:(sc.scrollTop+anchor.y)/zoom};
      zoom=next;
      // Resize the SVG itself so scroll extents and all edges/pins follow its viewBox.
      svg.setAttribute('width',width*zoom);svg.setAttribute('height',height*zoom);
      sc.scrollLeft=Math.max(0,point.x*zoom-anchor.x);
      sc.scrollTop=Math.max(0,point.y*zoom-anchor.y);
      zoomLabel.textContent=Math.round(zoom*100)+'%';
      zoomOut.disabled=zoom<=minZoom;zoomIn.disabled=zoom>=maxZoom;
    }
    zoomLabel.textContent='100%';
    zoomOut.onclick=()=>setZoom(zoom/1.25);zoomIn.onclick=()=>setZoom(zoom*1.25);
    const localPoint=e=>{const rect=sc.getBoundingClientRect();return {x:e.clientX-rect.left,y:e.clientY-rect.top};};
    sc.addEventListener('wheel',e=>{
      e.preventDefault();
      const delta=e.deltaY*(e.deltaMode===1?16:e.deltaMode===2?sc.clientHeight:1);
      setZoom(zoom*Math.exp(-Math.max(-100,Math.min(100,delta))*.002),localPoint(e));
    },{passive:false});
    // Own both one-finger pan and two-finger pinch; browser page zoom stays outside MAP.
    const pointers=new Map();let gesture=null,moved=false;
    function startGesture(){
      const points=[...pointers.values()];
      if(points.length>=2){
        const [a,b]=points,mid={x:(a.x+b.x)/2,y:(a.y+b.y)/2};
        gesture={distance:Math.max(1,Math.hypot(a.x-b.x,a.y-b.y)),zoom,
          world:{x:(sc.scrollLeft+mid.x)/zoom,y:(sc.scrollTop+mid.y)/zoom}};
        moved=true;
      }else gesture=points.length?{point:points[0],left:sc.scrollLeft,top:sc.scrollTop}:null;
    }
    sc.addEventListener('pointerdown',e=>{
      if(e.pointerType!=='touch'&&e.button!==0)return;
      if(!pointers.size)moved=false;
      pointers.set(e.pointerId,localPoint(e));sc.setPointerCapture?.(e.pointerId);startGesture();
    });
    sc.addEventListener('pointermove',e=>{
      if(!pointers.has(e.pointerId)||!gesture)return;
      pointers.set(e.pointerId,localPoint(e));
      const points=[...pointers.values()];
      if(points.length>=2){
        const [a,b]=points,mid={x:(a.x+b.x)/2,y:(a.y+b.y)/2};
        setZoom(gesture.zoom*Math.hypot(a.x-b.x,a.y-b.y)/gesture.distance,mid,gesture.world);
      }else{
        const dx=gesture.point.x-points[0].x,dy=gesture.point.y-points[0].y;
        if(Math.hypot(dx,dy)>5)moved=true;
        sc.scrollLeft=gesture.left+dx;sc.scrollTop=gesture.top+dy;
      }
    });
    const endPointer=e=>{pointers.delete(e.pointerId);startGesture();};
    sc.addEventListener('pointerup',endPointer);sc.addEventListener('pointercancel',endPointer);
    sc.addEventListener('lostpointercapture',endPointer);
    sc.addEventListener('click',e=>{
      if(moved&&e.detail!==0){e.preventDefault();e.stopPropagation();moved=false;}
    },true);
    const legend=document.createElement('p');legend.className='explorer-map-legend';
    legend.textContent='Kéo để xem · Hai ngón/con lăn để zoom · Chọn phòng để theo đường nối · Mũi tên: một chiều · Nét đứt: chưa khám phá · ◆ Dấu cá nhân · ✚ Vết khắc';
    choices.appendChild(legend);
    // Keep Core-approved obstruction notes outside the graph, never on a node.
    (Array.isArray(map.knownObstructions)?map.knownObstructions:[]).forEach(reason=>{
      const p=document.createElement('p');p.className='explorer-map-obstruction';
      p.textContent=String(reason);choices.appendChild(p);
    });
    const here=positions.get(currentId);
    const centre=()=>{if(here){
      sc.scrollLeft=Math.max(0,here.x*zoom-(sc.clientWidth||350)/2);
      sc.scrollTop=Math.max(0,here.y*zoom-(sc.clientHeight||400)/2);
    }};locate.onclick=centre;centre();if(global.requestAnimationFrame)global.requestAnimationFrame(centre);
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
    const showRetry=!!scene.pendingActionId||!!state.playerAction?.recoverable||(failed&&!!scene.generationNeeded);
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
    if(dialog.open&&dialog.className==='explorer-map-mode')openMap();
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
      // Keep the in-memory presentation in sync too; render remains once per
      // utterance, and the initial pending player row remains visible.
      state=update.state;
      try{
        if(global.localStorage)
          global.localStorage.setItem('backroom-apk-state',JSON.stringify(update.state));
      }catch(_){/* Core save remains authoritative. */}
    }
    const status=document.getElementById('status');
    if(status)status.textContent=update.requested>0?'Đang xử lý hành động '+update.executed+'/'+update.requested+'…':'Đang phân tích yêu cầu…';
    busy=true;refresh();
  };
  function playerActionStopText(result,count){
    const step=Number.isInteger(result.stoppedStep)&&result.stoppedStep>0
      ?' Dừng tại bước '+result.stoppedStep+'.':'';
    return 'Đã xử lý '+count+' ý định.'+step+' '+String(result.message||'Chuỗi hành động chưa hoàn tất.');
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
