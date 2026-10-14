'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { extract, compare } = require('../tools/verify-live-one-checkpoint.cjs');

function fixture() {
  return { summary: {checkpoint: 'CP-L00-01', turns: 50, regularEncounters: 20, battleStarts: 21,
    storyArcHash: 'arc-a', gmSampleHashes: ['a','b','c','d'], entityEventsHash: 'entity-a',
    actualEventNarrationsHash: 'actual-a', characterCommunicationHash: 'speech-a',
    saveLoadVerified: true, followerSequence: 'FOLLOWER:lucia', entitySequence: 'hound',
    characterCommunications: [{speakerId: 'lucia', text: 'Anh đừng chạm vào dây điện.'}]},
    actualEventNarrations: Array.from({length: 22}, (_, i) => ({eventId: 'e' + i, occurrence: 'scene', consequence: 'result'})),
    characterCommunications: [{speakerId: 'lucia', text: 'Anh đừng chạm vào dây điện.'}]};
}
function files(t) {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'live-evidence-'));
  t.after(() => fs.rmSync(folder, {recursive: true, force: true}));
  return ['a.json','b.json','summary.json'].map(name => path.join(folder, name));
}

test('full actual evidence is required, even when the planned quotas and hashes look valid', t => {
  const [a,,out] = files(t); const evidence = fixture();
  evidence.characterCommunications = [];
  fs.writeFileSync(a, JSON.stringify(evidence));
  assert.throws(() => extract(a, out), /full actual character/);
  assert.equal(fs.existsSync(out), false);
});
test('actual evidence with missing Save/Load cannot be accepted', t => {
  const [a,,out] = files(t); const evidence = fixture(); evidence.summary.saveLoadVerified = false;
  fs.writeFileSync(a, JSON.stringify(evidence));
  assert.throws(() => extract(a, out), /Save\/Load/);
});
test('same Canon follower can have different writing; changed plan cannot conceal copied Gehihi content', t => {
  const [a,b,out] = files(t); const first = fixture();
  fs.writeFileSync(a, JSON.stringify(first)); extract(a, out);
  const second = {...first.summary, storyArcHash: 'arc-b', gmSampleHashes: ['e','f','g','h'], entityEventsHash: 'entity-b'};
  fs.writeFileSync(b, JSON.stringify(second));
  assert.throws(() => compare(out, b), /same event narration/);
  second.actualEventNarrationsHash = 'actual-b'; second.characterCommunicationHash = 'speech-b';
  fs.writeFileSync(b, JSON.stringify(second));
  assert.doesNotThrow(() => compare(out, b));
  assert.equal(first.summary.followerSequence, second.followerSequence);
});
