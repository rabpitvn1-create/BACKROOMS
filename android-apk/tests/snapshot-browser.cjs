const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
const output=fs.mkdtempSync(path.join(require('os').tmpdir(),'overlay-preview-'));
const assets=root+'/android-apk/app/src/main/assets';
(async()=>{
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE_PATH||undefined,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader','--use-gl=angle','--no-zygote','--single-process']});
const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.route('http://overlay.test/**',async route=>{
 const name=new URL(route.request().url()).pathname;
 if(name==='/')return route.fulfill({contentType:'text/html',body:'<html><head><style>body{margin:0;background:#111;color:white}.snapshot{position:relative;width:350px;height:250px;margin:20px;background:#333}</style></head><body><h3>Overlay regression preview</h3><div id="snapshot" class="snapshot"></div></body></html>'});
 const file=path.join(assets,name);return route.fulfill({body:fs.readFileSync(file),contentType:file.endsWith('.png')?'image/png':'image/webp'});
});
await page.goto('http://overlay.test/');
await page.evaluate(()=>{window.state={flags:{},combat:{active:false,participants:[{id:'cao_minh'},{id:'luc_tram'},{id:'syvial'}]}};window.Android={levelSnapshot:()=>JSON.stringify({path:'http://overlay.test/level_snapshots/drive/level_0/01.webp',level:0})};});
await page.addScriptTag({content:fs.readFileSync(assets+'/snapshot-ui.js','utf8').replaceAll('file:///android_asset/','http://overlay.test/')});
const results=[];
for(const [w,h] of [[280,250],[350,250],[372,250],[620,450],[240,400],[800,250]]){
 await page.evaluate(([w,h])=>{const s=document.querySelector('#snapshot');s.style.width=w+'px';s.style.height=h+'px'},[w,h]);
 for(const mode of ['standing','aiming','luc_tram','syvial','hound']){
  await page.evaluate(mode=>{state.flags={};state.combat.active=mode!=='standing';if(mode==='standing')backroomClearCombatVisualActor();else backroomSetCombatVisualActor(mode==='luc_tram'?1:mode==='syvial'?2:0,mode==='hound'?'hound':'deathmoth');},mode);
  await page.waitForFunction(()=>[...document.querySelectorAll('.snapshot-grounded')].every(i=>i.complete&&i.naturalWidth&&i.style.visibility==='visible'));
  const data=await page.evaluate(()=>{
   const box=document.querySelector('#snapshot'),w=box.clientWidth,h=box.clientHeight;
   return [...box.querySelectorAll('.snapshot-grounded')].map(img=>{
    const c=document.createElement('canvas');c.width=img.naturalWidth;c.height=img.naturalHeight;const ctx=c.getContext('2d');ctx.drawImage(img,0,0);const m=SnapshotOverlayLayout.bounds(ctx.getImageData(0,0,c.width,c.height).data,c.width,c.height),scale=parseFloat(img.style.height)/c.height;
    return {src:img.src.split('/').pop(),w,h,scale,height:(m.body.bottom-m.body.top)*scale,baseline:parseFloat(img.style.top)+m.body.bottom*scale,left:parseFloat(img.style.left)+m.paint.left*scale,right:parseFloat(img.style.left)+m.paint.right*scale,top:parseFloat(img.style.top)+m.paint.top*scale,bottom:parseFloat(img.style.top)+m.paint.bottom*scale};
   });
  });
  data.forEach(d=>{assert.ok(Math.abs(d.baseline-h*.92)<.01);assert.ok(d.left>=-.01&&d.right<=w+.01&&d.top>=-.01&&d.bottom<=h+.01,JSON.stringify(d));});
  results.push({w,h,mode,data});
  if(w===350)await page.locator('#snapshot').screenshot({path:path.join(output,mode+'-preview.png')});
 }
 const poses=results.filter(r=>r.w===w&&['standing','aiming','luc_tram'].includes(r.mode));
 const heights=poses.map(r=>r.data.find(d=>!['deathmoth.webp','hound.webp'].includes(d.src)).height);
 assert.ok(Math.max(...heights)-Math.min(...heights)<.01);
}
for(const file of fs.readdirSync(assets+'/entity').filter(x=>x.endsWith('.webp'))){
 await page.evaluate(key=>backroomSetCombatVisualActor(0,key),file.slice(0,-4));
 await page.waitForFunction(()=>[...document.querySelectorAll('.snapshot-grounded')].every(i=>i.complete&&i.naturalWidth&&i.style.visibility==='visible'));
}
await page.evaluate(()=>backroomPlayCombatFeedback({target:'actor',flash:true,text:'-3 HP'}));
assert.equal(await page.locator('.combat-float').count(),1);
assert.deepEqual(errors,[]);
fs.writeFileSync(path.join(output,'browser-results.json'),JSON.stringify(results,null,2));
console.log('Evidence:',output);
console.log('PASS: 30 pose/size combinations, 20 entity assets, actor feedback, no JS errors');
await browser.close();
})();
