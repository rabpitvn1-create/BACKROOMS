'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const pool = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/EntityEncounterPool.java'), 'utf8');
const docs = fs.readFileSync(path.resolve(root, '..', 'ENTITY_ASSETS.md'), 'utf8');

function groupKeys(name) {
  const match = pool.match(new RegExp('private static final String\\[\\] ' + name + ' = \\{([^}]+)\\}'));
  assert.ok(match, 'Missing weighted Entity group: ' + name);
  return [...match[1].matchAll(/"([^"]+)"/g)].map(x => x[1]);
}

test('45% legacy Entity chance applies only when no scripted Milestone plan exists', () => {
  assert.match(main, /thresholdRoll\("entityEncounter", 10000,\s*EntityEncounterPool.ENCOUNTER_THRESHOLD,\s*entityEncounterAction && entityAllowed && !requireGameCore\(\)\.milestoneHasPlan\(\),/);
  assert.match(pool, /ENCOUNTER_THRESHOLD = 4500/);
  assert.match(main, /EntityEncounterPool\.pickForSuccessfulRoll\(entityRoll\.getInt\("roll"\)\)/);
  assert.match(main, /if \(candidateState == null \|\| rolls == null \|\| !rollSuccess\(rolls, "entityEncounter"\)\) return/);
  assert.doesNotMatch(main, /diepMinhEncounter|lifeformEncounter|lifeformEntityKey|roamingEntityKey|entityThresholds/);
  assert.match(docs, /45%/);
});

test('pool covers every packaged Entity exactly once with weights summing to 45%', () => {
  const specification = {COMMON: [14, 250], LIFEFORM: [3, 200], RARE: [3, 100], KILLERS: [2, 50]};
  const keys = [];
  let total = 0;
  for (const [group, [count, weight]] of Object.entries(specification)) {
    const names = groupKeys(group);
    assert.equal(names.length, count);
    keys.push(...names);
    total += names.length * weight;
  }
  assert.equal(total, 4500);
  assert.equal(new Set(keys).size, 22);
  const assets = fs.readdirSync(path.join(root, 'app/src/main/assets/entity'))
    .filter(name => name.endsWith('.webp')).map(name => name.slice(0, -5)).sort();
  assert.deepEqual(keys.sort(), assets);
});

test('AI may only introduce the Entity selected by the authoritative roll', () => {
  assert.match(main, /if \(root.equals\("entityEncounterKey"\)\) \{/);
  assert.match(main, /requested.equals\(rolls\.optString\("selectedEntityKey", ""\)\)/);
  // Gehihi only returns prose; Core owns both roll-based and scripted Entity encounters.
  assert.match(main, /JSONObject candidateState = applyModelOperations\(before, new JSONArray\(\), rolls, action\)/);
  assert.doesNotMatch(main, /generated\.optJSONArray\("ops"\)/);
  const runtime = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/StoryMilestoneRuntime.kt'), 'utf8');
  assert.match(runtime, /val encounter = required\.singleOrNull \{ it\.combatRequired \}/);
  assert.match(runtime, /CombatRuntime\.prepareEncounter\(working, requireNotNull\(encounter\.entityKey\)/);
  assert.doesNotMatch(runtime, /CombatRuntime\.startMilestoneBoss\(/);
  const facade = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'), 'utf8');
  assert.match(facade, /val key = rolls\.optString\("selectedEntityKey"\)/);
  assert.match(facade, /CombatRuntime\.prepareEncounter\(applied, rolledEntity,/);
  assert.doesNotMatch(main, /requireGameCore\(\)\.startCombatState\(/);
  assert.doesNotMatch(main, /theo tỷ lệ Level|roaming\/incursion cực hiếm|Entity gồm Hound/);
});
