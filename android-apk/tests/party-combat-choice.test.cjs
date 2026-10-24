const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const source=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map(m=>m[1]).find(s=>s.includes('if(window.__pokerCoreUiInstalled)'));
function boot(diceState){
  function node(){
    const n={hidden:true,disabled:false,children:[],listeners:{},attrs:{},classList:{add(){},remove(){}},
      setAttribute(k,v){this.attrs[k]=v;},addEventListener(k,fn){this.listeners[k]=fn;},appendChild(c){this.children.push(c);}};
    Object.defineProperty(n,'textContent',{get(){return this.text||'';},set(v){this.text=v;this.children=[];}});
    return n;
  }
  const ids=Object.fromEntries(['pokerDiceModal','pokerDiceRow','pokerDiceMeta','pokerDiceHand','pokerDiceRoll','pokerDiceFinish','pokerDiceTitle'].map(id=>[id,node()]));
  const calls=[];
  const scope={state:{combat:{active:true,encounterId:'enc',...(diceState?{diceState}:{})}},busy:false,statusEl:{},
    document:{getElementById:id=>ids[id]||null,body:{classList:{add(){},remove(){}}},createElement:node},setTimeout(){},
    Android:{combatDicePrepare:(s,a)=>calls.push(['prepare',a]),combatDiceHold:(s,i,held)=>calls.push(['hold',i,held]),combatDiceFinish:()=>calls.push(['finish']),submitAction:(s,k,a)=>calls.push(['execute',k,a])},
    render(){},backroomTurn(json){scope.state=JSON.parse(json);}};
  scope.window=scope;vm.runInNewContext(source,scope);
  return {scope,ids,calls};
}
const dice={action:'Cả Party cùng tấn công',values:[1,2,3,4,5],held:[false,true,false,false,false],rerollsUsed:0,maxRerolls:3,finalized:false,hand:'NONE'};
test('battle opens five dice and prepares one Core session without a four-button selector',()=>{
  const r=boot();assert.deepEqual(r.calls,[['prepare',dice.action]]);
  r.scope.ensureDirectCombatDice();r.scope.ensureDirectCombatDice();assert.equal(r.calls.length,1);
  assert.equal(r.ids.pokerDiceRow.children.length,5);
  assert.ok(r.ids.pokerDiceRow.children.every(d=>d.disabled));
  assert.doesNotMatch(html,/id="pokerDiceCommand"|data-party-combat=/);
});
test('Core dice values and HOLD state are rendered and clicks request a native hold',()=>{
  const r=boot(dice),buttons=r.ids.pokerDiceRow.children;
  assert.equal(buttons.length,5);assert.match(buttons[1].className,/held/);
  assert.equal(buttons[1].attrs['aria-pressed'],'true');assert.equal(buttons[0].attrs['aria-pressed'],'false');
  assert.equal(buttons[1].children[0].src,'dice/die-2.png');
  buttons[0].listeners.click();assert.deepEqual(r.calls,[['hold',0,true]]);
});
test('FINISH executes the same native dice action; restored finalized actions remain intact',()=>{
  const r=boot(dice);r.ids.pokerDiceFinish.listeners.click();
  r.scope.backroomCombatDiceState(JSON.stringify({combat:{active:true,encounterId:'enc',diceState:{...dice,finalized:true}}}));
  assert.deepEqual(r.calls,[['finish'],['execute','EXECUTE',dice.action]]);
  const guard='Cả Party cùng phòng thủ';
  const restored=boot({...dice,action:guard,finalized:true});
  assert.deepEqual(restored.calls,[['execute','EXECUTE',guard]]);
});
