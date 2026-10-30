const assert = require('node:assert/strict');
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    for (const viewport of [{ width: 412, height: 915 }, { width: 1365, height: 768 }]) {
      const page = await browser.newPage({ viewport });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(process.env.PAGES_TEST_URL || 'http://127.0.0.1:4173/Backrooms/', { waitUntil: 'domcontentloaded' });
      await page.locator('#webPreviewNotice').waitFor({ state: 'visible' });
      await page.waitForTimeout(250);
      assert.equal(await page.locator('#startupRecovery').isHidden(), true, 'Native recovery must not be reported as a web save failure');
      assert.equal(await page.locator('html').getAttribute('data-web-mode'), 'preview');
      assert.match(await page.locator('#webPreviewNotice').innerText(), /Bản xem trước trên web/);
      assert.equal(await page.locator('#webPreviewNotice a').getAttribute('href'), 'https://github.com/rabpitvn1-create/Backrooms/releases/download/v1.0.0.0/Backrooms-1.0.0.0-debug.apk');
      for (const id of ['searchActionButton', 'submit', 'exploreActionButton']) {
        assert.equal(await page.locator('#' + id).isDisabled(), true);
      }
      assert.equal(await page.locator('#log .message').count() > 0, true);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1);
      assert.equal(overflow, false, 'Unexpected horizontal overflow');
      assert.deepEqual(errors, [], 'Browser JavaScript errors');
      await page.screenshot({ path: `build/pages-${viewport.width}.png` });
      await page.close();
      console.log(`PASS: recovered preview, APK link and native-action guards at ${viewport.width}px`);
    }
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
