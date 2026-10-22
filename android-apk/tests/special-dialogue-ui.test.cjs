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
function finishModal(x) {
  for(let step=0;step<12&&!x.dialog.hidden;step++){
    const buttons=x.parts['#specialDialogueActions'].children;
    assert.ok(buttons.length,'dialogue should remain navigable');
    buttons.at(-1).click();
  }
  assert.equal(x.dialog.hidden,true,'dialogue must reach its final line');
}
function encounter(x,id,encounterId){
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.pendingEncounter={entityKey:id,encounterId};
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false,'Core encounter must open dialogue');
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
  assert.match(x.parts['#specialDialogueText'].textContent,/Cao gia/);
  finishModal(x);
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

test('R07 encounters have consecutive authored beats, repeat safely and never spawn on name mentions',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  assert.equal(x.dialog.hidden,true,'mere presence of catalog does not create Entity');
  encounter(x,'diep_minh','r07-diep-1');
  assert.match(x.parts['#specialDialogueText'].textContent,/Cao gia/);
  assert.equal(x.parts['#specialDialogueName'].textContent,'Diệp Minh');
  x.parts['#specialDialogueActions'].children[0].click();
  assert.equal(x.parts['#specialDialogueName'].textContent,'Cao Minh');
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh||0,0,
    'a partly read encounter cannot consume a chapter');
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,1);
  encounter(x,'diep_minh','r07-diep-2');
  assert.match(x.parts['#specialDialogueText'].textContent,/ở chốn này bao lâu/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,2);
  for(let n=3;n<=5;n++){encounter(x,'diep_minh','r07-diep-'+n);finishModal(x);}
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,5);
  encounter(x,'diep_minh','r07-diep-6');
  assert.match(x.parts['#specialDialogueText'].textContent,/Đường đi đổi hướng/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityRepeats.diep_minh,1);
  encounter(x,'diep_minh','r07-diep-7');
  assert.match(x.parts['#specialDialogueText'].textContent,/không nên sống sót/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityRepeats.diep_minh,2);
});
test('Lục Trầm is autonomous; both confirmed adversaries unlock one cross-encounter dialogue',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  encounter(x,'luc_tram_hac_hoa','r07-tram-1');
  assert.match(x.parts['#specialDialogueText'].textContent,/Ma khí trong kinh mạch/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.luc_tram_hac_hoa,1);
  for(let n=2;n<=5;n++){encounter(x,'luc_tram_hac_hoa','r07-tram-'+n);finishModal(x);}
  encounter(x,'luc_tram_hac_hoa','r07-tram-6');
  assert.match(x.parts['#specialDialogueText'].textContent,/Cùng một luồng ma khí/);
  finishModal(x);
  encounter(x,'diep_minh','r07-diep-cross-pre');
  finishModal(x);
  encounter(x,'luc_tram_hac_hoa','r07-tram-cross');
  assert.match(x.parts['#specialDialogueText'].textContent,/Diệp Minh đã đứng cạnh Vô Lượng/);
  assert.equal(x.ctx.state.specialDialogueProgress.entityCross.luc_tram_hac_hoa,undefined);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityCross.luc_tram_hac_hoa,1);
  encounter(x,'luc_tram_hac_hoa','r07-tram-after-cross');
  assert.match(x.parts['#specialDialogueText'].textContent,/Tại sao ngươi luôn gọi ta/);
  finishModal(x);
});
test('reload resumes the exact persisted encounter route and line before consuming progress',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[],
    pendingEncounter:{entityKey:'luc_tram_hac_hoa',encounterId:'persisted-r07'}}});
  x.parts['#specialDialogueActions'].children[0].click();
  const saved=JSON.parse(JSON.stringify(x.ctx.state));
  assert.equal(saved.specialDialogueProgress.pending.index,1);
  assert.equal(saved.specialDialogueProgress.pending.routeKind,'episode');
  assert.equal(saved.specialDialogueProgress.pending.routeIndex,0);
  const y=env({state:saved});
  assert.equal(y.dialog.hidden,false);
  assert.equal(y.parts['#specialDialogueName'].textContent,'Cao Minh');
  assert.equal(y.parts['#specialDialogueProgress'].textContent,'2 / 4');
  finishModal(y);
  assert.equal(y.ctx.state.specialDialogueProgress.entityBeats.luc_tram_hac_hoa,1);
  y.ctx.SpecialDialogue.offerEntity();
  assert.equal(y.dialog.hidden,true);
});
test('enemy route dialogue is observation and belief, never automatic disclosure of R07 writer secrets',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  const serialized=JSON.stringify(x.ctx.BackroomSpecialEntityDialogue);
  assert.doesNotMatch(serialized,/lớp trì lưu|tự rời đi|tự bỏ đi|ta hồi sinh nàng|chủ mưu bí mật/);
  assert.match(serialized,/Sinh Mệnh Đạo/);
  assert.match(serialized,/Vô Lượng/);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,undefined);
});

test('cross-route appears early after both actual encounters and does not skip individual episodes',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  encounter(x,'diep_minh','r07-early-d1');
  finishModal(x);
  encounter(x,'luc_tram_hac_hoa','r07-early-l1');
  finishModal(x);
  encounter(x,'diep_minh','r07-early-d2');
  assert.match(x.parts['#specialDialogueText'].textContent,/không phải người duy nhất/);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,1);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityCross.diep_minh,1);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,1,
    'cross encounter must not consume the independent lore episode');
  encounter(x,'diep_minh','r07-early-d3');
  assert.match(x.parts['#specialDialogueText'].textContent,/đã ở chốn này bao lâu/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,2);
});

