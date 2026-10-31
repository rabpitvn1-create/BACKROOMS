const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
const browser=await chromium.launch({headless:true});
try{for(const viewport of [{width:412,height:915}]){
const page=await browser.newPage({viewport});const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.addInitScript(()=>{Math.random=()=>0.61});
await page.goto(process.env.PAGES_TEST_URL||'http://127.0.0.1:4173/Backrooms/',{waitUntil:'domcontentloaded'});
await page.waitForFunction(()=>state?.coreSnapshot&&typeof Android!=='undefined');
await page.evaluate(()=>{
window.__combatProbe={prepare:0,submit:0,packets:[],visual:[],errors:[]};
const oldTurn=window.backroomTurn;
window.backroomTurn=function(raw){const next=JSON.parse(raw);if(next.combatFeedback)window.__combatProbe.packets.push(next.combatFeedback);return oldTurn(raw)};
const oldError=window.backroomCombatDiceError;
window.backroomCombatDiceError=function(message){window.__combatProbe.errors.push(String(message));return oldError(message)};
const prep=Android.combatDicePrepare.bind(Android),submit=Android.submitAction.bind(Android);
Android.combatDicePrepare=function(...args){window.__combatProbe.prepare++;return prep(...args)};
Android.submitAction=function(...args){window.__combatProbe.submit++;return submit(...args)};
window.__entityCueSeen=false;
new MutationObserver(()=>{const cue=document.getElementById('combatTurnIndicator');if(cue?.dataset.kind==='entity'&&!cue.hidden&&getComputedStyle(cue).display!=='none')window.__entityCueSeen=true}).observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['data-kind','hidden']});
const start=JSON.parse(__webGame.call('startCombatState',JSON.stringify(state),'hound'));if(!start.handled||!start.state)throw Error('Core refused combat start');backroomTurn(JSON.stringify(start.state));
});
await page.waitForFunction(()=>state.combat?.diceState&&!window.__combatDiceBusy&&!window.__directCombatPreparing,{timeout:30000});
assert.equal(await page.evaluate(()=>window.__combatProbe.prepare),1,'Dice prepared exactly once');
let rounds=0;
while(rounds<3&&await page.evaluate(()=>state.combat?.active===true)){
await page.waitForFunction(()=>state.combat?.diceState&&!state.combat.diceState.finalized&&!window.__combatFeedbackBusy&&!window.__combatDiceBusy&&!window.__directCombatPreparing,{timeout:60000});
const before=await page.evaluate(()=>({turn:state.turn,action:state.combat.diceState.action,held:Boolean(state.combat.diceState.held[0]),rerolls:state.combat.diceState.rerollsUsed,submits:window.__combatProbe.submit}));
await page.locator('#pokerDiceRow .poker-die').first().click();
await page.waitForFunction(v=>!window.__combatDiceBusy&&Boolean(state.combat?.diceState?.held?.[0])!==v,before.held,{timeout:15000});
assert.equal(await page.locator('#pokerDiceRow .poker-die').first().getAttribute('aria-pressed'),String(!before.held));
assert.equal(await page.locator('#backroomActionToast').count(),0,'Combat feedback must not use an intrusive floating toast');
assert.match(await page.locator('#pokerDiceMeta').innerText(),/HOLD|xúc xắc|ROLL/,'Combat Dice must give in-panel feedback');
await page.locator('#pokerDiceRoll').click();
await page.waitForFunction(v=>!window.__combatDiceRolling&&!window.__combatDiceBusy&&state.combat?.diceState?.rerollsUsed>v,before.rerolls,{timeout:20000});
await page.locator('#pokerDiceFinish').click();
await page.waitForFunction(v=>window.__combatProbe.submit===v+1,before.submits,{timeout:20000});
await page.waitForFunction(()=>!window.__combatFeedbackBusy&&!window.__directCombatResolving&&(!state.combat?.active||state.combat?.diceState&&!state.combat.diceState.finalized),{timeout:90000});
assert.equal(await page.evaluate(()=>window.__combatProbe.submit),before.submits+1);
if(await page.evaluate(()=>state.combat?.active===true)){
const after=await page.evaluate(()=>({turn:state.turn,action:state.combat.diceState.action}));
assert.ok(after.turn>before.turn);assert.notEqual(after.action,before.action);
}
rounds++;
}
const snapshot=await page.evaluate(()=>({p:window.__combatProbe,cue:window.__entityCueSeen,overflow:document.documentElement.scrollWidth>innerWidth+1}));
assert.deepEqual(snapshot.p.errors,[]);
assert.equal(snapshot.p.submit,rounds);
const entity=snapshot.p.packets.flatMap(p=>p.turns||[]).filter(t=>t.kind==='entity');
if(entity.length)assert.ok(snapshot.cue,'Entity cue must appear');
assert.equal(snapshot.overflow,false);assert.deepEqual(errors,[]);
console.log('PASS COMBAT '+viewport.width+'px rounds='+rounds+' prepare='+snapshot.p.prepare+' submits='+snapshot.p.submit+' entityCues='+entity.length);
await page.screenshot({path:'build/combat-pass-'+viewport.width+'.png'});await page.close();
}}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
