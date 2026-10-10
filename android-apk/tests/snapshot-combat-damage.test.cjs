const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/combat-feedback-1193a.js'),'utf8');

function boot(initialState){
 const timers=[];
 function element(tag='div'){
  let text='';
  const n={tagName:tag.toUpperCase(),children:[],style:{},dataset:{},listeners:{},className:'',id:'',hidden:false,isConnected:true,scrollTop:0,scrollHeight:0,parentNode:null,
   appendChild(c){if(c.parentNode)c.parentNode.children=c.parentNode.children.filter(x=>x!==c);this.children.push(c);c.parentNode=this;c.isConnected=true;this.scrollHeight=this.children.length;return c},
   insertBefore(c,before){if(c.parentNode)c.parentNode.children=c.parentNode.children.filter(x=>x!==c);const i=this.children.indexOf(before);this.children.splice(i<0?0:i,0,c);c.parentNode=this;c.isConnected=true;return c},
   insertAdjacentElement(where,c){if(where!=='afterend'||!this.parentNode)return null;if(c.parentNode)c.parentNode.children=c.parentNode.children.filter(x=>x!==c);const p=this.parentNode,i=p.children.indexOf(this);p.children.splice(i+1,0,c);c.parentNode=p;c.isConnected=true;return c},
   remove(){if(this.parentNode)this.parentNode.children=this.parentNode.children.filter(c=>c!==this);this.parentNode=null;this.isConnected=false},
   addEventListener(name,fn){this.listeners[name]=fn},
   setAttribute(name,value){this[name]=String(value)},
   getBoundingClientRect(){return {left:0,top:0,width:480,height:300}}
  };
  Object.defineProperty(n,'textContent',{get(){return text},set(value){text=String(value??'');if(text==='')n.children=[]}});
  Object.defineProperty(n,'nextElementSibling',{get(){if(!n.parentNode)return null;const i=n.parentNode.children.indexOf(n);return n.parentNode.children[i+1]||null}});
  Object.defineProperty(n,'firstElementChild',{get(){return n.children[0]||null}});
  n.classList={add(...xs){n.className+=' '+xs.join(' ')},remove(x){n.className=n.className.split(' ').filter(c=>c!==x).join(' ')}};
  return n;
 }
 const root=element('root'),box=element(),entity=element(),actor=element();
 box.id='snapshot';root.appendChild(box);
 box.querySelector=s=>s==='.snapshot-entity'?entity:actor;
 box.querySelectorAll=s=>box.children.filter(n=>n.className.includes('combat-float')&&(!s.includes('data-target')||s.includes('"'+n.dataset.target+'"')));
 function find(node,id){if(node.id===id)return node;for(const child of node.children){const hit=find(child,id);if(hit)return hit}return null}
 const starting=initialState||{combat:{active:true,encounterId:'enc',entityKey:'diep_minh',round:1}};
 const scope={
  state:JSON.parse(JSON.stringify(starting)),
  document:{body:null,getElementById(id){return find(root,id)},createElement:element},
  requestAnimationFrame(fn){fn()},
  setTimeout(fn,ms){timers.push({fn,ms});if(ms===0)fn()}
 };
 scope.window=scope;
 scope.backroomTurn=json=>{scope.state=JSON.parse(json)};
 vm.runInNewContext(script,scope);
 return {
  root,box,timers,scope,
  panel(){return scope.document.getElementById('combatFeedbackPanel')},
  entries(){return scope.document.getElementById('combatFeedbackEntries')},
  event(e){scope.backroomPlayCombatFeedback(e)},
  turn(events,id='enc:1',encounterId='enc',combat={active:true,encounterId:'enc',entityKey:'diep_minh',round:1},extra={}){
   const payload=Object.assign({},extra,{combat});
   if(events!==undefined)payload.combatFeedback={id,encounterId,events};
   scope.backroomTurn(JSON.stringify(payload));
  }
 };
}

