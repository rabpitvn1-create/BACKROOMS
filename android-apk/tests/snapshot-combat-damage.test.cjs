const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const patch=fs.readFileSync(path.join(__dirname,'../patch-poker-dice-ui-119-polish.py'),'utf8');
const script=patch.match(/damage_script = r'''<script>\n([\s\S]*?)<\/script>'''/)[1];

function boot(combat){
  const timers=[];
  function element(){return {children:[],listeners:{},style:{},appendChild(child){this.children.push(child);child.parentNode=this},removeChild(child){this.children=this.children.filter(n=>n!==child);child.parentNode=null},addEventListener(name,fn){this.listeners[name]=fn}}}
  const box=element();
  const scope={state:{combat},document:{getElementById(){return box},createElement:element},requestAnimationFrame(fn){fn()},setTimeout(fn,ms){timers.push({fn,ms})}};
  scope.window=scope;
  scope.backroomTurn=json=>{scope.state=JSON.parse(json)};
  vm.runInNewContext(script,scope);
  return {scope,box,timers,turn(combat){scope.backroomTurn(JSON.stringify({combat}))}};
}
const combat={active:true,entityKey:'hound',entityHp:110,playerHp:100};

test('HP loss creates separate damage tags and numeric values for each target',()=>{
  const r=boot(combat);r.turn({...combat,entityHp:74,playerHp:92});
  assert.deepEqual(r.box.children.map(n=>n.children.map(c=>c.textContent)),[['DMG','-36 HP'],['DMG','-8 HP']]);
  assert.match(r.box.children[0].className,/entity/);assert.match(r.box.children[1].className,/player/);
});
test('healing and encounter changes never create damage',()=>{
  const r=boot(combat);r.turn({...combat,entityHp:110,playerHp:110});assert.equal(r.box.children.length,0);
  r.turn({...combat,entityKey:'smiler',entityHp:20,playerHp:20});assert.equal(r.box.children.length,0);
});
test('damage cleans up on animation end or the mobile fallback timeout',()=>{
  const r=boot(combat);r.turn({...combat,entityHp:74});
  assert.equal(r.timers[0].ms,1800);r.box.children[0].listeners.animationend();assert.equal(r.box.children.length,0);
  r.timers[0].fn();assert.equal(r.box.children.length,0);
  const fallback=boot(combat);fallback.turn({...combat,entityHp:74});fallback.timers[0].fn();assert.equal(fallback.box.children.length,0);
});
test('a resolved direct kill projects the remaining Entity HP once',()=>{
  const r=boot(combat);r.scope.__directCombatResolving=true;r.turn(null);
  assert.deepEqual(r.box.children.map(n=>n.children.map(c=>c.textContent)),[['DMG','-110 HP']]);
});
