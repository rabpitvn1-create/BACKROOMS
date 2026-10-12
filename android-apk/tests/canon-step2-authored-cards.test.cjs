'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const db = JSON.parse(fs.readFileSync(path.join(root, 'app/src/main/assets/knowledge/knowledge_db.json'), 'utf8'));
const reader = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt'), 'utf8');
const records = new Map(db.records.map(r => [r.id, r]));
const entityKeys = [
  'hound', 'clump', 'duller', 'deathmoth', 'hostile_faceling', 'false_puddle',
  'paintings', 'smiler', 'skin-stealer', 'predatory_window', 'biological_pipeline',
  'wretch', 'cable_mimic', 'hotel_corpse_lure', 'blackroot_sentinel',
  'sinew_strider', 'hollow_grasper', 'the_beast_of_level_5', 'slenderman',
  'diep_minh', 'jeff_the_killer', 'jane_the_killer'
];

test('all twenty-two playable entities have one Drive-sourced visual card', () => {
  assert.equal(new Set(db.records.map(r => r.id)).size, db.records.length, 'unique IDs');
  for (const key of entityKeys) {
    const id = 'ENTITY.' + key.toUpperCase().replace(/[^A-Z0-9]/g, '_') + '.VISUAL';
    const record = records.get(id);
    assert.ok(record, 'missing ' + id);
    assert.equal(record.domain, 'ENTITY');
    assert.match(record.text, /^\[VISUAL\] /);
    assert.match(record.source.document, /^Google Drive \/Novel\/Asset\/03_Entity\//);
    assert.ok(record.source.anchor.startsWith('## '));
  }
});

test('eleven authored level cards and four exact authored node cards are indexed', () => {
  for (let number = 0; number <= 10; number++) {
    const id = 'LEVEL.' + String(number).padStart(2, '0') + '.AUTHORED';
    const record = records.get(id);
    assert.ok(record, 'missing ' + id);
    assert.match(record.source.document, /^Google Drive \/Novel\/Asset\/02_Level\//);
  }
  for (const node of ['0.5', 'epsilon', 'dullness', 'red_rooms']) {
    assert.ok(records.has('LEVEL.NODE.' + node));
  }
  assert.equal(records.has('LEVEL.NODE.0.1'), false, 'conflicting Level 0.1 lore cannot silently override runtime');
  assert.equal(records.has('LEVEL.NODE.0.7'), false, 'conflicting Level 0.7 lore cannot silently override runtime');
});

test('authored character identities stay separate and source traceable', () => {
  for (const person of ['CAO_MINH', 'LUC_TRAM', 'LUCIA', 'TRAC_LAM']) {
    const r = records.get('CHAR.' + person + '.AUTHORED');
    assert.ok(r);
    assert.match(r.source.document, /^Google Drive \/Novel\/Asset\/01_Character\//);
  }
  assert.notEqual(records.get('CHAR.LUC_TRAM.AUTHORED').text, records.get('CHAR.LUCIA.AUTHORED').text);
});

test('runtime reads exact level node and confirmed combat entity, not every card', () => {
  assert.match(reader, /add\("LEVEL.NODE\.\$node"/);
  assert.match(reader, /combat\?\.optBoolean\("active", false\)/);
  assert.match(reader, /add\("ENTITY\.\$recordId\.VISUAL"/);
  assert.match(reader, /if \("lucia" in presentActors\)/);
  assert.match(reader, /rolls\.optJSONObject\("luciaEncounter"\).*optBoolean\("success", false\)/);
  assert.match(reader, /rolls\.optString\("selectedEntityKey", ""\)/);
  assert.match(reader, /selectedRollKey\.isNotBlank\(\) -> selectedRollKey/);
});
