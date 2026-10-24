const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assets=path.join(__dirname,'../app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const source=fs.readFileSync(path.join(assets,'primary-actions.js'),'utf8');
function boot() {
  const nodes=new Map(),calls=[];
  function element() {return {value:'',hidden:true,disabled:false,style:{},listeners:{},
    setAttribute(k,v){this[k]=v;},focus(){this.focused=true;},blur(){this.focused=false;},
    addEventListener(k,fn){(this.listeners[k] ||= []).push(fn);},
    fire(k,event={}){for(const fn of this.listeners[k]||[])fn({preventDefault(){},...event});}};}
  for(const id of [...html.matchAll(/\bid="([^"]+)"/g)].map(x=>x[1]))nodes.set(id,element());
  const context={busy:false,state:{turn:1},window:null,innerHeight:800,
    document:{getElementById:id=>nodes.get(id),addEventListener(){}},
    actionEl:nodes.get('action'),submitEl:nodes.get('submit'),formEl:nodes.get('form'),
    statusEl:nodes.get('status'),save(){},render(){},
    Android:{submitGameplayAction(raw,kind,action){calls.push({state:JSON.parse(raw),kind,action});},
      submitAction(){throw Error('combat bridge must not receive exploration actions');}}};
  context.window=context;vm.createContext(context);
  // Execute the real form submission/callback code, not a second implementation.
  const submit=html.slice(html.indexOf('formEl.addEventListener("submit"'),html.indexOf('\nrender();\n</script>'));
  vm.runInContext(submit,context);vm.runInContext(source,context);
  return {context,nodes,calls};
}
test('three action buttons exist once in the persistent bottom bar; Execute opens input',()=>{
  for(const id of ['exploreActionButton','submit','searchActionButton','form','primaryActionRow'])
    assert.equal((html.match(new RegExp('id="'+id+'"','g'))||[]).length,1,id);
  const r=boot();assert.equal(r.context.submitEl.disabled,false);
  r.context.submitEl.fire('click');assert.equal(r.nodes.get('playerActionModal').hidden,false);
  assert.equal(r.nodes.get('playerActionConfirm').disabled,true);assert.equal(r.calls.length,0);
  r.context.actionEl.value='Đi qua lối thoát';r.context.actionEl.fire('input');
  assert.equal(r.nodes.get('playerActionConfirm').disabled,false);
  r.context.formEl.fire('submit');assert.equal(r.calls[0].action,'Đi qua lối thoát');
  assert.equal(r.calls[0].kind,'EXECUTE','exit intent must use the regular gameplay bridge');
  assert.equal(r.nodes.get('playerActionModal').hidden,true);
});
test('Explore and Search submit one real gameplay turn and preserve a freeform draft',()=>{
  const r=boot();r.context.actionEl.value='Bản nháp';
  r.nodes.get('exploreActionButton').fire('click');r.nodes.get('exploreActionButton').fire('click');
  assert.equal(r.calls.length,1);assert.equal(r.calls[0].action,'Khám phá');assert.equal(r.calls[0].kind,'EXPLORE');
  assert.equal(r.context.actionEl.value,'Bản nháp');assert.equal(r.nodes.get('searchActionButton').disabled,true);
  r.context.backroomError('Lỗi thử nghiệm');
  assert.equal(r.nodes.get('searchActionButton').disabled,false);
  r.nodes.get('searchActionButton').fire('click');assert.equal(r.calls[1].action,'Tìm kiếm');assert.equal(r.calls[1].kind,'SEARCH');
  r.context.backroomTurn(JSON.stringify({turn:2}));assert.equal(r.context.busy,false);
  assert.equal(r.nodes.get('exploreActionButton').disabled,false);
});
test('combat and failed startup lock every primary action; explicit recovery unlocks them',()=>{
  const r=boot();r.context.state.combat={active:true};r.context.render();
  for(const id of ['exploreActionButton','submit','searchActionButton'])assert.equal(r.nodes.get(id).disabled,true);
  r.nodes.get('exploreActionButton').fire('click');assert.equal(r.calls.length,0);
  r.context.state.combat=null;r.nodes.get('startupRecovery').hidden=false;r.context.render();
  r.nodes.get('searchActionButton').fire('click');assert.equal(r.calls.length,0);
  r.nodes.get('startupRecovery').hidden=true;r.context.render();
  assert.equal(r.nodes.get('searchActionButton').disabled,false);
});
