const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const source=fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');

test('Android JavaScript bridge is restricted to the bundled game page',()=>{
 assert.match(source,/private static boolean isTrustedGamePage\(Uri uri\)/);
 assert.match(source,/"file"\.equals\(uri\.getScheme\(\)\)/);
 assert.match(source,/"\/android_asset\/index\.html"\.equals\(uri\.getPath\(\)\)/);
 assert.match(source,/shouldOverrideUrlLoading\(WebView view, android\.webkit\.WebResourceRequest request\)[\s\S]*?return !isTrustedGamePage\(request\.getUrl\(\)\)/);
 assert.match(source,/shouldOverrideUrlLoading\(WebView view, String url\)[\s\S]*?return !isTrustedGamePage\(Uri\.parse\(url\)\)/);
 assert.match(source,/if \(!isTrustedGamePage\(Uri\.parse\(url\)\)\) return;[\s\S]*?installUiEnhancements\(\)/);
});

test('Game WebView cannot load remote frames or grant local-file cross-origin access',()=>{
 assert.match(source,/settings\.setBlockNetworkLoads\(true\)/);
 assert.match(source,/settings\.setAllowFileAccessFromFileURLs\(false\)/);
 assert.match(source,/settings\.setAllowUniversalAccessFromFileURLs\(false\)/);
 assert.match(source,/webView\.addJavascriptInterface\(new GameBridge\(\), "Android"\)/);
});