test('R07 late encounters add six episodes and six varied repeat scenes per antagonist',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[],
    specialDialogueProgress:{introduced:{},chapters:{},shownEncounters:{},pending:null,
      entityBeats:{diep_minh:5},entityCross:{},entityRepeats:{},lastRollTurn:1}}});
  const catalog=x.ctx.BackroomSpecialEntityDialogue;
  for(const id of ['diep_minh','luc_tram_hac_hoa']){
    const entry=catalog[id];
    assert.equal(entry.episodes.length,11);
    assert.equal(entry.cross.length,3);
    assert.equal(entry.repeat.length,6);
    for(const lines of [...entry.episodes,...entry.cross,...entry.repeat]){
      assert.ok(lines.length>=3,'substantial original exchange');
      for(const line of lines){
        assert.ok(['cao_minh',id].includes(line.speaker),'only present antagonists and Cao Minh speak');
        assert.ok(typeof line.text==='string'&&line.text.length>20);
      }
    }
  }
  encounter(x,'diep_minh','r07-late-d6');
  assert.match(x.parts['#specialDialogueText'].textContent,/Ngươi có bao giờ nghĩ/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,6);
  for(let n=7;n<=11;n++){encounter(x,'diep_minh','r07-late-d'+n);finishModal(x);}
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,11);
  encounter(x,'diep_minh','r07-late-repeat-1');
  assert.match(x.parts['#specialDialogueText'].textContent,/Đường đi đổi hướng/);
  finishModal(x);
  x.ctx.state.specialDialogueProgress.entityRepeats.diep_minh=3;
  encounter(x,'diep_minh','r07-late-repeat-4');
  assert.match(x.parts['#specialDialogueText'].textContent,/Người khác sợ lạc đường/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityRepeats.diep_minh,4);
});
test('three cross-interludes are gated by confirmed history on both storylines',()=>{
  function seeded(diepBeat,lucBeat,crossCount){
    return env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[],
      specialDialogueProgress:{introduced:{},chapters:{},shownEncounters:{},pending:null,lastRollTurn:1,
        entityBeats:{diep_minh:diepBeat,luc_tram_hac_hoa:lucBeat},
        entityCross:{diep_minh:crossCount},entityRepeats:{}}}});
  }
  const insufficient=seeded(3,2,1);
  encounter(insufficient,'diep_minh','r07-cross-gate-insufficient');
  assert.match(insufficient.parts['#specialDialogueText'].textContent,/Một tông môn không nhất thiết/);
  finishModal(insufficient);
  assert.equal(insufficient.ctx.state.specialDialogueProgress.entityCross.diep_minh,1);
  const second=seeded(3,3,1);
  encounter(second,'diep_minh','r07-cross-two');
  assert.match(second.parts['#specialDialogueText'].textContent,/Ngươi gọi tên một thanh kiếm cũ/);
  finishModal(second);
  assert.equal(second.ctx.state.specialDialogueProgress.entityCross.diep_minh,2);
  encounter(second,'diep_minh','r07-resume-episode');
  assert.match(second.parts['#specialDialogueText'].textContent,/Một tông môn không nhất thiết/);
  finishModal(second);
  const third=seeded(6,6,2);
  encounter(third,'diep_minh','r07-cross-three');
  assert.match(third.parts['#specialDialogueText'].textContent,/Ngươi từng đứng cạnh Lục Trầm/);
  third.parts['#specialDialogueActions'].children[0].click();
  const persisted=JSON.parse(JSON.stringify(third.ctx.state));
  assert.equal(persisted.specialDialogueProgress.pending.routeKind,'cross');
  assert.equal(persisted.specialDialogueProgress.pending.routeIndex,2);
  const loaded=env({state:persisted});
  assert.equal(loaded.parts['#specialDialogueProgress'].textContent,'2 / 4');
  finishModal(loaded);
  assert.equal(loaded.ctx.state.specialDialogueProgress.entityCross.diep_minh,3);
  encounter(loaded,'diep_minh','r07-after-three-crosses');
  assert.match(loaded.parts['#specialDialogueText'].textContent,/Ngươi có bao giờ nghĩ/);
  finishModal(loaded);
  assert.equal(loaded.ctx.state.specialDialogueProgress.entityBeats.diep_minh,7);
});
test('Lục Trầm late encounters do not claim she remembers Cao Minh, is recruited, or is reconciled',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[],
    specialDialogueProgress:{introduced:{},chapters:{},shownEncounters:{},pending:null,lastRollTurn:1,
      entityBeats:{luc_tram_hac_hoa:10},entityCross:{},entityRepeats:{}}}});
  encounter(x,'luc_tram_hac_hoa','r07-late-l11');
  assert.match(x.parts['#specialDialogueText'].textContent,/Nếu những gì ngươi nói là thật/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.luc_tram_hac_hoa,11);
  assert.equal(x.ctx.state.party.length,0);
  encounter(x,'luc_tram_hac_hoa','r07-late-repeat-l1');
  assert.match(x.parts['#specialDialogueText'].textContent,/Cùng một luồng ma khí/);
  finishModal(x);
  x.ctx.state.specialDialogueProgress.entityRepeats.luc_tram_hac_hoa=3;
  encounter(x,'luc_tram_hac_hoa','r07-late-repeat-l4');
  assert.match(x.parts['#specialDialogueText'].textContent,/Khi nghe tên Cao Minh/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityRepeats.luc_tram_hac_hoa,4);
});
