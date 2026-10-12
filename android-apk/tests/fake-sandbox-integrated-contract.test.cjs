'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const core = name => fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core', name), 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');

test('Gemini milestone v2 has author-locked Turn/action/outcome and explicit Level ending', () => {
  const plan = core('StoryMilestonePlan.kt');
  assert.match(plan, /SCHEMA_VERSION = 2/);
  assert.match(plan, /string\(item, "action", MAX_DESCRIPTION_CHARS\)/);
  assert.match(plan, /milestone_level_end_required/);
  assert.match(plan, /nextCheckpointTotal/);
  assert.match(plan, /milestone_early_level_end_forbidden/);
  assert.match(plan, /registered\.parent == expected\.level \+ 1/);
  assert.match(plan, /nextPosition\(current: StoryCheckpointCore\.Position, plan: Plan\)/);
});

test('Checkpoint is distinct from Level: a final Milestone can advance to Level 1', () => {
  const runtime = core('StoryMilestoneRuntime.kt');
  assert.match(runtime, /StoryMilestonePlan\.nextPosition\(currentPosition, activePlan\)/);
  assert.match(runtime, /world\["levelJson"\] = target\.asJson\(\)\.toString\(\)/);
  assert.match(runtime, /world\["story\.lastLevelEnding"\]/);
  assert.match(runtime, /milestone_missed_planned_turn/);
  assert.doesNotMatch(runtime, /events\.filter \{ it\.turn\.toLong\(\) <= relative/);
});

test('GM cannot independently advance a scripted main Level or open random exit', () => {
  const facade = core('GameCoreFacade.kt');
  assert.match(facade, /milestone_level_requires_planned_end/);
  assert.match(facade, /milestoneNextPlanRequest/);
  assert.match(facade, /milestone_event_pending/);
  assert.match(main, /boolean hiddenExitReady = !scripted/);
  assert.match(main, /gmScriptedRoutingContext\(currentNodeId\(before\)\)/);
  const route = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/LevelRouteRegistry.java'), 'utf8');
  assert.match(route, /String gmScriptedRoutingContext/);
  assert.match(route, /The main Level ends ONLY when the authored final Milestone/);
  assert.match(main, /!scripted && exitIntent/);
  assert.match(main, /!scripted && exploreAction/);
  assert.match(main, /if \(!requireGameCore\(\)\.milestoneHasPlan\(\)\)/);
});

test('Narrative facts do not become equipment, poisoning or combat modifications', () => {
  const ledger = core('StoryFactLedger.kt');
  assert.match(ledger, /state\.copy\(world = state\.world/);
  assert.doesNotMatch(ledger, /CharacterStatEngine\.setCurrentHp|EntityExpRewards\.award|StatusCommand\(/);
});
