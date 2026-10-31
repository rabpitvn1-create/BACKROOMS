const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},serviceWorkers:'block'});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{Math.random=()=>0.61;});
  await page.route('https://res.cloudinary.com/**',route=>route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="180" height="300"><path fill="#bba" d="M20 0h140v300H20z"/></svg>'}));
  await page.goto(process.env.PAGES_TEST_URL||'http://127.0.0.1:4173/Backrooms/',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>typeof state!=='undefined'&&state.coreSnapshot&&window.backroomDemonSword);
  await page.evaluate(()=>{
   window.__swordProbe={starts:[],contacts:[],ends:[],oldCao:0,entityLunges:[],pauseFly:true,packets:[]};
   const api=window.backroomDemonSword;
   window.backroomDemonSword={...api,attack(skill,contact,miss){
    const index=__swordProbe.starts.push(performance.now())-1;
    return api.attack(skill,()=>{__swordProbe.contacts.push({index,time:performance.now()});contact();},miss)
      .then(result=>{__swordProbe.ends.push({index,time:performance.now(),result});return result;});
   }};
   const animate=Element.prototype.animate;
   Element.prototype.animate=function(frames,options){
    if(options?.duration===480){
     if(this.matches('.snapshot-character'))__swordProbe.oldCao++;
     if(this.matches('.snapshot-entity'))__swordProbe.entityLunges.push(performance.now());
    }
    return animate.call(this,frames,options);
   };
   new MutationObserver(()=>{
    const sword=document.querySelector('.demon-sword.sword-flying');
    if(sword&&__swordProbe.pauseFly){
     const fly=sword.getAnimations().find(a=>a.animationName==='sword-fly-anim');
     if(fly){fly.pause();fly.currentTime=0;__swordProbe.pauseFly=false;__swordProbe.paused=true;}
    }
   }).observe(document.getElementById('snapshot'),{subtree:true,attributes:true,attributeFilter:['class']});
   const turn=window.backroomTurn;
   window.backroomTurn=function(raw){const next=JSON.parse(raw);if(next.combatFeedback)__swordProbe.packets.push(next.combatFeedback);return turn(raw);};
   const started=JSON.parse(__webGame.call('startCombatState',JSON.stringify(state),'hound'));
   if(!started.handled)throw Error('Core refused combat');backroomTurn(JSON.stringify(started.state));
  });
  await page.waitForFunction(()=>state.combat?.diceState&&!__combatDiceBusy&&!__directCombatPreparing);
  const shoulder=await page.locator('.demon-sword').evaluate(s=>({right:getComputedStyle(s).right,bottom:getComputedStyle(s).bottom,height:getComputedStyle(s).height,rotate:getComputedStyle(s).rotate}));
  await page.locator('#pokerDiceFinish').click();
  await page.locator('.demon-sword.sword-charging').waitFor();
  const charging=await page.locator('.demon-sword').evaluate(s=>({right:getComputedStyle(s).right,bottom:getComputedStyle(s).bottom,height:getComputedStyle(s).height,rotate:getComputedStyle(s).rotate}));
  assert.deepEqual(charging,shoulder,'Charging must preserve the shoulder pose');
  await page.waitForTimeout(300);
  assert.equal(await page.locator('.combat-float[data-target="entity"]').count(),0,'Damage must wait through charge');
  assert.equal(await page.evaluate(()=>__combatFeedbackBusy),true);
  await page.waitForFunction(()=>__swordProbe.paused,null,{timeout:5000});
  await page.waitForTimeout(200);
  assert.equal(await page.evaluate(()=>__swordProbe.contacts.length),0,'Paused flight must not reveal damage');
  await page.locator('.demon-sword').evaluate(s=>{const a=s.getAnimations().find(a=>a.animationName==='sword-fly-anim');a.currentTime=160;});
  await page.waitForTimeout(100);
  assert.equal(await page.evaluate(()=>__swordProbe.contacts.length),0,'Damage must wait for actual contact');
  await page.locator('.demon-sword').evaluate(s=>{const a=s.getAnimations().find(a=>a.animationName==='sword-fly-anim');a.currentTime=164;});
  await page.waitForFunction(()=>__swordProbe.contacts.length===1);
  assert.ok(await page.locator('.combat-float[data-target="entity"]').count());
  assert.equal(await page.evaluate(()=>__combatFeedbackBusy),true,'Paused sword completion must keep the turn locked');
  assert.equal(await page.evaluate(()=>__swordProbe.entityLunges.length),0,'Entity turn cannot begin before sword return');
  await page.locator('.demon-sword').evaluate(s=>s.getAnimations().find(a=>a.animationName==='sword-fly-anim').play());
  await page.waitForFunction(()=>!__combatFeedbackBusy&&!__directCombatResolving);
  const normal=await page.evaluate(()=>__swordProbe);
  assert.equal(normal.oldCao,0,'New sword must replace the old Cao Minh lunge');
  assert.equal(normal.contacts.length,1);
  assert.equal(normal.ends[0].result,true);
  assert.ok(normal.contacts[0].time-normal.starts[0]>=1500);
  assert.ok(normal.entityLunges[0]>=normal.ends[0].time,'Entity motion keeps its existing lunge after sword completion');
  assert.equal(normal.packets[0].events.find(e=>e.target==='entity').actorId,'cao-minh','Exercise a real Core event');
  console.log('PASS sword: real hyphenated Core ID, charge/flight contact, no old lunge, next actor waits');

  // The next real Core hit defeats the Hound. Its overlay must survive until contact.
  await page.locator('#pokerDiceFinish').click();
  await page.locator('.demon-sword.sword-charging').waitFor();
  assert.equal(await page.locator('.entity-shatter').count(),0);
  assert.equal(await page.evaluate(()=>state.combat.active),true);
  await page.locator('.entity-shatter').waitFor({timeout:5000});
  assert.equal(await page.evaluate(()=>__combatFeedbackBusy),true,'Defeat waits for the shatter presentation');
  await page.waitForFunction(()=>!__combatFeedbackBusy&&!state.combat?.active,null,{timeout:10000});
  assert.equal(await page.locator('.demon-aura,.sword-charging,.sword-flying').count(),0);
  assert.equal(await page.evaluate(()=>backroomDemonSword.isAttacking()),false);
  console.log('PASS sword: lethal contact, shatter, completion and cleanup');

  async function newCombat(){
   await page.evaluate(()=>{
    const fresh=JSON.parse(Android.initializeLevelGame(JSON.stringify(initial),true));
    backroomTurn(JSON.stringify(fresh));
    const started=JSON.parse(__webGame.call('startCombatState',JSON.stringify(fresh),'hound'));
    backroomTurn(JSON.stringify(started.state));
    __swordProbe.starts=[];__swordProbe.contacts=[];__swordProbe.ends=[];
   });
   await page.waitForFunction(()=>state.combat?.diceState&&!__combatDiceBusy&&!__directCombatPreparing,null,{timeout:5000}).catch(async error=>{
    console.log(await page.evaluate(()=>({combat:state.combat,feedback:__combatFeedbackBusy,diceBusy:__combatDiceBusy,preparing:__directCombatPreparing,sword:backroomDemonSword.isAttacking()})));
    throw error;
   });
  }
  async function packet(events){
   await page.evaluate(events=>backroomTurn(JSON.stringify({...state,combatFeedback:{id:'sword-test-'+Date.now(),encounterId:state.combat.encounterId,events,turns:[{kind:'actor',actorId:'cao-minh',actorName:'Cao Minh',eventIndex:0}]}})),events);
  }
  await newCombat();
  await packet([{target:'entity',actorId:'cao-minh',amount:5,text:'-5 HP',remainingHp:75,flash:true},{target:'entity',actorId:'cao_minh',amount:5,text:'-5 HP',remainingHp:70,flash:true}]);
  await page.waitForFunction(()=>!__combatFeedbackBusy,null,{timeout:12000});
  const multi=await page.evaluate(()=>__swordProbe);
  assert.equal(multi.contacts.length,2,'Every hit must play, not be dropped while busy');
  assert.ok(multi.starts[1]>=multi.ends[0].time);
  assert.equal(multi.oldCao,0);
  console.log('PASS sword: both ID forms and consecutive hits are serialized');

  await packet([{target:'entity',actorId:'cao-minh',outcome:'miss',text:'MISS'}]);
  await page.waitForFunction(()=>!__combatFeedbackBusy,null,{timeout:7000});
  assert.equal(await page.evaluate(()=>__swordProbe.contacts.length),3);
  assert.equal(await page.evaluate(()=>__swordProbe.oldCao),0,'Misses use the sword, not the old lunge');
  await packet([{target:'entity',actorId:'cao-minh',outcome:'tick',amount:2,text:'-2 HP',status:'Trúng độc'},{target:'entity',actorId:'cao-minh',outcome:'buff',text:'HỘ GIÁP',status:'Hộ giáp'}]);
  await page.waitForFunction(()=>!__combatFeedbackBusy,null,{timeout:5000});
  assert.equal(await page.evaluate(()=>__swordProbe.starts.length),3,'Ticks and buffs do not attack');
  await page.emulateMedia({reducedMotion:'reduce'});
  await packet([{target:'entity',actorId:'cao-minh',amount:5,text:'-5 HP',remainingHp:65,flash:true}]);
  await page.waitForFunction(()=>!__combatFeedbackBusy,null,{timeout:5000});
  assert.equal(await page.locator('.demon-aura,.sword-charging,.sword-flying').count(),0);
  await page.emulateMedia({reducedMotion:'no-preference'});
  console.log('PASS sword: misses, non-attack effects and reduced-motion fallback');

  await packet([{target:'entity',actorId:'cao-minh',amount:5,text:'-5 HP',remainingHp:60,flash:true}]);
  await page.locator('.demon-sword.sword-charging').waitFor();
  await newCombat();
  assert.equal(await page.locator('.demon-aura,.sword-charging,.sword-flying').count(),0,'New encounter must clean up immediately');
  await page.waitForTimeout(1900);
  assert.equal(await page.locator('.demon-aura,.sword-charging,.sword-flying').count(),0);
  assert.equal(await page.evaluate(()=>backroomDemonSword.isAttacking()),false);
  assert.equal(await page.evaluate(()=>__swordProbe.contacts.length),0,'Cancelled encounter cannot reveal a stale hit');
  assert.deepEqual(errors,[]);
  console.log('PASS sword: encounter cancellation cleans up and suppresses stale contact');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
