/* Run after prepare-runtime.py with Playwright available on NODE_PATH:
   node --test android-apk/tests/industrial-crt.browser.cjs
   Optional: BACKROOMS_RUNTIME_ROOT, CHROMIUM_EXECUTABLE, UI_QA_SCREENSHOTS. */
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const crypto=require('node:crypto');
const vm=require('node:vm');
const {chromium}=require('playwright');
const root=path.resolve(process.env.BACKROOMS_RUNTIME_ROOT||path.join(__dirname,'..'));
const assets=path.join(root,'app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
assert.ok(html.includes('UI_119_BACKPORT_R01'),'prepare-runtime.py must run first');
// Decode only the actual Java string literals passed to evaluateJavascript.
const java=fs.readFileSync(path.join(root,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const method=java.slice(java.indexOf('  private void installUiEnhancements()'),java.indexOf('    webView.evaluateJavascript(script, null);'));
const nativeScript=[...method.matchAll(/"(?:\\.|[^"\\])*"/g)]
  .map(m=>JSON.parse(m[0].replace(/\\'/g,"'"))).join('').replaceAll('file:///android_asset/','/');
let server,browser,origin;
before(async()=>{
  server=http.createServer((req,res)=>{
    try{
      const relative=decodeURIComponent(new URL(req.url,'http://localhost').pathname).replace(/^\/+/, '')||'index.html';
      const file=path.resolve(assets,relative);
      if(!file.startsWith(assets+path.sep)){res.writeHead(403).end();return;}
      const mime={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.webp':'image/webp','.png':'image/png','.svg':'image/svg+xml'};
      res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
      res.end(fs.readFileSync(file));
    }catch(e){res.writeHead(404).end();}
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE||undefined,args:['--no-sandbox','--disable-dev-shm-usage']});
});
after(async()=>{if(browser)await browser.close();if(server)await new Promise(resolve=>server.close(resolve));});

async function boot(viewport,theme){
  const page=await browser.newPage({viewport});
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/index.html',route=>route.fulfill({contentType:'text/html; charset=utf-8',body:html.replaceAll('file:///android_asset/','/').replace(theme?'NEVER_MATCH':/<link id="industrialCrtTheme"[^>]*>/,'')}));
  await page.addInitScript(()=>{
    window.__bridgeCalls=[];
    window.Android={
      getPartyDetails(){return '{}'},
      clearCoreState(){window.__bridgeCalls.push(['clearCoreState'])},
      submitAction(...args){window.__bridgeCalls.push(['submitAction',...args])},
      requestEntityOverlay(...args){window.__bridgeCalls.push(['requestEntityOverlay',...args])}
    };
  });
  await page.goto(origin+'/index.html');
  await page.evaluate(nativeScript);
  await page.waitForFunction(()=>{const b=document.querySelector('.snapshot-bg');return b&&b.complete&&b.naturalWidth>0});
  await page.waitForFunction(()=>Array.from(document.querySelectorAll('#snapshot img')).every(img=>img.complete&&img.naturalWidth>0));
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  return {page,errors};
}

test('all shipped Drive assets match their recorded hashes',()=>{
  const dir=path.join(assets,'ui/industrial-crt-v1');
  const manifest=JSON.parse(fs.readFileSync(path.join(dir,'assets.json'),'utf8'));
  assert.equal(manifest.assets.length,6);
  for(const asset of manifest.assets){
    const bytes=fs.readFileSync(path.join(dir,asset.file));
    assert.equal(bytes.length,asset.bytes,asset.file);
    assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),asset.sha256,asset.file);
  }
});

test('prepared inline JavaScript parses, including equipment detail callbacks',()=>{
  let count=0;
  for(const match of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)){
    if(match[1].trim())new vm.Script(match[1],{filename:'inline-'+(++count)+'.js'});
  }
  assert.ok(count>0);
});

for(const viewport of [{width:320,height:640},{width:390,height:844},{width:1280,height:800}]){
  test(`snapshot geometry, controls and save feedback at ${viewport.width}x${viewport.height}`,async()=>{
    const baseline=await boot(viewport,false),themed=await boot(viewport,true);
    const page=themed.page;
    try{
      async function geometry(p){return p.evaluate(()=>{
        const box=document.getElementById('snapshot'),r=box.getBoundingClientRect(),s=getComputedStyle(box);
        return {x:r.x,y:r.y,width:r.width,height:r.height,padding:s.padding,border:s.borderWidth,margin:s.margin};
      });}
      const expected=await geometry(baseline.page);
      assert.equal(expected.height,300);
      assert.deepEqual(await geometry(page),expected);
      async function scenePixels(p){
        const box=await p.locator('#snapshot').boundingBox();
        // Exclude the original 7px rounded corners, but retain the inner edge
        // that a 12px decorative overlay would cover even with equal geometry.
        return p.screenshot({clip:{x:box.x+8,y:box.y+8,width:box.width-16,height:box.height-16}});
      }
      async function imageGeometry(p){return p.locator('#snapshot img').evaluateAll(images=>images.map(img=>{
        const r=img.getBoundingClientRect(),s=getComputedStyle(img);
        return {className:img.className,x:r.x,y:r.y,width:r.width,height:r.height,objectFit:s.objectFit,transform:s.transform};
      }));}
      assert.deepEqual(await imageGeometry(page),await imageGeometry(baseline.page));
      const referenceScene=await scenePixels(baseline.page);
      assert.ok((await scenePixels(page)).equals(referenceScene),'theme must preserve visible scene pixels, not only the outer box');
      const bezel=await page.evaluate(()=>{const box=document.getElementById('snapshot'),s=getComputedStyle(box);return {image:s.borderImageSource,width:s.borderImageWidth,overlay:getComputedStyle(box,'::after').content}});
      assert.match(bezel.image,/black-observation-monitor-v1.webp/);
      assert.equal(bezel.width,'1');
      assert.equal(bezel.overlay,'none');
      const bar=await page.locator('#playerActionBar').boundingBox();
      assert.ok(bar.y>=0&&bar.y+bar.height<=viewport.height+1,'action bar stays in viewport');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
      for(const selector of ['#searchActionButton','#submit','#exploreActionButton'])assert.equal(await page.locator(selector).isVisible(),true);
      await page.locator('#gameMenuOpen').click();
      const status=page.locator('.game-menu-sheet .industrial-crt-status');
      await page.getByRole('button',{name:'Lưu',exact:true}).click();
      await page.waitForFunction(()=>document.querySelector('.game-menu-sheet .industrial-crt-status').textContent.includes('Đã lưu Turn'));
      const saved=await page.evaluate(()=>localStorage.getItem('backroom-apk-state'));
      assert.ok(saved);
      await page.getByRole('button',{name:'Tải',exact:true}).click();
      await page.waitForFunction(()=>document.querySelector('.game-menu-sheet .industrial-crt-status').textContent.includes('Đã tải save Turn'));
      const clears=await page.evaluate(()=>window.__bridgeCalls.filter(x=>x[0]==='clearCoreState').length);
      await page.getByRole('button',{name:'Bắt đầu lại từ đầu',exact:true}).click();
      assert.match(await status.textContent(),/lần nữa trong 5 giây/);
      assert.equal(await page.evaluate(()=>localStorage.getItem('backroom-apk-state')),saved);
      assert.equal(await page.evaluate(()=>window.__bridgeCalls.filter(x=>x[0]==='clearCoreState').length),clears);
      if(process.env.UI_QA_SCREENSHOTS){fs.mkdirSync(process.env.UI_QA_SCREENSHOTS,{recursive:true});await page.screenshot({path:path.join(process.env.UI_QA_SCREENSHOTS,`menu-${viewport.width}.png`)});}
      await page.locator('#gameMenuClose').click();
      await page.locator('#submit').click();
      assert.equal(await page.locator('#playerActionModal').isVisible(),true);
      await page.locator('#playerActionCancel').click();
      await page.evaluate(()=>{render();render()});
      await page.waitForFunction(()=>Array.from(document.querySelectorAll('#snapshot img')).every(img=>img.complete&&img.naturalWidth>0));
      assert.equal(await page.locator('#snapshot .snapshot-bg').count(),1);
      assert.deepEqual(await geometry(page),expected);
      assert.ok((await scenePixels(page)).equals(referenceScene),'native redraw must retain the same visible scene');
      if(process.env.UI_QA_SCREENSHOTS)await page.screenshot({path:path.join(process.env.UI_QA_SCREENSHOTS,`game-${viewport.width}.png`)});
      assert.deepEqual(baseline.errors,[]);
      assert.deepEqual(themed.errors,[]);
    }finally{await baseline.page.close();await page.close();}
  });
}
