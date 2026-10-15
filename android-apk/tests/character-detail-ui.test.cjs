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


function openItemThroughClick(item) {
  const nodes = new Map();
  const document = { getElementById(id) {
    if (!nodes.has(id)) nodes.set(id, { dataset: { characterId: 'cao_minh' }, hidden: true,
      textContent: '', innerHTML: '', listeners: {},
      addEventListener(type, listener) { this.listeners[type] = listener; } });
    return nodes.get(id);
  } };
  const context = { document, state: { partyDetails: { members: [{ id: 'cao_minh', inventory: [item] }] } },
    setTimeout() {} };
  context.window = context;
  const script = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
    .map(m => m[1]).find(js => js.includes('window.renderCharacterStatusEquipment=render;'));
  vm.runInNewContext(script, context);
  document.getElementById('characterInventoryView').listeners.click({ target: {
    closest() { return { getAttribute() { return item.id; } }; }
  } });
  assert.equal(document.getElementById('equipmentDetailModal').hidden, false);
  return id => document.getElementById(id).innerHTML;
}

test('Clicking equipped gear renders percentage bonuses inside the actual modal closure', () => {
  const armor = openItemThroughClick({ id: 'armor', name: 'Huyết Ma Chiến Khải', equipped: true,
    bonuses: { strPct: 20, dexPct: 30, agiPct: 100, vitPct: 20, defPct: 0 } });
  assert.match(armor('equipmentDetailStats'), /STR<\/span><strong>\+20%/);
  assert.match(armor('equipmentDetailStats'), /AGI<\/span><strong>\+100%/);
  assert.doesNotMatch(armor('equipmentDetailStats'), /Không có|DEF/);
  const sword = openItemThroughClick({ id: 'sword', weapon: { DMG: 32 }, bonuses: { strPct: 500, dexPct: 100 } });
  assert.match(sword('equipmentDetailStats'), /DMG<\/span><strong>32/);
  assert.match(sword('equipmentDetailStats'), /\+500%/);
});

test('Consumable effects and descriptions render safely without fabricating unknown effects', () => {
  const bandage = openItemThroughClick({ id: 'medical:bandage', useEffects: ['Hồi 10 HP mỗi vật phẩm, không vượt quá Max HP.'], description: '<img onerror="bad">' });
  assert.match(bandage('equipmentDetailAbilities'), /Hồi 10 HP/);
  assert.match(bandage('equipmentDetailDescription'), /&lt;img/);
  assert.doesNotMatch(bandage('equipmentDetailDescription'), /<img/);
  assert.doesNotMatch(bandage('equipmentDetailAbilities'), /<img|chưa ghi nhận/);
  const unknown = openItemThroughClick({ id: 'unknown', useEffects: [] });
  assert.match(unknown('equipmentDetailAbilities'), /Core chưa ghi nhận hiệu ứng/);
  assert.match(unknown('equipmentDetailDescription'), /Chưa có mô tả trong dữ liệu/);
});

 test('Liquid Pain renders percentage healing and its description', () => {
  const liquid = openItemThroughClick({ id: 'liquid-pain', description: 'Vật phẩm tiêu hao.',
    useEffects: ['Hồi 15% Max HP mỗi vật phẩm (làm tròn xuống), không vượt quá Max HP. Không hồi sinh.'] });
  assert.match(liquid('equipmentDetailAbilities'), /Hồi 15% Max HP/);
  assert.match(liquid('equipmentDetailDescription'), /Vật phẩm tiêu hao/);
});
