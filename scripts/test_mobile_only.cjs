/* Real mobile browsing context on wide screens, verified again after deployment. */
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
const base=process.env.PAGES_TEST_URL||'http://127.0.0.1:4173/Backrooms/';
const cases=[
  {width:360,height:740},{width:412,height:915},{width:844,height:390},
  {width:1365,height:768},{width:1920,height:1080}
];
function near(actual,expected,label,tolerance=2){
  assert.ok(Math.abs(actual-expected)<=tolerance,
    label+': expected '+expected+', got '+actual);
}
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  for(const viewport of cases){
   const page=await browser.newPage({viewport});
   const errors=[];
   page.on('pageerror',error=>errors.push(error.message));
   await page.addInitScript(()=>{Math.random=()=>0.61;});
   const url=base+(base.includes('?')?'&':'?')+'mobile-ui-check=2';
   await page.goto(url,{waitUntil:'commit'});
   const wide=viewport.width>430;
   if(wide){
     await page.waitForURL(/\/phone\.html(?:[?#]|$)/,{timeout:30000});
     await page.locator('#mobileGameFrame').waitFor({state:'visible'});
     await page.waitForFunction(()=>{
       const element=document.getElementById('mobileGameFrame');
       return element&&element.contentWindow&&element.contentWindow.document &&
         element.contentWindow.document.documentElement.dataset.webMode==='playable';
     },null,{timeout:30000});
     assert.equal(await page.evaluate(()=>document.querySelector('.shell')),null,
       'Desktop must NOT contain any alternate desktop game DOM');
     const rect=await page.locator('#mobileGameFrame').boundingBox();
     near(rect.width,430,'Phone iframe width');
     near(rect.x,(viewport.width-430)/2,'Phone frame centered');
     near(rect.height,viewport.height,'Phone frame height',3);
   }else{
     await page.waitForFunction(()=>document.documentElement.dataset.webMode==='playable' &&
       typeof state!=='undefined' && state.coreSnapshot,null,{timeout:30000});
     assert.ok(!page.url().includes('phone.html'),'Phone should load direct game');
   }
   const frame=wide?page.frame({name:'backrooms-mobile'}):page;
   assert.ok(frame,'Mobile browsing context is required');
   await frame.waitForFunction(()=>document.documentElement.dataset.webMode==='playable' &&
     typeof state!=='undefined' && state.coreSnapshot,null,{timeout:30000});
   const layout=await frame.evaluate(()=>{
     function box(selector){
       const r=document.querySelector(selector).getBoundingClientRect();
       return {x:r.x,width:r.width,right:r.right};
     }
     return {
       width:innerWidth,
       mobileMedia:matchMedia('(max-width:520px)').matches,
       mode:document.documentElement.dataset.uiMode,
       shell:box('body>.shell'),
       topbar:box('.game>.topbar'),
       actions:box('#playerActionBar'),
       snapshot:box('#snapshot'),
       menuDisplay:getComputedStyle(document.getElementById('sidePanel')).display,
       overflow:document.documentElement.scrollWidth>innerWidth+1
     };
   });
   const mobileWidth=Math.min(viewport.width,430);
   near(layout.width,mobileWidth,'Actual game viewport width');
   near(layout.shell.width,mobileWidth,'Game shell width');
   near(layout.topbar.width,mobileWidth,'Header width');
   near(layout.actions.width,mobileWidth,'Action bar width');
   assert.ok(layout.snapshot.x>=-1&&layout.snapshot.right<=mobileWidth+1,
     'Snapshot must remain within the phone frame');
   assert.equal(layout.mobileMedia,true,
     'Actual mobile CSS breakpoint must match, including on desktop');
   assert.equal(layout.mode,'mobile-only');
   assert.equal(layout.menuDisplay,'none');
   assert.equal(layout.overflow,false);
   await frame.locator('#mobileMenuButton').click();
   await frame.locator('#mobileMenuClose').waitFor({state:'visible'});
   const menu=await frame.locator('#sidePanel').evaluate(element=>{
     const rect=element.getBoundingClientRect();
     return {x:rect.x,width:rect.width,display:getComputedStyle(element).display};
   });
   near(menu.x,0,'Menu left');
   near(menu.width,mobileWidth,'Menu width');
   assert.equal(menu.display,'grid');
   await frame.locator('#party .party-member[data-character="cao-minh"]').click();
   await frame.locator('#characterInventoryView').waitFor({state:'visible'});
   const inventory=await frame.locator('#characterInventoryView').evaluate(
     element=>element.getBoundingClientRect().width);
   near(inventory,mobileWidth,'Inventory width');
   await frame.locator('#characterInventoryBack').click();
   await frame.locator('#mobileMenuClose').click();
   await frame.locator('#submit').click();
   await frame.locator('#playerActionModal').waitFor({state:'visible'});
   const modal=await frame.locator('#playerActionModal').evaluate(
     element=>element.getBoundingClientRect().width);
   near(modal,mobileWidth,'Action modal width');
   await frame.locator('#playerActionClose').click();
   assert.deepEqual(errors,[],'No JavaScript errors');
   await page.screenshot({path:'build/mobile-only-'+viewport.width+'.png'});
   console.log('PASS mobile-only '+viewport.width+'x'+viewport.height+
     ' actual UI viewport='+mobileWidth+'px; '+(wide?'iframe':'direct'));
   await page.close();
  }
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
