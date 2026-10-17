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
  const calls=[];
  const document={getElementById:id=>elements[id],createElement:element,body:element()};
  const ctx={document,state:{scene:{miniIndex:1,generationNeeded:false,availableActions:[{actionId:'run:1:MOVE:room:x',sceneVersion:1,roomId:'hall',kind:'MOVE',targetId:'room:x',label:'Phòng X'}],...scene}},busy:false,coreUnavailable:false,
    Android:{prepareScene:json=>calls.push(['prepare',json]),submitSceneAction:(json,c)=>calls.push(['action',c])},
    appendActionPending(){},backroomTurn(json){ctx.state=JSON.parse(json);ctx.busy=false;},backroomError(){ctx.busy=false;}};
  ctx.window=ctx;vm.runInNewContext(source,ctx);
  const panel=elements.playerActionBar.children[0],dialog=document.body.children[0];
  return {ctx,calls,panel,dialog,buttons:panel.children[3].children,retry:panel.children[4]};
}
test('three controls enable only Core-provided actions; popups do not submit or roll',()=>{
  const r=runtime();assert.deepEqual(r.buttons.map(b=>b.textContent),['Di chuyển','Tìm kiếm','Giao tiếp']);
  assert.equal(r.buttons[1].disabled,true);assert.equal(r.buttons[2].disabled,true);
  for(let i=0;i<4;i++){r.buttons[0].onclick();r.dialog.children[0].onclick();}
  assert.equal(r.calls.length,0);
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
