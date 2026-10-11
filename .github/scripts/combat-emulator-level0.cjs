'use strict';
// Android WebView emulator: real computed CSS and genuine Level 0 gameplay.
// Never mutate Core state, force Exit rolls, or inject a fake Level transition.
const http=require('node:http'),fs=require('node:fs'),cp=require('node:child_process'),path=require('node:path');
const dir=path.resolve('emulator-evidence');fs.mkdirSync(dir,{recursive:true});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const deadline=Date.now()+Number(process.env.EMULATOR_MAX_MINUTES||25)*60000;
const report={target:'0.1',targetName:'Zenith Station',reached:false,visual:null,actions:[],errors:[],lastState:null};
function shot(name){try{fs.writeFileSync(path.join(dir,name+'.png'),cp.execFileSync('adb',['exec-out','screencap','-p'],{maxBuffer:6000000,timeout:20000}));}catch(e){report.errors.push('screenshot '+e.message)}}
function getJson(endpoint){return new Promise((resolve,reject)=>{const r=http.get('http://127.0.0.1:9222'+endpoint,res=>{let text='';res.setEncoding('utf8');res.on('data',x=>text+=x);res.on('end',()=>{try{resolve(JSON.parse(text))}catch(e){reject(e)}})});r.on('error',reject);r.setTimeout(5000,()=>r.destroy(Error('CDP discovery timeout')));})}
class CDP{
 constructor(url){this.ws=new WebSocket(url);this.seq=0;this.pending=new Map();this.errors=[];this.ws.addEventListener('message',e=>{let m;try{m=JSON.parse(e.data)}catch(_){return}if(m.method==='Runtime.exceptionThrown')this.errors.push(m.params?.exceptionDetails?.text||'JS exception');if(m.id&&this.pending.has(m.id)){const h=this.pending.get(m.id);this.pending.delete(m.id);m.error?h.reject(Error(JSON.stringify(m.error))):h.resolve(m.result)}})}
 ready(){if(this.ws.readyState===1)return Promise.resolve();return new Promise((resolve,reject)=>{const t=setTimeout(()=>reject(Error('CDP socket handshake timeout')),15000);this.ws.addEventListener('open',()=>{clearTimeout(t);resolve()},{once:true});this.ws.addEventListener('error',()=>{clearTimeout(t);reject(Error('CDP socket failed'))},{once:true})})}
 send(method,params={}){const id=++this.seq;return new Promise((resolve,reject)=>{const t=setTimeout(()=>{this.pending.delete(id);reject(Error('CDP command timeout'))},25000);this.pending.set(id,{resolve:x=>{clearTimeout(t);resolve(x)},reject:e=>{clearTimeout(t);reject(e)}});this.ws.send(JSON.stringify({id,method,params}))})}
 async eval(code){const r=await this.send('Runtime.evaluate',{expression:code,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error('WebView: '+r.exceptionDetails.text);return r.result?.value}
}
async function attach(){for(let i=0;i<60;i++){try{const targets=await getJson('/json');const t=targets.find(x=>x.type==='page'&&x.webSocketDebuggerUrl);if(t){const c=new CDP(t.webSocketDebuggerUrl.replace('localhost:','127.0.0.1:'));await c.ready();await c.send('Runtime.enable');return c}}catch(_){}await pause(1000)}throw Error('Android WebView CDP endpoint unavailable')}
function probe(){
 const s=typeof state==='undefined'?null:state,c=s&&s.combat,x=s&&s.flags&&s.flags.exploration,d=c&&c.diceState;
 return {ready:!!(s&&s.partyDetails),level:String(s?.level?.id??s?.level?.number??''),turn:Number(s?.turn||0),
 combat:!!(c&&c.active),entity:c?.entityKey||'',streak:Number(x?.hiddenExitStreak||0),
 exitReady:!!x?.exitReady,rollsUsed:Number(d?.rerollsUsed||0),dice:!!d,finalized:!!d?.finalized,
 busy:(typeof busy!=='undefined'&&!!busy)||!!window.__combatFeedbackBusy||!!window.__combatDiceBusy||!!window.__directCombatResolving,
 entityVisible:!!document.querySelector('.snapshot-entity'),partyFocus:document.getElementById('snapshot')?.dataset?.combatFocus||'',
 status:String(document.getElementById('status')?.textContent||'').slice(0,160)};
}
function geometry(){
 const box=document.getElementById('snapshot'),cm=box?.querySelector('.snapshot-character');
 if(!cm)return {pass:false,error:'Cao Minh image missing'};
 const before=box.dataset.combatFocus;
 const create=(cls,src)=>{const img=document.createElement('img');img.className=cls;img.src=src;box.appendChild(img);return img};
 const luc=create('snapshot-lucia','file:///android_asset/LUCIA_LUC_OVERLAY.png');
 const iris=create('combat-turn-avatar','file:///android_asset/avatars/Iris_avatar.jpg');
 const ent=create('snapshot-entity','file:///android_asset/entity/hound.webp');
 ent.style.position='absolute';ent.style.left='0';ent.style.bottom='0';ent.style.height='97%';
 const dim=el=>{const r=el.getBoundingClientRect(),cs=getComputedStyle(el);return {right:r.right,bottom:r.bottom,height:r.height,display:cs.display,animation:cs.animationName,fit:cs.objectFit}};
 box.dataset.combatFocus='cao_minh';const leader=dim(cm),enemy=dim(ent);
 box.dataset.combatFocus='lucia';const teammate=dim(luc),other=dim(iris),enemy2=dim(ent);
 const pass=leader.display!=='none'&&teammate.display!=='none'&&enemy.display!=='none'&&
 Math.abs(leader.right-teammate.right)<1.5&&Math.abs(leader.right-other.right)<1.5&&
 Math.abs(leader.bottom-teammate.bottom)<1.5&&Math.abs(leader.height-teammate.height)<1.5&&
 enemy.animation==='none'&&enemy2.animation==='none'&&leader.fit==='contain'&&teammate.fit==='contain';
 luc.remove();iris.remove();ent.remove();
 if(before===undefined)delete box.dataset.combatFocus;else box.dataset.combatFocus=before;
 return {pass,leader,teammate,other,enemy};
}
function gameAction(kind){
 let el;
 if(kind==='EXPLORE')el=document.getElementById('exploreActionButton');
 if(kind==='ROLL')el=document.getElementById('pokerDiceRoll');
 if(kind==='FINISH')el=document.getElementById('pokerDiceFinish');
 if(el){if(el.disabled)return false;el.click();return true}
 if(kind==='TRAVERSE'){
  const field=document.getElementById('action'),form=document.getElementById('form');
  if(!field||!form)return false;
  field.value='Cao Minh chủ động bước qua Exit đã xác thực, đi vào Level 0.1 Zenith Station.';
  field.dispatchEvent(new Event('input',{bubbles:true}));form.requestSubmit();return true;
 }
 return false;
}
const call=fn=>'('+fn.toString()+')()';
(async()=>{
 let c;
 try{
  c=await attach();
  for(let i=0;i<30;i++){if((await c.eval(call(probe)))?.ready)break;await pause(1000)}
  report.visual=await c.eval(call(geometry));console.log('WebView CSS:',JSON.stringify(report.visual));shot('01-css-geometry');
  if(!report.visual.pass)throw Error('Android WebView computed CSS violates Party/Entity slots');
  await c.eval('location.reload();true');await pause(4500);
  let lastAction=0,lastLog='',lastChange=Date.now(),prev='',count=0,combatSeen=false;
  while(Date.now()<deadline&&count<240){
   const p=await c.eval(call(probe));report.lastState=p;
   if(!p?.ready){await pause(1000);continue}
   if(p.level==='0.1'){report.reached=true;shot('03-zenith-station');break}
   if(p.level!=='0')throw Error('Unexpected exit from Level 0 to '+p.level);
   const signature=[p.turn,p.combat,p.entity,p.streak,p.exitReady,p.rollsUsed,p.finalized].join(':');
   if(signature!==prev){lastChange=Date.now();prev=signature;if(signature!==lastLog){console.log('CORE:',JSON.stringify(p));lastLog=signature}}
   if(p.combat&&!combatSeen){combatSeen=true;shot('02-combat');}
   if(p.busy||Date.now()-lastAction<4200){await pause(1200);continue}
   if(Date.now()-lastChange>120000)throw Error('Gameplay stalled awaiting Core or GM provider: '+p.status);
   const what=p.combat?(p.finalized?'WAIT':p.dice&&p.rollsUsed>0?'FINISH':'ROLL'):(p.exitReady?'TRAVERSE':'EXPLORE');
   if(what==='WAIT'){await pause(1500);continue}
   const clicked=await c.eval('('+gameAction.toString()+')('+JSON.stringify(what)+')');
   if(clicked){lastAction=Date.now();count++;report.actions.push({turn:p.turn,action:what,streak:p.streak,entity:p.entity,time:new Date().toISOString()})}
   else await pause(1400);
  }
  if(!report.reached)throw Error('Did not reach Level 0.1 via genuine actions within emulator time budget');
 }catch(e){report.errors.push(String(e.stack||e));process.exitCode=1;console.error('EMULATOR FAILED:',e.message)}
 finally{if(c){report.errors.push(...c.errors.slice(-10));c.ws.close()}
  fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2));
  console.log('EMULATOR RESULT:',JSON.stringify({reached:report.reached,visual:report.visual?.pass,actions:report.actions.length,lastState:report.lastState,errors:report.errors.slice(0,2)}));
 }
})();
