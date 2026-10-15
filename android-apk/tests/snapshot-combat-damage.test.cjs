const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.js'),'utf8');

function boot(initialState,options={}){
 const timers=[],animations=[],frames=[];
 function element(tag='div'){
  let text='';
  const n={tagName:tag.toUpperCase(),children:[],style:{},dataset:{},listeners:{},className:'',id:'',hidden:false,isConnected:true,scrollTop:0,scrollHeight:0,parentNode:null,
   appendChild(c){if(c.parentNode)c.parentNode.children=c.parentNode.children.filter(x=>x!==c);this.children.push(c);c.parentNode=this;c.isConnected=true;this.scrollHeight=this.children.length;return c},
   insertBefore(c,before){if(c.parentNode)c.parentNode.children=c.parentNode.children.filter(x=>x!==c);const i=this.children.indexOf(before);this.children.splice(i<0?0:i,0,c);c.parentNode=this;c.isConnected=true;return c},
   insertAdjacentElement(where,c){if(where!=='afterend'||!this.parentNode)return null;if(c.parentNode)c.parentNode.children=c.parentNode.children.filter(x=>x!==c);const p=this.parentNode,i=p.children.indexOf(this);p.children.splice(i+1,0,c);c.parentNode=p;c.isConnected=true;return c},
   remove(){if(this.parentNode)this.parentNode.children=this.parentNode.children.filter(c=>c!==this);this.parentNode=null;this.isConnected=false},
   addEventListener(name,fn){this.listeners[name]=fn},
   animate(frames,opts){const animation={node:this,frames,options:opts,currentTime:options.animationTime===undefined?180:options.animationTime,playState:'running',cancelled:false,cancel(){this.playState='idle';this.cancelled=true}};animations.push(animation);return animation},
   setAttribute(name,value){this[name]=String(value)},
   getBoundingClientRect(){return {left:0,top:0,width:480,height:300}}
  };
  Object.defineProperty(n,'textContent',{get(){return text},set(value){text=String(value??'');if(text==='')n.children=[]}});
  Object.defineProperty(n,'nextElementSibling',{get(){if(!n.parentNode)return null;const i=n.parentNode.children.indexOf(n);return n.parentNode.children[i+1]||null}});
  Object.defineProperty(n,'firstElementChild',{get(){return n.children[0]||null}});
  n.classList={add(...xs){n.className+=' '+xs.join(' ')},remove(x){n.className=n.className.split(' ').filter(c=>c!==x).join(' ')}};
  return n;
 }
 const root=element('root'),box=element(),dice=element(),entity=element(),actor=element(),lucia=element();
 box.id='snapshot';dice.id='pokerDiceModal';root.appendChild(box);root.appendChild(dice);
 let entityReady=options.entityReady!==false;
 box.querySelector=s=>s==='.snapshot-entity'?(entityReady?entity:null):s==='.snapshot-character'?actor:s==='.snapshot-lucia'&&options.luciaReady?lucia:
  box.children.find(n=>s==='.combat-turn-avatar'&&n.className.includes('combat-turn-avatar'))||null;
 box.querySelectorAll=s=>s.includes('.combat-impact')?
  box.children.filter(n=>/combat-impact|combat-player-hit-edge|combat-float/.test(n.className)):
  s.includes('.combat-turn-exit')?
  box.children.filter(n=>/combat-turn-(?:exit|avatar)/.test(n.className)):
  box.children.filter(n=>n.className.includes('combat-float')&&(!s.includes('data-target')||s.includes('"'+n.dataset.target+'"')));
 function find(node,id){if(node.id===id)return node;for(const child of node.children){const hit=find(child,id);if(hit)return hit}return null}
 const starting=initialState||{combat:{active:true,encounterId:'enc',entityKey:'diep_minh',round:1}};
 const scope={
  state:JSON.parse(JSON.stringify(starting)),
  document:{body:null,getElementById(id){return find(root,id)},createElement:element},
  getComputedStyle(){return {transform:options.baseTransform||'none'}},
  requestAnimationFrame(fn){if(options.deferFrames)frames.push(fn);else fn()},
  setTimeout(fn,ms){timers.push({fn,ms});if(ms===0)fn()}
 };
 scope.window=scope;
 scope.backroomTurn=json=>{
  scope.state=JSON.parse(json);
  if(options.stripTransient)delete scope.state.combatFeedback;
 };
 vm.runInNewContext(script,scope);
 return {
  root,box,dice,actor,entity,lucia,timers,animations,scope,
  frame(){frames.shift()()},
  setEntityReady(value){entityReady=!!value},
  panel(){return scope.document.getElementById('combatFeedbackPanel')},
  entries(){return scope.document.getElementById('combatFeedbackEntries')},
  event(e){scope.backroomPlayCombatFeedback(e)},
  turn(events,id='enc:1',encounterId='enc',combat={active:true,encounterId:'enc',entityKey:'diep_minh',round:1},extra={},packetExtra={}){
   const payload=Object.assign({},extra,{combat});
   if(events!==undefined)payload.combatFeedback=Object.assign({id,encounterId,events},packetExtra);
   scope.backroomTurn(JSON.stringify(payload));
  }
 };
}

