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
  // Core-confirmed Lucia already in Party but without her authored first-contact
  // story must receive that story before ambient dialogue can be opened.
  assert.equal(x.dialog.hidden,false);
  assert.match(x.parts['#specialDialogueText'].textContent,/Tôi chưa biết anh là ai/);
  finishModal(x);
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
  assert.match(x.parts['#specialDialogueText'].textContent,/Có một câu tôi đã nói/);
  finishModal(x);
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
  for(let n=6;n<=11;n++){encounter(x,'diep_minh','r07-diep-'+n);finishModal(x);}
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.diep_minh,11);
  encounter(x,'diep_minh','r07-diep-repeat-1');
  assert.match(x.parts['#specialDialogueText'].textContent,/Đường đi đổi hướng/);
  finishModal(x);
  assert.equal(x.ctx.state.specialDialogueProgress.entityRepeats.diep_minh,1);
  encounter(x,'diep_minh','r07-diep-repeat-2');
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
  for(let n=2;n<=11;n++){encounter(x,'luc_tram_hac_hoa','r07-tram-'+n);finishModal(x);}
  assert.equal(x.ctx.state.specialDialogueProgress.entityBeats.luc_tram_hac_hoa,11);
  encounter(x,'luc_tram_hac_hoa','r07-tram-repeat-1');
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
  assert.equal(y.parts['#specialDialogueProgress'].textContent,'2 / 5');
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
        assert.ok(typeof line.text==='string'&&line.text.trim().length>0);
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
  assert.match(second.parts['#specialDialogueText'].textContent,/Ngươi gọi tên nàng/);
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
  assert.match(loaded.parts['#specialDialogueText'].textContent,/Ngươi từng dùng kiếm ý/);
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

test('Lucia Lục Core-confirmed first encounter opens authored popup and never auto-recruits',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  assert.equal(x.dialog.hidden,true);
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.turn=5;
  next.encounteredCharacters=[{id:'lucia',name:'Lucia "Lục"',
    avatar:'avatars/lucia_avatar.jpg',present:true,inParty:false,joinEligible:true}];
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false,'committed first meeting must open popup automatically');
  assert.match(x.parts['#specialDialogueText'].textContent,/Tôi chưa biết anh là ai/);
  assert.equal(x.parts['#specialDialogueProgress'].textContent,'1 / 10');
  assert.equal(x.parts['#specialDialogueAvatar'].children[0].src,'avatars/lucia_avatar.jpg');
  assert.equal(x.ctx.state.specialDialogueProgress.introduced.lucia,undefined);
  assert.equal(x.ctx.invited,undefined);
  for(let i=0;i<9;i++)x.parts['#specialDialogueActions'].children[0].click();
  const names=x.parts['#specialDialogueActions'].children.map(b=>b.textContent);
  assert.ok(names.includes('Mời vào Party'));
  assert.equal(x.ctx.invited,undefined,'invitation remains the player choice');
  x.parts['#specialDialogueActions'].children.at(-1).click();
  assert.equal(x.dialog.hidden,true);
  assert.equal(x.ctx.state.specialDialogueProgress.introduced.lucia,true);
  assert.equal(x.ctx.state.specialDialogueProgress.introRevisions.lucia,'lucia-intro-r01');
  assert.equal(x.ctx.state.party.length,0,'intro must not change authoritative Party');
  x.ctx.SpecialDialogue.offerEntity();
  assert.equal(x.dialog.hidden,true,'finished intro must not reopen on render');
});
test('Lucia first meeting is recovered after app launch, old generic greeting and a missed roster diff',()=>{
  const lucia={id:'lucia',name:'Lucia "Lục"',
    avatar:'avatars/lucia_avatar.jpg',present:true,inParty:false,joinEligible:true};
  const old=env({state:{party:[],partyDetails:{members:[]},
    encounteredCharacters:[lucia],
    specialDialogueProgress:{introduced:{lucia:true},chapters:{},
      shownEncounters:{},pending:null,lastRollTurn:4}}});
  assert.equal(old.dialog.hidden,false,'old generic greeting must not silently consume authored story');
  assert.match(old.parts['#specialDialogueText'].textContent,/Tôi chưa biết anh là ai/);
  finishModal(old);
  const restored=JSON.parse(JSON.stringify(old.ctx.state));
  const reloaded=env({state:restored});
  assert.equal(reloaded.dialog.hidden,true,'read Lucia intro must not be replayed after load');
  const unpresent=env({state:{party:[],partyDetails:{members:[]},
    encounteredCharacters:[{...lucia,present:false}]}});
  assert.equal(unpresent.dialog.hidden,true,'historical encounter does not mean current presence');
  const present=JSON.parse(JSON.stringify(unpresent.ctx.state));
  present.encounteredCharacters=[lucia];
  unpresent.ctx.backroomTurn(JSON.stringify(present));
  assert.equal(unpresent.dialog.hidden,false,'transition from absent to present must trigger even if ID was already recorded');
});
test('Lucia popup requires Core presence; delayed rendering can recover it without another turn',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[],
    log:[{role:'gm',text:'Lucia xuất hiện cạnh Cao Minh.'}]}});
  assert.equal(x.dialog.hidden,true,'GM prose alone cannot confirm an NPC');
  x.ctx.state.encounteredCharacters=[{id:'lucia',name:'Lucia "Lục"',present:true,
    inParty:false,joinEligible:true,avatar:'avatars/lucia_avatar.jpg'}];
  x.ctx.render();
  assert.equal(x.dialog.hidden,false,'Core-projected presence triggers even without new backroomTurn');
  finishModal(x);
  const p=x.ctx.state.specialDialogueProgress;
  assert.equal(p.introRevisions.lucia,'lucia-intro-r01');
  x.ctx.render();
  assert.equal(x.dialog.hidden,true);
});
test('Lucia authored intro resumes at the same line on reload',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},
    encounteredCharacters:[{id:'lucia',name:'Lucia "Lục"',present:true,inParty:false}]}});
  assert.equal(x.dialog.hidden,false);
  x.parts['#specialDialogueActions'].children[0].click();
  x.parts['#specialDialogueActions'].children[0].click();
  const saved=JSON.parse(JSON.stringify(x.ctx.state));
  assert.equal(saved.specialDialogueProgress.pending.index,2);
  const y=env({state:saved});
  assert.equal(y.dialog.hidden,false);
  assert.equal(y.parts['#specialDialogueProgress'].textContent,'3 / 10');
  assert.match(y.parts['#specialDialogueText'].textContent,/Hứa Thuý Mai/);
  finishModal(y);
  assert.equal(y.ctx.state.specialDialogueProgress.introRevisions.lucia,'lucia-intro-r01');
});

