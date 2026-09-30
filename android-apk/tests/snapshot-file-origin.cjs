// Requires Playwright. Keep file:// origins: the Android app does not serve HTTP.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright');
(async()=>{
 const assets=path.resolve(__dirname,'../app/src/main/assets');
 const output=fs.mkdtempSync(path.join(os.tmpdir(),'overlay-file-origin-'));
 const fixture=path.join(output,'index.html');
 fs.writeFileSync(fixture,'<style>body{background:#111;color:white}.snapshot{width:350px;height:250px}</style><div id="snapshot" class="snapshot"></div>');
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE_PATH||undefined,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader','--use-gl=angle','--no-zygote','--single-process']});
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('console',m=>{if(m.type()==='warning')console.log(m.text());});
  await page.goto(pathToFileURL(fixture).href);
  await page.evaluate(background=>{
   window.state={flags:{},combat:{active:false,participants:[{id:'cao_minh'},{id:'lucia'}]}};
   window.Android={levelSnapshot:()=>JSON.stringify({path:background,level:0})};
   window.pixelReadAttempts=0;
   const original=CanvasRenderingContext2D.prototype.getImageData;
   CanvasRenderingContext2D.prototype.getImageData=function(...args){pixelReadAttempts++;return original.apply(this,args);};
  },pathToFileURL(path.join(assets,'level_snapshots/drive/level_0/01.webp')).href);
  const source=fs.readFileSync(process.env.OVERLAY_SCRIPT||path.join(assets,'snapshot-ui.js'),'utf8');
  await page.addScriptTag({content:source.replaceAll('file:///android_asset/',pathToFileURL(assets+'/').href)});
  await page.waitForLoadState('networkidle');
  for(const mode of ['standing','cao_minh','lucia','hound']) {
   if(mode!=='standing')await page.evaluate(mode=>{state.combat.active=true;backroomSetCombatVisualActor(mode==='lucia'?1:0,mode==='hound'?'hound':'deathmoth');},mode);
   await page.waitForFunction(()=>[...document.querySelectorAll('img')].every(i=>i.complete&&i.naturalWidth));
   const overlays=await page.locator('.snapshot-grounded').evaluateAll(images=>images.map(i=>({src:i.src,visibility:getComputedStyle(i).visibility,width:i.getBoundingClientRect().width,height:i.getBoundingClientRect().height})));
   assert.ok(overlays.length>0);
   for(const i of overlays){assert.equal(i.visibility,'visible',JSON.stringify(i));assert.ok(i.width>0&&i.height>0);}
   await page.locator('#snapshot').screenshot({path:path.join(output,mode+'.png'),animations:'disabled'});
  }
  assert.equal(await page.evaluate(()=>pixelReadAttempts),0,'Bundled sprites must never require canvas pixel access');
  // Container-only resize must still place already-loaded sprites.
  await page.evaluate(()=>document.querySelector('#snapshot').style.width='240px');
  await page.waitForFunction(()=>{const i=document.querySelector('.snapshot-character');return parseFloat(i.style.left)<240;});
  assert.deepEqual(errors,[]);
  console.log('PASS: file:// standing, Cao Minh, Lucia, Deathmoth, Hound; zero canvas reads. Evidence:',output);
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
