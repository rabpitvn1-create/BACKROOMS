const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const css=fs.readFileSync(path.join(assets,'ui/industrial-crt-v1.css'),'utf8');
const glass=fs.readFileSync(path.join(assets,'ui/snapshot-crt.css'),'utf8');
const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');

function rule(source,selector){
  const start=source.indexOf(selector+'{');
  assert.ok(start>=0,selector+' missing');
  return source.slice(start+selector.length+1,source.indexOf('}',start));
}
function layer(declaration){
  const match=declaration.match(/(?:^|;)\s*z-index:\s*(\d+)/);
  assert.ok(match,'missing explicit stack position');
  return Number(match[1]);
}

test('Snapshot renders without a raster scanline overlay',()=>{
  assert.doesNotMatch(css,/\.industrial-ui \.snapshot::after\s*\{/,
    'do not create any snapshot ::after scanline layer');
  assert.doesNotMatch(css,/repeating-linear-gradient\(to bottom/,
    'no horizontal CCTV stripes in Snapshot CSS');
  assert.doesNotMatch(glass,/repeating-linear-gradient|scanline/i,
    'CRT glass CSS must not recreate scanlines');
  assert.match(rule(css,'.industrial-ui .snapshot'),/isolation:isolate/);
});

test('CRT frame, reflection, camera HUD, player and Entity overlays remain intact',()=>{
  const reflection=rule(css,'.industrial-ui .snapshot::before');
  const overlay=rule(glass,'.industrial-ui #snapshot::before');
  const hud=rule(glass,'.industrial-ui #snapshot .snapshot-tv-hud');
  assert.match(reflection,/radial-gradient/);
  assert.match(overlay,/crt-glass-v5\.svg/);
  assert.ok(layer(overlay)>layer(rule(native,'.snapshot .snapshot-character')));
  assert.ok(layer(hud)>2);
  assert.match(native,/img\.style\.zIndex='2';img\.style\.left='0'/,
    'Entity overlay still available');
  assert.match(native,/img\.style\.zIndex='2';img\.style\.filter='drop-shadow/,
    'Lucia overlay still available');
});