test('authored encounters have distinct natural voices without a dump of author-only history',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  const people=x.ctx.BackroomSpecialDialogueData;
  const foes=x.ctx.BackroomSpecialEntityDialogue;
  const speech=(seq)=>seq.map(line=>line.text).join(' ');
  const lucia=speech(people.lucia.intro);
  const diep=speech(foes.diep_minh.episodes[0]);
  const tram=speech(foes.luc_tram_hac_hoa.episodes[0]);
  assert.match(lucia,/Đồng bằng sông Cửu Long/);
  assert.match(lucia,/Hứa Thuý Mai/);
  assert.match(lucia,/Tôi chưa biết anh là ai/);
  assert.doesNotMatch(lucia,/Vạn Giới|Lôi Thiên Vực|ma nguyên|Lục Trầm/);
  assert.match(diep,/Cao gia/);
  assert.match(diep,/bỏ sót ngươi/);
  assert.match(tram,/Ma khí trong kinh mạch/);
  assert.match(tram,/Ngươi biết tên ta/);
  assert.doesNotMatch(tram,/tự rời đi|lớp trì lưu|tỉnh trước ngươi|hồi sinh/);
  assert.match(speech(people.hua_tieu_lan.intro),/Anh Tý/);
  assert.match(speech(people.trac_lam.intro),/SRU-03/);
  assert.match(speech(people.luc_tram.intro),/Thiên Kiếm Môn/);
  for(const entry of Object.values(foes)){
    for(const scene of [...entry.episodes,...entry.cross,...entry.repeat]){
      assert.ok(scene.length>=3&&scene.length<=6);
      assert.ok(scene.every(line=>line.text.trim().length>0));
      assert.ok(scene.some(line=>line.speaker==='cao_minh'));
    }
  }
});


