import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const html = readFileSync(fileURLToPath(new URL('./app/src/main/assets/index.html', import.meta.url)), 'utf8');
assert.ok(!html.includes('SEMANTIC_COMPATIBILITY_TERMS'), 'no universal compatibility dictionary');
assert.ok(html.includes('.log .message.core .gm-semantic{font-weight:700}'),
  'Core-result semantic names must receive the same bold weight as GM narration');
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
    runtime:'world-graph-v3',
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
assert.equal(updated.log[0].semanticVersion,5);
assertTag(updated.log[0].semanticSpans.map(s=>({text:updated.log[0].text.slice(s.start,s.end),kind:s.kind})),'Iris','character');
assert.ok(typeof updated.log[0].semanticHash==='string','cache bound to source text');

const held=prepare(updated,{player,log:updated.log});
assert.deepEqual(held.log[0].semanticSpans,updated.log[0].semanticSpans,'previous annotations stay stable without changed text');
const edited=prepare(held,{player,log:[{...held.log[0],text:'Không có Iris ở đây.'}]});
assert.ok(edited.log[0].semanticSpans.every(s=>s.end<=edited.log[0].text.length),'changed text must invalidate offsets');
assert.equal(edited.log[0].semanticHash===held.log[0].semanticHash,false,'changed text invalidates hash');
assert.equal(prepare(before,{player,log:[{role:'player',text:'Iris'}]}).log[0].semanticSpans,undefined,'player text is not highlighted');

console.log('Core-authoritative semantic highlighting, 6 categories, Unicode, cache and negative cases verified');

// Confirm all remaining Core projections are recognized without text guessing.
const sceneNpc={
  player,scene:{runtime:'scripted-mini-map',npcs:[
    {id:'npc_01',name:'Bà Quản Kho',originPeriod:'1980',role:'witness'}
  ],records:[]}
};
assertTag(spans('Bà Quản Kho cảnh báo Cao Minh.',sceneNpc),'Bà Quản Kho','character');
assert.equal(spans('Bà Quản Kho',{player,scene:{npcs:[]}}).length,0,'NPC outside current scene stays unrecognized');

const itemAbilities={
  player,partyDetails:{members:[{
    name:'Seth',
    inventory:[{name:'Bản đồ cũ',abilities:[{name:'Dò Dấu Vết'}]}],
    equipmentItems:[{name:'White Wraith Magnum',abilities:[{name:'Phân Tích Ma Lực'}]}]
  }]},
  equipment:{weapon:{id:'magnum',name:'Súng Dự Phòng',abilities:[{name:'Chế Độ Bộc Phát'}]}}
};
const nested=spans('Seth dùng Dò Dấu Vết, Phân Tích Ma Lực, Chế Độ Bộc Phát và White Wraith Magnum.',itemAbilities);
for(const name of ['Dò Dấu Vết','Phân Tích Ma Lực','Chế Độ Bộc Phát'])
  assertTag(nested,name,'skill');
assertTag(nested,'White Wraith Magnum','item');

const questItems={
  player,gmQuestItems:{version:1,items:[
    {id:'quest_held',name:'Mảnh Chìa Khóa Đồng',status:'held'},
    {id:'quest_consumed',name:'Mảnh Giấy Đã Đốt',status:'consumed'}
  ]}
};
assertTag(spans('Cao Minh cất Mảnh Chìa Khóa Đồng.',questItems),'Mảnh Chìa Khóa Đồng','item');
assert.equal(spans('Mảnh Giấy Đã Đốt',questItems).length,0,'consumed GM prop not available for new text');

const miniScene={
  player,scene:{runtime:'scripted-mini-map',records:[
    {kind:'CLUE',id:'a',label:'Dấu Chân Bùn',confirmed:true},
    {kind:'HINT',id:'b',label:'Ký Hiệu Mất Nét',confirmed:true},
    {kind:'EVIDENCE',id:'c',label:'Vết Nứt Ẩm',confirmed:true},
    {kind:'CLUE',id:'d',label:'Cánh Cửa Bí Mật',confirmed:false},
    {kind:'EVIDENCE',id:'e',label:'Lời Đoán Vô Căn Cứ',confirmed:false},
    {kind:'EVIDENCE',id:'f',label:'Bằng Chứng Thiếu Xác Nhận'}
  ]}
};
const mini=spans('Dấu Chân Bùn, Ký Hiệu Mất Nét và Vết Nứt Ẩm.',miniScene);
for(const name of ['Dấu Chân Bùn','Ký Hiệu Mất Nét','Vết Nứt Ẩm'])
  assertTag(mini,name,'clue');
for(const name of ['Cánh Cửa Bí Mật','Lời Đoán Vô Căn Cứ','Bằng Chứng Thiếu Xác Nhận'])
  assert.equal(spans(name,miniScene).length,0,'unconfirmed clue must not be promoted: '+name);

// Scene transfer compacts logs. Entry position must never decide whether it's old.
const compactBefore={player,partyDetails:{members:[{name:'Iris'}]},log:[
  {role:'gm',text:'Cảnh thứ nhất'},{role:'gm',text:'Cảnh thứ hai'},{role:'gm',text:'Cảnh thứ ba'}
]};
const compactAfter={player,partyDetails:{members:[]},log:[{role:'gm',text:'Iris biến mất ở lối cũ.'}]};
const compact=prepare(compactBefore,compactAfter);
assertTag(compact.log[0].semanticSpans.map(x=>({
  text:compact.log[0].text.slice(x.start,x.end),kind:x.kind
})),'Iris','character');

const miniSource = readFileSync(fileURLToPath(new URL(
  './app/src/main/java/com/rabpit/backroom/core/MiniMapRuntime.kt',import.meta.url)), 'utf8');
assert.ok(miniSource.includes('displayed.put("confirmed", confirmed)'),
  'Core must distinguish failed attempts before semantic recognition');
assert.ok(miniSource.includes('displayed.put("label", action.getString("label"))'),
  'Core must supply authorized label for confirmed mini-map clues');

const worldSource = readFileSync(fileURLToPath(new URL(
  './app/src/main/java/com/rabpit/backroom/core/WorldGraphRuntime.kt',import.meta.url)), 'utf8');
assert.ok(worldSource.includes('put("label", source.label)'),
  'World Graph must project authored confirmed evidence names');

const synonymState={player,scene:{runtime:'world-graph-v3',records:[
  {kind:'EVIDENCE',label:'Giọt nước từ trần',aliases:['giọt nước rơi từ trần']}
]}};
assertTag(spans('Những giọt nước rơi từ trần tạo thành một vệt.',synonymState),'giọt nước rơi từ trần','clue');
assert.equal(spans('giọt nước rơi từ trần',{player,scene:{runtime:'world-graph-v3',records:[]}}).length,0,
  'authored alias cannot highlight before discovery');

const crossKind={player,scene:{runtime:'world-graph-v3',
  environment:{name:'Phòng Đảo'},records:[{kind:'EVIDENCE',label:'Phòng Đảo'}]}};
const resolved=spans('Phòng Đảo',crossKind);
assert.equal(resolved.length,1,'cross-category names should not be duplicated');
assertTag(resolved,'Phòng Đảo','location');

const worldClueSource=readFileSync(fileURLToPath(new URL(
  './app/src/main/java/com/rabpit/backroom/core/Level0WorldDefinition.kt',import.meta.url)), 'utf8');
assert.ok(worldClueSource.includes('listOf("giọt nước rơi từ trần")'),
  'Level 0 alias must be grounded in actual authored Canon');

console.log('Semantic pipeline verified: Core sources, six kinds, scene transfer, Unicode and rejection rules');
