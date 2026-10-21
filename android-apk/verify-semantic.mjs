import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const html = readFileSync(fileURLToPath(new URL('./app/src/main/assets/index.html', import.meta.url)), 'utf8');
assert.ok(!html.includes('SEMANTIC_COMPATIBILITY_TERMS'), 'no universal compatibility dictionary');
const start = html.indexOf('const SEMANTIC_KINDS=');
const end = html.indexOf('\nfunction levelHeader(', start);
assert.ok(start >= 0 && end > start, 'semantic functions present');
const ctx = vm.createContext({});
vm.runInContext(html.slice(start, end), ctx, {timeout:1000});

function evalJson(expression) {
  return JSON.parse(vm.runInContext('JSON.stringify('+expression+')', ctx, {timeout:1000}));
}
function spans(text, before, after=before) {
  ctx.sampleText=text;ctx.beforeState=before;ctx.afterState=after;
  return evalJson('semanticResolve(sampleText,beforeState,afterState).map(s=>({text:sampleText.slice(s.start,s.end),kind:s.kind,start:s.start,end:s.end}))');
}
function assertTag(result,text,kind) {
  assert.ok(result.some(s=>s.text===text&&s.kind===kind), 'missing '+kind+' '+text);
}
function prepare(before,after) {
  ctx.before=before;ctx.after=after;
  return evalJson('semanticPrepareNextState(before,JSON.stringify(after))');
}
const player={name:'Cao Minh'};
const coreState={
  player,
  partyDetails:{members:[{name:'Iris',inventory:[{name:'Băng gạc'}],skills:[{name:'Huyết Ma Tứ Liên'}]}]},
  combat:{entityName:'Hound'}
};
const base='Iris dùng Băng gạc, đánh Hound bằng Huyết Ma Tứ Liên';
const active=spans(base,coreState);
for(const [name,kind] of [
  ['Iris','character'],['Băng gạc','item'],['Hound','entity'],['Huyết Ma Tứ Liên','skill']
])assertTag(active,name,kind);
assert.equal(spans('Hound',null).length,0,'unknown entity cannot be globally matched');

const explored={
  ...coreState,level:{name:'Level 0'},
  scene:{
    environment:{id:'phong_ngap',name:'Phòng Ngập'},
    room:{name:'Phòng Ngập, lối tây'},
    map:{rooms:[
      {name:'Vũng Đen',visited:true},
      {name:'Căn Phòng Bí Mật',visited:false}
    ]},
    records:[
      {kind:'EVIDENCE',id:'wet:floor',label:'Vết kéo lê',text:'Quan sát đã được Core xác nhận.'},
      {kind:'FIELD_NOTE',id:'phong_ngap',label:'Kết cấu nền',text:'Ghi chép đã được Core xác nhận.'},
      {kind:'OBSERVATION',id:'other',label:'Bí mật cuối cùng'}
    ]
  }
};
const world=spans('Cao Minh đến Phòng Ngập rồi ghi Vết kéo lê tại Vũng Đen và Kết cấu nền.',explored);
for(const [name,kind] of [
  ['Cao Minh','character'],['Phòng Ngập','location'],['Vết kéo lê','clue'],
  ['Vũng Đen','location'],['Kết cấu nền','clue']
])assertTag(world,name,kind);
assert.equal(spans('Căn Phòng Bí Mật',explored).length,0,'unvisited rooms must stay hidden');
assert.equal(spans('Bí mật cuối cùng',explored).length,0,'unverified notes are not clues');
assert.equal(spans('Vết kéo lê',{player,scene:{records:[]}}).length,0,'unconfirmed clue must not be highlighted');
assert.equal(spans('Phòng Ngập',{player}).length,0,'location cannot be guessed from capitalization');

for(const input of [
  'Một thực thể mang tên Hollow Beast vừa xuất hiện.',
  'Một kỹ năng có tên Lưỡi Gió vô cùng đáng sợ.',
  'Một nhân vật tên Phòng Ngập bước ra từ bóng tối.'
])assert.equal(spans(input,{player}).length,0,'unverified proper noun must not be typed');

const outside=spans('Irisland không phải Iris, Iris đã quay lại.',coreState);
assert.deepEqual(outside.filter(s=>s.text==='Iris').map(s=>s.start),[20,26],'word boundaries must exclude substrings');
const decomposed='Ba\u0306ng ga\u0323c';
assertTag(spans('Iris giữ '+decomposed,coreState),decomposed,'item');

const overlap={player:{name:'Minh'},partyDetails:{members:[{name:'Cao Minh'}]}};
assertTag(spans('Cao Minh',overlap),'Cao Minh','character');
assert.equal(spans('Cao Minh',overlap).length,1,'longest match must win overlap');

const before={player,log:[]};
const stale={...coreState,log:[{role:'gm',text:'Iris nhận ra Hound',semanticVersion:3,semanticSpans:[{start:0,end:4,kind:'item'}]}]};
const updated=prepare(before,stale);
assert.equal(updated.log[0].semanticVersion,4);
assertTag(updated.log[0].semanticSpans.map(s=>({text:updated.log[0].text.slice(s.start,s.end),kind:s.kind})),'Iris','character');
assert.ok(typeof updated.log[0].semanticHash==='string','cache bound to source text');

const held=prepare(updated,{player,log:updated.log});
assert.deepEqual(held.log[0].semanticSpans,updated.log[0].semanticSpans,'previous annotations stay stable without changed text');
const edited=prepare(held,{player,log:[{...held.log[0],text:'Không có Iris ở đây.'}]});
assert.ok(edited.log[0].semanticSpans.every(s=>s.end<=edited.log[0].text.length),'changed text must invalidate offsets');
assert.equal(edited.log[0].semanticHash===held.log[0].semanticHash,false,'changed text invalidates hash');
assert.equal(prepare(before,{player,log:[{role:'player',text:'Iris'}]}).log[0].semanticSpans,undefined,'player text is not highlighted');

console.log('Core-authoritative semantic highlighting, 6 categories, Unicode, cache and negative cases verified');
