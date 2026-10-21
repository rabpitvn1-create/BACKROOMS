const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.js'),'utf8');
function runtime(scene={}){
  const elements={};
  function element(){
    return {children:[],listeners:{},attributes:{},disabled:false,hidden:false,scrollLeft:0,scrollTop:0,clientWidth:360,clientHeight:480,
      getBoundingClientRect(){return {left:10,top:20};},
      append(...nodes){nodes.forEach(node=>{node.parent=this;this.children.push(node);});},
      appendChild(node){node.parent=this;this.children.push(node);},
      replaceChildren(...nodes){this.children=[];this.append(...nodes);},
      setAttribute(k,v){this.attributes[k]=v;},
      addEventListener(k,fn){this.listeners[k]=fn;},
      querySelector(selector){return this.querySelectorAll(selector)[0]||null;},
      querySelectorAll(selector){
        const found=[];function walk(node){
          for(const child of node.children){
            if(selector.startsWith('.')&&String(child.className||'').split(/\s+/).includes(selector.slice(1))||
              selector==='[data-scene-preparing="1"]'&&child.attributes['data-scene-preparing']==='1')found.push(child);
            walk(child);
          }
        }walk(this);return found;
      },
      remove(){if(this.parent)this.parent.children=this.parent.children.filter(n=>n!==this);},
      focus(){this.focused=true;},
      showModal(){this.open=true;},close(){this.open=false;}
    };
  }
  elements.playerActionBar=element();elements.form=element();elements.log=element();elements.status=element();
  for(const id of ['sceneJournal','sceneJournalRoom','sceneJournalObjective','sceneJournalRecords',
    'explorerMenuTools','explorerMenuObjective','explorerCurrentObjective','explorerCasebook','explorerQuestItems','gameMenuClose'])elements[id]=element();
  elements.gameMenuClose.click=()=>{elements.gameMenuClose.clicked=true;};
  const calls=[],stored=[];
  const document={getElementById:id=>elements[id],createElement:element,createElementNS:element,body:element()};
  const ctx={document,state:{scene:{miniIndex:1,generationNeeded:false,map:{currentRoomId:'hall',entryRoomId:'hall',rooms:[{id:'hall',name:'Hành lang',visited:true,adjacentRoomIds:['x']},{id:'x',name:'Chưa khám phá',visited:false,adjacentRoomIds:[]}]},areaLog:[],availableActions:[{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x',label:'Phòng X'}],...scene}},busy:false,coreUnavailable:false,
    Android:{prepareScene:json=>calls.push(['prepare',json]),submitSceneAction:(json,c)=>calls.push(['action',c]),submitPlayerAction:(json,text)=>calls.push(['player',text]),submitGmSuggestion:(json,index)=>calls.push(['gm-suggestion',index]),requestWorldHint:tier=>calls.push(['hint',tier]),retryPlayerActionNarration:json=>calls.push(['narration',json])},
    BackroomSnapshotAssets:{environmentFile:id=>({
      yellow_rot:'level_00__zone_01__yellow_rot.webp',
      deadlight:'level_00__zone_02__deadlight.webp'
    })[id]||null},
    localStorage:{setItem:(key,value)=>stored.push([key,value])},
    appendActionPending(){},backroomTurn(json){ctx.state=JSON.parse(json);ctx.busy=false;},backroomError(){ctx.busy=false;}};
  ctx.render=()=>{elements.log.replaceChildren(...(ctx.state.log||[]).map(entry=>{
    const article=element();article.className='message '+entry.role;return article;
  }));};
  ctx.window=ctx;vm.runInNewContext(source,ctx);
  const panel=elements.playerActionBar.children[0],dialog=document.body.children[0];
  return {ctx,calls,stored,panel,dialog,actionDialog:document.body.children[1],buttons:panel.children[0].children.map(slot=>slot.children[0]),menuTools:elements.explorerMenuTools,objectivePanel:elements.explorerMenuObjective,objectiveText:elements.explorerCurrentObjective,casebook:elements.explorerCasebook,menuClose:elements.gameMenuClose,player:panel.children[1].children[0],retry:panel.children[1].children[1],journal:elements.sceneJournal,room:elements.sceneJournalRoom,objective:elements.sceneJournalObjective,records:elements.sceneJournalRecords,log:elements.log,status:elements.status};
}
test('four GM hints are selectable INSIDE GM log and each tap queues a single Core action',()=>{
  const r=runtime({nodeId:'0'});
  const narration='Tường rung lên.\nGỢI Ý HÀNH ĐỘNG:\n'
    +'1. Kiểm tra vết bụi ở chân cửa phía nam.\n'
    +'2. Quan sát dấu xước ngang trên trần.\n'
    +'3. Lắng nghe bóng đèn đang rung phía tây.\n'
    +'4. Thử kéo vạt thảm đang bị ẩm.';
  r.ctx.state.log=[{role:'gm',sceneMap:'0:1',text:narration}];
  r.ctx.render();
  const choices=r.log.querySelector('.gm-suggestion-list');
  assert.ok(choices,'options must be placed in the original GM article');
  assert.equal(choices.children.length,4);
  assert.deepEqual(choices.children.map(b=>b.textContent.slice(0,2)),['1.','2.','3.','4.']);
  choices.children[3].onclick();
  assert.equal(r.calls.filter(x=>x[0]==='gm-suggestion').length,1);
  assert.deepEqual(r.calls.at(-1),['gm-suggestion',4]);
  assert.equal(r.calls.filter(x=>x[0]==='player').length,0,'Player Action composer stays separate');
  assert.equal(choices.children.every(b=>b.disabled),true,'one tap prevents rapid duplicate submissions');
});
test('GM suggestions unlock only after the completed Player Action event',()=>{
  const r=runtime({nodeId:'0'});
  const text='GỢI Ý HÀNH ĐỘNG:\n'
    +'1. Kiểm tra dấu xước trên vách.\n'
    +'2. Quan sát lối đi phía tây.\n'
    +'3. Lắng nghe âm thanh bên ngoài.\n'
    +'4. Chạm nhẹ vào mép thảm.';
  r.player.onclick();
  const input=r.actionDialog.children[1],send=r.actionDialog.children[2];
  input.value='Tôi quan sát căn phòng';input.oninput();send.onclick();
  assert.equal(r.calls.at(-1)[0],'player');
  // Core emits backroomTurn while batchBusy is still true. The narration
  // initially renders disabled. The finish event must re-enable these nodes.
  r.ctx.backroomTurn(JSON.stringify({...r.ctx.state,
    log:[{role:'gm',sceneMap:'0:1',text}],
    playerAction:{status:'complete',recoverable:false}}));
  r.ctx.render();
  const list=r.log.querySelector('.gm-suggestion-list');
  assert.ok(list);
  assert.equal(list.children.every(b=>b.disabled),true);
  r.ctx.backroomPlayerActionFinished(JSON.stringify({executed:1,completed:true}));
  assert.equal(list.children.every(b=>!b.disabled),true);
  list.children[0].onclick();
  assert.deepEqual(r.calls.at(-1),['gm-suggestion',1]);
  assert.equal(list.children.every(b=>b.disabled),true);
});

test('GM buttons obey pending and retry locks and recover when the lock clears',()=>{
  const r=runtime({nodeId:'0'});
  const text='GỢI Ý HÀNH ĐỘNG:\n'
    +'1. Kiểm tra dấu xước trên vách.\n'
    +'2. Quan sát lối đi phía tây.\n'
    +'3. Lắng nghe âm thanh bên ngoài.\n'
    +'4. Chạm nhẹ vào mép thảm.';
  r.ctx.state.log=[{role:'gm',sceneMap:'0:1',text}];
  r.ctx.render();
  const list=r.log.querySelector('.gm-suggestion-list');
  assert.equal(list.children.every(b=>!b.disabled),true);
  r.ctx.state.scene.pendingActionId='pending';
  r.ctx.backroomSyncExplorer();
  assert.equal(list.children.every(b=>b.disabled),true);
  list.children[0].onclick();
  assert.equal(r.calls.filter(c=>c[0]==='gm-suggestion').length,0);
  delete r.ctx.state.scene.pendingActionId;
  r.ctx.state.playerAction={recoverable:true};
  r.ctx.backroomSyncExplorer();
  assert.equal(list.children.every(b=>b.disabled),true);
  r.ctx.state.playerAction={recoverable:false};
  r.ctx.backroomSyncExplorer();
  assert.equal(list.children.every(b=>!b.disabled),true);
});

test('previous scene GM hints never remain actionable',()=>{
  const r=runtime({nodeId:'0',miniIndex:2});
  r.ctx.state.log=[{role:'gm',sceneMap:'0:1',text:'GỢI Ý HÀNH ĐỘNG:\n1. Lắng nghe tiếng gió.\n2. Thử nhìn sàn.\n3. Đọc mảnh giấy.\n4. Gõ nhẹ cửa.'}];
  r.ctx.render();
  assert.equal(r.log.querySelector('.gm-suggestion-list'),null);
});
test('MAP, Area Log, Giao tiếp occupy the top row; Player Action and Retry share the bottom slot',()=>{
  const r=runtime();
  assert.deepEqual(r.buttons.map(b=>b.textContent),['MAP','Area Log','Giao tiếp']);
  assert.equal(r.buttons[2].disabled,true);
  assert.equal(r.panel.children[0].children.length,3);
  assert.equal(r.panel.children[1].children.length,2);
  assert.equal(r.panel.children.some(x=>x===r.objectivePanel||x===r.casebook),false);
  assert.equal(r.menuTools.hidden,false);
  assert.equal(r.player.textContent,'Player Action');
  assert.equal(r.player.hidden,false);assert.equal(r.retry.hidden,true);
  assert.equal(r.retry.className,'explorer-retry-danger');
  r.buttons[0].onclick();r.dialog.children[0].children[1].onclick();
  r.buttons[1].onclick();r.dialog.children[0].children[1].onclick();
  assert.equal(r.calls.length,0,'MAP and Area Log do not submit gameplay actions');
});
test('Casebook is read-only and three hint tiers require explicit request',()=>{
  const r=runtime({runtime:'world-graph-v3',sceneVersion:9,room:{id:'hall'},
    records:[{id:'hall',kind:'FIELD_NOTE',text:'Ghi nhận vết ố trên tường.'}]});
  const casebook=r.casebook;
  assert.equal(r.calls.length,0);
  casebook.onclick();
  assert.equal(r.menuClose.clicked,true,'close GAME MENU before opening casebook dialog');
  const notes=r.dialog.querySelectorAll('.explorer-casebook-record');
  assert.equal(notes.length,1);
  assert.equal(notes[0].textContent,'Ghi nhận vết ố trên tường.');
  const hint=r.dialog.children[1].children.at(-1);
  hint.onclick();hint.onclick();hint.onclick();hint.onclick();
  assert.deepEqual(r.calls,[['hint',1],['hint',2],['hint',3]]);
  r.ctx.backroomWorldHint(JSON.stringify({roomId:'wrong',sceneVersion:9,tier:1,text:'stale'}));
  assert.equal(r.dialog.querySelectorAll('.explorer-hint').length,0);
  r.ctx.backroomWorldHint(JSON.stringify({roomId:'hall',sceneVersion:9,tier:3,text:'Kiểm tra dấu'}));
  assert.equal(r.dialog.querySelectorAll('.explorer-hint').length,1);
});
test('GM quest item tile displays single-use items without changing Core inventory',()=>{
  const r=runtime({runtime:'world-graph-v3'});
  r.ctx.state.gmQuestItems={version:1,items:[
    {id:'clue_001',name:'Mảnh ký hiệu',description:'Dùng để đối chiếu',nodeId:'ls_2',status:'held'},
    {id:'clue_002',name:'Thẻ đã dùng',description:'Không còn tác dụng',nodeId:'ls_2',status:'consumed'}
  ]};
  r.ctx.state.inventory=[{id:'core_item',quantity:4}];
  r.ctx.document.getElementById('explorerQuestItems').onclick();
  const entries=r.dialog.querySelectorAll('.gm-quest-item');
  assert.equal(entries.length,2);
  assert.equal(entries[0].children[0].textContent,'Mảnh ký hiệu');
  assert.equal(entries[1].children.length,3,'consumed item has no use control');
  assert.equal(r.calls.length,0);
  assert.deepEqual(r.ctx.state.inventory,[{id:'core_item',quantity:4}]);
  entries[0].children[3].onclick();
  assert.equal(r.actionDialog.open,true);
  assert.match(r.actionDialog.children[1].value,/Mảnh ký hiệu/);
  assert.equal(r.calls.length,0,'using a draft never consumes the item automatically');
});
test('Core action palette bypasses Gemini planning but still sends only an offered validated command',()=>{
  const r=runtime({runtime:'world-graph-v3',room:{id:'hall'},records:[],
    availableActions:[{actionId:'world:3:SEARCH:inspect',sceneVersion:3,
      roomId:'hall',kind:'SEARCH',targetId:'inspect',label:'Khảo sát cấu trúc hiện tại'}]});
  r.casebook.onclick();
  assert.equal(r.calls.length,0);
  r.dialog.children[1].children[0].onclick();
  assert.equal(r.dialog.children[1].children[0].textContent,'Khảo sát cấu trúc hiện tại');
  r.dialog.children[1].children[0].onclick();
  assert.equal(r.calls.length,1);
  assert.equal(r.calls[0][0],'action');
  assert.deepEqual(JSON.parse(r.calls[0][1]),{
    actionId:'world:3:SEARCH:inspect',sceneVersion:3,roomId:'hall',kind:'SEARCH',targetId:'inspect'
  });
  assert.equal(r.calls.some(([type])=>type==='player'||type==='hint'),false);
});
test('objective and casebook live inside GAME MENU and disappear without a scene',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  const menu=html.slice(html.indexOf('id="gameMenuModal" hidden'),html.indexOf('id="sceneJournal"'));
  assert.match(menu,/id="explorerMenuTools"/);
  assert.match(menu,/id="explorerMenuObjective"/);
  assert.match(menu,/id="explorerCurrentObjective"/);
  assert.match(menu,/id="explorerCasebook"/);
  const r=runtime();
  assert.equal(r.menuTools.hidden,false);
  assert.equal(r.objectivePanel.hidden,true);
  assert.equal(r.casebook.disabled,false);
  r.ctx.backroomTurn(JSON.stringify({...r.ctx.state,scene:null}));
  assert.equal(r.menuTools.hidden,true);
});
test('mini-map objective updates in GAME MENU without exposing hidden clues',()=>{
  const r=runtime({objective:'Tìm lối sang phòng kế tiếp.',records:[{text:'MẬT MÃ 213546'}],
    temporaryInference:'Gợi ý 213546'});
  assert.equal(r.objectivePanel.hidden,false);
  assert.equal(r.objectiveText.textContent,'Tìm lối sang phòng kế tiếp.');
  assert.equal(r.journal.hidden,true);
  assert.doesNotMatch(r.objectiveText.textContent,/213546|Gợi ý/);
  const changed={...r.ctx.state,scene:{...r.ctx.state.scene,objective:'Kiểm tra cửa thoát.'}};
  r.ctx.backroomTurn(JSON.stringify(changed));
  assert.equal(r.objectiveText.textContent,'Kiểm tra cửa thoát.');
  r.ctx.backroomTurn(JSON.stringify({...changed,scene:{...changed.scene,objective:''}}));
  assert.equal(r.objectivePanel.hidden,true);
  assert.equal(r.objectiveText.textContent,'');
});
test('Player Action empty submit is disabled and closing retains the editable draft',()=>{
  const r=runtime();r.player.onclick();
  const [head,input,send]=r.actionDialog.children;
  assert.equal(send.disabled,true);assert.equal(r.actionDialog.open,true);
  input.value='   ';input.oninput();assert.equal(send.disabled,true);
  input.value='Quan sát hành lang.\nLắng nghe tiếng đèn.';input.oninput();assert.equal(send.disabled,false);
  head.children[1].onclick();assert.equal(r.actionDialog.open,false);
  r.player.onclick();assert.equal(r.actionDialog.open,true);
  assert.equal(input.value,'Quan sát hành lang.\nLắng nghe tiếng đèn.');
  assert.equal(r.calls.length,0,'opening and closing the composer must not submit an action');
});
test('Player Action submits original natural text to the Core interpreter without forging choices',()=>{
  const r=runtime();r.player.onclick();assert.equal(r.actionDialog.open,true);
  const input=r.actionDialog.children[1],send=r.actionDialog.children[2];
  input.value='Cao Minh đi qua hành lang tới góc ẩm rồi gạt cầu dao 2-1-3-5-4-6';
  input.oninput();send.onclick();
  assert.equal(r.calls.length,1);assert.equal(r.calls[0][0],'player');
  assert.equal(r.calls[0][1],input.value);
  assert.equal(r.actionDialog.open,false);
});
test('MAP is fullscreen with Level 0, current zone and a gold location pin',()=>{
  const r=runtime({environment:{id:'yellow_rot',name:'Vàng Úa'},map:{
    currentRoomId:'hall',entryRoomId:'hall',
    snapshots:{hall:'yellow_rot',wet:'deadlight'},
    rooms:[{id:'hall',name:'Hành lang vàng',visited:true,adjacentRoomIds:['wet']},
      {id:'wet',name:'Chưa khám phá',visited:false,adjacentRoomIds:[]}]
  }});
  r.buttons[0].onclick();
  assert.equal(r.dialog.className,'explorer-map-mode');
  assert.equal(r.dialog.children[0].children[0].textContent,'LEVEL 0');
  assert.equal(r.dialog.children[0].children[2].textContent,'Vàng Úa');
  assert.equal(r.dialog.children[0].children[2].hidden,false);
  const sc=r.dialog.children[1].children[0],svg=sc.children[0];
  assert.equal(svg.attributes.role,'img');
  assert.equal(sc.attributes.tabindex,'0');
  assert.ok(Number(svg.attributes.height)>240,'large map keeps its native graph size');
  const pins=svg.children.flatMap(g=>g.children||[])
    .filter(el=>el.attributes?.['data-map-location']==='cao-minh');
  assert.equal(pins.length,1);
  assert.ok(pins[0].children.some(el=>String(el.attributes?.d||'').includes('M21 54')),'gold teardrop silhouette');
  assert.ok(pins[0].children.some(el=>el.attributes?.fill==='url(#explorer-map-location-gold)'));
  assert.ok(pins[0].children.some(el=>el.attributes?.fill==='#f8f4df'),'inset map medallion');
  assert.equal(svg.children.filter(el=>el.attributes?.['data-map-node']==='explored').length,1);
  assert.equal(svg.children.some(el=>el.attributes?.fill==='#1a73e8'),false);
  const visited=svg.children.find(el=>el.attributes?.['data-map-node']==='explored');
  const labels=visited.children.filter(el=>el.attributes?.class==='explorer-map-room-label');
  assert.equal(labels.length,1);
  assert.equal(labels[0].children.map(el=>el.textContent).join(' '),'Hành lang vàng');
  assert.equal(r.calls.length,0,'map is read-only');
  r.dialog.children[0].children[1].onclick();assert.equal(r.dialog.open,false);
  assert.equal(r.dialog.className,'','closing MAP clears fullscreen mode');
  r.buttons[1].onclick();assert.equal(r.dialog.className,'','other popups restore normal size');
});
test('undiscovered snapshot is dimmed, nameless, padlocked and connected by a dashed edge',()=>{
  const r=runtime({map:{currentRoomId:'hall',entryRoomId:'hall',
    snapshots:{hall:'yellow_rot',wet:'deadlight'},
    rooms:[{id:'hall',name:'Hành lang vàng',visited:true,marked:true,traced:true,adjacentRoomIds:['wet']},
      {id:'wet',name:'SECRET NEVER DISPLAY',visited:false,adjacentRoomIds:[]}]
  }});
  r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const edges=svg.children.filter(el=>el.attributes?.class==='explorer-map-edge-locked');
  assert.equal(edges.length,1);
  assert.equal(edges[0].attributes['stroke-dasharray'],'10 8');
  const locked=svg.children.find(el=>el.attributes?.['data-map-node']==='locked');
  const opened=svg.children.find(el=>el.attributes?.['data-map-node']==='explored');
  assert.ok(locked);
  assert.ok(locked.children.some(el=>el.attributes?.opacity==='.79'));
  assert.ok(locked.children.some(el=>el.attributes?.fill==='#e8cc90'),'lock body');
  assert.equal(locked.children.some(el=>el.attributes?.class==='explorer-map-room-label'),false);
  assert.equal(locked.children.some(n=>String(n.textContent||'').includes('SECRET NEVER DISPLAY')),false);
  const sources=svg.children.flatMap(el=>el.children||[]).filter(el=>typeof el.attributes?.href==='string')
    .map(el=>el.attributes.href);
  assert.ok(sources.some(src=>src.endsWith('/level_00__zone_01__yellow_rot.webp')));
  assert.ok(sources.some(src=>src.endsWith('/level_00__zone_02__deadlight.webp')));
  assert.equal(opened.children.some(el=>el.attributes?.class==='explorer-map-annotations'),true);
});
test('fullscreen graph can show every locked region while planner map stays limited',()=>{
  const r=runtime({map:{currentRoomId:'a',entryRoomId:'a',
    snapshots:{a:'yellow_rot',b:'deadlight',c:'yellow_rot'},
    rooms:[{id:'a',name:'Khu A',visited:true,adjacentRoomIds:['b']}],
    overviewRooms:[
      {id:'a',name:'Khu A',visited:true,adjacentRoomIds:['b']},
      {id:'b',name:'',visited:false,adjacentRoomIds:['a','c']},
      {id:'c',name:'SECRET ZONE',visited:false,adjacentRoomIds:['b']}
    ]
  }});
  r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const nodes=svg.children.filter(el=>el.attributes?.['data-map-node']);
  assert.equal(nodes.length,3,'use the complete Core atlas, not just frontier');
  assert.equal(nodes.filter(el=>el.attributes['data-map-node']==='locked').length,2);
  assert.equal(svg.children.filter(el=>el.attributes?.class==='explorer-map-edge-locked').length,2);
  assert.equal(nodes.some(n=>n.children.some(el=>String(el.textContent||'').includes('SECRET ZONE'))),false);
});
test('explored connected rooms use solid lines while all true Core loops remain',()=>{
  const r=runtime({map:{currentRoomId:'a',entryRoomId:'a',
    rooms:[{id:'a',name:'Khu A',visited:true,adjacentRoomIds:['b','c']},
      {id:'b',name:'Khu B',visited:true,adjacentRoomIds:['a','c']},
      {id:'c',name:'Khu C',visited:true,adjacentRoomIds:['a','b']}] }});
  r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const edges=svg.children.filter(el=>el.attributes?.class==='explorer-map-edge-open');
  assert.equal(edges.length,3,'triangle loop must not be reduced to a linear path');
  assert.ok(edges.every(el=>el.attributes['stroke-dasharray']==='none'));
});
test('fullscreen map scroll respects the CSS frame without forcing SVG scaling',()=>{
  const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.css'),'utf8');
  assert.ok(css.includes('#explorerChoices.explorer-map-mode'));
  assert.ok(css.includes('#explorerChoices.explorer-map-mode:not([open]){display:none!important}'),'closed fullscreen dialog cannot stay visible');
  assert.ok(css.includes('height:var(--app-height,100dvh)!important'));
  assert.ok(css.includes('max-height:none!important'));
  assert.ok(css.includes('touch-action:none'));
  assert.ok(css.includes('.explorer-map-scroll svg{'));
});
test('MAP shows only inspected blocked seams with generic reason and no hidden endpoint',()=>{
  const r=runtime({map:{currentRoomId:'hall',entryRoomId:'hall',rooms:[
    {id:'hall',name:'Hành lang',visited:true,adjacentRoomIds:[]}
  ],knownObstructions:['Lối nối đã được nhận biết nhưng chưa đủ chứng cứ kiểm chứng để mở.']}});
  r.buttons[0].onclick();
  const notices=r.dialog.querySelectorAll('.explorer-map-obstruction');
  assert.equal(notices.length,1);
  assert.match(notices[0].textContent,/chưa đủ chứng cứ/);
  assert.doesNotMatch(notices[0].textContent,/passage:|wet-ceiling|loop-reflection/);
  assert.equal(r.calls.length,0);
});
test('device controls use one popup, preserve Core commands and stay open across inputs',()=>{
  const lever=(id,version)=>({actionId:'run:0:'+version+':SEARCH:'+id,sceneVersion:version,
    roomId:'panel',kind:'SEARCH',targetId:id,label:id==='up'?'Gạt cần lên':'Gạt cần xuống',mechanismId:'door_lock'});
  const r=runtime({availableActions:[lever('up',3),lever('down',3)]});
  r.player.onclick();
  const field=r.actionDialog.children[1];field.value='Mở bảng điều khiển';field.oninput();
  r.actionDialog.children[2].onclick();
  assert.equal(r.dialog.open,true);
  assert.equal(r.dialog.children[0].children[0].textContent,'Bảng điều khiển');
  assert.equal(r.dialog.children[1].children.length,3,'close button and two physical controls');
  r.dialog.children[1].children[1].onclick();
  assert.equal(r.calls.length,1);
  assert.equal(r.ctx.backroomFreezeSnapshot,true,'stationary controls freeze Snapshot while Core processes input');
  assert.equal(r.dialog.open,true,'popup remains open for multi-step puzzle');
  assert.deepEqual(JSON.parse(r.calls[0][1]),{
    actionId:'run:0:3:SEARCH:up',sceneVersion:3,roomId:'panel',kind:'SEARCH',targetId:'up'
  });
  const next={...r.ctx.state,scene:{...r.ctx.state.scene,availableActions:[lever('up',4),lever('down',4)]}};
  r.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(r.ctx.backroomFreezeSnapshot,false,'Snapshot freeze clears after Core acknowledgement');
  assert.equal(r.dialog.open,true);
  r.dialog.children[1].children[2].onclick();
  assert.equal(r.calls.length,2);
  assert.equal(r.ctx.backroomFreezeSnapshot,true);
  assert.equal(JSON.parse(r.calls[1][1]).sceneVersion,4,'the popup must use refreshed Core choices');
  r.ctx.backroomTurn(JSON.stringify({...next,scene:{...next.scene,availableActions:[],completed:true}}));
  assert.equal(r.dialog.open,false,'Core completion automatically closes the device popup');
  assert.equal(r.ctx.backroomFreezeSnapshot,false,'completed mechanism resumes normal snapshot rendering');
});
test('Player Action commits Core facts before one Gehihi pass for the whole utterance',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const player=java.slice(java.indexOf('@JavascriptInterface public void submitPlayerAction('),
    java.indexOf('@JavascriptInterface public void submitSceneAction('));
  const direct=java.slice(java.indexOf('@JavascriptInterface public void submitSceneAction('),
    java.indexOf('@JavascriptInterface public void submitTurn('));
  assert.match(player,/PlayerActionPlan\.parse/);
  assert.match(player,/staged\.getString\("result"\)/);
  assert.match(player,/if \("FREE"\.equals\(kind\)\)/);
  assert.match(player,/narrateMixedPlayerAction/);
  assert.ok(player.indexOf('commitSceneAction(')<player.indexOf('String reply = narrateMixedPlayerAction('));
  assert.match(player,/finishPlayerPresentation/);
  assert.match(player,/emit\("backroomTurn", attachGmItems\(requireGameCore\(\)\.currentClientState\(projection\)\)\)/);
  assert.match(direct,/narrateStagedSceneAction\(stagedEvent\)/);
});
test('failed GM narration retries the persisted pending step, never the prior Player Action plan',()=>{
  const r=runtime();r.player.onclick();
  const input=r.actionDialog.children[1];input.value='Đi vào phòng bên cạnh';input.oninput();
  r.actionDialog.children[2].onclick();
  r.ctx.backroomTurn(JSON.stringify({...r.ctx.state,scene:{...r.ctx.state.scene,
    pendingActionId:'run:1:MOVE:room:x'}}));
  r.ctx.backroomPlayerActionError(JSON.stringify({committed:1,message:'Gehihi tạm thời gián đoạn'}));
  assert.equal(r.retry.hidden,false);
  r.retry.onclick();
  assert.equal(r.calls.length,2);
  assert.equal(r.calls[1][0],'action');
  assert.equal(r.calls[1][1],'RETRY');
});
test('counted footsteps bypass the Gemini planner, save per-step Core receipts and halt on Entity',()=>{
  const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const facade=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
  const graph=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/WorldGraphRuntime.kt'),'utf8');
  const spawn=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/EntitySpawnPool.kt'),'utf8');
  assert.match(native,/PlayerFootstepIntent\.count\(playerText\)/);
  assert.match(native,/JSONObject initial = new JSONObject\(requireGameCore\(\)\.scenePlayerActionContext\(\)\)/);
  assert.match(native,/executePlayerFootsteps\(playerText, footstepCount, projection\)/);
  assert.match(facade,/while \(executed < count\)/);
  assert.match(facade,/if \(fatal \|\| CombatRuntime\.pending\(loadCurrentState\(\)\) != null\) break/);
  assert.match(facade,/commitSceneAction\(command\.getString\("actionId"\), event\.getString\("result"\), presentation\)/);
  assert.match(graph,/fun footstepCommand\(p: JSONObject\)/);
  assert.match(spawn,/kind !in setOf\("MOVE", "SEARCH", "STEP", "FREE", "INTERACT", "TALK"\)/);
});

test('mechanism button presses use local Core narration, not a remote GM call',()=>{
  const runtimeSource=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/MiniMapRuntime.kt'),'utf8');
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  assert.ok(runtimeSource.includes('put("mechanismId", effect.getString("mechanismId"))'));
  assert.ok(runtimeSource.includes('event.put("mechanismStep", true)'));
  assert.ok(java.includes('stagedEvent.optBoolean("mechanismStep", false)'));
  assert.ok(java.includes('stagedEvent.getString("result")'));
  assert.ok(java.includes("renderSnapshot=function(){if(typeof window!=='undefined'&&window.backroomFreezeSnapshot)return;"));
  assert.ok(java.includes('if(!window.backroomFreezeSnapshot&&cinema&&previousScene)cinema.play'));
  assert.ok(java.includes('if(should&&!window.backroomFreezeSnapshot)requestSnapshot()'));
  assert.ok(runtimeSource.includes('if (deviceControl) "DEVICE"') === false,
    'device routing belongs to GameCoreFacade, not the scene model');
  const facade=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
  assert.ok(facade.includes('if (deviceControl) "DEVICE" else if (sceneKind == "MOVE" || sceneKind == "STEP") "SCENE_MOVE" else "EXECUTE"'));
});
test('MAP is read-only even when a Core-authorized next area is available',()=>{
  const r=runtime({completed:true,availableActions:[
    {actionId:'r:0:9:MOVE:next',sceneVersion:9,roomId:'hall',kind:'MOVE',targetId:'next',label:'Tiến vào khu vực kế tiếp.'}
  ]});
  r.buttons[0].onclick();
  assert.equal(r.calls.length,0);
  assert.doesNotMatch(r.dialog.children[1].textContent||'',/Tiến vào khu vực kế tiếp/);
  const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.css'),'utf8');
  assert.match(css,/explorer-action-slot/);
  assert.match(css,/explorer-retry-danger/);
});
test('generation failure stops automatic requests and leaves explicit retry available',()=>{
  const r=runtime({generationNeeded:true,availableActions:[]});assert.equal(r.calls.length,1);
  r.ctx.backroomError('429');r.ctx.backroomSyncExplorer();assert.equal(r.calls.length,1);
  assert.equal(r.retry.hidden,false);assert.equal(r.player.hidden,true);
  r.retry.onclick();assert.equal(r.calls.length,2);
});
test('current mini-map generation keeps Gemini 4.0 Argon icon and message during Gehihi fallback',()=>{
  const r=runtime({generationNeeded:true,availableActions:[]});
  assert.equal(r.calls.length,1);
  assert.equal(r.log.children.length,1);
  const pending=r.log.children[0];
  const indicator=pending.querySelector('.gm-thinking');
  const logo=pending.querySelector('.scene-provider-logo');
  const label=pending.querySelector('.gm-thinking-name');
  const copy='Gemini 4.0 Argon đang chuẩn bị màn chơi. Xin vui lòng chờ trong giây lát.';
  assert.equal(logo.src,'provider-logos/gemini.svg');
  assert.equal(logo.hidden,false);
  assert.equal(label.textContent,copy);
  assert.equal(indicator.attributes['aria-label'],copy);
  r.ctx.backroomSceneWriterProvider('Gehihi');
  assert.equal(r.log.children.length,1);
  assert.equal(logo.hidden,false);
  assert.equal(label.textContent,copy);
  assert.equal(r.calls.length,1,'provider switch must not restart generation');
  const next={...r.ctx.state,scene:{miniIndex:1,generationNeeded:false,availableActions:[]}};
  r.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(r.log.children.length,0,'completed background generation removes the indicator');
});
test('Android scene worker stays within one mini-map and never emits provider-name changes',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const start=java.indexOf('@JavascriptInterface public void prepareScene(');
  const end=java.indexOf('@JavascriptInterface public void submitSceneAction(',start);
  const worker=java.slice(start,end);
  assert.ok(start>0&&end>start);
  assert.match(worker,/currentMiniMapToken/);
  assert.match(worker,/phase < 2/);
  assert.doesNotMatch(worker,/emit\("backroomProvider"/);
  assert.doesNotMatch(worker,/emit\("backroomSceneWriterProvider"/);
});
test('pending narration retries the saved command and combat hides Explorer',()=>{
  const r=runtime({pendingActionId:'p',availableActions:[]});
  assert.equal(r.player.hidden,true);assert.equal(r.retry.hidden,false);
  r.retry.onclick();assert.equal(r.calls[0][1],'RETRY');
  r.ctx.state.combat={active:true};r.ctx.busy=false;r.ctx.backroomSyncExplorer();assert.equal(r.panel.hidden,true);
});
test('untrusted labels and observations use textContent and no HTML insertion',()=>{
  assert.doesNotMatch(source,/innerHTML|insertAdjacentHTML|eval\(/);
});

test('Area Log shows committed events but does not render puzzle clues, passwords or hints',()=>{
  const r=runtime({areaLog:[{type:'AREA',text:'Đã khám phá hành lang vàng.'},
    {type:'UNLOCK',text:'Một cơ cấu khóa đã mở.'}],
    records:[{text:'MẬT MÃ: 213546'}],temporaryInference:'Gợi ý: 213546'});
  r.buttons[1].onclick();
  const text=r.dialog.children[1].children.map(x=>x.textContent||'').join(' ');
  assert.match(text,/Đã khám phá/);assert.match(text,/khóa đã mở/);
  assert.doesNotMatch(text,/213546|MẬT MÃ|Gợi ý/);
  assert.equal(r.journal.hidden,true);
});
test('planner error leaves the original Player Action editable; committed steps are never retried',()=>{
  const r=runtime();r.player.onclick();
  const input=r.actionDialog.children[1];input.value='Đi tới góc ẩm';input.oninput();
  r.actionDialog.children[2].onclick();
  r.ctx.backroomPlayerActionError(JSON.stringify({committed:0,stoppedStep:1,
    stopCode:'invalid_plan',message:'Không phân tích được yêu cầu'}));
  assert.equal(r.player.hidden,false);
  assert.equal(r.retry.hidden,true,'planner failure has no Core pending command to retry');
  assert.equal(input.value,'Đi tới góc ẩm','draft remains editable');
  r.player.onclick();input.value='Đi vào hành lang';input.oninput();
  r.actionDialog.children[2].onclick();
  assert.deepEqual(r.calls.map(x=>x[0]),['player','player']);
  assert.equal(r.calls[1][1],'Đi vào hành lang');
  const partial=runtime();partial.player.onclick();
  partial.actionDialog.children[1].value='Đi qua hai phòng';
  partial.actionDialog.children[1].oninput();partial.actionDialog.children[2].onclick();
  partial.ctx.backroomPlayerActionError(JSON.stringify({committed:1,message:'Đã dừng'}));
  assert.equal(partial.player.hidden,false,'partial commits must not expose replay-from-start Retry');
  assert.equal(partial.retry.hidden,true);
  assert.equal(partial.calls.length,1);
});
test('Gemini planning has an explicit network budget and never falls back to GM planning',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const worker=java.slice(java.indexOf('private String geminiTextPolicy('),
    java.indexOf('private String[] geminiModelChain()'));
  assert.match(worker,/plannerDeadlineMs = System\.currentTimeMillis\(\) \+ 30_000L/);
  assert.match(worker,/workerAttempt < Math\.min\(2, attempted\.length\)/);
  assert.match(worker,/plannerDeadlineMs - System\.currentTimeMillis\(\)/);
  const action=java.slice(java.indexOf('@JavascriptInterface public void submitPlayerAction('),
    java.indexOf('@JavascriptInterface public void submitSceneAction('));
  assert.match(action,/geminiTextPolicy\(plannerPrompt, -1, 0\.0, 850, false\)/);
  assert.doesNotMatch(action,/gehihiText\(plannerPrompt/);
});
test('One player utterance stays whole until the full requested action count is finished',()=>{
  const r=runtime();r.player.onclick();
  const raw='  Tôi nói: "Đừng tiến thêm!"  ';
  const input=r.actionDialog.children[1];
  input.value=raw;input.oninput();r.actionDialog.children[2].onclick();
  assert.deepEqual(r.calls.map(x=>x[0]),['player']);
  assert.equal(r.calls[0][1],raw,'submit preserves exact player wording');
  const intermediate={...r.ctx.state,turn:2};
  r.ctx.backroomPlayerActionProgress(JSON.stringify({state:intermediate,executed:1,requested:3}));
  assert.equal(r.ctx.state.turn,2,'in-memory state stays aligned with the durable presentation');
  assert.equal(r.stored.length,1,'intermediate presentation is persisted for crash recovery');
  assert.equal(r.stored[0][0],'backroom-apk-state');
  assert.equal(JSON.parse(r.stored[0][1]).turn,2);
  assert.match(r.status.textContent,/1\/3/);
  assert.equal(input.value,raw,'the original is preserved while the group is running');
  r.ctx.backroomPlayerActionProgress(JSON.stringify({state:{...intermediate,turn:3},executed:2,requested:3}));
  assert.equal(r.ctx.state.turn,3);
  assert.equal(r.stored.length,2);
  r.ctx.backroomTurn(JSON.stringify({...intermediate,turn:4}));
  r.ctx.backroomPlayerActionFinished(JSON.stringify({executed:3,requested:3,completed:true}));
  assert.equal(input.value,'','the already executed utterance may not be sent again');
  assert.equal(r.calls.length,1,'multiple actions still originate from a single explicit submit');
  assert.equal(r.retry.hidden,true);
});
test('Core receipts own exact player wording, not autogenerated action labels',()=>{
  const core=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
  const view=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/CoreClientProjection.kt'),'utf8');
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  assert.match(core,/beginSceneAction\(raw: String, playerText: String\? = null, freeText: String\? = null\)/);
  assert.match(core,/put\("playerText", playerText\)/);
  assert.match(core,/if \(pending\.has\("playerText"\)\) put\("playerText", pending\.getString\("playerText"\)\)/);
  assert.match(view,/r\.optString\("playerText", r\.getString\("label"\)\)/);
  assert.match(java,/beginSceneAction\(command\.toString\(\), playerText\)/);
  assert.match(java,/totalActions = suggestedIndex > 0 \? 1 : -1/);
  assert.match(core,/PlayerActionPresentation\.receipt\(next, pending\)/);
  assert.doesNotMatch(java,/stopCode = "reading_pause"/);
});
test('Interrupted multi-action retry joins the existing paragraph after Core commits',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const direct=java.slice(java.indexOf('@JavascriptInterface public void submitSceneAction('),
    java.indexOf('@JavascriptInterface public void submitTurn('));
  assert.ok(direct.indexOf('commitSceneAction(')<direct.indexOf('recoverPlayerNarration(stateJson)'));
  assert.match(direct,/"RETRY"\.equals\(commandJson\)/);
  assert.match(direct,/playerPresentationContext/);
  assert.match(direct,/emit\("backroomTurn", committedView\.toString\(\)\)/);
});
test('World graph narration preserves scroll position instead of jumping down',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  assert.match(html,/const readingOffset=logEl\.scrollTop/);
  assert.match(html,/runtime==='world-graph-v3'\)logEl\.scrollTop=Math\.min\(readingOffset,logEl\.scrollHeight\)/);
  assert.match(java,/scene\.runtime===\\?'world-graph-v3\\?'\)return/);
});
test('long Player Action replans from fresh public state with committed history and bounded steps',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const worker=java.slice(java.indexOf('@JavascriptInterface public void submitPlayerAction('),
    java.indexOf('@JavascriptInterface public void submitSceneAction('));
  assert.match(worker,/while \(committed < 12 && \(totalActions < 0 \|\| committed < totalActions\)\)/);
  assert.match(worker,/PlayerActionPlan\.parse\(text, suggestedIndex > 0 \? 1 : totalActions, committed\)/);
  assert.match(worker,/if \("FREE"\.equals\(kind\)\)/);
  assert.match(worker,/narrativeEvents\.put\(free\.getJSONObject\("event"\)\)/);
  assert.match(worker,/narrativeEvents\.put\(new JSONObject\(\)\.put\("kind", "CORE"\)/);
  assert.match(worker,/if \(stopCode != null \|\| committed == totalActions\) break/);
  assert.match(worker,/plannerCalls >= \(totalActions > 0 \? totalActions : 12\)/);
  assert.match(worker,/CONFIRMED_STEPS=/);
  assert.match(worker,/new JSONObject\(requireGameCore\(\)\.scenePlayerActionContext\(\)\)/);
  assert.match(worker,/confirmedSteps\.put\(new JSONObject\(\)/);
  assert.ok(worker.indexOf('commitSceneAction(')<worker.lastIndexOf('confirmedSteps.put('));
  assert.match(worker,/if \(plan\.length\(\) == 0\)/);
  assert.match(worker,/stopCode = "step_limit"/);
});
test('Player Action reports one-based stopped step and Vietnamese reason without offering replay',()=>{
  const r=runtime();r.player.onclick();
  r.actionDialog.children[1].value='Đi qua hai phòng';r.actionDialog.children[1].oninput();
  r.actionDialog.children[2].onclick();
  r.ctx.backroomPlayerActionFinished(JSON.stringify({executed:1,requested:2,
    completed:false,stopCode:'invalid_route',stoppedStep:2,
    message:'Không có lối đi hợp lệ đến vị trí đã yêu cầu.'}));
  assert.match(r.status.textContent,/Đã xử lý 1 ý định/);
  assert.match(r.status.textContent,/Dừng tại bước 2/);
  assert.match(r.status.textContent,/Không có lối đi hợp lệ/);
  assert.equal(r.retry.hidden,true,'do not replay already committed moves');
  assert.equal(r.player.hidden,false);
});
test('pending GM failure retries saved Core command after partial Player Action commits',()=>{
  const r=runtime();r.player.onclick();
  r.actionDialog.children[1].value='Đi qua hai phòng';r.actionDialog.children[1].oninput();
  r.actionDialog.children[2].onclick();
  r.ctx.backroomTurn(JSON.stringify({...r.ctx.state,scene:{...r.ctx.state.scene,
    pendingActionId:'run:2:SEARCH:panel'}}));
  r.ctx.backroomPlayerActionError(JSON.stringify({committed:1,requested:2,
    stopCode:'narration_failed',stoppedStep:2,
    message:'Gehihi chưa hoàn tất lời kể. Bước đang chờ được giữ nguyên.'}));
  assert.match(r.status.textContent,/Dừng tại bước 2/);
  assert.equal(r.retry.hidden,false);
  r.retry.onclick();
  assert.deepEqual(r.calls.map(x=>x[0]),['player','action']);
  assert.equal(r.calls[1][1],'RETRY');
});
test('existing ornate frames and Gemini logo are used in Explorer controls',()=>{
  const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.css'),'utf8');
  assert.match(css,/--ornate-frame/);
  assert.match(css,/\.explorer-control-slot::after/);
  assert.match(css,/#explorerChoices::after/);
  assert.match(css,/\.explorer-control-slot::after\{[\s\S]*border-width:18px;border-image-width:18px/);
  assert.match(css,/pointer-events:none/);
  assert.match(source,/provider-logos\/gemini\.svg/);
  assert.match(source,/backroomSceneWriterProvider/);
});
test('waiting GM row shows animated Gemini 4.0 Argon logo below plain GAME MASTER header',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  assert.ok(html.includes("gm.innerHTML=\"<div class='role'>GAME MASTER</div>"));
  assert.ok(html.includes("<div class='gm-thinking' role='status' aria-label='Đang chờ phản hồi'><img src='provider-logos/gemini.svg' alt='' aria-hidden='true'><span class='gm-thinking-brand'>Gemini 4.0 Argon</span>"));
  assert.doesNotMatch(html,/gm-brand-spark/);
  assert.match(html,/\.gm-thinking img\{[^}]*animation:gm-thinking-pulse/);
  assert.match(html,/\.gm-thinking-brand\{color/);
  assert.match(html,/>BACKROOM TEXT GAME<\/div>/);
  assert.match(html,/>TURN <strong id="turn"/);
  assert.match(html,/id="gameMenuTitle">GAME MENU/);
});
test('obsolete evidence journal is hidden; Area Log only reads sanitized events',()=>{
  const js=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.js'),'utf8');
  assert.match(js,/journal\.hidden=true/);
  assert.match(js,/scene\?\.areaLog/);
  assert.doesNotMatch(js,/inference\.textContent=scene\.temporaryInference/);
  assert.doesNotMatch(js,/scene\.facts/);
});
test('Core-confirmed Lucia enables communication even without a scenario NPC topic',()=>{
  const r=runtime({availableActions:[{actionId:'r:0:8:TALK:character:lucia:introduce',sceneVersion:8,roomId:'hall',kind:'TALK',targetId:'character:lucia:introduce',label:'Lucia "Lục" · Giới thiệu và hỏi tên'}]});
  assert.equal(r.buttons[2].disabled,false);r.buttons[2].onclick();
  assert.ok(r.dialog.children[1].children[0].textContent.includes('Lucia'));
  r.dialog.children[1].children[0].onclick();
  const command=JSON.parse(r.calls[0][1]);assert.equal(command.kind,'TALK');assert.equal(command.targetId,'character:lucia:introduce');
});

test('world MAP only shows Core-confirmed marks, no secret names or generation',()=>{
  const r=runtime({runtime:'world-graph-v3',generationNeeded:false,map:{
    currentRoomId:'l0:r000',entryRoomId:'l0:r000',rooms:[
      {id:'l0:r000',name:'Vàng Úa',visited:true,marked:true,adjacentRoomIds:['l0:r009']},
      {id:'l0:r009',name:'DO NOT REVEAL',visited:false,marked:false,adjacentRoomIds:[]}
    ]}});
  r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const active=svg.children.find(el=>el.attributes?.['data-map-node']==='explored');
  const hidden=svg.children.find(el=>el.attributes?.['data-map-node']==='locked');
  assert.ok(active.children.some(el=>el.attributes?.class==='explorer-map-annotations'));
  assert.equal(hidden.children.some(n=>String(n.textContent||'').includes('DO NOT REVEAL')),false);
  assert.equal(r.calls.length,0);
});

test('FREE player actions use one real Core turn without invoking item UI or puzzle commands',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const worker=java.slice(java.indexOf('@JavascriptInterface public void submitPlayerAction('),
    java.indexOf('@JavascriptInterface public void submitSceneAction('));
  const free=worker.slice(worker.indexOf('if ("FREE".equals(kind))'),worker.indexOf('String targetId = planned.getString("targetId")'));
  assert.match(free,/executePlayerFreeStep\(detail, playerText, projection\)/);
  assert.match(free,/narrativeEvents\.put/);
  assert.match(free,/continue;/);
  assert.doesNotMatch(free,/beginSceneAction|commitSceneAction|processItemAction|processCombat/);
  assert.match(worker,/narrateMixedPlayerAction/);
  assert.match(worker,/finishPlayerPresentation/);
  assert.match(java,/được xuống đoạn để giữ nhịp kể/);
  assert.match(java,/Almond Water/);
});

test('eight Canon clusters route all 45 rooms, branches and cross-cluster loops without card crossings',()=>{
  const rooms=[],sizes=[4,4,3,3,4,3,4,4],connect=(a,b)=>{
    rooms.find(r=>r.id===a).adjacentRoomIds.push(b);rooms.find(r=>r.id===b).adjacentRoomIds.push(a);
  };
  sizes.forEach((size,g)=>{
    const ids=Array.from({length:size+2},(_,n)=>`g${g}r${n}`);
    ids.forEach((id,n)=>rooms.push({id,atlasGroup:`cluster:${g}`,atlasHub:n<2,
      atlasGroupName:g===0?'Vàng Úa':'',visited:g===0,name:g===0?'Vàng Úa · phòng '+n:'SECRET',adjacentRoomIds:[]}));
    const cycle=[ids[0],...ids.slice(2)];
    cycle.forEach((id,n)=>connect(id,cycle[(n+1)%cycle.length]));
    connect(ids[0],ids[1]);connect(ids[1],ids[2]);connect(ids[1],ids.at(-1));
  });
  sizes.forEach((_,g)=>{connect(`g${g}r0`,`g${(g+1)%8}r0`);connect(`g${g}r${sizes[g]+1}`,`g${(g+2)%8}r0`);});
  const r=runtime({map:{overviewRooms:rooms,overviewCurrentRoomId:'g0r0'}});r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const clusters=svg.children.filter(e=>e.attributes?.['data-map-cluster']);
  const nodes=svg.children.filter(e=>e.attributes?.['data-map-node']);
  const edges=svg.children.filter(e=>/^explorer-map-edge-/.test(e.attributes?.class||''));
  assert.equal(clusters.length,8);assert.equal(nodes.length,45);
  assert.equal(edges.length,rooms.reduce((sum,n)=>sum+n.adjacentRoomIds.length,0)/2,'every Core edge is rendered exactly once');
  const cards=nodes.map(n=>n.children[0].attributes).map(a=>({x:+a.x,y:+a.y,w:+a.width,h:+a.height}));
  for(let i=0;i<cards.length;i++)for(let j=i+1;j<cards.length;j++){
    const a=cards[i],b=cards[j];assert.ok(a.x+a.w<=b.x||b.x+b.w<=a.x||a.y+a.h<=b.y||b.y+b.h<=a.y,'no node overlaps');
  }
  edges.forEach(edge=>{
    const numbers=edge.attributes.d.match(/-?\d+(?:\.\d+)?/g).map(Number),pts=[];
    for(let i=0;i<numbers.length;i+=2)pts.push({x:numbers[i],y:numbers[i+1]});
    const endpoints=[pts[0],pts.at(-1)];
    for(let i=1;i<pts.length;i++){
      const a=pts[i-1],b=pts[i];assert.ok(a.x===b.x||a.y===b.y,'orthogonal segment');
      for(const c of cards){
        if(endpoints.some(p=>p.x>=c.x&&p.x<=c.x+c.w&&p.y>=c.y&&p.y<=c.y+c.h))continue;
        const crosses=a.x===b.x?a.x>c.x&&a.x<c.x+c.w&&Math.max(a.y,b.y)>c.y&&Math.min(a.y,b.y)<c.y+c.h:
          a.y>c.y&&a.y<c.y+c.h&&Math.max(a.x,b.x)>c.x&&Math.min(a.x,b.x)<c.x+c.w;
        assert.equal(crosses,false,'route avoids all unrelated room cards');
      }
    }
  });
  nodes[0].listeners.click();
  assert.ok(edges.some(e=>e.attributes.opacity==='1'));assert.ok(edges.some(e=>e.attributes.opacity==='.16'));
  assert.equal(r.calls.length,0);
});
test('Cao Minh pin and current zone recenter after a Core MOVE while MAP is open',()=>{
  const r=runtime({environment:{name:'Vàng Úa'},map:{currentRoomId:'a',rooms:[
    {id:'a',visited:true,name:'Vàng Úa',adjacentRoomIds:['b']},{id:'b',visited:true,name:'Trần Rỗng',adjacentRoomIds:['a']} ]}});
  r.buttons[0].onclick();
  let svg=r.dialog.children[1].children[0].children[0];
  const old=svg.children.flatMap(n=>n.children||[]).find(n=>n.attributes?.['data-map-location']).attributes.transform;
  r.ctx.backroomTurn(JSON.stringify({...r.ctx.state,scene:{...r.ctx.state.scene,environment:{name:'Trần Rỗng'},map:{...r.ctx.state.scene.map,currentRoomId:'b'}}}));
  svg=r.dialog.children[1].children[0].children[0];
  const now=svg.children.flatMap(n=>n.children||[]).find(n=>n.attributes?.['data-map-location']).attributes.transform;
  assert.notEqual(now,old);assert.equal(r.dialog.children[0].children[2].textContent,'Trần Rỗng');
});
test('writer recovery uses stored utterance endpoint and never resubmits a committed action',()=>{
  const r=runtime();
  r.ctx.backroomTurn(JSON.stringify({...r.ctx.state,playerAction:{id:'batch',status:'failed',recoverable:true}}));
  assert.equal(r.retry.hidden,false);assert.equal(r.player.hidden,true);
  r.retry.onclick();assert.equal(r.calls[0][0],'narration');
  assert.equal(r.calls.some(([type])=>type==='player'||type==='action'),false);
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  assert.match(html,/CORE · KẾT QUẢ ĐÃ XÁC NHẬN/);assert.match(html,/esc\(x.actionStatus\)/);
});

function mapZoomRuntime(){
  const r=runtime({map:{currentRoomId:'a',entryRoomId:'a',rooms:[
    {id:'a',name:'A',visited:true,adjacentRoomIds:['b','c']},
    {id:'b',name:'B',visited:true,adjacentRoomIds:['a','c']},
    {id:'c',name:'',visited:false,adjacentRoomIds:['a','b']}]}});
  r.buttons[0].onclick();
  const sc=r.dialog.children[1].children[0],svg=sc.children[0];
  const [out,label,into,locate]=r.dialog.querySelectorAll('.explorer-map-zoom')[0].children;
  const pointer=(kind,id,x,y,type='touch')=>sc.listeners[kind]({pointerId:id,clientX:x+10,clientY:y+20,pointerType:type,button:0});
  return {...r,sc,svg,out,label,into,locate,pointer};
}
test('MAP buttons zoom about viewport center, clamp scale, recenter and preserve graph/state',()=>{
  const r=mapZoomRuntime(),{sc,svg}=r,baseWidth=+svg.attributes.width,baseHeight=+svg.attributes.height;
  const before=JSON.stringify(r.ctx.state),topology=JSON.stringify(svg.children,(k,v)=>k==='parent'?undefined:v),viewBox=svg.attributes.viewBox;
  sc.scrollLeft=200;sc.scrollTop=300;r.into.onclick();
  assert.equal(r.label.textContent,'125%');assert.equal(+svg.attributes.width,baseWidth*1.25);
  assert.equal(+svg.attributes.height,baseHeight*1.25);
  assert.equal(sc.scrollLeft,295);assert.equal(sc.scrollTop,435);
  r.out.onclick();assert.equal(r.label.textContent,'100%');assert.equal(sc.scrollLeft,200);assert.equal(sc.scrollTop,300);
  for(let i=0;i<30;i++)r.out.onclick();assert.equal(r.label.textContent,'10%');assert.equal(r.out.disabled,true);
  for(let i=0;i<40;i++)r.into.onclick();assert.equal(r.label.textContent,'250%');assert.equal(r.into.disabled,true);
  sc.scrollLeft=999;sc.scrollTop=999;r.locate.onclick();
  const atMax={x:sc.scrollLeft,y:sc.scrollTop};r.out.onclick();r.locate.onclick();
  assert.ok(sc.scrollLeft<atMax.x||sc.scrollTop<atMax.y,'Cao Minh recenter follows current zoom');
  assert.equal(svg.attributes.viewBox,viewBox);assert.equal(JSON.stringify(svg.children,(k,v)=>k==='parent'?undefined:v),topology);
  assert.equal(JSON.stringify(r.ctx.state),before);assert.equal(r.calls.length,0);
});
test('MAP wheel zoom anchors under cursor and one-finger/mouse drag still pans',()=>{
  const r=mapZoomRuntime(),{sc,svg}=r,base=+svg.attributes.width;
  sc.scrollLeft=200;sc.scrollTop=300;let prevented=0;
  sc.listeners.wheel({clientX:110,clientY:140,deltaY:-100,deltaMode:0,preventDefault(){prevented++;}});
  const scale=+svg.attributes.width/base;
  assert.equal(prevented,1);assert.ok(scale>1);
  assert.ok(Math.abs((sc.scrollLeft+100)/scale-300)<.001);
  assert.ok(Math.abs((sc.scrollTop+120)/scale-420)<.001);
  for(const type of ['touch','mouse']){
    const left=sc.scrollLeft,top=sc.scrollTop;
    r.pointer('pointerdown',1,200,200,type);r.pointer('pointermove',1,160,180,type);
    assert.equal(sc.scrollLeft,left+40);assert.equal(sc.scrollTop,top+20);
    r.pointer('pointerup',1,160,180,type);
  }
  assert.equal(r.calls.length,0);
});
test('MAP pinch zoom preserves midpoint, allows continued single-finger pan and suppresses drag click',()=>{
  const r=mapZoomRuntime(),{sc,svg}=r,base=+svg.attributes.width;
  sc.scrollLeft=200;sc.scrollTop=300;
  r.pointer('pointerdown',1,100,100);r.pointer('pointerdown',2,200,100);
  r.pointer('pointermove',2,300,100);
  assert.equal(+svg.attributes.width/base,2);assert.equal(r.label.textContent,'200%');
  assert.equal(sc.scrollLeft,500);assert.equal(sc.scrollTop,700); // world midpoint (350,400), new midpoint (200,100)
  r.pointer('pointerup',2,300,100);r.pointer('pointermove',1,80,90);
  assert.equal(sc.scrollLeft,520);assert.equal(sc.scrollTop,710,'no jump on two-to-one finger transition');
  r.pointer('pointercancel',1,80,90);r.pointer('pointermove',1,10,10);
  assert.equal(sc.scrollLeft,520,'canceled finger cannot continue a stale drag');
  let prevented=0,stopped=0;
  const click={detail:1,preventDefault(){prevented++;},stopPropagation(){stopped++;}};
  sc.listeners.click(click);assert.equal(prevented,1);assert.equal(stopped,1);
  r.pointer('pointerdown',3,80,90);r.pointer('pointerup',3,80,90);sc.listeners.click(click);
  assert.equal(prevented,1,'an ordinary node tap is still delivered');assert.equal(r.calls.length,0);
});
