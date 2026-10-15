const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.join(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets');
const core = path.join(root, 'app/src/main/java/com/rabpit/backroom/core');
const context = { window: {} };
vm.runInNewContext(fs.readFileSync(path.join(assets, 'item-icons.js'), 'utf8'), context);
const icons = context.window.backroomItemIcons;
const manifest = JSON.parse(fs.readFileSync(path.join(assets, 'item-icons/manifest.json'), 'utf8'));

test('Every registered loot item and catalog equipment has its own WebP icon', () => {
  const files = ['GameState.kt', 'HealingItems.kt', 'SpecialFollowersCanon.kt', 'LucTramCanon.kt',
    'LuciaCanon.kt', 'AnNhienCanon.kt', 'MadGodCanon.kt', 'CharacterEquipmentSystem.kt'];
  const constants = new Map();
  for (const file of files) {
    const source = fs.readFileSync(path.join(core, file), 'utf8');
    for (const match of source.matchAll(/const val (\w+)\s*=\s*"([^"]+)"/g)) constants.set(match[1], match[2]);
  }
  const registry = fs.readFileSync(path.join(core, 'OfflineItemRegistry.kt'), 'utf8');
  const catalog = fs.readFileSync(path.join(core, 'CharacterEquipmentSystem.kt'), 'utf8');
  const ids = [...registry.matchAll(/\bDefinition\(\s*("[^"]+"|[A-Z_][A-Z_0-9]*)\s*,/g)].map(m =>
    m[1].startsWith('"') ? JSON.parse(m[1]) : constants.get(m[1]));
  ids.push(...[...catalog.matchAll(/\bid\s*=\s*([A-Z_][A-Z_0-9]*)\s*,\s*name\s*=/g)].map(m => constants.get(m[1])));
  assert.ok(ids.length >= 20, 'registry/catalog extraction must not silently skip definitions');
  for (const id of ids) {
    assert.ok(id && Object.hasOwn(icons, id), 'Missing item icon: ' + id);
    assert.ok(fs.existsSync(path.join(assets, 'item-icons', icons[id] + '.webp')));
  }
  assert.equal(new Set(ids.map(id => icons[id])).size, ids.length, 'Distinct catalog items need distinct artwork');
});

test('Manifest v5 covers exactly 22 registered item IDs and one unknown fallback', () => {
  assert.equal(manifest.version, 5);
  assert.equal(manifest.format, 'WEBP');
  assert.equal(manifest.items.length, 23);
  const named = manifest.items.filter(entry => entry.item_id !== null);
  assert.equal(named.length, Object.keys(icons).length);
  const seen = new Set();
  for (const entry of named) {
    assert.ok(!seen.has(entry.item_id), 'duplicate item ID: ' + entry.item_id);
    seen.add(entry.item_id);
    assert.ok(Object.hasOwn(icons, entry.item_id), 'missing manifest ID in runtime map: ' + entry.item_id);
    assert.equal(icons[entry.item_id], entry.key, 'wrong image mapping: ' + entry.item_id);
  }
  assert.equal(manifest.items.filter(entry => entry.item_id === null).length, 1);
  assert.equal(manifest.items.find(entry => entry.item_id === null).key, 'unknown');
});

test('Every packaged WebP is byte-for-byte compatible with Drive manifest SHA-256 and 128x128 VP8 header', () => {
  const iconDir = path.join(assets, 'item-icons');
  const files = fs.readdirSync(iconDir).filter(file => file.endsWith('.webp')).sort();
  assert.deepEqual(files, manifest.items.map(entry => entry.file).sort());
  assert.equal(files.length, 23);
  assert.equal(fs.readdirSync(iconDir).filter(file => file.endsWith('.svg')).length, 0, 'obsolete SVG icons must not ship');
  for (const entry of manifest.items) {
    assert.equal(entry.file, entry.key + '.webp');
    assert.equal(entry.mime_type, 'image/webp');
    assert.equal(entry.width, 128);
    assert.equal(entry.height, 128);
    const buffer = fs.readFileSync(path.join(iconDir, entry.file));
    assert.equal(buffer.length, entry.bytes, 'byte size changed: ' + entry.file);
    assert.equal(crypto.createHash('sha256').update(buffer).digest('hex'), entry.sha256, 'icon altered: ' + entry.file);
    assert.equal(buffer.toString('ascii', 0, 4), 'RIFF');
    assert.equal(buffer.readUInt32LE(4) + 8, buffer.length);
    assert.equal(buffer.toString('ascii', 8, 16), 'WEBPVP8 ');
    assert.equal(buffer.toString('hex', 23, 26), '9d012a');
    assert.equal(buffer.readUInt16LE(26) & 0x3fff, 128, 'width: ' + entry.file);
    assert.equal(buffer.readUInt16LE(28) & 0x3fff, 128, 'height: ' + entry.file);
  }
});

test('Real renderer uses only bundled WebP, contain scaling and no extra icon frame', () => {
  const html = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
  const css = fs.readFileSync(path.join(assets, 'item-presentation.css'), 'utf8');
  assert.match(html, /src="item-icons\/'\+key\+'\.webp"/);
  assert.doesNotMatch(html + css, /item-pixel-icon|image-rendering:pixelated|item-icons\/'\+key\+'\.svg"/);
  assert.match(html + css, /object-fit:contain/);
  assert.match(css, /\.equipment-card-icon[\s\S]*?border:0/);
  assert.match(css, /\.equipment-detail-icon\s*\{[^}]*border:0/);
});
