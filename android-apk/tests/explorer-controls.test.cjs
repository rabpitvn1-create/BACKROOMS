const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.js'),'utf8');
function runtime(scene={}){
  const elements={};
  function element(){return {children:[],listeners:{},disabled:false,hidden:false,
    append(...nodes){this.children.push(...nodes);},appendChild(node){this.children.push(node);},
    replaceChildren(...nodes){this.children=nodes;},setAttribute(){},addEventListener(k,fn){this.listeners[k]=fn;},
    showModal(){this.open=true;},close(){this.open=false;}};}
  elements.playerActionBar=element();elements.form=element();
  for(const id of ['sceneJournal','sceneJournalRoom','sceneJournalObjective','sceneJournalRecords'])elements[id]=element();
  const calls=[];
  const document={getElementById:id=>elements[id],createElement:element,body:element()};
  const ctx={document,state:{scene:{miniIndex:1,generationNeeded:false,availableActions:[{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x',label:'Phòng X'}],...scene}},busy:false,coreUnavailable:false,
    Android:{prepareScene:json=>calls.push(['prepare',json]),submitSceneAction:(json,c)=>calls.push(['action',c])},
    appendActionPending(){},backroomTurn(json){ctx.state=JSON.parse(json);ctx.busy=false;},backroomError(){ctx.busy=false;}};
  ctx.window=ctx;vm.runInNewContext(source,ctx);
  const panel=elements.playerActionBar.children[0],dialog=document.body.children[0];
  return {ctx,calls,panel,dialog,buttons:panel.children[0].children,retry:panel.children[1],journal:elements.sceneJournal,room:elements.sceneJournalRoom,objective:elements.sceneJournalObjective,records:elements.sceneJournalRecords};
}
test('three controls enable only Core-provided actions; popups do not submit or roll',()=>{
  const r=runtime();assert.deepEqual(r.buttons.map(b=>b.textContent),['Di chuyển','Tìm kiếm','Giao tiếp']);
  assert.equal(r.buttons[1].disabled,true);assert.equal(r.buttons[2].disabled,true);
  for(let i=0;i<4;i++){r.buttons[0].onclick();r.dialog.children[0].children[1].onclick();}
  assert.equal(r.calls.length,0);
  assert.equal(r.dialog.children[0].children[1].textContent,'×');
  assert.equal(r.dialog.children[0].children[1].open,undefined);
});

test('selection sends exactly one captured versioned target and no label/client state delta',()=>{
  const r=runtime();r.buttons[0].onclick();const select=r.dialog.children[1].children[0];select.onclick();select.onclick();
  assert.equal(r.calls.length,1);assert.deepEqual(JSON.parse(r.calls[0][1]),{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x'});
});
test('generation failure stops automatic requests and leaves explicit retry available',()=>{
  const r=runtime({generationNeeded:true,availableActions:[]});assert.equal(r.calls.length,1);
  r.ctx.backroomError('429');r.ctx.backroomSyncExplorer();assert.equal(r.calls.length,1);
  assert.equal(r.retry.hidden,false);r.retry.onclick();assert.equal(r.calls.length,2);
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
test('existing ornate frames and Gemini logo are used in Explorer controls',()=>{
  const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/explorer-controls.css'),'utf8');
  assert.match(css,/--ornate-frame/);
  assert.match(css,/\.explorer-buttons button::after/);
  assert.match(css,/#explorerChoices::after/);
  assert.match(source,/provider-logos\/gemini\.svg/);
  assert.match(source,/backroomSceneWriterProvider/);
});
test('Core-confirmed Lucia enables communication even without a scenario NPC topic',()=>{
  const r=runtime({availableActions:[{actionId:'r:0:8:TALK:character:lucia:introduce',sceneVersion:8,roomId:'hall',kind:'TALK',targetId:'character:lucia:introduce',label:'Lucia "Lục" · Giới thiệu và hỏi tên'}]});
  assert.equal(r.buttons[2].disabled,false);r.buttons[2].onclick();
  assert.ok(r.dialog.children[1].children[0].textContent.includes('Lucia'));
  r.dialog.children[1].children[0].onclick();
  const command=JSON.parse(r.calls[0][1]);assert.equal(command.kind,'TALK');assert.equal(command.targetId,'character:lucia:introduce');
});