test('normal hits keep the original dedicated badge and numeric value',()=>{
 const r=boot();r.event({target:'entity',text:'-36 HP'});
 const n=r.box.children[0];assert.match(n.className,/combat-float--normal/);assert.equal(n.children[0].textContent,'-36 HP');assert.equal(n.children[0].children[0].textContent,'Đánh thường');
});
test('skill damage uses the Core skill name instead of the normal attack badge, including critical skills',()=>{
 for(const critical of [false,true]){
  const r=boot();r.event({target:'entity',text:'-120 HP',skill:'Huyết Ma Nhị Thập Tứ Trảm',critical});
  const n=r.box.children[0],badge=n.children[0].children[0];
  assert.match(n.className,/combat-float--skill/);
  assert.doesNotMatch(n.className,/combat-float--normal/);
  assert.equal(badge.textContent,'Huyết Ma Nhị Thập Tứ Trảm');
  assert.match(badge.className,/combat-float-status--skill/);
  assert.equal(n.className.includes('combat-float--critical'),critical);
 }
});
test('explicit normal attacks stay normal; a skill with a status retains the status badge',()=>{
 const normal=boot();normal.event({target:'entity',text:'-12 HP',skill:'  ĐÁNH THƯỜNG  '});
 assert.equal(normal.box.children[0].children[0].children[0].textContent,'Đánh thường');
 const status=boot();status.event({target:'entity',text:'-12 HP',skill:'Huyết Ma Tứ Liên',status:'Chảy máu'});
 assert.equal(status.box.children[0].children[0].children[0].textContent,'CHẢY MÁU');
 assert.doesNotMatch(status.box.children[0].className,/combat-float--normal/);
});
test('critical hits and all five status badges keep their independent classifications',()=>{
 for(const [status,key,label] of [['Chảy máu','bleed','CHẢY MÁU'],['Trúng độc','poison','TRÚNG ĐỘC'],['Choáng','stun','CHOÁNG'],['Xuyên giáp','armor','XUYÊN GIÁP'],['Mất phương hướng','disorient','MẤT PHƯƠNG HƯỚNG']]){
  const r=boot();r.event({target:'entity',text:'-5 HP',status,critical:true});const n=r.box.children[0];assert.ok(n.className.includes('combat-float--status-'+key));assert.match(n.className,/combat-float--critical/);assert.equal(n.children[0].children[0].textContent,label);
 }
});
test('simultaneous hits use lanes 38px apart independently for each target',()=>{
 const r=boot();r.event({target:'entity',text:'-3 HP'});r.event({target:'entity',text:'-4 HP'});r.event({target:'actor',text:'-2 HP'});
 assert.equal(parseFloat(r.box.children[0].style.top)-parseFloat(r.box.children[1].style.top),38);assert.equal(r.box.children[0].style.top,r.box.children[2].style.top);
});
test('invalid values and off-screen teammates cannot be attributed to the visible actor',()=>{
 const r=boot();for(const text of ['PROC','<b>-3 HP</b>','-0 HP','-NaN HP','3 HP'])r.event({target:'entity',text});
 r.event({target:'actor',actorId:'lucia',text:'-5 HP'});r.event({target:'unknown',text:'-5 HP'});assert.equal(r.box.children.length,0);
});
test('animation-end cleanup and 1250ms fallback both remove feedback safely',()=>{
 const r=boot();r.event({target:'entity',text:'-1 HP'});const n=r.box.children[0];assert.equal(r.timers[0].ms,1250);n.listeners.animationend();r.timers[0].fn();assert.equal(r.box.children.length,0);
 const fallback=boot();fallback.event({target:'entity',text:'-1 HP'});fallback.timers[0].fn();assert.equal(fallback.box.children.length,0);
});
test('turn packets are authoritative, deduplicated and constrained to the encounter',()=>{
 const r=boot(),events=[{target:'entity',text:'-3 HP'}];r.turn(events);r.turn(events);assert.equal(r.box.children.length,1);r.turn(events,'other:1','other');assert.equal(r.box.children.length,1);
});
test('final kill feedback survives combat removal and does not use net turn HP',()=>{
 const r=boot();r.turn([{target:'entity',text:'-110 HP'},{target:'actor',text:'-8 HP'}],'enc:1','enc',null);
 assert.equal(r.panel().hidden,true);assert.equal(r.box.children.length,0);
 const healed=boot();healed.turn([{target:'actor',text:'-8 HP'}],'enc:1','enc',{active:true,encounterId:'enc',entityKey:'diep_minh',round:1,playerHp:100});assert.equal(healed.box.children[0].children[0].textContent,'-8 HP');
});
test('actor/enemy phases preserve 150ms hit staggering and 1000ms separation',()=>{
 const r=boot();const events=[{target:'entity',text:'-3 HP',phase:'actor'},{target:'entity',text:'-4 HP',phase:'actor'},{target:'actor',text:'-2 HP',phase:'entity'}];
 r.turn(events);assert.equal(r.box.children.length,1);r.turn(events);r.timers.find(t=>t.ms===150).fn();assert.equal(r.box.children.length,2);r.timers.find(t=>t.ms===1000).fn();assert.equal(r.box.children.length,3);
});
test('freed lanes are reused without colliding with surviving floats',()=>{
 const r=boot();for(let i=0;i<3;i++)r.event({target:'entity',text:'-1 HP'});r.box.children[0].listeners.animationend();r.event({target:'entity',text:'-2 HP'});
 assert.equal(new Set(r.box.children.map(n=>n.style.top)).size,3);
});
test('overflow queues without hiding Poker Dice and resyncs it when feedback starts and ends',()=>{
 const r=boot();let resumed=0;r.scope.ensureDirectCombatDice=()=>resumed++;
 r.turn(Array.from({length:4},()=>({target:'entity',text:'-1 HP',phase:'actor'})));
 assert.equal(r.scope.__combatFeedbackBusy,true);r.timers.find(t=>t.ms===150).fn();r.timers.find(t=>t.ms===300).fn();assert.equal(r.box.children.length,3);
 r.timers.find(t=>t.ms===1250).fn();r.timers.find(t=>t.ms===1800).fn();assert.equal(r.box.children.length,3);assert.equal(new Set(r.box.children.map(n=>n.style.top)).size,3);
 assert.ok(r.box.children.every(n=>parseFloat(n.style.top)>=0));r.timers.find(t=>t.ms===3600).fn();assert.equal(r.scope.__combatFeedbackBusy,false);assert.equal(resumed,2);
 assert.equal(script.includes('modal.hidden=true'),false);
});

test('combat panel is transient UI and never writes GM narrative state',()=>{
 const narrative=[{role:'gm',text:'GM narrative remains untouched'}];
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'diep_minh',round:1},log:narrative});
 assert.ok(r.panel());assert.equal(r.panel().hidden,false);assert.equal(r.root.children[1],r.dice);assert.equal(r.root.children[2],r.panel());
 const before=JSON.stringify(r.scope.state.log);
 r.turn([{target:'entity',text:'-36 HP',phase:'actor',critical:true,status:'Chảy máu'}],'enc:1','enc',{active:true,encounterId:'enc',entityKey:'diep_minh',round:2},{log:narrative});
 assert.equal(JSON.stringify(r.scope.state.log),before);
 const lines=r.entries().children.map(n=>n.textContent);
 assert.equal(lines.some(x=>/Vòng [0-9]+/.test(x)),false);
 assert.ok(lines.some(x=>x.includes('sử dụng đánh thường')&&x.includes('-36 HP')));
 assert.equal(script.includes('state.log'),false);
 assert.equal(script.includes('localStorage'),false);
});

