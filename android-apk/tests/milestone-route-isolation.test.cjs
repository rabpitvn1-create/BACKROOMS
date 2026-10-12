'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const facade = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'), 'utf8');

test('scripted Checkpoints cannot roll legacy Exit or unlock it from a stale streak', () => {
  assert.match(main, /boolean scripted = requireGameCore\(\)\.milestoneHasPlan\(\)/);
  assert.match(main, /!scripted && exploreAction && !com\.rabpit\.backroom\.core\.HiddenExitStreak\.ready\(state\)/);
  assert.match(main, /boolean hiddenExitReady = !scripted &&/);
  assert.match(main, /!scripted && exitIntent && \(physical \|\| search\)/);
  assert.match(main, /!scripted && anNhienFollowing && search && exitIntent/);
  assert.match(main, /MilestoneWorldGate\.stripLegacyExitProof\(flags\)/);
  assert.match(main, /MilestoneWorldGate\.allowScriptedRoute/);
  assert.match(main, /!canTransition\(before, rolls, requested\.id\)/);
});

test('Core independently rejects forged cross-Level transitions and EXIT proof', () => {
  assert.match(facade, /MilestoneWorldGate\.safeguardCandidate\(core, candidate\)/);
  assert.match(facade, /milestone_level_transition_locked/);
});

test('legacy Exit still runs only outside of scripted Checkpoints', () => {
  assert.match(main, /else \{\s*com\.rabpit\.backroom\.core\.HiddenExitStreak\.apply\(before, state, rolls, oldNodeId, newNodeId\);/);
  assert.match(main, /HiddenExitStreak\.projectedReady\(before, rolls\)/);
});
