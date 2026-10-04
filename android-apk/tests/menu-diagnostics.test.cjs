const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/index.html'), 'utf8');
function harness(android) {
  const nodes = new Map();
  const document = {
    getElementById(id) {
      if (!nodes.has(id)) nodes.set(id, {id, textContent:'', hidden:true, disabled:false, value:'', style:{setProperty(){}}, addEventListener(){}});
      return nodes.get(id);
    },
    documentElement:{style:{setProperty(){}}},
    createElement(){return {set textContent(s){this.innerHTML=String(s)}}},
    addEventListener(){}
  };
  const window = {innerHeight:800, addEventListener(){}, Android:android};
  const context = vm.createContext({window,document,Android:android,console,localStorage:{removeItem(){}},confirm:()=>true});
  vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], context);
  return {context,nodes};
}
test('menu formats current level, unknown position, AI mode and Cao Minh title without modifying Core state',()=>{
  const {context,nodes}=harness();
  assert.equal(nodes.get('location').textContent,'Level 0 - The Lobby • Chưa xác định vị trí.');
  vm.runInContext('state.mode="ai";render()',context);
  assert.equal(nodes.get('mode').textContent,'Artificial Intelligence Mode');
  assert.equal(nodes.get('player').textContent,'Cao Minh - Vạn Giới Ma Tôn');
  assert.equal(vm.runInContext('state.location===initial.location',context),true);
  assert.equal(vm.runInContext('menuLocation({currentLevel:2,currentLevelKey:"2",location:"Phòng máy"})',context),'Level 2 - Pipe Dreams • Phòng máy.');
  assert.equal(vm.runInContext('menuLocation({currentLevel:0,currentLevelKey:"0.1",location:""})',context),'Level 0.1 - Zenith Station • Chưa xác định vị trí.');
  assert.equal(vm.runInContext('menuCharacter({player:{name:"Lucia"}})',context),'Lucia');
});
test('export invokes native diagnostic export and restores button on result, cancel or synchronous failure',()=>{
  let exports=0;
  const {context,nodes}=harness({exportDiagnosticLog(){exports++}});
  vm.runInContext('exportDiagnosticLog()',context);
  assert.equal(exports,1);
  assert.equal(nodes.get('exportLog').disabled,true);
  vm.runInContext('window.backroomLogExportStatus("Đã hủy xuất LOG.")',context);
  assert.equal(nodes.get('exportLog').disabled,false);
  assert.equal(nodes.get('logExportStatus').hidden,false);
  context.Android.exportDiagnosticLog=()=>{throw Error('picker failure')};
  vm.runInContext('exportDiagnosticLog()',context);
  assert.equal(nodes.get('exportLog').disabled,false);
  assert.equal(nodes.get('logExportStatus').textContent,'picker failure');
});
test('browser fallback gives actionable feedback instead of pretending export succeeded',()=>{
  const {context,nodes}=harness();
  vm.runInContext('exportDiagnosticLog()',context);
  assert.match(nodes.get('logExportStatus').textContent,/ứng dụng Android/);
});
