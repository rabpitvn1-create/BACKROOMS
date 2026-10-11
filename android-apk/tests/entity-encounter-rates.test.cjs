'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const docs = fs.readFileSync(path.resolve(root, '..', 'ENTITY_ASSETS.md'), 'utf8');

function extractPool(name) {
  const match = main.match(new RegExp('String\\[\\] ' + name + ' = \\{([^}]+)\\}'));
  assert.ok(match, 'Missing pool: ' + name);
  return [...match[1].matchAll(/"([^"]+)"/g)].map(item => item[1]);
}

test('ordinary roaming encounter chance gains two percentage points at every Level', () => {
  const match = main.match(/int\[\] entityThresholds = \{([^}]+)\}/);
  assert.ok(match);
  const values = match[1].split(',').map(v => Number(v.trim()));
  assert.deepEqual(values, [1005, 1200, 1350, 1350, 1010, 1400, 1005]);
  assert.match(main, /thresholdRoll\("entityEncounter", 10000, entityThresholds\[level\], entityEncounterAction && entityAllowed/);
});

test('boss and Lifeform family get two percentage points without multiplying by member count', () => {
  assert.match(main, /thresholdRoll\("diepMinhEncounter", 10000, 500, entityEncounterAction && entityAllowed, " unique boss 5%"\)/);
  assert.match(main, /thresholdRoll\("lifeformEncounter", 10000, 400, entityEncounterAction && entityAllowed, " Lifeform trio 4%"\)/);
  assert.match(main, /lifeformEncounter là một roll độc lập đúng 4% cho cả họ Lifeform/);
  assert.match(docs, /roll xuất hiện độc lập 5%/);
  assert.match(docs, /roll độc lập 4% cho cả nhóm/);
});

test('every packaged Entity belongs to an updated encounter pool', () => {
  const roaming = extractPool('roamingPool');
  const lifeform = extractPool('lifeformPool');
  assert.equal(roaming.length, 18);
  assert.equal(lifeform.length, 3);
  const listed = [...roaming, ...lifeform, 'diep_minh'].sort();
  assert.equal(new Set(listed).size, listed.length, 'Entity pools must not overlap');
  const assets = fs.readdirSync(path.join(root, 'app/src/main/assets/entity'))
    .filter(name => name.endsWith('.webp')).map(name => name.slice(0, -5)).sort();
  assert.deepEqual(listed, assets);
});
