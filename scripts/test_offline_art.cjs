/* Browser regression: acquire all four images online, restart entirely offline. */
const assert = require('node:assert/strict');
const {chromium} = require('playwright');

(async () => {
  const browser = await chromium.launch({headless:true});
  try {
    const context = await browser.newContext({viewport:{width:412,height:915}});
    const page = await context.newPage();
    await page.goto(process.env.PAGES_TEST_URL || 'http://127.0.0.1:4173/Backrooms/', {
      waitUntil:'domcontentloaded'
    });
    await page.waitForFunction(() =>
      window.backroomOfflineCharacterAssets &&
      window.backroomOfflineCharacterAssets.status().offlineReady,
      null, {timeout:120000});
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.waitForFunction(() => navigator.serviceWorker.controller !== null,
      null, {timeout:30000});
    const before = await page.evaluate(() => {
      const db = window.BACKROOMS_CAO_MINH_ASSET_DATABASE;
      const art = window.backroomCloudinary;
      return {
        roles:db.assets.map(x=>x.role).sort(),
        ready:window.backroomOfflineCharacterAssets.status().offlineReady,
        urls:[art.characterAvatar('cao-minh'),
          art.characterOverlay('cao-minh',true),
          art.characterOverlay('cao-minh',false),
          art.characterSword('cao-minh')]
      };
    });
    assert.deepEqual(before.roles,['avatar','combat','exploration','sword']);
    assert.equal(before.ready,true);
    assert.ok(before.urls.every(url=>url.startsWith('blob:')));

    await context.setOffline(true);
    await page.reload({waitUntil:'domcontentloaded',timeout:45000});
    await page.waitForFunction(() =>
      window.backroomOfflineCharacterAssets &&
      window.backroomOfflineCharacterAssets.status().offlineReady,
      null, {timeout:30000});
    const after = await page.evaluate(() => {
      const art = window.backroomCloudinary;
      return {
        count:window.BACKROOMS_CAO_MINH_ASSET_DATABASE.assets.length,
        cache:window.backroomOfflineCharacterAssets.status(),
        urls:[art.characterAvatar('cao-minh'),
          art.characterOverlay('cao-minh',true),
          art.characterOverlay('cao-minh',false),
          art.characterSword('cao-minh')]
      };
    });
    assert.equal(after.count,4);
    assert.equal(after.cache.cached,4);
    assert.ok(after.urls.every(url=>url.startsWith('blob:')));
    await page.screenshot({path:'build/cao-minh-offline-412.png'});
    console.log('PASS: all four Cao Minh images persist across real offline Pages reload');
  } finally { await browser.close(); }
})().catch(error => {console.error(error);process.exit(1);});
