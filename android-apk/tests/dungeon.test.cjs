const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assets=path.join(__dirname,'../app/src/main/assets');
const source=fs.readFileSync(path.join(assets,'dungeon.js'),'utf8');
function boot(){
 let now=0;const requests=[],timers=[],intervals=[],ids={};let renderCount=0;
 const doc={hidden:false,activeElement:null,listeners:{},addEventListener(k,f){this.listeners[k]=f},getElementById(id){return ids[id]},createElement:element};
 function element(tag){
  const e={tagName:tag,children:[],dataset:{},listeners:{},disabled:false,hidden:false,attrs:{},value:'',className:'',_text:'',
   appendChild(c){this.children.push(c);return c},setAttribute(k,v){this.attrs[k]=String(v)},
   addEventListener(k,f){this.listeners[k]=f},click(){if(!this.disabled)this.listeners.click?.({})},focus(){doc.activeElement=this},
   querySelectorAll(selector){const all=[];function walk(n){n.children.forEach(c=>{all.push(c);walk(c)})}walk(this);
    return all.filter(c=>selector==='button,select'?['button','select'].includes(c.tagName):
     selector.startsWith('[data-slot=')?String(c.dataset.slot)===selector.match(/"([^"]+)"/)[1]:
     selector.startsWith('.')?c.className.split(' ').includes(selector.slice(1)):c.tagName===selector)},
   querySelector(selector){return this.querySelectorAll(selector)[0]||null}};
  Object.defineProperty(e,'textContent',{get(){return this._text+this.children.map(c=>c.textContent).join('')},set(v){this._text=String(v);this.children=[]}});
  return e;
 }
 doc.body=element('body');doc.body.classList={add(){},remove(){}};
 for(const id of ['dungeonOpen','dungeonModal','dungeonSlots','dungeonStatus','gameMenuClose','dungeonClose','dungeonRefresh']){ids[id]=element('div');ids[id].id=id;}
 ids.dungeonModal.hidden=true;ids.dungeonModal.children=[ids.dungeonClose,ids.dungeonSlots,ids.dungeonRefresh];
 const scope={document:doc,performance:{now(){return now}},state:{turn:1,log:[]},busy:false,
  semanticPrepareNextState(old,json){return JSON.parse(json)},syncPrimaryActions(){},
  Option:function(text,value){const e=element('option');e.textContent=text;e.value=value;return e},
  setTimeout(fn,ms){timers.push({fn,ms});return timers.length},clearTimeout(){},setInterval(fn){intervals.push(fn)},
  BackroomSnapshotAssets:{environmentFile(){return 'level_00__yellow_rot.webp'}},
  Android:{dungeonRequest(state,packet){requests.push({state:JSON.parse(state),...JSON.parse(packet)})}},
  render(){renderCount++}};scope.window=scope;
 vm.runInNewContext(source,scope);
 function data(){return {serverNowMs:1000000,followers:[{id:'iris',name:'Iris',level:0,avatar:'avatars/Iris_avatar.jpg',inParty:true}],
  slots:[15,30,60,120,180].map((durationMinutes,slot)=>({slot,durationMinutes,status:'empty'}))};}
 function respond(model=data(),id=requests.at(-1)?.requestId,ok=true){scope.backroomDungeon(JSON.stringify({requestId:id,ok,dungeon:model,state:{turn:1,log:[],party:['Cao Minh']}}))}
 return {scope,ids,requests,timers,intervals,data,respond,open(){ids.dungeonOpen.click()},tick(ms){now+=ms;intervals.forEach(f=>f())},renders(){return renderCount}};
}
function training(model,index=0){model.followers=[];model.slots[index]={slot:index,durationMinutes:[15,30,60,120,180][index],status:'training',runId:9,
 startedAtMs:model.serverNowMs,endsAtMs:model.serverNowMs+900000,creditedMinutes:0,earnedExp:0,booksFound:0,
 environmentId:'yellow_rot',environmentName:'Vàng Úa',follower:{id:'iris',name:'Iris',level:0,avatar:'avatars/Iris_avatar.jpg'}};return model;}

