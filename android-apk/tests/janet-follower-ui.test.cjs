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
