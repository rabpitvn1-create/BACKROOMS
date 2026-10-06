const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const uiPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'gm-choice-ui.js');
const source = fs.readFileSync(uiPath, 'utf8');
const coreFacadePath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'rabpit', 'backroom', 'core', 'GameCoreFacade.java');
const coreFacadeSource = fs.readFileSync(coreFacadePath, 'utf8');

test('Poker Dice uses one reusable inline panel inside the GM log', () => {
  assert.equal((source.match(/dicePanel=document\.createElement\('section'\)/g) || []).length, 1);
  assert.match(source, /article\.appendChild\(dicePanel\)/);
  assert.doesNotMatch(source, /document\.body\.appendChild\(dicePanel\)/);
  assert.doesNotMatch(source, /combat-dice-modal/);
  assert.doesNotMatch(source, /\.combat-dice-panel\{[^}]*position:fixed/);
});

test('combat hides the two fixed Explorer choices while the inline dice panel is active', () => {
  const start = source.indexOf('function appendExplorerChoices');
  const end = source.indexOf('function renderSemanticLog', start);
  assert.ok(start >= 0 && end > start);
  assert.match(source.slice(start, end), /if \(!entry \|\| \(state\.combat && state\.combat\.active\)\) return;/);
});

test('reroll UI does not append GM messages', () => {
  const start = source.indexOf('function sendCombatRoll()');
  const end = source.indexOf('function sendCombatFinish()', start);
  assert.ok(start >= 0 && end > start);
  const rollBlock = source.slice(start, end);
  assert.match(rollBlock, /Android\.combatRoll/);
  assert.doesNotMatch(rollBlock, /state\.log|appendBattleSection|appendLog/);
});

test('current hand is shown before FINISH', () => {
  assert.match(source, /diceResult\.textContent=hasRolled\?handLabel\(dice\.hand\):'';/);
  assert.match(source, /'TWO PAIR':'Two Pair'/);
});

test('ROLL and FINISH are separate controls', () => {
  assert.match(source, /id="combatDiceRoll">ROLL<\/button>/);
  assert.match(source, /id="combatDiceFinish">FINISH<\/button>/);
  assert.match(source, /Android\.combatFinish/);
  assert.match(source, /diceRoll\.disabled=.*rerolls>=maxRerolls\|\|allHeld\(held\)/);
});

test('Poker Dice meta is fully localized to Vietnamese', () => {
  assert.ok(source.includes("diceMeta.textContent='Lượt Quay '+String(rerolls)+'/'+String(maxRerolls)+' - Chạm Vào Xúc Xắc Để Giữ';"));
  assert.doesNotMatch(source, /Reroll .*chạm die để HOLD/);
});

test('production dice UI uses local assets and no Unicode dice glyphs', () => {
  assert.match(source, /file:\/\/\/android_asset\/dice\/die-/);
  assert.doesNotMatch(source, /[⚀⚁⚂⚃⚄⚅]/);
  assert.doesNotMatch(source, /Math\.random/);
});

test('finished hand waits two seconds before Core resolve', () => {
  assert.match(source, /setTimeout\(function\(\)[\s\S]*Android\.combatResolve\(JSON\.stringify\(state\)\)[\s\S]*},2000\)/);
});


test('battle log uses semantic font for Cao Minh title and compact hand tokens', () => {
  assert.match(source, /Vạn Giới Ma Tôn/);
  assert.match(source, /\[F\.O\.A\.K\]/);
  assert.match(source, /knownHandTokens\.forEach\(function\(x\)\{ addTerm\(map,x,'stat'\); \}\)/);
  assert.match(source, /Trúng độc/);
  assert.match(source, /Xuyên giáp/);
});


test('combat actor handoff delegates to snapshot transition without clearing the active Entity first', () => {
  assert.match(source, /backroomSetCombatVisualActor\(actorIndex,entityKey\)/);
  const sync=source.slice(source.indexOf('function syncCombatSnapshotActor'),source.indexOf('window.backroomCombatDiceState=function'));
  assert.doesNotMatch(sync, /backroomClearCombatVisualActor\(\);[\s\S]{0,100}backroomSetCombatVisualActor/);
});

test('combat completion scrolls to the start of the next GM narration', () => {
  const start = source.indexOf('function finishCombatAnimation');
  const end = source.indexOf('window.backroomCombatTurn', start);
  assert.ok(start >= 0 && end > start);
  const finishBlock = source.slice(start, end);
  assert.match(finishBlock, /scrollForCurrentMode\(\)/);
  assert.doesNotMatch(finishBlock, /scrollCombatToBottom\(\)/);
});

