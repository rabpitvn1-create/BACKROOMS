const assert=require('node:assert/strict');
const {chromium}=require('playwright');
const response={reply:'Cao Minh lần theo tiếng động, kiểm tra vết tích và quan sát vị trí xung quanh.',entityPreview:'Một tiếng gió thoảng qua bức tường phía xa.'};
function deferred(){let release;const promise=new Promise(resolve=>{release=resolve});return {promise,release}}
(async()=>{
  const browser=await chromium.launch({headless:true});
  try{
    for(const viewport of [{width:412,height:915},{width:1365,height:768}]){
      const page=await browser.newPage({viewport});
      const errors=[];page.on('pageerror',error=>errors.push(error.message));
      await page.addInitScript(()=>{Math.random=()=>0.61});
      let gate=null,requests=0;
      await page.route('https://backrooms-api-proxy.lightangel1092.workers.dev/**',async route=>{
        requests++;
        if(gate)await gate.promise;
        await route.fulfill({status:200,contentType:'application/json',
          body:JSON.stringify({candidates:[{content:{parts:[{text:JSON.stringify(response)}]}}]})});
      });
      await page.goto(process.env.PAGES_TEST_URL||'http://127.0.0.1:4173/Backrooms/',{waitUntil:'domcontentloaded'});
      await page.waitForFunction(()=>document.documentElement.dataset.webMode==='playable'&&state?.coreSnapshot,{timeout:30000});
      // Original Level 0 button images must load and visibly depress on touch.
      for (const [selector, file] of [
        ['#searchActionButton', 'backrooms_level0_search.png'],
        ['#submit', 'backrooms_level0_execute.png'],
        ['#exploreActionButton', 'backrooms_level0_explore.png']
      ]) {
        const art = await page.locator(selector).evaluate(async (button, filename) => {
          const url = new URL('action-buttons/' + filename, document.baseURI);
          const image = new Image();
          image.src = url.href;
          await image.decode();
          const skin = getComputedStyle(button).backgroundImage;
          button.style.setProperty('transition', 'none', 'important');
          button.classList.add('backroom-pressed');
          const pressOffset = new DOMMatrixReadOnly(getComputedStyle(button).transform).m42;
          button.classList.remove('backroom-pressed');
          button.style.removeProperty('transition');
          return {skin, width: image.naturalWidth, height: image.naturalHeight, pressOffset};
        }, file);
        assert.ok(art.skin.includes(file), 'Correct Level 0 artwork must skin ' + selector);
        assert.ok(art.width > 300 && art.height > 100, 'Original image must decode: ' + file);
        assert.ok(art.pressOffset >= 2, 'Button must depress by at least 2 CSS pixels: ' + selector);
      }
      assert.ok(typeof await page.evaluate(()=>window.backroomPendingAction?.start)==='string'||await page.evaluate(()=>typeof window.backroomPendingAction?.start)==='function');

      async function action({text,selector,typed=false}){
        gate=deferred();
        const before=await page.evaluate(()=>({
          turn:state.turn,length:state.log.length,
          saved:(JSON.parse(localStorage.getItem('backroom-apk-state')||'null')||{}).turn
        }));
        if(typed){
          await page.locator('#submit').click();
          assert.equal(await page.locator('#playerActionModal').isVisible(),true);
          await page.locator('#action').fill(text);
          await page.locator('#playerActionConfirm').click();
        }else{
          await page.locator(selector).click();
        }
        await page.waitForFunction(label=>{
          const player=document.querySelector('#log [data-pending-gm-action="player"] .text');
          const gm=document.querySelector('#log [data-pending-gm-action="gm"] .text');
          return player?.textContent===label&&gm?.textContent.includes('Game Master đang xử lý');
        },text,{timeout:7000});
        const actual=await page.evaluate(()=>({
          turn:state.turn,length:state.log.length,
          saved:(JSON.parse(localStorage.getItem('backroom-apk-state')||'null')||{}).turn,
          pending:document.querySelectorAll('#log [data-pending-gm-action]').length,
          gmText:document.querySelector('#log [data-pending-gm-action="gm"] .text')?.textContent,
          scrolled:document.getElementById('log').scrollTop,
          toast:document.querySelector('#backroomActionToast'),
          action:window.backroomPendingAction.current()
        }));
        assert.equal(actual.turn,before.turn,'Core turn must remain unchanged while waiting for GM');
        assert.equal(actual.length,before.length,'Pending player command must not enter authoritative log');
        assert.equal(actual.saved,before.saved,'Pending player command must not change browser save');
        assert.equal(actual.pending,2,'One player action and one GM progress line');
        assert.equal(actual.toast,null,'No detached floating action toast');
        assert.equal(actual.action.text,text);
        const pressSelector=typed?'#playerActionConfirm':selector;
        const visual=await page.locator(pressSelector).evaluate(el=>({
          active:el.classList.contains('backroom-activated'),
          filter:getComputedStyle(el).filter,
          outline:getComputedStyle(el).outlineStyle,
          shadow:getComputedStyle(el).boxShadow,
          disabled:el.disabled
        }));
        assert.equal(visual.active,true,'Pressed button must retain its feedback after click');
        assert.notEqual(visual.filter,'none','Pressed button must brighten');
        assert.equal(visual.outline,'solid','Pressed button must have a clearly visible outline');
        gate.release();gate=null;
        await page.waitForFunction(n=>state.turn>n&&!busy,before.turn,{timeout:60000});
        assert.equal(await page.locator('#log [data-pending-gm-action]').count(),0,
          'Pending lines must reconcile with canonical GM history');
        const canonical=await page.evaluate(label=>({
          matches:state.log.filter(x=>x.role==='player'&&x.text===label).length,
          rendered:Array.from(document.querySelectorAll('#log .message.player .text')).filter(x=>x.textContent===label).length,
          pending:window.backroomPendingAction.current()
        }),text);
        assert.equal(canonical.matches,1,'The Core must commit the player command exactly once');
        assert.equal(canonical.rendered,1,'The GM log must render the command once');
        assert.equal(canonical.pending,null,'No temporary action retained after Core commit');
      }
      await action({text:'Tìm kiếm',selector:'#searchActionButton'});
      await action({text:'Khám phá',selector:'#exploreActionButton'});
      await action({text:'Cao Minh quan sát các dấu vết trên tường',typed:true});

      // A rejected action must remain visibly failed, not silently disappear
      // or poison the save, and a subsequent command must be allowed.
      const beforeError=await page.evaluate(()=>({turn:state.turn,length:state.log.length}));
      await page.evaluate(()=>{
        window.__originalSubmitGameplayAction=Android.submitGameplayAction;
        Android.submitGameplayAction=function(){window.backroomError('Kết nối tạm gián đoạn');};
      });
      await page.locator('#searchActionButton').click();
      await page.waitForFunction(()=>document.querySelector('[data-pending-gm-action="gm"] .text')?.textContent.includes('Lượt chưa hoàn tất'));
      const afterError=await page.evaluate(()=>({
        turn:state.turn,length:state.log.length,busy:busy,
        message:document.querySelector('[data-pending-gm-action="gm"] .text')?.textContent
      }));
      assert.equal(afterError.turn,beforeError.turn);
      assert.equal(afterError.length,beforeError.length);
      assert.equal(afterError.busy,false);
      assert.match(afterError.message,/Kết nối tạm gián đoạn/);
      assert.equal(await page.locator('#searchActionButton').isEnabled(),true);
      await page.evaluate(()=>{
        Android.submitGameplayAction=window.__originalSubmitGameplayAction;
        delete window.__originalSubmitGameplayAction;
      });

      // Menu controls must offer direct feedback in-place, never impersonate
      // a story action in the GM log.
      await page.locator('#mobileMenuButton').click();
      assert.equal(await page.locator('#mobileMenuButton').evaluate(e=>e.classList.contains('backroom-activated')),true);
      const menuLog=await page.evaluate(()=>state.log.length);
      await page.getByRole('button',{name:'Lưu',exact:true}).click();
      assert.match(await page.locator('#backroomMenuFeedback').innerText(),/Đã lưu tiến trình/);
      const saveButton=page.getByRole('button',{name:'Lưu',exact:true});
      assert.equal(await saveButton.evaluate(e=>e.classList.contains('backroom-activated')),true);
      await page.getByRole('button',{name:'Tải',exact:true}).click();
      assert.match(await page.locator('#backroomMenuFeedback').innerText(),/Đã tải tiến trình/);
      assert.equal(await page.evaluate(()=>state.log.length),menuLog);
      await page.locator('#bgmToggle').click();
      assert.equal(await page.locator('#bgmToggle').evaluate(e=>e.classList.contains('backroom-activated')),true);
      await page.locator('#mobileMenuClose').click();
      assert.equal(await page.locator('#backroomActionToast').count(),0);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
      assert.deepEqual(errors,[],'No browser runtime errors');
      await page.screenshot({path:'build/gm-feedback-'+viewport.width+'.png'});
      console.log('PASS GM immediate actions and tactile menu feedback '+viewport.width+'px; network requests='+requests);
      await page.close();
    }
  }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
