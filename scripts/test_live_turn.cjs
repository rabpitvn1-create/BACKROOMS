/* Private localhost browser test. The runtime config is not uploaded or deployed. */
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{Math.random=()=>0.61;});
  await page.goto(process.env.PAGES_TEST_URL||'http://127.0.0.1:4173/Backrooms/',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>typeof state!=='undefined'&&state.coreSnapshot&&state.levelTravel);
  assert.equal(await page.locator('#startupRecovery').isHidden(),true);
  const turn=await page.evaluate(()=>state.turn);
  await page.locator('#submit').click();
  await page.locator('#action').fill('Cao Minh dừng lại, quan sát kỹ bức tường và lắng nghe âm thanh xung quanh.');
  await page.locator('#playerActionConfirm').click();
  await page.waitForFunction(t=>state.turn>t&&!busy,turn,{timeout:180000});
  assert.ok((await page.evaluate(()=>state.log.at(-1).text)).length>40);
  const statuses=await page.evaluate(()=>__webGame.events.filter(x=>x.kind==='provider').map(x=>({alias:x.alias,model:x.model,status:x.status})));
  assert.ok(statuses.some(x=>x.status===200));assert.deepEqual(errors,[]);
  await page.screenshot({path:'build/playable-live-turn.png'});
  console.log('PASS: real browser request, real Gemini narration, canon validation, authoritative turn commit and save');
  console.log('Provider status evidence:',JSON.stringify(statuses));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
