const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/battle-start.js'),'utf8');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
function element(){return {children:[],style:{},dataset:{},appendChild(x){this.children.push(x)},addEventListener(t,f){this.click=f},set textContent(t){this.text=t;this.children=[]},get textContent(){return this.text}}}
function runtime(characters=[]){
 const box=element(),calls=[];
 const ctx={state:{turn:4,log:[{role:'gm',text:'Lucia xuất hiện trong lời kể.'}],encounteredCharacters:characters},busy:false,statusEl:{},
  document:{getElementById:id=>id==='encounteredCharacters'?box:null,createElement:element},
  syncPrimaryActions(){},Android:{recruitFollower(json,id){calls.push({view:JSON.parse(json),id})}},
  backroomTurn(json){ctx.state=JSON.parse(json);ctx.backroomRenderEncounteredCharacters()}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(source,ctx);return {ctx,box,calls};
}
const lucia={id:'lucia',name:'Lucia "Lục"',present:true,joinEligible:true,inParty:false};
test('prose alone creates no contact; a Core meeting displays the registered name without auto-join',()=>{
 const absent=runtime();assert.equal(absent.box.children.length,0);assert.equal(absent.calls.length,0);
 const met=runtime([lucia]);assert.equal(met.box.children[0].children[0].textContent,'Đã gặp: Lucia "Lục"');
 assert.equal(met.box.children[0].children[1].textContent,'Mời vào Party');assert.equal(met.calls.length,0);
 assert.match(html,/id="encounteredCharacters"/);
});
test('an explicit invitation sends the Core ID once and updates Party only after confirmation',()=>{
 const r=runtime([lucia]),button=r.box.children[0].children[1];button.click();button.click();
 assert.equal(r.calls.length,1);assert.equal(r.calls[0].id,'lucia');assert.equal(r.calls[0].view.turn,4);
 r.ctx.backroomRecruitment(JSON.stringify({handled:true,reply:'Đã gia nhập.',state:{turn:4,encounteredCharacters:[{...lucia,inParty:true}]}}));
 assert.equal(r.box.children[0].children.length,1);assert.match(r.box.children[0].children[0].textContent,/Trong Party/);
});
test('NPC contacts cannot auto-recruit and pending Combat blocks invitations',()=>{
 const npc=runtime([{...lucia,id:'npc',name:'NPC',joinEligible:false}]);assert.equal(npc.box.children[0].children.length,1);
 const r=runtime([lucia]);r.ctx.state.pendingEncounter={encounterId:'HOUND'};r.ctx.backroomRenderEncounteredCharacters();
 const button=r.box.children[0].children[1];assert.equal(button.disabled,true);button.click();assert.equal(r.calls.length,0);
});
test('a Party capacity rejection keeps the saved contact and restores its invitation button',()=>{
 const r=runtime([lucia]);r.box.children[0].children[1].click();
 r.ctx.backroomRecruitment('{"handled":false,"error":"party_full"}');
 assert.equal(r.box.children[0].children[1].disabled,false);assert.equal(r.ctx.state.encounteredCharacters[0].inParty,false);
 assert.equal(r.ctx.statusEl.textContent,'Party đã đủ thành viên.');
});
test('GM prompt has no milestone due events and recruitment uses a separate native endpoint',()=>{
 // Milestone system removed: writerPrompt must not reference due events or milestone.
 assert.doesNotMatch(java,/milestoneDueEvents|MilestoneNarrationValidator|milestoneWaiting/);
 assert.match(java,/Bạn là GM Gehihi, kể văn xuôi tiếng Việt/);
 assert.match(java,/@JavascriptInterface public void recruitFollower\(String stateJson, String characterId\)/);
});

test('Explorer uses typed commands and does not execute the retired exit-streak pipeline',()=>{
 assert.match(java,/beginSceneAction\(command\)/);
 assert.match(java,/commitSceneAction/);
 assert.doesNotMatch(java,/makeGameplayRolls|determineExitDestination|HiddenExitStreak|beginAction\(currentView/);
 const spawner=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/EntitySpawnPool.kt'),'utf8');
 assert.match(spawner,/fun rollSceneSpawns/);
 assert.match(spawner,/if \(kind !in setOf\("MOVE", "SEARCH", "STEP", "FREE", "INTERACT", "TALK"\)\)/);
});
