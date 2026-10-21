const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const dir=__dirname;
const java=fs.readFileSync(path.join(dir,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const line=java.split('\n').find(row=>row.includes('"function appendEntityOverlay()'));
assert.ok(line,'Android snapshot Entity renderer must exist');
const literal=line.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/);
assert.ok(literal,'Embedded Entity renderer must remain parseable');
const renderer=JSON.parse(literal[1]);
const assets=path.join(dir,'../app/src/main/assets');
const supported=['926','hound','clump','duller','deathmoth','hostile_faceling','false_puddle',
 'paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch',
 'cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer',
 'jane_the_killer','slenderman','diep_minh','blackroot_sentinel','sinew_strider','hollow_grasper'];

function runtime(key){
 let children=[],syncCalls=0,bridgeCalls=0;
 const box={
  style:{},
  querySelector(selector){return children.find(c=>'.'+c.className===selector)||null},
  appendChild(img){children.push(img);return img}
 };
 const context={
  state:{combat:{active:true,entityKey:key}},
  __entityOverlay:{key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''},
  document:{getElementById(id){return id==='snapshot'?box:null},createElement(){
   return {style:{},dataset:{},isConnected:true,remove(){this.isConnected=false;children=children.filter(c=>c!==this)}}}
  },
  activeEntityKey(){return context.state.combat&&context.state.combat.active?context.state.combat.entityKey:''},
  normalizeEntityVisual(img){img.style.transform='translate(5px,6px) scale(1.2)'},
  requestEntityOverlay(){bridgeCalls++}
 };
 context.window=context;
 context.backroomSyncCombatPortrait=()=>{syncCalls++};
 vm.createContext(context);
 vm.runInContext(renderer,context);
 return {context,box,imgs:()=>children,bridgeCalls:()=>bridgeCalls,syncCalls:()=>syncCalls};
}

test('Entity overlay mounts without waiting for asynchronous Android bridge on every supported encounter',()=>{
 for(const key of supported){
  const r=runtime(key);
  r.context.appendEntityOverlay();
  const img=r.imgs().find(x=>x.className==='snapshot-entity');
  assert.ok(img,key+' overlay should be mounted at combat entry');
  assert.equal(r.bridgeCalls(),0,'local bundled asset should not require async bridge');
  assert.equal(img.src,'file:///android_asset/entity/'+key+'.webp');
  assert.equal(img.style.opacity,'0');
  assert.ok(fs.existsSync(path.join(assets,'entity',key+'.webp')),key+' image asset missing');
  img.onload();
  assert.equal(img.style.opacity,'1');
  assert.equal(img.dataset.entityStatus,'ready');
  assert.equal(img.style.transform,'translate(5px,6px) scale(1.2)');
  assert.equal(r.syncCalls(),1,'image load should synchronize current turn portrait');
 }
});

test('Entity overlay moves with a new encounter and disappears only after combat becomes inactive',()=>{
 const r=runtime('hound');
 r.context.appendEntityOverlay();
 assert.equal(r.imgs().length,1);
 r.context.state.combat.entityKey='smiler';
 r.context.appendEntityOverlay();
 assert.equal(r.imgs().length,1);
 assert.equal(r.imgs()[0].src,'file:///android_asset/entity/smiler.webp');
 r.context.state.combat.active=false;
 r.context.appendEntityOverlay();
 assert.equal(r.imgs().length,0);
});

test('direct Entity overlay URL derives only from validated encounter keys',()=>{
 assert.match(java,/function normalizeEntityKey\(v\)/);
 assert.match(renderer,/__entityOverlay\.url='file:\/\/\/android_asset\/entity\/'\+key\+'\.webp'/);
 assert.doesNotMatch(renderer,/requestEntityOverlay\(key\);return/);
});

test('Entity image decode failure is not reported as ready and shows a visible fallback label',()=>{
 const r=runtime('hound');
 r.context.appendEntityOverlay();
 const img=r.imgs()[0];
 img.onerror();
 assert.equal(img.dataset.entityStatus,'error');
 assert.equal(img.style.opacity,'0');
 assert.ok(r.imgs().some(n=>n.className==='snapshot-entity-error'));
 assert.equal(r.syncCalls(),0);
});
test('stale Entity image completion cannot modify new wave visual state',()=>{
 const r=runtime('hound');
 r.context.appendEntityOverlay();
 const old=r.imgs()[0];
 r.context.state.combat.entityKey='smiler';
 r.context.appendEntityOverlay();
 old.onload();
 assert.equal(r.syncCalls(),0);
 r.imgs()[0].onload();
 assert.equal(r.syncCalls(),1);
});

test('Entity 926 has grounded alpha bounds from its bundled transparent WebP',()=>{
 const parts=java.split('\n').filter(row=>row.includes('__entityVisualBounds=')||row.includes('function normalizeEntityVisual('))
  .map(row=>JSON.parse(row.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/)[1]));
 const ctx={__entityOverlay:{key:'926'}};
 vm.createContext(ctx);vm.runInContext(parts.join('\n'),ctx);
 // 926.webp 1086x1448, alpha >=16 bbox (323,5)-(1006,1438).
 for(const snapshotHeight of [200,300,450]){
  const h=snapshotHeight*.97,w=h*1086/1448;
  const img={alt:'926',style:{},getBoundingClientRect:()=>({width:w,height:h})};
  ctx.normalizeEntityVisual(img);
  const [,x,y,scale]=img.style.transform.match(/translate\(([-\d.]+)px,([-\d.]+)px\) scale\(([-\d.]+)\)/).map(Number);
  assert.ok(Math.abs(((1438-5)/1448)*h*scale-snapshotHeight*.94)<.5);
  assert.ok(Math.abs(y-(1-1438/1448)*h*scale)<.5);
  assert.ok(Math.abs(x+(323/1086)*w*scale)<.5);
 }
});

test('Hound alpha normalization fits its current artwork and keeps its feet on the ground',()=>{
 const parts=java.split('\n').filter(row=>row.includes('__entityVisualBounds=')||row.includes('function normalizeEntityVisual('))
  .map(row=>JSON.parse(row.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/)[1]));
 const ctx={__entityOverlay:{key:'hound'}};
 vm.createContext(ctx);vm.runInContext(parts.join('\n'),ctx);
 // Current hound.webp: 941x1672, alpha > 16 bbox (12,199)-(941,1556).
 // Use the decoded artwork dimensions, not the normalizer's own metadata.
 const alpha={left:12/941,bottom:1556/1672,height:(1556-199)/1672};
 for(const snapshotHeight of [200,300,450]){
  const h=snapshotHeight*.97,w=h*941/1672;
  const img={alt:'hound',style:{},getBoundingClientRect:()=>({width:w,height:h})};
  ctx.normalizeEntityVisual(img);
  const match=img.style.transform.match(/translate\(([-\d.]+)px,([-\d.]+)px\) scale\(([-\d.]+)\)/);
  assert.ok(match);
  const [,x,y,scale]=match.map(Number);
  assert.ok(Math.abs(alpha.height*h*scale-snapshotHeight*.94)<.5,'visible Hound must fit inside Snapshot');
  assert.ok(Math.abs(y-(1-alpha.bottom)*h*scale)<.5,'feet must meet the ground');
  assert.ok(Math.abs(x+alpha.left*w*scale)<.5,'visible left edge must stay anchored');
 }
});
