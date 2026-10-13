// Run after exporting the review HTML. Playwright and Chromium are test tools only.
const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const html=path.resolve(process.argv[2]);
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_BINARY||undefined,headless:true,
  args:['--no-sandbox','--disable-dev-shm-usage','--no-zygote','--disable-gpu']});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  async function load(){await page.goto(pathToFileURL(html).href);await page.waitForFunction(()=>Array.from(document.images).every(i=>i.complete&&i.naturalWidth>0));}
  async function run(kind,reduced=false,late=false){
   await page.emulateMedia({reducedMotion:reduced?'reduce':'no-preference'});await load();
   if(late)await page.evaluate(()=>{
    const animate=Element.prototype.animate;
    Element.prototype.animate=function(frames,options){
     const motion=animate.call(this,frames,options);
     if(options.duration===480){motion.pause();setTimeout(()=>motion.play(),160);}
     return motion;
    };
   });
   await page.evaluate(kind=>{
    const actor=document.querySelector('.snapshot-character'),entity=document.querySelector('.snapshot-entity');
    const base={actor:actor.getBoundingClientRect().x,entity:entity.getBoundingClientRect().x,entityTransform:getComputedStyle(entity).transform};
    window.trace={base,samples:[],impacts:0,impactSize:0,impactAfterFlash:false,impactStart:null,contactTime:null,damageAtContact:false,miss:false,done:false};
    function sample(){const t=window.trace;t.samples.push({actor:actor.getBoundingClientRect().x,entity:entity.getBoundingClientRect().x});
     t.impacts=Math.max(t.impacts,document.querySelectorAll('.combat-impact').length);
     const impact=document.querySelector('.combat-impact');
     if(impact){if(t.impactStart===null){
       t.impactStart=performance.now();
       const attack=actor.getAnimations().find(a=>a.effect.getTiming().duration===480);
       t.contactTime=attack?attack.currentTime:null;
       const number=document.querySelector('.combat-float');
       t.damageAtContact=!!number&&Number(getComputedStyle(number).opacity)>=.99;
      }
      t.impactSize=Math.max(t.impactSize,impact.getBoundingClientRect().width);
      if(performance.now()-t.impactStart>=150&&Number(getComputedStyle(impact).opacity)>.5)t.impactAfterFlash=true;}
     t.miss=t.miss||!!document.querySelector('.combat-float--miss');
     if(window.__combatFeedbackBusy)requestAnimationFrame(sample);else t.done=true;}
    runDemo(kind);requestAnimationFrame(sample);
   },kind);
   await page.waitForFunction(()=>window.trace.done,{},{timeout:10000});
   return await page.evaluate(()=>({...window.trace,finalEntityTransform:getComputedStyle(document.querySelector('.snapshot-entity')).transform,
    residualEffects:document.querySelectorAll('.combat-impact,.combat-player-hit-edge').length}));
  }
  const hit=await run('hit');
  assert.ok(Math.min(...hit.samples.map(s=>s.actor))<hit.base.actor-10,'Player lunges toward the left');
  assert.ok(Math.min(...hit.samples.map(s=>s.entity))<hit.base.entity-4,'Entity recoils left');
  assert.ok(hit.contactTime>=180&&hit.contactTime<240,'Impact waits for actual lunge contact');
  assert.ok(hit.damageAtContact,'Damage is fully visible in the first impact frame');
  assert.ok(hit.impacts>0);assert.ok(hit.impactSize>=144);assert.ok(hit.impactAfterFlash,'Impact remains visible after the hit flash');assert.equal(hit.finalEntityTransform,hit.base.entityTransform);assert.equal(hit.residualEffects,0);
  const delayed=await run('hit',false,true);
  assert.ok(delayed.contactTime>=180&&delayed.contactTime<240,'A late-starting lunge still contacts at animation time 180ms');
  assert.ok(delayed.damageAtContact,'Late lunge damage and impact share a frame');
  const critical=await run('crit');assert.ok(critical.damageAtContact);assert.ok(critical.impacts>0);assert.ok(critical.impactSize>=178);assert.ok(critical.impactAfterFlash);
  const incoming=await run('entityHit');
  assert.ok(Math.max(...incoming.samples.map(s=>s.entity))>incoming.base.entity+10,'Entity lunges right');
  assert.ok(Math.max(...incoming.samples.map(s=>s.actor))>incoming.base.actor+4,'Player recoils right');
  const playerDodge=await run('entityMiss');assert.ok(playerDodge.miss);assert.equal(playerDodge.impacts,0);
  assert.ok(Math.max(...playerDodge.samples.map(s=>s.actor))>playerDodge.base.actor+8);
  const entityDodge=await run('playerMiss');assert.ok(entityDodge.miss);assert.equal(entityDodge.impacts,0);
  assert.ok(Math.min(...entityDodge.samples.map(s=>s.entity))<entityDodge.base.entity-8);
  await run('lucia');await run('luciaHit');
  const tick=await run('tick');assert.equal(tick.impacts,0);assert.equal(tick.finalEntityTransform,tick.base.entityTransform);
  const reduced=await run('entityMiss',true);assert.ok(reduced.miss);assert.equal(reduced.impacts,0);
  assert.ok(reduced.samples.every(s=>Math.abs(s.actor-reduced.base.actor)<.1&&Math.abs(s.entity-reduced.base.entity)<.1));
  await page.emulateMedia({reducedMotion:'no-preference'});await load();
  await page.evaluate(()=>{runDemo('hit');document.getElementById('reset').click()});
  await page.waitForTimeout(1500);
  assert.equal(await page.locator('.combat-impact,.combat-float').count(),0,'Reset cancels pending contact');
  await page.setViewportSize({width:360,height:640});await load();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'No horizontal overflow on narrow screens');
  assert.deepEqual(errors,[]);
  console.log('Chromium review passed: lunge/recoil directions, impact, MISS, Lucia, periodic ticks, reduced motion, cancellation, native Entity alignment and 360px layout.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
