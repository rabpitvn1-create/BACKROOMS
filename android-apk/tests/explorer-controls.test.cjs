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
  elements.playerActionBar=element();elements.form=element();elements.log=element();
  for(const id of ['sceneJournal','sceneJournalRoom','sceneJournalObjective','sceneJournalRecords'])elements[id]=element();
  const calls=[];
  const document={getElementById:id=>elements[id],createElement:element,createElementNS:element,body:element()};
  const ctx={document,state:{scene:{miniIndex:1,generationNeeded:false,map:{currentRoomId:'hall',entryRoomId:'hall',rooms:[{id:'hall',name:'Hành lang',visited:true,adjacentRoomIds:['x']},{id:'x',name:'Chưa khám phá',visited:false,adjacentRoomIds:[]}]},areaLog:[],availableActions:[{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x',label:'Phòng X'}],...scene}},busy:false,coreUnavailable:false,
    Android:{prepareScene:json=>calls.push(['prepare',json]),submitSceneAction:(json,c)=>calls.push(['action',c]),submitPlayerAction:(json,text)=>calls.push(['player',text])},
    appendActionPending(){},backroomTurn(json){ctx.state=JSON.parse(json);ctx.busy=false;},backroomError(){ctx.busy=false;}};
  ctx.window=ctx;vm.runInNewContext(source,ctx);
  const panel=elements.playerActionBar.children[0],dialog=document.body.children[0];
  return {ctx,calls,panel,dialog,actionDialog:document.body.children[1],buttons:panel.children[0].children.map(slot=>slot.children[0]),player:panel.children[1].children[0],retry:panel.children[1].children[1],journal:elements.sceneJournal,room:elements.sceneJournalRoom,objective:elements.sceneJournalObjective,records:elements.sceneJournalRecords,log:elements.log};
}
test('MAP, Area Log, Giao tiếp occupy the top row; Player Action and Retry share the bottom slot',()=>{
  const r=runtime();
  assert.deepEqual(r.buttons.map(b=>b.textContent),['MAP','Area Log','Giao tiếp']);
  assert.equal(r.buttons[2].disabled,true);
  assert.equal(r.panel.children[0].children.length,3);
  assert.equal(r.panel.children[1].children.length,2);
  assert.equal(r.player.textContent,'Player Action');
  assert.equal(r.player.hidden,false);assert.equal(r.retry.hidden,true);
  assert.equal(r.retry.className,'explorer-retry-danger');
  r.buttons[0].onclick();r.dialog.children[0].children[1].onclick();
  r.buttons[1].onclick();r.dialog.children[0].children[1].onclick();
  assert.equal(r.calls.length,0,'MAP and Area Log do not submit gameplay actions');
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
  assert.ok(facade.includes('if (deviceControl) "DEVICE" else "EXECUTE"'));
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
test('Player Action error preserves text and Retry replaces the bottom button without replaying committed steps',()=>{
  const r=runtime();r.player.onclick();
  const input=r.actionDialog.children[1];input.value='Đi tới góc ẩm';input.oninput();
  r.actionDialog.children[2].onclick();
  r.ctx.backroomPlayerActionError(JSON.stringify({committed:0,message:'Mạng lỗi'}));
  assert.equal(r.player.hidden,true);assert.equal(r.retry.hidden,false);
  assert.equal(input.value,'Đi tới góc ẩm');
  r.retry.onclick();assert.equal(r.calls.length,2);
  assert.equal(r.calls[1][0],'player');
  const partial=runtime();partial.player.onclick();
  partial.actionDialog.children[1].value='Đi qua hai phòng';
  partial.actionDialog.children[1].oninput();partial.actionDialog.children[2].onclick();
  partial.ctx.backroomPlayerActionError(JSON.stringify({committed:1,message:'Đã dừng'}));
  assert.equal(partial.player.hidden,false,'partial commits must not expose replay-from-start Retry');
  assert.equal(partial.calls.length,1);
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
test('exploration journal shows confirmed records and separate Core-only provisional inference',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  const js=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.js'),'utf8');
  assert.match(html,/id="sceneJournalRecords"/);
  assert.match(html,/Suy luận tạm thời/);
  assert.match(html,/id="sceneJournalInference"/);
  assert.match(js,/inference\.textContent=scene\.temporaryInference/);
  assert.doesNotMatch(js,/scene\.facts/);
});
test('Core-confirmed Lucia enables communication even without a scenario NPC topic',()=>{
  const r=runtime({availableActions:[{actionId:'r:0:8:TALK:character:lucia:introduce',sceneVersion:8,roomId:'hall',kind:'TALK',targetId:'character:lucia:introduce',label:'Lucia "Lục" · Giới thiệu và hỏi tên'}]});
  assert.equal(r.buttons[2].disabled,false);r.buttons[2].onclick();
  assert.ok(r.dialog.children[1].children[0].textContent.includes('Lucia'));
  r.dialog.children[1].children[0].onclick();
  const command=JSON.parse(r.calls[0][1]);assert.equal(command.kind,'TALK');assert.equal(command.targetId,'character:lucia:introduce');
});