test('Core upgrades use live state and are locked only by active combat', () => {
  const start = coreFacadeSource.indexOf('public synchronized String processCoreUpgrade');
  const end = coreFacadeSource.indexOf('public synchronized String levelSnapshotDescriptor', start);
  assert.ok(start >= 0 && end > start);
  const upgradeBlock = coreFacadeSource.slice(start, end);
  assert.match(upgradeBlock, /JSONObject persisted = parseState\(liveStateJson\)/);
  assert.match(upgradeBlock, /if \(persisted\.length\(\) == 0\) persisted = submitted;/);
  assert.match(upgradeBlock, /CombatChoiceEngine\.isActive\(persisted\)/);
});


test('dice reserve a stable hold-label area and animate only unheld values while ROLL resolves', () => {
  assert.match(source, /\.combat-dice-row\{[^}]*grid-template-columns:repeat\(5,minmax\(0,1fr\)\)/);
  assert.match(source, /\.combat-die\{[^}]*min-width:44px;min-height:76px/);
  assert.match(source, /\.combat-die-skin\{[^}]*width:100%;height:100%/);
  assert.match(source, /@keyframes combat-die-3d/);
  assert.match(source, /var DICE_ROLL_ANIMATION_MS=680;/);
  assert.match(source, /diceRollAnimating=true;/);
  assert.match(source, /rolling=diceRollAnimating&&held\[index\]!==true/);
  assert.match(source, /setProperty\('--die-delay',String\(index\*-55\)\+'ms'\)/);
  assert.match(source, /DICE_ROLL_ANIMATION_MS-\(Date\.now\(\)-diceRollStartedAt\)/);
});

