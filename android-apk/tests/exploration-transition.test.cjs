const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/exploration-transition.js'),'utf8');
const prefix='file:///android_asset/level_snapshots/';

function boot({reduced=false,broken=false}={}){
  const timers=new Map();let nextTimer=1;
  function el(type='div'){
    const node={type,children:[],dataset:{},attributes:{},className:'',clientWidth:400,offsetWidth:400,
      parentNode:null,isConnected:true,complete:true,naturalWidth:120,
      style:{setProperty(key,value){this[key]=value},removeProperty(key){delete this[key]}},
      setAttribute(key,value){this.attributes[key]=value},
      appendChild(child){child.parentNode=this;child.isConnected=true;this.children.push(child);return child},
      remove(){if(!this.parentNode)return;this.parentNode.children=this.parentNode.children.filter(x=>x!==this);this.parentNode=null;this.isConnected=false},
      insertAdjacentElement(position,child){assert.equal(position,'afterend');const p=this.parentNode;const idx=p.children.indexOf(this);p.children.splice(idx+1,0,child);child.parentNode=p;child.isConnected=true},
      querySelector(selector){return this.children.find(x=>selector==='.snapshot-bg'&&x.className==='snapshot-bg')||null}
    };
    Object.defineProperty(node,'src',{get(){return this._src||''},set(src){this._src=src}});
    Object.defineProperty(node,'currentSrc',{get(){return this._src||''}});
    return node;
  }
  const box=el(),oldBg=el('img');oldBg.className='snapshot-bg';oldBg.src=prefix+'a.webp';box.appendChild(oldBg);
  function Img(){this.onload=null;this.onerror=null;this.decode=()=>broken?Promise.reject(Error('decode')):Promise.resolve();}
  Object.defineProperty(Img.prototype,'src',{set(v){this._src=v;queueMicrotask(()=>{if(broken)this.onerror?.();else this.onload?.()})},get(){return this._src}});
  const env={Image:Img,document:{getElementById(id){return id==='snapshot'?box:null},createElement:el},
    BackroomSnapshotAssets:{resolve(s){return {nodeId:s.level.id,src:prefix+(s.frame||'a.webp')}}},
    matchMedia:()=>({matches:reduced}),setTimeout:(fn,delay)=>{const n=nextTimer++;timers.set(n,{fn,delay});return n},
    clearTimeout:n=>timers.delete(n)};
  env.window=env;
  vm.runInNewContext(script,env);
  function state(turn,step,frame='a.webp',other={}){
    return {turn,level:{id:'0'},flags:{exploration:{snapshotStep:step}},frame,...other};
  }
  function render(frame){box.children.forEach(x=>{x.isConnected=false;x.parentNode=null});box.children=[];const n=el('img');n.src=prefix+frame;n.className='snapshot-bg';box.appendChild(n);return n}
  const next=async()=>{await Promise.resolve();await Promise.resolve();await Promise.resolve()};
  function fire(ms){const entry=[...timers].find(([id,t])=>t.delay===ms);assert.ok(entry,'timer '+ms);timers.delete(entry[0]);entry[1].fn()}
  return {box,env,timers,state,render,fire,next,api:env.backroomExplorationTransition};
}

test('validated Explore stages the old image, advances Cao Minh and crossfades after preload',async()=>{
  const x=boot(),prev=x.api.capture(x.state(1,0)),newBg=x.render('b.webp');
  assert.equal(x.api.play(prev,x.state(2,1,'b.webp')),true);
  assert.equal(x.box.dataset.explorePhase,'prepare');
  assert.equal(x.box.children[1].className,'snapshot-explore-previous');
  assert.equal(x.box.children[1].src,prefix+'a.webp');
  await x.next();assert.equal(x.box.dataset.explorePhase,'advance');
  x.fire(560);assert.equal(x.box.dataset.explorePhase,'fade');
  x.fire(470);assert.equal(x.box.dataset.explorePhase,'settle');
  x.fire(270);assert.equal(x.box.dataset.explorePhase,undefined);
  assert.equal(x.box.children.length,1);assert.equal(x.box.children[0],newBg);
});

