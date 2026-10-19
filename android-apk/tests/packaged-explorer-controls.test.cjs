const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const source=path.join(__dirname,'../app/src/main/assets');
const verifier=path.join(__dirname,'../verify-explorer-controls.mjs');

function fixture(t){
  const assets=fs.mkdtempSync(path.join(os.tmpdir(),'packaged-composer-'));
  t.after(()=>fs.rmSync(assets,{recursive:true,force:true}));
  for(const file of ['index.html','explorer-controls.js','explorer-controls.css','ui/ornate-frame-v1.css','ui/backrooms-reference-frame-v2.png','ui/industrial-crt-v1.css','ui/snapshot-tv.js','ui/snapshot-crt.css','ui/crt-glass-v5.svg',
    'ui/industrial-crt-v1/panel-frame.webp','ui/industrial-crt-v1/button-steel.webp',
    'ui/industrial-crt-v1/button-hazard.webp','ui/industrial-crt-v1/surface-crt.webp','ui/industrial-crt-v1/paper-dossier.webp',
    'ui/industrial-crt-v1/crt-glass-v3.webp','ui/industrial-crt-v1/tracking-v3.webp']){
    fs.mkdirSync(path.dirname(path.join(assets,file)),{recursive:true});
    fs.copyFileSync(path.join(source,file),path.join(assets,file));
  }
  return assets;
}
const verify=assets=>spawnSync(process.execPath,[verifier,assets],{encoding:'utf8'});

test('APK Explorer contract accepts the typed controls',t=>{
  const result=verify(fixture(t));
  assert.equal(result.status,0,result.stderr);
  assert.match(result.stdout,/Packaged Explorer controls verified/);
});
test('APK Explorer contract rejects a missing external controller',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'explorer-controls.js'));
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/explorer-controls.js/);
});
test('APK Explorer contract rejects stale packaged styles',t=>{
  const assets=fixture(t);fs.appendFileSync(path.join(assets,'explorer-controls.css'),'\n/* stale payload */');
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/differs from source: explorer-controls.css/);
});

test('APK Explorer contract rejects a missing reference frame PNG',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'ui/backrooms-reference-frame-v2.png'));
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/backrooms-reference-frame-v2.png/);
});

test('APK contract rejects a missing generated industrial surface',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'ui/industrial-crt-v1/button-hazard.webp'));
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/button-hazard.webp/);
});
test('APK contract rejects a stale industrial theme stylesheet',t=>{
  const assets=fixture(t);fs.appendFileSync(path.join(assets,'ui/industrial-crt-v1.css'),'\n/* stale theme */');
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/differs from source: ui\/industrial-crt-v1.css/);
});

test('menu mirrors Core action confirmation/errors without changing menu or action handlers',()=>{
  const vm=require('node:vm'),html=fs.readFileSync(path.join(source,'index.html'),'utf8');
  const start=html.indexOf('(function(){\n  "use strict";\n  var menu=');
  const code=html.slice(start,html.indexOf('</script>',start));
  let update;
  const nodes={};for(const id of ['gameMenuModal','gameMenuOpen','gameMenuFeedback','status','action','gameMenuClose','gameMenuBackdrop']){
    nodes[id]={hidden:false,textContent:'',listeners:{},setAttribute(k,v){this[k]=v;},addEventListener(k,fn){this.listeners[k]=fn;},blur(){}};
  }
  const ctx={document:{getElementById:id=>nodes[id],body:{classList:{add(){},remove(){}}},addEventListener(){}},
    MutationObserver:class {constructor(fn){update=fn;}observe(node,options){assert.equal(node,nodes.status);assert.ok(options.childList&&options.characterData);}}};
  vm.runInNewContext(code,ctx);
  assert.equal(nodes.gameMenuFeedback.hidden,true);
  nodes.status.textContent='Nhấn "Bắt đầu lại từ đầu" lần nữa trong 5 giây để xác nhận.';update();
  assert.equal(nodes.gameMenuFeedback.textContent,nodes.status.textContent);assert.equal(nodes.gameMenuFeedback.hidden,false);
  nodes.gameMenuOpen.listeners.click();assert.equal(nodes.gameMenuModal.hidden,false);
  nodes.status.textContent='Core reset thất bại; giữ nguyên lượt hiện tại.';update();
  assert.equal(nodes.gameMenuFeedback.textContent,nodes.status.textContent);
  nodes.gameMenuClose.listeners.click();assert.equal(nodes.gameMenuModal.hidden,true);
  nodes.status.textContent='';update();assert.equal(nodes.gameMenuFeedback.hidden,true);
});

