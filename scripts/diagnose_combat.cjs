const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  for(const width of [412,1365]){
   const page=await browser.newPage({viewport:{width,height:width===412?915:768}});
   page.on('pageerror',e=>console.log('PAGEERROR',width,e.message));
   await page.addInitScript(()=>{Math.random=()=>0.61});
   await page.goto('http://127.0.0.1:4173/Backrooms/',{waitUntil:'domcontentloaded'});
   await page.waitForFunction(()=>window.__webGame&&window.Android&&state?.coreSnapshot,{timeout:20000});
   await page.evaluate(()=>{
     window.__combatCalls=[];
     for(const name of ['combatDicePrepare','combatDiceHold','combatDiceRoll','combatDiceFinish','submitAction']){
       const original=Android[name].bind(Android);
       Android[name]=function(...args){
         window.__combatCalls.push({name,time:Date.now(),turn:state.turn,
           dice:state.combat?.diceState&&{action:state.combat.diceState.action,
             held:state.combat.diceState.held,finalized:state.combat.diceState.finalized}});
         return original(...args);
       };
     }
     const started=JSON.parse(__webGame.call('startCombatState',JSON.stringify(state),'hound'));
     if(!started.handled)throw Error('startCombatState: '+JSON.stringify(started));
     backroomTurn(JSON.stringify(started.state));
   });
   const snapshot=()=>page.evaluate(()=>({
     turn:state.turn, combat:state.combat&&{active:state.combat.active,
       encounterId:state.combat.encounterId,diceState:state.combat.diceState,
       entityHp:state.combat.entityHp,entityName:state.combat.entityName},
     ui:{busy:busy,preparing:window.__directCombatPreparing,
       resolving:window.__directCombatResolving,diceBusy:window.__combatDiceBusy,
       rolling:window.__combatDiceRolling,feedbackBusy:window.__combatFeedbackBusy,
       status:document.getElementById('status')?.textContent,
       modalHidden:document.getElementById('pokerDiceModal')?.hidden,
       diceButtons:Array.from(document.querySelectorAll('#pokerDiceRow button')).map(b=>({disabled:b.disabled,pressed:b.getAttribute('aria-pressed')})),
       turnIndicator:document.getElementById('combatTurnIndicator')?.textContent},
     calls:window.__combatCalls,errors:__webGame.events.slice(-6)
   }));
   await page.waitForTimeout(1000);
   console.log('BEFORE',width,JSON.stringify(await snapshot()).slice(0,7000));
   try{
     await page.locator('#pokerDiceRow button').first().click({timeout:8000});
     await page.waitForTimeout(800);
     console.log('AFTER_HOLD',width,JSON.stringify(await snapshot()).slice(0,7000));
     if(await page.locator('#pokerDiceRoll').isEnabled()){
       await page.locator('#pokerDiceRoll').click({timeout:8000});
       await page.waitForTimeout(1900);
       console.log('AFTER_ROLL',width,JSON.stringify(await snapshot()).slice(0,7000));
     }
     if(await page.locator('#pokerDiceFinish').isEnabled()){
       await page.locator('#pokerDiceFinish').click({timeout:8000});
       await page.waitForTimeout(3500);
       console.log('AFTER_FINISH',width,JSON.stringify(await snapshot()).slice(0,7000));
     }
   }catch(error){
     console.log('ACTION_ERROR',width,String(error).slice(0,1000));
     console.log('AT_ERROR',width,JSON.stringify(await snapshot()).slice(0,7000));
   }
   await page.screenshot({path:'build/combat-diagnostic-'+width+'.png'}).catch(()=>{});
   await page.close();
  }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
