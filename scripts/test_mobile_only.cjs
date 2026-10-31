/* Regression: GitHub Pages serves ONE mobile layout at all viewport sizes. */
const assert = require('node:assert/strict');
const {chromium} = require('playwright');

function near(actual, expected, label, tolerance = 2) {
  assert.ok(Math.abs(actual - expected) <= tolerance,
    label + ': expected ' + expected + ', got ' + actual);
}

(async () => {
  const browser = await chromium.launch({headless:true});
  const cases = [
    {width:360,height:740}, // compact Android portrait
    {width:412,height:915}, // standard Android portrait
    {width:844,height:390}, // landscape browser must not switch to desktop
    {width:1365,height:768}, // laptop
    {width:1920,height:1080} // desktop monitor
  ];
  try {
    for (const viewport of cases) {
      const page = await browser.newPage({viewport});
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.addInitScript(() => { Math.random = () => 0.61; });
      await page.goto(process.env.PAGES_TEST_URL ||
        'http://127.0.0.1:4173/Backrooms/', {waitUntil:'domcontentloaded'});
      await page.waitForFunction(() =>
        document.documentElement.dataset.webMode === 'playable' &&
        typeof state !== 'undefined' && state.coreSnapshot, null, {timeout:30000});

      const geometry = () => page.evaluate(() => {
        function box(selector) {
          const el = document.querySelector(selector);
          if (!el) return null;
          const r = el.getBoundingClientRect();
          return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};
        }
        return {
          mode:document.documentElement.dataset.uiMode,
          viewport:window.innerWidth,
          overflow:document.documentElement.scrollWidth > innerWidth + 1,
          shell:box('body > .shell'),
          header:box('.game > .topbar'),
          snapshot:box('#snapshot'),
          actions:box('#playerActionBar'),
          menu:box('#sidePanel'),
          backdrop:box('#mobileMenuBackdrop'),
          inventory:box('#characterInventoryView'),
          modal:box('#playerActionModal'),
          menuDisplay:getComputedStyle(document.querySelector('#sidePanel')).display
        };
      });
      function inPhoneFrame(rect, shell, label) {
        assert.ok(rect, 'Missing ' + label);
        near(rect.x, shell.x, label + ' left');
        near(rect.width, shell.width, label + ' mobile width');
      }
      const view = await geometry();
      const phoneWidth = Math.min(viewport.width,430);
      assert.equal(view.mode,'mobile-only','Desktop UI must never be enabled');
      near(view.shell.width, phoneWidth, 'Shell width');
      near(view.shell.x, (viewport.width - phoneWidth) / 2, 'Shell centered');
      near(view.shell.height, viewport.height, 'Phone frame viewport height', 3);
      assert.equal(view.overflow,false,'No document horizontal overflow');
      assert.equal(view.menuDisplay,'none','Sidebar must start closed');
      inPhoneFrame(view.header,view.shell,'Header');
      inPhoneFrame(view.actions,view.shell,'Action bar');
      assert.ok(view.snapshot.x >= view.shell.x - 1 &&
        view.snapshot.right <= view.shell.right + 1,
        'Snapshot must remain inside mobile frame');

      await page.locator('#mobileMenuButton').click();
      await page.locator('#mobileMenuClose').waitFor({state:'visible'});
      const opened = await geometry();
      inPhoneFrame(opened.menu,view.shell,'Mobile menu');
      inPhoneFrame(opened.backdrop,view.shell,'Mobile menu backdrop');
      assert.equal(opened.menuDisplay,'grid','Menu must always be mobile HUD');

      await page.locator('#party .party-member[data-character="cao-minh"]').click();
      await page.locator('#characterInventoryView').waitFor({state:'visible'});
      const inventory = await geometry();
      inPhoneFrame(inventory.inventory,view.shell,'Character inventory');
      await page.locator('#characterInventoryBack').click();
      await page.locator('#mobileMenuClose').click();

      await page.locator('#submit').click();
      await page.locator('#playerActionModal').waitFor({state:'visible'});
      const action = await geometry();
      inPhoneFrame(action.modal,view.shell,'Typed action dialog');
      await page.locator('#playerActionClose').click();
      assert.equal(await page.evaluate(() =>
        document.documentElement.scrollWidth > innerWidth + 1),false);
      assert.deepEqual(errors,[],'No browser errors in mobile-only UI');
      await page.screenshot({path:'build/mobile-only-'+viewport.width+'.png'});
      console.log('PASS mobile-only '+viewport.width+'x'+viewport.height+
        ': shell/menu/inventory/action are '+phoneWidth+'px wide');
      await page.close();
    }
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error);process.exit(1);});
