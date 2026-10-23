const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const main = path.resolve(__dirname, '../app/src/main');
const java = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const canon = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/JanetCanon.kt'), 'utf8');
const skill = fs.readFileSync(path.join(main, 'java/com/rabpit/backroom/core/CompanionSkillCatalog.kt'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(main, 'assets/item-icons/manifest.json'), 'utf8'));

test('Janet artwork and face portrait are separate real packaged assets', () => {
  assert.ok(fs.statSync(path.join(main, 'assets/Janet.webp')).size <= 350000);
  assert.ok(fs.statSync(path.join(main, 'assets/avatars/Janet_avatar.webp')).size > 1000);
  assert.match(canon, /avatars\/Janet_avatar\.webp/);
  assert.match(canon, /Janet\.webp/);
});
test('Janet snapshot is wired through the existing render hook and respects encounter presence', () => {
  assert.match(java, /JANET_FOLLOWER_OVERLAY_R01/);
  assert.match(java, /function appendJanetVisual/);
  assert.match(java, /encounteredCharacters/);
  assert.match(java, /file:\/\/\/android_asset\/Janet\.webp/);
  assert.match(java, /__janetBaseRenderSnapshot\(\);appendJanetVisual\(\);/);
});
test('Janet has five skill entries plus one Ultimate and four distinct equipment icons', () => {
  for (const name of ["Ragin' Cajun", 'Bayou Swing', 'Barbed Encore',
    'Redline Sidestep', 'Ride or Die', 'Last Inning // Bayou Thunder']) {
    assert.ok(skill.includes(name), 'Missing skill: ' + name);
  }
  const listed = manifest.items.filter(item => item.item_id && item.item_id.startsWith('janet:'));
  assert.equal(listed.length, 4);
  assert.equal(new Set(listed.map(x => x.key)).size, 4);
  for (const item of listed) {
    const file = path.join(main, 'assets/item-icons', item.file);
    assert.equal(fs.statSync(file).size, item.bytes);
    assert.equal(item.width, 128);
    assert.equal(item.height, 128);
  }
});


test('Janet encounter preserves the original full-size right-anchored Cao Minh sprite',()=>{
  // Execute the actual Android-injected layout body against a minimal DOM,
  // rather than merely accepting a textual claim about the expected styles.
  const sourceLine=java.split('\n').find(line=>line.includes('SNAPSHOT_PARTY_LAYOUT_R02'));
  assert.ok(sourceLine,'the fixed snapshot party compositor must be installed');
  const js=JSON.parse(sourceLine.trim().replace(/\s*\+\s*$/,''));
  const body=js.slice(js.indexOf('function layoutSnapshotParty()'),
    js.indexOf('var __partyLayoutBaseRenderSnapshot'));
  assert.ok(body.startsWith('function layoutSnapshotParty()'));
  const createLayout=new Function('document','state',body+';return layoutSnapshotParty;');
  function arrange(companions,combat=false){
    const hero={style:{}};
    const overlays=Array.from({length:companions},()=>({style:{}}));
    const box={dataset:{},querySelector:selector=>selector==='.snapshot-character'?hero:null,
      querySelectorAll:()=>overlays};
    createLayout({getElementById:id=>id==='snapshot'?box:null},
      {combat:{active:combat}})();
    return {hero,overlays,box};
  }
  const alone=arrange(0);
  assert.equal(alone.hero.style.height,undefined,'solo snapshot uses the original 97% rule');
  const janet=arrange(1);
  assert.equal(janet.hero.style.height,'97%');
  assert.equal(janet.hero.style.width,'auto');
  assert.equal(janet.hero.style.maxWidth,'100%');
  assert.equal(janet.hero.style.right,'0');
  assert.equal(janet.hero.style.left,'auto','Cao Minh must not remain at a fixed left lane');
  assert.equal(janet.overlays[0].style.left,'3%');
  assert.equal(janet.overlays[0].style.height,'90%');
  for(const count of [2,3,5]){
    const layout=arrange(count);
    assert.equal(layout.hero.style.height,'97%','followers never shrink Cao Minh');
    assert.equal(layout.hero.style.right,'0');
    assert.ok(layout.overlays.every(x=>x.style.left!=='auto'));
  }
  const battle=arrange(1,true);
  assert.equal(battle.box.dataset.partyLayout,'combat');
  assert.equal(battle.hero.style.height,undefined,'combat focus owns its own portrait layout');
});