test('combat panel reanchors itself directly below Poker Dice on every update',()=>{
 const r=boot();
 const panel=r.panel();
 r.root.insertBefore(panel,r.dice);
 assert.equal(r.root.children[1],panel);
 r.turn([{target:'entity',text:'-4 HP',phase:'actor'}],'enc:reanchor','enc',{active:true,encounterId:'enc',entityKey:'diep_minh',round:2});
 assert.equal(r.root.children[1],r.dice);
 assert.equal(r.root.children[2],panel);
});

test('combat panel renders the transient Combat Core summary without touching GM log',()=>{
 const narrative=[{role:'gm',text:'Narrative only'}];
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'hound',round:1},log:narrative});
 r.turn([{target:'entity',text:'-9 HP',phase:'actor'}],'enc:2','enc',{active:true,encounterId:'enc',entityKey:'hound',round:1},{log:narrative},{summary:'Cao Minh dùng kỹ năng thử nghiệm. Hound phản công.'});
 assert.equal(r.entries().children.some(n=>n.className.includes('combat-feedback-entry--summary')),false);
 assert.deepEqual(r.scope.state.log,narrative);
});

test('combat panel deduplicates packets and resets when a new encounter begins',()=>{
 const r=boot();
 r.turn([{target:'entity',text:'-3 HP',phase:'actor'}]);
 const once=r.entries().children.length;
 r.turn([{target:'entity',text:'-3 HP',phase:'actor'}]);
 assert.equal(r.entries().children.length,once);
 r.turn(undefined,'unused','unused',{active:true,encounterId:'enc-2',entityKey:'hound',round:1});
 const text=r.entries().children.map(n=>n.textContent).join('\n');
 assert.ok(text.includes('Hound'));assert.equal(text.includes('-3 HP'),false);
});

test('combat panel keeps the final packet briefly then clears after combat ends',()=>{
 const narrative=[{role:'gm',text:'Không trộn combat log vào đây'}];
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'diep_minh',round:3},log:narrative});
 r.turn([{target:'entity',text:'-110 HP',phase:'actor'}],'enc:final','enc',null,{log:narrative});
 assert.equal(r.panel().hidden,true);assert.equal(r.entries().children.length,0);
 assert.deepEqual(r.scope.state.log,narrative);
});


test('typed combat cues show active character and a short spinning transition between turns',()=>{
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'hound',round:1,waveNumber:1,waveCount:2}});
 const cues=[
  {kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',waveNumber:1,waveCount:2,entityName:'Hound'},
  {kind:'wave',eventIndex:1,waveNumber:2,waveCount:2,entityName:'Smiler'},
  {kind:'actor',eventIndex:1,actorId:'lucia',actorName:'Lucia',waveNumber:2,waveCount:2,entityName:'Smiler'},
  {kind:'entity',eventIndex:2,waveNumber:2,waveCount:2,entityName:'Smiler'}
 ];
 r.turn([{target:'entity',phase:'actor',text:'-12 HP'},{target:'entity',phase:'actor',text:'-9 HP'},{target:'actor',phase:'entity',text:'-3 HP'}],
  'enc:typed','enc',{active:true,encounterId:'enc',entityKey:'smiler',round:2,waveNumber:2,waveCount:2},
  {},{turns:cues});
 const title=()=>r.scope.document.getElementById('combatTurnName').textContent;
 assert.ok(title().includes('Cao Minh'));assert.ok(r.panel().children.length>=3);
 assert.equal(r.scope.document.getElementById('combatTurnIndicator').hidden,true);
 const timers=r.timers.filter(t=>t.ms>0).map(t=>t.ms);
 assert.ok(timers.includes(800),'800ms transition must be scheduled');
 assert.equal(r.scope.document.getElementById('combatFeedbackMeta').textContent,'NOW : Cao Minh');
 // Transition starts after actor feedback plus a short readability gap.
 r.timers.find(t=>t.ms===1430).fn();
 assert.ok(title().includes('Smiler'));assert.ok(r.entries().children.some(n=>n.textContent.includes('Bắt đầu giao chiến với Smiler')));
 r.timers.find(t=>t.ms===2640).fn();
 assert.ok(title().includes('Lucia'));assert.equal(r.scope.__combatFeedbackBusy,true);
});

