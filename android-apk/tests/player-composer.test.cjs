const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assets=path.join(__dirname,'../app/src/main/assets');
const source=fs.readFileSync(path.join(assets,'player-composer.js'),'utf8');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');

function runtime(){
  const root={style:{setProperty(k,v){this[k]=v;}}};
  function element(){return {style:{},dataset:{},listeners:{},value:'',scrollHeight:42,
    addEventListener(k,fn){(this.listeners[k]||=[]).push(fn);},
    fire(k,event={}){for(const fn of this.listeners[k]||[])fn(event);},blur(){document.activeElement=null;}};}
  const action=element(),send=element(),form=element(),bar=element();
  const elements={action,submit:send,form,playerActionBar:bar};
  bar.getBoundingClientRect=()=>({height:parseFloat(action.style.height)+30});
  let requests=0;
  form.requestSubmit=()=>{requests++;form.fire('submit',{preventDefault(){}});};
  const classes=new Set();
  const document={documentElement:root,activeElement:null,getElementById:id=>elements[id],
    body:{classList:{toggle(k,on){if(on)classes.add(k);else classes.delete(k);}}}};
  const calls=[],errors=[];
  const ctx={document,state:{combat:{active:false}},busy:false,coreUnavailable:false,innerHeight:900,
    visualViewport:{height:900,addEventListener(){}},addEventListener(){},
    formEl:form,actionEl:action,statusEl:{},appendActionPending(){},
    semanticPrepareNextState:(_,json)=>JSON.parse(json),persistPresentation:()=>false,render(){},focusLatestGmStart(){}};
  ctx.window=ctx;
  ctx.Android={submitAction:(s,k,a)=>calls.push({state:JSON.parse(s),kind:k,text:a})};
  ctx.syncPrimaryActions=()=>ctx.backroomSyncComposer();
  ctx.backroomError=reason=>{errors.push(reason);ctx.busy=false;ctx.syncPrimaryActions();};
  vm.createContext(ctx);vm.runInContext(source,ctx);
  const submit=html.match(/formEl.addEventListener\("submit",e=>\{[\s\S]*?\n\}\);/);
  assert.ok(submit);vm.runInContext(submit[0],ctx);
  const turn=html.split('\n').find(l=>l.startsWith('window.backroomTurn=json=>'));
  vm.runInContext(turn,ctx);
  return {ctx,action,send,form,root,document,classes,calls,errors,requests:()=>requests,
    sync:()=>ctx.backroomSyncComposer(),submit:()=>form.fire('submit',{preventDefault(){}})};
}

test('empty/whitespace input cannot send; growing and deleting text resizes and caps the composer',()=>{
  const r=runtime();assert.equal(r.send.disabled,true);assert.equal(r.action.style.height,'42px');
  r.action.value='  ';r.action.fire('input');assert.equal(r.send.disabled,true);
  r.action.value='Quan sát';r.action.scrollHeight=108;r.action.fire('input');
  assert.equal(r.send.disabled,false);assert.equal(r.action.style.height,'108px');
  r.action.scrollHeight=500;r.action.fire('input');
  assert.equal(r.action.style.height,'176px');assert.equal(r.action.style.overflowY,'auto');
  r.action.value='';r.action.scrollHeight=42;r.action.fire('input');
  assert.equal(r.action.style.height,'42px');assert.equal(r.action.style.overflowY,'hidden');
});

test('sending uses EXECUTE once, retains draft until Core confirms, then clears and shrinks',()=>{
  const r=runtime();r.action.value='  Kiểm tra cửa  ';r.action.scrollHeight=108;r.sync();
  r.submit();r.submit();assert.equal(r.calls.length,1);assert.equal(r.calls[0].kind,'EXECUTE');
  assert.equal(r.calls[0].text,'Kiểm tra cửa');assert.equal(r.action.value,'  Kiểm tra cửa  ');
  assert.equal(r.action.readOnly,true);assert.equal(r.send.disabled,true);
  r.action.scrollHeight=42;r.ctx.backroomTurn('{"turn":2,"combat":{"active":false}}');
  assert.equal(r.action.value,'');assert.equal(r.action.style.height,'42px');
  assert.equal(r.action.readOnly,false);assert.equal(r.send.disabled,true);
});

test('bridge failure unlocks input and keeps the original draft for retry',()=>{
  const r=runtime();r.action.value='Nhìn quanh';r.sync();
  r.ctx.Android.submitAction=()=>{throw new Error('bridge failed');};r.submit();
  assert.deepEqual(r.errors,['bridge failed']);assert.equal(r.action.value,'Nhìn quanh');
  assert.equal(r.action.readOnly,false);assert.equal(r.send.disabled,false);
});

test('missing bridge leaves the input editable without committing or locking a turn',()=>{
  const r=runtime();r.action.value='Nhìn quanh';r.ctx.Android={};r.sync();r.submit();
  assert.equal(r.ctx.busy,false);assert.equal(r.action.readOnly,false);assert.equal(r.calls.length,0);
  assert.match(r.ctx.statusEl.textContent,/bridge/);
});

test('pending encounter, active combat, unavailable Core and transient combat locks block even direct submits',()=>{
  for(const configure of [
    r=>r.ctx.state.pendingEncounter={encounterId:'E'},r=>r.ctx.state.combat.active=true,
    r=>r.ctx.coreUnavailable=true,r=>r.ctx.__combatBusy=true,
    r=>r.ctx.__combatDiceBusy=true,r=>r.ctx.__combatFeedbackBusy=true
  ]){
    const r=runtime();r.action.value='Đi tiếp';configure(r);r.sync();r.submit();
    assert.equal(r.action.readOnly,true);assert.equal(r.send.disabled,true);assert.equal(r.calls.length,0);
    assert.equal(r.action.value,'Đi tiếp');
  }
});

test('ordinary Enter and IME composition do not submit; Ctrl/Cmd+Enter sends',()=>{
  const r=runtime();r.action.value='Kiểm tra';r.sync();
  const event={key:'Enter',preventDefault(){}};
  r.action.fire('keydown',event);assert.equal(r.requests(),0);
  r.action.fire('keydown',{...event,ctrlKey:true,isComposing:true});assert.equal(r.requests(),0);
  r.action.fire('keydown',{...event,metaKey:true});assert.equal(r.requests(),1);assert.equal(r.calls.length,1);
});

test('short visual viewport bounds input height and reserves log space above the keyboard',()=>{
  const r=runtime();r.document.activeElement=r.action;r.ctx.visualViewport.height=360;
  r.action.value='Nội dung dài';r.action.scrollHeight=800;r.sync();
  assert.equal(r.root.style['--app-height'],'360px');assert.equal(r.action.style.height,'108px');
  assert.equal(r.classes.has('chat-keyboard-open'),true);
  assert.equal(r.root.style['--composer-height'],'150px');
  r.action.blur();r.action.fire('blur');assert.equal(r.classes.has('chat-keyboard-open'),false);
});

test('all inline scripts remain valid JavaScript after removing the modal controller',()=>{
  for(const match of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g))new vm.Script(match[1]);
});
