/* Run against the complete release patch chain, including MainActivity's injected UI. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..');
const assets=path.join(root,'app/src/main/assets');
const output=path.join(root,'app/build/combat93-preview');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const body=main.split('private void installUiEnhancements()')[1].split('webView.evaluateJavascript(script, null);')[0].split('String script =')[1];
const enhancements=(body.match(/"(?:\\.|[^"\\])*"/g)||[]).map(s=>JSON.parse(s.replace(/\\'/g,"'"))).join('');
async function run(){
  const server=http.createServer((req,res)=>{
    const file=path.join(assets,decodeURIComponent(req.url.split('?')[0]));
    if(!file.startsWith(assets+path.sep)){res.writeHead(403);res.end();return;}
    try{const types={'.html':'text/html','.js':'application/javascript','.png':'image/png','.webp':'image/webp'};res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.end(path.extname(file)==='.html'?fs.readFileSync(file,'utf8').replaceAll('file:///android_asset/','/'):fs.readFileSync(file));}catch(_){res.writeHead(404);res.end();}
  });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const url='http://127.0.0.1:'+server.address().port;
  const browser=await chromium.launch({headless:true});
  try{
    const page=await browser.newPage({viewport:{width:430,height:932},deviceScaleFactor:2});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.addInitScript(()=>{
      window.__targetCalls=[];
      window.Android={combatTarget:(json,index)=>window.__targetCalls.push(index),getProviderStatus:()=>'',saveGameState:()=>{}};
    });
    // Android asset URLs are served unchanged from the packaged asset directory.
    await page.route('**/*',async route=>{
      const requested=route.request().url();
      if(requested.startsWith('file:///android_asset/'))return route.fulfill({body:fs.readFileSync(path.join(assets,requested.split('android_asset/')[1]))});
      return route.continue();
    });
    await page.goto(url+'/index.html');
    await page.evaluate(enhancements.replaceAll('file:///android_asset/',url+'/'));
    const fixture=name=>JSON.parse(fs.readFileSync(path.join(output,name+'.json'),'utf8'));
    const send=async c=>page.evaluate(combat=>{const incoming=JSON.parse(JSON.stringify(state));incoming.combat=combat;incoming.turn=7;window.backroomTurn(JSON.stringify(incoming));},c);
    await send(fixture('before'));
    await page.locator('.combat93-unit.entity').nth(1).locator('button').click();
    assert.deepEqual(await page.evaluate(()=>window.__targetCalls),[1]);
    assert.equal(await page.locator('.combat93-unit.actor .combat93-name').textContent(),'Cao Minh');
    const queue=await page.evaluate(c=>window.Combat93Snapshot.phases(c),fixture('after'));
    assert.deepEqual(queue.filter(p=>p.phase==='entity').map(p=>p.entityIndex),[0,1]);
    assert.equal(queue.flatMap(p=>p.events).length,fixture('after').feedbackEvents.length);
    assert.deepEqual(queue.flatMap(p=>p.events),fixture('after').feedbackEvents);
    await page.screenshot({path:path.join(output,'combat93-before.png')});
    await send(fixture('after'));
    await page.waitForTimeout(100);
    assert.equal(await page.evaluate(()=>window.__combatFeedbackBusy),true);
    assert.equal(await page.locator('.combat93-unit.actor .combat93-name').textContent(),'Cao Minh');
    assert.equal(await page.locator('.combat93-unit.entity button:not(:disabled)').count(),0);
    assert((await page.locator('.combat93-float').allTextContents()).some(s=>s.includes('Chảy máu')));
    await page.screenshot({path:path.join(output,'combat93-actor.png')});
    await page.waitForTimeout(850);
    assert.equal(await page.locator('.responding').getAttribute('data-index'),'0');
    assert((await page.locator('.combat93-float').allTextContents()).includes('MISS'));
    await page.waitForTimeout(850);
    assert.equal(await page.locator('.responding').getAttribute('data-index'),'1');
    assert((await page.locator('.combat93-unit.actor .combat93-float').allTextContents()).some(s=>s.includes('HP')));
    await page.screenshot({path:path.join(output,'combat93-response.png')});
    await page.waitForFunction(()=>window.__combatFeedbackBusy===false);
    assert.equal(await page.locator('.combat93-unit.actor .combat93-name').textContent(),fixture('after').participants[fixture('after').actorIndex].name);
    await send(fixture('after'));
    assert.equal(await page.evaluate(()=>window.__combatFeedbackBusy),false);
    assert.equal(await page.locator('.combat93-float').count(),0);
    await send(fixture('victory'));
    assert.equal(await page.evaluate(()=>window.__combatFeedbackBusy),true);
    assert(await page.locator('.combat93-float').count()>0);
    await page.screenshot({path:path.join(output,'combat93-victory.png')});
    await page.waitForFunction(()=>window.__combatFeedbackBusy===false);
    assert.equal(await page.locator('.combat93-scene').count(),0);
    assert.deepEqual(errors,[]);
    console.log('Chromium passed: actor, ordered Entity responses, status damage, target lock, stale callback, killing blow and cleanup.');
  }finally{await browser.close();await new Promise(r=>server.close(r));}
}
run().catch(e=>{console.error(e);process.exitCode=1;});
