const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assets=path.join(__dirname,'../app/src/main/assets');
const scripts=['special-dialogue-data.js','special-dialogue.js'].map(name=>fs.readFileSync(path.join(assets,name),'utf8'));
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const explorer=fs.readFileSync(path.join(assets,'explorer-controls.js'),'utf8');
const core=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/CoreClientProjection.kt'),'utf8');
const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
function element(){
  const e={
    id:'',children:[],dataset:{},hidden:false,disabled:false,style:{},attributes:{},
    classList:{add(){},remove(){},contains(){return false}},
    addEventListener(name,fn){this.listeners=this.listeners||{};this.listeners[name]=fn;},
    appendChild(c){this.children.push(c);return c;},
    replaceChildren(...c){this.children=c;},
    setAttribute(k,v){this.attributes[k]=v;},
    getAttribute(k){return this.attributes[k]},
    querySelector(s){return this.nodes&&this.nodes[s]||null;},
    querySelectorAll(s){return s==='button'?this.children.filter(c=>c.tag==='button'):[];},
    click(){if(!this.disabled&&this.listeners&&this.listeners.click)this.listeners.click();},
    set textContent(v){this._text=String(v);this.children=[];},
    get textContent(){return this._text||'';},
    set innerHTML(v){this._html=String(v);if(this.id==='specialDialogue'){
      this.nodes=Object.fromEntries(['specialDialogueName','specialDialogueText',
        'specialDialogueAvatar','specialDialogueActions','specialDialogueProgress'].map(k=>['#'+k,element()]));
    }},
    get innerHTML(){return this._html||''}
  };
  return e;
}
function env(options={}){
  const root=element(),body=element(),classes=new Set();
  body.classList={add:s=>classes.add(s),remove:s=>classes.delete(s),contains:s=>classes.has(s)};
  const document={body,createElement(name){const e=element();e.tag=name;return e;},getElementById(){return null;}};
  document.body.appendChild=(n)=>{if(n.id==='specialDialogue')root.nodes=n.nodes;document.dialog=n;return n;};
  const character={id:'lucia',name:'Lucia "Lục"',avatar:'avatars/lucia_avatar.jpg',presence:'ACTIVE'};
  const state=Object.assign({turn:4,scene:{availableActions:[]},party:[character],
    partyDetails:{members:[character]},encounteredCharacters:[
      {id:'lucia',name:character.name,avatar:character.avatar,present:true,joinEligible:true,inParty:true}],
    log:[]},options.state||{});
  const ctx={document,state,window:null,Math:Object.assign(Object.create(Math),{random:()=>0.01}),
    persistPresentation(){ctx.saved=JSON.parse(JSON.stringify(ctx.state));},
    render(){ctx.rendered=true;},
    backroomTurn(json){ctx.state=JSON.parse(json);},
    backroomPlayerActionFinished(raw){ctx.finished=JSON.parse(raw);},
    Android:{requestDialogueHint(json,id){ctx.hintId=id},recruitFollower(){ctx.invited=true}}};
  ctx.window=ctx;
  vm.createContext(ctx);scripts.forEach(s=>vm.runInContext(s,ctx));
  return {ctx,dialog:document.dialog,parts:document.dialog.nodes,classes};
}
test('script and stylesheet are mounted without altering the CRT or the GM panel',()=>{
  assert.match(index,/special-dialogue\.css/);
  assert.match(index,/special-dialogue-data\.js/);
  assert.match(index,/special-dialogue\.js/);
  const css=fs.readFileSync(path.join(assets,'special-dialogue.css'),'utf8');
  assert.match(css,/#specialDialogue\{position:fixed;inset:0;z-index:600;display:grid;place-items:center/);
  assert.match(css,/body\.special-dialogue-active \.game>\.log\{filter:blur/);
  assert.doesNotMatch(css,/\.topbar\{|#snapshot\{/);
});
test('normal Follower talks offline using the same Party portrait and never invokes AI',()=>{
  const x=env();
  assert.equal(x.ctx.SpecialDialogue.openCharacter('lucia','talk'),true);
  assert.equal(x.dialog.hidden,false);
  assert.equal(x.parts['#specialDialogueName'].textContent,'Lucia "Lục"');
  assert.equal(x.parts['#specialDialogueAvatar'].children[0].src,'avatars/lucia_avatar.jpg');
  assert.equal(x.ctx.hintId,undefined);
  x.parts['#specialDialogueActions'].children.at(-1).click();
  assert.equal(x.dialog.hidden,true);
  assert.equal(x.ctx.state.specialDialogueProgress.introduced.lucia,true);
  assert.equal(x.ctx.SpecialDialogue.openCharacter('lucia','talk'),true);
  assert.equal(x.ctx.hintId,undefined);
});
test('NPC dialogue typed into Player Action is rejected without consuming a turn',()=>{
  const x=env(),before=x.ctx.state.turn;
  assert.equal(x.ctx.SpecialDialogue.rejectPlayerAction('Cao Minh quay qua hỏi Lucia hôm nay ăn gì chưa.'),true);
  assert.match(x.ctx.state.log.at(-1).text,/\[Giao Tiếp\]/);
  assert.equal(x.ctx.state.turn,before);
  x.ctx.backroomDialogueInvalidAction('HÀNH ĐỘNG KHÔNG HỢP LỆ! Vui lòng tương tác qua [Giao Tiếp].');
  assert.equal(x.ctx.finished.completed,true,'native rejection must unlock Explorer instead of leaving it busy');
  assert.equal(x.ctx.finished.executed,0);
  assert.equal(x.ctx.SpecialDialogue.rejectPlayerAction('Cao Minh cùng Lucia kiểm tra cánh cửa'),false);
  assert.equal(x.ctx.state.turn,before);
  assert.match(explorer,/rejectPlayerAction/);
  assert.match(native,/directNpcConversation\(playerText, initialView\)/);
});
test('Core-authored scripted TALK stays out of the GM narrative log',()=>{
  assert.match(core,/specialDialogueTalk/);
  assert.match(native,/requestDialogueTalk/);
  assert.match(native,/requestDialogueHint/);
  assert.match(native,/publicFacts\.put\("room"/);
});
test('Entity portraits reuse cropped encounter overlays and never need new assets',()=>{
  const x=env({state:{pendingEncounter:{entityKey:'diep_minh',encounterId:'entity-1'},
    party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  assert.equal(x.dialog.hidden,false);
  assert.equal(x.parts['#specialDialogueAvatar'].children[0].src,'entity/diep_minh.webp');
  assert.equal(x.parts['#specialDialogueAvatar'].children[0].className,'special-dialogue-entity');
  const buttons=x.parts['#specialDialogueActions'];
  buttons.children[0].click();
  buttons.children.at(-1).click();
  assert.equal(x.ctx.state.specialDialogueProgress.shownEncounters['entity-1'],true);
  x.ctx.SpecialDialogue.offerEntity();
  assert.equal(x.dialog.hidden,true);
});
test('offline story trigger consumes the next unheard chapter only after a committed exploration turn',()=>{
  const x=env({state:{turn:4,party:[{id:'hua_tieu_lan',name:'Hứa Tiểu Lan',presence:'ACTIVE'}],
    encounteredCharacters:[{id:'hua_tieu_lan',name:'Hứa Tiểu Lan',present:true,inParty:true}],
    partyDetails:{members:[{id:'hua_tieu_lan',name:'Hứa Tiểu Lan',avatar:'avatars/HuaTieuLan_avatar.webp'}]},
    specialDialogueProgress:{introduced:{hua_tieu_lan:true},chapters:{},lastRollTurn:4,shownEncounters:{},pending:null}}});
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.turn=5;next.log=[{role:'player',text:'Di chuyển qua hành lang'}];
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false);
  assert.match(x.parts['#specialDialogueText'].textContent,/cay nghiệt/);
  x.parts['#specialDialogueActions'].children.at(-1).click();
  assert.equal(x.ctx.state.specialDialogueProgress.chapters.hua_tieu_lan,1);
  x.ctx.SpecialDialogue.offerEntity();
  assert.equal(x.dialog.hidden,true);
});

function finishDialogue(x){
  const lines=[];
  for(let i=0;!x.dialog.hidden&&i<20;i++){
    lines.push({speaker:x.parts['#specialDialogueName'].textContent,text:x.parts['#specialDialogueText'].textContent});
    x.parts['#specialDialogueActions'].children.at(-1).click();
  }
  assert.equal(x.dialog.hidden,true,'dialogue must finish without restarting');
  return lines;
}
const darkTram={entityKey:'luc_tram_hac_hoa',encounterId:'tram-1'};
test('Lục Trầm confrontation and R07 story open for pending and auto-confirmed Core encounters',()=>{
  for(const encounter of [{pendingEncounter:darkTram},{combat:{...darkTram,active:true,waveNumber:1}}]){
    const x=env({state:encounter});
    assert.equal(x.dialog.hidden,false);
    assert.equal(x.parts['#specialDialogueName'].textContent,'Cao Minh');
    assert.equal(x.parts['#specialDialogueAvatar'].children[0].src,'avatars/CAO_MINH_AVATAR.jpg');
    x.parts['#specialDialogueActions'].children[0].click();
    assert.equal(x.parts['#specialDialogueAvatar'].children[0].src,'entity/luc_tram_hac_hoa.webp');
    const heard=finishDialogue(x).map(line=>line.text).join('\n');
    assert.match(heard,/Diệp Minh nói/);
    assert.match(heard,/Ta chưa từng tin hết lời Diệp Minh/);
    assert.match(heard,/Ta cứu nàng/);
    assert.equal(x.ctx.state.specialDialogueProgress.shownEncounters['tram-1'],true);
    assert.equal(x.ctx.invited,undefined,'enemy dialogue must not recruit the Follower');
    assert.equal(x.ctx.hintId,undefined,'authored encounter must not depend on a GM request');
    x.ctx.render();
    assert.equal(x.dialog.hidden,true);
  }
});
test('Core state delivered through render opens the encounter and confirmation does not repeat it',()=>{
  const x=env();
  x.ctx.state.pendingEncounter=darkTram;x.ctx.render();
  assert.equal(x.dialog.hidden,false);
  finishDialogue(x);
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  delete next.pendingEncounter;
  next.combat={...darkTram,active:true,waveNumber:1,entityInstanceId:'tram-1:wave:0'};
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,true);
  next.combat={...next.combat,encounterId:'tram-2',entityInstanceId:'tram-2:wave:0'};
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false,'a new Core encounter must still open');
});
test('saved entity story resumes at its current line and stale encounters are discarded',()=>{
  const x=env({state:{pendingEncounter:darkTram}});
  for(let i=0;i<3;i++)x.parts['#specialDialogueActions'].children[0].click();
  const saved=JSON.parse(JSON.stringify(x.ctx.state));
  const resumed=env({state:saved});
  assert.equal(resumed.parts['#specialDialogueText'].textContent,x.parts['#specialDialogueText'].textContent);
  const resumedAgain=env({state:JSON.parse(JSON.stringify(resumed.ctx.saved))});
  assert.equal(resumedAgain.parts['#specialDialogueText'].textContent,x.parts['#specialDialogueText'].textContent);
  delete saved.pendingEncounter;
  saved.combat={active:true,entityKey:'hound',encounterId:'other'};
  const stale=env({state:saved});
  assert.equal(stale.dialog.hidden,true);
  assert.equal(stale.ctx.state.specialDialogueProgress.pending,null);
  assert.equal(stale.ctx.state.specialDialogueProgress.shownEncounters['tram-1'],undefined);
});
test('an entity encounter waits for a character modal and later combat waves get their own dialogue',()=>{
  const x=env();x.ctx.SpecialDialogue.openCharacter('lucia','talk');
  x.ctx.state.pendingEncounter=darkTram;x.ctx.render();
  assert.equal(x.parts['#specialDialogueName'].textContent,'Lucia "Lục"');
  x.parts['#specialDialogueActions'].children.at(-1).click();
  assert.equal(x.parts['#specialDialogueName'].textContent,'Cao Minh');
  finishDialogue(x);
  delete x.ctx.state.pendingEncounter;
  x.ctx.state.combat={...darkTram,active:true,waveNumber:2,entityInstanceId:'tram-1:wave:1'};
  x.ctx.render();
  assert.equal(x.dialog.hidden,false);
  finishDialogue(x);
  assert.equal(x.ctx.state.specialDialogueProgress.shownEncounters['tram-1:wave:1'],true);
});
test('narrative mentions cannot spawn dialogue and a departing entity cannot leave its modal open',()=>{
  const x=env({state:{log:[{role:'gm',text:'Lục Trầm Hắc Hóa xuất hiện.'}]}});
  assert.equal(x.dialog.hidden,true);
  x.ctx.state.pendingEncounter=darkTram;x.ctx.render();
  assert.equal(x.dialog.hidden,false);
  delete x.ctx.state.pendingEncounter;x.ctx.render();
  assert.equal(x.dialog.hidden,true);
  assert.equal(x.ctx.state.specialDialogueProgress.shownEncounters['tram-1'],undefined);
});
