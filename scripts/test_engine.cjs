const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
vm.runInThisContext(fs.readFileSync('pages/regex.js','utf8'));
const core=require('../engine/target/javascript/game-core.js');
Object.assign(globalThis,core);
globalThis.window=globalThis;
globalThis.document={documentElement:{dataset:{}}};
const storage=new Map();
globalThis.localStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,String(v)),removeItem:k=>storage.delete(k)};
globalThis.BACKROOMS_ASSETS={};
for(const dir of ['knowledge','canon'])for(const name of fs.readdirSync('web/'+dir)){
 if(/\.(json|md)$/.test(name))BACKROOMS_ASSETS[dir+'/'+name]=fs.readFileSync('web/'+dir+'/'+name,'utf8');
}
globalThis.BACKROOMS_CONFIG={keys:Array.from({length:6},(_,i)=>'fixture-key-'+i),models:['gemini-3.5-flash-lite'],version:'test'};
vm.runInThisContext(fs.readFileSync('pages/bridge.js','utf8'));
const html=fs.readFileSync('web/index.html','utf8');
const initial=vm.runInNewContext(html.slice(html.indexOf('const prologue'),html.indexOf('const byId'))+'; initial');
let state=JSON.parse(Android.resumeLevelGame(JSON.stringify(initial),false));
const errors=[];
globalThis.backroomTurn=raw=>{state=JSON.parse(raw);};
globalThis.backroomError=message=>errors.push(message);
globalThis.backroomCombatDiceError=message=>errors.push(message);
globalThis.backroomCombatDiceState=raw=>{state=JSON.parse(raw);};
globalThis.backroomItemAction=raw=>{const r=JSON.parse(raw);if(r.state)state=r.state;};
globalThis.backroomCoreUpgrade=raw=>{const r=JSON.parse(raw);if(r.state)state=r.state;};
globalThis.backroomProvider=()=>{};
let requests=0;
globalThis.fetch=async(url,options)=>{
 requests++;
 // Bridge calls the Cloudflare Worker proxy (no API key in client); mock a successful Gemini response.
 return new Response(JSON.stringify({candidates:[{content:{parts:[{text:JSON.stringify({reply:'Cao Minh quan sát những dấu vết trước mặt rồi tiếp tục kiểm tra khu vực.',entityPreview:'Một tiếng động khe khẽ vang lên từ phía xa.'})}]}}]}),{status:200});
};
function call(name,...args){return JSON.parse(__webGame.call(name,...args));}
(async()=>{
 assert.equal(__webGame.bootError,null);
 assert.equal(state.turn,1);assert.ok(state.levelTravel.runId);assert.ok(state.coreSnapshot);
 assert.equal(state.partyDetails.members[0].id,'cao-minh');
 const identity=state.levelTravel.runId;
 console.log('PASS: real APK core initializes a new game, location, party and snapshot');
 const initialTurn=state.turn;
 await Android.submitGameplayAction(JSON.stringify(state),'SEARCH','Tìm kiếm');
 await __webGame.idle;
 assert.deepEqual(errors,[],'Turn pipeline errors');
 assert.equal(state.turn,initialTurn+1);assert.ok(requests>=1);
 assert.ok(__webGame.events.some(x=>x.alias==='Gemini'&&x.status===200));
 assert.match(state.log.at(-1).text,/Cao Minh/);
 console.log('PASS: full turn generation via API proxy, canon validation and authoritative commit');
 const saved=Android.levelCoreSnapshot(),ui=JSON.stringify(state);
 assert.equal(JSON.parse(saved).saveVersion,state.coreSnapshot.saveVersion);
 const init=JSON.parse(core.coreInit(BACKROOMS_ASSETS['knowledge/level_travel_policy.json'],saved));
 assert.equal(init.ok,true);
 const resumed=call('resumeLevelGame',ui,true);
 assert.equal(resumed.turn,state.turn);assert.equal(resumed.levelTravel.runId,identity);
 state=resumed;
 console.log('PASS: save/load preserves run identity and committed turn');
 const metaTurn=state.turn,beforeRequests=requests;
 await Android.submitGameplayAction(JSON.stringify(state),'EXECUTE','xem trạng thái');
 await __webGame.idle;
 assert.deepEqual(errors,[]);assert.equal(state.turn,metaTurn);assert.equal(requests,beforeRequests);
 console.log('PASS: status lookup is local and does not advance a turn');
 const started=call('startCombatState',JSON.stringify(state),'hound');
 assert.equal(started.handled,true);state=started.state;
 const action='Tấn công';
 await Android.combatDicePrepare(JSON.stringify(state),action);await __webGame.idle;
 assert.deepEqual(errors,[]);
 await Android.combatDiceHold(JSON.stringify(state),0,true);await __webGame.idle;
 await Android.combatDiceRoll(JSON.stringify(state));await __webGame.idle;
 await Android.combatDiceFinish(JSON.stringify(state));await __webGame.idle;
 assert.deepEqual(errors,[],'Dice pipeline errors');
 const combatTurn=state.turn,finalizedHand=JSON.stringify(state);
 await Android.submitAction(finalizedHand,'EXECUTE',action);await __webGame.idle;
 assert.deepEqual(errors,[],'Combat pipeline errors');
 assert.ok(state.turn>=combatTurn);assert.ok(state.combatFeedback||state.combat);
 assert.equal(requests,beforeRequests);
 const resolvedSnapshot=JSON.stringify(state);
 await Android.submitAction(finalizedHand,'EXECUTE',action);await __webGame.idle;
 assert.equal(JSON.stringify(state),resolvedSnapshot,'Same dice hand must not resolve twice');
 assert.ok(__webGame.events.some(e=>e.kind==='combat-repeat-blocked'));
 console.log('PASS: real combat prepare/hold/reroll/finish/resolution runs locally');

 const originalRandom=Math.random;
 try{
  function newTravelGame(){
   Math.random=()=>0;
   state=JSON.parse(Android.initializeLevelGame(JSON.stringify(initial),true));
  }
  function travelTurn(value){
   let draws=0;
   Math.random=()=>{draws++;return value;};
   const before=JSON.stringify(state),candidate=JSON.stringify({...state,turn:state.turn+1});
   state=call('finishLevelTurn',before,candidate,'Khám phá');
   assert.equal(draws,state.exitResult.completed?2:1,'One trial per completed turn; destination drawn only on exit');
   assert.equal(state.exitResult.chancePercent,50);
   assert.equal(state.exitResult.failurePercent,50);
   assert.equal(state.exitResult.requiredStreak,4);
   return {before,candidate};
  }
  for(const value of [0,0.399999,0.4,0.499999,0.5,0.999999]){
   newTravelGame();
   const node=state.levelTravel.nodeId;
   travelTurn(value);
   assert.equal(state.exitResult.success,value<0.5,'Exact 50/50 boundary at '+value);
   assert.equal(state.levelTravel.exitStreak,value<0.5?1:0);
   assert.equal(state.levelTravel.nodeId,node);
  }
  console.log('PASS: travel uses a 50/50 threshold, including the exact 0.5 boundary');
  for(let streak=0;streak<4;streak++){
   newTravelGame();
   const node=state.levelTravel.nodeId,run=state.levelTravel.runId;
   for(let i=1;i<=streak;i++){
    travelTurn(0.49);
    assert.equal(state.levelTravel.exitStreak,i);
    assert.equal(state.exitResult.completed,false,'No exit before four consecutive successes');
   }
   const inventory=JSON.stringify(state.inventory),party=JSON.stringify(state.party);
   travelTurn(0.5);
   assert.equal(state.levelTravel.exitStreak,0,'Failure resets the whole streak from '+streak);
   assert.equal(state.exitResult.streak,0);
   assert.equal(state.exitResult.completed,false);
   assert.equal(state.levelTravel.nodeId,node);
   assert.equal(state.levelTravel.runId,run);
   assert.equal(JSON.stringify(state.inventory),inventory);
   assert.equal(JSON.stringify(state.party),party);
   travelTurn(0.49);
   assert.equal(state.levelTravel.exitStreak,1,'The next success starts over at one');
  }
  console.log('PASS: failure at streak 0, 1, 2 or 3 resets to zero without resetting the game');
  newTravelGame();
  const source=state.levelTravel.nodeId,run=state.levelTravel.runId;
  for(let i=1;i<=3;i++){
   travelTurn(0.49);
   assert.equal(state.levelTravel.exitStreak,i);
   assert.equal(state.levelTravel.nodeId,source);
   assert.equal(state.exitResult.completed,false);
  }
  const partialUi=JSON.stringify(state),partialCore=Android.levelCoreSnapshot();
  assert.equal(JSON.parse(core.coreInit(BACKROOMS_ASSETS['knowledge/level_travel_policy.json'],partialCore)).ok,true);
  state=call('resumeLevelGame',partialUi,true);
  assert.equal(state.levelTravel.exitStreak,3,'Save/load preserves an unfinished streak');
  const {before,candidate}=travelTurn(0.49);
  assert.equal(state.exitResult.streak,4);
  assert.equal(state.exitResult.completed,true);
  assert.equal(state.exitResult.from,source);
  assert.notEqual(state.exitResult.to,source);
  assert.equal(state.levelTravel.nodeId,state.exitResult.to);
  assert.equal(state.levelTravel.exitStreak,0,'A new level starts a fresh streak');
  assert.equal(state.levelTravel.runId,run);
  assert.match(state.log.at(-1).text,/no-clip/i);
  const committedTravel=structuredClone(state.levelTravel);
  Math.random=()=>{throw Error('A replay must not draw travel RNG');};
  state=call('finishLevelTurn',before,candidate,'Khám phá');
  assert.deepEqual(state.levelTravel,committedTravel,'Replaying a completed turn must not add another streak');
  Math.random=()=>0.61;
  await Android.submitGameplayAction(JSON.stringify(state),'EXECUTE','xem trạng thái');
  await __webGame.idle;
  assert.deepEqual(errors,[]);
  assert.deepEqual(state.levelTravel,committedTravel,'Meta lookup must not roll or reset the streak');
  travelTurn(0.49);
  assert.equal(state.levelTravel.exitStreak,1);
  console.log('PASS: fourth success no-clips once; save/load, duplicate turns and meta actions preserve travel state');
 }finally{Math.random=originalRandom;}
 console.log('Engine gameplay checks passed.');
})().catch(error=>{console.error(error);process.exit(1);});