test('Catherine, Janet and Nguyễn Quốc Nam each receive their authored first-encounter popup',()=>{
  const cases=[
    ['catherine','Catherine Valeria Ashcombe','avatars/Catherine_avatar.webp',
      /tiếng chuông/, 'catherine-intro-r01'],
    ['janet','Janet the Lady','avatars/Janet_avatar.webp',
      /Mia/, 'janet-intro-r01'],
    ['nguyen_quoc_nam','Nguyễn Quốc Nam','avatars/NguyenQuocNam_avatar.webp',
      /bị thương/, 'nguyen-quoc-nam-intro-r01']
  ];
  for(const [id,name,avatar,opening,revision] of cases){
    const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
    const next=JSON.parse(JSON.stringify(x.ctx.state));
    next.encounteredCharacters=[{id,name,avatar,present:true,inParty:false,joinEligible:true}];
    x.ctx.backroomTurn(JSON.stringify(next));
    assert.equal(x.dialog.hidden,false,id+' popup must open after Core confirmation');
    assert.match(x.parts['#specialDialogueText'].textContent,opening);
    assert.equal(x.parts['#specialDialogueAvatar'].children[0].src,avatar);
    assert.equal(x.parts['#specialDialogueProgress'].textContent,'1 / 9');
    assert.equal(x.ctx.invited,undefined);
    assert.equal(x.ctx.state.party.length,0);
    for(let i=0;i<8;i++)x.parts['#specialDialogueActions'].children[0].click();
    const actions=x.parts['#specialDialogueActions'].children.map(b=>b.textContent);
    assert.ok(actions.includes('Mời vào Party'),id+' may be invited but not automatically recruited');
    x.parts['#specialDialogueActions'].children.at(-1).click();
    assert.equal(x.dialog.hidden,true);
    assert.equal(x.ctx.state.specialDialogueProgress.introduced[id],true);
    assert.equal(x.ctx.state.specialDialogueProgress.introRevisions[id],revision);
    assert.equal(x.ctx.invited,undefined);
    assert.equal(x.ctx.state.party.length,0,'intro never mutates Core party');
    x.ctx.SpecialDialogue.offerEntity();
    assert.equal(x.dialog.hidden,true,'completed introduction must not reopen');
  }
});

test('a previously generic greeting does not swallow the newly authored first meetings',()=>{
  for(const id of ['catherine','janet','nguyen_quoc_nam']){
    const x=env({state:{party:[],partyDetails:{members:[]},
      encounteredCharacters:[{id,name:id,present:true,joinEligible:true,inParty:false}],
      specialDialogueProgress:{introduced:{[id]:true},introRevisions:{},
        chapters:{},shownEncounters:{},pending:null,lastRollTurn:4}}});
    assert.equal(x.dialog.hidden,false,id+' receives authored dialogue after fallback greeting');
    finishModal(x);
    const snapshot=JSON.parse(JSON.stringify(x.ctx.state));
    const reloaded=env({state:snapshot});
    assert.equal(reloaded.dialog.hidden,true,id+' authored meeting must only play once');
  }
});

test('three new Follower narratives remain hidden until Core presence and expose backstory gradually',()=>{
  const catalog=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}})
    .ctx.BackroomSpecialDialogueData;
  const cases=[
    ['catherine','Catherine Valeria Ashcombe','avatars/Catherine_avatar.webp',/dấu chân/],
    ['janet','Janet the Lady','avatars/Janet_avatar.webp',/radio/],
    ['nguyen_quoc_nam','Nguyễn Quốc Nam','avatars/NguyenQuocNam_avatar.webp',/xây xát/]
  ];
  for(const [id,name,avatar,firstChapter] of cases){
    const d=catalog[id];
    assert.equal(d.story.length,7);
    assert.ok(d.story.every(scene=>scene.length>=3&&scene.some(x=>x.speaker==='cao_minh')));
    const unseen=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[],
      log:[{role:'gm',text:'Đã nhìn thấy '+name}]}});
    assert.equal(unseen.dialog.hidden,true,'GM text is not a Core-confirmed encounter');
    const intro=env({state:{party:[],partyDetails:{members:[]},
      encounteredCharacters:[{id,name,avatar,present:true,joinEligible:true,inParty:false}]}});
    assert.equal(intro.dialog.hidden,false);
    finishModal(intro);
    const next=JSON.parse(JSON.stringify(intro.ctx.state));
    next.party=[{id,name,avatar,presence:'ACTIVE'}];
    next.partyDetails={members:[{id,name,avatar,presence:'ACTIVE'}]};
    next.encounteredCharacters=[{id,name,avatar,present:true,joinEligible:true,inParty:true}];
    next.turn=5;
    next.log=[{role:'player',text:'Di chuyển kiểm tra hành lang'}];
    intro.ctx.backroomTurn(JSON.stringify(next));
    assert.equal(intro.dialog.hidden,false,'first chapter should trigger after a committed exploration turn');
    assert.match(intro.parts['#specialDialogueText'].textContent,firstChapter);
    assert.equal(intro.ctx.state.specialDialogueProgress.chapters[id],undefined);
    finishModal(intro);
    assert.equal(intro.ctx.state.specialDialogueProgress.chapters[id],1);
  }
});