test('typed no-hit turns still animate, and reduced-motion shortens the wait',()=>{
 const r=boot();
 r.scope.matchMedia=()=>({matches:true});
 r.turn([],'enc:quiet','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2,waveNumber:1,waveCount:1},
  {},{turns:[{kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',waveNumber:1,waveCount:1,entityName:'Hound'},
             {kind:'entity',eventIndex:0,waveNumber:1,waveCount:1,entityName:'Hound'}]});
 assert.ok(r.scope.document.getElementById('combatTurnName').textContent.includes('Cao Minh'));
 assert.ok(r.timers.some(t=>t.ms===500),'reduced motion advances to the next cue at 500ms');
 assert.equal(r.timers.some(t=>t.ms===800),false);
 assert.equal(r.scope.__combatFeedbackBusy,true);
});

test('typed final-kill cue plays before combat closure then clears transient panel',()=>{
 const r=boot();
 r.turn([{target:'entity',phase:'actor',text:'-5 HP'}],'enc:final-typed','enc',null,{},{
  turns:[{kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',waveNumber:1,waveCount:1,entityName:'Hound'}]
 });
 assert.equal(r.panel().hidden,false,'keep the final turn visible until animation ends');
 assert.equal(r.scope.state.combat.active,true);
 assert.equal(r.scope.__combatFeedbackBusy,true);
 const end=Math.max(...r.timers.map(t=>t.ms));
 r.timers.find(t=>t.ms===end).fn();
 assert.equal(r.panel().hidden,true);
 assert.equal(r.scope.__combatFeedbackBusy,false);
 assert.equal(r.scope.state.combat,null);
});


test('Core re-projection cannot drop skill names and damage from transient combat packet',()=>{
 const r=boot(undefined,{stripTransient:true});
 const summary='Cao Minh dùng Huyết Ma Tứ Liên: 170% DMG = -34 HP. Lucia bắn M4A1: -26 HP.';
 r.turn([{target:'entity',phase:'actor',actorId:'cao_minh',text:'-34 HP'}],
  'enc:combat-details','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},
  {},{summary,turns:[{kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',waveNumber:1,waveCount:1,entityName:'Hound'}]});
 assert.equal(r.scope.state.combatFeedback,undefined);
 r.timers.filter(t=>t.ms===980).forEach(t=>t.fn());
 assert.ok(r.entries().children.some(n=>n.textContent.includes('-34 HP')));
 assert.equal(r.scope.__combatFeedbackBusy,true);
 const again=r.entries().children.length;
 r.turn([{target:'entity',text:'-34 HP'}],'enc:combat-details','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{summary});
 assert.equal(r.entries().children.length,again);
});

test('combat keeps Entity in left slot while Party takes turns on right',()=>{
 const r=boot();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 const turns=[
  {kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',waveNumber:1,waveCount:1,entityName:'Hound'},
  {kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',waveNumber:1,waveCount:1,entityName:'Hound'},
  {kind:'entity',eventIndex:0,waveNumber:1,waveCount:1,entityName:'Hound'}
 ];
 r.turn([],'enc:party-rotates','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{turns});
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 const ordered=r.timers.filter(t=>t.ms===1210||t.ms===2420);
 assert.equal(ordered.length,2);
 ordered[0].fn();assert.equal(r.box.dataset.combatFocus,'lucia');
 ordered[1].fn();assert.equal(r.box.dataset.combatFocus,'cao_minh');
 const last=Math.max(...r.timers.map(t=>t.ms));
 r.timers.find(t=>t.ms===last).fn();
 r.turn(undefined,'enc:stop','enc',null);
 assert.equal(r.box.dataset.combatFocus,undefined);
});

test('snapshot style has distinct active-actor rules and existing local Party assets',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 assert.match(css,/COMBAT_ACTOR_SINGLE_FOCUS_R01/);
 assert.match(css,/\.snapshot\[data-combat-focus="lucia"\] \.snapshot-lucia/);
 assert.match(css,/\.snapshot\[data-combat-focus\] \.snapshot-character/);
 assert.match(css,/\.snapshot\[data-combat-focus\] \.snapshot-entity/);
 assert.match(css,/\.snapshot\[data-combat-focus\] \.snapshot-entity\{/);
 assert.match(css,/display:block!important;left:0!important;right:auto!important/);
 assert.doesNotMatch(css,/\.snapshot\[data-combat-focus\] \.snapshot-entity\{display:none!important\}/);
 assert.match(css,/@keyframes combat-turn-enter/);
 assert.match(css,/@keyframes combat-turn-leave/);
 assert.match(css,/rotate:y -65deg/);
 assert.match(css,/translate:-75% 0/);
 const keyframes=css.slice(css.indexOf('@keyframes combat-turn-enter'),css.indexOf('@media(prefers-reduced-motion:reduce)',css.indexOf('@keyframes combat-turn-enter')));
 assert.doesNotMatch(keyframes,/transform:/,'3D animation must preserve the Entity ground-plane inline transform');
 assert.doesNotMatch(css,/combat-switch-ring/);
 assert.doesNotMatch(css,/combat-party-appear/,'only one Party animation definition may own a slot');
 assert.match(css,/\.snapshot\[data-combat-focus\] \.snapshot-lucia,/);
 assert.match(css,/right:0!important;left:auto!important/);
 assert.match(css,/height:97%!important/);
 assert.match(css,/max-width:100%!important/);
 assert.match(css,/animation:none!important/,'Entity never rotates for its own cue');

 for(const filename of ['avatars/Iris_avatar.jpg','avatars/Syvial_avatar.jpg','avatars/an_nhien_avatar.png']){
  assert.ok(fs.existsSync(path.join(__dirname,'../app/src/main/assets',filename)),filename);
 }
});


test('individual cue skill and DMG details are displayed during that actor turn',()=>{
 const r=boot(undefined,{stripTransient:true});
 r.turn([],'enc:skills','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{
  summary:'Cả Party cùng tấn công.',
  turns:[
   {kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',waveNumber:1,waveCount:1,entityName:'Hound',
    details:['Huyết Ma Tứ Liên tự động kích hoạt: 170% DMG = -34 HP.']},
   {kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',waveNumber:1,waveCount:1,entityName:'Hound',
    details:['Lucia bắn M4A1: -26 HP.']}
  ]
 });
 assert.equal(r.entries().children.some(x=>x.textContent.includes('-34 HP')),false);
 const at=r.timers.find(t=>t.ms===1210);
 assert.ok(at);
 at.fn();
 assert.equal(r.scope.document.getElementById('combatFeedbackMeta').textContent,'NOW : Lucia');
 assert.equal(r.box.dataset.combatFocus,'lucia');
});


test('after animated turns keep the last combatant visible while waiting for the next round',()=>{
 const r=boot();
 r.turn([],'enc:recover','enc',{active:true,encounterId:'enc',entityKey:'hound',round:3},{},{
  turns:[{kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',waveNumber:1,waveCount:1,entityName:'Hound'},
         {kind:'entity',eventIndex:0,waveNumber:1,waveCount:1,entityName:'Hound'}]
 });
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 const finish=Math.max(...r.timers.map(t=>t.ms));
 r.timers.find(t=>t.ms===finish).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 assert.equal(r.scope.__combatFeedbackBusy,false);
});

test('Combat Log owns the GM slot and retains the inline action composer',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
 assert.match(css,/COMBAT_LOG_FIXED_PANEL_R02/);assert.match(css,/margin:5px auto 0!important/);
 assert.match(css,/flex:1 1 0/);assert.match(css,/max-height:none;overflow-y:auto/);
 assert.match(css,/body\.poker-dice-open \.game>\.log\{display:none!important\}/);
 assert.match(css,/pointer-events:none;opacity:1/);
 assert.ok(html.includes('.primary-action-row.combat-direct{display:flex!important}'));
 assert.ok(html.includes('row.hidden=false'));
});
test('Party NOW returns to Cao Minh as soon as Entity counterattacks and remains after round',()=>{
 const r=boot();const meta=()=>r.scope.document.getElementById('combatFeedbackMeta').textContent;
 assert.equal(meta(),'NOW : Cao Minh');
 r.turn([],'enc:header','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{turns:[
  {kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',entityName:'Hound',waveNumber:1,waveCount:1},
  {kind:'entity',eventIndex:0,entityName:'Hound',waveNumber:1,waveCount:1}
 ]});
 assert.equal(meta(),'NOW : Lucia');r.timers.find(t=>t.ms===1210).fn();assert.equal(meta(),'NOW : Cao Minh');
 const finish=Math.max(...r.timers.map(t=>t.ms));r.timers.find(t=>t.ms===finish).fn();
 assert.equal(meta(),'NOW : Cao Minh');
});

test('typed feedback prints one fully specified skill hit and remaining HP',()=>{
 const r=boot(undefined,{stripTransient:true});
 r.turn([{target:'entity',text:'-120 HP',phase:'actor',sourceName:'Cao Minh',skill:'Huyết Ma Nhị Thập Tứ Trảm',
  entityName:'Hound',remainingHp:80,maxHp:200}], 'enc:typed-skill','enc',
  {active:true,encounterId:'enc',entityName:'Hound',entityKey:'hound',round:2},{},{turns:[
  {kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',entityName:'Hound',waveNumber:1,waveCount:1}]});
 r.timers.filter(t=>t.ms===980).forEach(t=>t.fn());
 assert.equal(r.entries().children.filter(x=>x.textContent.includes('Huyết Ma Nhị Thập Tứ Trảm')).length,1);
 assert.ok(r.entries().children.some(x=>x.textContent==='Cao Minh sử dụng [Huyết Ma Nhị Thập Tứ Trảm] gây -120 HP lên Hound [80/200 HP]'));
});
test('Entity skill impacts Party once with shared HP and retains Party header',()=>{
 const r=boot();
 r.turn([],'enc:entity-skill','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{
 turns:[{kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',entityName:'Hound',waveNumber:1,waveCount:1},
 {kind:'entity',eventIndex:0,entityName:'Hound',waveNumber:1,waveCount:1,
 details:['Cắn Xé proc: 140% Basic Attack; Bleed.','Hound phản công: Party chịu -30 HP (170/200). Cao Minh -15; Lucia -15.']}]});
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.entries().children.some(x=>x.textContent.includes('gây -30 HP')),false);
 r.timers.find(t=>t.ms===2190).fn();
 assert.ok(r.entries().children.some(x=>x.textContent==='Hound sử dụng [Cắn Xé] gây -30 HP lên cả nhóm [170/200 HP]'));
 assert.equal(r.scope.document.getElementById('combatFeedbackMeta').textContent,'NOW : Cao Minh');
});


test('long Party timelines preserve every typed turn rather than truncating after twelve',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.js'),'utf8');
 assert.ok(!source.includes('}).slice(0,12)'));
 assert.match(source,/Keep every Party\/Entity cue/);
});

test('Combat Snapshot matches Explorer dimensions and only Combat Log gives up height',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
 const explorer=html.match(/\.snapshot\{height:(\d+)px;margin:([^;]+);border:/);
 const combat=css.match(/body\.poker-dice-open \.game>\.snapshot\{([^}]+)\}/);
 assert.ok(explorer,'Explorer Snapshot dimensions are defined');
 assert.ok(combat,'Combat Snapshot dimensions are defined');
 assert.equal(explorer[1],'300');
 assert.match(combat[1],new RegExp('height:'+explorer[1]+'px(?:;|$)'));
 assert.match(combat[1],new RegExp('flex:0 0 '+explorer[1]+'px(?:;|$)'));
 assert.match(combat[1],new RegExp('min-height:'+explorer[1]+'px(?:;|$)'));
 assert.ok(combat[1].includes('margin:'+explorer[2]+'!important'));
 assert.doesNotMatch(css,/\.game>\.snapshot\{height:clamp\(/);
 assert.match(css,/\.game>#combatFeedbackPanel\{display:flex;flex:1 1 0;min-height:0;overflow:hidden/);
 assert.match(css,/margin:5px auto 0!important/);
});

test('short-screen Combat stays scrollable without reducing Snapshot below Explorer size',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 const fallback=css.match(/@media\(max-height:740px\)\{([\s\S]*?)\n\}/);
 assert.ok(fallback,'Short viewport overflow fallback must exist');
 assert.match(fallback[1],/body\.poker-dice-open \.game\{overflow-y:auto!important/);
 assert.match(fallback[1],/\.game>#combatFeedbackPanel\{flex:0 0 120px;min-height:120px\}/);
 assert.match(fallback[1],/\.game>\.player-action-bar\{position:sticky;bottom:0/);
 assert.doesNotMatch(fallback[1],/\.game>\.snapshot\{/);
});


test('Entity counterattack keeps Cao Minh and avoids creating a rotating Entity clone',()=>{
 const r=boot();
 r.actor.src='file:///android_asset/CAO_MINH_OVERLAY_EXPLORER_IDLE.png';
 r.entity.src='file:///android_asset/entity.webp';
 r.turn([],'enc:handoff','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{
  turns:[{kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',entityName:'Hound'},
         {kind:'entity',eventIndex:0,entityName:'Hound'}]
 });
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 const leaving=r.box.children.find(n=>n.className.includes('combat-turn-exit'));
 assert.equal(leaving,undefined,'Entity cue must not clone or rotate the still-present actor');
});

test('turn focus uses projected Party avatar for actors outside the original hardcoded group',()=>{
 const members=[{id:'luc_tram',avatarRef:'avatars/luctram_avatar.png',presence:'ACTIVE'}];
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'hound'},partyDetails:{members}});
 r.turn([],'enc:luc-tram','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},
  {partyDetails:{members}},{
   turns:[{kind:'actor',eventIndex:0,actorId:'luc_tram',actorName:'Lục Trầm',entityName:'Hound'},
          {kind:'entity',eventIndex:0,entityName:'Hound'}]
  });
 assert.equal(r.box.dataset.combatFocus,'luc_tram');
 const portrait=r.box.children.find(n=>n.className==='combat-turn-avatar');
 assert.ok(portrait);assert.equal(portrait.src,'avatars/luctram_avatar.png');
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 assert.equal(r.box.children.some(n=>n.className==='combat-turn-avatar'),false);
 const exit=r.box.children.find(n=>n.className.includes('combat-turn-exit'));
 assert.ok(exit);assert.equal(exit.src,'avatars/luctram_avatar.png');
});


test('entering combat without a damage packet keeps Cao Minh in Snapshot instead of hiding all portraits',()=>{
 const r=boot({combat:{active:true,encounterId:'new-enc',entityKey:'hound',round:1}});
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 r.turn(undefined,'unused','new-enc',{active:true,encounterId:'new-enc',entityKey:'hound',round:1});
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 assert.equal(r.panel().hidden,false);
});

test('Entity load callbacks never alter the Party focus',()=>{
 const r=boot(undefined,{entityReady:false});
 const cues=[
  {kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',entityName:'Hound'},
  {kind:'entity',eventIndex:0,entityName:'Hound'}
 ];
 r.turn([],'enc:async','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{turns:cues});
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh','show existing actor while Android is loading Entity');
 r.entity.src='file:///android_asset/entity/hound.webp';
 r.entity.style.opacity='0'; // bridge inserted img but it has not loaded
 r.setEntityReady(true);
 r.scope.backroomSyncCombatPortrait();
 assert.equal(r.box.dataset.combatFocus,'cao_minh','do not reveal an undecoded Entity image');
 r.entity.style.opacity='1'; // appendEntityOverlay.onload
 r.scope.backroomSyncCombatPortrait();
 assert.equal(r.box.dataset.combatFocus,'cao_minh','Entity image load must not change the Party actor');
 const end=Math.max(...r.timers.map(t=>t.ms));
 r.timers.find(t=>t.ms===end).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh','Cao Minh remains the default between rounds');
});

test('Snapshot reconstruction reattaches active dynamic Party portrait without switching actor',()=>{
 const partyDetails={members:[{id:'luc_tram',avatarRef:'avatars/luctram_avatar.png',presence:'ACTIVE'}]};
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'hound',round:1},partyDetails});
 r.turn([],'enc:render','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{partyDetails},
  {turns:[{kind:'actor',eventIndex:0,actorId:'luc_tram',actorName:'Lục Trầm',entityName:'Hound'}]});
 assert.equal(r.box.dataset.combatFocus,'luc_tram');
 assert.equal(r.box.children.filter(n=>n.className==='combat-turn-avatar').length,1);
 r.box.textContent=''; // native renderSnapshot clears the entire Snapshot DOM
 r.scope.backroomSyncCombatPortrait();
 assert.equal(r.box.dataset.combatFocus,'luc_tram');
 assert.equal(r.box.children.filter(n=>n.className==='combat-turn-avatar').length,1);
 assert.equal(r.box.children.find(n=>n.className==='combat-turn-avatar').src,'avatars/luctram_avatar.png');
 const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
 assert.match(java,/appendLuciaCombatOverlay\(\);if\(typeof window!=='undefined'&&typeof window\.backroomSyncCombatPortrait==='function'\)window\.backroomSyncCombatPortrait\(\)/);
 assert.match(java,/normalizeEntityVisual\(img\);img\.dataset\.entityStatus='ready';img\.style\.opacity='1'/);
});

test('Entity rerender does not change Party focus even if its image is missing',()=>{
 const r=boot();
 r.actor.src='file:///android_asset/CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png';
 r.entity.src='file:///android_asset/entity/hound.webp';
 r.turn([],'enc:entity-rerender','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},
  {turns:[{kind:'entity',eventIndex:0,entityName:'Hound'}]});
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 r.setEntityReady(false);
 r.scope.backroomSyncCombatPortrait();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 r.setEntityReady(true);
 r.scope.backroomSyncCombatPortrait();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
});


test('final combat packet presents Lucia cue before Core victory projection closes the Snapshot',()=>{
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'hound',round:2},partyDetails:{
  members:[{id:'cao_minh',presence:'ACTIVE'},{id:'lucia',presence:'ACTIVE'}]
 }});
 const turns=[
  {kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',entityName:'Hound'},
  {kind:'actor',eventIndex:1,actorId:'lucia',actorName:'Lucia',entityName:'Hound'}
 ];
 r.turn([{target:'entity',phase:'actor',text:'-10 HP'}, {target:'entity',phase:'actor',text:'-20 HP'}],
  'enc:final-party','enc',null,{},{turns});
 assert.equal(r.scope.state.combat.active,true,'Core victory projection waits for final visual cues');
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 const lucia=r.timers.find(t=>t.ms===1430);
 assert.ok(lucia,'Core-supplied Lucia cue must be scheduled even if combat is now settled');
 lucia.fn();assert.equal(r.box.dataset.combatFocus,'lucia');
 assert.equal(r.panel().hidden,false);
 const end=Math.max(...r.timers.map(t=>t.ms));
 r.timers.find(t=>t.ms===end).fn();
 assert.equal(r.panel().hidden,true);
 assert.equal(r.scope.state.combat,null);
});

test('Entity overlay stays visible as an opponent during Cao Minh and Lucia active turns',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 assert.match(css,/\.snapshot\[data-combat-focus\] \.snapshot-entity\{/);
 assert.match(css,/display:block!important;left:0!important;right:auto!important/);
 assert.doesNotMatch(css,/\.snapshot\[data-combat-focus\] \.snapshot-entity\{display:none!important\}/);
 const r=boot();
 const turns=[{kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',entityName:'Hound'},
              {kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',entityName:'Hound'}];
 r.turn([],'enc:visible-foe','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{turns});
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.box.dataset.combatFocus,'lucia');
});

test('after Lucia finishes, Cao Minh returns without another dice round and Entity never rotates',()=>{
 const r=boot();
 r.actor.src='file:///android_asset/CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png';
 r.entity.src='file:///android_asset/entity/hound.webp';
 r.turn([],'enc:default-leader','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{
  turns:[{kind:'actor',eventIndex:0,actorId:'cao_minh',actorName:'Cao Minh',entityName:'Hound'},
         {kind:'actor',eventIndex:0,actorId:'lucia',actorName:'Lucia',entityName:'Hound'},
         {kind:'entity',eventIndex:0,entityName:'Hound'}]});
 r.timers.find(t=>t.ms===1210).fn();
 assert.equal(r.box.dataset.combatFocus,'lucia');
 r.timers.find(t=>t.ms===2420).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 assert.equal(r.box.children.some(n=>n.className.includes('combat-turn-exit--entity')),false);
 const final=Math.max(...r.timers.map(t=>t.ms));
 r.timers.find(t=>t.ms===final).fn();
 assert.equal(r.box.dataset.combatFocus,'cao_minh');
 assert.equal(r.scope.__combatFeedbackBusy,false);
});

test('Iris and Syvial use real Party portrait elements and common Cao Minh anchor, not pseudo portraits',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'hound',round:1}});
 r.turn([],'enc:iris','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{
  turns:[{kind:'actor',eventIndex:0,actorId:'iris',actorName:'Iris',entityName:'Hound'}]});
 assert.equal(r.box.dataset.combatFocus,'iris');
 const img=r.box.children.find(n=>n.className==='combat-turn-avatar');
 assert.ok(img);
 assert.equal(img.src,'avatars/Iris_avatar.jpg');
 assert.doesNotMatch(css,/\.snapshot\[data-combat-focus="iris"\]::after/);
 assert.match(css,/\.snapshot\[data-combat-focus\] \.combat-turn-avatar/);
});

test('each wave cue carries authoritative Entity key, even if adjacent waves share a species',()=>{
 const java=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt'),'utf8');
 assert.match(java,/put\("entityName", entityName\); put\("entityKey", entityKey\)/);
 let switches=[];
 const r=boot();
 r.scope.backroomSwitchCombatEntityWave=(key,wave)=>switches.push(key+':'+wave);
 r.turn([],'enc:waves','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},{},{
  turns:[{kind:'wave',eventIndex:0,entityKey:'hound',waveNumber:2,entityName:'Hound'},
         {kind:'wave',eventIndex:0,entityKey:'hound',waveNumber:3,entityName:'Hound'}]});
 assert.deepEqual(switches,['hound:2']);
 r.timers.find(t=>t.ms===1210).fn();
 assert.deepEqual(switches,['hound:2','hound:3']);
});


test('MISS dodges the actual target with a full-size floating label and no impact or hit flash',()=>{
 const r=boot();r.event({target:'entity',actorId:'cao_minh',outcome:'miss',text:'MISS',flash:false});
 const miss=r.box.children.find(n=>n.className.includes('combat-float--miss'));
 assert.equal(miss.children[0].textContent,'MISS');assert.equal(miss.children[0].children.length,0);
 assert.equal(r.box.children.some(n=>n.className.includes('combat-impact')),false);
 assert.equal(r.animations.length,1);assert.equal(r.animations[0].node,r.entity);
 assert.equal(r.animations[0].options.duration,420);
 assert.match(r.animations[0].frames[1].transform,/translateX\(-11px\)/);
});
test('direct hits preserve native Entity alignment and slower recoil independently of turn CSS',()=>{
 const r=boot(undefined,{baseTransform:'matrix(1.2, 0, 0, 1.2, -6, 3)'});
 r.event({target:'entity',text:'-12 HP',outcome:'hit',flash:true});
 const recoil=r.animations.find(a=>a.options.duration===400);
 assert.equal(recoil.node,r.entity);
 assert.match(recoil.frames[1].transform,/^translateX\(-6px\) matrix/);
 assert.equal(r.animations.find(a=>a.options.duration===140).node,r.entity);
 assert.equal(r.box.children.filter(n=>n.className==='combat-impact').length,1);
});
test('visible Lucia receives her own incoming hit instead of Cao Minh',()=>{
 const r=boot(undefined,{luciaReady:true});r.box.dataset.combatFocus='lucia';
 r.event({target:'actor',actorId:'lucia',text:'-9 HP',outcome:'hit',flash:true});
 assert.ok(r.animations.some(a=>a.node===r.lucia&&a.options.duration===400));
 assert.equal(r.animations.some(a=>a.node===r.actor),false);
});
test('typed lunge precedes impact by 180ms and no next wave can steal the hit',()=>{
 const r=boot();let waves=0;r.scope.backroomSwitchCombatEntityWave=()=>waves++;
 r.turn([{target:'entity',actorId:'cao_minh',text:'-7 HP',flash:true,outcome:'hit'}],
  'enc:contact','enc',undefined,{}, {turns:[
  {kind:'actor',actorId:'cao_minh',eventIndex:0},
  {kind:'wave',entityKey:'smiler',waveNumber:2,eventIndex:1}]});
 r.timers.find(t=>t.ms===800).fn();
 assert.ok(r.animations.some(a=>a.node===r.actor&&a.options.duration===480));
 assert.equal(r.box.children.some(n=>n.className.includes('combat-impact')),false);
 r.timers.find(t=>t.ms===980).fn();
 assert.ok(r.box.children.some(n=>n.className==='combat-impact'));
 r.timers.find(t=>t.ms===1430).fn();assert.equal(waves,1);
 assert.equal(r.box.children.some(n=>n.className.includes('combat-impact')),false);
});
test('periodic status ticks and reduced motion never lunge or recoil',()=>{
 const tick=boot();tick.event({target:'entity',text:'-3 HP',status:'Trúng độc',outcome:'tick',flash:false});
 assert.equal(tick.animations.some(a=>a.frames[0].transform),false);
 assert.equal(tick.box.children.some(n=>n.className.includes('combat-impact')),false);
 const reduced=boot();reduced.scope.matchMedia=()=>({matches:true});
 reduced.event({target:'actor',text:'MISS',outcome:'miss'});
 assert.equal(reduced.animations.length,0);
 assert.ok(reduced.box.children.some(n=>n.className.includes('combat-float--miss')));
});
test('restarting contact cancels preceding displacement and encounter exit cancels all motion',()=>{
 const r=boot();r.event({target:'entity',text:'-3 HP',flash:true});
 const first=r.animations.find(a=>a.options.duration===400);
 r.event({target:'entity',text:'-4 HP',flash:true});assert.equal(first.cancelled,true);
 r.turn(undefined,'none','enc',null);
 assert.ok(r.animations.every(a=>a.cancelled));assert.equal(r.box.children.length,0);
});


test('late-starting WebView lunge gates impact, damage and log on animation contact time',()=>{
 const r=boot(undefined,{animationTime:0,deferFrames:true});
 r.turn([{target:'entity',actorId:'cao_minh',text:'-7 HP',flash:true,outcome:'hit'}],
  'enc:late','enc',undefined,{}, {turns:[{kind:'actor',actorId:'cao_minh',eventIndex:0}]});
 r.frame();r.timers.find(t=>t.ms===800).fn();r.timers.find(t=>t.ms===980).fn();
 assert.equal(r.box.children.some(n=>/combat-impact|combat-float/.test(n.className)),false);
 assert.equal(r.entries().children.some(n=>n.textContent.includes('-7 HP')),false);
 r.animations[0].currentTime=179;r.frame();
 assert.equal(r.box.children.some(n=>/combat-impact|combat-float/.test(n.className)),false);
 r.animations[0].currentTime=180;r.frame();
 assert.ok(r.box.children.some(n=>n.className==='combat-impact'));
 assert.ok(r.box.children.some(n=>n.className.includes('combat-float')));
 assert.ok(r.entries().children.some(n=>n.textContent.includes('-7 HP')));
});
test('group hit and MISS share one delayed Entity impact and cancel on encounter exit',()=>{
 const r=boot(undefined,{animationTime:0,deferFrames:true});
 const card=r.scope.document.createElement('div');card.dataset.character='lucia';
 card.querySelector=()=>r.lucia;card.querySelectorAll=()=>card.children;
 r.scope.document.querySelectorAll=()=>[card];r.root.appendChild(card);
 r.turn([{target:'actor',actorId:'cao_minh',outcome:'hit',text:'-8 HP',flash:true},
  {target:'actor',actorId:'lucia',outcome:'miss',text:'MISS',flash:false}],
  'enc:group','enc',undefined,{}, {turns:[{kind:'entity',eventIndex:0,entityName:'Hound',
  details:['Hound phản công: Party chịu -8 HP (92/100).']}]});
 r.frame();r.timers.find(t=>t.ms===0).fn();
 assert.equal(r.entries().children.some(n=>n.textContent.includes('gây -8 HP')),false);
 r.timers.find(t=>t.ms===800).fn();r.timers.find(t=>t.ms===980).fn();
 assert.equal(r.box.children.some(n=>n.className.includes('combat-float')),false);
 r.animations[0].currentTime=180;r.frame();
 assert.equal(r.box.children.filter(n=>n.className.includes('combat-float')).length,1);
 assert.equal(card.children.filter(n=>n.className.includes('combat-float--miss')).length,1);
 assert.equal(r.entries().children.filter(n=>n.textContent.includes('gây -8 HP')).length,1);
 const cancelled=boot(undefined,{animationTime:0,deferFrames:true});
 cancelled.turn([{target:'entity',text:'-2 HP',flash:true}], 'enc:cancel','enc',undefined,{},
 {turns:[{kind:'actor',actorId:'cao_minh',eventIndex:0}]});
 cancelled.frame();cancelled.timers.find(t=>t.ms===800).fn();cancelled.timers.find(t=>t.ms===980).fn();
 cancelled.turn(undefined,'none','enc',null);cancelled.frame();
 assert.equal(cancelled.box.children.length,0);
});
test('normal and critical damage start fully visible at the impact frame',()=>{
 const css=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.css'),'utf8');
 assert.match(css,/@keyframes combat-float-up\{0%\{opacity:1;/);
 assert.match(css,/@keyframes combat-float-crit\{0%\{opacity:1;/);
});

function readyShatter(r){r.entity.src='file:///android_asset/entity/hound.webp';r.entity.style.opacity='1';return r;}
const lethal={target:'entity',text:'-24 HP',remainingHp:0,outcome:'hit'};
test('Core-confirmed lethal Entity damage grows one crack/core layer and twelve bounded shards',()=>{
 const r=readyShatter(boot());
 r.event({...lethal,remainingHp:1});assert.equal(r.box.children.some(n=>n.className==='entity-shatter'),false);
 r.event(lethal);
 const layer=r.box.children.find(n=>n.className==='entity-shatter');assert.ok(layer);
 assert.equal(layer.children.filter(n=>n.className==='entity-shatter-shard').length,12);
 assert.ok(layer.children.some(n=>n.className==='entity-shatter-cracks'));
 assert.ok(layer.children.some(n=>n.className==='entity-shatter-core'));
 assert.equal(r.entity.style.opacity,'1');
 r.event(lethal);assert.equal(r.box.children.filter(n=>n.className==='entity-shatter').length,1);
 r.timers.find(t=>t.ms===480).fn();assert.equal(r.entity.style.opacity,'0');
 r.timers.find(t=>t.ms===1320).fn();assert.equal(layer.isConnected,false);
});
test('actor defeat, MISS and unknown remaining HP never explode an Entity',()=>{
 const r=readyShatter(boot());
 for(const event of [{...lethal,target:'actor'},{...lethal,outcome:'miss',text:'MISS'},
  {...lethal,remainingHp:null},{...lethal,remainingHp:'0'},{...lethal,remainingHp:undefined}])r.event(event);
 assert.equal(r.box.children.some(n=>n.className==='entity-shatter'),false);
});
test('combat completion waits for the actual shatter before removing overlays and unlocking',()=>{
 const r=readyShatter(boot());
 r.turn([lethal],'enc:death','enc',null,{},{turns:[{kind:'actor',actorId:'cao_minh',eventIndex:0}]});
 r.timers.find(t=>t.ms===980).fn();
 const completion=r.timers.find(t=>t.ms===Math.max(...r.timers.map(t=>t.ms)));
 completion.fn();assert.equal(r.scope.state.combat.active,true);assert.equal(r.scope.__combatFeedbackBusy,true);
 r.timers.find(t=>t.ms===1320).fn();
 assert.equal(r.scope.state.combat,null);assert.equal(r.scope.__combatFeedbackBusy,false);
});
test('a wave switch waits for the old Entity shatter, including consecutive same-species waves',()=>{
 const r=readyShatter(boot());let switches=[];
 r.scope.backroomSwitchCombatEntityWave=(key,wave)=>switches.push(key+':'+wave);
 r.turn([lethal],'enc:death-wave','enc',{active:true,encounterId:'enc',entityKey:'hound',round:2},
  {},{turns:[{kind:'actor',actorId:'cao_minh',eventIndex:0},
   {kind:'wave',entityKey:'hound',waveNumber:2,eventIndex:1}]});
 r.timers.find(t=>t.ms===980).fn();
 r.timers.find(t=>t.ms===2390).fn();assert.deepEqual(switches,[]);
 r.timers.find(t=>t.ms===1320).fn();assert.deepEqual(switches,['hound:2']);
});
test('a new encounter cancels debris and stale callbacks without hiding its Entity',()=>{
 const r=readyShatter(boot());r.event(lethal);
 const layer=r.box.children.find(n=>n.className==='entity-shatter');
 r.turn([],'next:1','next',{active:true,encounterId:'next',entityKey:'hound',round:1});
 assert.equal(layer.isConnected,false);assert.equal(r.entity.style.opacity,'1');
 r.timers.filter(t=>t.ms===480||t.ms===1320).forEach(t=>t.fn());
 assert.equal(r.entity.style.opacity,'1');
});
test('reduced motion uses one short fade without cracks, core flash or flying fragments',()=>{
 const r=readyShatter(boot());r.scope.matchMedia=()=>({matches:true});r.event(lethal);
 const layer=r.box.children.find(n=>n.className==='entity-shatter entity-shatter--reduced');
 assert.ok(layer);assert.equal(layer.children.length,1);assert.equal(layer.children[0].className,'entity-shatter-ghost');
 r.timers.find(t=>t.ms===180).fn();assert.equal(layer.isConnected,false);
});
test('lethal status ticks without actor cues still finish their death effect before Core projection',()=>{
 const r=readyShatter(boot());
 r.turn([{...lethal,outcome:'tick',status:'Trúng độc'}],'enc:tick-death','enc',null);
 assert.equal(r.scope.state.combat.active,true);
 assert.ok(r.box.children.some(n=>n.className==='entity-shatter'));
 r.timers.find(t=>t.ms===1520).fn();assert.equal(r.scope.state.combat.active,true);
 r.timers.find(t=>t.ms===1320).fn();assert.equal(r.scope.state.combat,null);
});
