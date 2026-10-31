const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  for(const viewport of [{width:412,height:915}]){
   const page=await browser.newPage({viewport});const errors=[];
   page.on('pageerror',e=>errors.push(e.message));
   await page.addInitScript(()=>{Math.random=()=>0.61;});
   let requests=0;
   await page.route('https://backrooms-gemini-proxy.onrender.com/**',async route=>{
    requests++;
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
   assert.ok(requests>=1);
   const providers=await page.evaluate(()=>__webGame.events.filter(x=>x.kind==='provider'));
   assert.ok(providers.some(x=>x.alias==='Gemini'&&x.status===200));
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
   // Hành vi mới: reload = new game, KHÔNG tự resume save cũ
   const fresh=await page.evaluate(()=>({turn:state.turn,run:state.levelTravel.runId}));
   assert.equal(fresh.turn,1,'Reload must start a new game at turn 1');
   assert.notEqual(fresh.run,saved.run,'Reload must start a new run, not resume the old one');
   // Người chơi bấm "Tải" trong menu để quay lại save cũ
   await page.locator('#mobileMenuButton').click();
   await page.getByRole('button',{name:'Tải',exact:true}).click();
   await page.waitForFunction(s=>state.turn===s.turn&&state.levelTravel&&state.levelTravel.runId===s.run,saved,{timeout:15000});
   const loaded=await page.evaluate(()=>({turn:state.turn,run:state.levelTravel.runId}));
   assert.deepEqual(loaded,saved,'Manual load must restore the committed game');
   await page.locator('#mobileMenuClose').click();
   await page.locator('#submit').click();
   await page.locator('#action').fill('Cao Minh quan sát kỹ bức tường trước mặt');
   await page.locator('#playerActionConfirm').click();
   await page.waitForFunction(t=>state.turn>t&&!busy,saved.turn,{timeout:60000});
   assert.match(await page.evaluate(()=>state.log.at(-1).text),/Cao Minh/);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
   assert.deepEqual(errors,[],'Browser runtime errors');
   await page.screenshot({path:`build/playable-${viewport.width}.png`});
   console.log(`PASS: ${viewport.width}px startup, search, failover, inventory view, save, reload-as-newgame, manual load and typed action`);
   await page.close();
  }
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
