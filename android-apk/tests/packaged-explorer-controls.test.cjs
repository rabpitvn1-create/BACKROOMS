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
  for(const file of ['index.html','explorer-controls.js','explorer-controls.css','ui/ornate-frame-v1.css','ui/backrooms-reference-frame-v2.png','ui/industrial-crt-v1.css',
    'ui/industrial-crt-v1/panel-frame.webp','ui/industrial-crt-v1/button-steel.webp',
    'ui/industrial-crt-v1/button-hazard.webp','ui/industrial-crt-v1/surface-crt.webp','ui/industrial-crt-v1/paper-dossier.webp']){
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