test('no frame change, a reload, and reduced motion render immediately',()=>{
  for(const opt of [{},{reduced:true}]){
    const x=boot(opt),before=x.api.capture(x.state(1,0));
    x.render('b.webp');
    assert.equal(x.api.play(before,x.state(opt.reduced?2:1,opt.reduced?1:0,'b.webp')),false);
    assert.equal(x.box.children.length,1);
  }
  const x=boot(),prev=x.api.capture(x.state(1,0));x.render('a.webp');
  assert.equal(x.api.play(prev,x.state(2,1,'a.webp')),false);
});

test('Entity encounters and combat never animate even on a new scene',()=>{
  for(const other of [
    {combat:{active:true}},
    {flags:{exploration:{snapshotStep:1},lastRolls:{entityEncounter:{eligible:true,success:true}}}},
    {flags:{exploration:{snapshotStep:1},entityEncounterKey:'hound'}},
    {_snapshotEvent:{kind:'ENTITY_CONFIRMED'}}
  ]){
    const x=boot(),prev=x.api.capture(x.state(1,0));x.render('b.webp');
    assert.equal(x.api.play(prev,x.state(2,1,'b.webp',other)),false);
    assert.equal(x.box.children.length,1);
  }
});

test('actor movement is based on real sprite position',()=>{
  const x=boot(),prior=x.api.capture(x.state(1,0));
  x.box.getBoundingClientRect=()=>({left:0,width:400});
  const actor={getBoundingClientRect:()=>({left:250,width:100})};
  const original=x.box.querySelector;
  x.box.querySelector=function(selector){
    return selector==='.snapshot-character'?actor:original.call(x.box,selector);
  };
  x.render('b.webp');
  assert.equal(x.api.play(prior,x.state(2,1,'b.webp')),true);
  assert.equal(x.box.style['--explore-advance-x'],'-92px');
});

test('new turn cancels a running transition and a late load cannot resurrect stale images',async()=>{
  const x=boot(),before=x.api.capture(x.state(1,0));x.render('b.webp');
  assert.equal(x.api.play(before,x.state(2,1,'b.webp')),true);
  x.api.capture(x.state(2,1,'b.webp'));
  await x.next();
  assert.equal(x.box.dataset.explorePhase,undefined);
  assert.equal(x.box.children.length,1);
  assert.equal(x.timers.size,0);
});

test('decode failure exits cleanly without leaving a hidden background',async()=>{
  const x=boot({broken:true}),prev=x.api.capture(x.state(1,0));x.render('b.webp');
  assert.equal(x.api.play(prev,x.state(2,1,'b.webp')),true);
  await x.next();await x.next();
  assert.equal(x.box.dataset.explorePhase,undefined);
  assert.equal(x.box.children.length,1);
});

test('unrelated Snapshot render during animation is detected and cleaned up',async()=>{
  const x=boot(),prev=x.api.capture(x.state(1,0));x.render('b.webp');
  assert.equal(x.api.play(prev,x.state(2,1,'b.webp')),true);
  await x.next();x.render('b.webp');x.fire(560);
  assert.equal(x.box.dataset.explorePhase,undefined);
  assert.equal(x.box.children.length,1);
});

test('Android render bridge hooks the final Snapshot and assets are loaded offline',()=>{
  const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  assert.ok(native.includes('cinema.capture(state)'));
  assert.ok(native.includes('cinema.play(previousScene,state)'));
  assert.ok(native.includes("if(typeof window!=='undefined'&&window.backroomExplorationTransition)window.backroomExplorationTransition.cancel()"));
  assert.ok(html.includes('href="exploration-transition.css"'));
  assert.ok(html.includes('src="exploration-transition.js"'));
});