test('GM effect highlights keep the normal narration font', () => {
  assert.match(source, /\.message\.gm \.semantic-effect,\.message\.gm \.semantic-damage,\.message\.gm \.semantic-buff\{font-family:inherit\}/);
  assert.match(source, /\.semantic-effect\{color:#ff9f43\}/);
  assert.match(source, /\.semantic-damage\{color:#ff5c5c\}/);
  assert.match(source, /\.semantic-buff\{color:#73e6a2\}/);
});


test('multi-Entity combat exposes a compact target queue and Core-owned target mutation', () => {
  assert.match(source, /id="combatTargets"/);
  assert.match(source, /function combatEntities\(combat\)/);
  assert.match(source, /function sendCombatTarget\(index\)/);
  assert.match(source, /Android\.combatTarget\(Number\(index\)\)/);
  assert.match(source, /targetEntityIndex/);
  assert.match(coreFacadeSource, /public synchronized String combatTargetRuntime\(int entityIndex\)/);
  assert.match(coreFacadeSource, /CombatChoiceEngine\.setTargetEntity\(working, entityIndex\)/);
});

test('active Entity handoff is separate from player actor handoff', () => {
  assert.match(source, /resolvedEntityTurns/);
  assert.match(source, /backroomSetCombatVisualActor\(combat\.resolvedActorIndex,entityKey\)/);
  assert.match(source, /playCombatPhase\(events,'entity',entityIndex\)/);
});


test('rendered dice preserve Core values, Vietnamese hold state and index-specific tap routing', () => {
  const vm = require('node:vm');
  class Node {
    constructor(){ this.children=[];this.attrs={};this.events={};this.style={setProperty(){}}; }
    set textContent(value){this.text=String(value);this.children=[];}
    get textContent(){return this.text||'';}
    appendChild(child){this.children.push(child);return child;}
    setAttribute(key,value){this.attrs[key]=value;}
    addEventListener(event,handler){this.events[event]=handler;}
  }
  const calls=[];
  const context={document:{createElement(){return new Node()}},window:{},state:{combat:{active:true,currentActor:'Cao Minh',diceState:{values:[1,2,3,4,5],held:[true,false,true,false,false],hasRolled:true,rerollsUsed:1,maxRerolls:3}}},dicePanel:new Node(),diceTitle:new Node(),combatTargets:null,diceMeta:new Node(),diceRow:new Node(),diceResult:new Node(),diceRoll:new Node(),diceFinish:new Node(),diceRollAnimating:false,diceSettleUntil:0,diceSettleMask:[],combatDiceState(){return this.state.combat.diceState},sendCombatHold(index,held){calls.push([index,held])},handLabel(){return ''},allHeld(values){return values.every(Boolean)},scheduleCombatResolve(){},diceAsset(value){return 'file:///android_asset/dice/die-'+value+'.png'}};
  const start=source.indexOf('  function renderCombatPanel(){');
  const end=source.indexOf('  diceRoll.addEventListener',start);
  vm.createContext(context);
  // Core state is consumed without mutation; only click routing emits a hold request.
  context.combatDiceState=()=>context.state.combat.diceState;
  vm.runInContext(source.slice(start,end)+';renderCombatPanel();',context);
  assert.equal(context.diceRow.children.length,5);
  const before=JSON.stringify(context.state);
  const skins=button=>button.children.find(n=>n.className==='combat-die-object').children[0];
  context.diceRow.children.forEach((button,index)=>{
    assert.equal(skins(button).src,'file:///android_asset/dice/die-'+(index+1)+'.png');
    assert.match(button.attrs['aria-label'],new RegExp('Xúc xắc '+(index+1)));
    assert.equal(button.attrs['aria-pressed'],index===0||index===2?'true':'false');
    assert.equal(button.children.find(n=>n.className==='combat-die-hold-seal').textContent,'GIỮ');
  });
  context.diceRow.children[0].events.click();context.diceRow.children[1].events.click();
  assert.deepEqual(calls,[[0,false],[1,true]]);
  assert.equal(JSON.stringify(context.state),before);
  context.state.combat.diceState.values=[6,6,6,6,6];
  context.diceRollAnimating=true;
  context.window.__combatBusy=true;
  vm.runInContext('renderCombatPanel()',context);
  assert.equal(context.diceRow.children.filter(n=>n.className.includes('rolling')).length,3);
  assert.ok(context.diceRow.children.every(n=>n.disabled));
  assert.equal(skins(context.diceRow.children[0]).src,'file:///android_asset/dice/die-6.png');
});

test('battle drains all feedback before rotating to Entity turns and the next actor',()=>{
 const vm=require('node:vm');let now=0;const timers=[],seen=[],swaps=[];
 const ctx={window:{__combatAnimationToken:0,backroomPlayCombatFeedback:e=>seen.push([now,e.id]),backroomSetCombatVisualActor:(i,k)=>swaps.push([now,i,k]),render(){}},COMBAT_PHASE_MS:1600,COMBAT_SWAP_MS:480,COMBAT_EVENT_GAP_MS:450,state:{},action:null,status:{},busy:false,setTimeout(fn,ms){timers.push({fn,at:now+ms});},syncComposer(){},renderCombatPanel(){},scrollCombatToBottom(){},scrollForCurrentMode(){},activeCombatEntity:c=>c.entity,syncCombatSnapshotActor(c){ctx.window.backroomSetCombatVisualActor(c.actorIndex,c.entity.key);}};
 vm.createContext(ctx);
 const start=source.indexOf('  function combatPhaseEvents(');
 vm.runInContext(source.slice(start>=0?start:source.indexOf('  function playCombatPhase('),source.indexOf('  var previousError =')),ctx);
 const actor=Array.from({length:8},(_,i)=>({phase:'actor',entityKey:'hound',id:'skill-'+i}));
 const entity=[{phase:'entity',entityIndex:0,id:'e0'},{phase:'entity',entityIndex:1,id:'e1'}];
 ctx.window.backroomCombatTurn(JSON.stringify({combat:{active:true,resolvedActorIndex:0,actorIndex:1,round:2,currentActor:'Lục Trầm',entity:{key:'hound'},feedbackEvents:[...actor,...entity],resolvedEntityTurns:[{entityIndex:0,entityKey:'hound'},{entityIndex:1,entityKey:'clump'}]}}));
 while(timers.length){timers.sort((a,b)=>a.at-b.at);const t=timers.shift();now=t.at;t.fn();}
 assert.equal(seen.length,10);assert.equal(seen[7][1],'skill-7');
 assert.ok(swaps[1][0]>=seen[7][0]+1600,'Entity turn must wait for the last skill feedback');
 assert.ok(swaps.at(-1)[0]>=seen.at(-1)[0]+1600,'next actor must wait for the last Entity feedback');
 assert.equal(ctx.window.__combatBusy,false);
 // Late callbacks from an invalidated animation cannot play damage in the next turn.
 seen.length=0;ctx.playCombatPhase(actor,'actor');ctx.window.__combatAnimationToken++;
 while(timers.length)timers.shift().fn();assert.equal(seen.length,0);
});
