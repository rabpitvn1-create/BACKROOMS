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

test('all five new Entities have independently traceable visual and Canon cards',()=>{
  const full=path.join(root,'app/src/main/assets/canon/NEW_ENTITIES_CODEX_R01.md');
  assert.ok(fs.existsSync(full),'full Entity Codex must be bundled in the APK');
  const body=fs.readFileSync(full,'utf8');
  const pairs=[['926','926'],['927','927'],['LuckyBear','LUCKYBEAR'],
    ['Brownies','BROWNIES'],['luc_tram_hac_hoa','LUC_TRAM_HAC_HOA']];
  for(const [key,id] of pairs){
    const visual=records.get('ENTITY.'+id+'.VISUAL');
    const canon=records.get('ENTITY.'+id+'.CANON');
    assert.ok(visual,key+' visual missing');
    assert.ok(canon,key+' canon missing');
    assert.match(visual.text,/^\[VISUAL\] /);
    assert.equal(visual.domain,'ENTITY');
    assert.equal(canon.kind,'entity-canon-card');
    assert.equal(visual.source.document,canon.source.document);
    assert.ok(canon.source.document.startsWith('Google Drive /Novel/Asset/03_Entity/NEW_ENTITIES_CODEX_R01.md'));
    assert.ok(body.includes(visual.source.anchor),key+' anchor missing from complete Codex');
    assert.ok(visual.tags.some(tag=>canon.tags.includes(tag)));
    assert.ok(canon.references.includes('ENTITY.GLOBAL_HARD_LOCK'));
  }
  assert.match(records.get('ENTITY.LUCKYBEAR.CANON').text,/200 EXP/);
  assert.match(records.get('ENTITY.BROWNIES.CANON').text,/100 EXP/);
  assert.match(records.get('ENTITY.LUC_TRAM_HAC_HOA.CANON').text,/UNKNOWN/);
});

test('eleven authored level cards and four exact authored nodes are indexed without resolving conflicts', () => {
  for (let number = 0; number <= 10; number++) {
    const id = 'LEVEL.' + String(number).padStart(2, '0') + '.AUTHORED';
    const record = records.get(id);
    assert.ok(record, 'missing ' + id);
    assert.match(record.source.document, /^Google Drive \/Novel\/Asset\/02_Level\//);
  }
  for (const node of ['0.5', 'epsilon', 'dullness', 'red_rooms']) {
    assert.ok(records.has('LEVEL.NODE.' + node));
  }
  assert.equal(records.has('LEVEL.NODE.0.1'), false, 'unresolved authored/runtime conflict');
  assert.equal(records.has('LEVEL.NODE.0.7'), false, 'unresolved authored/runtime conflict');
});

test('authored character identities stay separate and source traceable', () => {
  for (const person of ['CAO_MINH', 'LUC_TRAM', 'LUCIA', 'TRAC_LAM']) {
    const r = records.get('CHAR.' + person + '.AUTHORED');
    assert.ok(r, person);
    assert.match(r.source.document, /^Google Drive \/Novel\/Asset\/01_Character\//);
  }
  assert.notEqual(records.get('CHAR.LUC_TRAM.AUTHORED').text, records.get('CHAR.LUCIA.AUTHORED').text);
  // Detailed revival/memory continuity belongs to the confirmed-actor firewall,
  // not the quick card returned merely because a name was mentioned.
  assert.doesNotMatch(records.get('CHAR.LUC_TRAM.AUTHORED').text, /quên Cao Minh/i);
  assert.match(records.get('CHAR.LUC_TRAM.REVIVAL_R06').text, /QUÊN TẤT CẢ VỀ CAO MINH/);
});

test('runtime uses exact level and Core-confirmed encounter identity', () => {
  assert.ok(reader.includes('add("LEVEL.NODE.$node"'));
  assert.ok(reader.includes('combat?.optBoolean("active", false) == true'));
  assert.ok(reader.includes('add(confirmedVisual, "confirmed active Entity visual canon")'));
  assert.ok(reader.includes('presentActors.filter { it != "cao_minh" }'));
  assert.ok(reader.includes('add("$prefix.AUTHORED", "present actor authored card")'));
  // Core-confirmed pending encounters use characterId (fallback to flagKey).
  assert.match(reader, /addConfirmed\(\s*event\.optString\("characterId", event\.optString\("flagKey", ""\)\),\s*"PRESENT_NOT_IN_PARTY"\s*\)/);
  assert.ok(!reader.includes('confirmedRoll("entityEncounter")'));
  assert.ok(reader.includes('flags?.optString("entityEncounterKey", "")'));
  assert.ok(reader.includes('id == confirmedEntityVisualId()'));
});

test('unknown or absent characters cannot be treated as active based on a name substring', () => {
  assert.ok(reader.includes('when (val id = normalize(idOrName))'));
  assert.ok(reader.includes('it in db.characterIds'));
  assert.ok(reader.includes('member.optString("presence", "ACTIVE") == "ACTIVE"'));
  assert.ok(!reader.includes('id.contains("iris")'));
  // Luc Tram confirmation comes from Core-confirmed presentActors (pendingNpcEncounters),
  // NOT from the removed Milestone scheduledCharacter().
  assert.ok(reader.includes('val lucTramConfirmed = "luc_tram" in presentActors'));
  assert.ok(!reader.includes('scheduledCharacter('));
  assert.match(reader, /if \(lucTramConfirmed\) \{[\s\S]*?direct \+= "CHAR.LUC_TRAM.REVIVAL_R06"/);
  assert.ok(reader.includes('if ("iris" in presentActors)'));
  assert.ok(reader.includes('if ("syvial" in presentActors)'));
});

test('encounter context comes from Core-confirmed pending encounters rather than Milestone', () => {
  // The Milestone system is removed. Encounter context now comes from
  // Core-confirmed pendingNpcEncounters (state or rolls JSON).
  assert.ok(reader.includes('state.optJSONArray("pendingNpcEncounters")'));
  assert.ok(reader.includes('rolls.optJSONArray("pendingNpcEncounters")'));
  assert.ok(!reader.includes('milestoneEventsDue'));
  assert.ok(!reader.includes('scheduledCharacter('));
  assert.ok(!reader.includes('confirmedRoll('));
  assert.ok(!reader.includes('selectedEntityKey'));
});

test('legacy provenance is labeled, not silently promoted into author Canon', () => {
  assert.ok(db.records.length >= 89, "adding author-approved Canon must not shrink the registry");
  assert.ok(db.records.some(r => r.id === "ENTITY.DIEP_MINH.CANON"));
  const approved = db.records.filter(r => r.id === 'LEVEL.00.ZONES.OVERVIEW' &&
    r.source.document === 'android-apk/LEVEL_0_EIGHT_ZONES_CANON.md');
  assert.equal(approved.length, 1, 'author-approved in-repository Canon has separate provenance');
  const legacy = db.records.filter(r =>
    !r.source.document.startsWith('Google Drive /Novel/Asset/') &&
    !approved.includes(r));
  assert.equal(legacy.length, 42, 'the existing unverified records remain unchanged');
  assert.ok(reader.includes('LEGACY_UNVERIFIED'));
  assert.ok(reader.includes('provenance='));
});
