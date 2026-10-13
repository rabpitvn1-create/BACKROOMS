'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');

function extract(input, output) {
  const lines = fs.readFileSync(input, 'utf8').split(/\r?\n/);
  const prefix = 'LIVE_ONE_CP_SUMMARY=';
  const line = lines.filter(line => line.includes(prefix)).at(-1);
  if (!line) throw new Error('LIVE_ONE_CP_SUMMARY missing: the real Checkpoint was not completed');
  const summary = JSON.parse(line.slice(line.indexOf(prefix) + prefix.length).trim());
  assert.equal(summary.checkpoint, 'CP-L00-01');
  assert.ok(summary.turns >= 50, 'at least 50 Explorer Turns');
  assert.ok(summary.regularEncounters >= 20, 'at least 20 mandatory Entity Combats');
  assert.ok(summary.battleStarts >= summary.regularEncounters + 1, 'Battle Start must include Boss');
  assert.ok(Array.isArray(summary.gmSampleHashes) && summary.gmSampleHashes.length >= 4,
    'real GM narration samples required');
  assert.ok(typeof summary.storyArcHash === 'string' && summary.storyArcHash.length > 0,
    'real SOL-authored story arc required');
  fs.writeFileSync(output, JSON.stringify(summary, null, 2) + '\n');
  console.log('ACCEPTED live Checkpoint:', {
    turns: summary.turns, regularEncounters: summary.regularEncounters,
    battleStarts: summary.battleStarts, storyArcHash: summary.storyArcHash,
    gmSamples: summary.gmSampleHashes.length,
    followerEvents: summary.followerSequence.split('|').filter(Boolean).length
  });
}

function compare(aPath, bPath) {
  const a = JSON.parse(fs.readFileSync(aPath, 'utf8'));
  const b = JSON.parse(fs.readFileSync(bPath, 'utf8'));
  assert.notEqual(a.storyArcHash, b.storyArcHash,
    'two independent SOL Checkpoints reused exactly the same chapter arc');
  assert.notDeepEqual(a.gmSampleHashes, b.gmSampleHashes,
    'two independent Gehihi runs repeated the same sampled narration');
  assert.notEqual(a.entityEventsHash, b.entityEventsHash,
    'the authored Entity encounter circumstances were copied across separate runs');
  console.log('DIVERSITY_ACCEPTED', {
    arcChanged: true, gmWritingChanged: true,
    entityEventsChanged: true,
    entityOrderChanged: a.entitySequence !== b.entitySequence,
    followerEventsChanged: a.followerSequence !== b.followerSequence,
    plotEventsChanged: a.storyEventsHash !== b.storyEventsHash,
    firstArcHash: a.storyArcHash, secondArcHash: b.storyArcHash
  });
}

if (require.main === module) {
  const [, , mode, a, b] = process.argv;
  if (!a || !b) throw new Error('Usage: verify-live-one-checkpoint.cjs extract|compare first second');
  if (mode === 'extract') extract(a, b);
  else if (mode === 'compare') compare(a, b);
  else throw new Error('unknown mode: ' + mode);
}
module.exports = { extract, compare };