test('all ten Follower biographies carry authored private memories and multi-turn past arcs',()=>{
  const env0=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  const catalog=env0.ctx.BackroomSpecialDialogueData;
  const ids=['lucia','hua_tieu_lan','luc_tram','trac_lam','catherine','janet',
    'nguyen_quoc_nam','iris','syvial','an-nhien'];
  assert.equal(Object.keys(catalog).length,ids.length);
  for(const id of ids){
    const p=catalog[id];
    assert.ok(p.intro.length>=5,id+' has an authored first contact');
    assert.ok(p.story.length>=4,id+' has multiple recalled scenes');
    assert.equal(p.past.length,p.story.length+1,id+' unlocks one recollection per finished chapter');
    for(const scene of p.story){
      assert.ok(scene.length>=3&&scene.length<=6,id+' has dialogue, not a lecture');
      assert.ok(scene.some(line=>line.speaker==='cao_minh'),id+' talks with Cao Minh');
      assert.ok(scene.every(line=>[id,'cao_minh'].includes(line.speaker)&&line.text.trim()),id+' legitimate speakers');
    }
  }
  assert.match(catalog.trac_lam.story.map(x=>x.map(l=>l.text).join(' ')).join(' '),/ECHO-17/);
  assert.match(catalog.catherine.story.map(x=>x.map(l=>l.text).join(' ')).join(' '),/Grayhaven/);
  assert.match(catalog.janet.story.map(x=>x.map(l=>l.text).join(' ')).join(' '),/Mia/);
  assert.match(catalog.nguyen_quoc_nam.story.map(x=>x.map(l=>l.text).join(' ')).join(' '),/Thùy Dương|Dương/);
  assert.match(catalog.luc_tram.story.map(x=>x.map(l=>l.text).join(' ')).join(' '),/Thiên Kiếm Môn/);
  assert.doesNotMatch(JSON.stringify(catalog['an-nhien']),/đã chết|bị sát hại|bị bắt cóc/);
  assert.doesNotMatch(JSON.stringify(catalog.syvial),/Kai.*đang ở đây|Kai.*đã chết/);
});

test('Iris, Syvial and An Nhiên only open introductory backstory on Core-confirmed presence',()=>{
  const profiles=[
    ['iris','Iris','avatars/Iris_avatar.jpg',/Đứng ở đó/],
    ['syvial','Syvial','avatars/Syvial_avatar.jpg',/Đừng nhìn thanh kiếm/],
    ['an-nhien','An Nhiên','avatars/an_nhien_avatar.png',/Chú ơi/]
  ];
  for(const [id,name,avatar,first] of profiles){
    const absent=env({state:{party:[],partyDetails:{members:[]},
      encounteredCharacters:[{id,name,avatar,present:false,inParty:false,joinEligible:true}]}});
    assert.equal(absent.dialog.hidden,true,'absent NPC cannot auto-open '+id);
    const present=JSON.parse(JSON.stringify(absent.ctx.state));
    present.encounteredCharacters[0].present=true;
    absent.ctx.backroomTurn(JSON.stringify(present));
    assert.equal(absent.dialog.hidden,false,'Core present opens '+id);
    assert.match(absent.parts['#specialDialogueText'].textContent,first);
    assert.equal(absent.parts['#specialDialogueAvatar'].children[0].src,avatar);
    assert.equal(absent.ctx.invited,undefined);
    finishModal(absent);
    assert.equal(absent.ctx.state.specialDialogueProgress.introduced[id],true);
    assert.ok(absent.ctx.state.specialDialogueProgress.introRevisions[id]);
    assert.equal(absent.ctx.state.party.length,0);
    const resumed=env({state:JSON.parse(JSON.stringify(absent.ctx.state))});
    assert.equal(resumed.dialog.hidden,true,'authored intro is not replayed on load');
  }
});