test('normal hits keep the original dedicated badge and numeric value',()=>{
 const r=boot();r.event({target:'entity',text:'-36 HP'});
 const n=r.box.children[0];assert.match(n.className,/combat-float--normal/);assert.equal(n.children[0].textContent,'-36 HP');assert.equal(n.children[0].children[0].textContent,'Đánh thường');
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
test('animation-end cleanup and 1800ms fallback both remove feedback safely',()=>{
 const r=boot();r.event({target:'entity',text:'-1 HP'});const n=r.box.children[0];assert.equal(r.timers[0].ms,1800);n.listeners.animationend();r.timers[0].fn();assert.equal(r.box.children.length,0);
 const fallback=boot();fallback.event({target:'entity',text:'-1 HP'});fallback.timers[0].fn();assert.equal(fallback.box.children.length,0);
});
test('turn packets are authoritative, deduplicated and constrained to the encounter',()=>{
 const r=boot(),events=[{target:'entity',text:'-3 HP'}];r.turn(events);r.turn(events);assert.equal(r.box.children.length,1);r.turn(events,'other:1','other');assert.equal(r.box.children.length,1);
});
test('final kill feedback survives combat removal and does not use net turn HP',()=>{
 const r=boot();r.turn([{target:'entity',text:'-110 HP'},{target:'actor',text:'-8 HP'}],'enc:1','enc',null);r.timers.filter(t=>t.ms===150).forEach(t=>t.fn());assert.equal(r.box.children.length,2);
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
test('overflow queues instead of clipping and restores Poker Dice after the last float',()=>{
 const r=boot();let resumed=0;r.scope.ensureDirectCombatDice=()=>resumed++;
 r.turn(Array.from({length:4},()=>({target:'entity',text:'-1 HP',phase:'actor'})));
 assert.equal(r.scope.__combatFeedbackBusy,true);r.timers.find(t=>t.ms===150).fn();r.timers.find(t=>t.ms===300).fn();assert.equal(r.box.children.length,3);
 r.timers.filter(t=>t.ms===1800).slice(0,2).forEach(t=>t.fn());assert.equal(r.box.children.length,3);assert.equal(new Set(r.box.children.map(n=>n.style.top)).size,3);
 assert.ok(r.box.children.every(n=>parseFloat(n.style.top)>=0));r.timers.find(t=>t.ms===3600).fn();assert.equal(r.scope.__combatFeedbackBusy,false);assert.equal(resumed,1);
});

test('combat panel is transient UI and never writes GM narrative state',()=>{
 const narrative=[{role:'gm',text:'GM narrative remains untouched'}];
 const r=boot({combat:{active:true,encounterId:'enc',entityKey:'diep_minh',round:1},log:narrative});
 assert.ok(r.panel());assert.equal(r.panel().hidden,false);assert.equal(r.root.children[1],r.panel());
 const before=JSON.stringify(r.scope.state.log);
 r.turn([{target:'entity',text:'-36 HP',phase:'actor',critical:true,status:'Chảy máu'}],'enc:1','enc',{active:true,encounterId:'enc',entityKey:'diep_minh',round:2},{log:narrative});
 assert.equal(JSON.stringify(r.scope.state.log),before);
 const lines=r.entries().children.map(n=>n.textContent);
 assert.ok(lines.some(x=>x.includes('Vòng 2')));
 assert.ok(lines.some(x=>x.includes('PARTY → ENTITY · -36 HP · CRIT · CHẢY MÁU')));
 assert.equal(script.includes('state.log'),false);
 assert.equal(script.includes('localStorage'),false);
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
 assert.equal(r.panel().hidden,false);
 assert.ok(r.entries().children.some(n=>n.textContent.includes('-110 HP')));
 r.timers.filter(t=>t.ms===1800).forEach(t=>t.fn());
 assert.equal(r.panel().hidden,true);assert.equal(r.entries().children.length,0);
 assert.deepEqual(r.scope.state.log,narrative);
});
