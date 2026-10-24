const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html = fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const source = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)]
  .map(m=>m[1]).find(s=>s.includes('if(window.__pokerCoreUiInstalled)'));

function boot(diceState) {
  function node() {
    return {hidden:true,disabled:false,textContent:'',children:[],listeners:{},classList:{add(){},remove(){}},
      setAttribute(){},addEventListener(k,fn){this.listeners[k]=fn;},appendChild(n){this.children.push(n);}};
  }
  const ids={};
  for(const id of ['pokerDiceModal','pokerDiceRow','pokerDiceMeta','pokerDiceHand','pokerDiceRoll','pokerDiceFinish','pokerDiceTitle','pokerDiceCommand'])ids[id]=node();
  const commands={};
  for(const command of ['ATTACK','GUARD','EVADE','READ']) {
    const button=node();button.getAttribute=()=>command;button.closest=()=>button;commands[command]=button;
  }
  ids.pokerDiceCommand.querySelectorAll=()=>Object.values(commands);
  const calls=[];
  const scope={state:{combat:{active:true,encounterId:'enc',...(diceState?{diceState}:{})}},busy:false,statusEl:{},
    document:{getElementById:id=>ids[id]||null,body:{classList:{add(){},remove(){}}},createElement:node},
    setTimeout(){},Android:{combatDicePrepare:(s,a)=>calls.push(['prepare',a]),
      combatDiceFinish:()=>calls.push(['finish']),submitAction:(s,k,a)=>calls.push(['execute',k,a])},
    render(){},backroomTurn(json){scope.state=JSON.parse(json);}};
  scope.window=scope;vm.runInNewContext(source,scope);
  return {scope,ids,calls,choose(command){ids.pokerDiceCommand.listeners.click({target:commands[command]});}};
}

test('a new combat round waits for a player command before rolling',()=>{
  const r=boot();assert.deepEqual(r.calls,[]);
  assert.equal(r.ids.pokerDiceCommand.hidden,false);
  assert.equal(r.ids.pokerDiceRoll.disabled,true);
  assert.equal(r.ids.pokerDiceFinish.disabled,true);
});

for(const [command,action] of Object.entries({ATTACK:'Cả Party cùng tấn công',GUARD:'Cả Party cùng phòng thủ',EVADE:'Cả Party cùng né tránh',READ:'Cả Party cùng quan sát'})) {
  test(command+' binds the real dice session and submits that same command',()=>{
    const r=boot();r.choose(command);
    assert.deepEqual(r.calls,[['prepare',action]]);
    r.choose(command==='ATTACK'?'GUARD':'ATTACK');
    assert.equal(r.calls.length,1);
    const dice={action,values:[1,2,3,4,5],held:[true,true,true,true,true],rerollsUsed:0,maxRerolls:3,finalized:false,hand:'SSF'};
    r.scope.backroomCombatDiceState(JSON.stringify({combat:{active:true,encounterId:'enc',diceState:dice}}));
    assert.equal(r.ids.pokerDiceCommand.hidden,true);
    r.ids.pokerDiceFinish.listeners.click();
    r.scope.backroomCombatDiceState(JSON.stringify({combat:{active:true,encounterId:'enc',diceState:{...dice,finalized:true}}}));
    assert.deepEqual(r.calls.at(-1),['execute','EXECUTE',action]);
  });
}

test('reload restores a finalized defensive command without changing it to attack',()=>{
  const action='Cả Party cùng phòng thủ';
  const r=boot({action,values:[1,1,1,1,1],held:[true,true,true,true,true],finalized:true,hand:'FSF'});
  assert.deepEqual(r.calls,[['execute','EXECUTE',action]]);
  assert.equal(r.ids.pokerDiceCommand.hidden,true);
});