test('Past button rotates only currently unlocked memories and never advances authored story',()=>{
  const id='catherine';
  const name='Catherine Valeria Ashcombe';
  const follower={id,name,avatar:'avatars/Catherine_avatar.webp',presence:'ACTIVE'};
  const x=env({state:{party:[follower],partyDetails:{members:[follower]},
    encounteredCharacters:[{...follower,present:true,inParty:true,joinEligible:true}],
    specialDialogueProgress:{introduced:{catherine:true},introRevisions:{catherine:'catherine-intro-r01'},
      chapters:{catherine:0},pastVisits:{},shownEncounters:{},pending:null,lastRollTurn:4}}});
  const past=x.ctx.BackroomSpecialDialogueData.catherine.past;
  assert.equal(x.dialog.hidden,true);
  for(let i=0;i<3;i++){
    assert.equal(x.ctx.SpecialDialogue.openCharacter(id,'past'),true);
    assert.equal(x.parts['#specialDialogueText'].textContent,past[0],
      'a later memory must not be available before chapter unlock');
    finishModal(x);
  }
  assert.equal(x.ctx.state.specialDialogueProgress.chapters.catherine,0);
  assert.equal(x.ctx.state.specialDialogueProgress.pastVisits.catherine,3);
  x.ctx.state.specialDialogueProgress.chapters.catherine=2;
  x.ctx.state.specialDialogueProgress.pastVisits.catherine=0;
  for(let i=0;i<3;i++){
    assert.equal(x.ctx.SpecialDialogue.openCharacter(id,'past'),true);
    assert.equal(x.parts['#specialDialogueText'].textContent,past[i]);
    finishModal(x);
  }
  assert.equal(x.ctx.state.specialDialogueProgress.chapters.catherine,2);
  const saved=JSON.parse(JSON.stringify(x.ctx.state));
  const y=env({state:saved});
  assert.equal(y.ctx.SpecialDialogue.openCharacter(id,'past'),true);
  assert.equal(y.parts['#specialDialogueText'].textContent,past[0],'past-memory rotation resumes with presentation state');
  finishModal(y);
});

test('new Follower chapters advance only when reading finishes after a committed exploration turn',()=>{
  const id='syvial';
  const f={id,name:'Syvial',presence:'ACTIVE',avatar:'avatars/Syvial_avatar.jpg'};
  const x=env({state:{party:[f],partyDetails:{members:[f]},
    encounteredCharacters:[{...f,present:true,inParty:true}],
    specialDialogueProgress:{introduced:{syvial:true},introRevisions:{syvial:'syvial-intro-r01'},
      chapters:{syvial:0},shownEncounters:{},pending:null,lastRollTurn:4}}});
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.turn=5;
  next.log=[{role:'player',text:'Di chuyển qua hành lang và kiểm tra lối rẽ'}];
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false);
  assert.match(x.parts['#specialDialogueText'].textContent,/Có lần người ta thấy quỷ tính/);
  assert.equal(x.ctx.state.specialDialogueProgress.chapters.syvial,0);
  const mid=JSON.parse(JSON.stringify(x.ctx.state));
  const resumed=env({state:mid});
  assert.equal(resumed.dialog.hidden,false);
  finishModal(resumed);
  assert.equal(resumed.ctx.state.specialDialogueProgress.chapters.syvial,1);
  const after=env({state:JSON.parse(JSON.stringify(resumed.ctx.state))});
  assert.equal(after.dialog.hidden,true,'no second reveal without a committed turn');
});


