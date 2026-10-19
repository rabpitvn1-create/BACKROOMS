const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.join(__dirname,'../app/src/main');
const html=fs.readFileSync(path.join(root,'assets/index.html'),'utf8');
const prologueDeclaration=html.match(/const prologue=`[\s\S]*?`;/)[0];
const setup=html.slice(html.indexOf('function setupPrologue(){'),html.indexOf('\nconst prologueControls=setupPrologue();'));
const compact=html.slice(html.indexOf('function compactReaderContent('),html.indexOf('function syncClientFromCore('));

function reader(storage=new Map(),failStorage=false){
  const nodes={};let writes=0,focuses=0;
  for(const id of ['prologuePrompt','prologueOpenButton','prologueDismissButton','prologueReader',
    'prologueBody','prologueReaderClose','prologueReaderDone','gameMenuOpen']){
    nodes[id]={hidden:false,open:false,listeners:{},addEventListener(k,fn){this.listeners[k]=fn;},
      showModal(){this.open=true;},close(){this.open=false;this.listeners.close?.();},focus(){focuses++;}};
  }
  nodes.prologuePrompt.hidden=true;
  Object.defineProperty(nodes.prologueBody,'textContent',{set(v){writes++;this.text=v;},get(){return this.text;}});
  const ctx={document:{getElementById:id=>nodes[id]},localStorage:{
    getItem(k){if(failStorage)throw Error('storage');return storage.get(k)||null;},
    setItem(k,v){if(failStorage)throw Error('storage');storage.set(k,v);}
  }};
  vm.runInNewContext(prologueDeclaration+'\n'+setup+'\nthis.controls=setupPrologue();this.story=prologue;',ctx);
  return {nodes,ctx,storage,get writes(){return writes;},get focuses(){return focuses;}};
}
test('one-time PROLOGUE is a centered rounded gold button with an independent X and modal reader',()=>{
  assert.doesNotMatch(html,/<details\b[^>]*id="prologuePanel"/);
  assert.match(html,/<div id="prologuePrompt"[^>]* hidden>/);
  assert.match(html,/<button[^>]*id="prologueOpenButton"[^>]*>PROLOGUE<\/button>\s*<button[^>]*id="prologueDismissButton"/);
  assert.match(html,/#prologuePrompt\{[^}]*position:relative;[^}]*border-radius:999px;[^}]*background:linear-gradient/);
  assert.match(html,/#prologueOpenButton\{[^}]*width:100%;[^}]*min-height:44px;[^}]*text-align:center/);
  assert.match(html,/#prologuePrompt\[hidden\]\{display:none!important\}/);
  assert.match(html,/#prologueReader:not\(\[open\]\)\{display:none!important\}/);
  assert.match(html,/<dialog id="prologueReader"[^>]*aria-labelledby="prologueReaderTitle">/);
  const initial=html.slice(html.indexOf('const initial={'),html.indexOf('const byId='));
  const ctx={};vm.runInNewContext(prologueDeclaration+'\n'+initial+'\nthis.start=initial;this.story=prologue;',ctx);
  assert.ok(ctx.story.length>30000,'retain the full authored novel');
  assert.ok(JSON.stringify(ctx.start).length<2000);
  assert.equal(ctx.start.log.length,1);assert.match(ctx.start.log[0].text,/LƯỢT 1/);
  assert.equal(JSON.stringify(ctx.start).includes(ctx.story),false);
});
test('X consumes the New Game offer across renders, reload and checkpoint restore without loading prose',()=>{
  const r=reader(),state={turn:1,log:[{role:'gm',text:'LƯỢT 1'}]},before=JSON.stringify(state);
  r.ctx.controls.refresh(state);assert.equal(r.nodes.prologuePrompt.hidden,false);
  r.nodes.prologueDismissButton.onclick();assert.equal(r.nodes.prologuePrompt.hidden,true);
  r.ctx.controls.refresh({turn:2});r.ctx.controls.refresh(state);
  assert.equal(r.nodes.prologuePrompt.hidden,true);assert.equal(r.writes,0);
  assert.equal(r.nodes.prologueReader.open,false);assert.equal(JSON.stringify(state),before);
  const reload=reader(r.storage);reload.ctx.controls.refresh(state);
  assert.equal(reload.nodes.prologuePrompt.hidden,true,'even loading a turn-1 checkpoint cannot restore the offer');
  r.nodes.prologueOpenButton.onclick();assert.equal(r.nodes.prologueReader.open,false);
  r.ctx.controls.reset();assert.equal(r.nodes.prologuePrompt.hidden,false,'next New Game restores offer');
});
test('reading consumes the offer, retains full novel and closing or Escape never restores it',()=>{
  const r=reader();r.ctx.controls.refresh({turn:1});
  assert.equal(r.writes,0);r.nodes.prologueOpenButton.onclick();
  assert.equal(r.nodes.prologueReader.open,true);assert.equal(r.nodes.prologuePrompt.hidden,true);
  assert.equal(r.nodes.prologueBody.textContent,r.ctx.story);assert.equal(r.writes,1);
  r.nodes.prologueReaderClose.onclick();assert.equal(r.nodes.prologueReader.open,false);
  assert.equal(r.focuses,1);r.ctx.controls.refresh({turn:1});assert.equal(r.nodes.prologuePrompt.hidden,true);
  const reload=reader(r.storage);reload.ctx.controls.refresh({turn:1});assert.equal(reload.nodes.prologuePrompt.hidden,true);
  r.ctx.controls.reset();r.nodes.prologueOpenButton.onclick();r.nodes.prologueReaderDone.onclick();
  assert.equal(r.nodes.prologueReader.open,false);assert.equal(r.writes,1,'do not rebuild on subsequent New Games');
  r.ctx.controls.reset();r.nodes.prologueOpenButton.onclick();r.nodes.prologueReader.close(); // native Escape/back
  assert.equal(r.nodes.prologuePrompt.hidden,true);
});
test('pending offer survives boot, existing continuation gets no offer and unavailable storage still works per session',()=>{
  const r=reader();r.ctx.controls.refresh({turn:1});
  const reload=reader(r.storage);reload.ctx.controls.refresh({turn:2});assert.equal(reload.nodes.prologuePrompt.hidden,false);
  const continuation=reader();continuation.ctx.controls.refresh({turn:9});assert.equal(continuation.nodes.prologuePrompt.hidden,true);
  const unavailable=reader(new Map(),true);unavailable.ctx.controls.refresh({turn:1});
  assert.equal(unavailable.nodes.prologuePrompt.hidden,false);unavailable.nodes.prologueDismissButton.onclick();
  unavailable.ctx.controls.refresh({turn:1});assert.equal(unavailable.nodes.prologuePrompt.hidden,true);
});
test('only a successful Core reset reoffers PROLOGUE; checkpoint load does not reset its choice',()=>{
  const reset=html.slice(html.indexOf('function resetGame(){'),html.indexOf('function clearSave(){'));
  const load=html.slice(html.indexOf('function load(){'),html.indexOf('function armDestructive('));
  let offers=0,renders=0,menuCloses=0;
  const ctx={state:{turn:8},freshInitial:()=>({turn:1}),armDestructive:(k,l,fn)=>fn(),
    prologueControls:{reset(){offers++;}},document:{getElementById:()=>({click(){menuCloses++;}})},localStorage:{removeItem(){}},SNAPSHOT_KEY:'snapshot',
    statusEl:{},render(){renders++;},persistPresentation:()=>true};
  ctx.window=ctx;ctx.Android={resetNewGameCore:()=>JSON.stringify({ok:false,message:'Core failed'})};
  vm.runInNewContext(reset,ctx);ctx.resetGame();
  assert.equal(offers,0);assert.equal(ctx.state.turn,8);assert.equal(renders,0);assert.equal(menuCloses,0);
  ctx.Android.resetNewGameCore=()=>JSON.stringify({ok:true,data:{}});ctx.resetGame();
  assert.equal(offers,1);assert.equal(ctx.state.turn,1);assert.equal(renders,1);assert.equal(menuCloses,1);
  assert.doesNotMatch(load,/prologueControls.*reset/);
});
test('reader content is omitted from bridge/save presentation without dropping player history',()=>{
  const ctx={};vm.runInNewContext(prologueDeclaration+'\n'+compact+'\nthis.story=prologue;',ctx);
  const opening={role:'gm',text:'MỞ ĐẦU\n\n'+ctx.story};
  const player={role:'player',text:opening.text};
  const ordinary={role:'gm',text:'MỞ ĐẦU\n\nMột câu khác của GM.'};
  const s={turn:4,log:[opening,player,ordinary],scene:{room:{id:'current'}}};
  ctx.compactReaderContent(s);
  assert.deepEqual(s.log,[player,ordinary]);assert.equal(s.turn,4);assert.equal(s.scene.room.id,'current');
  assert.match(html,/function syncClientFromCore\(presentation\) \{\s*compactReaderContent\(presentation\)/);
});
test('both writer paths receive the compact historical anchor, never the reader novel',()=>{
  const java=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
  assert.equal((java.match(/OPENING_CONTEXT=/g)||[]).length,2);
  assert.equal((java.match(/GmNarrativeVoicePolicy\.openingContext\(\)/g)||[]).length,2);
  assert.doesNotMatch(java,/getString\("prologue"\)|BackroomPrologueText/);
  const staged=java.slice(java.indexOf('private String sceneWriterPrompt()'),java.indexOf('private String narrateStagedSceneAction('));
  const mixed=java.slice(java.indexOf('private String narrateMixedPlayerAction('),java.indexOf('/** Retry stored public events only.'));
  for(const writer of [staged,mixed]){
    assert.match(writer,/OPENING_CONTEXT=" \+ com\.rabpit\.backroom\.core\.knowledge\.GmNarrativeVoicePolicy\.openingContext\(\)\s*\+ "\\nCANON=/,
      'each narration request appends the full native context outside the clipped recent-history view');
    assert.doesNotMatch(writer,/openingContext\(\)\.(?:substring|take|slice)/);
  }
});
