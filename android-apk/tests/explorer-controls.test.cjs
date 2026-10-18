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
  const document={getElementById:id=>elements[id],createElement:element,body:element()};
  const ctx={document,state:{scene:{miniIndex:1,generationNeeded:false,availableActions:[{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x',label:'Phòng X'}],...scene}},busy:false,coreUnavailable:false,
    Android:{prepareScene:json=>calls.push(['prepare',json]),submitSceneAction:(json,c)=>calls.push(['action',c])},
    appendActionPending(){},backroomTurn(json){ctx.state=JSON.parse(json);ctx.busy=false;},backroomError(){ctx.busy=false;}};
  ctx.window=ctx;vm.runInNewContext(source,ctx);
  const panel=elements.playerActionBar.children[0],dialog=document.body.children[0];
  return {ctx,calls,panel,dialog,buttons:panel.children[0].children.map(slot=>slot.children[0]),retry:panel.children[1],journal:elements.sceneJournal,room:elements.sceneJournalRoom,objective:elements.sceneJournalObjective,records:elements.sceneJournalRecords,log:elements.log};
}
test('three controls enable only Core-provided actions; popups do not submit or roll',()=>{
  const r=runtime();assert.deepEqual(r.buttons.map(b=>b.textContent),['Di chuyển','Tìm kiếm','Giao tiếp']);
  assert.equal(r.buttons[1].disabled,true);assert.equal(r.buttons[2].disabled,true);
  assert.equal(r.panel.children[0].children.length,3);
  assert.ok(r.panel.children[0].children.every(slot=>slot.className==='explorer-control-slot'&&slot.children.length===1));
  for(let i=0;i<4;i++){r.buttons[0].onclick();r.dialog.children[0].children[1].onclick();}
  assert.equal(r.calls.length,0);
  assert.equal(r.dialog.children[0].children[1].textContent,'×');
  assert.equal(r.dialog.children[0].children[1].open,undefined);
});

test('selection sends exactly one captured versioned target and no label/client state delta',()=>{
  const r=runtime();r.buttons[0].onclick();const select=r.dialog.children[1].children[0];select.onclick();select.onclick();
  assert.equal(r.calls.length,1);assert.deepEqual(JSON.parse(r.calls[0][1]),{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x'});
});
test('device controls use one popup, preserve Core commands and stay open across inputs',()=>{
  const lever=(id,version)=>({actionId:'run:0:'+version+':SEARCH:'+id,sceneVersion:version,
    roomId:'panel',kind:'SEARCH',targetId:id,label:id==='up'?'Gạt cần lên':'Gạt cần xuống',mechanismId:'door_lock'});
  const r=runtime({availableActions:[lever('up',3),lever('down',3)]});
  r.buttons[1].onclick();
  assert.equal(r.dialog.open,true);
  assert.equal(r.dialog.children[1].children.length,1,'controls should be grouped in one puzzle entry');
  r.dialog.children[1].children[0].onclick();
  assert.equal(r.dialog.children[0].children[0].textContent,'Bảng điều khiển');
  assert.equal(r.dialog.children[1].children.length,3,'back button and two physical controls');
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
  assert.ok(java.includes('renderSnapshot=function(){if(window.backroomFreezeSnapshot)return;'));
  assert.ok(java.includes('if(!window.backroomFreezeSnapshot&&cinema&&previousScene)cinema.play'));
  assert.ok(java.includes('if(should&&!window.backroomFreezeSnapshot)requestSnapshot()'));
  assert.ok(runtimeSource.includes('if (deviceControl) "DEVICE"') === false,
    'device routing belongs to GameCoreFacade, not the scene model');
  const facade=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'),'utf8');
  assert.ok(facade.includes('if (deviceControl) "DEVICE" else "EXECUTE"'));
});
test('completed mini-map uses a distinct dark-green next-area choice with Core-authored label',()=>{
  const normal={actionId:'r:0:9:MOVE:room:lab',sceneVersion:9,roomId:'hall',kind:'MOVE',targetId:'room:lab',label:'Kho hồ sơ'};
  const transition={actionId:'r:0:9:MOVE:next',sceneVersion:9,roomId:'hall',kind:'MOVE',targetId:'next',label:'Tiến vào khu vực kế tiếp.'};
  const r=runtime({completed:true,availableActions:[normal,transition]});
  r.buttons[0].onclick();
  const choices=r.dialog.children[1],nextPanel=r.dialog.children[2];
  assert.equal(choices.children.length,1);
  assert.equal(choices.children[0].textContent,'Kho hồ sơ');
  assert.equal(nextPanel.hidden,false);
  assert.equal(nextPanel.children.length,1);
  const green=nextPanel.children[0];
  assert.equal(green.className,'explorer-next-area-option');
  assert.equal(green.textContent,'Tiến vào khu vực kế tiếp.');
  assert.doesNotMatch(green.textContent,/mini-map|10/);
  green.onclick();
  assert.deepEqual(JSON.parse(r.calls[0][1]),{
    actionId:transition.actionId,sceneVersion:9,roomId:'hall',kind:'MOVE',targetId:'next'
  });
  const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.css'),'utf8');
  assert.ok(css.includes('#explorerChoices .explorer-next-area-panel{'));
  assert.ok(css.includes('background:linear-gradient(150deg,#173d2b,#0a261b)'));
  assert.ok(css.includes('#explorerChoices .explorer-next-area-panel .explorer-next-area-option{'));
});
test('completion styling cannot create an unauthorized transition',()=>{
  const r=runtime({completed:true,availableActions:[{
    actionId:'r:0:9:MOVE:room:hall',sceneVersion:9,roomId:'lab',kind:'MOVE',targetId:'room:hall',label:'Hành lang'
  }]});
  r.buttons[0].onclick();
  assert.equal(r.dialog.children[2].hidden,true);
  assert.equal(r.dialog.children[2].children.length,0);
  assert.equal(r.dialog.children[1].children.length,1);
});
test('generation failure stops automatic requests and leaves explicit retry available',()=>{
  const r=runtime({generationNeeded:true,availableActions:[]});assert.equal(r.calls.length,1);
  r.ctx.backroomError('429');r.ctx.backroomSyncExplorer();assert.equal(r.calls.length,1);
  assert.equal(r.retry.hidden,false);r.retry.onclick();assert.equal(r.calls.length,2);
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
  const r=runtime({pendingActionId:'p',availableActions:[]});r.retry.onclick();assert.equal(r.calls[0][1],'RETRY');
  r.ctx.state.combat={active:true};r.ctx.busy=false;r.ctx.backroomSyncExplorer();assert.equal(r.panel.hidden,true);
});
test('untrusted labels and observations use textContent and no HTML insertion',()=>{
  assert.doesNotMatch(source,/innerHTML|insertAdjacentHTML|eval\(/);
});

test('room objective and observations live only in a separate Game Menu journal',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  const start=html.indexOf('id="gameMenuModal"');
  const end=html.indexOf('<script',start);
  const menu=html.slice(start,end);
  for(const id of ['sceneJournal','sceneJournalRoom','sceneJournalObjective','sceneJournalRecords'])assert.ok(menu.includes(`id="${id}"`),id);
  const r=runtime({room:{name:'Phòng X',description:'Dấu vết'},objective:'Giải khóa',records:[{text:'Vật chứng đã xác nhận'}]});
  assert.equal(r.panel.children.length,2);
  assert.ok(r.room.textContent.includes('Phòng X'));assert.ok(r.objective.textContent.includes('Giải khóa'));
  assert.equal(r.records.children[0].textContent,'Vật chứng đã xác nhận');
  assert.equal(r.journal.hidden,false);assert.equal(r.calls.length,0);
});
test('unresolved puzzle offers an in-menu evidence hint without revealing an answer',()=>{
  const r=runtime({room:{name:'Góc Thảm',description:'Thảm ẩm'},records:[],completed:false});
  assert.match(r.records.children[0].textContent,/Chưa có chứng cứ/);
  assert.match(r.records.children[0].textContent,/tìm kiếm/i);
  assert.doesNotMatch(r.records.children[0].textContent,/answer|factId|policy/);
  r.ctx.state.scene={...r.ctx.state.scene,completed:true,records:[{text:'Đối chiếu xong'}]};
  r.ctx.backroomSyncExplorer();
  assert.match(r.records.children[1].textContent,/Đã giải xong nhiệm vụ/);
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
