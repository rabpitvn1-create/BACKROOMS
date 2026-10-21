const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const css=fs.readFileSync(path.join(assets,'ui/industrial-crt-v1.css'),'utf8');
const glass=fs.readFileSync(path.join(assets,'ui/snapshot-crt.css'),'utf8');
const native=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
const combat=fs.readFileSync(path.join(assets,'combat-feedback-1193a.css'),'utf8');

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
test('stationary CCTV scanlines are above background but BELOW every portrait and Entity',()=>{
  const bg=layer(rule(css,'.industrial-ui .snapshot .snapshot-bg'));
  const raster=rule(css,'.industrial-ui .snapshot::after');
  const scanlines=layer(raster);
  const actor=rule(native,'.snapshot .snapshot-character');
  assert.equal(bg,0);
  assert.equal(scanlines,1);
  assert.match(raster,/repeating-linear-gradient\(to bottom/);
  assert.match(raster,/pointer-events:none/);
  assert.ok(bg<scanlines);
  assert.ok(scanlines<layer(actor),'Cao Minh must not be striped');
  assert.match(native,/img\.style\.zIndex='2';img\.style\.left='0'/,
    'Entity remains in front of scanlines');
  assert.match(native,/img\.style\.zIndex='2';img\.style\.filter='drop-shadow/,
    'Lucia remains in front of scanlines');
  const dynamicParty=rule(combat,'.snapshot[data-combat-focus] .snapshot-character,\n.snapshot[data-combat-focus] .snapshot-lucia,\n.snapshot[data-combat-focus] .combat-turn-avatar,\n.snapshot[data-combat-focus] .combat-turn-exit');
  assert.ok(scanlines<layer(dynamicParty),'dynamic companions stay above scanlines');
});
test('camera OSD and CRT optics remain untouched above actors',()=>{
  const overlay=rule(glass,'.industrial-ui #snapshot::before');
  const hud=rule(glass,'.industrial-ui #snapshot .snapshot-tv-hud');
  assert.ok(layer(overlay)>2);
  assert.ok(layer(hud)>2);
  assert.match(overlay,/crt-glass-v5\.svg/);
});
