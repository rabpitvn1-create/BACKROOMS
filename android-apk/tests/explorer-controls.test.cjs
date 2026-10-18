const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.js'),'utf8');
function runtime(scene={}){
  const elements={};
  function element(){
    return {children:[],listeners:{},attributes:{},disabled:false,hidden:false,
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
      showModal(){this.open=true;},close(){this.open=false;}
    };
  }
  elements.playerActionBar=element();elements.form=element();elements.log=element();elements.status=element();
  for(const id of ['sceneJournal','sceneJournalRoom','sceneJournalObjective','sceneJournalRecords'])elements[id]=element();
  const calls=[];
  const document={getElementById:id=>elements[id],createElement:element,createElementNS:element,body:element()};
  const ctx={document,state:{scene:{miniIndex:1,generationNeeded:false,map:{currentRoomId:'hall',entryRoomId:'hall',rooms:[{id:'hall',name:'Hành lang',visited:true,adjacentRoomIds:['x']},{id:'x',name:'Chưa khám phá',visited:false,adjacentRoomIds:[]}]},areaLog:[],availableActions:[{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x',label:'Phòng X'}],...scene}},busy:false,coreUnavailable:false,
    Android:{prepareScene:json=>calls.push(['prepare',json]),submitSceneAction:(json,c)=>calls.push(['action',c]),submitPlayerAction:(json,text)=>calls.push(['player',text]),requestWorldHint:tier=>calls.push(['hint',tier])},
    appendActionPending(){},backroomTurn(json){ctx.state=JSON.parse(json);ctx.busy=false;},backroomError(){ctx.busy=false;}};
  ctx.window=ctx;vm.runInNewContext(source,ctx);
  const panel=elements.playerActionBar.children[0],dialog=document.body.children[0];
  return {ctx,calls,panel,dialog,actionDialog:document.body.children[1],buttons:panel.children[0].children.map(slot=>slot.children[0]),objectivePanel:panel.children[1],objectiveText:panel.children[1].children[1],player:panel.children[2].children[0],retry:panel.children[2].children[1],journal:elements.sceneJournal,room:elements.sceneJournalRoom,objective:elements.sceneJournalObjective,records:elements.sceneJournalRecords,log:elements.log,status:elements.status};
}
test('MAP, Area Log, Giao tiếp occupy the top row; Player Action and Retry share the bottom slot',()=>{
  const r=runtime();
  assert.deepEqual(r.buttons.map(b=>b.textContent),['MAP','Area Log','Giao tiếp']);
  assert.equal(r.buttons[2].disabled,true);
  assert.equal(r.panel.children[0].children.length,3);
  assert.equal(r.panel.children[2].children.length,3);
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
  const casebook=r.panel.children[2].children[2];
  assert.equal(r.calls.length,0);
  casebook.onclick();
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
test('Core action palette bypasses Gemini planning but still sends only an offered validated command',()=>{
  const r=runtime({runtime:'world-graph-v3',room:{id:'hall'},records:[],
    availableActions:[{actionId:'world:3:SEARCH:inspect',sceneVersion:3,
      roomId:'hall',kind:'SEARCH',targetId:'inspect',label:'Khảo sát cấu trúc hiện tại'}]});
  r.panel.children[2].children[2].onclick();
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
test('mini-map objective remains visible and updates without exposing hidden clues',()=>{
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
test('Player Action submits original natural text to the Core interpreter without forging choices',()=>{
  const r=runtime();r.player.onclick();assert.equal(r.actionDialog.open,true);
  const input=r.actionDialog.children[1],send=r.actionDialog.children[2];
  input.value='Cao Minh đi qua hành lang tới góc ẩm rồi gạt cầu dao 2-1-3-5-4-6';
  input.oninput();send.onclick();
  assert.equal(r.calls.length,1);assert.equal(r.calls[0][0],'player');
  assert.equal(r.calls[0][1],input.value);
  assert.equal(r.actionDialog.open,false);
});
test('MAP renders a highlighted current node and hides undiscovered room names',()=>{
  const r=runtime({map:{currentRoomId:'hall',entryRoomId:'hall',rooms:[
    {id:'hall',name:'Hành lang vàng',visited:true,adjacentRoomIds:['wet']},
    {id:'wet',name:'Chưa khám phá',visited:false,adjacentRoomIds:[]}
  ]}});r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  assert.equal(svg.attributes.role,'img');
  assert.equal(svg.children.filter(n=>n.attributes&&n.attributes.stroke==='#b3ffc6').length,0);
  const texts=svg.children.flatMap(g=>g.children||[]).map(el=>el.textContent||'');
  assert.ok(texts.some(t=>t.includes('● Hành lang vàng')));
  assert.ok(texts.some(t=>t.includes('Chưa khám phá')));
  assert.equal(r.calls.length,0);
});
test('MAP distinguishes physical marks from personal annotations without showing undiscovered changes',()=>{
  const r=runtime({map:{currentRoomId:'hall',entryRoomId:'hall',rooms:[
    {id:'hall',name:'Hành lang vàng',visited:true,marked:true,traced:true,adjacentRoomIds:['x']},
    {id:'x',name:'Chưa khám phá',visited:false,marked:false,traced:false,adjacentRoomIds:[]}
  ]}});
  r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const labels=svg.children.flatMap(n=>n.children||[]).map(n=>n.textContent||'');
  assert.ok(labels.some(n=>n.includes('◆ ✚ Hành lang vàng')));
  assert.ok(labels.some(n=>n==='Chưa khám phá'));
  assert.equal(r.calls.length,0);
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
test('Player Action runs Gehihi narration only after Core validates each MOVE/SEARCH step',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const helper=java.slice(java.indexOf('private String narrateStagedSceneAction('),
    java.indexOf('private JSONObject parseModelJson(',java.indexOf('private String narrateStagedSceneAction(')));
  const player=java.slice(java.indexOf('@JavascriptInterface public void submitPlayerAction('),
    java.indexOf('@JavascriptInterface public void submitSceneAction('));
  const direct=java.slice(java.indexOf('@JavascriptInterface public void submitSceneAction('),
    java.indexOf('@JavascriptInterface public void submitTurn('));
  assert.match(helper,/generateText\(sceneWriterPrompt\(\)\)/);
  assert.match(helper,/ProviderResponseContract\.requireWriter\(reply\)/);
  assert.match(helper,/canonBoundary\(sceneNode, reply\.getString\("reply"\)\)/);
  assert.match(player,/deviceStep \? staged\.getString\("result"\) : narrateStagedSceneAction\(staged\)/);
  assert.ok(player.indexOf('beginSceneAction(command.toString())') <
    player.indexOf('narrateStagedSceneAction(staged)'));
  assert.ok(player.indexOf('narrateStagedSceneAction(staged)') <
    player.indexOf('commitSceneAction('));
  assert.match(player,/emit\("backroomTurn", requireGameCore\(\)\.currentClientState\(projection\)\)/);
  assert.match(direct,/narrateStagedSceneAction\(stagedEvent\)/);
  assert.doesNotMatch(player,/if \(result\.isEmpty\(\)\) result = action\.getString\("label"\)/);
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
  assert.ok(facade.includes('if (deviceControl) "DEVICE" else if (sceneKind == "MOVE") "SCENE_MOVE" else "EXECUTE"'));
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
test('long Player Action replans from fresh public state with committed history and bounded steps',()=>{
  const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const worker=java.slice(java.indexOf('@JavascriptInterface public void submitPlayerAction('),
    java.indexOf('@JavascriptInterface public void submitSceneAction('));
  assert.match(worker,/while \(committed < 12\)/);
  assert.match(worker,/plannerCalls >= 4/);
  assert.match(worker,/CONFIRMED_STEPS=/);
  assert.match(worker,/new JSONObject\(requireGameCore\(\)\.scenePlayerActionContext\(\)\)/);
  assert.match(worker,/confirmedSteps\.put\(new JSONObject\(\)/);
  assert.ok(worker.indexOf('commitSceneAction(')<worker.indexOf('confirmedSteps.put('));
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
  assert.match(r.status.textContent,/Đã xác nhận 1 bước/);
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

test('world MAP displays Core-confirmed personal markers without triggering generation',()=>{
  const r=runtime({runtime:'world-graph-v2',generationNeeded:false,map:{
    currentRoomId:'l0:r000',entryRoomId:'l0:r000',rooms:[
      {id:'l0:r000',name:'Vàng Úa',visited:true,marked:true,adjacentRoomIds:['l0:r009']},
      {id:'l0:r009',name:'Chưa khám phá',visited:false,marked:false,adjacentRoomIds:[]}
    ]}});
  r.buttons[0].onclick();
  const svg=r.dialog.children[1].children[0].children[0];
  const texts=svg.children.flatMap(g=>g.children||[]).map(el=>el.textContent||'');
  assert.ok(texts.some(t=>t.includes('● ◆ Vàng Úa')));
  assert.ok(texts.some(t=>t==='Chưa khám phá'));
  assert.equal(r.calls.length,0);
});
