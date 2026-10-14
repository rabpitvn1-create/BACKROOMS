'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');

function extract(input, output) {
  // Read the full emulator artifact. Logcat may truncate a multi-character scene.
  const evidence = JSON.parse(fs.readFileSync(input, 'utf8'));
  const summary = evidence.summary;
  assert.ok(summary, 'full live Checkpoint evidence missing');
  assert.ok(Array.isArray(evidence.actualEventNarrations) && evidence.actualEventNarrations.length >= 5,
    'full actual event scenes required for semantic/Canon review');
  assert.ok(Array.isArray(evidence.characterCommunications) && evidence.characterCommunications.length > 0,
    'full actual character communication required');
  assert.equal(summary.checkpoint, 'CP-L00-01');
  assert.ok(summary.turns === 12, 'exactly 12 Explorer Turns');
  assert.ok(summary.regularEncounters >= 3, 'at least 3 mandatory Entity Combats');
  assert.ok(summary.battleStarts >= summary.regularEncounters + 1, 'Battle Start must include Boss');
  assert.ok(Array.isArray(summary.gmSampleHashes) && summary.gmSampleHashes.length >= 4,
    'real GM narration samples required');
  assert.ok(typeof summary.storyArcHash === 'string' && summary.storyArcHash.length > 0,
    'real SOL-authored story arc required');
  assert.equal(summary.saveLoadVerified, true, 'real UI Save/Load must preserve progress');
  assert.ok(typeof summary.actualEventNarrationsHash === 'string' && summary.actualEventNarrationsHash.length > 0,
    'actual Gehihi event content required, not just SOL plan');
  assert.ok(Array.isArray(summary.characterCommunications) && summary.characterCommunications.length > 0,
    'actual NPC/Follower communication required');
  summary.characterCommunications.forEach(value => {
    assert.ok(typeof value.speakerId === 'string' && value.speakerId.length > 0);
    assert.ok(typeof value.text === 'string' && value.text.trim().length > 0);
  });
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
  assert.notEqual(a.actualEventNarrationsHash, b.actualEventNarrationsHash,
    'Gehihi repeated exactly the same event narration');
  assert.notEqual(a.characterCommunicationHash, b.characterCommunicationHash,
    'Gehihi repeated exactly the same character communication');
  console.log('DIVERSITY_ACCEPTED', {
    arcChanged: true, gmWritingChanged: true,
    entityEventsChanged: true,
    actualGmEventWritingChanged: true,
    characterCommunicationChanged: true,
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
