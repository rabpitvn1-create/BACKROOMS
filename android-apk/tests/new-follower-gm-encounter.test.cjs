const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../app/src/main');
const db = JSON.parse(fs.readFileSync(path.join(root, 'assets/knowledge/knowledge_db.json'), 'utf8'));
const activity = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const engine = fs.readFileSync(path.join(root, 'java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt'), 'utf8');

test('all four recent Followers have concise grounded GM authored cards', () => {
  for (const [id, name, weapon] of [
    ['CATHERINE', 'Catherine', 'Vesper Nocturne'],
    ['JANET', 'Janet', 'Bayou Belle'],
    ['NGUYEN_QUOC_NAM', 'Nguyễn Quốc Nam', 'Hành Lang 01'],
    ['HUA_TIEU_LAN', 'Hứa Tiểu Lan', 'Trường Xuân']
  ]) {
    const records = db.records.filter(r => r.id === `CHAR.${id}.AUTHORED`);
    assert.equal(records.length, 1, `missing ${id} Canon card`);
    const record = records[0];
    assert.equal(record.domain, 'CHARACTER');
    assert.equal(record.kind, 'authored-character-card');
    assert.match(record.text, new RegExp(name));
    assert.ok(record.text.includes(weapon));
    const canon = path.join(root, 'assets', record.source.document);
    assert.ok(fs.statSync(canon).size > 3000, `missing ${id} source Canon`);
  }
});

test('first meeting Canon is mandatory and cannot appear from name-only text', () => {
  assert.match(engine, /private fun firstMeetingIds\(\)/);
  assert.match(engine, /state\.optJSONArray\("pendingNpcEncounters"\)/);
  assert.match(engine, /record\.id\.endsWith\("\.AUTHORED"\)/);
  assert.match(engine, /actor !in presentActors/);
});

test('both staged-scene and resolved player-action writers require encounter portrayal', () => {
  assert.match(activity, /PUBLIC_EVENT\.npcEncounters/);
  assert.match(activity, /RESOLVED_EVENTS có event\.npcEncounters/);
  assert.match(activity, /firstMeetings/);
  assert.match(activity, /view\.put\("pendingNpcEncounters", firstMeetings\)/);
  assert.match(activity, /!"CORE"\.equals\(step\.optString\("kind"\)\)/);
  assert.match(activity, /CHAR\.\*\.AUTHORED/);
});
