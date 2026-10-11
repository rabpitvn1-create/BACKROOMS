const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.join(__dirname, '../app/src/main/assets');
const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const actions = fs.readFileSync(path.join(assets, 'item-detail-actions.js'), 'utf8');

test('Character Detail render script parses (Stats and item click handlers)', () => {
  const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(m => m[1]);
  const character = scripts.find(js => js.includes('window.renderCharacterStatusEquipment=render;'));
  assert.ok(character, 'missing Character Detail rendering script');
  assert.doesNotThrow(() => new vm.Script(character));
  assert.match(character, /function comparisonRows\(c\)\{return \[\]\}/);
  assert.match(character, /data-item-id/);
  assert.match(character, /function openItem\(item\)/);
  assert.match(character, /characterBaseStats/);
  assert.match(character, /characterCombatStats/);
  assert.match(character, /characterStatusEffects/);
});

test('Inventory items stay interactive and refer to correct Cao Minh portrait', () => {
  assert.doesNotThrow(() => new vm.Script(actions));
  assert.match(html, /<script src="item-detail-actions\.js"><\/script>/);
  assert.match(html, /<button type="button" class="'\+\(slot\?'equipment-card':'inventory-item-card'\)/);
  assert.match(actions, /view\.addEventListener\('click'/);
  assert.match(actions, /Android\.itemAction/);
  assert.match(html, /avatars\/CAO_MINH_AVATAR\.jpg/);
  assert.ok(fs.statSync(path.join(assets, 'avatars/CAO_MINH_AVATAR.jpg')).size > 0);
});