test('edited follower history respects dynamic Core state and does not assert invented injuries',()=>{
  const profiles=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}})
    .ctx.BackroomSpecialDialogueData;
  const namFirst=profiles.nguyen_quoc_nam.story[0].map(x=>x.text).join(' ');
  assert.match(namFirst,/Nếu có vết xây xát/);
  assert.doesNotMatch(namFirst,/Anh bị xây xát/);
  const endings={
    catherine:/tiếng chuông thứ mười ba/,
    janet:/dây thép gai/,
    nguyen_quoc_nam:/Dương vẽ cả nhà/,
    lucia:/Đồng bằng sông Cửu Long/,
    hua_tieu_lan:/không phải Anh Tý/,
    luc_tram:/vết xước/,
    trac_lam:/ECHO-17/,
    iris:/chưa sẵn sàng/,
    syvial:/GodKiller/,
    'an-nhien':/cơm nóng/
  };
  for(const [id,pattern] of Object.entries(endings)){
    const d=profiles[id];
    assert.match(d.past.at(-1),pattern,id+' last recalled detail is not unlocked at introduction');
    assert.equal(d.past.length,d.story.length+1);
    assert.ok(d.past.every(t=>typeof t==='string'&&t.trim().length>15));
  }
});


test('Follower intro recomposes snapshot on open and finish without another Core turn',()=>{
  const x=env({state:{party:[],partyDetails:{members:[]},encounteredCharacters:[]}});
  const states=[];
  x.ctx.backroomRefreshSnapshotVisuals=()=>states.push({
    pending:x.ctx.state.specialDialogueProgress.pending&&x.ctx.state.specialDialogueProgress.pending.id,
    stillPresent:x.ctx.state.encounteredCharacters.some(c=>c.id==='janet'&&c.present)
  });
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.encounteredCharacters=[{id:'janet',name:'Janet the Lady',
    avatar:'avatars/Janet_avatar.webp',present:true,joinEligible:true,inParty:false}];
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false);
  assert.deepEqual(states,[{pending:'janet',stillPresent:true}]);
  finishModal(x);
  assert.deepEqual(states,[{pending:'janet',stillPresent:true},{pending:null,stillPresent:true}],
    'closing first-contact must repaint even while Core keeps Janet physically present');
  assert.equal(x.ctx.state.specialDialogueProgress.introduced.janet,true);
});


test('later Follower story chapters repaint the two-shot on open and restore it on finish',()=>{
  const follower={id:'janet',name:'Janet the Lady',presence:'ACTIVE',
    avatar:'avatars/Janet_avatar.webp'};
  const x=env({state:{party:[follower],partyDetails:{members:[follower]},
    encounteredCharacters:[{...follower,present:false,inParty:true}],
    specialDialogueProgress:{introduced:{janet:true},introRevisions:{janet:'janet-intro-r01'},
      chapters:{janet:0},shownEncounters:{},pending:null,lastRollTurn:4}}});
  assert.equal(x.dialog.hidden,true);
  const events=[];
  x.ctx.backroomRefreshSnapshotVisuals=()=>events.push(
    x.ctx.state.specialDialogueProgress.pending?.kind||'exploration');
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.turn=5;next.log=[{role:'player',text:'Di chuyển và kiểm tra hành lang'}];
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,false);
  assert.equal(x.ctx.state.specialDialogueProgress.pending.kind,'story');
  finishModal(x);
  assert.deepEqual(events,['story','exploration']);
  assert.equal(x.ctx.state.specialDialogueProgress.chapters.janet,1);
});


test('unavailable Party Follower does not start a story with no matching overlay',()=>{
  const member={id:'janet',name:'Janet the Lady',presence:'ACTIVE',
    currentHp:0,avatar:'avatars/Janet_avatar.webp'};
  const x=env({state:{party:[{id:'janet',name:member.name,presence:'ACTIVE'}],
    partyDetails:{members:[member]},
    encounteredCharacters:[{id:'janet',present:false,inParty:true}],
    specialDialogueProgress:{introduced:{janet:true},introRevisions:{janet:'janet-intro-r01'},
      chapters:{janet:0},shownEncounters:{},pending:null,lastRollTurn:4}}});
  const next=JSON.parse(JSON.stringify(x.ctx.state));
  next.turn=5;next.log=[{role:'player',text:'Đi qua hành lang'}];
  x.ctx.backroomTurn(JSON.stringify(next));
  assert.equal(x.dialog.hidden,true);
  assert.equal(x.ctx.state.specialDialogueProgress.pending,null);
});
