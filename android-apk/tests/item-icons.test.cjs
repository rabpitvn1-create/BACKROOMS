const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets');
const core = path.join(root, 'app/src/main/java/com/rabpit/backroom/core');
const context = { window: {} };
vm.runInNewContext(fs.readFileSync(path.join(assets, 'item-icons.js'), 'utf8'), context);
const icons = context.window.backroomItemIcons;

test('Every registered loot item and catalog equipment has its own pixel icon', () => {
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
    assert.ok(fs.existsSync(path.join(assets, 'item-icons', icons[id] + '.svg')));
  }
  assert.equal(new Set(ids.map(id => icons[id])).size, ids.length, 'Distinct catalog items need distinct artwork');
});

test('Pixel SVGs use local, static integer geometry and a brown gold background', () => {
  const files = fs.readdirSync(path.join(assets, 'item-icons')).filter(f => f.endsWith('.svg'));
  assert.equal(files.length, Object.keys(icons).length + 1, 'one fallback plus all named icons');
  const unique = new Set();
  for (const file of files) {
    const svg = fs.readFileSync(path.join(assets, 'item-icons', file), 'utf8');
    assert.match(svg, /viewBox="0 0 64 64"/);
    assert.match(svg, /shape-rendering="crispEdges"/);
    assert.match(svg, /fill="#75532f"/);
    assert.doesNotMatch(svg, /<script|<image|<foreignObject|\son\w+=|(?:href|filter)=/i);
    assert.ok(!unique.has(svg), 'duplicate icon ' + file); unique.add(svg);
    for (const rect of svg.matchAll(/<rect x="(\d+)" y="(\d+)" width="(\d+)" height="(\d+)"/g)) {
      const [x,y,w,h] = rect.slice(1).map(Number);
      assert.ok(w > 0 && h > 0 && x+w <= 64 && y+h <= 64, 'out of bounds: ' + file);
    }
  }
});
