import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const dir=path.dirname(fileURLToPath(import.meta.url));
const java=fs.readFileSync(path.join(dir,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const line=java.split('\n').find(row=>row.includes('"function appendEntityOverlay()'));
assert.ok(line,'Android snapshot Entity renderer must exist');
const literal=line.match(/^\s*("(?:[^"\\]|\\.)*")\s*\+/);
assert.ok(literal,'Embedded Entity renderer must remain parseable');
const renderer=JSON.parse(literal[1]);
const assets=path.join(dir,'../app/src/main/assets');
const supported=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle',
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
   return {style:{},remove(){children=children.filter(c=>c!==this)}}}
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
