const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  for(const viewport of [{width:412,height:915},{width:1365,height:768}]){
   const page=await browser.newPage({viewport});const errors=[];
   page.on('pageerror',e=>errors.push(e.message));
   await page.addInitScript(()=>{Math.random=()=>0.61;});
   let firstKey,requests=0;
   await page.route('https://generativelanguage.googleapis.com/**',async route=>{
    requests++;
    if(!firstKey)firstKey=await page.evaluate(()=>BACKROOMS_CONFIG.keys[0]);
    if(route.request().headers()['x-goog-api-key']===firstKey){await route.fulfill({status:401,contentType:'application/json',body:'{}'});return;}
    const response={reply:'Cao Minh dừng lại trước bức tường. Hắn quan sát các vết xước rồi kiểm tra khoảng trống phía trước, chưa vội chạm tay vào.',entityPreview:'Một tiếng động nhẹ vọng tới từ phía xa.'};
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({candidates:[{content:{parts:[{text:JSON.stringify(response)}]}}]})});
   });
   await page.goto(process.env.PAGES_TEST_URL||'http://127.0.0.1:4173/Backrooms/',{waitUntil:'domcontentloaded'});
   await page.waitForFunction(()=>document.documentElement.dataset.webMode==='playable'&&typeof state!=='undefined'&&state.levelTravel&&state.coreSnapshot);
   assert.equal(await page.evaluate(()=>__webGame.bootError),null);
   assert.equal(await page.locator('#webPreviewNotice').count(),0);
   assert.equal(await page.locator('#startupRecovery').isHidden(),true);
   await page.locator('#searchActionButton').waitFor({state:'visible'});
   assert.equal(await page.locator('#searchActionButton').isEnabled(),true);
   const before=await page.evaluate(()=>({turn:state.turn,run:state.levelTravel.runId}));
   await page.locator('#searchActionButton').click();
   await page.waitForFunction(t=>state.turn>t&&!busy,before.turn,{timeout:60000});
   assert.ok(requests>=2);
   const providers=await page.evaluate(()=>__webGame.events.filter(x=>x.kind==='provider'));
   assert.ok(providers.some(x=>x.alias==='K1'&&x.status===401));
   assert.ok(providers.some(x=>x.alias==='K2'&&x.status===200));
   const saved=await page.evaluate(()=>({turn:state.turn,run:state.levelTravel.runId}));
   await page.locator('#mobileMenuButton').click();
   await page.getByRole('button',{name:'Lưu',exact:true}).click();
   await page.locator('#party .party-member[data-character="cao-minh"]').click();
   await page.locator('#characterInventoryView').waitFor({state:'visible'});
   assert.match(await page.locator('#characterInventoryName').innerText(),/Cao Minh/);
   assert.ok(await page.locator('#characterEquipmentList').innerText());
   await page.locator('#characterInventoryBack').click();
   await page.locator('#mobileMenuClose').click();
   await page.reload({waitUntil:'domcontentloaded'});
   await page.waitForFunction(()=>typeof state!=='undefined'&&state.coreSnapshot&&state.levelTravel);
   const loaded=await page.evaluate(()=>({turn:state.turn,run:state.levelTravel.runId}));
   assert.deepEqual(loaded,saved,'Reload must resume the committed game');
   await page.locator('#submit').click();
   await page.locator('#action').fill('Cao Minh quan sát kỹ bức tường trước mặt');
   await page.locator('#playerActionConfirm').click();
   await page.waitForFunction(t=>state.turn>t&&!busy,saved.turn,{timeout:60000});
   assert.match(await page.evaluate(()=>state.log.at(-1).text),/Cao Minh/);
   // Exercise real pointer actions on the combat UI, not only the Core methods.
   await page.evaluate(()=>{
     window.__combatSubmitCount=0;window.__observedEntityCue=false;
     const originalSubmit=Android.submitAction.bind(Android);
     Android.submitAction=function(...args){window.__combatSubmitCount++;return originalSubmit(...args)};
     const turnIndicator=document.getElementById('combatTurnIndicator');
     if(turnIndicator)new MutationObserver(()=>{
       if(turnIndicator.dataset.kind==='entity'&&!turnIndicator.hidden)window.__observedEntityCue=true;
     }).observe(turnIndicator,{attributes:true,attributeFilter:['data-kind','hidden']});
     const begun=JSON.parse(__webGame.call('startCombatState',JSON.stringify(state),'hound'));
     if(!begun.handled||!begun.state)throw new Error('Cannot start combat fixture');
     backroomTurn(JSON.stringify(begun.state));
   });
   await page.waitForFunction(()=>state.combat?.diceState&&!window.__combatDiceBusy&&!window.__directCombatPreparing,
     undefined,{timeout:30000});
   const firstDice=await page.evaluate(()=>state.combat.diceState.action);
   await page.locator('#pokerDiceRow .poker-die').first().click();
   await page.waitForFunction(()=>state.combat?.diceState?.held?.[0]===true&&!window.__combatDiceBusy);
   assert.equal(await page.locator('#pokerDiceRow .poker-die').first().getAttribute('aria-pressed'),'true');
   assert.equal(await page.locator('#backroomActionToast').isVisible(),true,'Buttons should show immediate feedback');
   await page.locator('#pokerDiceRoll').click();
   await page.waitForFunction(()=>Number(state.combat?.diceState?.rerollsUsed)>=1&&!window.__combatDiceRolling,
     undefined,{timeout:30000});
   await page.locator('#pokerDiceFinish').click();
   await page.waitForFunction(()=>window.__combatSubmitCount===1,undefined,{timeout:30000});
   await page.waitForFunction(()=>!window.__combatFeedbackBusy&&!window.__directCombatResolving,
     undefined,{timeout:60000});
   assert.equal(await page.evaluate(()=>window.__combatSubmitCount),1,'No automatic repeated attacks');
   if(await page.evaluate(()=>state.combat?.active===true)){
     await page.waitForFunction(()=>state.combat?.diceState&&!state.combat.diceState.finalized,
       undefined,{timeout:30000});
     assert.notEqual(await page.evaluate(()=>state.combat.diceState.action),firstDice,
       'A living combat needs a fresh dice hand after the previous round');
   }
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
   assert.deepEqual(errors,[],'Browser runtime errors');
   await page.screenshot({path:`build/playable-${viewport.width}.png`});
   console.log(`PASS: ${viewport.width}px startup, search, failover, inventory view, save/reload and typed action`);
   await page.close();
  }
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