test('TV HUD survives native snapshot replacement once without touching state, Core or callbacks',()=>{
  const vm=require('node:vm'),script=fs.readFileSync(path.join(source,'ui/snapshot-tv.js'),'utf8');
  let changed,observed,now=0;const intervals=[];
  const box={children:[],querySelector(){return this.children.find(n=>n.className==='snapshot-tv-hud')||null;},appendChild(node){this.children.push(node);}};
  const state={turn:12,log:[{role:'player',text:'Quan sát hành lang.'}],scene:{room:{id:'current'}}};
  const turn=()=>{};
  const ctx={state,backroomTurn:turn,Android:new Proxy({},{get(){throw Error('decorative HUD must not use Core bridge');}}),
    document:{getElementById:id=>id==='snapshot'?box:null,body:{classList:{contains:()=>true}},createElement:()=>({slots:{},setAttribute(k,v){this[k]=v;},querySelector(k){return this.slots[k]||(this.slots[k]={textContent:''});}})},
    performance:{now:()=>now},setInterval:(fn,delay)=>intervals.push({fn,delay}),
    MutationObserver:class {constructor(fn){changed=fn;}observe(node,opts){observed=node;assert.deepEqual(Object.keys(opts),['childList']);}}};
  const before=JSON.stringify(state);vm.runInNewContext(script,ctx);
  assert.equal(observed,box);assert.equal(box.children.length,1);assert.equal(box.children[0]['aria-hidden'],'true');
  assert.match(box.children[0].innerHTML,/PLAY|REC/);
  changed();assert.equal(box.children.length,1,'observer sees its own HUD append but must not duplicate');
  for(let i=0;i<3;i++){
    box.children=[{className:'snapshot-bg'},{className:'snapshot-character'}];changed();changed();
    assert.equal(box.children.length,3);assert.equal(box.children.filter(n=>n.className==='snapshot-tv-hud').length,1);
  }
  assert.equal(intervals.length,1,'one display clock per page, not per native redraw');
  assert.equal(intervals[0].delay,1000);now=3661000;intervals[0].fn();
  assert.equal(box.children[2].slots['[data-tv-tape-time]'].textContent,'61:01');
  assert.equal(box.children[2].slots['[data-tv-view-time]'].textContent,'01:01:01');
  assert.equal(JSON.stringify(state),before);assert.equal(ctx.backroomTurn,turn);
});

test('APK contract rejects missing TV controller',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'ui/snapshot-tv.js'));
  const result=verify(assets);assert.notEqual(result.status,0);assert.match(result.stderr,/snapshot-tv.js/);
});

test('APK contract rejects missing CRT tracking texture',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'ui/industrial-crt-v1/tracking-v3.webp'));
  const result=verify(assets);assert.notEqual(result.status,0);assert.match(result.stderr,/tracking-v3.webp/);
});

test('CRT correction stays scoped to snapshot and uses a bowed transparent aperture',()=>{
  const css=fs.readFileSync(path.join(source,'ui/snapshot-crt.css'),'utf8');
  assert.doesNotMatch(css,/@font-face|\.topbar|\.log\b|\.message\b|#explorerControls|#prologue/);
  const svg=fs.readFileSync(path.join(source,'ui/crt-glass-v5.svg'),'utf8');
  assert.match(svg,/fill-rule="evenodd"/);
  const aperture=svg.match(/<path id="aperture" d="([^"]+)"/)[1];
  const commands=aperture.match(/[MC][^MCZ]+/g).map(c=>c.slice(1).trim().split(/\s+/).map(Number));
  let start=commands[0];const edges=[];
  for(const c of commands.slice(1)){
    assert.equal(c.length,6);const end=c.slice(4);
    edges.push({start,end,mid:[(start[0]+3*c[0]+3*c[2]+end[0])/8,(start[1]+3*c[1]+3*c[3]+end[1])/8]});start=end;
  }
  assert.equal(edges.length,8);
  assert.ok(edges[0].mid[1]<Math.min(edges[0].start[1],edges[0].end[1])-1,'top bows upward');
  assert.ok(edges[2].mid[0]>Math.max(edges[2].start[0],edges[2].end[0])+1,'right bows outward');
  assert.ok(edges[4].mid[1]>Math.max(edges[4].start[1],edges[4].end[1])+1,'bottom bows downward');
  assert.ok(edges[6].mid[0]<Math.min(edges[6].start[0],edges[6].end[0])-1,'left bows outward');
  assert.ok(svg.includes('d="M0 0H1000V724H0Z '+aperture+'"'),'opaque matte uses the same aperture');
  assert.doesNotMatch(svg,/<image\b|<text\b|<script\b|https?:\/\/(?!www\.w3\.org)/);
});

test('APK contract rejects a missing curved CRT aperture',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'ui/crt-glass-v5.svg'));
  const result=verify(assets);assert.notEqual(result.status,0);assert.match(result.stderr,/crt-glass-v5.svg/);
});