test('Dungeon opens from menu with five fixed slots and only sends a read request',()=>{
 const r=boot();r.open();assert.equal(r.ids.dungeonModal.hidden,false);assert.equal(r.requests.length,1);assert.equal(r.requests[0].operation,'view');
 r.respond();assert.equal(r.ids.dungeonSlots.children.length,5);
 assert.deepEqual(r.ids.dungeonSlots.children.map(c=>c.querySelector('h3').textContent),[
 'SLOT 1 · 15 phút','SLOT 2 · 30 phút','SLOT 3 · 1 giờ','SLOT 4 · 2 giờ','SLOT 5 · 3 giờ']);
 assert.equal(r.renders(),0,'read-only polling must not reset the combat renderer');
 r.ids.dungeonClose.click();assert.equal(r.ids.dungeonModal.hidden,true);
});

test('assignment submits only slot/follower IDs, prevents double click and refreshes Party on success',()=>{
 const r=boot();r.open();r.respond();const card=r.ids.dungeonSlots.children[0],select=card.querySelector('select'),start=card.querySelector('button');
 assert.equal(start.disabled,true);select.value='iris';select.listeners.change();start.click();start.click();
 assert.equal(r.requests.length,2);assert.equal(r.requests[1].operation,'start');assert.equal(r.requests[1].slot,0);assert.equal(r.requests[1].followerId,'iris');
 assert.equal('timestamp' in r.requests[1],false);assert.equal('exp' in r.requests[1],false);assert.equal(r.scope.busy,true);
 r.respond(training(r.data()));assert.equal(r.scope.busy,false);assert.equal(r.renders(),1);
 assert.equal(r.ids.dungeonSlots.children[0].querySelector('img').src,'level_snapshots/level_00__yellow_rot.webp');
});

test('progress display never manufactures EXP or counts; early release includes the exact run ID',()=>{
 const r=boot();r.open();r.respond(training(r.data()));r.tick(60000);
 const card=r.ids.dungeonSlots.children[0];assert.equal(card.querySelector('progress').value,60000);
 assert.match(card.querySelector('.dungeon-exp').textContent,/0 EXP · 0\/15 Entity/);
 assert.equal(r.requests.at(-1).operation,'view');r.respond(training(r.data()));
 r.ids.dungeonSlots.children[0].querySelector('button').click();
 assert.equal(r.requests.at(-1).operation,'release');assert.equal(r.requests.at(-1).runId,9);
});

test('busy gameplay prevents assignment and stale async responses cannot unlock a new request',()=>{
 const r=boot();r.open();r.respond();const card=r.ids.dungeonSlots.children[0],select=card.querySelector('select');
 select.value='iris';select.listeners.change();r.scope.busy=true;card.querySelector('button').click();
 assert.equal(r.requests.length,1);assert.match(r.ids.dungeonStatus.textContent,/hoàn tất/);
 r.scope.busy=false;card.querySelector('button').click();const current=r.requests.at(-1).requestId;
 r.respond(r.data(),'stale');assert.equal(r.scope.busy,true);
 r.respond(training(r.data()),current);assert.equal(r.scope.busy,false);
});

test('unsafe image sources and HTML names are not interpreted as markup',()=>{
 const r=boot();r.open();const model=training(r.data());model.slots[0].follower.avatar='javascript:alert(1)';model.slots[0].follower.name='<img src=x onerror=alert(1)>';
 r.respond(model);const card=r.ids.dungeonSlots.children[0];assert.equal(card.querySelectorAll('img').length,1);
 assert.match(card.querySelector('strong').textContent,/<img src=x/);
});

