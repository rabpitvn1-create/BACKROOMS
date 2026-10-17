const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const assets=path.join(__dirname,'../app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const code=fs.readFileSync(path.join(assets,'diagnostic-export.js'),'utf8');

function boot(){
  const listeners={},elements={},exports=[];
  const ids=['exportDiagnosticButton','diagnosticSensitive','diagnosticExportStatus','status','log'];
  for(const id of ids)elements[id]={checked:false,hidden:false,textContent:'',innerText:'',
    addEventListener(name,fn){this[name]=fn;}};
  const env={
    document:{getElementById:id=>elements[id]||null,addEventListener(name,fn){listeners[name]=fn;}},
    addEventListener(name,fn){listeners[name]=fn;},
    navigator:{userAgent:'Android WebView',onLine:true},
    location:{protocol:'file:',host:''},
    localStorage:{getItem:()=>null},
    console:{warn(){},error(){}},
    Android:{currentClientState:()=>JSON.stringify({turn:9,log:[{text:'Core event'}]}),
      exportDiagnosticLog(json,raw){exports.push({data:JSON.parse(json),raw});return '{"ok":true}';}},
    state:{turn:8,log:[{text:'UI event'}],combat:{active:false}},
    Date
  };
  env.window=env;
  vm.runInNewContext(code,env);
  listeners.DOMContentLoaded();
  return {env,listeners,elements,exports};
}

test('Game Menu exports structured read-only evidence without milestone overlay',()=>{
  const x=boot();
  x.elements.exportDiagnosticButton.click();
  assert.equal(x.exports.length,1);
  assert.equal(x.exports[0].raw,false);
  assert.equal(x.exports[0].data.schema,'backroom.diagnostics.v1');
  assert.equal(x.exports[0].data.presentation.turn,8);
  assert.equal(x.exports[0].data.authoritativeCore.turn,9);
  assert.equal(x.env.state.turn,8,'diagnostics must not mutate gameplay');
  // Milestone overlay removed: no second export path, no milestone fields in snapshot.
  assert.equal(x.exports[0].data.milestoneBlocked,undefined);
  assert.equal(x.exports[0].data.milestoneVisible,undefined);
  assert.match(html,/id="exportDiagnosticButton"/);
  assert.match(html,/diagnostic-export\.js/);
});

test('Sensitive export is opt-in and requires a second direct click',()=>{
  const x=boot();
  x.elements.diagnosticSensitive.checked=true;
  x.elements.exportDiagnosticButton.click();
  assert.equal(x.exports.length,0);
  assert.match(x.elements.diagnosticExportStatus.textContent,/CẢNH BÁO/);
  x.elements.exportDiagnosticButton.click();
  assert.equal(x.exports.length,1);
  assert.equal(x.exports[0].raw,true);
});

test('JavaScript failures are captured and Android save failure is reported',()=>{
  const x=boot();
  x.listeners.error({message:'render failure',filename:'index.html',lineno:42,colno:3});
  x.elements.exportDiagnosticButton.click();
  assert.match(JSON.stringify(x.exports[0].data.javascriptEvents),/render failure/);
  x.env.backroomDiagnosticExportResult('{"saved":false,"error":"file_write_failed"}');
  assert.match(x.elements.diagnosticExportStatus.textContent,/file_write_failed/);
});

test('Android owns the document picker and native secret consent, not a network uploader',()=>{
  assert.match(native,/Intent\.ACTION_CREATE_DOCUMENT/);
  assert.match(native,/@JavascriptInterface public String exportDiagnosticLog/);
  assert.match(native,/DiagnosticLog\.build\(webJson, includeSensitive/);
  assert.match(native,/onActivityResult\(int requestCode/);
  assert.doesNotMatch(code,/fetch\(|XMLHttpRequest/);
});


test('post-combat diagnostics expose stale UI locks without changing state',()=>{
  const x=boot();
  x.env.busy=true;
  x.env.__combatFeedbackBusy=true;
  x.env.__combatDiceBusy=true;
  x.elements.submit={disabled:true};
  x.elements.action={readOnly:true};
  x.elements.pokerDiceModal={hidden:false};
  const before=JSON.stringify(x.env.state);
  const snapshot=x.env.BackroomDiagnostics.snapshot();
  assert.equal(snapshot.runtime.busy,true);
  assert.equal(snapshot.runtime.combatFeedbackBusy,true);
  assert.equal(snapshot.runtime.combatDiceBusy,true);
  assert.equal(snapshot.runtime.executeDisabled,true);
  assert.equal(snapshot.runtime.actionReadOnly,true);
  assert.equal(snapshot.runtime.pokerDiceHidden,false);
  assert.equal(JSON.stringify(x.env.state),before);
});
