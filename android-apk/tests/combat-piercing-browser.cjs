const fs=require('fs'),path=require('path'),assert=require('assert/strict'),{chromium}=require('playwright');
const assets=path.resolve(__dirname,'../app/src/main/assets'),out=process.env.COMBAT_PREVIEW_OUTPUT||'/tmp/combat-piercing-preview';fs.mkdirSync(out,{recursive:true});
(async()=>{const browser=await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE_PATH,args:['--no-sandbox','--disable-dev-shm-usage'],headless:true});try{
const page=await browser.newPage({viewport:{width:620,height:480}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.route('http://combat.test/**',async r=>{const n=new URL(r.request().url()).pathname;await r.fulfill({path:path.join(assets,n==='/'?'index.html':n)});});
await page.goto('http://combat.test/');
await page.evaluate(()=>{window.Android={levelSnapshot:()=>JSON.stringify({path:'http://combat.test/level_snapshots/drive/level_0/01.webp',level:0})};state.flags={};state.combat={active:true,actorIndex:0,currentActor:'Cao Minh',round:1,participants:[{id:'cao_minh',name:'Cao Minh'},{id:'luc_tram',name:'Lục Trầm'},{id:'lucia',name:'Lucia'}],entities:[{key:'hound',hp:999},{key:'clump',hp:999}],entity:{key:'hound',hp:999},activeEntityIndex:0};render();});
const script=async name=>page.addScriptTag({content:fs.readFileSync(assets+'/'+name,'utf8').replaceAll('file:///android_asset/','http://combat.test/')});await script('snapshot-ui.js');
await page.addStyleTag({content:'#snapshot{position:fixed!important;left:10px!important;top:10px!important;width:600px!important;height:380px!important;margin:0!important;z-index:100!important}#review-title{position:fixed;left:10px;top:405px;font:600 18px system-ui;color:#efe9cf;z-index:101;background:#151719;padding:12px;width:574px;border-radius:10px}'});
await page.evaluate(()=>{const label=document.createElement('div');label.id='review-title';document.body.appendChild(label);const backdrop=document.createElement('div');backdrop.style.cssText='position:fixed;inset:0;background:#0b0e0f;z-index:99';document.body.appendChild(backdrop);});await page.waitForFunction(()=>[...document.querySelectorAll('#snapshot img')].every(i=>i.complete&&i.naturalWidth));await page.waitForTimeout(200);
await page.clock.install();await page.clock.pauseAt(new Date());
const hit=(idx=0,key='hound',extra={})=>page.evaluate(([idx,key,extra])=>backroomPlayCombatFeedback({phase:'actor',target:'entity',kind:'damage',text:'-12 HP',flash:true,actorIndex:idx,entityKey:key,...extra}),[idx,key,extra]);
await hit();await page.waitForFunction(()=>document.querySelector('.combat-piercing-sword')?.dataset.contactMs);assert.equal(await page.locator('.combat-float').count(),0);
assert.equal(await page.locator('.combat-piercing-sword').getAttribute('data-angle'),'90');
assert.equal(await page.locator('.combat-piercing-sword').evaluate(el=>el.getAnimations()[0].effect.getTiming().duration)>=270,true);
const contact=Number(await page.locator('.combat-piercing-sword').getAttribute('data-contact-ms'));await page.clock.runFor(contact+1);assert.equal(await page.locator('.combat-float').count(),1);assert.equal(await page.locator('.combat-green-blood').count(),44);assert.equal(await page.locator('.combat-impact-slash svg').count(),1);assert.equal(await page.locator('.combat-contact-burst').count(),1);assert.ok(await page.locator('.snapshot-entity').evaluate(el=>el.getAnimations().some(a=>a.effect.getKeyframes().some(k=>k.translate&&k.translate!=='0px'))));assert.equal(await page.locator('.combat-impact-slash').evaluate(el=>getComputedStyle(el).boxShadow),'none');
assert.equal(await page.locator('.combat-piercing-sword').evaluate(e=>getComputedStyle(e).zIndex),'2');assert.equal(await page.locator('.snapshot-entity').evaluate(e=>getComputedStyle(e).zIndex),'3');await page.clock.runFor(1900);
await hit(0,'hound',{damageSource:'status',status:'Trúng độc'});assert.equal(await page.locator('.combat-piercing-sword').count(),0);await page.clock.runFor(1900);
await hit(0,'clump');assert.equal(await page.locator('.combat-piercing-sword').count(),0);assert.equal(await page.locator('.combat-float').count(),0);
await hit(0,'hound',{kind:'miss',text:''});assert.equal(await page.locator('.combat-piercing-sword').count(),0);
const angles=new Set(),lengths=[];
for(let trial=0;trial<25;trial++){
const idx=trial%2;await page.evaluate(idx=>backroomSetCombatVisualActor(idx,'hound'),idx);await page.clock.runFor(500);
await page.evaluate(([idx,trial])=>document.getElementById('review-title').textContent=(idx?'Lục Trầm':'Cao Minh')+' · Xuyên qua và bay ra · Góc '+(trial+1)+'/25',[idx,trial]);
await hit(idx);await page.waitForFunction(()=>document.querySelector('.combat-piercing-sword')?.dataset.contactMs);
const geometry=await page.locator('.combat-piercing-sword').evaluate(el=>({length:+el.dataset.visibleLength,angle:+el.dataset.angle,flight:+el.dataset.flightMs,entry:[+el.dataset.entryX,+el.dataset.entryY],exit:[+el.dataset.exitX,+el.dataset.exitY],finish:[+el.dataset.finishX,+el.dataset.finishY]}));
angles.add(Math.round(geometry.angle));lengths.push(geometry.length);assert.ok(geometry.finish[0]<geometry.entry[0],'swords must approach from the facing side, never behind the Entity');
assert.ok(Math.hypot(geometry.finish[0]-geometry.exit[0],geometry.finish[1]-geometry.exit[1])>geometry.length,'entire hilt must clear the body');
const capture=trial<8;
for(let frame=0;frame<35;frame++){
 await page.evaluate(()=>{for(const el of document.querySelectorAll('[data-fx-born]'))for(const anim of el.getAnimations()){anim.pause();anim.currentTime=Date.now()-Number(el.dataset.fxBorn);}});
 if(capture)await page.screenshot({path:path.join(out,`sword-${trial}-${String(frame).padStart(2,'0')}.png`)});
 await page.clock.runFor(30);
}
assert.equal(await page.locator('.combat-piercing-sword').count(),0,'sword flies out, with no impaled hold');assert.equal(await page.locator('.combat-contact-burst').count(),0);assert.equal(await page.locator('.snapshot-entity').evaluate(el=>getComputedStyle(el).translate),'none');await page.clock.runFor(1900);
}
assert.equal(angles.size,25);assert.ok(lengths.every(length=>Math.abs(length-lengths[0])<.01),'all swords have the same visible length');
await page.evaluate(()=>backroomSetCombatVisualActor(2,'hound'));await page.clock.runFor(500);await page.evaluate(()=>document.getElementById('review-title').textContent='Lucia · Chỉ hiệu ứng trúng đạn trên Entity');await hit(2);
assert.equal(await page.locator('.combat-bullet-impact').count(),1);assert.equal(await page.locator('.combat-piercing-sword').count(),0);assert.equal(await page.locator('.combat-green-blood').count(),0);await page.screenshot({path:path.join(out,'bullet-impact.png')});await page.clock.runFor(1900);
await script('gm-choice-ui.js');await page.evaluate(()=>{window.seenHits=[];const original=backroomPlayCombatFeedback;window.backroomPlayCombatFeedback=e=>{seenHits.push({key:e.entityKey,active:document.querySelector('.snapshot-entity:not(.combat-turn-out)')?.dataset.entityKey});original(e);};const c=state.combat;c.actorIndex=1;c.currentActor='Lục Trầm';c.resolvedActorIndex=0;c.feedbackEvents=Array.from({length:4},(_,i)=>({phase:'actor',target:'entity',kind:'damage',actorIndex:0,entityKey:i<2?'hound':'clump',text:'-3 HP',flash:true}));c.resolvedEntityTurns=[];backroomCombatTurn(JSON.stringify(state));});await page.clock.runFor(3500);assert.equal(await page.evaluate(()=>__combatBusy),true);await page.clock.runFor(4500);assert.equal(await page.evaluate(()=>__combatBusy),false);assert.equal(await page.evaluate(()=>seenHits.length),4);assert.ok(await page.evaluate(()=>seenHits.every(e=>e.key===e.active)));assert.deepEqual(errors,[]);
console.log(JSON.stringify({browser:await browser.version(),checks:['damage at contact','blood','body occlusion','25 front-side directions and equal sword lengths','entire sword clears the exit','status without weapons','no wrong target','miss','bullet-only','multi-hit two-Entity drain'],errors,output:out}));
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