for(const slotStatus of ['training','complete']) test('Lucia Dungeon '+slotStatus+' displays the bundled overlay instead of her avatar',()=>{
 const r=boot();r.open();const model=training(r.data()),slot=model.slots[0];
 slot.status=slotStatus;
 slot.follower={id:'lucia',name:'Lucia "Lục"',level:4,
  sprite:'LUCIA_LUC_OVERLAY.png',avatar:'avatars/lucia_avatar.jpg'};
 r.respond(model);
 const card=r.ids.dungeonSlots.children[0],portrait=card.querySelector('.dungeon-follower');
 assert.ok(portrait,'training and completed slots must retain the follower image');
 assert.equal(portrait.src,'LUCIA_LUC_OVERLAY.png');
 assert.equal(portrait.alt,'Lucia "Lục"');
 assert.equal(card.querySelectorAll('.dungeon-follower').length,1);
 assert.equal(card.querySelectorAll('img').length,2,'one environment and one follower');
 assert.equal(card.querySelector('button').textContent,slotStatus==='complete'?'Hoàn tất · Đưa về':'Đưa về sớm');
 const overlay=fs.readFileSync(path.join(assets,portrait.src));
 assert.equal(overlay.subarray(0,8).toString('hex'),'89504e470d0a1a0a','overlay path must resolve to the packaged PNG');
});

test('Trac Lam Dungeon uses the canonical bundled full-body PNG instead of the avatar',()=>{
 const r=boot();r.open();const model=training(r.data());
 model.slots[0].follower={id:'trac_lam',name:'Trác Lâm',level:3,
  sprite:'TracLam.png',avatar:'avatars/TracLam_avatar.jpg'};
 r.respond(model);
 const portrait=r.ids.dungeonSlots.children[0].querySelector('.dungeon-follower');
 assert.ok(portrait);
 assert.equal(portrait.src,'TracLam.png');
 assert.equal(portrait.alt,'Trác Lâm');
 assert.equal(fs.readFileSync(path.join(assets,portrait.src)).subarray(0,8).toString('hex'),
  '89504e470d0a1a0a','full-body sprite must be bundled');
});

test('Lucia-like unsafe sprite paths cannot bypass the bundled image allowlist',()=>{
 for(const sprite of ['https://example.com/LUCIA_LUC_OVERLAY.png','file:///android_asset/LUCIA_LUC_OVERLAY.png',
  '../LUCIA_LUC_OVERLAY.png','avatars/../LUCIA_LUC_OVERLAY.png','LUCIA_LUC_OVERLAY.png?remote=1','javascript:alert(1)']){
  const r=boot();r.open();const model=training(r.data());
  model.slots[0].follower={id:'lucia',name:'Lucia "Lục"',level:4,sprite,avatar:'avatars/lucia_avatar.jpg'};
  r.respond(model);
  const portrait=r.ids.dungeonSlots.children[0].querySelector('.dungeon-follower');
  assert.ok(portrait,'an invalid sprite should still permit the safe bundled avatar');
  assert.equal(portrait.src,'avatars/lucia_avatar.jpg',sprite);
 }
});

test('new UI assets are wired into HTML, byte verification and APK extraction',()=>{
 const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');assert.match(html,/id="dungeonOpen"/);assert.match(html,/id="dungeonModal" hidden role="dialog"/);
 assert.match(html,/src="dungeon.js"/);assert.match(html,/href="dungeon.css"/);
 const verifier=fs.readFileSync(path.join(__dirname,'../verify-explorer-controls.mjs'),'utf8');assert.match(verifier,/'dungeon.js','dungeon.css'/);
 const workflow=fs.readFileSync(path.join(__dirname,'../../.github/workflows/build-backroom-apk.yml'),'utf8');assert.match(workflow,/'assets\/dungeon\.\*'/);
 const css=fs.readFileSync(path.join(assets,'dungeon.css'),'utf8');assert.match(css,/#dungeonModal\[hidden\]\{display:none!important\}/);
 assert.doesNotMatch(source,/localStorage|Math.random|grantExp|processCombat|submitAction/);
});
